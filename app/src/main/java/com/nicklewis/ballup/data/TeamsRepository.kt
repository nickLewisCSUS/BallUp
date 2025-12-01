package com.nicklewis.ballup.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.nicklewis.ballup.model.Team
import com.nicklewis.ballup.model.UserProfile
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
     * (Kept for compatibility if other parts of the app use it.)
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
     * Live list of teams where the current user is a member (includes owned teams).
     */
    fun getTeamsForCurrentUser(): Flow<List<Team>> = callbackFlow {
        val uid = uidOrThrow()
        val reg = db.collection("teams")
            .whereArrayContains("memberUids", uid)
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
     * Optional helper to add a member to a team (owner-only).
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

    /**
     * Rename a team (owner-only).
     */
    suspend fun renameTeam(teamId: String, newName: String) {
        val uid = uidOrThrow()
        val ref = db.collection("teams").document(teamId)

        db.runTransaction { tx ->
            val snap = tx.get(ref)
            if (!snap.exists()) throw IllegalStateException("Team not found")
            val ownerUid = snap.getString("ownerUid") ?: ""
            if (ownerUid != uid) throw IllegalAccessException("Not team owner")

            tx.update(ref, "name", newName)
        }.await()
    }

    /**
     * Delete a team (owner-only).
     */
    suspend fun deleteTeam(teamId: String) {
        val uid = uidOrThrow()
        val ref = db.collection("teams").document(teamId)

        db.runTransaction { tx ->
            val snap = tx.get(ref)
            if (!snap.exists()) return@runTransaction
            val ownerUid = snap.getString("ownerUid") ?: ""
            if (ownerUid != uid) throw IllegalAccessException("Not team owner")

            tx.delete(ref)
        }.await()
    }

    /**
     * Fetch user profiles for the given member UIDs.
     * Simple implementation: one doc read per uid.
     */
    suspend fun getMembers(memberUids: List<String>): List<UserProfile> {
        if (memberUids.isEmpty()) return emptyList()

        val col = db.collection("users")
        val snaps = memberUids.map { uid ->
            col.document(uid).get().await()
        }

        return snaps.mapNotNull { snap ->
            snap.toObject(UserProfile::class.java)
        }
    }
}
