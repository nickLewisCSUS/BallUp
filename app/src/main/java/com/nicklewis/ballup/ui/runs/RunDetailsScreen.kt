package com.nicklewis.ballup.ui.runs

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.nicklewis.ballup.data.joinRun
import com.nicklewis.ballup.data.leaveRun
import com.nicklewis.ballup.data.requestJoinRun
import com.nicklewis.ballup.model.RunAccess
import com.nicklewis.ballup.model.Team
import com.nicklewis.ballup.util.openDirections
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private val ActionBtnHeight = 48.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunDetailsScreen(
    runId: String,
    hidePlayers: Boolean = false,
    onBack: (() -> Unit)? = null,
    viewModel: RunDetailsViewModel
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val uid = remember { FirebaseAuth.getInstance().currentUser?.uid }
    val scope = rememberCoroutineScope()

    val uiState by viewModel.uiState.collectAsState()
    val ownedTeams = uiState.ownedTeams
    val squadError = uiState.errorMessage
    var showSquadSheet by remember { mutableStateOf(false) }

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    var courtLat by remember { mutableStateOf<Double?>(null) }
    var courtLng by remember { mutableStateOf<Double?>(null) }

    var run by remember { mutableStateOf<RunDoc?>(null) }
    var courtName by remember { mutableStateOf<String?>(null) }
    var isMember by remember { mutableStateOf(false) }
    var isHost by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }

    var hostProfile by remember { mutableStateOf<PlayerProfile?>(null) }
    var playerProfiles by remember { mutableStateOf<Map<String, PlayerProfile>>(emptyMap()) }

    var pendingRequests by remember { mutableStateOf<List<JoinRequestDoc>>(emptyList()) }
    var pendingProfiles by remember { mutableStateOf<Map<String, PlayerProfile>>(emptyMap()) }

    var invitedProfiles by remember { mutableStateOf<Map<String, PlayerProfile>>(emptyMap()) }
    var showInviteDialog by remember { mutableStateOf(false) }

    var myRequestStatus by remember { mutableStateOf<String?>(null) }

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

    // ----- listen to pending join requests (host only) -----
    DisposableEffect(run?.ref, isHost) {
        val r = run
        if (!isHost || r == null) {
            onDispose { }
        } else {
            val reg = r.ref.collection("joinRequests")
                .whereEqualTo("status", "pending")
                .addSnapshotListener { snap, e ->
                    if (e != null) {
                        Log.e("RunDetails", "joinRequests listen error", e)
                        return@addSnapshotListener
                    }
                    pendingRequests = snap?.documents.orEmpty().mapNotNull { doc ->
                        val data = doc.data ?: return@mapNotNull null
                        JoinRequestDoc.from(data, doc.reference)
                    }
                }
            onDispose { reg.remove() }
        }
    }

    // ----- listen to my join request (any viewer) -----
    DisposableEffect(run?.ref, uid) {
        val r = run
        val me = uid
        if (r == null || me == null) {
            myRequestStatus = null
            onDispose { }
        } else {
            val reg = r.ref.collection("joinRequests").document(me)
                .addSnapshotListener { snap, e ->
                    if (e != null) {
                        Log.e("RunDetails", "my joinRequest listen error", e)
                        myRequestStatus = null
                        return@addSnapshotListener
                    }
                    myRequestStatus =
                        if (snap != null && snap.exists()) (snap.data?.get("status") as? String
                            ?: "pending")
                        else null
                }
            onDispose { reg.remove() }
        }
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

    // ----- membership + host flag -----
    LaunchedEffect(run, uid) {
        val r = run ?: return@LaunchedEffect
        val me = uid

        isMember = when {
            me == null -> false
            r.hostId == me || r.hostUid == me -> true
            r.playerIds.contains(me) -> true
            else -> false
        }
        isHost = (me != null && (r.hostId == me || r.hostUid == me))
    }

    // ----- host profile lookup -----
    LaunchedEffect(run?.hostUid ?: run?.hostId) {
        val r = run ?: return@LaunchedEffect
        hostProfile = null
        val hostUid = r.hostUid ?: r.hostId
        if (!hostUid.isNullOrBlank()) hostProfile = lookupUserProfile(db, hostUid)
    }

    // ----- player profiles lookup -----
    LaunchedEffect(run?.playerIds?.joinToString(",")) {
        val r = run ?: return@LaunchedEffect
        if (r.playerIds.isEmpty()) {
            playerProfiles = emptyMap()
            return@LaunchedEffect
        }
        val map = mutableMapOf<String, PlayerProfile>()
        for (id in r.playerIds) lookupUserProfile(db, id)?.let { map[id] = it }
        playerProfiles = map
    }

    // ----- pending profiles lookup -----
    LaunchedEffect(pendingRequests.map { it.uid }.sorted().joinToString(",")) {
        val ids = pendingRequests.map { it.uid }.distinct()
        if (ids.isEmpty()) {
            pendingProfiles = emptyMap()
            return@LaunchedEffect
        }
        val map = mutableMapOf<String, PlayerProfile>()
        for (id in ids) lookupUserProfile(db, id)?.let { map[id] = it }
        pendingProfiles = map
    }

    // ----- invited profiles lookup (INVITE_ONLY) -----
    LaunchedEffect(run?.allowedUids?.sorted()?.joinToString(",")) {
        val r = run ?: return@LaunchedEffect
        if (r.allowedUids.isEmpty()) {
            invitedProfiles = emptyMap()
            return@LaunchedEffect
        }
        val map = mutableMapOf<String, PlayerProfile>()
        for (id in r.allowedUids) lookupUserProfile(db, id)?.let { map[id] = it }
        invitedProfiles = map
    }

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
                    val ctx = LocalContext.current
                    val hostUidForSort = r.hostUid ?: r.hostId
                    val openSlots = (r.maxPlayers - r.playerCount).coerceAtLeast(0)

                    val nowMs = System.currentTimeMillis()
                    val sMs = r.startsAt?.toDate()?.time
                    val eMs = r.endsAt?.toDate()?.time

                    val statusLabel = when {
                        r.status == "cancelled" -> "Cancelled"
                        r.status == "inactive" || r.status == "ended" -> "Ended"
                        sMs != null && eMs != null && nowMs in sMs..eMs -> "Active"
                        sMs != null && nowMs < sMs -> "Scheduled"
                        else -> r.status?.replaceFirstChar { it.uppercase() } ?: "Unknown"
                    }

                    val accessEnum: RunAccess = remember(r.access) {
                        try {
                            RunAccess.valueOf(r.access)
                        } catch (_: IllegalArgumentException) {
                            RunAccess.OPEN
                        }
                    }

                    val accessLabel = when (accessEnum) {
                        RunAccess.OPEN -> "Open to anyone"
                        RunAccess.HOST_APPROVAL -> "Host approval required"
                        RunAccess.INVITE_ONLY -> "Invite only"
                    }

                    val isAllowedForInviteOnly = uid != null && r.allowedUids.contains(uid)
                    val hasPendingRequestFromMe = (myRequestStatus == "pending")
                    val joinableNow = statusLabel in listOf("Active", "Scheduled")

                    // Header / summary card (compact)
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
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

                            RunMetaRow(
                                mode = r.mode ?: "Unknown",
                                players = "${r.playerCount}/${r.maxPlayers}",
                                access = accessLabel,
                                status = statusLabel
                            )

                            if (joinableNow) {
                                Text(
                                    text = if (openSlots > 0) "$openSlots spot${if (openSlots == 1) "" else "s"} left" else "Full",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (openSlots > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                )
                            }

                            if (!isHost && accessEnum == RunAccess.INVITE_ONLY && isAllowedForInviteOnly) {
                                Text(
                                    text = "You were invited to this run",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            val hostLabel = hostProfile?.username ?: hostProfile?.displayName
                            ?: r.hostId ?: r.hostUid ?: "unknown"
                            Text(
                                text = "Host • $hostLabel",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Actions (stylized)
                    var joining by remember { mutableStateOf(false) }
                    var requesting by remember { mutableStateOf(false) }
                    var leaving by remember { mutableStateOf(false) }
                    var ending by remember { mutableStateOf(false) }
                    var showCancelConfirm by remember { mutableStateOf(false) }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (!isMember) {
                            val canAct = joinableNow && uid != null
                            when (accessEnum) {
                                RunAccess.OPEN -> {
                                    val canJoin = canAct && openSlots > 0
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
                                        modifier = Modifier.weight(1f).height(ActionBtnHeight),
                                        shape = MaterialTheme.shapes.large
                                    ) { Text(if (joining) "Joining…" else "Join") }
                                }

                                RunAccess.HOST_APPROVAL -> {
                                    when {
                                        !canAct -> {
                                            OutlinedButton(
                                                onClick = {},
                                                enabled = false,
                                                modifier = Modifier.weight(1f)
                                                    .height(ActionBtnHeight),
                                                shape = MaterialTheme.shapes.large
                                            ) { Text("Request") }
                                        }

                                        hasPendingRequestFromMe -> {
                                            OutlinedButton(
                                                onClick = {},
                                                enabled = false,
                                                modifier = Modifier.weight(1f)
                                                    .height(ActionBtnHeight),
                                                shape = MaterialTheme.shapes.large
                                            ) { Text("Request sent") }
                                        }

                                        else -> {
                                            Button(
                                                onClick = {
                                                    requesting = true
                                                    scope.launch {
                                                        try {
                                                            requestJoinRun(db, runId, uid!!)
                                                        } catch (e: Exception) {
                                                            Log.e(
                                                                "RunDetails",
                                                                "requestJoinRun failed",
                                                                e
                                                            )
                                                        } finally {
                                                            requesting = false
                                                        }
                                                    }
                                                },
                                                enabled = !requesting,
                                                modifier = Modifier.weight(1f)
                                                    .height(ActionBtnHeight),
                                                shape = MaterialTheme.shapes.large
                                            ) { Text(if (requesting) "Requesting…" else "Request") }
                                        }
                                    }
                                }

                                RunAccess.INVITE_ONLY -> {
                                    val canJoin = canAct && isAllowedForInviteOnly && openSlots > 0
                                    if (canJoin) {
                                        Button(
                                            onClick = {
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
                                            enabled = !joining,
                                            modifier = Modifier.weight(1f).height(ActionBtnHeight),
                                            shape = MaterialTheme.shapes.large
                                        ) { Text(if (joining) "Joining…" else "Join") }
                                    } else {
                                        OutlinedButton(
                                            onClick = {},
                                            enabled = false,
                                            modifier = Modifier.weight(1f).height(ActionBtnHeight),
                                            shape = MaterialTheme.shapes.large
                                        ) { Text("Invite only") }
                                    }
                                }
                            }
                        } else if (isHost) {
                            // ✅ Cancel run (destructive)
                            Button(
                                onClick = { if (!ending && joinableNow) showCancelConfirm = true },
                                enabled = !ending && joinableNow,
                                modifier = Modifier.weight(1f).height(ActionBtnHeight),
                                shape = MaterialTheme.shapes.large,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Icon(Icons.Default.Block, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (ending) "Cancelling…" else "Cancel run")
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
                                modifier = Modifier.weight(1f).height(ActionBtnHeight),
                                shape = MaterialTheme.shapes.large
                            ) { Text(if (leaving) "Leaving…" else "Leave") }
                        }

                        // ✅ Directions (outlined + icon)
                        OutlinedButton(
                            onClick = {
                                val lat = courtLat
                                val lng = courtLng
                                val name = courtName ?: "Court"
                                if (lat != null && lng != null) openDirections(ctx, lat, lng, name)
                            },
                            enabled = courtLat != null && courtLng != null,
                            modifier = Modifier.weight(1f).height(ActionBtnHeight),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Icon(Icons.Default.Navigation, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Directions")
                        }
                    }

                    // ✅ Invite squad (tonal + icon) only if Invite Only
                    val canInviteSquad =
                        isHost &&
                                accessEnum == RunAccess.INVITE_ONLY &&
                                ownedTeams.isNotEmpty() &&
                                joinableNow

                    if (canInviteSquad) {
                        FilledTonalButton(
                            onClick = { showSquadSheet = true },
                            modifier = Modifier.fillMaxWidth().height(ActionBtnHeight),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Icon(Icons.Default.GroupAdd, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Invite squad")
                        }
                    }

                    // Players dropdown
                    val sortedPlayerIds = r.playerIds.sortedWith(
                        compareBy(
                            { pid -> if (pid == hostUidForSort) 0 else 1 },
                            { pid -> playerProfiles[pid]?.username ?: "" }
                        )
                    )
                    val shouldHidePlayersList = hidePlayers && !isHost

                    if (shouldHidePlayersList) {
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Players", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Players are hidden until the host approves you.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        // your existing ExpandableSectionCard Players block (unchanged)
                        ExpandableSectionCard(
                            title = "Players",
                            countLabel = "${r.playerCount}/${r.maxPlayers}",
                            initiallyExpanded = true
                        ) {
                            if (sortedPlayerIds.isEmpty()) {
                                EmptyHint("No players yet.")
                            } else {
                                sortedPlayerIds.forEach { pid ->
                                    val p = playerProfiles[pid]
                                    val isRowHost = pid == hostUidForSort
                                    val tags =
                                        listOfNotNull(p?.skillLevel, p?.playStyle, p?.heightBracket)
                                    PlayerRow(
                                        username = (p?.username ?: pid),
                                        tags = tags,
                                        isHost = isRowHost
                                    )
                                }
                            }
                        }

                        // Invites dropdown (host-only, invite-only)
                        if (isHost && accessEnum == RunAccess.INVITE_ONLY) {
                            ExpandableSectionCard(
                                title = "Invited",
                                countLabel = "${r.allowedUids.size}",
                                initiallyExpanded = false,
                                trailing = {
                                    TextButton(onClick = {
                                        showInviteDialog = true
                                    }) { Text("Invite") }
                                }
                            ) {
                                if (r.allowedUids.isEmpty()) {
                                    EmptyHint("No invited players yet.")
                                } else {
                                    r.allowedUids.forEach { invitedUid ->
                                        val p = invitedProfiles[invitedUid]
                                        val tags = listOfNotNull(
                                            p?.skillLevel,
                                            p?.playStyle,
                                            p?.heightBracket
                                        )
                                        InvitedRow(
                                            username = p?.username ?: invitedUid,
                                            tags = tags,
                                            onRemove = {
                                                scope.launch {
                                                    try {
                                                        r.ref.update(
                                                            "allowedUids",
                                                            FieldValue.arrayRemove(invitedUid)
                                                        ).await()
                                                    } catch (e: Exception) {
                                                        Log.e(
                                                            "RunDetails",
                                                            "remove invite failed",
                                                            e
                                                        )
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Pending dropdown (host-only, host-approval only)
                        if (isHost && accessEnum == RunAccess.HOST_APPROVAL) {
                            ExpandableSectionCard(
                                title = "Pending requests",
                                countLabel = "${pendingRequests.size}",
                                initiallyExpanded = pendingRequests.isNotEmpty()
                            ) {
                                if (pendingRequests.isEmpty()) {
                                    EmptyHint("No pending join requests.")
                                } else {
                                    pendingRequests
                                        .sortedBy { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
                                        .forEach { req ->
                                            val p = pendingProfiles[req.uid]
                                            val tags = listOfNotNull(
                                                p?.skillLevel,
                                                p?.playStyle,
                                                p?.heightBracket
                                            )
                                            var rowBusy by remember(req.uid) { mutableStateOf(false) }

                                            PendingRequestRow(
                                                username = p?.username ?: req.uid,
                                                tags = tags,
                                                busy = rowBusy,
                                                onApprove = {
                                                    if (rowBusy) return@PendingRequestRow
                                                    rowBusy = true
                                                    scope.launch {
                                                        try {
                                                            val runRef = db.collection("runs")
                                                                .document(runId)
                                                            val reqRef = req.ref
                                                            try {
                                                                joinRun(db, runId, req.uid)
                                                                db.runBatch { batch ->
                                                                    batch.update(
                                                                        runRef,
                                                                        "pendingJoinsCount",
                                                                        FieldValue.increment(-1)
                                                                    )
                                                                    batch.update(
                                                                        reqRef,
                                                                        mapOf(
                                                                            "status" to "approved",
                                                                            "approvedAt" to FieldValue.serverTimestamp()
                                                                        )
                                                                    )
                                                                }.await()
                                                            } catch (e: Exception) {
                                                                Log.e(
                                                                    "RunDetails",
                                                                    "approveJoin failed",
                                                                    e
                                                                )
                                                                try {
                                                                    db.runBatch { batch ->
                                                                        batch.update(
                                                                            runRef,
                                                                            "pendingJoinsCount",
                                                                            FieldValue.increment(-1)
                                                                        )
                                                                        batch.update(
                                                                            reqRef,
                                                                            mapOf(
                                                                                "status" to "denied",
                                                                                "decidedAt" to FieldValue.serverTimestamp()
                                                                            )
                                                                        )
                                                                    }.await()
                                                                } catch (inner: Exception) {
                                                                    Log.e(
                                                                        "RunDetails",
                                                                        "cleanup after approve failure",
                                                                        inner
                                                                    )
                                                                }
                                                            }
                                                        } finally {
                                                            rowBusy = false
                                                        }
                                                    }
                                                },
                                                onDeny = {
                                                    if (rowBusy) return@PendingRequestRow
                                                    rowBusy = true
                                                    scope.launch {
                                                        try {
                                                            val runRef = db.collection("runs")
                                                                .document(runId)
                                                            val reqRef = req.ref
                                                            db.runBatch { batch ->
                                                                batch.update(
                                                                    runRef,
                                                                    "pendingJoinsCount",
                                                                    FieldValue.increment(-1)
                                                                )
                                                                batch.update(
                                                                    reqRef,
                                                                    mapOf(
                                                                        "status" to "denied",
                                                                        "decidedAt" to FieldValue.serverTimestamp()
                                                                    )
                                                                )
                                                            }.await()
                                                        } catch (e: Exception) {
                                                            Log.e(
                                                                "RunDetails",
                                                                "denyJoin failed",
                                                                e
                                                            )
                                                        } finally {
                                                            rowBusy = false
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                }
                            }
                        }

                        // Cancel confirmation dialog
                        if (showCancelConfirm) {
                            AlertDialog(
                                onDismissRequest = { if (!ending) showCancelConfirm = false },
                                title = { Text("Cancel run?") },
                                text = { Text("This will cancel the run for everyone.") },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            if (ending) return@TextButton
                                            ending = true
                                            scope.launch {
                                                try {
                                                    r.ref.update(
                                                        mapOf(
                                                            "status" to "cancelled",
                                                            "lastHeartbeatAt" to FieldValue.serverTimestamp()
                                                        )
                                                    ).await()
                                                    showCancelConfirm = false
                                                    onBack?.invoke()
                                                } catch (e: Exception) {
                                                    Log.e("RunDetails", "cancelRun failed", e)
                                                    error = "Failed to cancel run."
                                                } finally {
                                                    ending = false
                                                }
                                            }
                                        }
                                    ) { Text(if (ending) "Cancelling…" else "Yes, cancel") }
                                },
                                dismissButton = {
                                    TextButton(onClick = {
                                        if (!ending) showCancelConfirm = false
                                    }) { Text("Keep run") }
                                }
                            )
                        }

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

        // invite dialog (host only, invite-only)
        InvitePlayerDialog(
            visible = showInviteDialog,
            run = run,
            uid = uid,
            db = FirebaseFirestore.getInstance(),
            onDismiss = { showInviteDialog = false }
        )

        // squad sheet
        if (showSquadSheet) {
            SquadInviteSheet(
                ownedTeams = ownedTeams,
                errorText = squadError,
                onDismiss = { showSquadSheet = false },
                onInviteTeam = { team: Team ->
                    val r = run ?: return@SquadInviteSheet
                    scope.launch {
                        try {
                            viewModel.inviteSquad(team)
                            showSquadSheet = false
                        } catch (_: Exception) {
                            // viewModel handles errorMessage
                        }
                    }
                },
                onClearError = { viewModel.clearError() }
            )
        }
    }
}
