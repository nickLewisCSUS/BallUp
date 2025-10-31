package com.nicklewis.ballup.ui.courts.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng
import com.nicklewis.ballup.data.CourtLite
import com.nicklewis.ballup.ui.components.StarButton
import com.nicklewis.ballup.util.CourtRow
import com.nicklewis.ballup.util.distanceKm
import com.nicklewis.ballup.util.kmToMiles
import com.nicklewis.ballup.vm.PrefsViewModel
import com.nicklewis.ballup.vm.StarsViewModel
import com.nicklewis.ballup.nav.AppNavControllerHolder

// ui/courts/components/CourtCard.kt
@Composable
fun CourtCard(
    row: CourtRow,
    uid: String?,
    userLoc: LatLng?,
    onStartRun: (courtId: String) -> Unit,
    onJoinRun: (runId: String) -> Unit,
    onLeaveRun: (runId: String) -> Unit,
    onViewRun: (runId: String) -> Unit,
    starsVm: StarsViewModel,
    prefsVm: PrefsViewModel,
) {
    val courtId = row.courtId
    val court = row.court
    val starred by starsVm.starred.collectAsState()
    val prefs by prefsVm.prefs.collectAsState()

    ElevatedCard {
        Column(Modifier.padding(12.dp)) {

            // Header row
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(court.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                StarButton(
                    checked = starred.contains(courtId),
                    onCheckedChange = { v ->
                        val lite = CourtLite(courtId, court.name, court.geo?.lat, court.geo?.lng)
                        starsVm.toggle(lite, v, runAlertsEnabled = prefs.runAlerts)
                    }
                )
            }

            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (row.runsForCard.isNotEmpty()) {
                    row.runsForCard.forEach { rr ->
                        RunRow(
                            rr = rr,
                            currentUid = uid,
                            onView = { onViewRun(rr.id) },
                            onJoin = { onJoinRun(rr.id) },
                            onLeave = { onLeaveRun(rr.id) }
                        )
                    }
                }

                // Always show buttons below
                TextButton(
                    onClick = { AppNavControllerHolder.navController?.navigate("court/${row.courtId}/runs") }
                ) {
                    Text("See all runs")
                }

                Button(
                    onClick = { onStartRun(courtId) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Start run") }
            }


            Spacer(Modifier.height(4.dp))
            TextButton(onClick = {
                AppNavControllerHolder.navController?.navigate("court/${row.courtId}/runs")
            }) { Text("See all runs") }

            // Court meta (type, address, distance)
            Spacer(Modifier.height(10.dp))
            Text("${court.type?.uppercase().orEmpty()} • ${court.address.orEmpty()}")
            court.geo?.lat?.let { lat ->
                court.geo?.lng?.let { lng ->
                    userLoc?.let {
                        val mi = kmToMiles(distanceKm(it.latitude, it.longitude, lat, lng))
                        Text(String.format("%.1f mi away", mi), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}