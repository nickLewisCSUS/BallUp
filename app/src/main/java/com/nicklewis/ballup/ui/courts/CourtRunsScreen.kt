package com.nicklewis.ballup.ui.courts

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.nicklewis.ballup.model.Run
import com.nicklewis.ballup.util.RowRun
import com.nicklewis.ballup.nav.AppNavControllerHolder
import com.nicklewis.ballup.firebase.joinRun
import com.nicklewis.ballup.firebase.leaveRun
import kotlinx.coroutines.launch
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourtRunsScreen(courtId: String) {
    val db = remember { FirebaseFirestore.getInstance() }
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf<List<RowRun>>(emptyList()) }

    // live query of ALL upcoming active runs for this court
    LaunchedEffect(courtId) {
        db.collection("runs")
            .whereEqualTo("courtId", courtId)
            .whereEqualTo("status", "active")
            .orderBy("startsAt")
            .addSnapshotListener { snap, _ ->
                items = snap?.documents?.mapNotNull { d ->
                    val r = d.toObject(Run::class.java) ?: return@mapNotNull null
                    val now = Instant.now().toEpochMilli()
                    val start = r.startsAt?.toDate()?.time ?: Long.MAX_VALUE
                    val end   = r.endsAt?.toDate()?.time ?: start
                    if (now <= end) RowRun(
                        id = d.id,
                        name = r.name,
                        startsAt = r.startsAt,
                        endsAt = r.endsAt,
                        playerCount = r.playerCount,
                        maxPlayers = r.maxPlayers,
                        playerIds = r.playerIds ?: emptyList()
                    ) else null
                }.orEmpty()
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Runs at this court") },
                navigationIcon = {
                    TextButton(onClick = { AppNavControllerHolder.navController?.popBackStack() }) {
                        Text("Back")
                    }
                }
            )
        }
    ) { pads ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = pads.calculateTopPadding() + 8.dp, bottom = 16.dp
            ),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 0.dp)
        ) {
            items(items, key = { it.id }) { rr ->
                com.nicklewis.ballup.ui.courts.components.RunRow(
                    rr = rr,
                    currentUid = uid,
                    onView  = { AppNavControllerHolder.navController?.navigate("run/${rr.id}") },
                    onJoin  = { if (uid != null) scope.launch { joinRun(db, rr.id, uid) } },
                    onLeave = { if (uid != null) scope.launch { leaveRun(db, rr.id, uid) } }
                )
            }
        }
    }
}
