@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.nicklewis.ballup

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.google.firebase.auth.FirebaseAuth
import com.nicklewis.ballup.nav.BallUpApp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        signInAnonIfNeeded()

        setContent {
            BallUpApp()
        }
    }

    private fun signInAnonIfNeeded() {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnSuccessListener { Log.d("AUTH", "anon ok: ${it.user?.uid}") }
                .addOnFailureListener { e -> Log.e("AUTH", "anon fail", e) }
        }
    }
}
