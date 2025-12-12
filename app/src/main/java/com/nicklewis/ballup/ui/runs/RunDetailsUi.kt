package com.nicklewis.ballup.ui.runs

import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.nicklewis.ballup.model.Team
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun RunMetaRow(
    mode: String,
    players: String,
    access: String,
    status: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Mode • $mode", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Players • $players", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Access • $access", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Status • $status", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ExpandableSectionCard(
    title: String,
    countLabel: String,
    initiallyExpanded: Boolean,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(countLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            trailing?.invoke()
        }

        if (expanded) {
            HorizontalDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
fun EmptyHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun PlayerRow(username: String, tags: List<String>, isHost: Boolean) {
    val subtitle = tags.takeIf { it.isNotEmpty() }?.joinToString(" • ")
    ListItem(
        headlineContent = {
            Text(
                text = username,
                fontWeight = if (isHost) FontWeight.SemiBold else FontWeight.Normal
            )
        },
        supportingContent = {
            if (subtitle != null) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            if (isHost) {
                AssistChip(onClick = {}, enabled = false, label = { Text("Host") })
            }
        }
    )
}

@Composable
fun InvitedRow(username: String, tags: List<String>, onRemove: () -> Unit) {
    val subtitle = tags.takeIf { it.isNotEmpty() }?.joinToString(" • ")
    ListItem(
        headlineContent = { Text(username, fontWeight = FontWeight.SemiBold) },
        supportingContent = { if (subtitle != null) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = { TextButton(onClick = onRemove) { Text("Remove") } }
    )
}

@Composable
fun PendingRequestRow(
    username: String,
    tags: List<String>,
    busy: Boolean,
    onApprove: () -> Unit,
    onDeny: () -> Unit
) {
    val subtitle = tags.takeIf { it.isNotEmpty() }?.joinToString(" • ")

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ListItem(
            headlineContent = { Text(username, fontWeight = FontWeight.SemiBold) },
            supportingContent = { if (subtitle != null) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onApprove, enabled = !busy, modifier = Modifier.weight(1f)) {
                Text(if (busy) "…" else "Approve")
            }
            OutlinedButton(onClick = onDeny, enabled = !busy, modifier = Modifier.weight(1f)) {
                Text("Deny")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SquadInviteSheet(
    ownedTeams: List<Team>,
    errorText: String?,
    onDismiss: () -> Unit,
    onInviteTeam: (Team) -> Unit,
    onClearError: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = { onClearError(); onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Invite squad to this run", style = MaterialTheme.typography.titleMedium)

            if (ownedTeams.isEmpty()) {
                Text(
                    "You don’t have any squads yet. Create one from the Squads tab.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                ownedTeams.forEach { team ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(team.name.ifBlank { "Unnamed squad" }, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${team.memberUids.size} players",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { onInviteTeam(team) }) { Text("Invite") }
                        }
                    }
                }
            }

            if (errorText != null) {
                Text(errorText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { onClearError(); onDismiss() }) { Text("Close") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRunSheet(
    run: RunDoc,
    onDismiss: () -> Unit,
    onSave: (Map<String, Any?>) -> Unit
) {
    var name by remember { mutableStateOf(run.name.orEmpty()) }
    val modes = listOf("5v5", "4v4", "3v3", "2v2", "Open gym")
    var mode by remember { mutableStateOf(run.mode ?: "5v5") }
    var max by remember { mutableStateOf(run.maxPlayers) }

    fun tsToLocal(ts: Timestamp?): LocalDateTime =
        ts?.toDate()?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDateTime()
            ?: LocalDateTime.now()

    var start by remember { mutableStateOf(tsToLocal(run.startsAt)) }
    var end by remember { mutableStateOf(tsToLocal(run.endsAt)) }

    val hasStarted = remember(run.startsAt) {
        run.startsAt?.toDate()?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDateTime()
            ?.let { it.isBefore(LocalDateTime.now()) || it.isEqual(LocalDateTime.now()) }
            ?: false
    }

    val active = run.status == "active" || run.status == "scheduled"

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Edit run", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                label = { Text("Run name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    readOnly = true,
                    value = mode,
                    onValueChange = {},
                    label = { Text("Mode") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    modes.forEach {
                        DropdownMenuItem(text = { Text(it) }, onClick = { mode = it; expanded = false })
                    }
                }
            }

            Column {
                val minCap = run.playerIds.size.coerceAtLeast(2)
                Text("Capacity: $max (min $minCap)")
                Slider(
                    value = max.toFloat(),
                    onValueChange = { max = it.toInt().coerceIn(minCap, 30) },
                    valueRange = minCap.toFloat()..30f,
                    steps = (30 - minCap) - 1
                )
            }

            Text("Start time", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { start = start.minusHours(1) }, enabled = !hasStarted) { Text("−1h") }
                OutlinedButton(onClick = { start = start.plusHours(1) }, enabled = !hasStarted) { Text("+1h") }
                Text(DateTimeFormatter.ofPattern("EEE, MMM d • h:mm a").format(start), modifier = Modifier.padding(top = 12.dp))
            }

            Text("End time", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { end = end.minusHours(1) }) { Text("−1h") }
                OutlinedButton(onClick = { end = end.plusHours(1) }) { Text("+1h") }
                Text(DateTimeFormatter.ofPattern("EEE, MMM d • h:mm a").format(end), modifier = Modifier.padding(top = 12.dp))
            }

            val endAfterStart = end.isAfter(start)
            val endAfterNowIfActive = !active || end.isAfter(LocalDateTime.now())

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }

                Button(
                    onClick = {
                        val patch = mutableMapOf<String, Any?>(
                            "name" to name.trim().replace(Regex("\\s+"), " "),
                            "mode" to mode,
                            "maxPlayers" to max,
                            "endsAt" to Timestamp(java.util.Date.from(end.atZone(ZoneId.systemDefault()).toInstant())),
                            "lastHeartbeatAt" to FieldValue.serverTimestamp()
                        )
                        if (!hasStarted) {
                            patch["startsAt"] =
                                Timestamp(java.util.Date.from(start.atZone(ZoneId.systemDefault()).toInstant()))
                        }
                        onSave(patch)
                    },
                    enabled = endAfterStart && endAfterNowIfActive && name.trim().length in 3..30,
                    modifier = Modifier.weight(1f)
                ) { Text("Save") }
            }

            if (hasStarted) {
                Text(
                    "Start time locked because the run has already started.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun InvitePlayerDialog(
    visible: Boolean,
    run: RunDoc?,
    uid: String?,
    db: FirebaseFirestore,
    onDismiss: () -> Unit
) {
    if (!visible || run == null || uid == null) return

    val scope = rememberCoroutineScope()
    var inviteUsername by remember { mutableStateOf("") }
    var inviteBusy by remember { mutableStateOf(false) }
    var inviteError by remember { mutableStateOf<String?>(null) }

    val isHost = uid == run.hostId || uid == run.hostUid
    if (!isHost) return

    AlertDialog(
        onDismissRequest = {
            if (!inviteBusy) {
                onDismiss()
                inviteError = null
            }
        },
        title = { Text("Invite player") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter the username of the player you want to invite.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = inviteUsername,
                    onValueChange = {
                        inviteUsername = it.trim().take(32)
                        inviteError = null
                    },
                    label = { Text("Username") },
                    singleLine = true,
                    isError = inviteError != null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (inviteError != null) {
                    Text(inviteError.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = inviteUsername.isNotBlank() && !inviteBusy,
                onClick = {
                    if (inviteUsername.isBlank()) return@TextButton
                    inviteBusy = true
                    val usernameToFind = inviteUsername
                    val runRef = run.ref

                    scope.launch {
                        try {
                            inviteError = null
                            val snap = db.collection("users")
                                .whereEqualTo("username", usernameToFind)
                                .limit(1)
                                .get()
                                .await()

                            if (snap.isEmpty) {
                                inviteError = "No user found with that username."
                            } else {
                                val invitedUid = snap.documents.first().id
                                when {
                                    invitedUid == uid -> inviteError = "You’re already in this run."
                                    run.allowedUids.contains(invitedUid) -> inviteError = "This user is already invited."
                                    run.playerIds.contains(invitedUid) -> inviteError = "This user is already playing."
                                    else -> {
                                        runRef.update("allowedUids", FieldValue.arrayUnion(invitedUid)).await()
                                        onDismiss()
                                        inviteUsername = ""
                                        inviteError = null
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("RunDetails", "invite user failed", e)
                            inviteError = "Failed to send invite. Try again."
                        } finally {
                            inviteBusy = false
                        }
                    }
                }
            ) {
                if (inviteBusy) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Text("Invite")
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !inviteBusy, onClick = { onDismiss(); inviteError = null }) {
                Text("Cancel")
            }
        }
    )
}
