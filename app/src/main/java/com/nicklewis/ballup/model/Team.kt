package com.nicklewis.ballup.model

import com.google.firebase.Timestamp

data class Team(
    val id: String = "",
    val name: String = "",
    val ownerUid: String = "",
    val memberUids: List<String> = emptyList(),
    val createdAt: Timestamp? = null,
    val preferredSkillLevel: String? = null,   // e.g. "Any", "Casual", etc.
    val playDays: List<String> = emptyList(),  // e.g. ["Mon", "Wed", "Sat"]
    val inviteOnly: Boolean = false            // true = host invites only
)
