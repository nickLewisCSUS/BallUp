package com.nicklewis.ballup.ui.runs

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
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
import com.nicklewis.ballup.util.openDirections
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

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    var courtLat by remember { mutableStateOf<Double?>(null) }
    var courtLng by remember { mutableStateOf<Double?>(null) }

    var run by remember { mutableStateOf<RunDoc?>(null) }
    var courtName by remember { mutableStateOf<String?>(null) }
    var isMember by remember { mutableStateOf(false) }
    var isHost by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }

    // Host + players profile info
    var hostProfile by remember { mutableStateOf<PlayerProfile?>(null) }
    var playerProfiles by remember { mutableStateOf<Map<String, PlayerProfile>>(emptyMap()) }

    // ----- pending join requests -----
    var pendingRequests by remember { mutableStateOf<List<JoinRequestDoc>>(emptyList()) }
    var pendingProfiles by remember { mutableStateOf<Map<String, PlayerProfile>>(emptyMap()) }

    // invited players (for INVITE_ONLY)
    var invitedProfiles by remember { mutableStateOf<Map<String, PlayerProfile>>(emptyMap()) }

    // invite dialog visibility
    var showInviteDialog by remember { mutableStateOf(false) }

    // current viewer's join request status (pending/approved/denied)
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
            val subQuery = r.ref.collection("joinRequests")
                .whereEqualTo("status", "pending")

            val reg = subQuery.addSnapshotListener { snap, e ->
                if (e != null) {
                    Log.e("RunDetails", "joinRequests listen error", e)
                    return@addSnapshotListener
                }
                val list = snap?.documents.orEmpty().mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    JoinRequestDoc.from(data, doc.reference)
                }
                pendingRequests = list
            }

            onDispose { reg.remove() }
        }
    }

    // ----- listen to *my* join request (any viewer) -----
    DisposableEffect(run?.ref, uid) {
        val r = run
        val me = uid
        if (r == null || me == null) {
            myRequestStatus = null
            onDispose { }
        } else {
            val docRef = r.ref.collection("joinRequests").document(me)
            val reg = docRef.addSnapshotListener { snap, e ->
                if (e != null) {
                    Log.e("RunDetails", "my joinRequest listen error", e)
                    myRequestStatus = null
                    return@addSnapshotListener
                }
                if (snap != null && snap.exists()) {
                    val data = snap.data ?: emptyMap()
                    myRequestStatus = data["status"] as? String ?: "pending"
                } else {
                    myRequestStatus = null
                }
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

    // ----- pending profiles lookup -----
    LaunchedEffect(pendingRequests.map { it.uid }.sorted().joinToString(",")) {
        val ids = pendingRequests.map { it.uid }.distinct()
        if (ids.isEmpty()) {
            pendingProfiles = emptyMap()
            return@LaunchedEffect
        }

        val map = mutableMapOf<String, PlayerProfile>()
        for (id in ids) {
            val profile = lookupUserProfile(db, id)
            if (profile != null) {
                map[id] = profile
            }
        }
        pendingProfiles = map
    }

    // ----- invited profiles lookup (INVITE_ONLY) -----
    LaunchedEffect(run?.allowedUids?.sorted()?.joinToString(",")) {
        val r = run ?: return@LaunchedEffect
        val ids = r.allowedUids
        if (ids.isEmpty()) {
            invitedProfiles = emptyMap()
            return@LaunchedEffect
        }

        val map = mutableMapOf<String, PlayerProfile>()
        for (id in ids) {
            val profile = lookupUserProfile(db, id)
            if (profile != null) {
                map[id] = profile
            }
        }
        invitedProfiles = map
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

                    val statusColor = when (statusLabel) {
                        "Cancelled", "Ended" -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    // Decode access
                    val accessEnum: RunAccess = remember(r.access) {
                        try {
                            RunAccess.valueOf(r.access)
                        } catch (_: IllegalArgumentException) {
                            RunAccess.OPEN
                        }
                    }
                    val isAllowedForInviteOnly =
                        uid != null && r.allowedUids.contains(uid)
                    val hasPendingRequestFromMe = (myRequestStatus == "pending")

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
                                text = "Access • " + when (accessEnum) {
                                    RunAccess.OPEN -> "Open to anyone"
                                    RunAccess.HOST_APPROVAL -> "Host approval required"
                                    RunAccess.INVITE_ONLY -> "Invite only"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (r.pendingJoinsCount > 0 && isHost) {
                                Text(
                                    text = "Pending requests • ${r.pendingJoinsCount}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Text(
                                text = "Status • $statusLabel",
                                style = MaterialTheme.typography.bodySmall,
                                color = statusColor
                            )

                            if (openSlots > 0 && statusLabel in listOf("Active", "Scheduled")) {
                                Text(
                                    "$openSlots spot${if (openSlots == 1) "" else "s"} left",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else if (statusLabel in listOf("Active", "Scheduled")) {
                                Text(
                                    "Full",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }

                    // Invited user banner
                    if (!isHost && accessEnum == RunAccess.INVITE_ONLY && isAllowedForInviteOnly) {
                        Text(
                            text = "You were invited to this run",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Notice when run is ended/cancelled
                    if (statusLabel in listOf("Cancelled", "Ended")) {
                        Text(
                            text = "This run has ended and is no longer joinable.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Players list card
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

                    // --- Invited players (host-only, invite-only) ---
                    if (isHost && accessEnum == RunAccess.INVITE_ONLY) {
                        Text("Invited players", style = MaterialTheme.typography.titleMedium)
                        Surface(
                            tonalElevation = 1.dp,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (r.allowedUids.isEmpty()) {
                                    Text(
                                        text = "No invited players yet.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    r.allowedUids.forEach { invitedUid ->
                                        val profile = invitedProfiles[invitedUid]
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(
                                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    text = profile?.username ?: invitedUid,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                val tags = listOfNotNull(
                                                    profile?.skillLevel,
                                                    profile?.playStyle,
                                                    profile?.heightBracket
                                                )
                                                if (tags.isNotEmpty()) {
                                                    Text(
                                                        text = tags.joinToString(" • "),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            TextButton(
                                                onClick = {
                                                    scope.launch {
                                                        try {
                                                            r.ref.update(
                                                                "allowedUids",
                                                                FieldValue.arrayRemove(invitedUid)
                                                            ).await()
                                                        } catch (e: Exception) {
                                                            Log.e("RunDetails", "remove invite failed", e)
                                                        }
                                                    }
                                                }
                                            ) {
                                                Text("Remove")
                                            }
                                        }
                                    }
                                }

                                Spacer(Modifier.height(4.dp))

                                OutlinedButton(
                                    onClick = { showInviteDialog = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Invite player")
                                }
                            }
                        }
                    }

                    // --- Pending join requests (host only) ---
                    if (isHost) {
                        Text("Pending requests", style = MaterialTheme.typography.titleMedium)
                        Surface(
                            tonalElevation = 1.dp,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (pendingRequests.isEmpty()) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Text(
                                        text = "No pending join requests.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    pendingRequests
                                        .sortedBy { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
                                        .forEach { req ->
                                            val profile = pendingProfiles[req.uid]
                                            var rowBusy by remember(req.uid) { mutableStateOf(false) }

                                            Column(
                                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(
                                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Text(
                                                            text = profile?.username ?: req.uid,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                        val tags = listOfNotNull(
                                                            profile?.skillLevel,
                                                            profile?.playStyle,
                                                            profile?.heightBracket
                                                        )
                                                        if (tags.isNotEmpty()) {
                                                            Text(
                                                                text = tags.joinToString(" • "),
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }

                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        TextButton(
                                                            enabled = !rowBusy,
                                                            onClick = {
                                                                if (rowBusy || uid == null) return@TextButton
                                                                rowBusy = true
                                                                scope.launch {
                                                                    try {
                                                                        val runRef = db.collection("runs").document(runId)
                                                                        val reqRef = req.ref

                                                                        try {
                                                                            // Try to actually join the run
                                                                            joinRun(db, runId, req.uid)

                                                                            // Mark approved + decrement pending count
                                                                            db.runBatch { batch ->
                                                                                batch.update(
                                                                                    runRef,
                                                                                    mapOf(
                                                                                        "pendingJoinsCount" to FieldValue.increment(-1)
                                                                                    )
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
                                                                            Log.e("RunDetails", "approveJoin failed", e)
                                                                            // Best-effort: mark as denied/decided so it doesn't get stuck forever
                                                                            try {
                                                                                db.runBatch { batch ->
                                                                                    batch.update(
                                                                                        runRef,
                                                                                        mapOf(
                                                                                            "pendingJoinsCount" to FieldValue.increment(-1)
                                                                                        )
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
                                                                                Log.e("RunDetails", "cleanup after approve failure", inner)
                                                                            }
                                                                        }
                                                                    } finally {
                                                                        rowBusy = false
                                                                    }
                                                                }
                                                            }
                                                        ) {
                                                            Text("Approve")
                                                        }

                                                        TextButton(
                                                            enabled = !rowBusy,
                                                            onClick = {
                                                                if (rowBusy || uid == null) return@TextButton
                                                                rowBusy = true
                                                                scope.launch {
                                                                    try {
                                                                        val runRef = db.collection("runs").document(runId)
                                                                        val reqRef = req.ref
                                                                        db.runBatch { batch ->
                                                                            batch.update(
                                                                                runRef,
                                                                                mapOf(
                                                                                    "pendingJoinsCount" to FieldValue.increment(-1)
                                                                                )
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
                                                                        Log.e("RunDetails", "denyJoin failed", e)
                                                                    } finally {
                                                                        rowBusy = false
                                                                    }
                                                                }
                                                            }
                                                        ) {
                                                            Text("Deny")
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                }
                            }
                        }
                    }

                    // Actions row
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
                            val canAct =
                                statusLabel in listOf("Active", "Scheduled") && uid != null

                            when (accessEnum) {
                                RunAccess.OPEN -> {
                                    val canJoin =
                                        canAct && openSlots > 0
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
                                }

                                RunAccess.HOST_APPROVAL -> {
                                    when {
                                        !canAct -> {
                                            OutlinedButton(
                                                onClick = {},
                                                enabled = false,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .heightIn(min = 44.dp)
                                            ) {
                                                Text("Request to join")
                                            }
                                        }

                                        hasPendingRequestFromMe -> {
                                            OutlinedButton(
                                                onClick = {},
                                                enabled = false,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .heightIn(min = 44.dp)
                                            ) {
                                                Text("Request sent")
                                            }
                                        }

                                        else -> {
                                            Button(
                                                onClick = {
                                                    if (!canAct) return@Button
                                                    requesting = true
                                                    scope.launch {
                                                        try {
                                                            requestJoinRun(db, runId, uid!!)
                                                        } catch (e: Exception) {
                                                            Log.e("RunDetails", "requestJoinRun failed", e)
                                                        } finally {
                                                            requesting = false
                                                        }
                                                    }
                                                },
                                                enabled = !requesting,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .heightIn(min = 44.dp)
                                            ) {
                                                Text(if (requesting) "Requesting…" else "Request to join")
                                            }
                                        }
                                    }
                                }

                                RunAccess.INVITE_ONLY -> {
                                    if (isAllowedForInviteOnly && canAct && openSlots > 0) {
                                        Button(
                                            onClick = {
                                                if (!canAct) return@Button
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
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 44.dp)
                                        ) {
                                            Text(if (joining) "Joining…" else "Join Run")
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = {},
                                            enabled = false,
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 44.dp)
                                        ) {
                                            Text("Invite only")
                                        }
                                    }
                                }
                            }
                        } else if (isHost) {
                            OutlinedButton(
                                onClick = {
                                    if (!ending && statusLabel in listOf("Active", "Scheduled")) {
                                        showCancelConfirm = true
                                    }
                                },
                                enabled = !ending && statusLabel in listOf("Active", "Scheduled"),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 44.dp)
                            ) {
                                Text("Cancel Run")
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

                    // Cancel confirmation dialog for host
                    if (showCancelConfirm) {
                        AlertDialog(
                            onDismissRequest = {
                                if (!ending) showCancelConfirm = false
                            },
                            title = { Text("Cancel run?") },
                            text = {
                                Text(
                                    "This will cancel the run for everyone. " +
                                            "Players won’t be able to join and the run will be marked as cancelled."
                                )
                            },
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
                                ) {
                                    Text(if (ending) "Cancelling…" else "Yes, cancel")
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = {
                                        if (!ending) showCancelConfirm = false
                                    }
                                ) {
                                    Text("Keep run")
                                }
                            }
                        )
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

    // ---- Invite dialog (host, invite-only) ----
    InvitePlayerDialog(
        visible = showInviteDialog,
        run = run,
        uid = uid,
        db = db,
        onDismiss = { showInviteDialog = false }
    )
}
