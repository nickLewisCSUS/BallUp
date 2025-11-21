package com.nicklewis.ballup.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nicklewis.ballup.vm.PrefsViewModel

@Composable
fun SettingsScreen(
    onEditProfile: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val prefsVm: PrefsViewModel = viewModel(factory = PrefsViewModel.factory(context))
    val prefs by prefsVm.prefs.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))

        // ---- ACCOUNT / PROFILE SECTION ----
        Text(
            text = "Account",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))

        // "Edit Profile" row
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onEditProfile() },
            tonalElevation = 1.dp,
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Edit profile",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Update username, skill level, play style, height & favorite courts",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                // You can later swap this for an icon (chevron) if you want
                Text(
                    text = "›",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        // ---- NOTIFICATIONS SECTION ----
        Text(
            text = "Notifications",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))

        // Run alerts
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("Run alerts", style = MaterialTheme.typography.bodyLarge)
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

        // Foreground notifications
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Show system notifications while app is open",
                    style = MaterialTheme.typography.bodyLarge
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

        // SIGN OUT
        Button(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Sign Out")
        }
    }
}
