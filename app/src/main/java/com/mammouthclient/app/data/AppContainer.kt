package com.mammouthclient.app.data

import android.content.Context
import com.mammouthclient.app.net.MammouthApi

/** Instances uniques partagées par tous les écrans. */
object AppContainer {

    @Volatile private var settingsRepository: SettingsRepository? = null
    @Volatile private var apiClient: MammouthApi? = null

    fun settings(context: Context): SettingsRepository =
        settingsRepository ?: synchronized(this) {
            settingsRepository ?: SettingsRepository(context.applicationContext)
                .also { settingsRepository = it }
        }

    fun api(): MammouthApi =
        apiClient ?: synchronized(this) {
            apiClient ?: MammouthApi().also { apiClient = it }
        }
}
