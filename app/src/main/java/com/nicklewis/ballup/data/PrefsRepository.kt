package com.nicklewis.ballup.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

// Add the new flag here
data class UserPrefs(
    val runAlerts: Boolean = true,
    val notifyWhileForeground: Boolean = false,
)

class PrefsRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private fun uid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Not signed in")

    private fun doc() =
        db.collection("users").document(uid()).collection("prefs").document("app")

    // Now emit BOTH fields
    fun listen(): Flow<UserPrefs> = callbackFlow {
        val reg = doc().addSnapshotListener { snap, _ ->
            val run = snap?.getBoolean("runAlerts") ?: true
            val notifyFg = snap?.getBoolean("notifyWhileForeground") ?: false
            trySend(UserPrefs(runAlerts = run, notifyWhileForeground = notifyFg))
        }
        awaitClose { reg.remove() }
    }

    suspend fun setRunAlerts(enabled: Boolean) {
        doc().set(mapOf("runAlerts" to enabled), SetOptions.merge()).await()
    }

    // New setter
    suspend fun setNotifyWhileForeground(enabled: Boolean) {
        doc().set(mapOf("notifyWhileForeground" to enabled), SetOptions.merge()).await()
    }
}
