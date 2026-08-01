package com.mammouthclient.app.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* ---------- Requêtes (format OpenAI, compatible Mammouth) ---------- */

@Serializable
data class ApiMessage(
    val role: String,
    val content: String
)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ApiMessage>,
    val stream: Boolean = true,
    val temperature: Float? = null
)

/* ---------- Réponses ---------- */

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
    val choices: List<StreamChoice> = emptyList()
)

@Serializable
data class CompletionChoice(
    val index: Int = 0,
    val message: ApiMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class ChatResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<CompletionChoice> = emptyList()
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
