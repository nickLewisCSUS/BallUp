package com.nicklewis.ballup.model

import com.google.firebase.Timestamp

data class Run(
    var courtId: String? = null,
    var status: String = "active",
    var startTime: Timestamp? = null,
    var startsAt: Timestamp? = null,
    var endsAt: Timestamp? = null,
    var hostId: String? = null,
    var mode: String = "5v5",
    var maxPlayers: Int = 10,
    var lastHeartbeatAt: Timestamp? = null,
    var playerCount: Int = 0,
    var playerIds: List<String>? = emptyList(),
    var createdAt: Timestamp? = null,
    var name: String = "",
    val access: String = RunAccess.OPEN.name,
    val allowedUids: List<String> = emptyList(),
    val pendingJoinsCount: Int = 0,
)