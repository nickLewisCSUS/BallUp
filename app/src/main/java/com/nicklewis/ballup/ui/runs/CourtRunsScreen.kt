package com.nicklewis.ballup.ui.runs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import com.nicklewis.ballup.model.Run
import com.nicklewis.ballup.nav.AppNavControllerHolder
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Locale

// Local wrapper so we can keep both doc id + Run data
private data class CourtRunItem(
    val id: String,
    val run: Run
)

@Composable
fun CourtRunsScreen(
    courtId: String,
    onBack: () -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    var isLoading by remember { mutableStateOf(true) }
    var runs by remember { mutableStateOf<List<CourtRunItem>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(courtId) {
        scope.launch {
            try {
                isLoading = true
                val snap = db.collection("runs")
                    .whereEqualTo("courtId", courtId)
                    .get()
                    .await()

                val items = snap.documents.mapNotNull { doc ->
                    val run = doc.toObject(Run::class.java)
                    run?.let { CourtRunItem(id = doc.id, run = it) }
                }

                // Optional: sort by start time
                runs = items.sortedBy { it.run.startsAt?.toDate() ?: it.run.startTime?.toDate() }

                error = null
            } catch (e: Exception) {
                error = e.message ?: "Failed to load runs"
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        // --- Header ---
        Text(
            text = "Runs at this court",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Court ID: $courtId",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        when {
            isLoading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Loading runs…")
                }
            }

            error != null -> {
                Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onBack) {
                    Text("Back")
                }
            }

            runs.isEmpty() -> {
                Text(
                    text = "No runs found for this court.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onBack) {
                    Text("Back")
                }
            }

            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f, fill = true)
                ) {
                    items(runs, key = { it.id }) { item ->
                        RunCard(item = item)
                    }
                }

                Spacer(Modifier.height(16.dp))

                Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text("Back")
                }
            }
        }
    }
}

@Composable
private fun RunCard(item: CourtRunItem) {
    val run = item.run

    val timeText = remember(run.startsAt, run.startTime) {
        val ts = run.startsAt ?: run.startTime
        ts?.let {
            val df = SimpleDateFormat("EEE, MMM d • h:mm a", Locale.getDefault())
            df.format(it.toDate())
        } ?: "Time TBD"
    }

    // Prefer explicit playerCount, fall back to playerIds size if needed
    val players = remember(run.playerCount, run.playerIds) {
        when {
            run.playerCount > 0 -> run.playerCount
            run.playerIds != null -> run.playerIds!!.size
            else -> 0
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                AppNavControllerHolder.navController
                    ?.navigate("run/${item.id}")
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = if (run.name.isNotBlank()) run.name else "Pickup run",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Starts: $timeText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Players: $players / ${run.maxPlayers}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Mode: ${run.mode} • Status: ${run.status}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
