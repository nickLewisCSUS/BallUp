// app/src/main/java/com/nicklewis/ballup/ui/runs/RunDetailsViewModel.kt
package com.nicklewis.ballup.ui.runs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.nicklewis.ballup.data.TeamsRepository
import com.nicklewis.ballup.data.inviteSquadToRun
import com.nicklewis.ballup.model.Run
import com.nicklewis.ballup.model.Team
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class RunDetailsUiState(
    val isLoading: Boolean = true,
    val isHost: Boolean = false,
    val run: Run? = null,
    val ownedTeams: List<Team> = emptyList(),
    val errorMessage: String? = null
)

class RunDetailsViewModel(
    private val runId: String,
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val teamsRepo: TeamsRepository = TeamsRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(RunDetailsUiState())
    val uiState: StateFlow<RunDetailsUiState> = _uiState.asStateFlow()

    private val currentUid: String?
        get() = auth.currentUser?.uid

    init {
        observeRun()
        observeOwnedTeams()
    }

    private fun observeRun() {
        val uid = currentUid
        if (uid == null) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "Not signed in"
            )
            return
        }

        db.collection("runs")
            .document(runId)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "Failed to load run"
                    )
                    return@addSnapshotListener
                }

                if (snap == null || !snap.exists()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        run = null,
                        isHost = false,
                        errorMessage = "Run not found"
                    )
                    return@addSnapshotListener
                }

                val run = snap.toObject(Run::class.java)
                val hostId = snap.getString("hostId") ?: snap.getString("hostUid") ?: run?.hostId
                val isHost = hostId == uid

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    run = run,
                    isHost = isHost,
                    errorMessage = null
                )
            }
    }

    private fun observeOwnedTeams() {
        viewModelScope.launch {
            try {
                teamsRepo.getOwnedTeams().collectLatest { teams ->
                    _uiState.value = _uiState.value.copy(ownedTeams = teams)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message ?: "Failed to load squads"
                )
            }
        }
    }

    fun inviteSquad(team: Team) {
        val uid = currentUid ?: return
        viewModelScope.launch {
            try {
                inviteSquadToRun(
                    db = db,
                    runId = runId,
                    requesterUid = uid,
                    team = team
                )
                _uiState.value = _uiState.value.copy(errorMessage = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message ?: "Failed to invite squad"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

/** Simple factory so you can pass runId from NavHost */
class RunDetailsViewModelFactory(
    private val runId: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RunDetailsViewModel::class.java)) {
            return RunDetailsViewModel(runId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
