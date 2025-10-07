package com.nicklewis.ballup.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nicklewis.ballup.data.CourtLite
import com.nicklewis.ballup.data.StarRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StarsViewModel(
    private val repo: StarRepository = StarRepository()
) : ViewModel() {

    // Stream of starred IDs; empty until signed in
    val starred: StateFlow<Set<String>> =
        repo.starredIds()                 // NOTE: now a function call
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptySet()
            )

    // Keep the runAlertsEnabled param for call sites, but repo handles topics
    fun toggle(court: CourtLite, star: Boolean, runAlertsEnabled: Boolean) {
        viewModelScope.launch {
            repo.setStar(court, star)
        }
    }
}
