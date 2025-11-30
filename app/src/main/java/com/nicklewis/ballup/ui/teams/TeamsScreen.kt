package com.nicklewis.ballup.ui.teams

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Temporary local model; later we’ll move to model/Team.kt and Firestore.
data class LocalTeam(
    val id: String,
    val name: String,
    val memberCount: Int
)

@Composable
fun TeamsScreen(
    onBackToMap: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val teams = remember {
        mutableStateListOf(
            // sample data – remove once wired to Firestore
            LocalTeam("1", "Lincoln Hoopers", 5),
            LocalTeam("2", "McBean Squad", 3)
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    // TODO: open "Create Squad" dialog
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create squad")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.Top
        ) {
            // Quick shortcuts row (optional)
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Your Squads",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Create a squad and later invite them into your runs.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Simple actions row
                Button(onClick = onBackToMap) {
                    Text("Back to map")
                }
                Spacer(modifier = Modifier.height(4.dp))
                Button(onClick = onOpenSettings) {
                    Text("Open settings")
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                items(teams) { team ->
                    SquadRow(team = team)
                }
            }
        }
    }
}

@Composable
private fun SquadRow(team: LocalTeam) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp)
    ) {
        Text(text = team.name, style = MaterialTheme.typography.titleMedium)
        Text(
            text = "${team.memberCount} players",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Later: “Invite squad to run”, “View members”, “Edit squad”
        Button(onClick = { /* TODO: Invite this squad to a run */ }) {
            Text("Invite this squad to a run")
        }
    }
}
