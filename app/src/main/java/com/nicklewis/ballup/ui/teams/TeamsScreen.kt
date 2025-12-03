@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.nicklewis.ballup.ui.teams

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nicklewis.ballup.data.TeamsRepository.PendingTeamRequest
import com.nicklewis.ballup.model.Team
import com.nicklewis.ballup.model.UserProfile
import kotlinx.coroutines.launch
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox

// ---------- SCREEN ----------

@Composable
fun TeamsScreen(
    onBackToMap: () -> Unit,      // kept for nav wiring, but no longer used
    onOpenSettings: () -> Unit,  // kept for nav wiring, but no longer used
    viewModel: TeamsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // tabs: 0 = My squads, 1 = Find squads, 2 = Invites
    var selectedTab by remember { mutableStateOf(0) }

    var showCreateDialog by remember { mutableStateOf(false) }
    var newTeamName by remember { mutableStateOf("") }
    var createError by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }

    // Edit dialog state
    var teamBeingEdited by remember { mutableStateOf<Team?>(null) }
    var editName by remember { mutableStateOf("") }
    var editSelectedSkill by remember { mutableStateOf("Any") }
    var editSelectedDays by remember { mutableStateOf(setOf<String>()) }
    var editInviteOnly by remember { mutableStateOf(false) }
    var editError by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }

    var teamBeingDeleted by remember { mutableStateOf<Team?>(null) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }

    // Members sheet state
    var showMembersSheet by remember { mutableStateOf(false) }
    var membersForTeam by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var membersLoading by remember { mutableStateOf(false) }
    var membersError by remember { mutableStateOf<String?>(null) }
    var membersTeamName by remember { mutableStateOf("") }

    // Join-requests sheet state (for owners)
    var showRequestsSheet by remember { mutableStateOf(false) }
    var requestsForTeam by remember { mutableStateOf<List<PendingTeamRequest>>(emptyList()) }
    var requestsLoading by remember { mutableStateOf(false) }
    var requestsError by remember { mutableStateOf<String?>(null) }
    var requestsTeamName by remember { mutableStateOf("") }
    var requestsTeamId by remember { mutableStateOf("") }

    // Invite dialog (owner -> player)
    var showInviteDialog by remember { mutableStateOf(false) }
    var inviteUsername by remember { mutableStateOf("") }
    var inviteError by remember { mutableStateOf<String?>(null) }
    var invitingTeam by remember { mutableStateOf<Team?>(null) }
    var inviting by remember { mutableStateOf(false) }

    val mySquadsFiltered = remember(uiState.teams, searchQuery) {
        if (searchQuery.isBlank()) uiState.teams
        else uiState.teams.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val discoverFiltered = remember(uiState.discoverableTeams, searchQuery) {
        if (searchQuery.isBlank()) uiState.discoverableTeams
        else uiState.discoverableTeams.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    // Create dialog state
    var selectedSkill by remember { mutableStateOf("Any") }
    var selectedDays by remember { mutableStateOf(setOf<String>()) }
    var inviteOnly by remember { mutableStateOf(false) }

    val skillOptions = listOf("Any", "Casual runs", "Run it back", "Tryhard / league")
    val dayOptions = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(onClick = { showCreateDialog = true }) {
                    androidx.compose.material3.Icon(
                        Icons.Default.Add,
                        contentDescription = "Create squad"
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .padding(padding)
        ) {
            Text(
                text = "Squads",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Keep your hoop crew together. Reuse the same squads when you start or join runs.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search squads") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            uiState.errorMessage?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("My squads") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Find squads") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        BadgedBox(
                            badge = {
                                val count = uiState.incomingInvites.size
                                if (count > 0) {
                                    // Number badge; if you want just a dot, remove Text()
                                    Badge {
                                        Text(if (count > 9) "9+" else count.toString())
                                    }
                                }
                            }
                        ) {
                            Text("Invites")
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (selectedTab) {
                // ---- My squads ----
                0 -> {
                    if (mySquadsFiltered.isEmpty() && !uiState.isLoading) {
                        Text(
                            text = if (searchQuery.isBlank()) {
                                "You don’t have any squads yet. Tap + to create one."
                            } else {
                                "No squads match “$searchQuery”."
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(mySquadsFiltered, key = { it.id }) { team ->
                                val isOwner = team.ownerUid == uiState.currentUid
                                SquadRow(
                                    team = team,
                                    isOwner = isOwner,
                                    onViewMembers = { tappedTeam ->
                                        membersTeamName = tappedTeam.name
                                        membersForTeam = emptyList()
                                        membersError = null
                                        membersLoading = true
                                        showMembersSheet = true

                                        viewModel.loadMembersFor(tappedTeam) { ok, profiles, err ->
                                            membersLoading = false
                                            if (ok) {
                                                membersForTeam = profiles.orEmpty()
                                            } else {
                                                membersError = err
                                            }
                                        }
                                    },
                                    onEdit = { t ->
                                        teamBeingEdited = t
                                        editName = t.name
                                        editSelectedSkill = t.preferredSkillLevel ?: "Any"
                                        editSelectedDays = t.playDays.toSet()
                                        editInviteOnly = t.inviteOnly
                                        editError = null
                                    },
                                    onDelete = {
                                        teamBeingDeleted = it
                                        deleteError = null
                                    },
                                    onLeave = { leavingTeam ->
                                        viewModel.leaveTeam(leavingTeam.id) { ok, msg ->
                                            scope.launch {
                                                if (ok) {
                                                    snackbarHostState.showSnackbar(
                                                        "Left ${leavingTeam.name}"
                                                    )
                                                } else if (msg != null) {
                                                    snackbarHostState.showSnackbar(msg)
                                                }
                                            }
                                        }
                                    },
                                    onViewRequests = { tappedTeam ->
                                        requestsTeamName = tappedTeam.name
                                        requestsTeamId = tappedTeam.id
                                        requestsForTeam = emptyList()
                                        requestsError = null
                                        requestsLoading = true
                                        showRequestsSheet = true

                                        viewModel.loadJoinRequestsFor(tappedTeam) { ok, pending, err ->
                                            requestsLoading = false
                                            if (ok) {
                                                requestsForTeam = pending.orEmpty()
                                            } else {
                                                requestsError = err
                                            }
                                        }
                                    },
                                    onInvite = { squad ->
                                        invitingTeam = squad
                                        inviteUsername = ""
                                        inviteError = null
                                        inviting = false
                                        showInviteDialog = true
                                    },
                                    currentUid = uiState.currentUid
                                )
                            }
                        }
                    }
                }

                // ---- Find squads ----
                1 -> {
                    if (discoverFiltered.isEmpty() && !uiState.isLoading) {
                        Text(
                            text = if (searchQuery.isBlank()) {
                                "No squads to discover yet. As more players create squads, you’ll see them here."
                            } else {
                                "No squads match “$searchQuery”."
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(discoverFiltered, key = { it.id }) { team ->
                                val isOwner = team.ownerUid == uiState.currentUid
                                val isMember = team.memberUids.contains(uiState.currentUid)
                                val hasRequested = uiState.pendingJoinTeamIds.contains(team.id)

                                DiscoverSquadRow(
                                    team = team,
                                    isOwner = isOwner,
                                    isMember = isMember,
                                    hasRequested = hasRequested,
                                    onRequestJoin = { t ->
                                        viewModel.requestToJoinTeam(t.id) { ok, msg ->
                                            scope.launch {
                                                if (ok) {
                                                    snackbarHostState.showSnackbar(
                                                        "Request sent to join ${t.name}"
                                                    )
                                                } else if (msg != null) {
                                                    snackbarHostState.showSnackbar(msg)
                                                }
                                            }
                                        }
                                    },
                                    onCancelRequest = { t ->
                                        viewModel.cancelJoinRequest(t.id) { ok, msg ->
                                            scope.launch {
                                                if (ok) {
                                                    snackbarHostState.showSnackbar(
                                                        "Request cancelled for ${t.name}"
                                                    )
                                                } else if (msg != null) {
                                                    snackbarHostState.showSnackbar(msg)
                                                }
                                            }
                                        }
                                    },
                                    onViewMembers = { tappedTeam ->
                                        membersTeamName = tappedTeam.name
                                        membersForTeam = emptyList()
                                        membersError = null
                                        membersLoading = true
                                        showMembersSheet = true

                                        viewModel.loadMembersFor(tappedTeam) { ok, profiles, err ->
                                            membersLoading = false
                                            if (ok) {
                                                membersForTeam = profiles.orEmpty()
                                            } else {
                                                membersError = err
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // ---- Invites tab ----
                2 -> {
                    if (uiState.incomingInvites.isEmpty() && !uiState.isLoading) {
                        Text(
                            text = "You don’t have any squad invites right now.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(uiState.incomingInvites, key = { it.inviteId }) { invite ->
                                InviteRow(
                                    invite = invite,
                                    onAccept = {
                                        viewModel.acceptInvite(invite.inviteId) { ok, msg ->
                                            scope.launch {
                                                if (ok) {
                                                    snackbarHostState.showSnackbar(
                                                        "Joined ${invite.teamName}"
                                                    )
                                                } else if (msg != null) {
                                                    snackbarHostState.showSnackbar(msg)
                                                }
                                            }
                                        }
                                    },
                                    onDecline = {
                                        viewModel.declineInvite(invite.inviteId) { ok, msg ->
                                            scope.launch {
                                                if (ok) {
                                                    snackbarHostState.showSnackbar(
                                                        "Invite declined"
                                                    )
                                                } else if (msg != null) {
                                                    snackbarHostState.showSnackbar(msg)
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.isLoading) {
                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Loading squads…")
                }
            }
        }
    }

    // --------- Create squad dialog ---------

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!creating) {
                    showCreateDialog = false
                    newTeamName = ""
                    createError = null
                    selectedSkill = "Any"
                    selectedDays = emptySet()
                    inviteOnly = false
                }
            },
            title = { Text("Create new squad") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = newTeamName,
                        onValueChange = {
                            newTeamName = it
                            createError = null
                        },
                        label = { Text("Squad name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Skill section
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Preferred skill level",
                            style = MaterialTheme.typography.labelMedium
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            skillOptions.forEach { option ->
                                FilterChip(
                                    selected = selectedSkill == option,
                                    onClick = { selectedSkill = option },
                                    label = { Text(option) }
                                )
                            }
                        }
                    }

                    // Days section
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Days you usually play",
                            style = MaterialTheme.typography.labelMedium
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            dayOptions.forEach { day ->
                                FilterChip(
                                    selected = selectedDays.contains(day),
                                    onClick = {
                                        selectedDays = if (selectedDays.contains(day)) {
                                            selectedDays - day
                                        } else {
                                            selectedDays + day
                                        }
                                    },
                                    label = { Text(day) }
                                )
                            }
                        }
                    }

                    // Privacy section
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Private squad", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Hide this squad from \"Find squads\". Players can’t send join requests.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = inviteOnly,
                            onCheckedChange = { inviteOnly = it }
                        )
                    }

                    if (createError != null) {
                        Text(
                            text = createError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !creating && newTeamName.isNotBlank(),
                    onClick = {
                        if (newTeamName.isBlank()) return@TextButton
                        creating = true

                        val skill = selectedSkill.ifBlank { null }
                        val days = selectedDays.toList()

                        viewModel.createTeam(
                            name = newTeamName,
                            preferredSkillLevel = skill,
                            playDays = days,
                            inviteOnly = inviteOnly
                        ) { ok, msg ->
                            creating = false
                            if (ok) {
                                newTeamName = ""
                                createError = null
                                selectedSkill = "Any"
                                selectedDays = emptySet()
                                inviteOnly = false
                                showCreateDialog = false
                            } else {
                                createError = msg
                            }
                        }
                    }
                ) {
                    Text(if (creating) "Creating…" else "Create")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !creating,
                    onClick = {
                        showCreateDialog = false
                        newTeamName = ""
                        createError = null
                        selectedSkill = "Any"
                        selectedDays = emptySet()
                        inviteOnly = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // --------- Edit squad dialog ---------

    val editTarget = teamBeingEdited
    if (editTarget != null) {
        AlertDialog(
            onDismissRequest = {
                if (!editing) {
                    teamBeingEdited = null
                    editName = ""
                    editError = null
                }
            },
            title = { Text("Edit squad") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = {
                            editName = it
                            editError = null
                        },
                        label = { Text("Squad name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Skill section
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Preferred skill level",
                            style = MaterialTheme.typography.labelMedium
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            skillOptions.forEach { option ->
                                FilterChip(
                                    selected = editSelectedSkill == option,
                                    onClick = { editSelectedSkill = option },
                                    label = { Text(option) }
                                )
                            }
                        }
                    }

                    // Days section
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Days you usually play",
                            style = MaterialTheme.typography.labelMedium
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            dayOptions.forEach { day ->
                                FilterChip(
                                    selected = editSelectedDays.contains(day),
                                    onClick = {
                                        editSelectedDays = if (editSelectedDays.contains(day)) {
                                            editSelectedDays - day
                                        } else {
                                            editSelectedDays + day
                                        }
                                    },
                                    label = { Text(day) }
                                )
                            }
                        }
                    }

                    // Privacy section
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Private squad", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Hide this squad from \"Find squads\". Players can’t send join requests.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = editInviteOnly,
                            onCheckedChange = { editInviteOnly = it }
                        )
                    }

                    if (editError != null) {
                        Text(
                            text = editError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !editing && editName.isNotBlank(),
                    onClick = {
                        if (editName.isBlank()) return@TextButton
                        editing = true

                        val skill = editSelectedSkill.ifBlank { null }
                        val days = editSelectedDays.toList()

                        viewModel.updateTeam(
                            teamId = editTarget.id,
                            name = editName,
                            preferredSkillLevel = skill,
                            playDays = days,
                            inviteOnly = editInviteOnly
                        ) { ok, msg ->
                            editing = false
                            if (ok) {
                                teamBeingEdited = null
                                editName = ""
                                editError = null
                            } else {
                                editError = msg
                            }
                        }
                    }
                ) {
                    Text(if (editing) "Saving…" else "Save")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !editing,
                    onClick = {
                        teamBeingEdited = null
                        editName = ""
                        editError = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // --------- Delete squad dialog ---------

    val deleteTarget = teamBeingDeleted
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = {
                if (!deleting) {
                    teamBeingDeleted = null
                    deleteError = null
                }
            },
            title = { Text("Delete squad") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Are you sure you want to delete “${deleteTarget.name}”?\n" +
                                "This will remove the squad for everyone in it."
                    )
                    if (deleteError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = deleteError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        deleting = true
                        viewModel.deleteTeam(deleteTarget.id) { ok, msg ->
                            deleting = false
                            if (ok) {
                                teamBeingDeleted = null
                                deleteError = null
                            } else {
                                deleteError = msg
                            }
                        }
                    }
                ) {
                    Text(if (deleting) "Deleting…" else "Delete")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        teamBeingDeleted = null
                        deleteError = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // --------- Invite players dialog (owner -> player) ---------

    if (showInviteDialog && invitingTeam != null) {
        AlertDialog(
            onDismissRequest = {
                if (!inviting) {
                    showInviteDialog = false
                    invitingTeam = null
                    inviteUsername = ""
                    inviteError = null
                }
            },
            title = { Text("Invite player to ${invitingTeam!!.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = inviteUsername,
                        onValueChange = {
                            inviteUsername = it
                            inviteError = null
                        },
                        label = { Text("Player username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Enter their BallUp username. They’ll see this squad in the Invites tab.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (inviteError != null) {
                        Text(
                            text = inviteError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !inviting && inviteUsername.isNotBlank(),
                    onClick = {
                        val team = invitingTeam ?: return@TextButton
                        inviting = true
                        viewModel.sendInviteByUsername(team.id, inviteUsername) { ok, msg ->
                            inviting = false
                            if (ok) {
                                showInviteDialog = false
                                invitingTeam = null
                                inviteUsername = ""
                                inviteError = null
                                scope.launch {
                                    snackbarHostState.showSnackbar("Invite sent")
                                }
                            } else {
                                inviteError = msg
                            }
                        }
                    }
                ) {
                    Text(if (inviting) "Sending…" else "Send invite")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !inviting,
                    onClick = {
                        showInviteDialog = false
                        invitingTeam = null
                        inviteUsername = ""
                        inviteError = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // --------- Members bottom sheet ---------

    if (showMembersSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showMembersSheet = false
                membersForTeam = emptyList()
                membersError = null
                membersLoading = false
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "$membersTeamName – players",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))

                when {
                    membersLoading -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.width(12.dp))
                            Text("Loading squad members…")
                        }
                    }

                    membersError != null -> {
                        Text(
                            text = membersError!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    membersForTeam.isEmpty() -> {
                        Text(
                            text = "No members yet. You’re the only one in this squad for now.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    else -> {
                        Spacer(Modifier.height(4.dp))
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                        ) {
                            items(membersForTeam, key = { it.uid }) { profile ->
                                MemberRow(profile = profile)
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // --------- Join requests bottom sheet (owner only) ---------

    if (showRequestsSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showRequestsSheet = false
                requestsForTeam = emptyList()
                requestsError = null
                requestsLoading = false
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "$requestsTeamName – join requests",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))

                when {
                    requestsLoading -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.width(12.dp))
                            Text("Loading requests…")
                        }
                    }

                    requestsError != null -> {
                        Text(
                            text = requestsError!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    requestsForTeam.isEmpty() -> {
                        Text(
                            text = "No pending requests right now.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    else -> {
                        Spacer(Modifier.height(4.dp))
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                        ) {
                            items(requestsForTeam, key = { it.uid }) { req ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                ) {
                                    val profile = req.profile
                                    Text(
                                        text = profile?.username?.ifBlank {
                                            profile.displayName ?: req.uid
                                        } ?: req.uid,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    if (profile != null &&
                                        !profile.displayName.isNullOrBlank() &&
                                        profile.displayName != profile.username
                                    ) {
                                        Text(
                                            text = profile.displayName!!,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (profile != null) {
                                        Text(
                                            text = "Skill: ${profile.skillLevel}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Spacer(Modifier.height(4.dp))

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = {
                                            viewModel.approveJoin(requestsTeamId, req.uid) { ok, msg ->
                                                scope.launch {
                                                    if (ok) {
                                                        requestsForTeam =
                                                            requestsForTeam.filterNot { it.uid == req.uid }
                                                        snackbarHostState.showSnackbar(
                                                            "Approved request"
                                                        )
                                                    } else if (msg != null) {
                                                        snackbarHostState.showSnackbar(msg)
                                                    }
                                                }
                                            }
                                        }) {
                                            Text("Approve")
                                        }
                                        OutlinedButton(onClick = {
                                            viewModel.denyJoin(requestsTeamId, req.uid) { ok, msg ->
                                                scope.launch {
                                                    if (ok) {
                                                        requestsForTeam =
                                                            requestsForTeam.filterNot { it.uid == req.uid }
                                                        snackbarHostState.showSnackbar(
                                                            "Denied request"
                                                        )
                                                    } else if (msg != null) {
                                                        snackbarHostState.showSnackbar(msg)
                                                    }
                                                }
                                            }
                                        }) {
                                            Text("Deny")
                                        }
                                    }
                                }

                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
