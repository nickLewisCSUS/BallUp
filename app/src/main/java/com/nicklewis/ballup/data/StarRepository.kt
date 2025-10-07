package com.nicklewis.ballup.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resumeWithException

data class CourtLite(
    val id: String,
    val name: String?,
    val lat: Double?,
    val lng: Double?
)

class StarRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val fns: FirebaseFunctions = FirebaseFunctions.getInstance("us-central1"),
    private val fcm: FirebaseMessaging = FirebaseMessaging.getInstance()
) {
    /** Emit uid (or null) as auth state changes. */
    private fun authUidFlow(): Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { fa -> trySend(fa.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        trySend(auth.currentUser?.uid) // initial
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /** Public: stream of starred court IDs. Empty set while not signed-in yet. */
    fun starredIds(): Flow<Set<String>> =
        authUidFlow().flatMapLatest { uid ->
            if (uid == null) {
                flowOf(emptySet())
            } else {
                callbackFlow {
                    val reg = db.collection("users").document(uid)
                        .collection("stars")
                        .addSnapshotListener { snap, _ ->
                            val ids = snap?.documents?.map { it.id }?.toSet().orEmpty()
                            trySend(ids)
                        }
                    awaitClose { reg.remove() }
                }
            }
        }

    /** Set/clear a star. Quiet no-op if not signed-in yet. */
    suspend fun setStar(court: CourtLite, star: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        val ref = db.collection("users").document(uid)
            .collection("stars").document(court.id)

        if (star) {
            val data = mapOf(
                "createdAt" to FieldValue.serverTimestamp(),
                "courtName" to (court.name ?: ""),
                "lat" to (court.lat ?: 0.0),
                "lng" to (court.lng ?: 0.0)
            )
            ref.set(data).await()
        } else {
            ref.delete().await()
        }

        // Subscribe/unsubscribe this device to the court topic via your callable
        val token = fcm.token.await()
        fns.getHttpsCallable("setCourtTopicSubscription")
            .call(mapOf("token" to token, "courtId" to court.id, "subscribe" to star))
            .await()
    }
}

/** await helper for Tasks */
private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) cont.resume(task.result, null)
            else cont.resumeWithException(task.exception ?: RuntimeException("Task failed"))
        }
    }
