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
import com.nicklewis.ballup.model.Court
import com.nicklewis.ballup.ui.components.StarButton
import com.nicklewis.ballup.util.CourtRow
import com.nicklewis.ballup.util.distanceKm
import com.nicklewis.ballup.util.kmToMiles
import com.nicklewis.ballup.vm.PrefsViewModel
import com.nicklewis.ballup.vm.StarsViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    val court: Court = row.court
    val runId = row.active?.first
    val currentRun = row.active?.second

    val starred by starsVm.starred.collectAsState()
    val prefs by prefsVm.prefs.collectAsState()

    val courtLite = CourtLite(
        id = row.courtId,
        name = row.court.name,
        lat = row.court.geo?.lat,
        lng = row.court.geo?.lng
    )

    ElevatedCard {
        Column(Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(court.name.orEmpty(), style = MaterialTheme.typography.titleMedium)

                currentRun?.let { r ->
                    val open = (r.maxPlayers - r.playerCount).coerceAtLeast(0)
                    val label = when {
                        open > 0 -> "Open • $open left"
                        r.playerCount >= r.maxPlayers -> "Full"
                        else -> "Active"
                    }
                    AssistChip(onClick = {}, label = { Text(label) })
                }

                StarButton(
                    checked = starred.contains(row.courtId),
                    onCheckedChange = { v ->
                        starsVm.toggle(courtLite, v, runAlertsEnabled = prefs.runAlerts)
                    }
                )
            }

            // --- NEW: show run name and time window if available ---
            currentRun?.let { r ->
                if (!r.name.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = r.name!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                val start = r.startsAt
                val end = r.endsAt
                if (start != null && end != null) {
                    val zone = ZoneId.systemDefault()
                    val s = start.toDate().toInstant().atZone(zone).toLocalDateTime()
                    val e = end.toDate().toInstant().atZone(zone).toLocalDateTime()
                    val tFmt = DateTimeFormatter.ofPattern("h:mm a")
                    val dFmt = DateTimeFormatter.ofPattern("EEE, MMM d")
                    val window = if (s.toLocalDate() == e.toLocalDate())
                        "${dFmt.format(s)} • ${tFmt.format(s)}–${tFmt.format(e)}"
                    else
                        "${dFmt.format(s)} ${tFmt.format(s)} → ${dFmt.format(e)} ${tFmt.format(e)}"
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = window,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // --- Court info ---
            Spacer(Modifier.height(6.dp))
            Text("${court.type?.uppercase().orEmpty()} • ${court.address.orEmpty()}")

            court.geo?.lat?.let { lat ->
                court.geo?.lng?.let { lng ->
                    userLoc?.let {
                        val mi = kmToMiles(distanceKm(it.latitude, it.longitude, lat, lng))
                        Text(String.format("%.1f mi away", mi), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (currentRun == null) {
                Button(onClick = { onStartRun(courtId) }) { Text("Start run") }
            } else {
                val alreadyIn = uid != null && (currentRun.playerIds?.contains(uid) == true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Primary action
                    Button(onClick = { runId?.let(onViewRun) }) { Text("View run") }

                    // Secondary action depends on membership
                    if (!alreadyIn) {
                        OutlinedButton(onClick = { runId?.let(onJoinRun) }) { Text("Join") }
                    } else {
                        OutlinedButton(onClick = { runId?.let(onLeaveRun) }) { Text("Leave") }
                    }
                }
            }
        }
    }
}
