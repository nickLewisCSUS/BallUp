package com.nicklewis.ballup.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.nicklewis.ballup.CourtsMapScreen
import com.nicklewis.ballup.CourtsScreen
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.saveable.rememberSaveable
import com.nicklewis.ballup.AddCourtDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BallUpApp() {
    val nav = rememberNavController()
    val items = listOf(Screen.Map, Screen.List)
    var showAddCourt by remember { mutableStateOf(false) }
    var showIndoor by rememberSaveable { mutableStateOf(true) }
    var showOutdoor by rememberSaveable { mutableStateOf(true) }

    Scaffold(
        topBar = {
            val title = when (currentRoute(nav)) {
                Screen.List.route -> "BallUp — Courts"
                else -> "BallUp — Map"
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
                                    launchSingleTop = true; restoreState = true
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
            composable(Screen.Map.route)  {
                CourtsMapScreen(
                    showIndoor = showIndoor,
                    showOutdoor = showOutdoor,
                    onToggleIndoor = { showIndoor = !showIndoor },
                    onToggleOutdoor = { showOutdoor = !showOutdoor }
                )
            }
            composable(Screen.List.route) {
                CourtsScreen(
                    showIndoor = showIndoor,
                    showOutdoor = showOutdoor,
                    onToggleIndoor = { showIndoor = !showIndoor },
                    onToggleOutdoor = { showOutdoor = !showOutdoor }
                )
            }
        }

        if (showAddCourt) {
            AddCourtDialog(
                onDismiss = { showAddCourt = false },
                onSaved = { showAddCourt = false }
            )
        }
    }
}


@Composable
private fun currentRoute(nav: NavHostController): String? {
    val entry by nav.currentBackStackEntryAsState()
    return entry?.destination?.route
}
