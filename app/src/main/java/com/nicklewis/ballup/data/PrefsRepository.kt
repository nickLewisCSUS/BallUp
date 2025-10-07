// data/PrefsRepository.kt
package com.nicklewis.ballup.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlin.coroutines.resumeWithException

data class CloudPrefs(
    val runAlerts: Boolean = true // default when not signed in or doc missing
)

class PrefsRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {
    /** Emits current UID or null as auth state changes (safe). */
    private fun authUidFlow(): Flow<String?> = callbackFlow {
        val l = FirebaseAuth.AuthStateListener { fa -> trySend(fa.currentUser?.uid) }
        auth.addAuthStateListener(l)
        trySend(auth.currentUser?.uid) // initial
        awaitClose { auth.removeAuthStateListener(l) }
    }

    /** Reads cloud prefs from users/{uid}/prefs/app (safe, emits defaults). */
    private fun cloudPrefsFor(uid: String): Flow<CloudPrefs> = callbackFlow {
        val ref = db.collection("users").document(uid)
            .collection("prefs").document("app")
        val reg = ref.addSnapshotListener { snap, _ ->
            val run = snap?.getBoolean("runAlerts") ?: true
            trySend(CloudPrefs(runAlerts = run))
        }
        awaitClose { reg.remove() }
    }

    /** Public stream: defaults when signed out; live doc when signed in. */
    val cloudPrefs: Flow<CloudPrefs> =
        authUidFlow().flatMapLatest { uid ->
            if (uid == null) flowOf(CloudPrefs())
            else cloudPrefsFor(uid)
        }

    /** Optional: cloud setter for runAlerts (no-op if signed out). */
    suspend fun setRunAlerts(enabled: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid)
            .collection("prefs").document("app")
            .set(mapOf("runAlerts" to enabled), SetOptions.merge())
            .await()
    }
}

// tiny await helper (keep if you don’t already have one here)
private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        addOnCompleteListener { t ->
            if (t.isSuccessful) cont.resume(t.result, null)
            else cont.resumeWithException(t.exception ?: RuntimeException("Task failed"))
        }
    }
