package com.nicklewis.ballup.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.nicklewis.ballup.model.Team
import com.nicklewis.ballup.model.UserProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.FieldValue
import com.google.firebase.Timestamp

class TeamsRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    private fun uidOrThrow(): String =
        auth.currentUser?.uid ?: error("Not signed in")

    // --- helper to map a document to Team safely ---
    private fun mapTeam(doc: com.google.firebase.firestore.DocumentSnapshot): Team {
        return Team(
            id = doc.id,
            name = doc.getString("name") ?: "",
            ownerUid = doc.getString("ownerUid") ?: "",
            memberUids = doc.get("memberUids") as? List<String> ?: emptyList(),
            createdAt = doc.getTimestamp("createdAt"),
            preferredSkillLevel = doc.getString("preferredSkillLevel"),
            playDays = doc.get("playDays") as? List<String> ?: emptyList(),
            inviteOnly = doc.getBoolean("inviteOnly") ?: false
        )
    }

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
                val teams = snap?.documents
                    ?.map { doc -> mapTeam(doc) }
                    .orEmpty()
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
                val teams = snap?.documents
                    ?.map { doc -> mapTeam(doc) }
                    .orEmpty()
                trySend(teams)
            }

        awaitClose { reg.remove() }
    }

    /**
     * Squads to show in the "Find squads" tab.
     * Simple version: all squads where I'm NOT already a member.
     */
    fun getDiscoverableTeams(): Flow<List<Team>> = callbackFlow {
        val uid = uidOrThrow()
        val reg = db.collection("teams")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    close(err)
                    return@addSnapshotListener
                }
                val all = snap?.documents
                    ?.map { doc -> mapTeam(doc) }
                    .orEmpty()

                val filtered = all.filter { team ->
                    !team.memberUids.contains(uid) &&
                            !team.inviteOnly // NEW: host-invite-only squads are not discoverable
                }

                trySend(filtered)
            }

        awaitClose { reg.remove() }
    }

    /**
     * Track which squads this user has already requested to join.
     * Returns teamIds with a pending request.
     */
    fun getPendingJoinRequestsForCurrentUser(): Flow<List<String>> = callbackFlow {
        val uid = uidOrThrow()
        val reg = db.collection("teamJoinRequests")
            .whereEqualTo("uid", uid)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    close(err)
                    return@addSnapshotListener
                }
                val ids = snap?.documents
                    ?.mapNotNull { it.getString("teamId") }
                    .orEmpty()
                trySend(ids)
            }

        awaitClose { reg.remove() }
    }

    /**
     * Create a team with the current user as owner and first member.
     */
    suspend fun createTeam(
        name: String,
        preferredSkillLevel: String?,
        playDays: List<String>,
        inviteOnly: Boolean
    ): String {
        val uid = uidOrThrow()
        val ref = db.collection("teams").document()
        val data = mapOf(
            "name" to name,
            "ownerUid" to uid,
            "memberUids" to listOf(uid),
            "createdAt" to com.google.firebase.Timestamp.now(),
            // NEW
            "preferredSkillLevel" to preferredSkillLevel,
            "playDays" to playDays,
            "inviteOnly" to inviteOnly
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

    /**
     * Player → request to join a squad.
     * (We allow multiple docs for same teamId+uid for now; could de-dupe later.)
     */
    suspend fun requestToJoinTeam(teamId: String, uid: String) {
        val ref = db.collection("teamJoinRequests").document()
        val data = mapOf(
            "teamId" to teamId,
            "uid" to uid,
            "status" to "pending",
            "createdAt" to com.google.firebase.Timestamp.now()
        )
        ref.set(data).await()
    }

    /**
     * Player → cancel their pending join request for this squad.
     */
    suspend fun cancelJoinRequest(teamId: String, uid: String) {
        val snap = db.collection("teamJoinRequests")
            .whereEqualTo("teamId", teamId)
            .whereEqualTo("uid", uid)
            .whereEqualTo("status", "pending")
            .limit(1)
            .get()
            .await()

        val doc = snap.documents.firstOrNull()?.reference ?: return
        doc.delete().await()
    }

    /**
     * Non-owner member leaves squad.
     */
    suspend fun leaveTeam(teamId: String, uid: String) {
        val ref = db.collection("teams").document(teamId)

        db.runTransaction { tx ->
            val snap = tx.get(ref)
            if (!snap.exists()) throw IllegalStateException("Squad not found")

            val ownerUid = snap.getString("ownerUid") ?: ""
            if (uid == ownerUid) {
                throw IllegalStateException("Owner cannot leave their own squad")
            }

            val members = snap.get("memberUids") as? List<String> ?: emptyList()
            val newMembers = members.filterNot { it == uid }
            tx.update(ref, "memberUids", newMembers)
        }.await()
    }

    // -------- Host approval helpers --------

    data class PendingTeamRequest(
        val uid: String,
        val profile: UserProfile?
    )

    /**
     * Owner: load pending join requests for a given team.
     * Returns list of (uid + UserProfile?).
     */
    suspend fun getPendingRequestsForTeam(teamId: String): List<PendingTeamRequest> {
        val requestsSnap = db.collection("teamJoinRequests")
            .whereEqualTo("teamId", teamId)
            .whereEqualTo("status", "pending")
            .get()
            .await()

        val uids = requestsSnap.documents
            .mapNotNull { it.getString("uid") }
            .distinct()

        if (uids.isEmpty()) return emptyList()

        val profiles = getMembers(uids).associateBy { it.uid }

        return uids.map { uid ->
            PendingTeamRequest(
                uid = uid,
                profile = profiles[uid]
            )
        }
    }

    /**
     * Owner: approve a request → add to memberUids and delete the joinRequest doc.
     */
    suspend fun approveJoinRequest(teamId: String, targetUid: String) {
        val ownerUid = uidOrThrow()
        val teamRef = db.collection("teams").document(teamId)

        // find any pending request for (teamId, targetUid)
        val reqSnap = db.collection("teamJoinRequests")
            .whereEqualTo("teamId", teamId)
            .whereEqualTo("uid", targetUid)
            .whereEqualTo("status", "pending")
            .limit(1)
            .get()
            .await()

        val reqDocRef = reqSnap.documents.firstOrNull()?.reference
            ?: throw IllegalStateException("Request not found")

        db.runTransaction { tx ->
            val team = tx.get(teamRef)
            if (!team.exists()) throw IllegalStateException("Squad not found")

            val owner = team.getString("ownerUid") ?: ""
            if (owner != ownerUid) throw IllegalAccessException("Not squad owner")

            val members = team.get("memberUids") as? List<String> ?: emptyList()
            if (!members.contains(targetUid)) {
                tx.update(teamRef, "memberUids", members + targetUid)
            }

            tx.delete(reqDocRef)
        }.await()
    }

    /**
     * Owner: deny a request → just delete the joinRequest doc.
     */
    suspend fun denyJoinRequest(teamId: String, targetUid: String) {
        val ownerUid = uidOrThrow()
        val teamRef = db.collection("teams").document(teamId)

        val reqSnap = db.collection("teamJoinRequests")
            .whereEqualTo("teamId", teamId)
            .whereEqualTo("uid", targetUid)
            .whereEqualTo("status", "pending")
            .limit(1)
            .get()
            .await()

        val reqDocRef = reqSnap.documents.firstOrNull()?.reference
            ?: return

        db.runTransaction { tx ->
            val team = tx.get(teamRef)
            if (!team.exists()) throw IllegalStateException("Squad not found")

            val owner = team.getString("ownerUid") ?: ""
            if (owner != ownerUid) throw IllegalAccessException("Not squad owner")

            tx.delete(reqDocRef)
        }.await()
    }

    /**
     * Edit an existing squad (owner-only).
     * Updates name, skill, play days, and privacy.
     */
    suspend fun updateTeam(
        teamId: String,
        name: String,
        preferredSkillLevel: String?,
        playDays: List<String>,
        inviteOnly: Boolean
    ) {
        val uid = uidOrThrow()
        val ref = db.collection("teams").document(teamId)

        db.runTransaction { tx ->
            val snap = tx.get(ref)
            if (!snap.exists()) throw IllegalStateException("Squad not found")

            val ownerUid = snap.getString("ownerUid") ?: ""
            if (ownerUid != uid) throw IllegalAccessException("Not team owner")

            // Build updates map
            val updates = mutableMapOf<String, Any>(
                "name" to name,
                "playDays" to playDays,
                "inviteOnly" to inviteOnly
            )

            // If preferredSkillLevel is null, clear the field; otherwise set it
            if (preferredSkillLevel != null) {
                updates["preferredSkillLevel"] = preferredSkillLevel
            } else {
                updates["preferredSkillLevel"] = FieldValue.delete()
            }

            tx.update(ref, updates as Map<String, Any>)
        }.await()
    }

    // -------- Squad Invites (owner → player) --------

    data class TeamInviteForUser(
        val inviteId: String,
        val teamId: String,
        val teamName: String,
        val preferredSkillLevel: String?,
        val playDays: List<String>,
        val inviteOnly: Boolean
    )

    /**
     * Live invites for the current user (as player).
     */
    fun getInvitesForCurrentUser(): Flow<List<TeamInviteForUser>> = callbackFlow {
        val uid = uidOrThrow()
        val reg = db.collection("teamInvites")
            .whereEqualTo("uid", uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    close(err)
                    return@addSnapshotListener
                }

                val invites = snap?.documents
                    ?.mapNotNull { doc ->
                        val status = doc.getString("status") ?: "pending"
                        if (status != "pending") return@mapNotNull null

                        val teamId = doc.getString("teamId") ?: return@mapNotNull null
                        val teamName = doc.getString("teamName") ?: "Unnamed squad"
                        val skill = doc.getString("preferredSkillLevel")
                        val playDays = doc.get("playDays") as? List<String> ?: emptyList()
                        val inviteOnly = doc.getBoolean("inviteOnly") ?: false

                        TeamInviteForUser(
                            inviteId = doc.id,
                            teamId = teamId,
                            teamName = teamName,
                            preferredSkillLevel = skill,
                            playDays = playDays,
                            inviteOnly = inviteOnly
                        )
                    }
                    .orEmpty()

                trySend(invites)
            }

        awaitClose { reg.remove() }
    }

    /**
     * Owner: send an invite to a player by username.
     * (We assume user docs are stored under /users/{uid} with a `username` field.)
     */
    suspend fun sendTeamInviteByUsername(teamId: String, username: String) {
        val ownerUid = uidOrThrow()

        // Make sure the squad exists and I'm the owner
        val teamRef = db.collection("teams").document(teamId)
        val teamSnap = teamRef.get().await()
        if (!teamSnap.exists()) throw IllegalStateException("Squad not found")

        val owner = teamSnap.getString("ownerUid") ?: ""
        if (owner != ownerUid) throw IllegalAccessException("Not squad owner")

        // Look up the player by username
        val userSnap = db.collection("users")
            .whereEqualTo("username", username)
            .limit(1)
            .get()
            .await()

        val userDoc = userSnap.documents.firstOrNull()
            ?: throw IllegalStateException("No player with that username")

        val targetUid = userDoc.id

        // Don't invite if already in squad
        val members = teamSnap.get("memberUids") as? List<String> ?: emptyList()
        if (members.contains(targetUid)) {
            throw IllegalStateException("That player is already in this squad")
        }

        // Don't double-invite
        val existing = db.collection("teamInvites")
            .whereEqualTo("teamId", teamId)
            .whereEqualTo("uid", targetUid)
            .whereEqualTo("status", "pending")
            .limit(1)
            .get()
            .await()

        if (!existing.isEmpty) {
            throw IllegalStateException("You’ve already invited this player")
        }

        // Copy some display info from the squad so the invite tab can show it
        val teamName = teamSnap.getString("name") ?: "Unnamed squad"
        val preferredSkill = teamSnap.getString("preferredSkillLevel")
        val playDays = teamSnap.get("playDays") as? List<String> ?: emptyList()
        val inviteOnly = teamSnap.getBoolean("inviteOnly") ?: false

        val inviteRef = db.collection("teamInvites").document()
        val data = mapOf(
            "teamId" to teamId,
            "uid" to targetUid,
            "status" to "pending",
            "createdAt" to Timestamp.now(),
            "teamName" to teamName,
            "preferredSkillLevel" to preferredSkill,
            "playDays" to playDays,
            "inviteOnly" to inviteOnly
        )

        inviteRef.set(data).await()
    }

    /**
     * Player: accept an invite (adds me to memberUids).
     */
    suspend fun acceptInvite(inviteId: String) {
        val uid = uidOrThrow()
        val inviteRef = db.collection("teamInvites").document(inviteId)

        db.runTransaction { tx ->
            val inviteSnap = tx.get(inviteRef)
            if (!inviteSnap.exists()) throw IllegalStateException("Invite not found")

            val inviteUid = inviteSnap.getString("uid") ?: ""
            if (inviteUid != uid) throw IllegalAccessException("Not your invite")

            val status = inviteSnap.getString("status") ?: "pending"
            if (status != "pending") return@runTransaction

            val teamId = inviteSnap.getString("teamId")
                ?: throw IllegalStateException("Invite missing teamId")

            val teamRef = db.collection("teams").document(teamId)
            val teamSnap = tx.get(teamRef)
            if (!teamSnap.exists()) throw IllegalStateException("Squad not found")

            val members = teamSnap.get("memberUids") as? List<String> ?: emptyList()
            if (!members.contains(uid)) {
                tx.update(teamRef, "memberUids", members + uid)
            }

            tx.update(inviteRef, "status", "accepted")
        }.await()
    }

    /**
     * Player: decline an invite.
     */
    suspend fun declineInvite(inviteId: String) {
        val uid = uidOrThrow()
        val inviteRef = db.collection("teamInvites").document(inviteId)

        db.runTransaction { tx ->
            val inviteSnap = tx.get(inviteRef)
            if (!inviteSnap.exists()) return@runTransaction

            val inviteUid = inviteSnap.getString("uid") ?: ""
            if (inviteUid != uid) throw IllegalAccessException("Not your invite")

            tx.update(inviteRef, "status", "declined")
        }.await()
    }


}
