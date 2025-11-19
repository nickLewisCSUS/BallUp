package com.nicklewis.ballup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.nicklewis.ballup.auth.AuthViewModel
import com.nicklewis.ballup.nav.BallUpApp
import com.nicklewis.ballup.ui.auth.LoginScreen
import com.nicklewis.ballup.ui.profile.ProfileSetupScreen

@Composable
fun BallUpAppRoot() {
    val rootNav = rememberNavController()
    val authVm: AuthViewModel = viewModel()

    NavHost(
        navController = rootNav,
        startDestination = "login"
    ) {

        // ----------------------------
        // 1. LOGIN SCREEN
        // ----------------------------
        composable("login") {

            // Listen once for auth nav events
            LaunchedEffect(Unit) {
                authVm.navEvents.collect { event ->
                    when (event) {
                        is AuthViewModel.AuthNav.ToHome -> {
                            rootNav.navigate("main") {
                                popUpTo("login") { inclusive = true }
                            }
                        }
                        is AuthViewModel.AuthNav.ToProfileSetup -> {
                            // 👇 Only pass uid now
                            rootNav.navigate("profileSetup/${event.uid}") {
                                popUpTo("login") { inclusive = true }
                            }
                        }
                    }
                }
            }

            LoginScreen(
                authViewModel = authVm
            )
        }

        // ----------------------------
        // 2. PROFILE SETUP SCREEN
        // ----------------------------
        composable(
            route = "profileSetup/{uid}",
            arguments = listOf(
                navArgument("uid") { type = NavType.StringType }
            )
        ) { backStack ->
            val uid = backStack.arguments?.getString("uid")!!

            // Grab display name from current Firebase user
            val displayName = Firebase.auth.currentUser?.displayName

            ProfileSetupScreen(
                uid = uid,
                displayName = displayName,
                onProfileSaved = {
                    rootNav.navigate("main") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }

        // ----------------------------
        // 3. MAIN APP (existing nav graph)
        // ----------------------------
        composable("main") {
            BallUpApp(
                onSignOut = {
                    authVm.signOut()
                    rootNav.navigate("login") {
                        // Clear everything and show a fresh login
                        popUpTo(rootNav.graph.startDestinationId) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}

