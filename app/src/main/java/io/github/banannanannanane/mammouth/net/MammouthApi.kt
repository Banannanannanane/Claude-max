package io.github.banannanannanane.mammouth.net

import io.github.banannanannanane.mammouth.data.ChatMessage
import io.github.banannanannanane.mammouth.data.ModelInfo
import io.github.banannanannanane.mammouth.data.Role
import io.github.banannanannanane.mammouth.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Thrown when the API answers with a non-2xx status. */
class ApiException(
    val statusCode: Int,
    val apiMessage: String?,
) : IOException(apiMessage ?: "HTTP $statusCode")

/**
 * Minimal client for the OpenAI-compatible Mammouth API.
 *
 * Chat completions: `POST {baseUrl}/chat/completions` with a `Bearer` token.
 * Models: `GET {baseUrl}/models`, falling back to the public catalogue at
 * [PUBLIC_MODELS_URL] which needs no credentials.
 */
class MammouthApi(
    private val client: OkHttpClient = defaultClient(),
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Sends [messages] and reports generated text through [onDelta].
     *
     * With `stream = true` the response is consumed as server-sent events, so
     * [onDelta] fires many times; otherwise it fires once with the full answer.
     * Cancelling the calling coroutine cancels the HTTP call.
     */
    suspend fun chat(
        settings: Settings,
        messages: List<ChatMessage>,
        model: String,
        stream: Boolean = settings.streaming,
        maxTokens: Int? = null,
        onDelta: (String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        require(settings.apiKey.isNotBlank()) { "missing API key" }

        val payload = buildJsonObject {
            put("model", model)
            put("stream", stream)
            put("temperature", settings.temperature.toDouble())
            if (maxTokens != null) put("max_tokens", maxTokens)
            put("messages", buildJsonArray {
                if (settings.systemPrompt.isNotBlank()) {
                    add(buildJsonObject {
                        put("role", "system")
                        put("content", settings.systemPrompt)
                    })
                }
                messages.forEach { message ->
                    add(buildJsonObject {
                        put("role", message.apiRole)
                        put("content", message.content)
                    })
                }
            })
        }

        val request = Request.Builder()
            .url("${settings.baseUrl.trimEnd('/')}/chat/completions")
            .addHeader("Authorization", "Bearer ${settings.apiKey}")
            .addHeader("Accept", if (stream) "text/event-stream" else "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val call = client.newCall(request)
        // Propagate coroutine cancellation to the socket: the blocking read below
        // then fails fast instead of waiting for the server.
        val cancellation = coroutineContext[Job]?.invokeOnCompletion { call.cancel() }

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw ApiException(response.code, extractErrorMessage(response.body?.string()))
                }
                val source = response.body?.source() ?: throw IOException("empty response")

                if (!stream) {
                    val body = source.readUtf8()
                    val content = json.parseToJsonElement(body).jsonObject["choices"]
                        ?.jsonArray?.firstOrNull()?.jsonObject
                        ?.get("message")?.jsonObject
                        ?.get("content")?.jsonPrimitive?.contentOrNull
                        .orEmpty()
                    if (content.isNotEmpty()) onDelta(content)
                    return@use
                }

                while (isActive) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isBlank() || line.startsWith(":")) continue
                    if (!line.startsWith("data:")) continue

                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break

                    val delta = runCatching { parseStreamDelta(data) }.getOrNull()
                    if (!delta.isNullOrEmpty()) onDelta(delta)
                }
            }
        } finally {
            cancellation?.dispose()
        }
    }

    /**
     * Round-trips one throwaway completion to confirm the key, base URL and
     * model actually work together. Throws [ApiException] when they do not.
     */
    suspend fun checkCredentials(settings: Settings, model: String) {
        chat(
            settings = settings,
            messages = listOf(ChatMessage(role = Role.USER, content = "ping")),
            model = model,
            stream = false,
            maxTokens = 1,
            onDelta = {},
        )
    }

    /** Fetches the model catalogue, preferring the authenticated endpoint. */
    suspend fun listModels(settings: Settings): List<ModelInfo> = withContext(Dispatchers.IO) {
        val authenticated = if (settings.hasApiKey) {
            runCatching {
                fetchModels("${settings.baseUrl.trimEnd('/')}/models", settings.apiKey)
            }.getOrNull()
        } else {
            null
        }
        if (!authenticated.isNullOrEmpty()) return@withContext authenticated

        runCatching { fetchModels(PUBLIC_MODELS_URL, null) }
            .getOrElse { emptyList() }
            .ifEmpty { FALLBACK_MODELS }
    }

    private fun fetchModels(url: String, apiKey: String?): List<ModelInfo> {
        val request = Request.Builder()
            .url(url)
            .addHeader("Accept", "application/json")
            .apply { if (!apiKey.isNullOrBlank()) addHeader("Authorization", "Bearer $apiKey") }
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw ApiException(response.code, extractErrorMessage(response.body?.string()))
            }
            val body = response.body?.string().orEmpty()
            return ModelCatalog.parse(json.parseToJsonElement(body))
        }
    }

    private fun parseStreamDelta(data: String): String? {
        val choice = json.parseToJsonElement(data).jsonObject["choices"]
            ?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        val delta = choice["delta"]?.jsonObject ?: choice["message"]?.jsonObject ?: return null
        return delta["content"]?.jsonPrimitive?.contentOrNull
    }

    private fun extractErrorMessage(body: String?): String? {
        if (body.isNullOrBlank()) return null
        val parsed = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return body.take(300)
        val obj = parsed as? JsonObject ?: return body.take(300)
        val error = obj["error"]
        val message = when (error) {
            is JsonObject -> error["message"]?.jsonPrimitive?.contentOrNull
            is JsonPrimitive -> error.contentOrNull
            else -> null
        } ?: obj["message"]?.jsonPrimitive?.contentOrNull
        return message ?: body.take(300)
    }

    companion object {
        const val PUBLIC_MODELS_URL = "https://api.mammouth.ai/public/models"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /** Used only when both catalogue endpoints are unreachable. */
        val FALLBACK_MODELS = listOf(
            ModelInfo("mammouth-recommended", "Mammouth (recommandé)"),
            ModelInfo("gpt-4.1", "GPT-4.1", provider = "OpenAI"),
            ModelInfo("claude-sonnet-4-6", "Claude Sonnet 4.6", provider = "Anthropic"),
            ModelInfo("gemini-2.5-flash", "Gemini 2.5 Flash", provider = "Google"),
        )

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            // Streaming responses stay open between tokens.
            .readTimeout(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
