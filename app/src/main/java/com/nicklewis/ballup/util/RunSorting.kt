package com.nicklewis.ballup.util

import com.google.android.gms.maps.model.LatLng
import com.nicklewis.ballup.Court
import com.nicklewis.ballup.model.Run

data class CourtRow(
    val courtId: String,
    val court: Court,
    val active: Pair<String, Run>? // (runId, Run)
)

fun buildSortedCourtRows(
    filtered: List<Pair<String, Court>>,
    runs: List<Pair<String, Run>>,
    sortMode: SortMode,
    userLoc: LatLng?
): List<CourtRow> {
    // courtId -> (runId, Run)
    val activeByCourtId: Map<String, Pair<String, Run>> =
        runs.asSequence()
            .filter { it.second.status == "active" }
            .mapNotNull { pair ->
                val courtId = pair.second.courtId
                if (courtId != null) courtId to pair else null
            }
            .toMap()

    val rows = filtered.map { (courtId, court) ->
        CourtRow(courtId, court, activeByCourtId[courtId])
    }

    return when (sortMode) {
        SortMode.CLOSEST -> rows.sortedBy { row ->
            val lat = row.court.geo?.lat; val lng = row.court.geo?.lng
            if (lat != null && lng != null && userLoc != null)
                distanceKm(userLoc.latitude, userLoc.longitude, lat, lng)
            else Double.POSITIVE_INFINITY
        }
        SortMode.MOST_PLAYERS -> rows.sortedByDescending { row ->
            row.active?.second?.playerCount ?: 0
        }
        SortMode.NEWEST -> rows.sortedByDescending { row ->
            row.active?.second?.startTime?.toDate()?.time ?: 0L
        }
    }
}