package com.nicklewis.ballup.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

object FirestoreRepos {
    val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    fun startRunAtCourt(courtId: String, hostId: String) =
        db.collection("runs").add(
            mapOf(
                "courtId" to courtId,
                "status" to "active",
                "startTime" to FieldValue.serverTimestamp(),
                "hostId" to hostId,
                "mode" to "5v5",
                "maxPlayers" to 10,
                "lastHeartbeatAt" to FieldValue.serverTimestamp(),
                "playerCount" to 1,
                "playerIds" to listOf(hostId)
            )
        )
}
