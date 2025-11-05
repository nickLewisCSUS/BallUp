package com.nicklewis.ballup.ui.runs

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.nicklewis.ballup.firebase.joinRun
import com.nicklewis.ballup.firebase.leaveRun
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.ui.platform.LocalContext
import com.nicklewis.ballup.util.openDirections

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunDetailsScreen(
    runId: String,
    onBack: (() -> Unit)? = null
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val uid = remember { FirebaseAuth.getInstance().currentUser?.uid }
    val scope = rememberCoroutineScope()

    // ----- State -----
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    var courtLat by remember { mutableStateOf<Double?>(null) }
    var courtLng by remember { mutableStateOf<Double?>(null) }

    var run by remember { mutableStateOf<RunDoc?>(null) }
    var courtName by remember { mutableStateOf<String?>(null) }
    var isMember by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }

    // ----- Listen to run doc -----
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
                val rd = RunDoc.from(data, snap.reference)
                run = rd
                loading = false
            }
        onDispose { reg?.remove() }
    }

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
            courtName = run?.courtId // fallback
        }
    }

    // track membership from the run doc itself
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
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            when {
                loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                error != null -> {
                    Text(text = error!!, color = MaterialTheme.colorScheme.error)
                }
                run == null -> {
                    Text("Run not found")
                }
                else -> {
                    val r = run!!
                    val open = (r.maxPlayers - r.playerCount).coerceAtLeast(0)

                    // --- Run name + time window ---
                    Text(
                        text = r.name?.ifBlank { "Pickup Run" } ?: "Pickup Run",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = formatWindow(r.startsAt, r.endsAt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = courtName ?: r.courtId.orEmpty(),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(6.dp))
                    AssistChipRow(r.status, r.mode, open)

                    Spacer(Modifier.height(12.dp))

                    // Capacity row
                    Text(
                        text = "Players: ${r.playerCount} / ${r.maxPlayers}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (open > 0) {
                        Text(
                            text = "$open spot${if (open == 1) "" else "s"} left",
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(text = "Full", color = MaterialTheme.colorScheme.secondary)
                    }

                    Spacer(Modifier.height(16.dp))

                    // Members list
                    if (r.playerIds.isNotEmpty()) {
                        Text("Players (${r.playerIds.size})", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(r.playerIds) { uidItem ->
                                Text("• $uidItem", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    } else {
                        Spacer(Modifier.height(4.dp))
                    }

                    // Join/Leave actions
                    val joining = remember { mutableStateOf(false) }
                    val leaving = remember { mutableStateOf(false) }
                    val ctx = LocalContext.current

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isMember) {
                            val canJoin = r.status == "active" && open > 0 && uid != null
                            Button(
                                onClick = { /* same as before */ },
                                enabled = canJoin && !joining.value,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (joining.value) "Joining..." else "Join Run")
                            }
                        } else {
                            Button(
                                onClick = { /* same as before */ },
                                enabled = !leaving.value,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (leaving.value) "Leaving..." else "Leave Run")
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
                            enabled = courtLat != null && courtLng != null
                        ) {
                            Text("Directions")
                        }
                    }

                    // Tiny footer
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Host: ${r.hostId ?: r.hostUid ?: "unknown"}",
                        style = MaterialTheme.typography.bodySmall
                    )

                    // Host edit sheet
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

/* ---------- Helpers & simple model ---------- */

@Composable
private fun AssistChipRow(status: String?, mode: String?, open: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!mode.isNullOrBlank()) {
            AssistChip(onClick = {}, label = { Text(mode) })
        }
        if (!status.isNullOrBlank()) {
            AssistChip(onClick = {}, label = { Text(status) })
        }
        AssistChip(onClick = {}, label = { Text(if (open > 0) "$open open" else "Full") })
    }
}

/** Minimal view model for the run document (tolerates missing fields). */
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
    // NEW fields
    val name: String?,
    val startsAt: com.google.firebase.Timestamp?,
    val endsAt: com.google.firebase.Timestamp?
) {
    companion object {
        fun from(data: Map<String, Any>, ref: DocumentReference): RunDoc {
            val courtId    = data["courtId"] as? String
            val mode       = data["mode"] as? String
            val status     = data["status"] as? String ?: "active"
            val maxPlayers = (data["maxPlayers"] as? Number)?.toInt() ?: 10
            val playerCount= (data["playerCount"] as? Number)?.toInt() ?: 0
            val hostUid    = data["hostUid"] as? String
            val hostId     = data["hostId"] as? String
            @Suppress("UNCHECKED_CAST")
            val playerIds  = (data["playerIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val name       = data["name"] as? String
            val startsAt   = data["startsAt"] as? com.google.firebase.Timestamp
            val endsAt     = data["endsAt"] as? com.google.firebase.Timestamp
            return RunDoc(
                ref, courtId, mode, status, maxPlayers, playerCount, hostUid, hostId, playerIds,
                name, startsAt, endsAt
            )
        }
    }
}

private fun formatWindow(
    start: com.google.firebase.Timestamp?, end: com.google.firebase.Timestamp?
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
    // fields (respecting rules: startsAt locked when status == "active")
    var name by remember { mutableStateOf(run.name.orEmpty()) }
    val modes = listOf("5v5","4v4","3v3","2v2","Open gym")
    var mode by remember { mutableStateOf(run.mode ?: "5v5") }
    var max by remember { mutableStateOf(run.maxPlayers) }

    fun tsToLocal(ts: com.google.firebase.Timestamp?): LocalDateTime =
        ts?.toDate()?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDateTime()
            ?: LocalDateTime.now()

    var start by remember { mutableStateOf(tsToLocal(run.startsAt)) }
    var end by remember { mutableStateOf(tsToLocal(run.endsAt)) }

    val active = run.status == "active"

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Edit run", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                label = { Text("Run name") },
                singleLine = true
            )

            // Mode dropdown
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    readOnly = true,
                    value = mode,
                    onValueChange = {},
                    label = { Text("Mode") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.menuAnchor()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    modes.forEach {
                        DropdownMenuItem(text = { Text(it) }, onClick = { mode = it; expanded = false })
                    }
                }
            }

            // Capacity slider; can’t go below current players (rule)
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

            // Times (start locked for active)
            Text("Start time", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { start = start.minusHours(1) }, enabled = !active) { Text("−1h") }
                OutlinedButton(onClick = { start = start.plusHours(1) }, enabled = !active) { Text("+1h") }
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

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = {
                        val patch = mutableMapOf<String, Any?>(
                            "name" to name.trim().replace(Regex("\\s+"), " "),
                            "mode" to mode,
                            "maxPlayers" to max,
                            "endsAt" to com.google.firebase.Timestamp(
                                java.util.Date.from(end.atZone(ZoneId.systemDefault()).toInstant())
                            ),
                            "lastHeartbeatAt" to FieldValue.serverTimestamp()
                        )
                        if (!active) {
                            patch["startsAt"] = com.google.firebase.Timestamp(
                                java.util.Date.from(start.atZone(ZoneId.systemDefault()).toInstant())
                            )
                        }
                        onSave(patch)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = endAfterStart && endAfterNowIfActive && name.trim().length in 3..30
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
