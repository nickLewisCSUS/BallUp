package com.nicklewis.ballup.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class UserProfile(
    // This will be filled with the Firestore document ID (users/{id})
    @DocumentId val id: String = "",

    // This comes from the actual "uid" field in the document (you already store this)
    val uid: String = "",

    val displayName: String? = null,
    val username: String = "",
    val photoUrl: String? = null,

    val heightBracket: String? = null,
    val playStyle: String? = null,
    val skillLevel: String = "Beginner",

    val favoriteCourtIds: List<String> = emptyList(),
    val favoriteCourts: List<String> = emptyList(),

    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)
