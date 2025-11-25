package com.nicklewis.ballup.firebase

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.nicklewis.ballup.model.Run
import kotlinx.coroutines.tasks.await

/** Creates a run; if startsAtMillis is in the future, status = "scheduled". */
suspend fun startRun(
    db: FirebaseFirestore,
    courtId: String,
    hostUid: String,
    mode: String = "5v5",
    maxPlayers: Int = 10,
    startsAtMillis: Long? = null,
    endsAtMillis: Long? = null
): String {
    require(maxPlayers in 2..30) { "Max players must be between 2 and 30" }

    val ref = db.collection("runs").document()

    val now = System.currentTimeMillis()
    val startsAt = startsAtMillis?.let { com.google.firebase.Timestamp(it / 1000, ((it % 1000) * 1_000_000).toInt()) }
    val endsAt   = endsAtMillis?.let   { com.google.firebase.Timestamp(it / 1000, ((it % 1000) * 1_000_000).toInt()) }

    val isFuture = startsAtMillis != null && startsAtMillis > now
    val status = if (isFuture) "scheduled" else "active"

    val data = mutableMapOf<String, Any?>(
        "runId" to ref.id,
        "courtId" to courtId,
        "status" to status,
        "startTime" to FieldValue.serverTimestamp(), // keep writing for BC
        "startsAt" to (startsAt ?: FieldValue.serverTimestamp()),
        "endsAt" to endsAt,
        "hostId" to hostUid,
        "mode" to mode,
        "maxPlayers" to maxPlayers,
        "createdAt" to FieldValue.serverTimestamp(),
        "lastHeartbeatAt" to FieldValue.serverTimestamp(),
        "playerCount" to 1,
        "playerIds" to listOf(hostUid)
    )

    // Remove nulls so Firestore doesn’t store them
    val cleaned = data.filterValues { it != null }
    ref.set(cleaned).await()
    return ref.id
}

/** Transactional JOIN: prevents double-join and overfilling. */
suspend fun joinRun(db: FirebaseFirestore, runId: String, uid: String) {
    db.runTransaction { tx ->
        val ref  = db.collection("runs").document(runId)
        val snap = tx.get(ref)
        val run  = snap.toObject(Run::class.java) ?: throw IllegalStateException("Run not found")

        if (run.status !in listOf("active", "scheduled")) {
            throw IllegalStateException("This run has ended")
        }
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
 * Leaves the run. Rules:
 * - If a non-host leaves: just remove them from playerIds and decrement playerCount.
 * - If the host leaves and there are other players:
 *      -> transfer host to the next player and remove the old host.
 * - If the host is the only player:
 *      -> throw HOST_SOLO_CANNOT_LEAVE so UI can show "end/cancel the run instead".
 */
suspend fun leaveRun(db: FirebaseFirestore, runId: String, uid: String) {
    val runRef = db.collection("runs").document(runId)

    db.runTransaction { tx ->
        val snap = tx.get(runRef)

        if (!snap.exists()) {
            throw IllegalStateException("Run not found")
        }

        val hostId = snap.getString("hostId")
        @Suppress("UNCHECKED_CAST")
        val playerIds = (snap.get("playerIds") as? List<String>) ?: emptyList()
        val currentCount = snap.getLong("playerCount")?.toInt() ?: playerIds.size

        // If user isn't in the run, nothing to do
        if (!playerIds.contains(uid)) {
            return@runTransaction null
        }

        // --- Host trying to leave
        if (uid == hostId) {
            // Host is the only player → not allowed
            if (playerIds.size <= 1) {
                throw IllegalStateException("HOST_SOLO_CANNOT_LEAVE")
            }

            val updatedPlayers = playerIds.filter { it != uid }
            val updatedCount = (currentCount - 1).coerceAtLeast(0)

            // Simple rule: next player in the list becomes host
            val newHostId = updatedPlayers.firstOrNull()
                ?: throw IllegalStateException("No remaining players for new host")

            tx.update(
                runRef,
                mapOf(
                    "playerIds" to updatedPlayers,
                    "playerCount" to updatedCount,
                    "hostId" to newHostId
                )
            )
        } else {
            // --- Regular player leaving
            val updatedPlayers = playerIds.filter { it != uid }
            val updatedCount = (currentCount - 1).coerceAtLeast(0)

            tx.update(
                runRef,
                mapOf(
                    "playerIds" to updatedPlayers,
                    "playerCount" to updatedCount
                )
            )
        }

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
    require(mode in listOf("3v3","4v4","5v5")) { "Unsupported mode" }
    db.runTransaction { tx ->
        val ref = db.collection("runs").document(runId)
        val snap = tx.get(ref)
        val run = snap.toObject(Run::class.java) ?: throw IllegalStateException("Run not found")
        if (run.hostId != requesterUid) throw IllegalStateException("Only host can change mode")
        if (run.status !in listOf("active", "scheduled")) {
            throw IllegalStateException("Run not active")
        }

        tx.update(ref, mapOf(
            "mode" to mode,
            "lastHeartbeatAt" to FieldValue.serverTimestamp()
        ))
        null
    }.await()
}

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
