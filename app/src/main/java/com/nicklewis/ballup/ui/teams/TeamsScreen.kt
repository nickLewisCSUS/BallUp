@file:OptIn(ExperimentalMaterial3Api::class)

package com.nicklewis.ballup.ui.teams

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.nicklewis.ballup.data.TeamsRepository
import com.nicklewis.ballup.model.Team
import com.nicklewis.ballup.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// ---------- UI STATE + VIEWMODEL ----------

data class TeamsUiState(
    val isLoading: Boolean = true,
    val teams: List<Team> = emptyList(),
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
    }

    private fun observeMyTeams() {
        viewModelScope.launch {
            repo.getTeamsForCurrentUser().collectLatest { teams ->
                val uid = auth.currentUser?.uid.orEmpty()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    teams = teams,
                    currentUid = uid,
                    errorMessage = null
                )
            }
        }
    }

    fun createTeam(name: String, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                repo.createTeam(name.trim())
                onDone(true, null)
            } catch (e: Exception) {
                onDone(false, e.message ?: "Failed to create squad")
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
}

// ---------- SCREEN ----------

@Composable
fun TeamsScreen(
    onBackToMap: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: TeamsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var newTeamName by remember { mutableStateOf("") }
    var createError by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }

    var teamBeingRenamed by remember { mutableStateOf<Team?>(null) }
    var renameText by remember { mutableStateOf("") }
    var renameError by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf(false) }

    var teamBeingDeleted by remember { mutableStateOf<Team?>(null) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }

    // Members sheet state
    var showMembersSheet by remember { mutableStateOf(false) }
    var membersForTeam by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var membersLoading by remember { mutableStateOf(false) }
    var membersError by remember { mutableStateOf<String?>(null) }
    var membersTeamName by remember { mutableStateOf("") }

    val filteredTeams = remember(uiState.teams, searchQuery) {
        if (searchQuery.isBlank()) uiState.teams
        else uiState.teams.filter {
            it.name.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Create squad")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Header / description
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Your Squads",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Create a squad once, then invite them into your runs from the run details screen.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onBackToMap, modifier = Modifier.weight(1f)) {
                        Text("Back to map")
                    }
                    Button(onClick = onOpenSettings, modifier = Modifier.weight(1f)) {
                        Text("Open settings")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search squads") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (uiState.errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // List of squads
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                if (filteredTeams.isEmpty() && !uiState.isLoading) {
                    item {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isBlank()) {
                                "You don’t have any squads yet. Tap + to create one."
                            } else {
                                "No squads match “$searchQuery”."
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    items(filteredTeams, key = { it.id }) { team ->
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
                            onEdit = {
                                teamBeingRenamed = it
                                renameText = it.name
                                renameError = null
                            },
                            onDelete = {
                                teamBeingDeleted = it
                                deleteError = null
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    // Create squad dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!creating) {
                    showCreateDialog = false
                    newTeamName = ""
                    createError = null
                }
            },
            title = { Text("Create new squad") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newTeamName,
                        onValueChange = {
                            newTeamName = it
                            createError = null
                        },
                        label = { Text("Squad name") },
                        singleLine = true
                    )
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
                        viewModel.createTeam(newTeamName) { ok, msg ->
                            creating = false
                            if (ok) {
                                newTeamName = ""
                                createError = null
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
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rename squad dialog
    val renameTarget = teamBeingRenamed
    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = {
                if (!renaming) {
                    teamBeingRenamed = null
                    renameText = ""
                    renameError = null
                }
            },
            title = { Text("Rename squad") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = {
                            renameText = it
                            renameError = null
                        },
                        label = { Text("New squad name") },
                        singleLine = true
                    )
                    if (renameError != null) {
                        Text(
                            text = renameError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !renaming && renameText.isNotBlank(),
                    onClick = {
                        if (renameText.isBlank()) return@TextButton
                        renaming = true
                        viewModel.renameTeam(renameTarget.id, renameText) { ok, msg ->
                            renaming = false
                            if (ok) {
                                teamBeingRenamed = null
                                renameText = ""
                                renameError = null
                            } else {
                                renameError = msg
                            }
                        }
                    }
                ) {
                    Text(if (renaming) "Saving…" else "Save")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !renaming,
                    onClick = {
                        teamBeingRenamed = null
                        renameText = ""
                        renameError = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete squad dialog
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

    // Members bottom sheet
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
}

// ---------- ROWS ----------

@Composable
private fun SquadRow(
    team: Team,
    isOwner: Boolean,
    onViewMembers: (Team) -> Unit,
    onEdit: (Team) -> Unit,
    onDelete: (Team) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = team.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${team.memberUids.size} players",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isOwner) {
                        Text(
                            text = "You’re the owner",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

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
                }
            }

            Spacer(Modifier.height(4.dp))

            TextButton(onClick = { onViewMembers(team) }) {
                Text("View members")
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

