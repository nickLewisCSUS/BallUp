package com.nicklewis.ballup.ui.runs

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.nicklewis.ballup.data.cancelJoinRequest
import com.nicklewis.ballup.data.joinRun
import com.nicklewis.ballup.data.leaveRun
import com.nicklewis.ballup.data.requestJoinRun
import com.nicklewis.ballup.nav.AppNavControllerHolder
import com.nicklewis.ballup.ui.courts.components.RunRow
import com.nicklewis.ballup.util.RowRun
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourtRunsScreen(
    courtId: String,
    onBack: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    var allRuns by remember { mutableStateOf<List<RowRun>>(emptyList()) }
    var query by remember { mutableStateOf("") }

    // runId -> pending request (HOST_APPROVAL)
    val pendingRequests = remember { mutableStateMapOf<String, Boolean>() }

    // Live listener
    DisposableEffect(courtId) {
        var reg: ListenerRegistration? = null
        isLoading = true
        error = null

        reg = db.collection("runs")
            .whereEqualTo("courtId", courtId)
            .whereIn("status", listOf("active", "scheduled")) // ✅ no cancelled
            .orderBy("startsAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    error = e.message ?: "Failed to load runs"
                    isLoading = false
                    return@addSnapshotListener
                }

                val nowMillis = System.currentTimeMillis()

                val rows = snap?.documents.orEmpty().mapNotNull { doc ->
                    val startsAt = doc.getTimestamp("startsAt") ?: doc.getTimestamp("startTime")
                    val endsAt = doc.getTimestamp("endsAt") ?: doc.getTimestamp("endTime")

                    // ✅ hide already-ended runs
                    val endMillis = endsAt?.toDate()?.time
                    if (endMillis != null && endMillis < nowMillis) return@mapNotNull null

                    val playerIds = (doc.get("playerIds") as? List<String>).orEmpty()
                    val allowedUids = (doc.get("allowedUids") as? List<String>).orEmpty()

                    val playerCount = (doc.getLong("playerCount")?.toInt()) ?: playerIds.size
                    val maxPlayers = (doc.getLong("maxPlayers")?.toInt()) ?: 0

                    val access = doc.getString("access")
                        ?: doc.getString("mode")
                        ?: "OPEN"

                    val hostId = doc.getString("hostId") ?: ""
                    val hostUid = doc.getString("hostUid") ?: doc.getString("hostId")

                    val name = doc.getString("name")

                    RowRun(
                        id = doc.id,
                        name = name,
                        startsAt = startsAt,
                        endsAt = endsAt,
                        playerCount = playerCount,
                        maxPlayers = maxPlayers,
                        playerIds = playerIds,
                        access = access,
                        hostId = hostId,
                        hostUid = hostUid,
                        allowedUids = allowedUids
                    )
                }

                allRuns = rows.sortedBy { it.startsAt?.toDate()?.time ?: Long.MAX_VALUE }
                isLoading = false
                error = null
            }

        onDispose { reg?.remove() }
    }

    val visibleRuns = remember(allRuns, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) allRuns
        else allRuns.filter { rr ->
            (rr.name ?: "").lowercase().contains(q) ||
                    rr.id.lowercase().contains(q) ||
                    rr.access.lowercase().contains(q)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Runs at this court") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(16.dp)
        ) {
            // Search bar
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search runs") }
            )

            Spacer(Modifier.height(12.dp))

            when {
                isLoading -> {
                    Column(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(10.dp))
                        Text("Loading runs…")
                    }
                }

                error != null -> {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                visibleRuns.isEmpty() -> {
                    Text(
                        text = if (query.isBlank())
                            "No upcoming or active runs for this court."
                        else
                            "No runs match your search.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(visibleRuns, key = { it.id }) { rr ->
                            RunRow(
                                rr = rr,
                                currentUid = uid,
                                onView = {
                                    AppNavControllerHolder.navController
                                        ?.navigate("run/${rr.id}") {
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                },
                                onJoin = {
                                    if (uid == null) return@RunRow
                                    scope.launch {
                                        try {
                                            joinRun(db, rr.id, uid)
                                        } catch (e: Exception) {
                                            Log.e("CourtRuns", "joinRun failed", e)
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
                                            Log.e("CourtRuns", "requestJoinRun failed", e)
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
                                            Log.e("CourtRuns", "leaveRun failed", e)
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
                                            Log.e("CourtRuns", "cancelJoinRequest failed", e)
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
}
