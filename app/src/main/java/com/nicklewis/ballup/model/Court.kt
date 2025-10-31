package com.nicklewis.ballup.model

import com.google.firebase.Timestamp

data class Court(
    var name: String? = null,
    var type: String? = null,          // "indoor" | "outdoor"
    var address: String? = null,
    var geo: Geo? = null,
    var surfaces: Int? = null,
    var amenities: Amenities? = null,
    var createdAt: Timestamp? = null,
    var createdBy: String? = null
)

data class Geo(
    var lat: Double? = null,
    var lng: Double? = null
)

data class Amenities(
    var lights: Boolean? = null,
    var restrooms: Boolean? = null
)
