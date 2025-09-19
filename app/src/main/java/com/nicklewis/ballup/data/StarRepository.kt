package com.nicklewis.ballup.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.coroutines.resumeWithException

data class CourtLite(
    val id: String,
    val name: String?,
    val lat: Double?,
    val lng: Double?
)

class StarRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private fun uid(): String = auth.currentUser?.uid
        ?: throw IllegalStateException("Not signed in")

    fun starredIds(): Flow<Set<String>> = callbackFlow {
        val reg = db.collection("users").document(uid())
            .collection("stars")
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptySet()); return@addSnapshotListener }
                val ids = snap?.documents?.map { it.id }?.toSet().orEmpty()
                trySend(ids)
            }
        awaitClose { reg.remove() }
    }

    suspend fun setStar(court: CourtLite, star: Boolean) {
        val ref = db.collection("users").document(uid())
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
    }
}

// simple Task.await() helper (if you don’t already have one)
private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) cont.resume(task.result, null)
            else cont.resumeWithException(task.exception ?: RuntimeException("Task failed"))
        }
    }
