package com.nicklewis.ballup.fcm

import com.google.firebase.messaging.FirebaseMessagingService
import com.nicklewis.ballup.data.TokenRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BallUpMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        CoroutineScope(Dispatchers.IO).launch {
            TokenRepository.saveToken(token)
        }
    }
}
