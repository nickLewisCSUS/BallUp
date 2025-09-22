package com.nicklewis.ballup.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nicklewis.ballup.vm.PrefsViewModel

@Composable
fun SettingsScreen(prefsVm: PrefsViewModel = viewModel()) {
    val prefs by prefsVm.prefs.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        Spacer(Modifier.height(24.dp))

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
    }
}
