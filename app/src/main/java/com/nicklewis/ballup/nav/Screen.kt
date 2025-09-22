package com.nicklewis.ballup.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings   // 👈 add this
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Map      : Screen("map",      "Map",      Icons.Filled.Map)
    data object List     : Screen("list",     "List",     Icons.Filled.List)
    data object Settings : Screen("settings", "Settings", Icons.Filled.Settings) // 👈 this line
}