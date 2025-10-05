package com.nicklewis.ballup.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.NotificationManager.IMPORTANCE_DEFAULT
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.nicklewis.ballup.MainActivity
import com.nicklewis.ballup.R
import com.nicklewis.ballup.data.TokenRepository
import com.nicklewis.ballup.nav.NotifBus
import com.nicklewis.ballup.nav.InAppAlert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class BallUpMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                TokenRepository.saveToken(token)
                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
                val db  = FirebaseFirestore.getInstance()
                val fns = FirebaseFunctions.getInstance("us-central1")

                val stars = db.collection("users").document(uid).collection("stars").get().await()
                for (doc in stars.documents) {
                    val courtId = doc.id
                    fns.getHttpsCallable("setCourtTopicSubscription")
                        .call(mapOf("token" to token, "courtId" to courtId, "subscribe" to true))
                        .await()
                }
            } catch (_: Exception) { /* no-op */ }
        }
    }

    override fun onMessageReceived(msg: RemoteMessage) {
        android.util.Log.d("FCM", "onMessageReceived data=${msg.data} notif=${msg.notification}")
        val data = msg.data
        val type = data["type"] ?: ""
        val courtName = data["courtName"] ?: ""
        val runId = data["runId"] ?: ""
        val slotsLeft = data["slotsLeft"] ?: ""

        if (type == "run_spots" && runId.isNotEmpty()) {
            if (applicationIsInForeground()) {
                val title = if (slotsLeft == "1") "A spot opened" else "$slotsLeft spots left"
                NotifBus.emit(InAppAlert.RunSpots(title, courtName, runId))
                return
            }

            // System notification → deep link to the run
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("deeplink_runId", runId)
            }
            val pi = PendingIntent.getActivity(
                this, 1001, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val channelId = "run_spots"
            ensureChannel(channelId, "Run Alerts")

            val title = if (slotsLeft == "1") "A spot just opened" else "$slotsLeft spots left"
            val text  = if (courtName.isNotEmpty()) courtName else "Tap to view"

            // ---- run_spots branch ----
            val notif = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(notificationIcon())
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()

            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return  // user denied notifications on Android 13+
            }
            if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                return  // app-level notifications disabled
            }

            NotificationManagerCompat.from(this).notify(9001, notif)
            return
        }

        // Fallback for notification-only messages
        val title = msg.notification?.title ?: "BallUp"
        val body  = msg.notification?.body  ?: "New update"
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "runs"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, "Run Alerts", NotificationManager.IMPORTANCE_HIGH)
            mgr.createNotificationChannel(ch)
        }
        val fallback = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(notificationIcon())
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return

        NotificationManagerCompat.from(this).notify(
            (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
            fallback
        )
    }

    private fun ensureChannel(id: String, name: String) {
        if (Build.VERSION.SDK_INT >= 26) {
            val mgr = getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(id) == null) {
                mgr.createNotificationChannel(NotificationChannel(id, name, IMPORTANCE_DEFAULT))
            }
        }
    }

    private fun notificationIcon(): Int {
        // Use your vector if present; else fall back to the app icon
        return try { R.drawable.ic_notification } catch (_: Exception) { R.mipmap.ic_launcher }
    }

    private fun applicationIsInForeground(): Boolean {
        return try {
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        } catch (_: Throwable) {
            false
        }
    }
}
