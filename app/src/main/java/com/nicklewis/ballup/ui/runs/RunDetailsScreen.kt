package com.nicklewis.ballup.ui.runs

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.nicklewis.ballup.firebase.joinRun
import com.nicklewis.ballup.firebase.leaveRun
import com.nicklewis.ballup.util.openDirections
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunDetailsScreen(
    runId: String,
    onBack: (() -> Unit)? = null
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val uid = remember { FirebaseAuth.getInstance().currentUser?.uid }
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    var courtLat by remember { mutableStateOf<Double?>(null) }
    var courtLng by remember { mutableStateOf<Double?>(null) }

    var run by remember { mutableStateOf<RunDoc?>(null) }
    var courtName by remember { mutableStateOf<String?>(null) }
    var isMember by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }

    // Host + players profile info
    var hostProfile by remember { mutableStateOf<PlayerProfile?>(null) }
    var playerProfiles by remember { mutableStateOf<Map<String, PlayerProfile>>(emptyMap()) }

    // ----- listen to run -----
    DisposableEffect(runId) {
        var reg: ListenerRegistration? = null
        reg = db.collection("runs").document(runId)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    error = "Failed to load run"
                    Log.e("RunDetails", "run listen error", e)
                    loading = false
                    return@addSnapshotListener
                }
                if (snap == null || !snap.exists()) {
                    error = "Run not found"
                    run = null
                    loading = false
                    return@addSnapshotListener
                }
                val data = snap.data ?: emptyMap<String, Any>()
                run = RunDoc.from(data, snap.reference)
                loading = false
            }
        onDispose { reg?.remove() }
    }

    // ----- fetch court meta -----
    LaunchedEffect(run?.courtId) {
        val cid = run?.courtId ?: return@LaunchedEffect
        courtName = null
        courtLat = null
        courtLng = null

        try {
            val doc = db.collection("courts").document(cid).get().await()
            val data = doc.data
            courtName = (data?.get("name") as? String) ?: cid
            val geo = data?.get("geo") as? Map<*, *>
            courtLat = (geo?.get("lat") as? Number)?.toDouble()
            courtLng = (geo?.get("lng") as? Number)?.toDouble()
        } catch (e: Exception) {
            Log.w("RunDetails", "court fetch failed", e)
            courtName = run?.courtId
        }
    }

    // ----- membership -----
    LaunchedEffect(run, uid) {
        val r = run ?: return@LaunchedEffect
        val me = uid
        isMember = when {
            me == null -> false
            r.hostId == me -> true
            r.hostUid == me -> true
            r.playerIds.contains(me) -> true
            else -> false
        }
    }

    // ----- host profile lookup -----
    LaunchedEffect(run?.hostUid ?: run?.hostId) {
        val r = run ?: return@LaunchedEffect
        hostProfile = null

        val hostUid = r.hostUid ?: r.hostId
        if (hostUid.isNullOrBlank()) return@LaunchedEffect

        hostProfile = lookupUserProfile(db, hostUid)
    }

    // ----- player profiles lookup -----
    LaunchedEffect(run?.playerIds?.joinToString(",")) {
        val r = run ?: return@LaunchedEffect
        val ids = r.playerIds
        if (ids.isEmpty()) {
            playerProfiles = emptyMap()
            return@LaunchedEffect
        }

        val map = mutableMapOf<String, PlayerProfile>()
        for (id in ids) {
            val profile = lookupUserProfile(db, id)
            if (profile != null) {
                map[id] = profile
            }
        }
        playerProfiles = map
    }

    // ----- UI -----
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(courtName?.let { "Run at $it" } ?: "Run Details") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    val r = run
                    if (r != null && uid != null && (r.hostId == uid || r.hostUid == uid)) {
                        IconButton(onClick = { showEdit = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit run")
                        }
                    }
                }
            )
        }
    ) { padding ->
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 56.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)

                run == null -> Text("Run not found")

                else -> {
                    val r = run!!
                    val open = (r.maxPlayers - r.playerCount).coerceAtLeast(0)
                    val ctx = LocalContext.current
                    val isHost = uid != null && (r.hostId == uid || r.hostUid == uid)
                    val hostUidForSort = r.hostUid ?: r.hostId

                    // Header card
                    Surface(
                        tonalElevation = 2.dp,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = r.name?.ifBlank { "Pickup Run" } ?: "Pickup Run",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = formatWindow(r.startsAt, r.endsAt),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = courtName ?: r.courtId.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Mode • ${r.mode ?: "Unknown"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Players • ${r.playerCount}/${r.maxPlayers}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Text(
                                text = "Status • " + when (r.status) {
                                    "active" -> "Active"
                                    "cancelled" -> "Cancelled"
                                    "inactive" -> "Inactive"
                                    else -> r.status ?: "Unknown"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (r.status != "active")
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (open > 0 && r.status == "active") {
                                Text(
                                    "$open spot${if (open == 1) "" else "s"} left",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else if (r.status == "active") {
                                Text(
                                    "Full",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }

                    // Notice when run is ended/cancelled
                    if (r.status != "active") {
                        Text(
                            text = "This run has ended and is no longer joinable.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Players list card – cleaner tags layout
                    if (r.playerIds.isNotEmpty()) {
                        Text("Players", style = MaterialTheme.typography.titleMedium)
                        Surface(
                            tonalElevation = 1.dp,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Host first, then others by username
                                val sortedIds = r.playerIds.sortedWith(
                                    compareBy(
                                        { pid -> if (pid == hostUidForSort) 0 else 1 },
                                        { pid -> playerProfiles[pid]?.username ?: "" }
                                    )
                                )

                                sortedIds.forEach { pid ->
                                    val profile = playerProfiles[pid]
                                    val isRowHost = pid == hostUidForSort

                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = profile?.username ?: pid,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isRowHost) FontWeight.SemiBold else FontWeight.Normal
                                            )
                                            if (isRowHost) {
                                                AssistChip(
                                                    onClick = {},
                                                    enabled = false,
                                                    label = { Text("Host") }
                                                )
                                            }
                                        }

                                        // Compact tags line: skill • playstyle • height
                                        val tagPieces = listOfNotNull(
                                            profile?.skillLevel,
                                            profile?.playStyle,
                                            profile?.heightBracket
                                        )
                                        if (tagPieces.isNotEmpty()) {
                                            Text(
                                                text = tagPieces.joinToString(" • "),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        // Favorite courts
                                        val fav = profile?.favoriteCourts
                                        if (fav != null && fav.isNotEmpty()) {
                                            Text(
                                                text = "Courts: ${fav.joinToString(", ")}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Actions row
                    var joining by remember { mutableStateOf(false) }
                    var leaving by remember { mutableStateOf(false) }
                    var ending by remember { mutableStateOf(false) }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (!isMember) {
                            val canJoin = r.status == "active" && open > 0 && uid != null
                            Button(
                                onClick = {
                                    if (!canJoin) return@Button
                                    joining = true
                                    scope.launch {
                                        try {
                                            joinRun(db, runId, uid!!)
                                        } catch (e: Exception) {
                                            Log.e("RunDetails", "joinRun failed", e)
                                        } finally {
                                            joining = false
                                        }
                                    }
                                },
                                enabled = canJoin && !joining,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 44.dp)
                            ) {
                                Text(if (joining) "Joining…" else "Join Run")
                            }
                        } else if (isHost) {
                            OutlinedButton(
                                onClick = {
                                    ending = true
                                    scope.launch {
                                        try {
                                            r.ref.update(
                                                mapOf(
                                                    "status" to "cancelled",
                                                    "lastHeartbeatAt" to FieldValue.serverTimestamp()
                                                )
                                            ).await()
                                            onBack?.invoke()
                                        } catch (e: Exception) {
                                            Log.e("RunDetails", "endRun failed", e)
                                            error = "Failed to end run."
                                        } finally {
                                            ending = false
                                        }
                                    }
                                },
                                enabled = !ending && r.status == "active",
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 44.dp)
                            ) {
                                Text(if (ending) "Ending…" else "End Run")
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    if (uid == null) return@OutlinedButton
                                    leaving = true
                                    scope.launch {
                                        try {
                                            leaveRun(db, runId, uid)
                                        } catch (e: Exception) {
                                            Log.e("RunDetails", "leaveRun failed", e)
                                        } finally {
                                            leaving = false
                                        }
                                    }
                                },
                                enabled = !leaving,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 44.dp)
                            ) {
                                Text(if (leaving) "Leaving…" else "Leave Run")
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                val lat = courtLat
                                val lng = courtLng
                                val name = courtName ?: "Court"
                                if (lat != null && lng != null) {
                                    openDirections(ctx, lat, lng, name)
                                }
                            },
                            enabled = courtLat != null && courtLng != null,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                        ) {
                            Text("Directions")
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    val hostLabel = hostProfile?.username
                        ?: hostProfile?.displayName
                        ?: r.hostId
                        ?: r.hostUid
                        ?: "unknown"
                    Text(
                        text = "Host: $hostLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (showEdit) {
                        EditRunSheet(
                            run = r,
                            onDismiss = { showEdit = false },
                            onSave = { patch ->
                                scope.launch {
                                    try {
                                        r.ref.update(patch).await()
                                    } catch (e: Exception) {
                                        Log.e("RunDetails", "update failed", e)
                                    } finally {
                                        showEdit = false
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/* ---------- Helpers & local models ---------- */

private data class RunDoc(
    val ref: DocumentReference,
    val courtId: String?,
    val mode: String?,
    val status: String?,
    val maxPlayers: Int,
    val playerCount: Int,
    val hostUid: String?,
    val hostId: String?,
    val playerIds: List<String>,
    val name: String?,
    val startsAt: com.google.firebase.Timestamp?,
    val endsAt: com.google.firebase.Timestamp?
) {
    companion object {
        fun from(data: Map<String, Any>, ref: DocumentReference): RunDoc {
            val courtId = data["courtId"] as? String
            val mode = data["mode"] as? String
            val status = data["status"] as? String ?: "active"
            val maxPlayers = (data["maxPlayers"] as? Number)?.toInt() ?: 10
            val playerCount = (data["playerCount"] as? Number)?.toInt() ?: 0
            val hostUid = data["hostUid"] as? String
            val hostId = data["hostId"] as? String
            @Suppress("UNCHECKED_CAST")
            val playerIds =
                (data["playerIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val name = data["name"] as? String
            val startsAt = data["startsAt"] as? com.google.firebase.Timestamp
            val endsAt = data["endsAt"] as? com.google.firebase.Timestamp
            return RunDoc(
                ref, courtId, mode, status, maxPlayers, playerCount,
                hostUid, hostId, playerIds, name, startsAt, endsAt
            )
        }
    }
}

private data class PlayerProfile(
    val username: String,
    val skillLevel: String?,
    val playStyle: String?,
    val heightBracket: String?,
    val favoriteCourts: List<String>,
    val displayName: String?
)

private suspend fun lookupUserProfile(
    db: FirebaseFirestore,
    uid: String
): PlayerProfile? {
    return try {
        val doc = db.collection("users").document(uid).get().await()
        if (!doc.exists()) {
            Log.d("PROFILE", "No user doc for $uid")
            null
        } else {
            val username = doc.getString("username") ?: uid
            val skillLevel = doc.getString("skillLevel")
            val playStyle = doc.getString("playStyle")
            val heightBracket = doc.getString("heightBracket")
            val displayName = doc.getString("displayName")
            @Suppress("UNCHECKED_CAST")
            val fav = (doc.get("favoriteCourts") as? List<*>)?.mapNotNull { it as? String }
                ?: emptyList()

            PlayerProfile(
                username = username,
                skillLevel = skillLevel,
                playStyle = playStyle,
                heightBracket = heightBracket,
                favoriteCourts = fav,
                displayName = displayName
            )
        }
    } catch (e: Exception) {
        Log.e("PROFILE", "Error looking up $uid", e)
        null
    }
}

private fun formatWindow(
    start: com.google.firebase.Timestamp?,
    end: com.google.firebase.Timestamp?
): String {
    if (start == null || end == null) return "Time: not set"
    val zone = ZoneId.systemDefault()
    val s = start.toDate().toInstant().atZone(zone).toLocalDateTime()
    val e = end.toDate().toInstant().atZone(zone).toLocalDateTime()
    val dFmt = DateTimeFormatter.ofPattern("EEE, MMM d")
    val tFmt = DateTimeFormatter.ofPattern("h:mm a")
    return if (s.toLocalDate() == e.toLocalDate())
        "${dFmt.format(s)} • ${tFmt.format(s)} – ${tFmt.format(e)}"
    else
        "${dFmt.format(s)} ${tFmt.format(s)} → ${dFmt.format(e)} ${tFmt.format(e)}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditRunSheet(
    run: RunDoc,
    onDismiss: () -> Unit,
    onSave: (Map<String, Any?>) -> Unit
) {
    var name by remember { mutableStateOf(run.name.orEmpty()) }
    val modes = listOf("5v5", "4v4", "3v3", "2v2", "Open gym")
    var mode by remember { mutableStateOf(run.mode ?: "5v5") }
    var max by remember { mutableStateOf(run.maxPlayers) }

    fun tsToLocal(ts: com.google.firebase.Timestamp?): LocalDateTime =
        ts?.toDate()?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDateTime()
            ?: LocalDateTime.now()

    var start by remember { mutableStateOf(tsToLocal(run.startsAt)) }
    var end by remember { mutableStateOf(tsToLocal(run.endsAt)) }

    val active = run.status == "active"

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Edit run", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                label = { Text("Run name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    readOnly = true,
                    value = mode,
                    onValueChange = {},
                    label = { Text("Mode") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    modes.forEach {
                        DropdownMenuItem(
                            text = { Text(it) },
                            onClick = { mode = it; expanded = false }
                        )
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
                OutlinedButton(onClick = { start = start.minusHours(1) }, enabled = !active) {
                    Text("−1h")
                }
                OutlinedButton(onClick = { start = start.plusHours(1) }, enabled = !active) {
                    Text("+1h")
                }
                Text(
                    DateTimeFormatter.ofPattern("EEE, MMM d • h:mm a").format(start),
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            Text("End time", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { end = end.minusHours(1) }) { Text("−1h") }
                OutlinedButton(onClick = { end = end.plusHours(1) }) { Text("+1h") }
                Text(
                    DateTimeFormatter.ofPattern("EEE, MMM d • h:mm a").format(end),
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            val endAfterStart = end.isAfter(start)
            val endAfterNowIfActive = !active || end.isAfter(LocalDateTime.now())

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) { Text("Cancel") }

                Button(
                    onClick = {
                        val patch = mutableMapOf<String, Any?>(
                            "name" to name.trim().replace(Regex("\\s+"), " "),
                            "mode" to mode,
                            "maxPlayers" to max,
                            "endsAt" to com.google.firebase.Timestamp(
                                java.util.Date.from(
                                    end.atZone(ZoneId.systemDefault()).toInstant()
                                )
                            ),
                            "lastHeartbeatAt" to FieldValue.serverTimestamp()
                        )
                        if (!active) {
                            patch["startsAt"] = com.google.firebase.Timestamp(
                                java.util.Date.from(
                                    start.atZone(ZoneId.systemDefault()).toInstant()
                                )
                            )
                        }
                        onSave(patch)
                    },
                    enabled = endAfterStart && endAfterNowIfActive && name.trim().length in 3..30,
                    modifier = Modifier.weight(1f)
                ) { Text("Save") }
            }

            Spacer(Modifier.height(8.dp))
            if (active) {
                Text(
                    "Start time locked because the run is active.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}



