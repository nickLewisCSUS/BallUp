package com.nicklewis.ballup.nav

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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.nicklewis.ballup.AddCourtDialog
import com.nicklewis.ballup.ui.courts.CourtsListScreen
import com.nicklewis.ballup.ui.map.CourtsMapScreen
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.nicklewis.ballup.firebase.joinRun
import com.nicklewis.ballup.firebase.leaveRun
import com.nicklewis.ballup.ui.settings.SettingsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BallUpApp() {
    val nav = rememberNavController()
    val items = listOf(Screen.Map, Screen.List, Screen.Settings)
    var showAddCourt by remember { mutableStateOf(false) }
    var showIndoor by rememberSaveable { mutableStateOf(true) }
    var showOutdoor by rememberSaveable { mutableStateOf(true) }

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
        NavHost(
            navController = nav,
            startDestination = Screen.Map.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Map.route) {
                CourtsMapScreen(
                    showIndoor = showIndoor,
                    showOutdoor = showOutdoor,
                    onToggleIndoor = { showIndoor = !showIndoor },
                    onToggleOutdoor = { showOutdoor = !showOutdoor }
                )
            }
            composable(Screen.List.route) {
                val db    = remember { FirebaseFirestore.getInstance() }
                val uid   = FirebaseAuth.getInstance().currentUser?.uid
                val scope = rememberCoroutineScope()

                CourtsListScreen(
                    onStartRun = { courtId ->
                        val hostId = uid ?: "uid_dev"
                        val run = mapOf(
                            "courtId" to courtId,
                            "status" to "active",
                            "startTime" to FieldValue.serverTimestamp(),
                            "hostId" to hostId,
                            "mode" to "5v5",
                            "maxPlayers" to 10,
                            "lastHeartbeatAt" to FieldValue.serverTimestamp(),
                            "playerCount" to 1,
                            "playerIds" to listOfNotNull(hostId)
                        )
                        db.collection("runs").add(run)
                    },
                    onJoinRun = { runId ->
                        if (uid != null) scope.launch { joinRun(db, runId, uid) }
                    },
                    onLeaveRun = { runId ->
                        if (uid != null) scope.launch { leaveRun(db, runId, uid) }
                    }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen()
            }
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
