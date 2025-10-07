// vm/PrefsViewModel.kt
package com.nicklewis.ballup.vm

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.nicklewis.ballup.data.LocalPrefsStore
import com.nicklewis.ballup.data.PrefsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class Prefs(
    val runAlerts: Boolean,
    val notifyWhileForeground: Boolean
)

class PrefsViewModel(
    private val repo: PrefsRepository,
    private val local: LocalPrefsStore
) : ViewModel() {

    private val notifyFgFlow: Flow<Boolean> =
        local.prefsFlow.map { it.notifyWhileForeground }

    val prefs: StateFlow<Prefs> = combine(
        repo.cloudPrefs,     // Flow<CloudPrefs>
        notifyFgFlow         // Flow<Boolean>
    ) { cloud, notifyFg ->
        Prefs(
            runAlerts = cloud.runAlerts,
            notifyWhileForeground = notifyFg
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        Prefs(runAlerts = true, notifyWhileForeground = false)
    )

    fun setRunAlerts(enabled: Boolean) {
        viewModelScope.launch { repo.setRunAlerts(enabled) }
    }

    fun setNotifyWhileForeground(enabled: Boolean) {
        viewModelScope.launch { local.setNotifyWhileForeground(enabled) }
    }

    companion object {
        fun factory(ctx: Context) = viewModelFactory {
            initializer {
                PrefsViewModel(
                    repo = PrefsRepository(),
                    local = LocalPrefsStore(ctx.applicationContext)
                )
            }
        }
    }
}
