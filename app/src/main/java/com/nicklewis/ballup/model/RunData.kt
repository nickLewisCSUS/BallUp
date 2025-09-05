package com.nicklewis.ballup.model

import com.google.firebase.firestore.DocumentId

data class Run(
    @DocumentId var id: String? = null,
    var courtId: String? = null,
    var status: String? = null,
    var hostId: String? = null,
    var mode: String? = null,
    var maxPlayers: Int = 10,
    var playerCount: Int = 0,
    var playerIds: List<String>? = null,
    var startTime: com.google.firebase.Timestamp? = null,
    var lastHeartbeatAt: com.google.firebase.Timestamp? = null,
    var endedAt: com.google.firebase.Timestamp? = null
)