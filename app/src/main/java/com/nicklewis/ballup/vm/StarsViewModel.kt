package com.nicklewis.ballup.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nicklewis.ballup.data.CourtLite
import com.nicklewis.ballup.data.StarRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StarsViewModel(
    private val repo: StarRepository = StarRepository()
) : ViewModel() {

    private val _starred = MutableStateFlow<Set<String>>(emptySet())
    val starred: StateFlow<Set<String>> = _starred.asStateFlow()

    init {
        viewModelScope.launch {
            repo.starredIds().collect { _starred.value = it }
        }
    }

    fun toggle(court: CourtLite, newValue: Boolean) {
        // optimistic UI
        val before = _starred.value
        _starred.value = if (newValue) before + court.id else before - court.id

        viewModelScope.launch {
            try { repo.setStar(court, newValue) }
            catch (e: Exception) { _starred.value = before } // rollback on failure
        }
    }
}
