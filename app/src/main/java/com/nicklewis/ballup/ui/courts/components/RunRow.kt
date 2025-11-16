package com.nicklewis.ballup.ui.courts.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
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
        open > 0 -> "Open"
        rr.playerCount >= rr.maxPlayers -> "Full"
        else -> "Active"
    }

    val isJoined = currentUid != null && (rr.playerIds?.contains(currentUid) == true)
    val isHost = currentUid != null && rr.hostId == currentUid

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {

            // Header: run name (single line) + status pill
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = rr.name?.ifBlank { "Pickup run" } ?: "Pickup run",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                StatusPill(text = status, filled = status == "Full")
            }

            // Subline: time + players
            Spacer(Modifier.height(2.dp))
            Text(
                text = buildString {
                    if (timeLabel.isNotBlank()) append(timeLabel)
                    if (rr.maxPlayers > 0) {
                        val count = rr.playerCount
                        if (isNotEmpty()) append("  •  ")
                        append("$count/${rr.maxPlayers}")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(6.dp))

            // Footer: View/Manage (left) + Join/Leave (right for non-hosts)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onView) {
                    Text(if (isHost) "Manage" else "View")
                }

                when {
                    !isJoined -> {
                        FilledTonalButton(
                            onClick = onJoin,
                            shape = MaterialTheme.shapes.medium,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.defaultMinSize(minWidth = 0.dp)
                        ) {
                            Text("Join", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    isHost -> {
                        // Host is already in — we don't show a generic "Leave" here.
                        // They manage/end the run from the details screen.
                    }

                    else -> {
                        OutlinedButton(
                            onClick = onLeave,
                            shape = MaterialTheme.shapes.medium,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.defaultMinSize(minWidth = 0.dp)
                        ) {
                            Text("Leave", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, filled: Boolean) {
    val container = if (filled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
    val content = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        color = container,
        shape = MaterialTheme.shapes.small,
        tonalElevation = if (filled) 2.dp else 0.dp
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}
