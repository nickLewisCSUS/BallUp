package com.nicklewis.ballup.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nicklewis.ballup.AddCourtDialog
import com.nicklewis.ballup.ui.courts.CourtsListScreen
import com.nicklewis.ballup.ui.map.CourtsMapScreen
import com.nicklewis.ballup.ui.settings.SettingsScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.nicklewis.ballup.ui.runs.RunDetailsScreen

const val ROUTE_RUN = "run/{runId}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BallUpApp(
    onSignOut: () -> Unit
) {
    val nav = rememberNavController()
    val items = listOf(Screen.Map, Screen.List, Screen.Settings)
    var showAddCourt by remember { mutableStateOf(false) }
    var showIndoor by rememberSaveable { mutableStateOf(true) }
    var showOutdoor by rememberSaveable { mutableStateOf(true) }

    // set & clear the global holder so MainActivity can navigate from pushes
    DisposableEffect(nav) {
        AppNavControllerHolder.navController = nav
        onDispose {
            if (AppNavControllerHolder.navController === nav) {
                AppNavControllerHolder.navController = null
            }
        }
    }
    Scaffold(
        topBar = {
            val title = when (currentRoute(nav)) {
                Screen.List.route     -> "BallUp — Courts"
                Screen.Settings.route -> "BallUp — Settings"
                else                  -> "BallUp — Map"
            }
            TopAppBar(title = { Text(title) })
        },
        bottomBar = {
            NavigationBar {
                val current = currentRoute(nav)
                items.forEach { s ->
                    NavigationBarItem(
                        selected = current == s.route,
                        onClick = {
                            if (current != s.route) {
                                nav.navigate(s.route) {
                                    popUpTo(nav.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(s.icon, contentDescription = s.label) },
                        label = { Text(s.label) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (currentRoute(nav) == Screen.List.route) {
                FloatingActionButton(onClick = { showAddCourt = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add court")
                }
            }
        }
    ) { padding ->
        androidx.compose.foundation.layout.Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            NavHost(
                navController = nav,
                startDestination = Screen.Map.route,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(Screen.Map.route) {
                    CourtsMapScreen(
                        showIndoor = showIndoor,
                        showOutdoor = showOutdoor,
                        onToggleIndoor = { showIndoor = !showIndoor },
                        onToggleOutdoor = { showOutdoor = !showOutdoor },
                        onOpenRunDetails = { runId ->
                            nav.navigate("run/$runId")
                        }
                    )
                }
                composable(Screen.List.route) { CourtsListScreen() }

                // 🔹 Pass onSignOut down to Settings
                composable(Screen.Settings.route) {
                    SettingsScreen(onSignOut = onSignOut)
                }

                composable(
                    route = ROUTE_RUN,
                    arguments = listOf(navArgument("runId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val runId = backStackEntry.arguments?.getString("runId")!!
                    RunDetailsScreen(runId = runId, onBack = { nav.popBackStack() })
                }

                composable("court/{courtId}/runs") { backStack ->
                    val courtId = backStack.arguments?.getString("courtId") ?: return@composable
                    com.nicklewis.ballup.ui.courts.CourtRunsScreen(courtId = courtId)
                }
            }
            InAppAlertsOverlay(nav)
        }
    }

    if (showAddCourt) {
        AddCourtDialog(
            onDismiss = { showAddCourt = false },
            onSaved = { showAddCourt = false }
        )
    }
}

@Composable
private fun currentRoute(nav: NavHostController): String? {
    val entry by nav.currentBackStackEntryAsState()
    return entry?.destination?.route
}
