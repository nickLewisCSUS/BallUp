@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.nicklewis.ballup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObject

// Maps
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

data class Court(
    val name: String = "",
    val type: String = "",
    val address: String = "",
    val geo: Geo? = null,
    val amenities: Amenities? = null,
    val createdAt: com.google.firebase.Timestamp? = null,
    val createdBy: String? = null
)

data class Geo(
    val lat: Double? = null,
    val lng: Double? = null
)

data class Amenities(
    val lights: Boolean? = null,
    val restrooms: Boolean? = null
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // TEMP: show the map screen
            CourtsMapScreen()
            // To go back to the list: CourtsScreen()
        }
    }
}

/* --------------------  LIST SCREEN (unchanged) -------------------- */

@Composable
fun CourtsScreen() {
    val db = remember { FirebaseFirestore.getInstance() }
    var courts by remember { mutableStateOf(listOf<Pair<String, Court>>()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        db.collection("courts")
            .orderBy("name", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, e ->
                if (e != null) { error = e.message; return@addSnapshotListener }
                courts = snap?.documents?.map { doc ->
                    doc.id to (doc.toObject<Court>() ?: Court())
                }.orEmpty()
            }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("BallUp — Courts") }) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            if (error != null) Text("Error: $error", color = MaterialTheme.colorScheme.error)
            if (courts.isEmpty()) Text("No courts yet. Add one in Firestore to see it here.")

            Button(
                onClick = {
                    val courtId = courts.firstOrNull()?.first ?: return@Button
                    val run = mapOf(
                        "courtId" to courtId,
                        "status" to "active",
                        "startTime" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                        "hostId" to "uid_dev",
                        "mode" to "5v5",
                        "maxPlayers" to 10,
                        "lastHeartbeatAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                        "playerCount" to 1
                    )
                    db.collection("runs").add(run)
                },
                modifier = Modifier.padding(bottom = 12.dp)
            ) { Text("Start a Test Run at First Court") }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(courts) { (id, court) ->
                    ElevatedCard {
                        Column(Modifier.padding(12.dp)) {
                            Text(court.name, style = MaterialTheme.typography.titleMedium)
                            Text("${court.type.uppercase()} • ${court.address}")
                            val lat = court.geo?.lat
                            val lng = court.geo?.lng
                            if (lat != null && lng != null) {
                                Text("($lat, $lng)", style = MaterialTheme.typography.bodySmall)
                            }
                            Text("id: $id", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

/* --------------------  MAP SCREEN  -------------------- */

// MapView that follows the Compose lifecycle
@Composable
private fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return mapView
}

@Composable
fun CourtsMapScreen() {
    val db = remember { FirebaseFirestore.getInstance() }
    var courts by remember { mutableStateOf(listOf<Pair<String, Court>>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var cameraMoved by remember { mutableStateOf(false) } // move only once

    LaunchedEffect(Unit) {
        db.collection("courts")
            .orderBy("name", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, e ->
                if (e != null) { error = e.message; return@addSnapshotListener }
                courts = snap?.documents?.map { d -> d.id to (d.toObject<Court>() ?: Court()) }.orEmpty()
            }
    }

    val mapView = rememberMapViewWithLifecycle()
    val context = LocalContext.current

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            MapsInitializer.initialize(context)
            mapView.getMapAsync { googleMap ->
                googleMap.uiSettings.isZoomControlsEnabled = true

                // Hard-coded fallback so you instantly see the map
                val sac = LatLng(38.5816, -121.4944)
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(sac, 12f))
                googleMap.addMarker(MarkerOptions().position(sac).title("Sacramento"))
            }
            mapView
        },
        update = { view ->
            view.getMapAsync { googleMap ->
                googleMap.clear()
                // re-add the fallback marker (keeps showing even if courts are empty)
                val sac = LatLng(38.5816, -121.4944)
                googleMap.addMarker(MarkerOptions().position(sac).title("Sacramento"))

                // then draw your Firestore courts
                courts.forEach { (_, court) ->
                    val lat = court.geo?.lat
                    val lng = court.geo?.lng
                    if (lat != null && lng != null) {
                        googleMap.addMarker(
                            MarkerOptions()
                                .position(LatLng(lat, lng))
                                .title(court.name)
                                .snippet("${court.type} • ${court.address}")
                        )
                    }
                }
            }
        }
    )

    if (error != null) {
        Text("Map error: $error", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
    }
}
