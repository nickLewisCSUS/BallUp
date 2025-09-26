package com.nicklewis.ballup.fcm

import com.google.firebase.messaging.FirebaseMessagingService
import com.nicklewis.ballup.data.TokenRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.RemoteMessage
import com.nicklewis.ballup.R


class BallUpMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        CoroutineScope(Dispatchers.IO).launch {
            TokenRepository.saveToken(token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val title = remoteMessage.notification?.title ?: "BallUp"
        val body  = remoteMessage.notification?.body ?: "New update"

        // Build + show a basic notification
        val channelId = "runs"
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, "Run Alerts", NotificationManager.IMPORTANCE_HIGH)
            mgr.createNotificationChannel(ch)
        }

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification) // ensure you have one
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        mgr.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), builder.build())
    }
}
