package com.nicklewis.ballup.util

data class RowRun(
    val id: String,
    val name: String?,
    val startsAt: com.google.firebase.Timestamp?,
    val endsAt: com.google.firebase.Timestamp?,
    val playerCount: Int,
    val maxPlayers: Int,
    val playerIds: List<String>?,
    val hostId: String? = null
)
data class CourtRow(
    val courtId: String,
    val court: com.nicklewis.ballup.model.Court,
    val runsForCard: List<RowRun>,   // <= NEW: top N runs for the card
    val moreRunsCount: Int           // <= NEW: how many not shown
)