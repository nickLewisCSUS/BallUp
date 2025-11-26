// ui/courts/components/CourtCard.kt
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

@Composable
fun CourtCard(
    row: CourtRow,
    uid: String?,
    userLoc: LatLng?,
    onStartRun: (courtId: String) -> Unit,
    onJoinRun: (runId: String) -> Unit,
    onRequestJoinRun: (runId: String) -> Unit,
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

            // --- Header: name + star
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = court.name.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                StarButton(
                    checked = starred.contains(courtId),
                    onCheckedChange = { v ->
                        val lite = CourtLite(courtId, court.name, court.geo?.lat, court.geo?.lng)
                        starsVm.toggle(lite, v, runAlertsEnabled = prefs.runAlerts)
                    }
                )
            }

            // --- Meta: type • address • distance (right under the name)
            Spacer(Modifier.height(4.dp))
            MetaLine(courtRow = row, userLoc = userLoc)

            Spacer(Modifier.height(10.dp))

            // --- Runs preview (if any)
            if (row.runsForCard.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.runsForCard.forEach { rr ->
                        RunRow(
                            rr = rr,
                            currentUid = uid,
                            onView = { onViewRun(rr.id) },
                            onJoin = { onJoinRun(rr.id) },
                            onRequestJoin = { onRequestJoinRun(rr.id) },
                            onLeave = { onLeaveRun(rr.id) }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Divider()
                Spacer(Modifier.height(10.dp))
            }

            // --- Footer actions: compact + balanced
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = { AppNavControllerHolder.navController?.navigate("court/$courtId/runs") }
                ) {
                    Text("See all runs")
                }

                FilledTonalButton(
                    onClick = { onStartRun(courtId) },
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
                    modifier = Modifier.defaultMinSize(minWidth = 0.dp)
                ) {
                    Text("Create run", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun MetaLine(courtRow: CourtRow, userLoc: LatLng?) {
    val court = courtRow.court
    val type = court.type?.uppercase().orEmpty()
    val addr = court.address.orEmpty()

    // distance if we can compute it
    val distanceText = run {
        val lat = court.geo?.lat
        val lng = court.geo?.lng
        if (lat != null && lng != null && userLoc != null) {
            val mi = kmToMiles(distanceKm(userLoc.latitude, userLoc.longitude, lat, lng))
            String.format("%.1f mi away", mi)
        } else null
    }

    val meta = buildString {
        if (type.isNotBlank()) append(type)
        if (addr.isNotBlank()) {
            if (isNotEmpty()) append(" • ")
            append(addr)
        }
        if (!distanceText.isNullOrBlank()) {
            if (isNotEmpty()) append(" • ")
            append(distanceText)
        }
    }

    if (meta.isNotBlank()) {
        Text(
            text = meta,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
