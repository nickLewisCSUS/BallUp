package com.nicklewis.ballup.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.nicklewis.ballup.model.Run
import com.nicklewis.ballup.model.RunAccess
import com.nicklewis.ballup.model.Team
import kotlinx.coroutines.tasks.await

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

suspend fun requestJoinRun(db: FirebaseFirestore, runId: String, uid: String) {
    val runRef = db.collection("runs").document(runId)
    val reqRef = runRef.collection("joinRequests").document(uid)

    db.runTransaction { tx ->
        val runSnap = tx.get(runRef)
        if (!runSnap.exists()) {
            throw IllegalStateException("Run not found")
        }

        val access = runSnap.getString("access") ?: RunAccess.OPEN.name
        if (access != RunAccess.HOST_APPROVAL.name) {
            throw IllegalStateException("This run doesn’t require host approval.")
        }

        val existing = tx.get(reqRef)
        if (existing.exists() && existing.getString("status") == "pending") {
            throw IllegalStateException("You’ve already requested to join this run.")
        }

        val data = mapOf(
            "uid"       to uid,
            "status"    to "pending",
            "createdAt" to FieldValue.serverTimestamp()
        )

        tx.set(reqRef, data)
        tx.update(
            runRef,
            mapOf("pendingJoinsCount" to FieldValue.increment(1))
        )

        null
    }.await()
}

suspend fun cancelJoinRequest(
    db: FirebaseFirestore,
    runId: String,
    uid: String
) {
    val runRef = db.collection("runs").document(runId)
    val reqRef = runRef.collection("joinRequests").document(uid)

    db.runTransaction { tx ->
        val runSnap = tx.get(runRef)
        if (!runSnap.exists()) {
            throw IllegalStateException("Run not found")
        }

        val existing = tx.get(reqRef)
        if (!existing.exists() || existing.getString("status") != "pending") {
            // nothing pending → nothing to cancel
            return@runTransaction null
        }

        tx.delete(reqRef)
        tx.update(
            runRef,
            mapOf("pendingJoinsCount" to FieldValue.increment(-1))
        )

        null
    }.await()
}

/**
 * Host bulk-invites a squad into a run.
 *
 * Behavior:
 * - Only the host can call this.
 * - OPEN + INVITE_ONLY → members are added directly to playerIds (up to maxPlayers).
 * - HOST_APPROVAL → creates pending joinRequests for each member.
 */
suspend fun inviteSquadToRun(
    db: FirebaseFirestore,
    runId: String,
    requesterUid: String,
    team: Team
) {
    val runRef = db.collection("runs").document(runId)

    db.runTransaction { tx ->
        val runSnap = tx.get(runRef)
        if (!runSnap.exists()) throw IllegalStateException("Run not found")

        val run = runSnap.toObject(Run::class.java)
            ?: throw IllegalStateException("Run not found")

        // Only host can invite squads
        if (run.hostId != requesterUid) {
            throw IllegalStateException("Only host can invite squads")
        }

        if (run.status !in listOf("active", "scheduled")) {
            throw IllegalStateException("This run has ended")
        }

        val access = runSnap.getString("access") ?: RunAccess.OPEN.name
        val maxPlayers = run.maxPlayers.takeIf { it > 0 } ?: 10

        val currentPlayers = (run.playerIds ?: emptyList()).toMutableList()
        val currentSet = currentPlayers.toSet()

        val teamMembers = team.memberUids.toSet()

        // remove anyone already in the run
        val newMembers = teamMembers - currentSet

        if (newMembers.isEmpty()) {
            return@runTransaction null
        }

        when (access) {
            RunAccess.HOST_APPROVAL.name -> {
                // create pending joinRequests for each member
                newMembers.forEach { memberUid ->
                    val reqRef = runRef.collection("joinRequests").document(memberUid)
                    val existing = tx.get(reqRef)
                    if (!existing.exists() || existing.getString("status") != "pending") {
                        tx.set(
                            reqRef,
                            mapOf(
                                "uid"       to memberUid,
                                "status"    to "pending",
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                        )
                        tx.update(
                            runRef,
                            mapOf("pendingJoinsCount" to FieldValue.increment(1))
                        )
                    }
                }
            }

            RunAccess.OPEN.name,
            RunAccess.INVITE_ONLY.name -> {
                // treat host squad invite as direct approval; obey maxPlayers
                val availableSlots = maxPlayers - currentPlayers.size
                if (availableSlots <= 0) {
                    // run is full, nothing to add
                    return@runTransaction null
                }
                val toAdd = newMembers.take(availableSlots)
                currentPlayers += toAdd
                tx.update(
                    runRef,
                    mapOf(
                        "playerIds" to currentPlayers,
                        "playerCount" to currentPlayers.size,
                        "lastHeartbeatAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            else -> {
                // unknown access mode → default to no-op
                return@runTransaction null
            }
        }

        null
    }.await()
}
