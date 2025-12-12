package com.nicklewis.ballup.ui.courts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObject
import com.nicklewis.ballup.model.Court
import com.nicklewis.ballup.model.Run
import com.nicklewis.ballup.util.CourtRow
import com.nicklewis.ballup.util.SortMode
import com.nicklewis.ballup.util.buildSortedCourtRows

class CourtsListViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()

    // Raw streams
    var courts by mutableStateOf(listOf<Pair<String, Court>>()); private set
    var runs by mutableStateOf(listOf<Pair<String, Run>>()); private set
    var error by mutableStateOf<String?>(null); private set

    // UI state
    var query by mutableStateOf("")
    var searchActive by mutableStateOf(false)
    var sortMode by mutableStateOf(SortMode.CLOSEST)
    var showIndoor by mutableStateOf(true)
    var showOutdoor by mutableStateOf(true)
    var userLoc by mutableStateOf<LatLng?>(null)

    init {
        db.collection("courts")
            .orderBy("name", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, e ->
                if (e != null) { error = e.message; return@addSnapshotListener }
                courts = snap?.documents
                    ?.map { it.id to (it.toObject<Court>() ?: Court()) }
                    .orEmpty()
            }

        // ✅ show ACTIVE + SCHEDULED runs in the court cards
        db.collection("runs")
            .whereIn("status", listOf("active", "scheduled"))
            .addSnapshotListener { snap, e ->
                if (e != null) { error = e.message; return@addSnapshotListener }
                runs = snap?.documents
                    ?.map { it.id to (it.toObject(Run::class.java) ?: Run()) }
                    .orEmpty()
            }
    }

    val filtered: List<Pair<String, Court>>
        get() = courts.filter { (_, c) ->
            when (c.type?.trim()?.lowercase()) {
                "indoor"  -> showIndoor
                "outdoor" -> showOutdoor
                else      -> false
            }
        }

    val filteredByQuery: List<Pair<String, Court>>
        get() = if (query.isBlank()) filtered else {
            val t = query.trim().lowercase()
            filtered.filter { (_, c) ->
                c.name.orEmpty().lowercase().contains(t) ||
                        c.address.orEmpty().lowercase().contains(t) ||
                        c.type.orEmpty().lowercase().contains(t)
            }
        }

    val rows: List<CourtRow>
        get() = buildSortedCourtRows(filteredByQuery, runs, sortMode, userLoc)

    val suggestions: List<String>
        get() = if (query.isBlank()) emptyList() else
            filtered.map { it.second.name.orEmpty() }
                .filter { it.contains(query, ignoreCase = true) }
                .distinct()
                .take(5)
}
