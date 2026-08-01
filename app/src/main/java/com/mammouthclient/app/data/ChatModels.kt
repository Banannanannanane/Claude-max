package com.mammouthclient.app.data

import kotlinx.serialization.Serializable
import java.util.UUID

/** Type de pièce jointe : image (envoyée en vision) ou texte (injecté dans le prompt). */
enum class AttachmentKind { IMAGE, TEXT }

@Serializable
data class Attachment(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val mime: String = "application/octet-stream",
    /** Chemin du fichier copié dans le stockage privé de l'app. */
    val path: String = "",
    val kind: AttachmentKind = AttachmentKind.IMAGE,
    /** Contenu textuel extrait (fichiers texte / code / CSV). */
    val text: String = "",
    val sizeBytes: Long = 0L
)

@Serializable
data class TokenUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0
) {
    val isEmpty: Boolean get() = totalTokens == 0 && promptTokens == 0 && completionTokens == 0
}

@Serializable
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false,
    val attachments: List<Attachment> = emptyList(),
    /** Modèle ayant produit la réponse (affiché sous la bulle). */
    val model: String = "",
    val usage: TokenUsage? = null,
    /** Images générées (URL distante ou chemin local). */
    val images: List<String> = emptyList()
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
    val title: String = DEFAULT_TITLE,
    val model: String = AppSettings.DEFAULT_MODEL,
    val messages: List<Message> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
    val pinned: Boolean = false,
    /** Projet / assistant appliqué à cette discussion. */
    val assistantId: String? = null,
    /** Active un modèle de recherche web pour cette discussion. */
    val webSearch: Boolean = false,
    val archived: Boolean = false
) {
    /** Jetons cumulés sur la discussion. */
    val totalTokens: Int get() = messages.sumOf { it.usage?.totalTokens ?: 0 }

    fun withDerivedTitle(): Conversation {
        if (title != DEFAULT_TITLE) return this
        val first = messages.firstOrNull { it.role == Message.ROLE_USER }?.content?.trim().orEmpty()
        if (first.isBlank()) return this
        val short = first.lineSequence().first().take(48)
        return copy(title = if (first.length > 48) "$short…" else short)
    }

    companion object {
        const val DEFAULT_TITLE = "Nouvelle discussion"
    }
}

/** Média produit par l'atelier (image locale ou vidéo distante). */
@Serializable
data class GeneratedMedia(
    val id: String = UUID.randomUUID().toString(),
    val path: String = "",
    val url: String = "",
    val prompt: String = "",
    val model: String = "",
    val isVideo: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/** « Projet » Mammouth : instructions personnalisées + documents de référence. */
@Serializable
data class Assistant(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val emoji: String = "🤖",
    val instructions: String = "",
    val model: String = "",
    val documents: List<Attachment> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

/** Entrée de la bibliothèque de prompts. */
@Serializable
data class PromptTemplate(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val content: String = "",
    val category: String = "Général",
    val builtIn: Boolean = false
)

object DefaultPrompts {
    val ALL = listOf(
        PromptTemplate(
            title = "Résumer un texte",
            content = "Résume le texte suivant en 5 points clés, en français, de façon concise :\n\n",
            category = "Écriture",
            builtIn = true
        ),
        PromptTemplate(
            title = "Corriger et améliorer",
            content = "Corrige l'orthographe, la grammaire et le style du texte suivant, puis explique brièvement tes corrections :\n\n",
            category = "Écriture",
            builtIn = true
        ),
        PromptTemplate(
            title = "Traduire en anglais",
            content = "Traduis le texte suivant en anglais naturel et idiomatique :\n\n",
            category = "Écriture",
            builtIn = true
        ),
        PromptTemplate(
            title = "Expliquer ce code",
            content = "Explique ce code ligne par ligne, puis signale les bugs ou améliorations possibles :\n\n```\n\n```",
            category = "Code",
            builtIn = true
        ),
        PromptTemplate(
            title = "Écrire des tests",
            content = "Écris des tests unitaires complets pour le code suivant, en couvrant les cas limites :\n\n```\n\n```",
            category = "Code",
            builtIn = true
        ),
        PromptTemplate(
            title = "Email professionnel",
            content = "Rédige un email professionnel, poli et concis, à partir de ces éléments :\n\n",
            category = "Pro",
            builtIn = true
        ),
        PromptTemplate(
            title = "Brainstorming",
            content = "Propose 10 idées originales et concrètes sur le sujet suivant, avec pour chacune un avantage et une limite :\n\n",
            category = "Pro",
            builtIn = true
        ),
        PromptTemplate(
            title = "Analyser une image",
            content = "Décris précisément cette image, puis relève les détails importants et le contexte probable.",
            category = "Vision",
            builtIn = true
        )
    )
}
