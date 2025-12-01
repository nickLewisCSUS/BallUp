package com.nicklewis.ballup.model

import com.google.firebase.Timestamp

data class Team(
    val id: String = "",
    val name: String = "",
    val ownerUid: String = "",
    val memberUids: List<String> = emptyList(),
    val createdAt: Timestamp? = null
)
