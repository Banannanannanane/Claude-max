package com.mammouthclient.app.ui

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mammouthclient.app.data.AppContainer
import com.mammouthclient.app.data.AppSettings
import com.mammouthclient.app.data.AttachmentKind
import com.mammouthclient.app.data.GeneratedMedia
import com.mammouthclient.app.data.MediaStore
import com.mammouthclient.app.net.ImageGenerationResult
import com.mammouthclient.app.net.MammouthApi
import com.mammouthclient.app.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ImageUiState(
    val prompt: String = "",
    val model: String = AppSettings.DEFAULT_IMAGE_MODEL,
    val count: Int = 1,
    val size: String = "1024x1024",
    val isGenerating: Boolean = false,
    /** Image source pour l'édition (image-to-image). */
    val sourcePath: String? = null,
    /** Historique persistant des médias produits, les plus récents en tête. */
    val results: List<GeneratedMedia> = emptyList(),
    val note: String = "",
    val error: String? = null,
    val info: String? = null,
    val availableModels: List<String> = MammouthApi.FALLBACK_IMAGE_MODELS
) {
    val isEditing: Boolean get() = sourcePath != null

    companion object {
        val SIZES = listOf("1024x1024", "1024x1536", "1536x1024", "512x512")
    }
}

class ImageViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val settingsRepository = AppContainer.settings(application)
    private val api = AppContainer.api()
    private val mediaStore = MediaStore(application)

    private val _state = MutableStateFlow(
        ImageUiState(model = settingsRepository.settings.value.imageModel)
    )
    val state: StateFlow<ImageUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = mediaStore.load().sortedByDescending { it.createdAt }
            _state.value = _state.value.copy(results = saved)
        }
    }

    fun setPrompt(value: String) {
        _state.value = _state.value.copy(prompt = value)
    }

    fun setModel(value: String) {
        _state.value = _state.value.copy(model = value)
        settingsRepository.update { it.copy(imageModel = value) }
    }

    fun setCount(value: Int) {
        _state.value = _state.value.copy(count = value.coerceIn(1, 4))
    }

    fun setSize(value: String) {
        _state.value = _state.value.copy(size = value)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun clearInfo() {
        _state.value = _state.value.copy(info = null)
    }

    /** Choisit une image de départ pour l'édition (image-to-image). */
    fun pickSource(uri: Uri) {
        viewModelScope.launch {
            val imported = runCatching { FileUtils.importUri(appContext, uri) }
                .getOrDefault(emptyList())
                .firstOrNull { it.kind == AttachmentKind.IMAGE }
            _state.value = _state.value.copy(
                sourcePath = imported?.path,
                error = if (imported == null) "Image illisible." else null
            )
        }
    }

    fun clearSource() {
        _state.value = _state.value.copy(sourcePath = null)
    }

    /** Réutilise un résultat comme image de départ. */
    fun useAsSource(media: GeneratedMedia) {
        if (media.isVideo || media.path.isBlank()) return
        _state.value = _state.value.copy(sourcePath = media.path)
    }

    fun offerModels(models: List<String>) {
        val visual = models.filter { id ->
            VISUAL_KEYWORDS.any { keyword -> id.contains(keyword, true) }
        }
        if (visual.isNotEmpty()) {
            _state.value = _state.value.copy(availableModels = visual)
        }
    }

    fun generate() {
        val current = _state.value
        if (current.isGenerating || current.prompt.isBlank()) return
        val settings = settingsRepository.settings.value
        if (!settings.hasApiKey) {
            _state.value = current.copy(error = "Ajoutez votre clé API dans les réglages.")
            return
        }

        _state.value = current.copy(isGenerating = true, error = null, note = "")
        viewModelScope.launch {
            val outcome = runCatching {
                val source = current.sourcePath
                if (source != null) {
                    api.editImage(
                        settings = settings,
                        prompt = current.prompt,
                        model = current.model,
                        imagePath = source,
                        size = current.size.takeIf { it.isNotBlank() }
                    )
                } else {
                    api.generateImages(
                        settings = settings,
                        prompt = current.prompt,
                        model = current.model,
                        count = current.count,
                        size = current.size.takeIf { it.isNotBlank() }
                    )
                }
            }

            outcome.fold(
                onSuccess = { result -> storeResult(result, current) },
                onFailure = { throwable ->
                    _state.value = _state.value.copy(
                        isGenerating = false,
                        error = throwable.message ?: "Échec de la génération."
                    )
                }
            )
        }
    }

    private suspend fun storeResult(result: ImageGenerationResult, request: ImageUiState) {
        val produced = mutableListOf<GeneratedMedia>()

        result.base64.forEach { encoded ->
            FileUtils.decodeBase64Image(appContext, encoded)?.let { path ->
                produced += GeneratedMedia(
                    path = path,
                    prompt = request.prompt,
                    model = request.model
                )
            }
        }

        result.urls.forEach { url ->
            if (MammouthApi.isVideoUrl(url)) {
                produced += GeneratedMedia(
                    url = url,
                    prompt = request.prompt,
                    model = request.model,
                    isVideo = true
                )
            } else {
                val bytes = api.downloadBytes(url)
                val bitmap = bytes?.let {
                    withContext(Dispatchers.IO) { BitmapFactory.decodeByteArray(it, 0, it.size) }
                }
                val path = bitmap?.let { FileUtils.saveGeneratedImage(appContext, it) }
                produced += if (path != null) {
                    GeneratedMedia(path = path, url = url, prompt = request.prompt, model = request.model)
                } else {
                    GeneratedMedia(url = url, prompt = request.prompt, model = request.model)
                }
            }
        }

        val merged = produced + _state.value.results
        _state.value = _state.value.copy(
            isGenerating = false,
            results = merged,
            note = if (produced.isEmpty()) result.note else "",
            error = if (produced.isEmpty()) {
                "Aucun média renvoyé par « ${request.model} ». Vérifiez que ce modèle produit des images."
            } else {
                null
            }
        )
        mediaStore.save(merged.take(200))
    }

    fun saveToGallery(media: GeneratedMedia) {
        if (media.path.isBlank()) {
            _state.value = _state.value.copy(error = "Ce média est distant : utilisez « Ouvrir ».")
            return
        }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { FileUtils.exportToGallery(appContext, media.path) }
            _state.value = _state.value.copy(
                info = if (ok) "Image enregistrée dans la galerie." else null,
                error = if (ok) null else "Impossible d'enregistrer l'image."
            )
        }
    }

    fun delete(media: GeneratedMedia) {
        val remaining = _state.value.results.filterNot { it.id == media.id }
        _state.value = _state.value.copy(results = remaining)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (media.path.isNotBlank()) runCatching { java.io.File(media.path).delete() }
            }
            mediaStore.save(remaining)
        }
    }

    private companion object {
        val VISUAL_KEYWORDS = listOf(
            "image", "flux", "dall", "recraft", "diffusion", "imagen",
            "midjourney", "grok-image", "banana", "video", "veo", "kling", "sora"
        )
    }
}
