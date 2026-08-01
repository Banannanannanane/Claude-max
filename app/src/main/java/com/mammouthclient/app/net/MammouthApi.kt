package com.mammouthclient.app.net

import com.mammouthclient.app.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class MammouthException(message: String, val httpCode: Int? = null) : IOException(message)

/** Évènements émis pendant une génération. */
sealed interface ChatEvent {
    data class Delta(val text: String) : ChatEvent
    data class Completed(val usage: ApiUsage?, val model: String?) : ChatEvent
}

/**
 * Client de l'API Mammouth (compatible OpenAI) :
 * `POST /chat/completions` (texte + vision, en streaming), `POST /images/generations`
 * et `GET /models`.
 */
class MammouthApi {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(2, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .build()

    private fun requestBuilder(settings: AppSettings, path: String): Request.Builder {
        val base = settings.baseUrl.trim().trimEnd('/').ifBlank { AppSettings.DEFAULT_BASE_URL }
        return Request.Builder()
            .url("$base/$path")
            .header("Authorization", "Bearer ${settings.apiKey.trim()}")
            .header("Accept", "application/json")
    }

    private fun jsonBody(payload: String) =
        payload.toRequestBody("application/json; charset=utf-8".toMediaType())

    /** Liste les modèles disponibles pour la clé fournie. */
    suspend fun listModels(settings: AppSettings): List<String> = withContext(Dispatchers.IO) {
        val request = requestBuilder(settings, "models").get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw errorFrom(response.code, body)
            json.decodeFromString(ModelList.serializer(), body)
                .data.map { it.id }.filter { it.isNotBlank() }.distinct().sorted()
        }
    }

    /**
     * Envoie une conversation et émet les fragments au fil de l'eau, puis un
     * [ChatEvent.Completed] portant la consommation de jetons.
     */
    fun streamChat(
        settings: AppSettings,
        messages: List<ApiMessage>,
        model: String
    ): Flow<ChatEvent> = flow {
        val payload = ChatRequest(
            model = model,
            messages = messages,
            stream = settings.streaming,
            temperature = settings.temperature,
            topP = settings.topP.takeIf { it < 1f },
            maxTokens = settings.maxTokens.takeIf { it > 0 },
            streamOptions = if (settings.streaming) StreamOptions(true) else null
        )
        val request = requestBuilder(settings, "chat/completions")
            .post(jsonBody(json.encodeToString(ChatRequest.serializer(), payload)))
            .build()

        val call: Call = client.newCall(request)
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw errorFrom(response.code, response.body?.string().orEmpty())
                }
                val body = response.body ?: throw MammouthException("Réponse vide du serveur.")

                if (!settings.streaming) {
                    val parsed = json.decodeFromString(ChatResponse.serializer(), body.string())
                    val text = parsed.choices.firstOrNull()?.message?.content.orEmpty()
                    if (text.isNotEmpty()) emit(ChatEvent.Delta(text))
                    emit(ChatEvent.Completed(parsed.usage, parsed.model))
                    return@use
                }

                var usage: ApiUsage? = null
                var responseModel: String? = null
                val source = body.source()
                while (currentCoroutineContext().isActive) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isEmpty() || line.startsWith(":")) continue
                    if (!line.startsWith("data:")) continue

                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break

                    val chunk = runCatching {
                        json.decodeFromString(StreamChunk.serializer(), data)
                    }.getOrNull() ?: continue

                    chunk.usage?.let { usage = it }
                    chunk.model?.let { responseModel = it }
                    val delta = chunk.choices.firstOrNull()?.delta?.content
                    if (!delta.isNullOrEmpty()) emit(ChatEvent.Delta(delta))
                }
                emit(ChatEvent.Completed(usage, responseModel))
            }
        } finally {
            if (!call.isCanceled()) call.cancel()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Génération d'images. Tente d'abord l'endpoint OpenAI `images/generations` ; si le
     * serveur ne l'expose pas, bascule sur `chat/completions` avec le modèle d'image et
     * récupère les URLs présentes dans la réponse.
     */
    suspend fun generateImages(
        settings: AppSettings,
        prompt: String,
        model: String,
        count: Int,
        size: String?
    ): ImageGenerationResult = withContext(Dispatchers.IO) {
        val payload = ImageRequest(
            model = model,
            prompt = prompt,
            n = count,
            size = size,
            responseFormat = null
        )
        val request = requestBuilder(settings, "images/generations")
            .post(jsonBody(json.encodeToString(ImageRequest.serializer(), payload)))
            .build()

        val direct = runCatching {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw errorFrom(response.code, body)
                json.decodeFromString(ImageResponse.serializer(), body).data
            }
        }

        direct.fold(
            onSuccess = { data ->
                ImageGenerationResult(
                    urls = data.mapNotNull { it.url },
                    base64 = data.mapNotNull { it.b64Json },
                    note = data.firstOrNull()?.revisedPrompt.orEmpty()
                )
            },
            onFailure = { error ->
                val code = (error as? MammouthException)?.httpCode
                // 404/400/501 : endpoint absent → on passe par le chat.
                if (code != null && code !in listOf(400, 404, 405, 501)) throw error
                imagesViaChat(settings, prompt, model)
            }
        )
    }

    private suspend fun imagesViaChat(
        settings: AppSettings,
        prompt: String,
        model: String
    ): ImageGenerationResult = withContext(Dispatchers.IO) {
        val payload = ChatRequest(
            model = model,
            messages = listOf(ApiMessage.text("user", prompt)),
            stream = false
        )
        val request = requestBuilder(settings, "chat/completions")
            .post(jsonBody(json.encodeToString(ChatRequest.serializer(), payload)))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw errorFrom(response.code, body)
            val text = json.decodeFromString(ChatResponse.serializer(), body)
                .choices.firstOrNull()?.message?.content.orEmpty()
            ImageGenerationResult(
                urls = MEDIA_URL_PATTERN.findAll(text).map { it.value }.distinct().toList(),
                base64 = BASE64_IMAGE_PATTERN.findAll(text).map { it.value }.toList(),
                note = text
            )
        }
    }

    /**
     * Édition d'image (image-to-image) : `POST /images/edits` en multipart ; si l'endpoint
     * n'existe pas, la demande repart en vision via `chat/completions`.
     */
    suspend fun editImage(
        settings: AppSettings,
        prompt: String,
        model: String,
        imagePath: String,
        size: String?
    ): ImageGenerationResult = withContext(Dispatchers.IO) {
        val file = java.io.File(imagePath)
        val multipart = okhttp3.MultipartBody.Builder()
            .setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("model", model)
            .addFormDataPart("prompt", prompt)
            .apply { if (!size.isNullOrBlank()) addFormDataPart("size", size) }
            .addFormDataPart(
                "image",
                file.name,
                file.asRequestBody("image/jpeg".toMediaType())
            )
            .build()

        val request = requestBuilder(settings, "images/edits").post(multipart).build()
        val direct = runCatching {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw errorFrom(response.code, body)
                json.decodeFromString(ImageResponse.serializer(), body).data
            }
        }

        direct.fold(
            onSuccess = { data ->
                ImageGenerationResult(
                    urls = data.mapNotNull { it.url },
                    base64 = data.mapNotNull { it.b64Json },
                    note = data.firstOrNull()?.revisedPrompt.orEmpty()
                )
            },
            onFailure = { error ->
                val code = (error as? MammouthException)?.httpCode
                if (code != null && code !in listOf(400, 404, 405, 501)) throw error
                val dataUrl = com.mammouthclient.app.util.FileUtils.toBase64DataUrl(imagePath)
                    ?: throw MammouthException("Image source illisible.")
                val payload = ChatRequest(
                    model = model,
                    messages = listOf(ApiMessage.multimodal("user", prompt, listOf(dataUrl))),
                    stream = false
                )
                val chatRequest = requestBuilder(settings, "chat/completions")
                    .post(jsonBody(json.encodeToString(ChatRequest.serializer(), payload)))
                    .build()
                client.newCall(chatRequest).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw errorFrom(response.code, body)
                    val text = json.decodeFromString(ChatResponse.serializer(), body)
                        .choices.firstOrNull()?.message?.content.orEmpty()
                    ImageGenerationResult(
                        urls = MEDIA_URL_PATTERN.findAll(text).map { it.value }.distinct().toList(),
                        base64 = BASE64_IMAGE_PATTERN.findAll(text).map { it.value }.toList(),
                        note = text
                    )
                }
            }
        )
    }

    /** Appel unique sans streaming (titres automatiques, utilitaires). */
    suspend fun completeOnce(
        settings: AppSettings,
        messages: List<ApiMessage>,
        model: String,
        maxTokens: Int? = null
    ): String = withContext(Dispatchers.IO) {
        val payload = ChatRequest(
            model = model,
            messages = messages,
            stream = false,
            temperature = settings.temperature,
            maxTokens = maxTokens
        )
        val request = requestBuilder(settings, "chat/completions")
            .post(jsonBody(json.encodeToString(ChatRequest.serializer(), payload)))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw errorFrom(response.code, body)
            json.decodeFromString(ChatResponse.serializer(), body)
                .choices.firstOrNull()?.message?.content.orEmpty()
        }
    }

    /** Télécharge une image distante (résultats de génération). */
    suspend fun downloadBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.bytes()
            }
        }.getOrNull()
    }

    private fun errorFrom(code: Int, body: String): MammouthException {
        val parsed = runCatching { json.decodeFromString(ApiErrorBody.serializer(), body) }.getOrNull()
        val detail = parsed?.error?.message ?: parsed?.message ?: body.take(300).ifBlank { null }
        val friendly = when (code) {
            401, 403 -> "Clé API refusée (HTTP $code). Vérifiez la clé dans les réglages."
            404 -> "Endpoint introuvable (HTTP 404). Vérifiez l'URL de base et le modèle."
            413 -> "Requête trop volumineuse (HTTP 413) : réduisez les pièces jointes."
            429 -> "Quota atteint ou trop de requêtes (HTTP 429)."
            in 500..599 -> "Erreur côté serveur Mammouth (HTTP $code)."
            else -> "Erreur HTTP $code."
        }
        return MammouthException(if (detail.isNullOrBlank()) friendly else "$friendly\n$detail", code)
    }

    companion object {
        private val MEDIA_URL_PATTERN =
            Regex(
                """https?://[^\s)"']+\.(?:png|jpe?g|webp|gif|mp4|webm|mov)(?:\?[^\s)"']*)?""",
                RegexOption.IGNORE_CASE
            )

        /** Vrai si l'URL pointe vers une vidéo (génération vidéo Mammouth). */
        fun isVideoUrl(url: String): Boolean =
            Regex("""\.(?:mp4|webm|mov)(?:\?|$)""", RegexOption.IGNORE_CASE).containsMatchIn(url)
        private val BASE64_IMAGE_PATTERN =
            Regex("""data:image/[a-zA-Z]+;base64,[A-Za-z0-9+/=]+""")

        /** Repli utilisé tant que `GET /models` n'a pas répondu. */
        val FALLBACK_MODELS = listOf(
            "mammouth-recommended",
            "gpt-4.1",
            "claude-sonnet-4-6",
            "kimi-k2.5",
            "mistral",
            "sonar-pro"
        )

        /** Modèles proposés par défaut dans l'atelier d'images. */
        val FALLBACK_IMAGE_MODELS = listOf(
            "gpt-image",
            "flux",
            "recraft",
            "stable-diffusion",
            "imagen"
        )
    }
}

data class ImageGenerationResult(
    val urls: List<String> = emptyList(),
    val base64: List<String> = emptyList(),
    val note: String = ""
) {
    val isEmpty: Boolean get() = urls.isEmpty() && base64.isEmpty()
}
