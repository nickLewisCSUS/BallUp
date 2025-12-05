// BallUpMessagingService.kt
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
        CoroutineScope(Dispatchers.IO).launch {
            val allowWhileForeground = try {
                com.nicklewis.ballup.data.LocalPrefsStore(ctx).prefsFlow.first().notifyWhileForeground
            } catch (_: Throwable) { false }

            val inForeground = applicationIsInForeground()
            if (!inForeground || allowWhileForeground) {
                val intent = Intent(ctx, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("deeplink_runId", deeplinkRunId)
                }
                val pi = PendingIntent.getActivity(
                    ctx, 1001, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                ensureChannel(channelId, "Run Alerts")
                val notif = NotificationCompat.Builder(ctx, channelId)
                    .setSmallIcon(notificationIcon())
                    .setContentTitle(title)
                    .setContentText(text)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build()

                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(
                        ctx, Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) return@launch
                val nm = NotificationManagerCompat.from(ctx)
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

        // 🔹 NEW: direct run invite
        if (type == "run_invite" && runId.isNotEmpty()) {
            val runName = data["runName"].orEmpty()

            val title = if (runName.isNotEmpty()) {
                "Run invite: $runName"
            } else {
                "You were invited to a run"
            }

            val body = if (courtName.isNotEmpty()) {
                "You were invited to a run at $courtName."
            } else {
                "Tap to review your invite."
            }

            // In-app banner
            NotifBus.emit(
                InAppAlert.RunInvite(
                    title = title,
                    courtName = courtName,
                    runId = runId
                )
            )

            // System notification
            val ctx = applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                val allowWhileForeground = try {
                    com.nicklewis.ballup.data.LocalPrefsStore(ctx)
                        .prefsFlow.first().notifyWhileForeground
                } catch (_: Throwable) { false }

                val inForeground = applicationIsInForeground()
                if (!inForeground || allowWhileForeground) {
                    val intent = Intent(ctx, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("deeplink_tab", "courts")
                        // optional: extra to hint that we should open invites
                        putExtra("deeplink_hasRunInvite", true)
                    }
                    val pi = PendingIntent.getActivity(
                        ctx, 3001, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val channelId = "runs"
                    ensureChannel(channelId, "Run Alerts")

                    val notif = NotificationCompat.Builder(ctx, channelId)
                        .setSmallIcon(notificationIcon())
                        .setContentTitle(title)
                        .setContentText(body)
                        .setContentIntent(pi)
                        .setAutoCancel(true)
                        .build()

                    if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(
                            ctx,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) return@launch
                    if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return@launch

                    NotificationManagerCompat.from(ctx).notify(3001, notif)
                }
            }
            return
        }
        // --- Squad: invite accepted (notify owner) ---
        if (type == "team_invite_accepted") {
            val teamId      = data["teamId"].orEmpty()
            val teamName    = data["teamName"].orEmpty()
            val playerName  = data["playerName"].orEmpty()

            val title = when {
                teamName.isNotEmpty() && playerName.isNotEmpty() ->
                    "$playerName joined $teamName"
                teamName.isNotEmpty() ->
                    "Invite accepted for $teamName"
                else ->
                    "Squad invite accepted"
            }

            val body = if (playerName.isNotEmpty()) {
                "$playerName accepted your squad invite."
            } else {
                "Your squad invite was accepted."
            }

            NotifBus.emit(
                InAppAlert.TeamInviteAccepted(
                    title = title,
                    message = body,
                    teamId = teamId
                )
            )

            val ctx = applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                val allowWhileForeground = try {
                    com.nicklewis.ballup.data.LocalPrefsStore(ctx)
                        .prefsFlow.first().notifyWhileForeground
                } catch (_: Throwable) { false }

                val inForeground = applicationIsInForeground()
                if (!inForeground || allowWhileForeground) {
                    val intent = Intent(ctx, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("deeplink_tab", "teams_squads")
                        putExtra("deeplink_teamId", teamId)
                    }
                    val pi = PendingIntent.getActivity(
                        ctx, 2002, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val channelId = "teams"
                    ensureChannel(channelId, "Squad Alerts")

                    val notif = NotificationCompat.Builder(ctx, channelId)
                        .setSmallIcon(notificationIcon())
                        .setContentTitle(title)
                        .setContentText(body)
                        .setContentIntent(pi)
                        .setAutoCancel(true)
                        .build()

                    if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(
                            ctx,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) return@launch
                    if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return@launch

                    NotificationManagerCompat.from(ctx).notify(2002, notif)
                }
            }
            return
        }

        // --- Squad: join request created (notify owner) ---
        if (type == "team_join_requested") {
            val teamId        = data["teamId"].orEmpty()
            val teamName      = data["teamName"].orEmpty()
            val requesterName = data["requesterName"].orEmpty()

            val title = if (teamName.isNotEmpty()) {
                "Join request for $teamName"
            } else {
                "New squad join request"
            }

            val body = if (requesterName.isNotEmpty()) {
                "$requesterName wants to join your squad."
            } else {
                "Someone requested to join your squad."
            }

            NotifBus.emit(
                InAppAlert.TeamJoinRequest(
                    title = title,
                    teamName = teamName,
                    requesterName = requesterName,
                    teamId = teamId
                )
            )

            val ctx = applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                val allowWhileForeground = try {
                    com.nicklewis.ballup.data.LocalPrefsStore(ctx)
                        .prefsFlow.first().notifyWhileForeground
                } catch (_: Throwable) { false }

                val inForeground = applicationIsInForeground()
                if (!inForeground || allowWhileForeground) {
                    val intent = Intent(ctx, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("deeplink_tab", "teams_requests")
                        putExtra("deeplink_teamId", teamId)
                    }
                    val pi = PendingIntent.getActivity(
                        ctx, 2003, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val channelId = "teams"
                    ensureChannel(channelId, "Squad Alerts")

                    val notif = NotificationCompat.Builder(ctx, channelId)
                        .setSmallIcon(notificationIcon())
                        .setContentTitle(title)
                        .setContentText(body)
                        .setContentIntent(pi)
                        .setAutoCancel(true)
                        .build()

                    if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(
                            ctx,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) return@launch
                    if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return@launch

                    NotificationManagerCompat.from(ctx).notify(2003, notif)
                }
            }
            return
        }

        // 🔹 NEW: Squad join approved (notify requester)
        if (type == "team_join_approved") {
            val teamId   = data["teamId"].orEmpty()
            val teamName = data["teamName"].orEmpty()

            val title = if (teamName.isNotEmpty()) {
                "You're in: $teamName"
            } else {
                "Squad request approved"
            }

            val body = if (teamName.isNotEmpty()) {
                "You were added to $teamName."
            } else {
                "Your squad join request was approved."
            }

            // In-app banner
            NotifBus.emit(
                InAppAlert.TeamJoinApproved(
                    title = title,
                    message = body,
                    teamId = teamId
                )
            )

            // System notification
            val ctx = applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                val allowWhileForeground = try {
                    com.nicklewis.ballup.data.LocalPrefsStore(ctx)
                        .prefsFlow.first().notifyWhileForeground
                } catch (_: Throwable) { false }

                val inForeground = applicationIsInForeground()
                if (!inForeground || allowWhileForeground) {
                    val intent = Intent(ctx, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("deeplink_tab", "teams_squads")
                        putExtra("deeplink_teamId", teamId)
                    }
                    val pi = PendingIntent.getActivity(
                        ctx, 2005, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val channelId = "teams"
                    ensureChannel(channelId, "Squad Alerts")

                    val notif = NotificationCompat.Builder(ctx, channelId)
                        .setSmallIcon(notificationIcon())
                        .setContentTitle(title)
                        .setContentText(body)
                        .setContentIntent(pi)
                        .setAutoCancel(true)
                        .build()

                    if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(
                            ctx,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) return@launch
                    if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return@launch

                    NotificationManagerCompat.from(ctx).notify(2005, notif)
                }
            }
            return
        }

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

            NotifBus.emit(
                InAppAlert.RunUpcoming(
                    title = titleNotif,
                    courtName = courtName,
                    runId = runId,
                    minutes = minutes ?: 0
                )
            )

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

            NotifBus.emit(InAppAlert.RunSpots(title, courtName, runId))

            val ctx = applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                val allowWhileForeground = try {
                    com.nicklewis.ballup.data.LocalPrefsStore(ctx)
                        .prefsFlow.first().notifyWhileForeground
                } catch (_: Throwable) { false }

                val inForeground = applicationIsInForeground()
                if (!inForeground || allowWhileForeground) {
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

            NotifBus.emit(InAppAlert.RunCreated(title, courtNameLocal, runIdLocal, timeText))

            postSystemNotificationIfAllowed(
                channelId = "runs",
                title = title,
                text = text,
                deeplinkRunId = runIdLocal
            )
            return
        }

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

            NotifBus.emit(
                InAppAlert.RunCancelled(
                    title = title,
                    courtName = courtName,
                    runId = runId
                )
            )

            postSystemNotificationIfAllowed(
                channelId = "runs",
                title = title,
                text = text,
                deeplinkRunId = runId
            )
            return
        }

        // Fallback
        val fbTitle = msg.notification?.title ?: "BallUp"
        val fbBody  = msg.notification?.body  ?: "New update"
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "runs"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, "Run Alerts", NotificationManager.IMPORTANCE_HIGH)
            mgr.createNotificationChannel(ch)
        }
        val fallback = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(notificationIcon())
            .setContentTitle(fbTitle)
            .setContentText(fbBody)
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

        // --- Squad: deleted (notify members) ---
        if (type == "team_deleted") {
            val teamId   = data["teamId"].orEmpty()
            val teamName = data["teamName"].orEmpty()

            val title = if (teamName.isNotEmpty()) {
                "Squad deleted: $teamName"
            } else {
                "A squad you were in was deleted"
            }

            val body = "This squad was deleted by its owner."

            NotifBus.emit(
                InAppAlert.TeamDeleted(
                    title = title,
                    message = body
                )
            )

            val ctx = applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                val allowWhileForeground = try {
                    com.nicklewis.ballup.data.LocalPrefsStore(ctx)
                        .prefsFlow.first().notifyWhileForeground
                } catch (_: Throwable) { false }

                val inForeground = applicationIsInForeground()
                if (!inForeground || allowWhileForeground) {
                    val intent = Intent(ctx, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("deeplink_tab", "teams_squads")
                    }
                    val pi = PendingIntent.getActivity(
                        ctx, 2004, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val channelIdTeams = "teams"
                    ensureChannel(channelIdTeams, "Squad Alerts")

                    val notif = NotificationCompat.Builder(ctx, channelIdTeams)
                        .setSmallIcon(notificationIcon())
                        .setContentTitle(title)
                        .setContentText(body)
                        .setContentIntent(pi)
                        .setAutoCancel(true)
                        .build()

                    if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(
                            ctx,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) return@launch
                    if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return@launch

                    NotificationManagerCompat.from(ctx).notify(2004, notif)
                }
            }
            return
        }
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
