package com.nicklewis.ballup.model

import com.google.firebase.Timestamp

data class Run(
    var courtId: String? = null,
    var status: String = "active", // "active" | "scheduled" | "ended"
    var startTime: Timestamp? = null,   // kept for BC (was set at start)
    var startsAt: Timestamp? = null,    // NEW: canonical start timestamp
    var endsAt: Timestamp? = null,      // NEW: optional end timestamp
    var hostId: String? = null,
    var mode: String = "5v5",
    var maxPlayers: Int = 10,
    var lastHeartbeatAt: Timestamp? = null,
    var playerCount: Int = 0,
    var playerIds: List<String>? = emptyList(),
    var createdAt: Timestamp? = null    // NEW: when doc created
)
