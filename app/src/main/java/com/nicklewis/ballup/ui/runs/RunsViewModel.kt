// ui/runs/RunsViewModel.kt
package com.nicklewis.ballup.ui.runs

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.nicklewis.ballup.model.Run
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

//  put the limit as a top-level const (not inside the class)
private const val HOST_DAILY_LIMIT = 2   // host can start up to 2 runs per court per day
private const val HOST_FUTURE_LIMIT = 3
class RunsViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /** Capacity guard: count only runs that time-overlap the new run, capped by court.surfaces */
    private suspend fun ensureCapacity(
        courtId: String,
        startTs: Timestamp,
        endTs: Timestamp
    ) {
        val courtDoc = db.collection("courts").document(courtId).get().await()
        val allowed = (courtDoc.getLong("surfaces")?.toInt() ?: 1).coerceAtLeast(1)

        // server filter: startsAt <= newEnd
        val snap = db.collection("runs")
            .whereEqualTo("courtId", courtId)
            .whereEqualTo("status", "active")
            .whereLessThanOrEqualTo("startsAt", endTs)
            .get()
            .await()

        // client filter: endsAt >= newStart
        val overlapping = snap.documents.count { d ->
            val e = d.getTimestamp("endsAt")
            e != null && e.toDate().time >= startTs.toDate().time
        }

        if (overlapping >= allowed) {
            throw IllegalStateException("This court is full at that time — all surfaces are in use.")
        }
    }

    /** Host daily limit (per court, per calendar day of newStart) */
    /**** Host daily limit (per court, per calendar day of newStart) ****/
    private suspend fun enforceHostLimit(
        uid: String,
        courtId: String,
        newStart: Timestamp
    ) {
        val zone = java.time.ZoneId.systemDefault()
        val startOfDay = java.time.Instant.ofEpochSecond(newStart.seconds)
            .atZone(zone).toLocalDate()
            .atStartOfDay(zone).toInstant()
        val endOfDay = startOfDay.plus(java.time.Duration.ofDays(1))

        val startTs = Timestamp(startOfDay.epochSecond, 0)
        val endTs   = Timestamp(endOfDay.epochSecond, 0)

        // FULL server-side filtering (requires composite index you added)
        val snap = db.collection("runs")
            .whereEqualTo("courtId", courtId)
            .whereEqualTo("hostId", uid)          // <— restore this
            .whereEqualTo("status", "active")
            .whereGreaterThanOrEqualTo("startsAt", startTs)
            .whereLessThan("startsAt", endTs)
            .get()
            .await()

        val count = snap.size()
        Log.d("RunsVM", "Host daily: court=$courtId, day=${startTs.toDate()}, count=$count")
        if (count >= HOST_DAILY_LIMIT) {
            throw IllegalStateException("Daily host limit reached for this court.")
        }
    }

    private suspend fun enforceHostFutureLimit(
        uid: String,
        courtId: String,
        nowTs: com.google.firebase.Timestamp = com.google.firebase.Timestamp.now(),
    ) {
        val snap = db.collection("runs")
            .whereEqualTo("courtId", courtId)
            .whereEqualTo("hostId", uid)
            .whereEqualTo("status", "active")
            .whereGreaterThanOrEqualTo("startsAt", nowTs)   // only upcoming
            .get()
            .await()

        if (snap.size() >= HOST_FUTURE_LIMIT) {
            throw IllegalStateException("You already have the maximum number of upcoming runs at this court.")
        }
    }


    /** Public entry from the dialog: checks capacity + host limit, then creates the run */
    fun createRunWithCapacity(
        run: Run,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) { onError(IllegalStateException("Not signed in")); return }

        val courtId = run.courtId ?: return onError(IllegalArgumentException("Missing courtId"))
        val startTs = run.startsAt ?: return onError(IllegalArgumentException("Missing start"))
        val endTs   = run.endsAt   ?: return onError(IllegalArgumentException("Missing end"))

        viewModelScope.launch {
            try {
                // (1) overlap capacity (by surfaces)
                ensureCapacity(courtId, startTs, endTs)

                // (2) per-host daily limit at this court
                enforceHostLimit(uid, courtId, startTs)

                enforceHostFutureLimit(uid, courtId)

                // (3) write
                val name = run.name?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
                val doc = hashMapOf(
                    "courtId"     to courtId,
                    "status"      to "active",
                    "startsAt"    to startTs,
                    "endsAt"      to endTs,
                    "hostId"      to uid,
                    "mode"        to run.mode,
                    "maxPlayers"  to run.maxPlayers,
                    "playerIds"   to listOf(uid),
                    "playerCount" to 1,
                    "createdAt"   to FieldValue.serverTimestamp(),
                    "name"        to name,
                    "name_lc"     to name.lowercase(),
                    "nameTokens"  to name.lowercase().split(" ").filter { it.isNotBlank() },
                    "access"            to run.access,
                    "allowedUids"       to run.allowedUids,
                    "pendingJoinsCount" to run.pendingJoinsCount
                )
                // legacy
                doc["startTime"] = startTs

                db.collection("runs").add(doc).await()
                Log.d("RunsVM", "Run created successfully!")
                onSuccess()
            } catch (t: Throwable) {
                Log.e("RunsVM", "createRunWithCapacity failed", t)
                onError(t)
            }
        }
    }
}
