package com.nicklewis.ballup.ui.teams

import androidx.lifecycle.ViewModel
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

// ---------- LIMITS ----------
private const val MAX_OWNED_TEAMS = 3     // lead / in charge of
private const val MAX_JOINED_TEAMS = 10   // total squads you're a member of (including ones you own)

private fun TeamsUiState.ownedCount(): Int =
    teams.count { it.ownerUid == currentUid }

private fun TeamsUiState.joinedCount(): Int =
    teams.size

// ---------- UI STATE + VIEWMODEL ----------

data class TeamsUiState(
    val isLoading: Boolean = true,
    val teams: List<Team> = emptyList(),              // squads I’m in
    val discoverableTeams: List<Team> = emptyList(),  // squads I can request
    val pendingJoinTeamIds: Set<String> = emptySet(), // teams I’ve requested to join
    val incomingInvites: List<TeamsRepository.TeamInviteForUser> = emptyList(),
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
            val s = uiState.value
            val owned = s.ownedCount()
            val joined = s.joinedCount()

            if (owned >= MAX_OWNED_TEAMS) {
                onDone(
                    false,
                    "You can only own $MAX_OWNED_TEAMS squads. Delete one to create another."
                )
                return@launch
            }
            if (joined >= MAX_JOINED_TEAMS) {
                onDone(
                    false,
                    "You can only be in $MAX_JOINED_TEAMS squads. Leave one to create another."
                )
                return@launch
            }

            try {
                // NOTE: for true enforcement, also enforce in repository (transaction/rules)
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
            val s = uiState.value
            val joined = s.joinedCount()

            if (joined >= MAX_JOINED_TEAMS) {
                onDone(
                    false,
                    "You can only be in $MAX_JOINED_TEAMS squads. Leave one to join another."
                )
                return@launch
            }

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
                // NOTE: the joining player's limit should be enforced server-side / repo-side.
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
            val s = uiState.value
            val joined = s.joinedCount()

            if (joined >= MAX_JOINED_TEAMS) {
                onDone(
                    false,
                    "You can only be in $MAX_JOINED_TEAMS squads. Leave one to accept this invite."
                )
                return@launch
            }

            try {
                // NOTE: for true enforcement, also enforce in repository (transaction/rules)
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
