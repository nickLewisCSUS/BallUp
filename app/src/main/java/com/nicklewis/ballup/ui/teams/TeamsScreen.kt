@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.nicklewis.ballup.ui.teams

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.material3.FilterChipDefaults
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.nicklewis.ballup.data.TeamsRepository
import com.nicklewis.ballup.data.TeamsRepository.PendingTeamRequest
import com.nicklewis.ballup.model.Team
import com.nicklewis.ballup.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------- UI STATE + VIEWMODEL ----------

data class TeamsUiState(
    val isLoading: Boolean = true,
    val teams: List<Team> = emptyList(),              // squads I’m in
    val discoverableTeams: List<Team> = emptyList(),  // squads I can request
    val pendingJoinTeamIds: Set<String> = emptySet(), // teams I’ve requested to join
    val incomingInvites: List<TeamsRepository.TeamInviteForUser> = emptyList(), // NEW
    val errorMessage: String? = null,
    val currentUid: String = ""
)

class TeamsViewModel(
    private val repo: TeamsRepository = TeamsRepository(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow(TeamsUiState())
    val uiState: StateFlow<TeamsUiState> = _uiState.asStateFlow()

    init {
        observeMyTeams()
        observeDiscoverableTeams()
        observeMyPendingTeamRequests()
        observeInvitesForMe()
    }

    private fun observeMyTeams() {
        viewModelScope.launch {
            repo.getTeamsForCurrentUser().collectLatest { teams ->
                val uid = auth.currentUser?.uid.orEmpty()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        teams = teams,
                        currentUid = uid,
                        errorMessage = null
                    )
                }
            }
        }
    }

    private fun observeDiscoverableTeams() {
        viewModelScope.launch {
            repo.getDiscoverableTeams().collectLatest { teams ->
                _uiState.update { it.copy(discoverableTeams = teams) }
            }
        }
    }

    private fun observeMyPendingTeamRequests() {
        viewModelScope.launch {
            repo.getPendingJoinRequestsForCurrentUser().collectLatest { pendingIds ->
                _uiState.update { it.copy(pendingJoinTeamIds = pendingIds.toSet()) }
            }
        }
    }

    private fun observeInvitesForMe() {
        viewModelScope.launch {
            repo.getInvitesForCurrentUser().collectLatest { invites ->
                _uiState.update { it.copy(incomingInvites = invites) }
            }
        }
    }

    fun createTeam(
        name: String,
        preferredSkillLevel: String?,
        playDays: List<String>,
        inviteOnly: Boolean,
        onDone: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repo.createTeam(name.trim(), preferredSkillLevel, playDays, inviteOnly)
                onDone(true, null)
            } catch (e: Exception) {
                onDone(false, e.message ?: "Failed to create squad")
            }
        }
    }

    fun updateTeam(
        teamId: String,
        name: String,
        preferredSkillLevel: String?,
        playDays: List<String>,
        inviteOnly: Boolean,
        onDone: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repo.updateTeam(
                    teamId = teamId,
                    name = name.trim(),
                    preferredSkillLevel = preferredSkillLevel,
                    playDays = playDays,
                    inviteOnly = inviteOnly
                )
                onDone(true, null)
            } catch (e: Exception) {
                onDone(false, e.message ?: "Failed to update squad")
            }
        }
    }

    fun renameTeam(teamId: String, newName: String, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                repo.renameTeam(teamId, newName.trim())
                onDone(true, null)
            } catch (e: Exception) {
                onDone(false, e.message ?: "Failed to rename squad")
            }
        }
    }

    fun deleteTeam(teamId: String, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                repo.deleteTeam(teamId)
                onDone(true, null)
            } catch (e: Exception) {
                onDone(false, e.message ?: "Failed to delete squad")
            }
        }
    }

    fun loadMembersFor(
        team: Team,
        onDone: (Boolean, List<UserProfile>?, String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val profiles = repo.getMembers(team.memberUids)
                onDone(true, profiles, null)
            } catch (e: Exception) {
                onDone(false, null, e.message ?: "Failed to load squad members")
            }
        }
    }

    // ---- join / leave / cancel ----

    fun requestToJoinTeam(teamId: String, onDone: (Boolean, String?) -> Unit) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onDone(false, "Not signed in")
            return
        }
        viewModelScope.launch {
            try {
                repo.requestToJoinTeam(teamId, uid)
                _uiState.update {
                    it.copy(pendingJoinTeamIds = it.pendingJoinTeamIds + teamId)
                }
                onDone(true, null)
            } catch (e: Exception) {
                onDone(false, e.message ?: "Couldn't request to join squad")
            }
        }
    }

    fun cancelJoinRequest(teamId: String, onDone: (Boolean, String?) -> Unit) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onDone(false, "Not signed in")
            return
        }
        viewModelScope.launch {
            try {
                repo.cancelJoinRequest(teamId, uid)
                _uiState.update {
                    it.copy(pendingJoinTeamIds = it.pendingJoinTeamIds - teamId)
                }
                onDone(true, null)
            } catch (e: Exception) {
                onDone(false, e.message ?: "Couldn't cancel join request")
            }
        }
    }

    fun leaveTeam(teamId: String, onDone: (Boolean, String?) -> Unit) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onDone(false, "Not signed in")
            return
        }
        viewModelScope.launch {
            try {
                repo.leaveTeam(teamId, uid)
                onDone(true, null)
            } catch (e: Exception) {
                onDone(false, e.message ?: "Couldn't leave squad")
            }
        }
    }

    // ---- host approval helpers ----

    fun loadJoinRequestsFor(
        team: Team,
        onDone: (Boolean, List<PendingTeamRequest>?, String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val pending = repo.getPendingRequestsForTeam(team.id)
                onDone(true, pending, null)
            } catch (e: Exception) {
                onDone(false, null, e.message ?: "Failed to load join requests")
            }
        }
    }

    fun approveJoin(teamId: String, uid: String, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                repo.approveJoinRequest(teamId, uid)
                onDone(true, null)
            } catch (e: Exception) {
                onDone(false, e.message ?: "Failed to approve request")
            }
        }
    }

    fun denyJoin(teamId: String, uid: String, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                repo.denyJoinRequest(teamId, uid)
                onDone(true, null)
            } catch (e: Exception) {
                onDone(false, e.message ?: "Failed to deny request")
            }
        }
    }

    // ---- invites: owner -> player ----

    fun sendInviteByUsername(
        teamId: String,
        username: String,
        onDone: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repo.sendTeamInviteByUsername(teamId, username.trim())
                onDone(true, null)
            } catch (e: Exception) {
                onDone(false, e.message ?: "Failed to send invite")
            }
        }
    }

    fun acceptInvite(inviteId: String, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                repo.acceptInvite(inviteId)
                onDone(true, null)
            } catch (e: Exception) {
                onDone(false, e.message ?: "Failed to accept invite")
            }
        }
    }

    fun declineInvite(inviteId: String, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                repo.declineInvite(inviteId)
                onDone(true, null)
            } catch (e: Exception) {
                onDone(false, e.message ?: "Failed to decline invite")
            }
        }
    }
}

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
                    Icon(Icons.Default.Add, contentDescription = "Create squad")
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
                    text = { Text("Invites") }
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

// ---------- ROWS ----------

@Composable
private fun SquadRow(
    team: Team,
    isOwner: Boolean,
    onViewMembers: (Team) -> Unit,
    onEdit: (Team) -> Unit,
    onDelete: (Team) -> Unit,
    onLeave: (Team) -> Unit,
    onViewRequests: (Team) -> Unit,
    onInvite: (Team) -> Unit,
    currentUid: String
) {
    val isMemberButNotOwner = !isOwner && team.memberUids.contains(currentUid)

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // LEFT: details
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = team.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${team.memberUids.size} players",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (team.preferredSkillLevel != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Skill: ${team.preferredSkillLevel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (team.playDays.isNotEmpty()) {
                        Text(
                            text = "Days: ${team.playDays.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (team.inviteOnly) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Private squad (host invites only)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                    when {
                        isOwner -> {
                            Text(
                                text = "You’re the owner",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        isMemberButNotOwner -> {
                            Text(
                                text = "You’re in this squad",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }

                Spacer(Modifier.width(12.dp))

                // RIGHT: actions stacked so they don't squeeze the content
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isOwner) {
                        Row {
                            IconButton(onClick = { onEdit(team) }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit squad"
                                )
                            }
                            IconButton(onClick = { onDelete(team) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete squad"
                                )
                            }
                        }
                        OutlinedButton(onClick = { onViewRequests(team) }) {
                            Text("View requests")
                        }
                        OutlinedButton(onClick = { onInvite(team) }) {
                            Text("Invite players")
                        }
                    } else if (isMemberButNotOwner) {
                        OutlinedButton(onClick = { onLeave(team) }) {
                            Text("Leave")
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            TextButton(onClick = { onViewMembers(team) }) {
                Text("View members")
            }
        }
    }
}

@Composable
private fun DiscoverSquadRow(
    team: Team,
    isOwner: Boolean,
    isMember: Boolean,
    hasRequested: Boolean,
    onRequestJoin: (Team) -> Unit,
    onCancelRequest: (Team) -> Unit,
    onViewMembers: (Team) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // LEFT: details
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = team.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${team.memberUids.size} players",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (team.preferredSkillLevel != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Skill: ${team.preferredSkillLevel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (team.playDays.isNotEmpty()) {
                        Text(
                            text = "Days: ${team.playDays.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (team.inviteOnly) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Private squad (host invites only)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                    when {
                        isOwner -> {
                            Text(
                                text = "You own this squad",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        isMember -> {
                            Text(
                                text = "Already in this squad",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        hasRequested -> {
                            Text(
                                text = "Request pending",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }

                Spacer(Modifier.width(12.dp))

                // RIGHT: actions stacked
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    when {
                        isOwner || isMember -> {
                            // no join actions
                        }

                        hasRequested -> {
                            OutlinedButton(onClick = { onCancelRequest(team) }) {
                                Text("Cancel")
                            }
                        }

                        else -> {
                            Button(onClick = { onRequestJoin(team) }) {
                                Text("Request")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            TextButton(onClick = { onViewMembers(team) }) {
                Text("View members")
            }
        }
    }
}

@Composable
private fun InviteRow(
    invite: TeamsRepository.TeamInviteForUser,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = invite.teamName,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "You’ve been invited to join this squad",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (invite.preferredSkillLevel != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Skill: ${invite.preferredSkillLevel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (invite.playDays.isNotEmpty()) {
                Text(
                    text = "Days: ${invite.playDays.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (invite.inviteOnly) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Private squad (host invites only)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.align(Alignment.End)
            ) {
                OutlinedButton(onClick = onDecline) {
                    Text("Decline")
                }
                Button(onClick = onAccept) {
                    Text("Accept")
                }
            }
        }
    }
}

@Composable
private fun MemberRow(profile: UserProfile) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Text(
            text = profile.username.ifBlank { profile.displayName ?: "Unnamed player" },
            style = MaterialTheme.typography.bodyLarge
        )
        if (!profile.displayName.isNullOrBlank() &&
            profile.displayName != profile.username
        ) {
            Text(
                text = profile.displayName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "Skill: ${profile.skillLevel}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
