package com.nicklewis.ballup.util

import com.google.android.gms.maps.model.LatLng
import com.nicklewis.ballup.model.Court
import com.nicklewis.ballup.model.Run
import java.time.Instant
import java.time.temporal.ChronoUnit

// ----- helpers (file-private) -----

private fun maxConcurrentFor(court: Court): Int {
    val surfaces = (court.surfaces ?: 1).coerceAtLeast(1)
    return when {
        surfaces <= 1 -> 1
        surfaces <= 3 -> 2
        else -> 3
    }
}

private fun scoreForListing(nowMs: Long, r: Run): Long {
    val s = r.startsAt?.toDate()?.time ?: Long.MAX_VALUE
    val e = r.endsAt?.toDate()?.time ?: s
    // Lower is better: live now first, then starts soonest
    return when {
        nowMs in s..e -> 0L
        s >= nowMs    -> (s - nowMs)
        else          -> Long.MAX_VALUE / 2
    }
}

// 🔁 UPDATED: include hostId + hostUid in RowRun
private fun toRowRun(id: String, r: Run) = RowRun(
    id = id,
    name = r.name,
    startsAt = r.startsAt,
    endsAt = r.endsAt,
    playerCount = r.playerCount,
    maxPlayers = r.maxPlayers,
    playerIds = r.playerIds ?: emptyList(),
    hostId = r.hostId,
    hostUid = r.hostId   // Firestore uses hostId as the UID, so we mirror it here
)

// ----- single public API used by CourtsListViewModel -----

fun buildSortedCourtRows(
    filtered: List<Pair<String, Court>>,
    runs: List<Pair<String, Run>>,
    sortMode: SortMode,
    userLoc: LatLng?
): List<CourtRow> {

    val nowMs = System.currentTimeMillis()
    val soonCutoff = Instant.ofEpochMilli(nowMs).plus(6, ChronoUnit.HOURS).toEpochMilli()

    // group active runs by courtId
    val runsByCourt: Map<String, List<Pair<String, Run>>> =
        runs.asSequence()
            .filter { it.second.status == "active" }
            .filter { (_, r) -> r.courtId != null }
            .groupBy { it.second.courtId!! }

    val rows: List<CourtRow> = filtered.map { (courtId, court) ->
        val maxN = maxConcurrentFor(court)
        val courtRuns = runsByCourt[courtId]
            .orEmpty()
            // keep live now or starting within next 6h
            .filter { (_, r) ->
                val s = r.startsAt?.toDate()?.time
                val e = r.endsAt?.toDate()?.time ?: s
                if (s == null) {
                    false
                } else {
                    val end = e ?: s
                    (nowMs in s..end) || (s in nowMs..soonCutoff)
                }
            }
            .sortedBy { (_, r) -> scoreForListing(nowMs, r) }

        val top = courtRuns.take(maxN).map { (id, r) -> toRowRun(id, r) }
        val more = (courtRuns.size - top.size).coerceAtLeast(0)

        CourtRow(
            courtId = courtId,
            court = court,
            runsForCard = top,
            moreRunsCount = more
        )
    }

    // final sorting (same modes you had before)
    return when (sortMode) {
        SortMode.CLOSEST -> rows.sortedBy { row ->
            val lat = row.court.geo?.lat
            val lng = row.court.geo?.lng
            if (lat != null && lng != null && userLoc != null)
                distanceKm(userLoc.latitude, userLoc.longitude, lat, lng)
            else Double.POSITIVE_INFINITY
        }
        SortMode.MOST_PLAYERS -> rows.sortedByDescending { row ->
            row.runsForCard.maxOfOrNull { it.playerCount } ?: 0
        }
        SortMode.NEWEST -> rows.sortedByDescending { row ->
            row.runsForCard.maxOfOrNull { it.startsAt?.toDate()?.time ?: 0L } ?: 0L
        }
    }
}
