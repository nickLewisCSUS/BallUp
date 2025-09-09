package com.nicklewis.ballup.ui.courts.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng
import com.nicklewis.ballup.util.CourtRow
import com.nicklewis.ballup.util.distanceKm
import com.nicklewis.ballup.util.kmToMiles
import com.nicklewis.ballup.model.Court

@Composable
fun CourtCard(
    row: CourtRow,
    uid: String?,
    userLoc: LatLng?,
    onStartRun: (courtId: String) -> Unit,
    onJoinRun: (runId: String) -> Unit,
    onLeaveRun: (runId: String) -> Unit,
) {
    val courtId = row.courtId
    val court: Court = row.court
    val runId   = row.active?.first
    val currentRun = row.active?.second

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
            }

            Text("${court.type?.uppercase().orEmpty()} • ${court.address.orEmpty()}")

            court.geo?.lat?.let { lat ->
                court.geo?.lng?.let { lng ->
                    userLoc?.let {
                        val mi = kmToMiles(
                            distanceKm(it.latitude, it.longitude, lat, lng)
                        )
                        Text(
                            String.format("%.1f mi away", mi),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (currentRun == null) {
                Button(onClick = { onStartRun(courtId) }) { Text("Start run") }
            } else {
                val alreadyIn = uid != null && (currentRun.playerIds?.contains(uid) == true)
                if (!alreadyIn) {
                    Button(onClick = { runId?.let(onJoinRun) }) { Text("Join") }
                } else {
                    OutlinedButton(onClick = { runId?.let(onLeaveRun) }) { Text("Leave") }
                }
            }
        }
    }
}
