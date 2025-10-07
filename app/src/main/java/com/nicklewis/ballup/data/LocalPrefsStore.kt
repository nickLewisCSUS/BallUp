// data/LocalPrefsStore.kt
package com.nicklewis.ballup.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Top-level DataStore instance (extension on Context)
val Context.dataStore by preferencesDataStore("settings")

// Keys used in the local DataStore
object PrefKeys {
    val NOTIFY_WHILE_FOREGROUND = booleanPreferencesKey("notify_while_foreground")
}

/** Local-only prefs we keep on device. */
data class LocalPrefs(
    val notifyWhileForeground: Boolean = false
)

/** Small helper to read/write the local mirror. */
class LocalPrefsStore(private val context: Context) {

    // Emits LocalPrefs; defaults are applied if nothing stored yet
    val prefsFlow: Flow<LocalPrefs> = context.dataStore.data.map { p ->
        LocalPrefs(
            notifyWhileForeground = p[PrefKeys.NOTIFY_WHILE_FOREGROUND] ?: false
        )
    }

    suspend fun setNotifyWhileForeground(enabled: Boolean) {
        context.dataStore.edit { it[PrefKeys.NOTIFY_WHILE_FOREGROUND] = enabled }
    }
}
