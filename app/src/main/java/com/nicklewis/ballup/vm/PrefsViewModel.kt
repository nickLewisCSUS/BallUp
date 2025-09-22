package com.nicklewis.ballup.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nicklewis.ballup.data.PrefsRepository
import com.nicklewis.ballup.data.UserPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PrefsViewModel(
    private val repo: PrefsRepository = PrefsRepository()
) : ViewModel() {
    private val _prefs = MutableStateFlow(UserPrefs())
    val prefs: StateFlow<UserPrefs> = _prefs.asStateFlow()

    init {
        viewModelScope.launch { repo.listen().collect { _prefs.value = it } }
    }

    fun setRunAlerts(enabled: Boolean) = viewModelScope.launch {
        repo.setRunAlerts(enabled)
    }
}
