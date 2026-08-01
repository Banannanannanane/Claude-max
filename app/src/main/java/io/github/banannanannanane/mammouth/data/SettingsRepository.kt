package io.github.banannanannanane.mammouth.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    val apiKey: String = "",
    val baseUrl: String = DEFAULT_BASE_URL,
    val model: String = DEFAULT_MODEL,
    val systemPrompt: String = "",
    val temperature: Float = 0.7f,
    val streaming: Boolean = true,
) {
    val hasApiKey: Boolean get() = apiKey.isNotBlank()

    companion object {
        const val DEFAULT_BASE_URL = "https://api.mammouth.ai/v1"
        const val DEFAULT_MODEL = "gpt-4.1"
    }
}

class SettingsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val secureStore = SecureStore(appContext)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private fun load(): Settings = Settings(
        apiKey = secureStore.get(SecureStore.KEY_API_KEY).orEmpty(),
        baseUrl = prefs.getString(KEY_BASE_URL, null)?.takeIf { it.isNotBlank() }
            ?: Settings.DEFAULT_BASE_URL,
        model = prefs.getString(KEY_MODEL, null)?.takeIf { it.isNotBlank() }
            ?: Settings.DEFAULT_MODEL,
        systemPrompt = prefs.getString(KEY_SYSTEM_PROMPT, "").orEmpty(),
        temperature = prefs.getFloat(KEY_TEMPERATURE, 0.7f),
        streaming = prefs.getBoolean(KEY_STREAMING, true),
    )

    fun setApiKey(value: String) {
        val trimmed = value.trim()
        secureStore.put(SecureStore.KEY_API_KEY, trimmed)
        _settings.value = _settings.value.copy(apiKey = trimmed)
    }

    fun setBaseUrl(value: String) {
        val normalized = value.trim().trimEnd('/').ifBlank { Settings.DEFAULT_BASE_URL }
        prefs.edit().putString(KEY_BASE_URL, normalized).apply()
        _settings.value = _settings.value.copy(baseUrl = normalized)
    }

    fun setModel(value: String) {
        prefs.edit().putString(KEY_MODEL, value).apply()
        _settings.value = _settings.value.copy(model = value)
    }

    fun setSystemPrompt(value: String) {
        prefs.edit().putString(KEY_SYSTEM_PROMPT, value).apply()
        _settings.value = _settings.value.copy(systemPrompt = value)
    }

    fun setTemperature(value: Float) {
        prefs.edit().putFloat(KEY_TEMPERATURE, value).apply()
        _settings.value = _settings.value.copy(temperature = value)
    }

    fun setStreaming(value: Boolean) {
        prefs.edit().putBoolean(KEY_STREAMING, value).apply()
        _settings.value = _settings.value.copy(streaming = value)
    }

    private companion object {
        const val PREFS_NAME = "mammouth_settings"
        const val KEY_BASE_URL = "base_url"
        const val KEY_MODEL = "model"
        const val KEY_SYSTEM_PROMPT = "system_prompt"
        const val KEY_TEMPERATURE = "temperature"
        const val KEY_STREAMING = "streaming"
    }
}
