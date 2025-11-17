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
import kotlinx.coroutines.flow.first
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

    private fun postSystemNotificationIfAllowed(
        channelId: String,
        title: String,
        text: String,
        deeplinkRunId: String
    ) {
        val ctx = applicationContext
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val allowWhileForeground = try {
                com.nicklewis.ballup.data.LocalPrefsStore(ctx).prefsFlow.first().notifyWhileForeground
            } catch (_: Throwable) { false }

            val inForeground = applicationIsInForeground()
            if (!inForeground || allowWhileForeground) {
                val intent = android.content.Intent(ctx, com.nicklewis.ballup.MainActivity::class.java).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("deeplink_runId", deeplinkRunId)
                }
                val pi = android.app.PendingIntent.getActivity(
                    ctx, 1001, intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
                ensureChannel(channelId, "Run Alerts")
                val notif = androidx.core.app.NotificationCompat.Builder(ctx, channelId)
                    .setSmallIcon(notificationIcon())
                    .setContentTitle(title)
                    .setContentText(text)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build()

                if (android.os.Build.VERSION.SDK_INT >= 33 &&
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        ctx, android.Manifest.permission.POST_NOTIFICATIONS
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) return@launch
                val nm = androidx.core.app.NotificationManagerCompat.from(ctx)
                if (!nm.areNotificationsEnabled()) return@launch

                nm.notify(9001, notif)
            }
        }
    }


    override fun onMessageReceived(msg: RemoteMessage) {
        android.util.Log.d("FCM", "onMessageReceived data=${msg.data} notif=${msg.notification}")
        val data = msg.data
        val type = data["type"] ?: ""
        val courtName = data["courtName"] ?: ""
        val runId = data["runId"] ?: ""
        val slotsLeft = data["slotsLeft"] ?: ""

        // --- Upcoming run reminder (1h / 10m before) ---
        if (type == "run_upcoming" && runId.isNotEmpty()) {
            val minutes = data["minutes"]?.toIntOrNull()
            val runName = data["runName"] ?: data["name"] ?: ""

            val whenText = when (minutes) {
                60 -> "in 1 hour"
                10 -> "in 10 minutes"
                else -> "soon"
            }

            val titleNotif = if (runName.isNotEmpty()) {
                runName
            } else {
                "Your run is coming up"
            }

            val text = buildString {
                append("Starts ")
                append(whenText)
                if (courtName.isNotEmpty()) {
                    append(" at ")
                    append(courtName)
                }
            }

            // In-app banner
            NotifBus.emit(
                InAppAlert.RunUpcoming(
                    title = titleNotif,
                    courtName = courtName,
                    runId = runId,
                    minutes = minutes ?: 0
                )
            )

            // System notification
            postSystemNotificationIfAllowed(
                channelId = "runs",
                title = titleNotif,
                text = text,
                deeplinkRunId = runId
            )
            return
        }

        if (type == "run_spots" && runId.isNotEmpty()) {
            val title = if (slotsLeft == "1") "A spot opened" else "$slotsLeft spots left"
            val text  = if (courtName.isNotEmpty()) courtName else "Tap to view"

            // 1) Always emit in-app banner (no-op if app not visible)
            NotifBus.emit(InAppAlert.RunSpots(title, courtName, runId))

            // 2) Post a SYSTEM notification only if:
            //    - app is backgrounded, or
            //    - user enabled "notify while foreground"
            val ctx = applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                val allowWhileForeground = try {
                    com.nicklewis.ballup.data.LocalPrefsStore(ctx)
                        .prefsFlow.first().notifyWhileForeground
                } catch (_: Throwable) { false }

                val inForeground = applicationIsInForeground()
                if (!inForeground || allowWhileForeground) {

                    // Deep link intent → run screen
                    val intent = Intent(ctx, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("deeplink_runId", runId)
                    }
                    val pi = PendingIntent.getActivity(
                        ctx, 1001, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val channelId = "run_spots"
                    ensureChannel(channelId, "Run Alerts")

                    val notif = NotificationCompat.Builder(ctx, channelId)
                        .setSmallIcon(notificationIcon())
                        .setContentTitle(title)
                        .setContentText(text)
                        .setContentIntent(pi)
                        .setAutoCancel(true)
                        .build()

                    if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED
                    ) return@launch
                    if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
                        return@launch
                    }

                    NotificationManagerCompat.from(ctx).notify(9001, notif)
                }
            }
            return
        }

        // --- New run created / opened ---
        if (type == "run_created" || type == "run_open") {
            val courtNameLocal = data["courtName"].orEmpty()
            val runIdLocal = data["runId"].orEmpty()
            val mode = data["mode"].orEmpty()
            val maxPlayers = data["maxPlayers"].orEmpty()
            val startsAtMs = data["startsAt"]?.toLongOrNull()

            val timeText = startsAtMs?.let {
                android.text.format.DateFormat.format("EEE h:mm a", java.util.Date(it)).toString()
            } ?: "now"

            val title = "New run at ${courtNameLocal.ifEmpty { "this court" }}"
            val text  = "$mode • up to $maxPlayers • starts $timeText"

            // In-app banner (Snackbar overlay)
            NotifBus.emit(InAppAlert.RunCreated(title, courtNameLocal, runIdLocal, timeText))

            // System notification (same checks as before)
            postSystemNotificationIfAllowed(
                channelId = "runs",
                title = title,
                text = text,
                deeplinkRunId = runIdLocal
            )
            return
        }

        // 👇👇👇 NEW BLOCK GOES *HERE* — before the fallback

        // --- Run cancelled / ended ---
        if ((type == "run_cancelled" || type == "run_ended") && runId.isNotEmpty()) {
            val runName = data["runName"].orEmpty()

            val title = when {
                runName.isNotEmpty() -> "Run cancelled: $runName"
                courtName.isNotEmpty() -> "Run cancelled at $courtName"
                else -> "A run you joined was cancelled"
            }

            val text = buildString {
                if (courtName.isNotEmpty()) {
                    append("The run at ")
                    append(courtName)
                    append(" has been cancelled.")
                } else {
                    append("This run has been cancelled.")
                }
            }

            // In-app banner
            NotifBus.emit(
                InAppAlert.RunCancelled(
                    title = title,
                    courtName = courtName,
                    runId = runId
                )
            )

            // System notification (deep links into the run details)
            postSystemNotificationIfAllowed(
                channelId = "runs",
                title = title,
                text = text,
                deeplinkRunId = runId
            )
            return
        }

        // --- Fallback for notification-only messages (unchanged) ---
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
