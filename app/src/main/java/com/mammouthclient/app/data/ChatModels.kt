package com.mammouthclient.app.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val ROLE_SYSTEM = "system"
    }
}

@Serializable
data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Nouvelle discussion",
    val model: String = AppSettings.DEFAULT_MODEL,
    val messages: List<Message> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /** Titre dérivé du premier message utilisateur. */
    fun withDerivedTitle(): Conversation {
        if (title != "Nouvelle discussion") return this
        val first = messages.firstOrNull { it.role == Message.ROLE_USER }?.content?.trim().orEmpty()
        if (first.isBlank()) return this
        val short = first.lineSequence().first().take(48)
        return copy(title = if (first.length > 48) "$short…" else short)
    }
}
