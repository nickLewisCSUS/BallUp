package com.nicklewis.ballup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nicklewis.ballup.auth.AuthViewModel
import com.nicklewis.ballup.nav.BallUpApp
import com.nicklewis.ballup.ui.auth.LoginScreen

@Composable
fun BallUpAppRoot() {
    val authViewModel: AuthViewModel = viewModel()
    val currentUser by authViewModel.currentUser.collectAsState()

    if (currentUser == null) {
        // Not signed in → show Google login
        LoginScreen(authViewModel = authViewModel)
    } else {
        // Signed in → show your existing app
        BallUpMainAppContent(
            onSignOut = { authViewModel.signOut() }
        )
    }
}

@Composable
fun BallUpMainAppContent(
    onSignOut: () -> Unit
) {
    // For now just call your existing root app composable
    BallUpApp()

    // Later, you can pass `onSignOut` into a settings/profile screen button:
    // SettingsScreen(onSignOut = onSignOut)
}
