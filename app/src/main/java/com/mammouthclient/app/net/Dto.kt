package com.mammouthclient.app.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/* ---------- Requêtes (format OpenAI, compatible Mammouth) ---------- */

/**
 * `content` est soit une chaîne, soit un tableau de blocs (texte + images) pour les
 * modèles multimodaux — d'où le [JsonElement].
 */
@Serializable
data class ApiMessage(
    val role: String,
    val content: JsonElement
) {
    companion object {
        fun text(role: String, text: String): ApiMessage =
            ApiMessage(role, JsonPrimitive(text))

        /** Message multimodal : texte + images encodées en data-URL base64. */
        fun multimodal(role: String, text: String, imageDataUrls: List<String>): ApiMessage {
            val parts: JsonArray = buildJsonArray {
                if (text.isNotBlank()) {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", text)
                    })
                }
                imageDataUrls.forEach { url ->
                    add(buildJsonObject {
                        put("type", "image_url")
                        putJsonObject("image_url") { put("url", url) }
                    })
                }
            }
            return ApiMessage(role, parts)
        }
    }
}

@Serializable
data class StreamOptions(
    @SerialName("include_usage") val includeUsage: Boolean = true
)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ApiMessage>,
    val stream: Boolean = true,
    val temperature: Float? = null,
    @SerialName("top_p") val topP: Float? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    @SerialName("stream_options") val streamOptions: StreamOptions? = null
)

@Serializable
data class ImageRequest(
    val model: String,
    val prompt: String,
    val n: Int = 1,
    val size: String? = null,
    @SerialName("response_format") val responseFormat: String? = null
)

/* ---------- Réponses ---------- */

@Serializable
data class ApiUsage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0
)

@Serializable
data class ChatChoiceDelta(
    val role: String? = null,
    val content: String? = null
)

@Serializable
data class StreamChoice(
    val index: Int = 0,
    val delta: ChatChoiceDelta? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class StreamChunk(
    val id: String? = null,
    val model: String? = null,
    val choices: List<StreamChoice> = emptyList(),
    val usage: ApiUsage? = null
)

@Serializable
data class CompletionMessage(
    val role: String = "assistant",
    val content: String? = null
)

@Serializable
data class CompletionChoice(
    val index: Int = 0,
    val message: CompletionMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class ChatResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<CompletionChoice> = emptyList(),
    val usage: ApiUsage? = null
)

@Serializable
data class ModelEntry(
    val id: String,
    @SerialName("owned_by") val ownedBy: String? = null
)

@Serializable
data class ModelList(
    val data: List<ModelEntry> = emptyList()
)

@Serializable
data class ImageData(
    val url: String? = null,
    @SerialName("b64_json") val b64Json: String? = null,
    @SerialName("revised_prompt") val revisedPrompt: String? = null
)

@Serializable
data class ImageResponse(
    val data: List<ImageData> = emptyList()
)

@Serializable
data class ApiErrorBody(
    val error: ApiErrorDetail? = null,
    val message: String? = null
)

@Serializable
data class ApiErrorDetail(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)
