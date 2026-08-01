package com.mammouthclient.app.ui

import android.app.Application
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mammouthclient.app.data.AppContainer
import com.mammouthclient.app.data.AppSettings
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
    /** Chemins locaux des images générées, les plus récentes en tête. */
    val results: List<String> = emptyList(),
    val note: String = "",
    val error: String? = null,
    val info: String? = null,
    val availableModels: List<String> = MammouthApi.FALLBACK_IMAGE_MODELS
) {
    companion object {
        val SIZES = listOf("1024x1024", "1024x1536", "1536x1024", "512x512")
    }
}

class ImageViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val settingsRepository = AppContainer.settings(application)
    private val api = AppContainer.api()

    private val _state = MutableStateFlow(
        ImageUiState(model = settingsRepository.settings.value.imageModel)
    )
    val state: StateFlow<ImageUiState> = _state.asStateFlow()

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

    /** Propose la liste des modèles renvoyée par l'API en filtrant ceux qui semblent visuels. */
    fun offerModels(models: List<String>) {
        val visual = models.filter { id ->
            listOf("image", "flux", "dall", "recraft", "diffusion", "imagen", "midjourney", "grok-image", "banana")
                .any { keyword -> id.contains(keyword, true) }
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
                api.generateImages(
                    settings = settings,
                    prompt = current.prompt,
                    model = current.model,
                    count = current.count,
                    size = current.size.takeIf { it.isNotBlank() }
                )
            }

            outcome.fold(
                onSuccess = { result ->
                    val saved = mutableListOf<String>()
                    result.base64.forEach { encoded ->
                        FileUtils.decodeBase64Image(appContext, encoded)?.let { saved += it }
                    }
                    result.urls.forEach { url ->
                        api.downloadBytes(url)?.let { bytes ->
                            val bitmap = withContext(Dispatchers.IO) {
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            }
                            if (bitmap != null) {
                                FileUtils.saveGeneratedImage(appContext, bitmap)?.let { saved += it }
                            }
                        }
                    }
                    _state.value = _state.value.copy(
                        isGenerating = false,
                        results = saved + _state.value.results,
                        note = if (saved.isEmpty()) result.note else "",
                        error = if (saved.isEmpty()) {
                            "Aucune image renvoyée par le modèle « ${current.model} ». " +
                                "Vérifiez que ce modèle génère bien des images."
                        } else {
                            null
                        }
                    )
                },
                onFailure = { throwable ->
                    _state.value = _state.value.copy(
                        isGenerating = false,
                        error = throwable.message ?: "Échec de la génération."
                    )
                }
            )
        }
    }

    fun saveToGallery(path: String) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { FileUtils.exportToGallery(appContext, path) }
            _state.value = _state.value.copy(
                info = if (ok) "Image enregistrée dans la galerie." else null,
                error = if (ok) null else "Impossible d'enregistrer l'image."
            )
        }
    }
}
