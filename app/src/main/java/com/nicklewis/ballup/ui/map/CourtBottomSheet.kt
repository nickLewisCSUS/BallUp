package com.nicklewis.ballup.ui.map

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.nicklewis.ballup.data.CourtLite
import com.nicklewis.ballup.data.joinRun
import com.nicklewis.ballup.data.leaveRun
import com.nicklewis.ballup.data.requestJoinRun
import com.nicklewis.ballup.model.Court
import com.nicklewis.ballup.model.Run
import com.nicklewis.ballup.model.RunAccess
import com.nicklewis.ballup.util.openDirections
import com.nicklewis.ballup.vm.StarsViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CourtBottomSheet(
    selected: Pair<String, Court>,
    runs: List<Pair<String, Run>>,
    userNames: Map<String, String>,
    starredIds: Set<String>,
    starsVm: StarsViewModel,
    onOpenRunDetails: (runId: String) -> Unit,
    onDismiss: () -> Unit,
    onStartRunHere: (courtId: String) -> Unit
) {
    val (courtId, court) = selected
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()

    val courtRuns = runs.filter { (_, run) ->
        if (run.courtId != courtId || run.status != "active") return@filter false
        val ts = run.startsAt ?: return@filter false
        val runDate = ts.toDate().toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        runDate == today
    }

    val isStarred = courtId in starredIds

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header row: court name + star button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    court.name.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = {
                        val lat = court.geo?.lat ?: 0.0
                        val lng = court.geo?.lng ?: 0.0

                        val courtLite = CourtLite(
                            id = courtId,
                            name = court.name.orEmpty(),
                            lat = lat,
                            lng = lng
                        )

                        starsVm.toggle(
                            court = courtLite,
                            star = !isStarred,
                            runAlertsEnabled = false
                        )
                    }
                ) {
                    Icon(
                        imageVector = if (isStarred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (isStarred) "Unfavorite court" else "Favorite court"
                    )
                }
            }

            Text(court.address.orEmpty(), style = MaterialTheme.typography.bodyMedium)
            Text(
                listOfNotNull(
                    court.type?.uppercase(),
                    if (court.amenities?.lights == true) "Lights" else null,
                    if (court.amenities?.restrooms == true) "Restrooms" else null
                ).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall
            )

            // Show today's runs (if any)
            if (courtRuns.isNotEmpty()) {
                Text(
                    text = "Pickup running: ${courtRuns.size} run" +
                            if (courtRuns.size == 1) "" else "s",
                    style = MaterialTheme.typography.bodyMedium
                )

                courtRuns.forEach { (runId, currentRun) ->
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        tonalElevation = 2.dp,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val hostName = currentRun.hostId
                                ?.let { userNames[it] }
                                ?: "Host"

                            Text(
                                text = currentRun.mode?.let { "Pickup • $it" } ?: "Pickup run",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Host: $hostName",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Players: ${currentRun.playerCount}/${currentRun.maxPlayers}",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            AttendeeChips(
                                currentRun = currentRun,
                                isHost = (uid != null && currentRun.hostId == uid),
                                runId = runId,
                                uid = uid,
                                userNames = userNames
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val alreadyIn = uid != null &&
                                        (currentRun.playerIds?.contains(uid) == true)
                                val isHost = uid != null && currentRun.hostId == uid

                                val accessEnum = try {
                                    RunAccess.valueOf(
                                        currentRun.access ?: RunAccess.OPEN.name
                                    )
                                } catch (_: IllegalArgumentException) {
                                    RunAccess.OPEN
                                }

                                val allowedUids = currentRun.allowedUids ?: emptyList()
                                val isAllowedForInviteOnly =
                                    uid != null && allowedUids.contains(uid)

                                val openSlots =
                                    (currentRun.maxPlayers - currentRun.playerCount)
                                        .coerceAtLeast(0)
                                val canAct = uid != null && openSlots > 0

                                if (!alreadyIn) {
                                    when (accessEnum) {
                                        RunAccess.OPEN -> {
                                            val canJoin = canAct
                                            Button(
                                                onClick = {
                                                    if (!canJoin || uid == null) return@Button
                                                    scope.launch {
                                                        try {
                                                            joinRun(db, runId, uid)
                                                        } catch (e: Exception) {
                                                            Log.e("JOIN", "Failed", e)
                                                        }
                                                    }
                                                },
                                                enabled = canJoin,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(if (openSlots > 0) "Join" else "Full")
                                            }
                                        }

                                        RunAccess.HOST_APPROVAL -> {
                                            Button(
                                                onClick = {
                                                    if (!canAct || uid == null) return@Button
                                                    scope.launch {
                                                        try {
                                                            requestJoinRun(db, runId, uid)
                                                        } catch (e: Exception) {
                                                            Log.e("JOIN_REQ", "Failed", e)
                                                        }
                                                    }
                                                },
                                                enabled = canAct,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    if (openSlots > 0)
                                                        "Request to join"
                                                    else
                                                        "Full"
                                                )
                                            }
                                        }

                                        RunAccess.INVITE_ONLY -> {
                                            if (isAllowedForInviteOnly && canAct) {
                                                Button(
                                                    onClick = {
                                                        if (!canAct || uid == null) return@Button
                                                        scope.launch {
                                                            try {
                                                                joinRun(db, runId, uid)
                                                            } catch (e: Exception) {
                                                                Log.e("JOIN", "Failed", e)
                                                            }
                                                        }
                                                    },
                                                    enabled = canAct,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text("Join")
                                                }
                                            } else {
                                                OutlinedButton(
                                                    onClick = {},
                                                    enabled = false,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text("Invite only")
                                                }
                                            }
                                        }
                                    }
                                } else if (!isHost) {
                                    // Regular player can leave
                                    OutlinedButton(
                                        onClick = {
                                            if (uid != null) {
                                                scope.launch {
                                                    try {
                                                        leaveRun(db, runId, uid)
                                                    } catch (e: Exception) {
                                                        Log.e("LEAVE", "Failed", e)
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Leave") }
                                }

                                // Everyone (including host) can open details
                                OutlinedButton(
                                    onClick = { onOpenRunDetails(runId) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(if (isHost) "Manage run" else "View details")
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
            }

            // Start run + Directions row
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { onStartRunHere(courtId) },
                    modifier = Modifier.weight(1f)
                ) { Text("Start run here") }

                val lat = court.geo?.lat
                val lng = court.geo?.lng
                OutlinedButton(
                    enabled = lat != null && lng != null,
                    onClick = {
                        if (lat != null && lng != null) {
                            openDirections(context, lat, lng, court.name)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Directions") }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AttendeeChips(
    currentRun: Run,
    isHost: Boolean,
    runId: String?,
    uid: String?,
    userNames: Map<String, String>
) {
    val scope = rememberCoroutineScope()
    val db = FirebaseFirestore.getInstance()

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        currentRun.playerIds.orEmpty().forEach { pid ->
            val baseName = userNames[pid] ?: pid.takeLast(6)
            val labelText =
                if (pid == currentRun.hostId) "Host • $baseName" else baseName

            AssistChip(
                onClick = {},
                label = { Text(labelText) },
                trailingIcon = if (isHost && pid != currentRun.hostId) {
                    {
                        IconButton(
                            onClick = {
                                if (runId != null && uid != null) {
                                    scope.launch {
                                        try {
                                            com.nicklewis.ballup.data.kickPlayer(
                                                db,
                                                runId,
                                                uid,
                                                pid
                                            )
                                        } catch (e: Exception) {
                                            Log.e("RUN", "kick", e)
                                        }
                                    }
                                }
                            }
                        ) { Icon(Icons.Default.Close, contentDescription = "Kick") }
                    }
                } else null
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}
