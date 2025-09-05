package com.nicklewis.ballup.util

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class SortMode { CLOSEST, MOST_PLAYERS, NEWEST }

private const val EARTH_RADIUS_KM = 6371.0

private fun Double.toRadians() = this * PI / 180.0

fun distanceKm(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
    val dLat = (bLat - aLat).toRadians()
    val dLng = (bLng - aLng).toRadians()
    val aa = sin(dLat/2) * sin(dLat/2) +
            cos(aLat.toRadians()) * cos(bLat.toRadians()) *
            sin(dLng/2) * sin(dLng/2)
    return 2 * EARTH_RADIUS_KM * atan2(sqrt(aa), sqrt(1 - aa))
}

fun kmToMiles(km: Double) = km * 0.621371