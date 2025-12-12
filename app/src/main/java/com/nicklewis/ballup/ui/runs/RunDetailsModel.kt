package com.nicklewis.ballup.ui.runs

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class RunDoc(
    val ref: DocumentReference,
    val courtId: String?,
    val mode: String?,
    val status: String?,
    val maxPlayers: Int,
    val playerCount: Int,
    val hostUid: String?,
    val hostId: String?,
    val playerIds: List<String>,
    val name: String?,
    val startsAt: Timestamp?,
    val endsAt: Timestamp?,
    val access: String,
    val allowedUids: List<String>,
    val pendingJoinsCount: Int
) {
    companion object {
        fun from(data: Map<String, Any>, ref: DocumentReference): RunDoc {
            val courtId = data["courtId"] as? String
            val mode = data["mode"] as? String
            val status = data["status"] as? String ?: "active"
            val maxPlayers = (data["maxPlayers"] as? Number)?.toInt() ?: 10
            val playerCount = (data["playerCount"] as? Number)?.toInt() ?: 0
            val hostUid = data["hostUid"] as? String
            val hostId = data["hostId"] as? String

            @Suppress("UNCHECKED_CAST")
            val playerIds = (data["playerIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

            val name = data["name"] as? String
            val startsAt = data["startsAt"] as? Timestamp
            val endsAt = data["endsAt"] as? Timestamp
            val access = data["access"] as? String ?: com.nicklewis.ballup.model.RunAccess.OPEN.name

            @Suppress("UNCHECKED_CAST")
            val allowedUids = (data["allowedUids"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

            val pending = (data["pendingJoinsCount"] as? Number)?.toInt() ?: 0

            return RunDoc(
                ref,
                courtId,
                mode,
                status,
                maxPlayers,
                playerCount,
                hostUid,
                hostId,
                playerIds,
                name,
                startsAt,
                endsAt,
                access,
                allowedUids,
                pending
            )
        }
    }
}

data class JoinRequestDoc(
    val ref: DocumentReference,
    val uid: String,
    val status: String,
    val createdAt: Timestamp?
) {
    companion object {
        fun from(data: Map<String, Any>, ref: DocumentReference): JoinRequestDoc {
            val uid = data["uid"] as? String ?: ref.id
            val status = data["status"] as? String ?: "pending"
            val createdAt = data["createdAt"] as? Timestamp
            return JoinRequestDoc(ref, uid, status, createdAt)
        }
    }
}

data class PlayerProfile(
    val username: String,
    val skillLevel: String?,
    val playStyle: String?,
    val heightBracket: String?,
    val favoriteCourts: List<String>,
    val displayName: String?
)

suspend fun lookupUserProfile(
    db: FirebaseFirestore,
    uid: String
): PlayerProfile? {
    return try {
        val doc = db.collection("users").document(uid).get().await()
        if (!doc.exists()) {
            Log.d("PROFILE", "No user doc for $uid")
            null
        } else {
            val username = doc.getString("username") ?: uid
            val skillLevel = doc.getString("skillLevel")
            val playStyle = doc.getString("playStyle")
            val heightBracket = doc.getString("heightBracket")
            val displayName = doc.getString("displayName")

            @Suppress("UNCHECKED_CAST")
            val fav = (doc.get("favoriteCourts") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

            PlayerProfile(
                username = username,
                skillLevel = skillLevel,
                playStyle = playStyle,
                heightBracket = heightBracket,
                favoriteCourts = fav,
                displayName = displayName
            )
        }
    } catch (e: Exception) {
        Log.e("PROFILE", "Error looking up $uid", e)
        null
    }
}

fun formatWindow(start: Timestamp?, end: Timestamp?): String {
    if (start == null || end == null) return "Time: not set"

    val zone = ZoneId.systemDefault()
    val s = start.toDate().toInstant().atZone(zone).toLocalDateTime()
    val e = end.toDate().toInstant().atZone(zone).toLocalDateTime()

    val dFmt = DateTimeFormatter.ofPattern("EEE, MMM d")
    val tFmt = DateTimeFormatter.ofPattern("h:mm a")

    return if (s.toLocalDate() == e.toLocalDate()) {
        "${dFmt.format(s)} • ${tFmt.format(s)} – ${tFmt.format(e)}"
    } else {
        "${dFmt.format(s)} ${tFmt.format(s)} → ${dFmt.format(e)} ${tFmt.format(e)}"
    }
}
