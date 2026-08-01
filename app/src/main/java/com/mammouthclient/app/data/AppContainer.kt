package com.mammouthclient.app.data

import android.content.Context

/** Fournit une instance unique du dépôt de réglages, partagée par tous les écrans. */
object AppContainer {

    @Volatile
    private var settingsRepository: SettingsRepository? = null

    fun settings(context: Context): SettingsRepository =
        settingsRepository ?: synchronized(this) {
            settingsRepository ?: SettingsRepository(context.applicationContext)
                .also { settingsRepository = it }
        }
}
