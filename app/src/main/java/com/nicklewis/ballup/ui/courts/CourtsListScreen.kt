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
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.nicklewis.ballup.ui.courts.components.CourtCard
import com.nicklewis.ballup.ui.courts.components.SearchBarWithSuggestions
import com.nicklewis.ballup.ui.courts.components.FilterBar
import com.nicklewis.ballup.ui.courts.components.RunCreateDialog
import com.nicklewis.ballup.util.fetchLastKnownLocation
import com.nicklewis.ballup.util.hasLocationPermission
import com.nicklewis.ballup.vm.StarsViewModel
import com.nicklewis.ballup.firebase.startRun
import com.nicklewis.ballup.firebase.joinRun
import com.nicklewis.ballup.firebase.leaveRun
import kotlinx.coroutines.launch
import com.nicklewis.ballup.vm.PrefsViewModel
import com.nicklewis.ballup.nav.AppNavControllerHolder

@Composable
fun CourtsListScreen(
    vm: CourtsListViewModel = viewModel(),
) {
    val starsVm: StarsViewModel = viewModel()

    val ctx = LocalContext.current
    val prefsVm: PrefsViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(factory = PrefsViewModel.factory(ctx))
    val fused = remember { LocationServices.getFusedLocationProviderClient(ctx) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) fetchLastKnownLocation(fused) { loc -> vm.userLoc = loc }
    }

    // holds the courtId when the dialog is open
    var showCreate by remember { mutableStateOf<String?>(null) }

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

    Column(Modifier.fillMaxSize().padding(16.dp)) {
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
            onToggleIndoor = { vm.showIndoor = !vm.showIndoor },
            onToggleOutdoor = { vm.showOutdoor = !vm.showOutdoor },
            onSortChange = { vm.sortMode = it },
            modifier = Modifier.padding(bottom = 12.dp)
        )

        vm.error?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error) }
        if (vm.courts.isEmpty()) Text("No courts yet. Add one in Firestore to see it here.")
        if (vm.rows.isEmpty()) Text("No courts match your filter.")
        else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(vm.rows, key = { it.courtId }) { row ->
                    CourtCard(
                        row = row,
                        uid = uid,
                        userLoc = vm.userLoc,
                        onStartRun = { courtId -> showCreate = courtId },
                        onJoinRun = { runId ->
                            if (uid == null) { Log.e("Runs","joinRun: not signed in"); return@CourtCard }
                            scope.launch { try { joinRun(db, runId, uid) } catch (e: Exception) { Log.e("Runs","joinRun failed", e) } }
                        },
                        onLeaveRun = { runId ->
                            if (uid == null) { Log.e("Runs","leaveRun: not signed in"); return@CourtCard }
                            scope.launch { try { leaveRun(db, runId, uid) } catch (e: Exception) { Log.e("Runs","leaveRun failed", e) } }
                        },
                        onViewRun = { runId ->
                            AppNavControllerHolder.navController?.navigate("run/$runId") {
                                launchSingleTop = true; restoreState = true
                            }
                        },
                        starsVm = starsVm,
                        prefsVm = prefsVm
                    )
                }
            }
        }

        // DIALOG (place after list so it can overlay)
        showCreate?.let { courtId ->
            RunCreateDialog(
                onDismiss = { showCreate = null },
                onCreate = { startsAt, endsAt, mode, max ->
                    showCreate = null
                    if (uid == null) {
                        Log.e("Runs","startRun: not signed in")
                        return@RunCreateDialog
                    }
                    scope.launch {
                        try {
                            startRun(
                                db = db,
                                courtId = courtId,
                                hostUid = uid,
                                mode = mode,
                                maxPlayers = max,
                                startsAtMillis = startsAt,
                                endsAtMillis = endsAt
                            )
                        } catch (e: Exception) {
                            Log.e("Runs","startRun failed", e)
                        }
                    }
                }
            )
        }
    }
}
