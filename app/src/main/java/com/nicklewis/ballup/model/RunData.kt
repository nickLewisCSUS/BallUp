package com.nicklewis.ballup.model

data class Run(
    var courtId: String = "",
    var status: String = "active",
    var hostId: String = "",
    var mode: String = "5v5",
    var maxPlayers: Int = 10,
    var playerIds: List<String>? = null,   // nullable for older docs
    var playerCount: Int = 0,
    var startTime: com.google.firebase.Timestamp? = null,
    var lastHeartbeatAt: com.google.firebase.Timestamp? = null
)