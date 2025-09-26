package com.nicklewis.ballup.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

object TokenRepository {
    private val auth get() = FirebaseAuth.getInstance()
    private val db   get() = FirebaseFirestore.getInstance()

    suspend fun ensureTokenSaved() {
        val uid = auth.currentUser?.uid ?: return
        val token = FirebaseMessaging.getInstance().token.await()
        db.collection("users").document(uid)
            .collection("tokens").document(token)
            .set(
                mapOf("createdAt" to FieldValue.serverTimestamp(), "platform" to "android"),
                SetOptions.merge()
            ).await()
    }

    suspend fun saveToken(token: String) {
        val uid = auth.currentUser?.uid ?: return
        android.util.Log.d("FCM_TOKEN", "Saved token: $token")
        db.collection("users").document(uid)
            .collection("tokens").document(token)
            .set(
                mapOf("createdAt" to FieldValue.serverTimestamp(), "platform" to "android"),
                SetOptions.merge()
            ).await()
    }
}
