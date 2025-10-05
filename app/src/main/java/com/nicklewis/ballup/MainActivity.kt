@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.nicklewis.ballup

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.nicklewis.ballup.data.TokenRepository
import com.nicklewis.ballup.nav.AppNavControllerHolder
import com.nicklewis.ballup.nav.BallUpApp
import kotlinx.coroutines.launch
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions

class MainActivity : ComponentActivity() {

    private fun resubscribeStarTopics() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val fns = FirebaseFunctions.getInstance("us-central1")

        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            db.collection("users").document(uid).collection("stars").get()
                .addOnSuccessListener { snaps ->
                    Log.d("TOPICS", "found ${snaps.size()} starred courts")
                    snaps.documents.forEach { doc ->
                        val courtId = doc.id
                        Log.d("TOPICS", "subscribing to court_$courtId")
                        fns.getHttpsCallable("setCourtTopicSubscription")
                            .call(mapOf("token" to token, "courtId" to courtId, "subscribe" to true))
                            .addOnFailureListener { e -> Log.e("TOPICS", "subscribe failed $courtId", e) }
                    }
                }
                .addOnFailureListener { e -> Log.e("TOPICS", "stars fetch failed", e) }
        }.addOnFailureListener { e -> Log.e("TOPICS", "token fetch failed", e) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 13+ notifications permission
        if (Build.VERSION.SDK_INT >= 33) {
            val perm = android.Manifest.permission.POST_NOTIFICATIONS
            if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(perm), 1001)
            }
        }

        // Ensure signed in (anon ok) before UI; also save FCM token
        signInAnonIfNeeded()

        setContent { BallUpApp() }

        // Handle push deep link (works on cold start)
        handleDeeplink(intent)

        // (Optional) debug log
        val app = com.google.firebase.FirebaseApp.getInstance()
        Log.d("Firebase", "projectId=${app.options.projectId}, appId=${app.options.applicationId}")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle push deep link when activity is already alive
        handleDeeplink(intent)
    }

    private fun handleDeeplink(intent: Intent) {
        val runId = intent.getStringExtra("deeplink_runId") ?: return
        AppNavControllerHolder.navController?.navigate("run/$runId") {
            launchSingleTop = true
            restoreState = true
        }
        intent.removeExtra("deeplink_runId")
    }

    private fun signInAnonIfNeeded() {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnSuccessListener {
                    Log.d("AUTH", "anon ok: ${it.user?.uid}")
                    lifecycleScope.launch { TokenRepository.ensureTokenSaved() }
                    resubscribeStarTopics()
                }
                .addOnFailureListener { e -> Log.e("AUTH", "anon fail", e) }
        } else {
            Log.d("AUTH", "already signed in: ${auth.currentUser?.uid}")
            lifecycleScope.launch { TokenRepository.ensureTokenSaved() }
            resubscribeStarTopics()
        }
    }
}
