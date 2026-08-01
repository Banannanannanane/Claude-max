package com.mammouthclient.app.data

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Profil de l'utilisateur : ce que l'IA doit savoir de vous et comment elle doit
 * vous répondre. Injecté en tête de chaque discussion.
 */
@Serializable
data class UserProfile(
    val enabled: Boolean = true,
    val name: String = "",
    val role: String = "",
    val language: String = "français",
    val tone: String = "",
    val expertise: String = "",
    val about: String = "",
    val instructions: String = ""
) {
    val isEmpty: Boolean
        get() = listOf(name, role, tone, expertise, about, instructions).all { it.isBlank() }

    /** Rend le profil sous forme d'instruction système. */
    fun toSystemPrompt(): String {
        if (!enabled || isEmpty) return ""
        val lines = mutableListOf<String>()
        if (name.isNotBlank()) lines += "L'utilisateur s'appelle $name."
        if (role.isNotBlank()) lines += "Son activité : $role."
        if (expertise.isNotBlank()) lines += "Son niveau / domaines : $expertise."
        if (about.isNotBlank()) lines += "À son sujet : $about"
        if (language.isNotBlank()) lines += "Réponds en $language."
        if (tone.isNotBlank()) lines += "Adopte ce ton : $tone."
        if (instructions.isNotBlank()) lines += instructions
        return lines.joinToString("\n")
    }

    companion object {
        val TONES = listOf(
            "Neutre et factuel",
            "Direct et concis",
            "Pédagogue",
            "Chaleureux",
            "Professionnel",
            "Décontracté"
        )
    }
}

/**
 * Persona : une personnalité d'IA réutilisable (ton, rôle, modèle préféré),
 * applicable à une discussion en un tap.
 */
@Serializable
data class Persona(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val emoji: String = "✨",
    val description: String = "",
    val systemPrompt: String = "",
    val model: String = "",
    val builtIn: Boolean = false
)

object DefaultPersonas {
    val ALL = listOf(
        Persona(
            id = "builtin-polyvalent",
            name = "Assistant polyvalent",
            emoji = "🧠",
            description = "Réponses claires et équilibrées sur tous les sujets.",
            systemPrompt = "Tu es un assistant polyvalent. Réponds de façon claire, structurée et " +
                "directement utile. Signale ce dont tu n'es pas certain.",
            builtIn = true
        ),
        Persona(
            id = "builtin-code",
            name = "Expert code",
            emoji = "💻",
            description = "Code idiomatique, revue critique, cas limites.",
            systemPrompt = "Tu es un ingénieur logiciel senior. Donne du code idiomatique et " +
                "commenté sobrement, signale les cas limites et les risques, et propose des tests. " +
                "Va droit au but, pas de remplissage.",
            builtIn = true
        ),
        Persona(
            id = "builtin-redacteur",
            name = "Rédacteur",
            emoji = "✍️",
            description = "Textes fluides, style soigné, réécriture.",
            systemPrompt = "Tu es un rédacteur professionnel francophone. Écris dans une langue " +
                "fluide et précise, évite les clichés et les formules creuses. Propose des " +
                "variantes quand c'est utile.",
            builtIn = true
        ),
        Persona(
            id = "builtin-traducteur",
            name = "Traducteur",
            emoji = "🌍",
            description = "Traductions naturelles et idiomatiques.",
            systemPrompt = "Tu es traducteur professionnel. Traduis de façon naturelle et " +
                "idiomatique, en conservant le registre et l'intention. Signale les ambiguïtés " +
                "en fin de réponse si nécessaire.",
            builtIn = true
        ),
        Persona(
            id = "builtin-prof",
            name = "Professeur",
            emoji = "🎓",
            description = "Explications progressives, exemples, vérification.",
            systemPrompt = "Tu es un professeur patient. Explique pas à pas, avec des exemples " +
                "concrets et des analogies. Termine par une question courte pour vérifier la " +
                "compréhension.",
            builtIn = true
        ),
        Persona(
            id = "builtin-analyste",
            name = "Analyste",
            emoji = "📊",
            description = "Synthèse, chiffres, arguments contradictoires.",
            systemPrompt = "Tu es analyste. Structure tes réponses en constats, implications et " +
                "recommandations. Distingue les faits des hypothèses et présente l'argument " +
                "contraire le plus solide.",
            builtIn = true
        ),
        Persona(
            id = "builtin-createur",
            name = "Créatif",
            emoji = "🎨",
            description = "Idées originales, angles inattendus.",
            systemPrompt = "Tu es un créatif. Propose des idées originales et variées, quitte à " +
                "sortir des sentiers battus, puis retiens les plus solides en expliquant pourquoi.",
            builtIn = true
        ),
        Persona(
            id = "builtin-coach",
            name = "Coach",
            emoji = "🧭",
            description = "Questions, plan d'action, suivi.",
            systemPrompt = "Tu es un coach. Commence par comprendre la situation avec des " +
                "questions ciblées, puis propose un plan d'action concret en étapes réalisables.",
            builtIn = true
        )
    )
}

/**
 * Espace : un jeu de réglages de connexion (clé API, URL, modèle) pour séparer par
 * exemple un usage personnel d'un usage professionnel.
 */
@Serializable
data class Workspace(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val emoji: String = "🐘",
    /** Clé chiffrée par l'AndroidKeyStore, jamais en clair. */
    val apiKeyEncrypted: String = "",
    val baseUrl: String = AppSettings.DEFAULT_BASE_URL,
    val model: String = AppSettings.DEFAULT_MODEL
)
