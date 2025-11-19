package com.nicklewis.ballup.model

import com.google.firebase.Timestamp

data class UserProfile(
    val uid: String = "",
    val displayName: String? = null,
    val username: String = "",
    val skillLevel: String = "Beginner",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)