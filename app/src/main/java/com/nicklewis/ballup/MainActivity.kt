@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.nicklewis.ballup

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.google.firebase.auth.FirebaseAuth
import com.nicklewis.ballup.nav.BallUpApp
import androidx.lifecycle.lifecycleScope
import com.nicklewis.ballup.data.TokenRepository
import kotlinx.coroutines.launch
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33) {
            val perm = android.Manifest.permission.POST_NOTIFICATIONS
            if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(perm), 1001)
            }
        }

        signInAnonIfNeeded()

        setContent {
            BallUpApp()
        }
    }
    private fun signInAnonIfNeeded() {
        val auth = FirebaseAuth.getInstance()

        if (auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnSuccessListener {
                    Log.d("AUTH", "anon ok: ${it.user?.uid}")
                    //  Save (or resave) the FCM token right after sign-in
                    lifecycleScope.launch { TokenRepository.ensureTokenSaved() }
                }
                .addOnFailureListener { e -> Log.e("AUTH", "anon fail", e) }
        } else {
            Log.d("AUTH", "already signed in: ${auth.currentUser?.uid}")
            // Also save on app start for existing user
            lifecycleScope.launch { TokenRepository.ensureTokenSaved() }
        }
    }


}
