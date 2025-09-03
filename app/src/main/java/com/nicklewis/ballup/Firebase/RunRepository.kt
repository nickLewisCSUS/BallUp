package com.nicklewis.ballup.firestore

import com.google.firebase.firestore.FirebaseFirestore
import com.nicklewis.ballup.model.Run

suspend fun joinRun(db: FirebaseFirestore, runId: String, uid: String) {
    db.runTransaction { tx ->
        val ref = db.collection("runs").document(runId)
        val snap = tx.get(ref)
        val run = snap.toObject(Run::class.java) ?: throw Exception("Run missing")

        val current = run.playerIds ?: emptyList()
        if (uid in current) throw Exception("Already joined")
        if (run.playerCount >= run.maxPlayers) throw Exception("Full")

        val newList = current + uid
        tx.update(ref, mapOf(
            "playerIds" to newList,
            "playerCount" to run.playerCount + 1
        ))
    }
}

suspend fun leaveRun(db: FirebaseFirestore, runId: String, uid: String) {
    db.runTransaction { tx ->
        val ref = db.collection("runs").document(runId)
        val snap = tx.get(ref)
        val run = snap.toObject(Run::class.java) ?: return@runTransaction

        val current = run.playerIds ?: emptyList()
        if (uid !in current) return@runTransaction

        val newList = current.filterNot { it == uid }
        tx.update(ref, mapOf(
            "playerIds" to newList,
            "playerCount" to (run.playerCount - 1).coerceAtLeast(0)
        ))
    }
}
