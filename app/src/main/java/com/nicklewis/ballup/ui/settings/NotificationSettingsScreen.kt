package com.nicklewis.ballup.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nicklewis.ballup.vm.PrefsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefsVm: PrefsViewModel = viewModel(factory = PrefsViewModel.factory(context))
    val prefs by prefsVm.prefs.collectAsState()

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            // Run alerts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
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

            // System notifications while app is open
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
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
        }
    }
}
