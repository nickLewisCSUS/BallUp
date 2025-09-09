package com.nicklewis.ballup.ui.courts

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.nicklewis.ballup.ui.courts.components.CourtCard
import com.nicklewis.ballup.ui.courts.components.SearchBarWithSuggestions
import com.nicklewis.ballup.ui.courts.components.FilterBar
import android.Manifest
import com.nicklewis.ballup.util.fetchLastKnownLocation
import com.nicklewis.ballup.util.hasLocationPermission



@Composable
fun CourtsListScreen(
    vm: CourtsListViewModel = viewModel(),
    onStartRun: (courtId: String) -> Unit,
    onJoinRun: (runId: String) -> Unit,
    onLeaveRun: (runId: String) -> Unit,
) {

    // ---- location bootstrap ----
    val ctx = LocalContext.current
    val fused = remember { LocationServices.getFusedLocationProviderClient(ctx) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted =
            grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (granted) {
            fetchLastKnownLocation(fused) { loc -> vm.userLoc = loc }   // <-- pass onResult
        }
    }

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
        if (vm.courts.isEmpty()) {
            Text("No courts yet. Add one in Firestore to see it here.")
        }

        if (vm.rows.isEmpty()) {
            Text("No courts match your filter.")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(vm.rows, key = { it.courtId }) { row ->
                    CourtCard(
                        row = row,
                        uid = uid,
                        userLoc = vm.userLoc,
                        onStartRun = onStartRun,
                        onJoinRun = onJoinRun,
                        onLeaveRun = onLeaveRun
                    )
                }
            }
        }
    }
}
