package com.nicklewis.ballup.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class Team(
    @DocumentId val id: String = "",
    val name: String = "",
    val ownerUid: String = "",
    val memberUids: List<String> = emptyList(),
    val createdAt: Timestamp? = null
)