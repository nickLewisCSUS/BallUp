package com.nicklewis.ballup.ui.map

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import com.nicklewis.ballup.data.cancelJoinRequest
import com.nicklewis.ballup.data.joinRun
import com.nicklewis.ballup.data.leaveRun
import com.nicklewis.ballup.data.requestJoinRun
import com.nicklewis.ballup.model.Court
import com.nicklewis.ballup.model.Run
import com.nicklewis.ballup.ui.courts.components.RunRow
import com.nicklewis.ballup.util.RowRun
import com.nicklewis.ballup.util.openDirections
import com.nicklewis.ballup.vm.StarsViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
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
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }

    val today = remember { LocalDate.now() }
    val nowMs = remember { System.currentTimeMillis() }

    // runId -> pending request (HOST_APPROVAL)
    val pendingRequests = remember { mutableStateMapOf<String, Boolean>() }

    // ✅ Build runs like CourtCard does: include active + scheduled, hide ended
    val runsForSheet: List<RowRun> = remember(runs, courtId) {
        val nowMillis = System.currentTimeMillis()

        runs.asSequence()
            .filter { (_, run) ->
                run.courtId == courtId &&
                        (run.status == "active" || run.status == "scheduled")
            }
            .mapNotNull { (runId, run) ->
                val startsAt = run.startsAt ?: return@mapNotNull null
                val endsAt = run.endsAt

                // ✅ only show today (same behavior you had)
                val runDate = startsAt.toDate().toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                if (runDate != today) return@mapNotNull null

                // ✅ hide ended runs (same idea as CourtRunsScreen / CourtCard behavior)
                val endMs = endsAt?.toDate()?.time
                    ?: startsAt.toDate().time
                if (endMs < nowMillis) return@mapNotNull null

                val playerIds = run.playerIds.orEmpty()
                val allowedUids = run.allowedUids.orEmpty()

                val playerCount = run.playerCount
                    ?: playerIds.size

                val maxPlayers = run.maxPlayers ?: 0

                val access = run.access ?: "OPEN"
                val hostId = run.hostId ?: ""
                val hostUid = run.hostId ?: ""

                RowRun(
                    id = runId,
                    name = run.name,
                    startsAt = run.startsAt,
                    endsAt = run.endsAt,
                    playerCount = playerCount,
                    maxPlayers = maxPlayers,
                    playerIds = playerIds,
                    access = access,
                    hostId = hostId,
                    hostUid = hostUid,
                    allowedUids = allowedUids
                )
            }
            .sortedBy { it.startsAt?.toDate()?.time ?: Long.MAX_VALUE }
            .toList()
    }

    // ✅ count “running” as live right now
    val liveCount = remember(runsForSheet) {
        val nowMillis = System.currentTimeMillis()
        runsForSheet.count { rr ->
            val start = rr.startsAt?.toDate()?.time ?: Long.MAX_VALUE
            val end = rr.endsAt?.toDate()?.time ?: start
            nowMillis in start..end
        }
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

            // ✅ Runs section
            if (runsForSheet.isNotEmpty()) {
                if (liveCount > 0) {
                    Text(
                        text = "Pickup running: $liveCount run" + if (liveCount == 1) "" else "s",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = "Runs today: ${runsForSheet.size}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    runsForSheet.forEach { rr ->
                        RunRow(
                            rr = rr,
                            currentUid = uid,
                            onView = { onOpenRunDetails(rr.id) },
                            onJoin = {
                                if (uid == null) return@RunRow
                                scope.launch {
                                    try {
                                        joinRun(db, rr.id, uid)
                                    } catch (e: Exception) {
                                        Log.e("CourtBottomSheet", "joinRun failed", e)
                                    }
                                }
                            },
                            onRequestJoin = {
                                if (uid == null) return@RunRow
                                scope.launch {
                                    try {
                                        requestJoinRun(db, rr.id, uid)
                                        pendingRequests[rr.id] = true
                                    } catch (e: Exception) {
                                        Log.e("CourtBottomSheet", "requestJoinRun failed", e)
                                        if (e.message?.contains("already requested", ignoreCase = true) == true) {
                                            pendingRequests[rr.id] = true
                                        }
                                    }
                                }
                            },
                            onLeave = {
                                if (uid == null) return@RunRow
                                scope.launch {
                                    try {
                                        leaveRun(db, rr.id, uid)
                                    } catch (e: Exception) {
                                        Log.e("CourtBottomSheet", "leaveRun failed", e)
                                    }
                                }
                            },
                            hasPendingRequest = (pendingRequests[rr.id] == true),
                            onCancelRequest = {
                                if (uid == null) return@RunRow
                                scope.launch {
                                    try {
                                        cancelJoinRequest(db, rr.id, uid)
                                        pendingRequests[rr.id] = false
                                    } catch (e: Exception) {
                                        Log.e("CourtBottomSheet", "cancelJoinRequest failed", e)
                                    }
                                }
                            }
                        )
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
