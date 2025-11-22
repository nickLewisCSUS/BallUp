package com.nicklewis.ballup.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun NotificationSettingsCard(
    runAlertsEnabled: Boolean,
    showSystemWhileOpen: Boolean,
    onRunAlertsChanged: (Boolean) -> Unit,
    onShowSystemWhileOpenChanged: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        ) {

            // --- Run alerts row ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Run alerts",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Notify me when a run opens at a starred court",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Switch(
                    checked = runAlertsEnabled,
                    onCheckedChange = onRunAlertsChanged
                )
            }

            Spacer(Modifier.height(12.dp))
            Divider()
            Spacer(Modifier.height(12.dp))

            // --- System notifications while app open row ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Show system notifications while app is open",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "You’ll still see the in-app banner either way",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Switch(
                    checked = showSystemWhileOpen,
                    onCheckedChange = onShowSystemWhileOpenChanged
                )
            }
        }
    }
}
