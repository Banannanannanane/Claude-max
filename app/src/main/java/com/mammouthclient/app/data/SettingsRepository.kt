package com.mammouthclient.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val apiKey: String = "",
    val baseUrl: String = DEFAULT_BASE_URL,
    val model: String = DEFAULT_MODEL,
    val systemPrompt: String = "",
    val temperature: Float = 0.7f,
    val streaming: Boolean = true
) {
    val hasApiKey: Boolean get() = apiKey.isNotBlank()

    companion object {
        const val DEFAULT_BASE_URL = "https://api.mammouth.ai/v1"
        const val DEFAULT_MODEL = "mammouth-recommended"
    }
}

class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("mammouth_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun read(): AppSettings = AppSettings(
        apiKey = Crypto.decrypt(prefs.getString(KEY_API, "").orEmpty()),
        baseUrl = prefs.getString(KEY_BASE_URL, AppSettings.DEFAULT_BASE_URL)
            .orEmpty()
            .ifBlank { AppSettings.DEFAULT_BASE_URL },
        model = prefs.getString(KEY_MODEL, AppSettings.DEFAULT_MODEL)
            .orEmpty()
            .ifBlank { AppSettings.DEFAULT_MODEL },
        systemPrompt = prefs.getString(KEY_SYSTEM, "").orEmpty(),
        temperature = prefs.getFloat(KEY_TEMPERATURE, 0.7f),
        streaming = prefs.getBoolean(KEY_STREAMING, true)
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settings.value)
        prefs.edit()
            .putString(KEY_API, Crypto.encrypt(updated.apiKey))
            .putString(KEY_BASE_URL, updated.baseUrl.trim().trimEnd('/'))
            .putString(KEY_MODEL, updated.model)
            .putString(KEY_SYSTEM, updated.systemPrompt)
            .putFloat(KEY_TEMPERATURE, updated.temperature)
            .putBoolean(KEY_STREAMING, updated.streaming)
            .apply()
        _settings.value = updated.copy(baseUrl = updated.baseUrl.trim().trimEnd('/'))
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
        const val KEY_STREAMING = "streaming"
    }
}
