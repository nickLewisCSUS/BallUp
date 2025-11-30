package com.nicklewis.ballup.ui.teams

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.nicklewis.ballup.data.TeamsRepository
import com.nicklewis.ballup.model.Team
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// ---------- UI STATE + VIEWMODEL ----------

data class TeamsUiState(
    val isLoading: Boolean = true,
    val teams: List<Team> = emptyList(),
    val errorMessage: String? = null
)

class TeamsViewModel(
    private val repo: TeamsRepository = TeamsRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(TeamsUiState())
    val uiState: StateFlow<TeamsUiState> = _uiState.asStateFlow()

    init {
        observeOwnedTeams()
    }

    private fun observeOwnedTeams() {
        viewModelScope.launch {
            repo.getOwnedTeams().collectLatest { teams ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    teams = teams,
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
                if (uiState.teams.isEmpty() && !uiState.isLoading) {
                    item {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "You don’t have any squads yet. Tap + to create one.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    items(uiState.teams, key = { it.id }) { team ->
                        SquadRow(team = team)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }

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
}

// ---------- ROW ----------

@Composable
private fun SquadRow(team: Team) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(text = team.name, style = MaterialTheme.typography.titleMedium)
        Text(
            text = "${team.memberUids.size} players",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // Later: add "View members" / "Edit squad" / "Delete squad"
    }
}
