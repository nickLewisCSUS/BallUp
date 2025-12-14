// ui/map/CourtBottomSheet.kt
package com.nicklewis.ballup.ui.map

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.nicklewis.ballup.data.CourtLite
import com.nicklewis.ballup.data.cancelJoinRequest
import com.nicklewis.ballup.data.joinRun
import com.nicklewis.ballup.data.leaveRun
import com.nicklewis.ballup.data.requestJoinRun
import com.nicklewis.ballup.model.Court
import com.nicklewis.ballup.model.Run
import com.nicklewis.ballup.ui.courts.components.RunRow
import com.nicklewis.ballup.util.RowRun
import com.nicklewis.ballup.util.openDirections
import com.nicklewis.ballup.vm.StarsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourtBottomSheet(
    selected: Pair<String, Court>,
    runs: List<Pair<String, Run>>,
    userNames: Map<String, String>, // should now be username-only from map screen
    starredIds: Set<String>,
    starsVm: StarsViewModel,
    onOpenRunDetails: (runId: String, hidePlayers: Boolean) -> Unit,
    onDismiss: () -> Unit,
    onStartRunHere: (courtId: String) -> Unit
) {
    val (courtId, court) = selected
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }

    val today = remember { LocalDate.now() }

    // runId -> pending request (HOST_APPROVAL)
    val pendingRequests = remember { mutableStateMapOf<String, Boolean>() }

    val lat = court.geo?.lat
    val lng = court.geo?.lng

    // ---- Weather state ----
    var weather by remember(courtId) { mutableStateOf<CourtWeather?>(null) }
    var weatherLoading by remember(courtId) { mutableStateOf(false) }
    var weatherError by remember(courtId) { mutableStateOf<String?>(null) }

    LaunchedEffect(lat, lng) {
        if (lat == null || lng == null) return@LaunchedEffect
        weatherLoading = true
        weatherError = null
        try {
            weather = fetchCourtWeather(lat, lng)
        } catch (_: Exception) {
            weatherError = "Weather unavailable"
        } finally {
            weatherLoading = false
        }
    }

    // ✅ Build runs: active + scheduled, today only, hide ended
    val runsForSheet: List<RowRun> = remember(runs, courtId, today) {
        val nowMillis = System.currentTimeMillis()

        runs.asSequence()
            .filter { (_, run) ->
                run.courtId == courtId &&
                        (run.status == "active" || run.status == "scheduled")
            }
            .mapNotNull { (runId, run) ->
                val startsAt = run.startsAt ?: return@mapNotNull null
                val endsAt = run.endsAt

                val runDate = startsAt.toDate().toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                if (runDate != today) return@mapNotNull null

                val endMs = endsAt?.toDate()?.time ?: startsAt.toDate().time
                if (endMs < nowMillis) return@mapNotNull null

                val playerIds = run.playerIds.orEmpty()
                val allowedUids = run.allowedUids.orEmpty()

                val playerCount = run.playerCount ?: playerIds.size
                val maxPlayers = run.maxPlayers ?: 0
                val access = run.access ?: "OPEN"

                // Run model stores host uid in hostId
                val hostId = run.hostId ?: ""
                val hostUid = hostId

                RowRun(
                    id = runId,
                    name = run.name,
                    startsAt = run.startsAt,
                    endsAt = run.endsAt,
                    playerCount = playerCount,
                    maxPlayers = maxPlayers,
                    playerIds = playerIds,
                    access = access,
                    hostId = hostId,
                    hostUid = hostUid,
                    allowedUids = allowedUids
                )
            }
            .sortedBy { it.startsAt?.toDate()?.time ?: Long.MAX_VALUE }
            .toList()
    }

    val liveCount = remember(runsForSheet) {
        val nowMillis = System.currentTimeMillis()
        runsForSheet.count { rr ->
            val start = rr.startsAt?.toDate()?.time ?: Long.MAX_VALUE
            val end = rr.endsAt?.toDate()?.time ?: start
            nowMillis in start..end
        }
    }

    val isStarred = courtId in starredIds

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    court.name.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = {
                        val safeLat = court.geo?.lat ?: 0.0
                        val safeLng = court.geo?.lng ?: 0.0

                        val courtLite = CourtLite(
                            id = courtId,
                            name = court.name.orEmpty(),
                            lat = safeLat,
                            lng = safeLng
                        )

                        starsVm.toggle(
                            court = courtLite,
                            star = !isStarred,
                            runAlertsEnabled = false
                        )
                    }
                ) {
                    Icon(
                        imageVector = if (isStarred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (isStarred) "Unfavorite court" else "Favorite court"
                    )
                }
            }

            Text(court.address.orEmpty(), style = MaterialTheme.typography.bodyMedium)

            Text(
                listOfNotNull(
                    court.type?.uppercase(),
                    if (court.amenities?.lights == true) "Lights" else null,
                    if (court.amenities?.restrooms == true) "Restrooms" else null
                ).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall
            )

            WeatherRow(
                weather = weather,
                loading = weatherLoading,
                error = weatherError
            )

            if (runsForSheet.isNotEmpty()) {
                Text(
                    text = if (liveCount > 0)
                        "Pickup running: $liveCount run" + if (liveCount == 1) "" else "s"
                    else
                        "Runs today: ${runsForSheet.size}",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    runsForSheet.forEach { rr ->
                        val hostUid = rr.hostUid?.trim().orEmpty().ifBlank {
                            rr.hostId?.trim().orEmpty()
                        }

                        // ✅ This should be username-only from CourtsMapScreen
                        val hostLabel = userNames[hostUid]?.trim().takeIf { !it.isNullOrBlank() }

                        RunRow(
                            rr = rr,
                            currentUid = uid,
                            onView = { hidePlayers ->
                                onOpenRunDetails(rr.id, hidePlayers)
                            },
                            onJoin = {
                                if (uid == null) return@RunRow
                                scope.launch {
                                    try {
                                        joinRun(db, rr.id, uid)
                                    } catch (e: Exception) {
                                        Log.e("CourtBottomSheet", "joinRun failed", e)
                                    }
                                }
                            },
                            onRequestJoin = {
                                if (uid == null) return@RunRow
                                scope.launch {
                                    try {
                                        requestJoinRun(db, rr.id, uid)
                                        pendingRequests[rr.id] = true
                                    } catch (e: Exception) {
                                        Log.e("CourtBottomSheet", "requestJoinRun failed", e)
                                        if (e.message?.contains("already requested", ignoreCase = true) == true) {
                                            pendingRequests[rr.id] = true
                                        }
                                    }
                                }
                            },
                            onLeave = {
                                if (uid == null) return@RunRow
                                scope.launch {
                                    try {
                                        leaveRun(db, rr.id, uid)
                                    } catch (e: Exception) {
                                        Log.e("CourtBottomSheet", "leaveRun failed", e)
                                    }
                                }
                            },
                            hasPendingRequest = (pendingRequests[rr.id] == true),
                            onCancelRequest = {
                                if (uid == null) return@RunRow
                                scope.launch {
                                    try {
                                        cancelJoinRequest(db, rr.id, uid)
                                        pendingRequests[rr.id] = false
                                    } catch (e: Exception) {
                                        Log.e("CourtBottomSheet", "cancelJoinRequest failed", e)
                                    }
                                }
                            },
                            hostLabel = hostLabel
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { onStartRunHere(courtId) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Start run here")
                }

                OutlinedButton(
                    enabled = lat != null && lng != null,
                    onClick = {
                        if (lat != null && lng != null) {
                            openDirections(context, lat, lng, court.name)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Directions") }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

private data class CourtWeather(
    val tempC: Double,
    val tempF: Double,
    val label: String,
    val emoji: String
)

private fun weatherLabelFromCode(code: Int): Pair<String, String> {
    return when (code) {
        0 -> "Sunny" to "☀️"
        1, 2 -> "Partly cloudy" to "🌤️"
        3 -> "Cloudy" to "☁️"
        45, 48 -> "Foggy" to "🌫️"
        51, 53, 55, 56, 57 -> "Drizzle" to "🌦️"
        61, 63, 65, 66, 67 -> "Rainy" to "🌧️"
        71, 73, 75, 77 -> "Snowy" to "🌨️"
        80, 81, 82 -> "Rain showers" to "🌦️"
        95, 96, 99 -> "Thunderstorm" to "⛈️"
        else -> "Weather" to "🌡️"
    }
}

private suspend fun fetchCourtWeather(lat: Double, lng: Double): CourtWeather {
    return withContext(Dispatchers.IO) {
        val url =
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lng&current_weather=true&temperature_unit=celsius&windspeed_unit=mph"
        val json = URL(url).readText()
        val root = JSONObject(json)
        val current = root.getJSONObject("current_weather")

        val tempC = current.getDouble("temperature")
        val code = current.optInt("weathercode", -1)

        val (label, emoji) = weatherLabelFromCode(code)
        val tempF = (tempC * 9.0 / 5.0) + 32.0

        CourtWeather(
            tempC = tempC,
            tempF = tempF,
            label = label,
            emoji = emoji
        )
    }
}

@Composable
private fun WeatherRow(
    weather: CourtWeather?,
    loading: Boolean,
    error: String?
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Weather", style = MaterialTheme.typography.labelMedium)

        when {
            loading -> Text(
                "Loading…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            error != null -> Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )

            weather != null -> {
                val tempText = String.format(
                    Locale.US,
                    "%.0f°F (%.0f°C)",
                    weather.tempF,
                    weather.tempC
                )
                Text(
                    "${weather.emoji} ${weather.label} • $tempText",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            else -> Text(
                "—",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
