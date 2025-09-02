package com.nicklewis.ballup

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import android.content.Intent
import android.net.Uri

private val DEFAULT_LATLNG = LatLng(38.5816, -121.4944) // Sacramento

@SuppressLint("MissingPermission")
fun centerOnLastKnown(map: GoogleMap, fused: FusedLocationProviderClient, ctx: Context) {
    if (!hasLocationPermission(ctx)) return

    fused.lastLocation.addOnSuccessListener { loc ->
        if (loc != null) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 13f))
        } else {
            // Try a one-shot current location (emulators often need this)
            val cts = CancellationTokenSource()
            val req = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .setMaxUpdateAgeMillis(0)
                .build()

            fused.getCurrentLocation(req, cts.token)
                .addOnSuccessListener { cur ->
                    if (cur != null) {
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(cur.latitude, cur.longitude), 13f))
                    } else {
                        // Final fallback so the map isn’t empty
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_LATLNG, 12f))
                    }
                }
                .addOnFailureListener {
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_LATLNG, 12f))
                }
        }
    }.addOnFailureListener {
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_LATLNG, 12f))
    }
}
@SuppressLint("MissingPermission")
fun hasLocationPermission(ctx: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(
        ctx, android.Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(
        ctx, android.Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

@SuppressLint("MissingPermission")
fun enableMyLocation(map: GoogleMap, ctx: Context) {
    if (!hasLocationPermission(ctx)) return
    try {
        map.isMyLocationEnabled = true
        map.uiSettings.isMyLocationButtonEnabled = true
    } catch (_: SecurityException) {
        // no-op: permission could still be revoked between check and call
    }
}

fun openDirections(ctx: Context, lat: Double, lng: Double, label: String?) {
    // Works with Google Maps or any maps app
    val uri = Uri.parse("geo:$lat,$lng?q=${lat},${lng}(${Uri.encode(label ?: "Court")})")
    ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
}

