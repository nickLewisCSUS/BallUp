package com.nicklewis.ballup.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// 1) Top-level DataStore instance
val Context.dataStore by preferencesDataStore("settings")

// 2) Keys used in the local DataStore
object PrefKeys {
    val RUN_ALERTS = booleanPreferencesKey("run_alerts")
    val NOTIFY_WHILE_FOREGROUND = booleanPreferencesKey("notify_while_foreground")
}

// 3) Small helper to read/write the local mirror
class LocalPrefsStore(private val context: Context) {

    val prefsFlow: Flow<UserPrefs> = context.dataStore.data.map { p ->
        UserPrefs(
            runAlerts = p[PrefKeys.RUN_ALERTS] ?: true,
            notifyWhileForeground = p[PrefKeys.NOTIFY_WHILE_FOREGROUND] ?: false,
        )
    }

    suspend fun setRunAlerts(enabled: Boolean) {
        context.dataStore.edit { it[PrefKeys.RUN_ALERTS] = enabled }
    }

    suspend fun setNotifyWhileForeground(enabled: Boolean) {
        context.dataStore.edit { it[PrefKeys.NOTIFY_WHILE_FOREGROUND] = enabled }
    }
}
