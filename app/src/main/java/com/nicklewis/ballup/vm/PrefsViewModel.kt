// vm/PrefsViewModel.kt
package com.nicklewis.ballup.vm

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nicklewis.ballup.data.LocalPrefsStore
import com.nicklewis.ballup.data.PrefsRepository
import com.nicklewis.ballup.data.UserPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class PrefsViewModel(
    private val cloud: PrefsRepository,
    private val local: LocalPrefsStore
) : ViewModel() {

    private val _prefs = MutableStateFlow(UserPrefs())
    val prefs: StateFlow<UserPrefs> = _prefs.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                cloud.listen(),        // emits UserPrefs(runAlerts=…)
                local.prefsFlow        // emits UserPrefs(notifyWhileForeground=…)
            ) { cloudPrefs, localPrefs ->
                cloudPrefs.copy(notifyWhileForeground = localPrefs.notifyWhileForeground)
            }.collectLatest { merged -> _prefs.value = merged }
        }
    }

    fun setRunAlerts(enabled: Boolean) = viewModelScope.launch {
        cloud.setRunAlerts(enabled)
    }

    fun setNotifyWhileForeground(enabled: Boolean) = viewModelScope.launch {
        local.setNotifyWhileForeground(enabled)
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val appCtx = context.applicationContext
                    return PrefsViewModel(
                        cloud = PrefsRepository(),
                        local = LocalPrefsStore(appCtx)
                    ) as T
                }
            }
    }
}
