package com.nicklewis.ballup.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class UserPrefs(val runAlerts: Boolean = true)

class PrefsRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private fun uid(): String = auth.currentUser?.uid ?: throw IllegalStateException("Not signed in")
    private fun doc() = db.collection("users").document(uid()).collection("prefs").document("app")

    fun listen(): Flow<UserPrefs> = callbackFlow {
        val reg = doc().addSnapshotListener { snap, _ ->
            val on = snap?.getBoolean("runAlerts") ?: true
            trySend(UserPrefs(runAlerts = on))
        }
        awaitClose { reg.remove() }
    }

    suspend fun setRunAlerts(enabled: Boolean) {
        doc().set(mapOf("runAlerts" to enabled), SetOptions.merge()).await()
    }
}
