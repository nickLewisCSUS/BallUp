package com.nicklewis.ballup.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.nicklewis.ballup.R
import com.nicklewis.ballup.data.TokenRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// re-subscribe imports
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

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
            } catch (_: Exception) {
                // optionally Log.w("BallUpFCM", "onNewToken re-subscribe failed", e)
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val title = remoteMessage.notification?.title ?: "BallUp"
        val body  = remoteMessage.notification?.body  ?: "New update"

        val channelId = "runs"
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, "Run Alerts", NotificationManager.IMPORTANCE_HIGH)
            mgr.createNotificationChannel(ch)
        }

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        mgr.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), builder.build())
    }
}
