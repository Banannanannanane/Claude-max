package com.mammouthclient.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val apiKey: String = "",
    val baseUrl: String = DEFAULT_BASE_URL,
    val model: String = DEFAULT_MODEL,
    val systemPrompt: String = "",
    val temperature: Float = 0.7f,
    val topP: Float = 1f,
    val maxTokens: Int = 0,
    val streaming: Boolean = true,
    /** Modèle utilisé par l'atelier d'images. */
    val imageModel: String = DEFAULT_IMAGE_MODEL,
    /** Modèle utilisé quand la recherche web est activée. */
    val searchModel: String = DEFAULT_SEARCH_MODEL,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fontScale: Float = 1f,
    val showUsage: Boolean = true,
    val sendOnEnter: Boolean = false,
    /** Modèles épinglés en haut du sélecteur. */
    val favoriteModels: Set<String> = emptySet(),
    /** Demande le code/empreinte de l'appareil au lancement. */
    val appLock: Boolean = false,
    /** Fait nommer la discussion par le modèle après le premier échange. */
    val autoTitle: Boolean = true,
    /** Ouvre directement l'app web au lancement (usage sur abonnement, sans clé API). */
    val startOnWeb: Boolean = false,
    /** Espaces de connexion (perso, pro…). */
    val workspaces: List<Workspace> = emptyList(),
    val activeWorkspaceId: String = ""
) {
    val hasApiKey: Boolean get() = apiKey.isNotBlank()

    companion object {
        const val DEFAULT_BASE_URL = "https://api.mammouth.ai/v1"
        const val DEFAULT_MODEL = "mammouth-recommended"
        const val DEFAULT_IMAGE_MODEL = "gpt-image"
        const val DEFAULT_SEARCH_MODEL = "sonar-pro"
    }
}

class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("mammouth_settings", Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun read(): AppSettings = AppSettings(
        apiKey = Crypto.decrypt(prefs.getString(KEY_API, "").orEmpty()),
        baseUrl = prefs.getString(KEY_BASE_URL, AppSettings.DEFAULT_BASE_URL)
            .orEmpty().ifBlank { AppSettings.DEFAULT_BASE_URL },
        model = prefs.getString(KEY_MODEL, AppSettings.DEFAULT_MODEL)
            .orEmpty().ifBlank { AppSettings.DEFAULT_MODEL },
        systemPrompt = prefs.getString(KEY_SYSTEM, "").orEmpty(),
        temperature = prefs.getFloat(KEY_TEMPERATURE, 0.7f),
        topP = prefs.getFloat(KEY_TOP_P, 1f),
        maxTokens = prefs.getInt(KEY_MAX_TOKENS, 0),
        streaming = prefs.getBoolean(KEY_STREAMING, true),
        imageModel = prefs.getString(KEY_IMAGE_MODEL, AppSettings.DEFAULT_IMAGE_MODEL)
            .orEmpty().ifBlank { AppSettings.DEFAULT_IMAGE_MODEL },
        searchModel = prefs.getString(KEY_SEARCH_MODEL, AppSettings.DEFAULT_SEARCH_MODEL)
            .orEmpty().ifBlank { AppSettings.DEFAULT_SEARCH_MODEL },
        themeMode = runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name).orEmpty())
        }.getOrDefault(ThemeMode.SYSTEM),
        fontScale = prefs.getFloat(KEY_FONT_SCALE, 1f),
        showUsage = prefs.getBoolean(KEY_SHOW_USAGE, true),
        sendOnEnter = prefs.getBoolean(KEY_SEND_ON_ENTER, false),
        favoriteModels = prefs.getStringSet(KEY_FAVORITES, emptySet()).orEmpty().toSet(),
        appLock = prefs.getBoolean(KEY_APP_LOCK, false),
        autoTitle = prefs.getBoolean(KEY_AUTO_TITLE, true),
        startOnWeb = prefs.getBoolean(KEY_START_ON_WEB, false),
        workspaces = runCatching {
            json.decodeFromString(
                ListSerializer(Workspace.serializer()),
                prefs.getString(KEY_WORKSPACES, "[]").orEmpty().ifBlank { "[]" }
            )
        }.getOrDefault(emptyList()),
        activeWorkspaceId = prefs.getString(KEY_ACTIVE_WORKSPACE, "").orEmpty()
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settings.value).let {
            it.copy(baseUrl = it.baseUrl.trim().trimEnd('/').ifBlank { AppSettings.DEFAULT_BASE_URL })
        }
        prefs.edit()
            .putString(KEY_API, Crypto.encrypt(updated.apiKey))
            .putString(KEY_BASE_URL, updated.baseUrl)
            .putString(KEY_MODEL, updated.model)
            .putString(KEY_SYSTEM, updated.systemPrompt)
            .putFloat(KEY_TEMPERATURE, updated.temperature)
            .putFloat(KEY_TOP_P, updated.topP)
            .putInt(KEY_MAX_TOKENS, updated.maxTokens)
            .putBoolean(KEY_STREAMING, updated.streaming)
            .putString(KEY_IMAGE_MODEL, updated.imageModel)
            .putString(KEY_SEARCH_MODEL, updated.searchModel)
            .putString(KEY_THEME, updated.themeMode.name)
            .putFloat(KEY_FONT_SCALE, updated.fontScale)
            .putBoolean(KEY_SHOW_USAGE, updated.showUsage)
            .putBoolean(KEY_SEND_ON_ENTER, updated.sendOnEnter)
            .putStringSet(KEY_FAVORITES, updated.favoriteModels)
            .putBoolean(KEY_APP_LOCK, updated.appLock)
            .putBoolean(KEY_AUTO_TITLE, updated.autoTitle)
            .putBoolean(KEY_START_ON_WEB, updated.startOnWeb)
            .putString(
                KEY_WORKSPACES,
                runCatching {
                    json.encodeToString(ListSerializer(Workspace.serializer()), updated.workspaces)
                }.getOrDefault("[]")
            )
            .putString(KEY_ACTIVE_WORKSPACE, updated.activeWorkspaceId)
            .apply()
        _settings.value = updated
    }

    /* ---------------- Espaces ---------------- */

    /** Crée ou met à jour un espace à partir des réglages de connexion courants. */
    fun saveWorkspace(name: String, emoji: String, id: String? = null) {
        update { current ->
            val workspace = Workspace(
                id = id ?: java.util.UUID.randomUUID().toString(),
                name = name.trim(),
                emoji = emoji.ifBlank { "🐘" },
                apiKeyEncrypted = Crypto.encrypt(current.apiKey),
                baseUrl = current.baseUrl,
                model = current.model
            )
            val others = current.workspaces.filterNot { it.id == workspace.id }
            current.copy(
                workspaces = others + workspace,
                activeWorkspaceId = workspace.id
            )
        }
    }

    /** Bascule vers un espace : sauvegarde l'état courant puis applique le sien. */
    fun switchWorkspace(id: String) {
        update { current ->
            val target = current.workspaces.firstOrNull { it.id == id } ?: return@update current
            val saved = current.workspaces.map { workspace ->
                if (workspace.id == current.activeWorkspaceId) {
                    workspace.copy(
                        apiKeyEncrypted = Crypto.encrypt(current.apiKey),
                        baseUrl = current.baseUrl,
                        model = current.model
                    )
                } else {
                    workspace
                }
            }
            current.copy(
                workspaces = saved,
                activeWorkspaceId = target.id,
                apiKey = Crypto.decrypt(target.apiKeyEncrypted),
                baseUrl = target.baseUrl,
                model = target.model
            )
        }
    }

    fun deleteWorkspace(id: String) {
        update { current ->
            current.copy(
                workspaces = current.workspaces.filterNot { it.id == id },
                activeWorkspaceId = if (current.activeWorkspaceId == id) "" else current.activeWorkspaceId
            )
        }
    }

    fun clearAll() {
        prefs.edit().clear().apply()
        _settings.value = AppSettings()
    }

    private companion object {
        const val KEY_API = "api_key_enc"
        const val KEY_BASE_URL = "base_url"
        const val KEY_MODEL = "model"
        const val KEY_SYSTEM = "system_prompt"
        const val KEY_TEMPERATURE = "temperature"
        const val KEY_TOP_P = "top_p"
        const val KEY_MAX_TOKENS = "max_tokens"
        const val KEY_STREAMING = "streaming"
        const val KEY_IMAGE_MODEL = "image_model"
        const val KEY_SEARCH_MODEL = "search_model"
        const val KEY_THEME = "theme_mode"
        const val KEY_FONT_SCALE = "font_scale"
        const val KEY_SHOW_USAGE = "show_usage"
        const val KEY_SEND_ON_ENTER = "send_on_enter"
        const val KEY_FAVORITES = "favorite_models"
        const val KEY_APP_LOCK = "app_lock"
        const val KEY_AUTO_TITLE = "auto_title"
        const val KEY_START_ON_WEB = "start_on_web"
        const val KEY_WORKSPACES = "workspaces"
        const val KEY_ACTIVE_WORKSPACE = "active_workspace"
    }
}
