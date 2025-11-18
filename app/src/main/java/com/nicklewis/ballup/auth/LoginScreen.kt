package com.nicklewis.ballup.ui.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider
import com.nicklewis.ballup.auth.AuthViewModel

// From google-services.json, client_type = 3
private const val WEB_CLIENT_ID =
    "312640310174-naqml8q54f91r03bg9cftfnontgk09st.apps.googleusercontent.com"

@Composable
fun LoginScreen(
    authViewModel: AuthViewModel
) {
    val context = LocalContext.current
    val activity = context as Activity

    val isLoading by authViewModel.isLoading.collectAsState()
    val errorMessage by authViewModel.errorMessage.collectAsState()

    // Build Google Sign-In options once, using the web client ID
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(WEB_CLIENT_ID)
            .requestEmail()
            .build()
    }

    val googleSignInClient = remember {
        GoogleSignIn.getClient(activity, gso)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            authViewModel.signInWithCredential(credential)
        } catch (e: ApiException) {
            // Could optionally surface this to the viewmodel
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Welcome to BallUp",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(Modifier.height(16.dp))

            if (errorMessage != null) {
                Text(
                    text = errorMessage ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = { launcher.launch(googleSignInClient.signInIntent) },
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("Sign in with Google")
            }
        }
    }
}
