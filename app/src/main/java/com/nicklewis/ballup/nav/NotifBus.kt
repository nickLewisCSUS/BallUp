package com.nicklewis.ballup.nav

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object NotifBus {
    private val _events = MutableSharedFlow<InAppAlert>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()
    fun emit(a: InAppAlert) { _events.tryEmit(a) }
}

// Add RunCreated alongside RunSpots
sealed class InAppAlert {
    data class RunSpots(
        val title: String,
        val subtitle: String,
        val runId: String
    ) : InAppAlert()

    data class RunCreated(
        val title: String,
        val courtName: String,
        val runId: String,
        val timeText: String
    ) : InAppAlert()

    data class RunUpcoming(
        val title: String,
        val courtName: String,
        val runId: String,
        val minutes: Int
    ) : InAppAlert()

    data class RunCancelled(
        val title: String,
        val courtName: String,
        val runId: String
    ) : InAppAlert()

    data class TeamInvite(
        val title: String,
        val teamName: String,
        val teamId: String,
        val inviteId: String
    ) : InAppAlert()

    data class TeamInviteAccepted(
        val title: String,
        val message: String,
        val teamId: String
    ) : InAppAlert()

    data class TeamJoinRequest(
        val title: String,
        val teamName: String,
        val requesterName: String,
        val teamId: String
    ) : InAppAlert()

    data class TeamJoinApproved(
        val title: String,
        val message: String,
        val teamId: String
    ) : InAppAlert()

    data class TeamDeleted(
        val title: String,
        val message: String
    ) : InAppAlert()

    data class RunInvite(
        val title: String,
        val courtName: String,
        val runId: String
    ) : InAppAlert()
}
