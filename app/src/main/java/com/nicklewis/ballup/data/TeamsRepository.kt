package com.nicklewis.ballup.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.nicklewis.ballup.model.Team
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class TeamsRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    private fun uidOrThrow(): String =
        auth.currentUser?.uid ?: error("Not signed in")

    /**
     * Live list of teams where the user is the owner.
     */
    fun getOwnedTeams(): Flow<List<Team>> = callbackFlow {
        val uid = uidOrThrow()
        val reg = db.collection("teams")
            .whereEqualTo("ownerUid", uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    close(err)
                    return@addSnapshotListener
                }
                val teams = snap?.toObjects(Team::class.java).orEmpty()
                trySend(teams)
            }

        awaitClose { reg.remove() }
    }

    /**
     * Create a team with the current user as owner and first member.
     */
    suspend fun createTeam(name: String): String {
        val uid = uidOrThrow()
        val ref = db.collection("teams").document()
        val data = mapOf(
            "name" to name,
            "ownerUid" to uid,
            "memberUids" to listOf(uid),
            "createdAt" to com.google.firebase.Timestamp.now()
        )
        ref.set(data).await()
        return ref.id
    }

    /**
     * Optional helper to add a member to a team (later you may want invite flows).
     */
    suspend fun addMember(teamId: String, memberUid: String) {
        val uid = uidOrThrow()
        val ref = db.collection("teams").document(teamId)

        db.runTransaction { tx ->
            val snap = tx.get(ref)
            if (!snap.exists()) throw IllegalStateException("Team not found")
            val ownerUid = snap.getString("ownerUid") ?: ""
            if (ownerUid != uid) throw IllegalAccessException("Not team owner")

            val members = snap.get("memberUids") as? List<String> ?: emptyList()
            if (!members.contains(memberUid)) {
                tx.update(ref, "memberUids", members + memberUid)
            }
        }.await()
    }
}
