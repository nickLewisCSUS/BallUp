@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.nicklewis.ballup

import android.os.Bundle
import android.util.Log
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
import com.nicklewis.ballup.nav.BallUpApp
import androidx.compose.runtime.saveable.rememberSaveable

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.location.LocationServices


// Maps
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth

data class Court(
    var name: String? = null,
    var type: String? = null,
    var address: String? = null,
    var geo: Geo? = null,
    var amenities: Amenities? = null,
    var createdAt: com.google.firebase.Timestamp? = null,
    var createdBy: String? = null
)
data class Geo(var lat: Double? = null, var lng: Double? = null)
data class Amenities(var lights: Boolean? = null, var restrooms: Boolean? = null)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Sign in (once) with anonymous auth so Firestore can fetch a token
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnSuccessListener { Log.d("AUTH", "anon ok: ${it.user?.uid}") }
                .addOnFailureListener { e -> Log.e("AUTH", "anon fail", e) }
        }

        setContent {
            BallUpApp()
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

    // No inner Scaffold or TopAppBar — the app shell provides those.
    Column(Modifier.fillMaxSize().padding(16.dp)) {
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

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(courts) { (id, court) ->
                ElevatedCard {
                    Column(Modifier.padding(12.dp)) {
                        Text(court.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
                        Text("${court.type?.uppercase().orEmpty()} • ${court.address.orEmpty()}")
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

    var savedCenter by rememberSaveable { mutableStateOf<LatLng?>(null) }
    var savedZoom   by rememberSaveable { mutableStateOf<Float?>(null) }
    var userMoved   by rememberSaveable { mutableStateOf(false) }
    var didAutoFit  by rememberSaveable { mutableStateOf(false) }

    // ---- Firestore: load courts ----
    LaunchedEffect(Unit) {
        db.collection("courts")
            .orderBy("name", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, e ->
                if (e != null) { error = e.message; return@addSnapshotListener }
                courts = snap?.documents?.map { d -> d.id to (d.toObject<Court>() ?: Court()) }.orEmpty()
            }
    }

    // ---- Map state ----
    val context = LocalContext.current
    val mapView = rememberMapViewWithLifecycle()
    var gmap by remember { mutableStateOf<com.google.android.gms.maps.GoogleMap?>(null) }
    val fused = remember { LocationServices.getFusedLocationProviderClient(context) }

    // ---- Permission launcher ----
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted =
            (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
                    (grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true)

        if (granted) {
            gmap?.let { map ->
                enableMyLocation(map, context)
                centerOnLastKnown(map, fused, context)
            }
        }
    }

    // Ask for permission the first time we show this screen (if needed)
    LaunchedEffect(Unit) {
        if (!hasLocationPermission(context)) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // ---- Map view ----
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            MapsInitializer.initialize(it)
            mapView.getMapAsync { m ->
                m.uiSettings.isZoomControlsEnabled = true
                gmap = m

                // Restore camera if we saved it
                if (savedCenter != null && savedZoom != null) {
                    m.moveCamera(CameraUpdateFactory.newLatLngZoom(savedCenter!!, savedZoom!!))
                }

                // Track user-initiated moves (so we don’t auto-fit again)
                m.setOnCameraMoveStartedListener { reason ->
                    if (reason == com.google.android.gms.maps.GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                        userMoved = true
                    }
                }
                m.setOnCameraIdleListener {
                    val pos = m.cameraPosition
                    savedCenter = pos.target
                    savedZoom = pos.zoom
                }

                if (hasLocationPermission(context)) {
                    enableMyLocation(m, context)
                    centerOnLastKnown(m, fused, context) // this does nothing if we already restored above
                }
            }
            mapView
        }
    )

    // ---- Markers + camera fit whenever data/map changes ----
    LaunchedEffect(gmap, courts) {
        val map = gmap ?: return@LaunchedEffect
        map.clear()

        val points = mutableListOf<LatLng>()
        courts.forEach { (_, c) ->
            val lat = c.geo?.lat
            val lng = c.geo?.lng
            if (lat != null && lng != null) {
                val p = LatLng(lat, lng)
                map.addMarker(
                    MarkerOptions()
                        .position(p)
                        .title(c.name.orEmpty())
                        .snippet("${c.type.orEmpty()} • ${c.address.orEmpty()}")
                )
                points += p
            }
        }

        // If we have a saved camera (user has seen/used the map before), honor it and bail.
        if (savedCenter != null && savedZoom != null) return@LaunchedEffect

        // Otherwise, first-time behavior:
        if (points.isEmpty()) {
            val sac = LatLng(38.5816, -121.4944)
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(sac, 12f))
            map.addMarker(MarkerOptions().position(sac).title("Sacramento"))
            return@LaunchedEffect
        }

        // Only auto-fit once (unless user never touched the map)
        if (!didAutoFit && !userMoved) {
            map.setOnMapLoadedCallback {
                if (points.size == 1) {
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(points.first(), 15f))
                } else {
                    val b = com.google.android.gms.maps.model.LatLngBounds.Builder()
                    points.forEach(b::include)
                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), 150))
                }
                didAutoFit = true
            }
        }
    }

    if (error != null) {
        Text("Map error: $error", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
    }
}


