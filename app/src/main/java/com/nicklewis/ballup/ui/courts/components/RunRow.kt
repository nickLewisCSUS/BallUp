// ui/courts/components/RunRow.kt
package com.nicklewis.ballup.ui.courts.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nicklewis.ballup.model.RunAccess
import com.nicklewis.ballup.util.RowRun
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun RunRow(
    rr: RowRun,
    currentUid: String?,
    onView: () -> Unit,
    onJoin: () -> Unit,
    onRequestJoin: () -> Unit,
    onLeave: () -> Unit,
    hasPendingRequest: Boolean,
    onCancelRequest: () -> Unit,

    // ✅ NEW: pass a nice label like username/displayName from CourtsListScreen
    hostLabel: String? = null
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

    val openSlots = (rr.maxPlayers - rr.playerCount).coerceAtLeast(0)

    // --- Decode access safely into enum ---
    val runAccess: RunAccess = remember(rr.access) {
        try {
            RunAccess.valueOf(rr.access)
        } catch (e: IllegalArgumentException) {
            RunAccess.OPEN
        }
    }

    val isJoined = currentUid != null && rr.playerIds?.contains(currentUid) == true

    val isHost = currentUid != null && (
            rr.hostId == currentUid || rr.hostUid == currentUid
            )

    // Host is allowed to leave only if more than 1 player exists
    val hostCanLeave = isHost && rr.playerCount > 1

    // Allow-list check (used for INVITE_ONLY and HOST_APPROVAL)
    val isAllowedByAllowList =
        currentUid != null && rr.allowedUids.contains(currentUid)

    // Status pill text
    val status = when {
        runAccess == RunAccess.INVITE_ONLY && openSlots > 0 -> "Invite only"
        runAccess == RunAccess.HOST_APPROVAL && openSlots > 0 && !isAllowedByAllowList -> "Needs approval"
        runAccess == RunAccess.HOST_APPROVAL && openSlots > 0 && isAllowedByAllowList -> "Invited"
        openSlots > 0 -> "Open"
        rr.playerCount >= rr.maxPlayers -> "Full"
        else -> "Active"
    }

    // ✅ Host label to display
    val hostText = remember(hostLabel, rr.hostId, rr.hostUid) {
        val nice = hostLabel?.trim().orEmpty()
        when {
            nice.isNotBlank() -> nice
            !rr.hostId.isNullOrBlank() -> rr.hostId!!
            !rr.hostUid.isNullOrBlank() -> rr.hostUid!!
            else -> "Unknown"
        }
    }

    var showLeaveConfirm by remember { mutableStateOf(false) }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {

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

            Spacer(Modifier.height(2.dp))

            // Time + count
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

            // ✅ NEW: Host line
            Text(
                text = "Host • $hostText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(6.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                TextButton(onClick = onView) {
                    Text(if (isHost) "Manage" else "View")
                }

                when {
                    // --- Not joined yet: button depends on access ---
                    !isJoined -> {
                        when (runAccess) {
                            RunAccess.OPEN -> {
                                FilledTonalButton(
                                    onClick = onJoin,
                                    shape = MaterialTheme.shapes.medium,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text("Join", style = MaterialTheme.typography.labelLarge)
                                }
                            }

                            RunAccess.HOST_APPROVAL -> {
                                if (isAllowedByAllowList) {
                                    FilledTonalButton(
                                        onClick = onJoin,
                                        shape = MaterialTheme.shapes.medium,
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Text("Join", style = MaterialTheme.typography.labelLarge)
                                    }
                                } else if (!hasPendingRequest) {
                                    FilledTonalButton(
                                        onClick = onRequestJoin,
                                        shape = MaterialTheme.shapes.medium,
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Text("Request", style = MaterialTheme.typography.labelLarge)
                                    }
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilledTonalButton(
                                            onClick = { /* no-op */ },
                                            enabled = false,
                                            shape = MaterialTheme.shapes.medium,
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                        ) {
                                            Text("Requested", style = MaterialTheme.typography.labelLarge)
                                        }
                                        TextButton(onClick = onCancelRequest) {
                                            Text("Undo", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }

                            RunAccess.INVITE_ONLY -> {
                                if (isAllowedByAllowList) {
                                    FilledTonalButton(
                                        onClick = onJoin,
                                        shape = MaterialTheme.shapes.medium,
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Text("Join", style = MaterialTheme.typography.labelLarge)
                                    }
                                } else {
                                    FilledTonalButton(
                                        onClick = { /* no-op */ },
                                        enabled = false,
                                        shape = MaterialTheme.shapes.medium,
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Text("Invite only", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                    }

                    // Host with multiple players → host-transfer confirm dialog
                    hostCanLeave -> {
                        OutlinedButton(
                            onClick = { showLeaveConfirm = true },
                            shape = MaterialTheme.shapes.medium,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("Leave", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    // Solo host → cannot leave (Manage button only)
                    isHost -> Unit

                    // Normal player → confirm leave
                    else -> {
                        OutlinedButton(
                            onClick = { showLeaveConfirm = true },
                            shape = MaterialTheme.shapes.medium,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("Leave", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }

    // CONFIRMATION DIALOG (HOST + REGULAR)
    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = {
                Text(if (hostCanLeave) "Leave run?" else "Confirm leave")
            },
            text = {
                Text(
                    if (hostCanLeave)
                        "You are the host. If you leave now, host duties will be transferred to another player.\n\nAre you sure you want to leave?"
                    else
                        "Are you sure you want to leave this run?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLeaveConfirm = false
                        onLeave()
                    }
                ) { Text("Leave") }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StatusPill(text: String, filled: Boolean) {
    val container =
        if (filled) MaterialTheme.colorScheme.surfaceVariant
        else MaterialTheme.colorScheme.surface
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
