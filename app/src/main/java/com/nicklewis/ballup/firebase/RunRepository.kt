package com.nicklewis.ballup.firebase

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.nicklewis.ballup.model.Run
import kotlinx.coroutines.tasks.await

/**
 * Transactional JOIN: prevents double-join and overfilling.
 * Throws IllegalStateException with a user-friendly message you can surface in UI.
 */
suspend fun joinRun(db: FirebaseFirestore, runId: String, uid: String) {
    db.runTransaction { tx ->
        val ref  = db.collection("runs").document(runId)
        val snap = tx.get(ref)
        val run  = snap.toObject(Run::class.java) ?: throw IllegalStateException("Run not found")

        // basic guards
        if (run.status != "active") throw IllegalStateException("This run has ended")
        val max = run.maxPlayers.takeIf { it > 0 } ?: 10

        val current = (run.playerIds ?: emptyList()).toMutableList()
        if (uid in current) return@runTransaction null
        if (current.size >= max) throw IllegalStateException("Run full")

        current += uid
        tx.update(ref, mapOf(
            "playerIds" to current,
            "playerCount" to current.size,
            "lastHeartbeatAt" to FieldValue.serverTimestamp()
        ))
        null
    }.await()
}

/**
 * Transactional LEAVE: safely removes a user and keeps counts in sync.
 */
suspend fun leaveRun(db: FirebaseFirestore, runId: String, uid: String) {
    db.runTransaction { tx ->
        val ref  = db.collection("runs").document(runId)
        val snap = tx.get(ref)
        val run  = snap.toObject(Run::class.java) ?: return@runTransaction null

        val current = (run.playerIds ?: emptyList()).toMutableList()
        if (!current.remove(uid)) return@runTransaction null

        tx.update(ref, mapOf(
            "playerIds" to current,
            "playerCount" to current.size.coerceAtLeast(0),
            "lastHeartbeatAt" to FieldValue.serverTimestamp()
        ))
        null
    }.await()
}

suspend fun endRun(db: FirebaseFirestore, runId: String, requesterUid: String) {
    db.runTransaction { tx ->
        val ref = db.collection("runs").document(runId)
        val snap = tx.get(ref)
        val run = snap.toObject(Run::class.java) ?: throw IllegalStateException("Run not found")
        if (run.hostId != requesterUid) throw IllegalStateException("Only the host can end the run")
        if (run.status != "active") return@runTransaction null

        tx.update(ref, mapOf(
            "status" to "ended",
            "endedAt" to FieldValue.serverTimestamp(),
            "lastHeartbeatAt" to FieldValue.serverTimestamp()
        ))
        null
    }.await()
}

suspend fun updateMaxPlayers(db: FirebaseFirestore, runId: String, requesterUid: String, newMax: Int) {
    require(newMax in 2..30) { "Max players must be between 2 and 30" }
    db.runTransaction { tx ->
        val ref = db.collection("runs").document(runId)
        val snap = tx.get(ref)
        val run = snap.toObject(Run::class.java) ?: throw IllegalStateException("Run not found")
        if (run.hostId != requesterUid) throw IllegalStateException("Only host can change capacity")
        val currentCount = run.playerIds?.size ?: run.playerCount
        if (newMax < currentCount) throw IllegalStateException("New max can’t be less than current players")

        tx.update(ref, mapOf(
            "maxPlayers" to newMax,
            "lastHeartbeatAt" to FieldValue.serverTimestamp()
        ))
        null
    }.await()
}

suspend fun updateMode(db: FirebaseFirestore, runId: String, requesterUid: String, mode: String) {
    // e.g. "3v3","4v4","5v5"
    require(mode in listOf("3v3","4v4","5v5")) { "Unsupported mode" }
    db.runTransaction { tx ->
        val ref = db.collection("runs").document(runId)
        val snap = tx.get(ref)
        val run = snap.toObject(Run::class.java) ?: throw IllegalStateException("Run not found")
        if (run.hostId != requesterUid) throw IllegalStateException("Only host can change mode")
        if (run.status != "active") throw IllegalStateException("Run not active")

        tx.update(ref, mapOf(
            "mode" to mode,
            "lastHeartbeatAt" to FieldValue.serverTimestamp()
        ))
        null
    }.await()
}

/** Optional: host kicks a player */
suspend fun kickPlayer(db: FirebaseFirestore, runId: String, requesterUid: String, targetUid: String) {
    db.runTransaction { tx ->
        val ref = db.collection("runs").document(runId)
        val snap = tx.get(ref)
        val run = snap.toObject(Run::class.java) ?: throw IllegalStateException("Run not found")
        if (run.hostId != requesterUid) throw IllegalStateException("Only host can kick players")
        val current = (run.playerIds ?: emptyList()).toMutableList()
        if (!current.remove(targetUid)) return@runTransaction null

        tx.update(ref, mapOf(
            "playerIds" to current,
            "playerCount" to current.size,
            "lastHeartbeatAt" to FieldValue.serverTimestamp()
        ))
        null
    }.await()
}