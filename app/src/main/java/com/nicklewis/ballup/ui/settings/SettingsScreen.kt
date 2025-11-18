package com.nicklewis.ballup.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nicklewis.ballup.vm.PrefsViewModel
import com.nicklewis.ballup.auth.AuthViewModel

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefsVm: PrefsViewModel = viewModel(factory = PrefsViewModel.factory(context))

    // NEW: AuthViewModel for sign out
    val authViewModel: AuthViewModel = viewModel()

    val prefs by prefsVm.prefs.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))

        // Run alerts (existing)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("Run alerts", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Notify me when a run opens at a starred court",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = prefs.runAlerts,
                onCheckedChange = { prefsVm.setRunAlerts(it) }
            )
        }

        Spacer(Modifier.height(16.dp))

        // Notify in foreground (existing)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Show system notifications while app is open",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "You’ll still see the in-app banner either way",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = prefs.notifyWhileForeground,
                onCheckedChange = { prefsVm.setNotifyWhileForeground(it) }
            )
        }

        Spacer(Modifier.height(32.dp))

        // --- NEW SIGN OUT BUTTON ---
        Button(
            onClick = { authViewModel.signOut() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Sign Out")
        }
    }
}