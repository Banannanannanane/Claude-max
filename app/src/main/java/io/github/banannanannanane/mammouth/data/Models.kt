package io.github.banannanannanane.mammouth.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class Role {
    USER,
    ASSISTANT
}

@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val content: String,
    val model: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** Set when the assistant turn failed; [content] then holds whatever was streamed before the failure. */
    val error: String? = null,
) {
    val apiRole: String get() = if (role == Role.USER) "user" else "assistant"
}

@Serializable
data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val messages: List<ChatMessage> = emptyList(),
    /** Model used for this conversation; falls back to the global setting when null. */
    val model: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/** A model entry as advertised by the Mammouth API. */
data class ModelInfo(
    val id: String,
    val name: String = id,
    val provider: String? = null,
    val description: String? = null,
    val contextWindow: Int? = null,
)
