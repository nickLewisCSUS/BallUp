package com.nicklewis.ballup.ui.runs

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.nicklewis.ballup.firebase.joinRun
import com.nicklewis.ballup.firebase.leaveRun
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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

    var run by remember { mutableStateOf<RunDoc?>(null) }
    var courtName by remember { mutableStateOf<String?>(null) }
    var isMember by remember { mutableStateOf(false) }

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

    // ----- Fetch court name when courtId known -----
    LaunchedEffect(run?.courtId) {
        val cid = run?.courtId ?: return@LaunchedEffect
        try {
            val c = db.collection("courts").document(cid).get().await()
            courtName = c.getString("name") ?: cid
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

                    // Header stats
                    Text(
                        text = courtName ?: r.courtId.orEmpty(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
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

                    // Members list from run.playerIds (optional, simple rendering)
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
                        // Spacer to keep layout comfy
                        Spacer(Modifier.height(4.dp))
                    }

                    // Join/Leave actions
                    val joining = remember { mutableStateOf(false) }
                    val leaving = remember { mutableStateOf(false) }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (!isMember) {
                            val canJoin = r.status == "active" && open > 0 && uid != null
                            Button(
                                onClick = {
                                    if (!canJoin) return@Button
                                    joining.value = true
                                    scope.launch {
                                        try {
                                            joinRun(db, runId, uid!!)
                                        } catch (e: Exception) {
                                            Log.e("RunDetails", "joinRun failed", e)
                                        } finally {
                                            joining.value = false
                                        }
                                    }
                                },
                                enabled = canJoin && !joining.value
                            ) {
                                Text(if (joining.value) "Joining..." else "Join Run")
                            }
                        } else {
                            Button(
                                onClick = {
                                    if (uid == null) return@Button
                                    leaving.value = true
                                    scope.launch {
                                        try {
                                            leaveRun(db, runId, uid)
                                        } catch (e: Exception) {
                                            Log.e("RunDetails", "leaveRun failed", e)
                                        } finally {
                                            leaving.value = false
                                        }
                                    }
                                },
                                enabled = !leaving.value
                            ) {
                                Text(if (leaving.value) "Leaving..." else "Leave Run")
                            }
                        }
                    }

                    // Tiny footer info
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Host: ${r.hostId ?: r.hostUid ?: "unknown"}",
                        style = MaterialTheme.typography.bodySmall
                    )
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
            AssistChip(
                onClick = {},
                label = { Text(mode) },
            )
        }
        if (!status.isNullOrBlank()) {
            AssistChip(
                onClick = {},
                label = { Text(status) }
            )
        }
        AssistChip(
            onClick = {},
            label = { Text(if (open > 0) "$open open" else "Full") }
        )
    }
}

/**
 * Minimal view model for the run document that tolerates missing fields.
 * Adjust to your exact schema if you have a proper Run data class already.
 */
private data class RunDoc(
    val ref: DocumentReference,
    val courtId: String?,
    val mode: String?,
    val status: String?,
    val maxPlayers: Int,
    val playerCount: Int,
    val hostUid: String?,              // you used hostUid here, firestore field is hostId in your startRun()
    val hostId: String?,               // keep both in case
    val playerIds: List<String>        // <-- NEW
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
            return RunDoc(
                ref = ref,
                courtId = courtId,
                mode = mode,
                status = status,
                maxPlayers = maxPlayers,
                playerCount = playerCount,
                hostUid = hostUid,
                hostId = hostId,
                playerIds = playerIds
            )
        }
    }
}
