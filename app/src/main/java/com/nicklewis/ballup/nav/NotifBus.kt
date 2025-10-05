package com.nicklewis.ballup.nav

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object NotifBus {
    private val _events = MutableSharedFlow<InAppAlert>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()
    fun emit(a: InAppAlert) { _events.tryEmit(a) }
}

sealed class InAppAlert {
    data class RunSpots(val title: String, val subtitle: String, val runId: String) : InAppAlert()
}