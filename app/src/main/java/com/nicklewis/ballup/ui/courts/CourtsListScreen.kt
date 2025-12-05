// ui/courts/CourtsListScreen.kt
package com.nicklewis.ballup.ui.courts

import android.Manifest
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.nicklewis.ballup.data.cancelJoinRequest
import com.nicklewis.ballup.data.joinRun
import com.nicklewis.ballup.data.leaveRun
import com.nicklewis.ballup.data.requestJoinRun
import com.nicklewis.ballup.model.Team
import com.nicklewis.ballup.nav.AppNavControllerHolder
import com.nicklewis.ballup.ui.courts.components.CourtCard
import com.nicklewis.ballup.ui.courts.components.FilterBar
import com.nicklewis.ballup.ui.courts.components.SearchBarWithSuggestions
import com.nicklewis.ballup.ui.runs.RunsViewModel
import com.nicklewis.ballup.ui.runs.StartRunDialog
import com.nicklewis.ballup.ui.teams.TeamsViewModel
import com.nicklewis.ballup.util.fetchLastKnownLocation
import com.nicklewis.ballup.util.hasLocationPermission
import com.nicklewis.ballup.vm.PrefsViewModel
import com.nicklewis.ballup.vm.StarsViewModel
import kotlinx.coroutines.launch

@Composable
fun CourtsListScreen(
    vm: CourtsListViewModel = viewModel(),
) {
    val starsVm: StarsViewModel = viewModel()
    val runsViewModel: RunsViewModel = viewModel()
    val teamsViewModel: TeamsViewModel = viewModel()          // 👈 squads VM

    val ctx = LocalContext.current
    val prefsVm: PrefsViewModel =
        viewModel(factory = PrefsViewModel.factory(ctx))
    val fused = remember { LocationServices.getFusedLocationProviderClient(ctx) }

    val snackbarHostState = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) fetchLastKnownLocation(fused) { loc -> vm.userLoc = loc }
    }

    var showCreate by remember { mutableStateOf<String?>(null) }
    var dialogError by remember { mutableStateOf<String?>(null) }

    var showStarredOnly by rememberSaveable { mutableStateOf(false) }
    val starredIds by starsVm.starred.collectAsState()

    LaunchedEffect(Unit) {
        if (hasLocationPermission(ctx)) {
            fetchLastKnownLocation(fused) { loc -> vm.userLoc = loc }
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val scope = rememberCoroutineScope()
    val db = remember { FirebaseFirestore.getInstance() }

    // runId -> has pending request from this user
    val pendingRequests = remember { mutableStateMapOf<String, Boolean>() }

    // 🔥 real squads from TeamsViewModel
    val teamsUiState by teamsViewModel.uiState.collectAsState()
    val myTeams: List<Team> = teamsUiState.teams

    val visibleRows = remember(vm.rows, showStarredOnly, starredIds) {
        if (!showStarredOnly) {
            vm.rows
        } else {
            vm.rows.filter { row -> row.courtId in starredIds }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { scaffoldPadding ->

        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp)
                .padding(scaffoldPadding)
        ) {

            SearchBarWithSuggestions(
                query = vm.query,
                onQueryChange = { vm.query = it },
                active = vm.searchActive,
                onActiveChange = { vm.searchActive = it },
                suggestions = vm.suggestions,
                onPickSuggestion = { pick ->
                    vm.query = pick
                    vm.searchActive = false
                }
            )

            FilterBar(
                showIndoor = vm.showIndoor,
                showOutdoor = vm.showOutdoor,
                sortMode = vm.sortMode,
                onToggleIndoor = {
                    val newIndoor = !vm.showIndoor
                    if (newIndoor || vm.showOutdoor) {
                        vm.showIndoor = newIndoor
                    }
                },
                onToggleOutdoor = {
                    val newOutdoor = !vm.showOutdoor
                    if (newOutdoor || vm.showIndoor) {
                        vm.showOutdoor = newOutdoor
                    }
                },
                onSortChange = { vm.sortMode = it },
                modifier = Modifier.padding(bottom = 12.dp),
                showStarredOnly = showStarredOnly,
                onToggleStarredOnly = { showStarredOnly = !showStarredOnly }
            )

            vm.error?.let {
                Text(
                    "Error: $it",
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (vm.courts.isEmpty()) {
                Text("No courts yet. Add one in Firestore to see it here.")
            }

            if (visibleRows.isEmpty() && vm.courts.isNotEmpty()) {
                Text("No courts match your filter.")
            } else if (visibleRows.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visibleRows, key = { it.courtId }) { row ->
                        CourtCard(
                            row = row,
                            uid = uid,
                            userLoc = vm.userLoc,
                            onStartRun = { courtId ->
                                dialogError = null
                                showCreate = courtId
                            },
                            onJoinRun = { runId ->
                                if (uid == null) {
                                    Log.e("Runs", "joinRun: not signed in")
                                    return@CourtCard
                                }
                                scope.launch {
                                    try {
                                        joinRun(db, runId, uid)
                                    } catch (e: Exception) {
                                        Log.e("Runs", "joinRun failed", e)
                                        snackbarHostState.showSnackbar(
                                            message = "Couldn't join run. Please try again.",
                                            withDismissAction = true
                                        )
                                    }
                                }
                            },
                            onRequestJoinRun = { runId ->
                                if (uid == null) {
                                    Log.e("Runs", "requestJoinRun: not signed in")
                                    return@CourtCard
                                }
                                scope.launch {
                                    try {
                                        requestJoinRun(db, runId, uid)
                                        pendingRequests[runId] = true
                                        snackbarHostState.showSnackbar(
                                            message = "Request sent to host.",
                                            withDismissAction = true
                                        )
                                    } catch (e: Exception) {
                                        Log.e("Runs", "requestJoinRun failed", e)

                                        if (e.message?.contains("already requested", ignoreCase = true) == true) {
                                            pendingRequests[runId] = true
                                            snackbarHostState.showSnackbar(
                                                message = "You’ve already requested to join this run.",
                                                withDismissAction = true
                                            )
                                        } else {
                                            snackbarHostState.showSnackbar(
                                                message = "Couldn't send request. Please try again.",
                                                withDismissAction = true
                                            )
                                        }
                                    }
                                }
                            },
                            onLeaveRun = { runId ->
                                if (uid == null) {
                                    Log.e("Runs", "leaveRun: not signed in")
                                    return@CourtCard
                                }
                                scope.launch {
                                    try {
                                        leaveRun(db, runId, uid)
                                    } catch (e: Exception) {
                                        if (e.message?.contains("HOST_SOLO_CANNOT_LEAVE") == true) {
                                            Log.w(
                                                "Runs",
                                                "Host is solo and cannot leave; must cancel run."
                                            )
                                            snackbarHostState.showSnackbar(
                                                message = "You’re the only player in this run. Cancel it instead.",
                                                withDismissAction = true
                                            )
                                        } else {
                                            Log.e("Runs", "leaveRun failed", e)
                                            snackbarHostState.showSnackbar(
                                                message = "Couldn't leave the run. Please try again.",
                                                withDismissAction = true
                                            )
                                        }
                                    }
                                }
                            },
                            onCancelRequestRun = { runId ->
                                if (uid == null) {
                                    Log.e("Runs", "cancelJoinRequest: not signed in")
                                    return@CourtCard
                                }
                                scope.launch {
                                    try {
                                        cancelJoinRequest(db, runId, uid)
                                        pendingRequests[runId] = false
                                        snackbarHostState.showSnackbar(
                                            message = "Request cancelled.",
                                            withDismissAction = true
                                        )
                                    } catch (e: Exception) {
                                        Log.e("Runs", "cancelJoinRequest failed", e)
                                        snackbarHostState.showSnackbar(
                                            message = "Couldn't cancel request. Please try again.",
                                            withDismissAction = true
                                        )
                                    }
                                }
                            },
                            hasPendingRequestForRun = { runId ->
                                pendingRequests[runId] == true
                            },
                            onViewRun = { runId ->
                                AppNavControllerHolder.navController?.navigate("run/$runId") {
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            starsVm = starsVm,
                            prefsVm = prefsVm
                        )
                    }
                }
            }

            showCreate?.let { courtId ->
                StartRunDialog(
                    visible = true,
                    courtId = courtId,
                    onDismiss = {
                        dialogError = null
                        showCreate = null
                    },
                    onCreate = { run ->
                        runsViewModel.createRunWithCapacity(
                            run = run,
                            onSuccess = {
                                dialogError = null
                                showCreate = null
                            },
                            onError = { e ->
                                Log.e("RunsVM", "create failed", e)
                                dialogError = humanizeCreateRunError(e)
                            }
                        )
                    },
                    errorMessage = dialogError,
                    teams = myTeams          // ✅ real squads fed into dialog
                )
            }
        }
    }
}

private fun humanizeCreateRunError(t: Throwable): String {
    val msg = t.message ?: ""
    return when {
        "all surfaces are in use" in msg ->
            "This court is full at that time — all surfaces are in use."
        "Daily host limit" in msg ->
            "Daily host limit reached for this court."
        "maximum number of upcoming runs" in msg ->
            "You already have the maximum number of upcoming runs scheduled."
        "Not signed in" in msg ->
            "You must be signed in to create a run."
        "Missing courtId" in msg ->
            "Couldn’t create run: court is missing."
        "Missing start" in msg ->
            "Please pick a start time."
        "Missing end" in msg ->
            "Please pick an end time."
        msg.contains("FAILED_PRECONDITION", ignoreCase = true) &&
                msg.contains("index", ignoreCase = true) ->
            "Run search needs to finish setting up. Try again in a moment."
        else -> "Couldn't start the run. Please adjust the time or try again."
    }
}
