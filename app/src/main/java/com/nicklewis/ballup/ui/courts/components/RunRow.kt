package com.nicklewis.ballup.ui.courts.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nicklewis.ballup.util.RowRun
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun RunRow(
    rr: RowRun,
    currentUid: String?,
    onView: () -> Unit,
    onJoin: () -> Unit,
    onLeave: () -> Unit
) {
    val zone = ZoneId.systemDefault()
    val s = rr.startsAt?.toDate()?.toInstant()?.atZone(zone)?.toLocalDateTime()
    val e = rr.endsAt?.toDate()?.toInstant()?.atZone(zone)?.toLocalDateTime()
    val tFmt = DateTimeFormatter.ofPattern("h:mm a")

    val timeLabel = when {
        s != null && e != null -> "${tFmt.format(s)}–${tFmt.format(e)}"
        s != null -> tFmt.format(s)
        else -> ""
    }

    val open = (rr.maxPlayers - rr.playerCount).coerceAtLeast(0)
    val status = when {
        open > 0 -> "Open • $open left"
        rr.playerCount >= rr.maxPlayers -> "Full"
        else -> "Active"
    }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    rr.name?.ifBlank { "Pickup run" } ?: "Pickup run",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                AssistChip(onClick = {}, label = { Text(status) })
                Spacer(Modifier.weight(1f))
                if (timeLabel.isNotBlank()) {
                    Text(timeLabel, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onView) { Text("View run") }
                val alreadyIn = currentUid != null && (rr.playerIds?.contains(currentUid) == true)
                if (alreadyIn) {
                    OutlinedButton(onClick = onLeave) { Text("Leave") }
                } else {
                    OutlinedButton(onClick = onJoin) { Text("Join") }
                }
            }
        }
    }
}
