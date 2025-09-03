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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material3.FilterChip
import androidx.compose.ui.Alignment
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.nicklewis.ballup.firestore.joinRun
import com.nicklewis.ballup.firestore.leaveRun
import com.nicklewis.ballup.model.Run
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

/* -------------------- LIST SCREEN -------------------- */
@Composable
fun CourtsScreen(
    showIndoor: Boolean,
    showOutdoor: Boolean,
    onToggleIndoor: () -> Unit,
    onToggleOutdoor: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    var courts by remember { mutableStateOf(listOf<Pair<String, Court>>()) }
    var error by remember { mutableStateOf<String?>(null) }

    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val scope = rememberCoroutineScope()

    var runs by remember { mutableStateOf(listOf<Pair<String, Run>>()) }

    LaunchedEffect(Unit) {
        db.collection("courts")
            .orderBy("name", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, e ->
                if (e != null) { error = e.message; return@addSnapshotListener }
                courts = snap?.documents?.map { d -> d.id to (d.toObject<Court>() ?: Court()) }.orEmpty()
            }
    }

    LaunchedEffect(Unit) {
        db.collection("runs")
            .whereEqualTo("status", "active")
            .addSnapshotListener { snap, e ->
                if (e != null) { error = e.message; return@addSnapshotListener }
                runs = snap?.documents
                    ?.map { d -> d.id to (d.toObject(Run::class.java) ?: Run()) }
                    .orEmpty()
            }
    }

    val filtered = courts.filter { (_, c) ->
        when (c.type?.trim()?.lowercase()) {
            "indoor"  -> showIndoor
            "outdoor" -> showOutdoor
            else      -> false
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Surface(
            tonalElevation = 2.dp,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = showIndoor,  onClick = onToggleIndoor,  label = { Text("Indoor") })
                FilterChip(selected = showOutdoor, onClick = onToggleOutdoor, label = { Text("Outdoor") })
            }
        }

        if (error != null) Text("Error: $error", color = MaterialTheme.colorScheme.error)
        if (courts.isEmpty()) Text("No courts yet. Add one in Firestore to see it here.")

        if (filtered.isEmpty()) {
            Text("No courts match your filter.")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered) { (id, court) ->
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

                            // --- Active run info + actions ---
                            val currentRunPair = runs.firstOrNull { it.second.courtId == id && it.second.status == "active" }
                            val runId = currentRunPair?.first
                            val currentRun = currentRunPair?.second

                            Spacer(Modifier.height(8.dp))

                            if (currentRun == null) {
                                // No run here yet — let user host one from the list
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Button(onClick = {
                                        val hostId = uid ?: "uid_dev"
                                        val run = mapOf(
                                            "courtId" to id,
                                            "status" to "active",
                                            "startTime" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                                            "hostId" to hostId,
                                            "mode" to "5v5",
                                            "maxPlayers" to 10,
                                            "lastHeartbeatAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                                            "playerCount" to 1,
                                            "playerIds" to listOfNotNull(hostId)
                                        )
                                        db.collection("runs").add(run)
                                    }) { Text("Start run") }
                                }
                            } else {
                                // Run exists — show count and join/leave
                                Text(
                                    "Pickup running: ${currentRun.playerCount}/${currentRun.maxPlayers}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.height(6.dp))

                                val alreadyIn = uid != null && (currentRun.playerIds?.contains(uid) == true)

                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    if (!alreadyIn) {
                                        Button(onClick = {
                                            if (uid != null && runId != null) {
                                                scope.launch {
                                                    try { joinRun(db, runId, uid) }
                                                    catch (e: Exception) { Log.e("JOIN", "Failed", e) }
                                                }
                                            }
                                        }) { Text("Join") }
                                    } else {
                                        OutlinedButton(onClick = {
                                            if (uid != null && runId != null) {
                                                scope.launch {
                                                    try { leaveRun(db, runId, uid) }
                                                    catch (e: Exception) { Log.e("LEAVE", "Failed", e) }
                                                }
                                            }
                                        }) { Text("Leave") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


/* -------------------- MAP SCREEN -------------------- */
// MapView that follows the Compose lifecycle
@Composable
private fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE  -> mapView.onCreate(null)
                Lifecycle.Event.ON_START   -> mapView.onStart()
                Lifecycle.Event.ON_RESUME  -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE   -> mapView.onPause()
                Lifecycle.Event.ON_STOP    -> mapView.onStop()
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
fun CourtsMapScreen(
        showIndoor: Boolean,
        showOutdoor: Boolean,
        onToggleIndoor: () -> Unit,
        onToggleOutdoor: () -> Unit
    ) {
        val db = remember { FirebaseFirestore.getInstance() }
        var courts by remember { mutableStateOf(listOf<Pair<String, Court>>()) }
        var error by remember { mutableStateOf<String?>(null) }

        // camera + UX state we keep here (not hoisted)
        var savedCenter by rememberSaveable { mutableStateOf<LatLng?>(null) }
        var savedZoom by rememberSaveable { mutableStateOf<Float?>(null) }
        var userMoved by rememberSaveable { mutableStateOf(false) }
        var didAutoFit by rememberSaveable { mutableStateOf(false) }

        var runs by remember { mutableStateOf(listOf<Pair<String, Run>>()) }

        // Firestore subscription
        LaunchedEffect(Unit) {
            db.collection("courts")
                .orderBy("name", Query.Direction.ASCENDING)
                .addSnapshotListener { snap, e ->
                    if (e != null) {
                        error = e.message; return@addSnapshotListener
                    }
                    courts = snap?.documents
                        ?.map { d -> d.id to (d.toObject<Court>() ?: Court()) }
                        .orEmpty()
                }
        }

        LaunchedEffect(Unit) {
            db.collection("runs")
                .whereEqualTo("status", "active")
                .addSnapshotListener { snap, e ->
                    if (e != null) {
                        error = e.message; return@addSnapshotListener
                    }
                    runs = snap?.documents
                        ?.map { d -> d.id to (d.toObject(Run::class.java) ?: Run()) }
                        .orEmpty()
                }
        }

        // Map state
        var selected by remember { mutableStateOf<Pair<String, Court>?>(null) }
        val context = LocalContext.current
        val mapView = rememberMapViewWithLifecycle()
        var gmap by remember { mutableStateOf<com.google.android.gms.maps.GoogleMap?>(null) }
        val fused = remember { LocationServices.getFusedLocationProviderClient(context) }

        // Permission launcher
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { grants ->
            val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) {
                gmap?.let { map ->
                    enableMyLocation(map, context)
                    centerOnLastKnown(map, fused, context)
                }
            }
        }

        // Ask once if needed
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

        // UI
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.matchParentSize(),
                factory = {
                    MapsInitializer.initialize(it)
                    mapView.getMapAsync { m ->
                        m.uiSettings.isZoomControlsEnabled = true
                        gmap = m

                        m.setOnMapLoadedCallback {
                            PerfEvents.signalMapLoaded()
                        }

                        m.setOnMarkerClickListener { marker ->
                            (marker.tag as? Pair<String, Court>)?.let { selected = it }
                            marker.showInfoWindow()
                            true
                        }

                        // restore camera if we have it
                        if (savedCenter != null && savedZoom != null) {
                            m.moveCamera(
                                CameraUpdateFactory.newLatLngZoom(
                                    savedCenter!!,
                                    savedZoom!!
                                )
                            )
                        }

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
                            centerOnLastKnown(m, fused, context)
                        }
                    }
                    mapView
                }
            )

            // Recenter FAB
            FloatingActionButton(
                onClick = { gmap?.let { map -> centerOnLastKnown(map, fused, context) } },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) { Icon(Icons.Default.LocationSearching, contentDescription = "My location") }

            // Filter chips (hooked to hoisted state)
            Surface(
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(12.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = showIndoor,
                        onClick = onToggleIndoor,
                        label = { Text("Indoor") }
                    )
                    FilterChip(
                        selected = showOutdoor,
                        onClick = onToggleOutdoor,
                        label = { Text("Outdoor") }
                    )
                }
            }
        }

        // Markers + camera fit react to filters too
        LaunchedEffect(gmap, courts, showIndoor, showOutdoor) {
            val map = gmap ?: return@LaunchedEffect
            map.clear()

            val filtered = courts.filter { (_, c) ->
                when (c.type?.trim()?.lowercase()) {
                    "indoor" -> showIndoor
                    "outdoor" -> showOutdoor
                    else -> false
                }
            }

            // if the selected court is now filtered out, close the sheet
            if (selected != null && filtered.none { it.first == selected!!.first }) {
                selected = null
            }

            val points = mutableListOf<LatLng>()
            filtered.forEach { (id, c) ->
                val lat = c.geo?.lat
                val lng = c.geo?.lng
                if (lat != null && lng != null) {
                    val p = LatLng(lat, lng)
                    val mk = map.addMarker(
                        MarkerOptions()
                            .position(p)
                            .title(c.name.orEmpty())
                            .snippet("${c.type.orEmpty()} • ${c.address.orEmpty()}")
                    )
                    mk?.tag = id to c
                    points += p
                }
            }

            // honor saved camera if present
            if (savedCenter != null && savedZoom != null) return@LaunchedEffect

            // first-time behavior
            if (points.isEmpty()) {
                val sac = LatLng(38.5816, -121.4944)
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(sac, 12f))
                map.addMarker(MarkerOptions().position(sac).title("Sacramento"))
                return@LaunchedEffect
            }

            // auto-fit once (unless user already moved)
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

    // Bottom sheet for selected court
    if (selected != null) {
        val (courtId, court) = selected!!

        val db = FirebaseFirestore.getInstance()
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val scope = rememberCoroutineScope()

        // find active run at this court (if any)
        val currentRunPair = runs.firstOrNull { it.second.courtId == courtId && it.second.status == "active" }
        val runId = currentRunPair?.first
        val currentRun = currentRunPair?.second

        ModalBottomSheet(
            onDismissRequest = { selected = null },
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(court.name.orEmpty(), style = MaterialTheme.typography.titleLarge)
                Text(court.address.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                Text(
                    listOfNotNull(
                        court.type?.uppercase(),
                        if (court.amenities?.lights == true) "Lights" else null,
                        if (court.amenities?.restrooms == true) "Restrooms" else null
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall
                )

                // If no run here yet – host one
                if (currentRun == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = {
                            val hostId = uid ?: "uid_dev"
                            val run = mapOf(
                                "courtId" to courtId,
                                "status" to "active",
                                "startTime" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                                "hostId" to hostId,
                                "mode" to "5v5",
                                "maxPlayers" to 10,
                                "lastHeartbeatAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                                "playerCount" to 1,
                                "playerIds" to listOfNotNull(hostId) // <-- new field
                            )
                            db.collection("runs").add(run)
                                .addOnSuccessListener { selected = null }
                        }) { Text("Start run here") }

                        val lat = court.geo?.lat; val lng = court.geo?.lng
                        OutlinedButton(
                            enabled = lat != null && lng != null,
                            onClick = { if (lat != null && lng != null) openDirections(context, lat, lng, court.name) }
                        ) { Text("Directions") }
                    }
                } else {
                    // Run exists – show status + join/leave
                    Text("Pickup running: ${currentRun.playerCount}/${currentRun.maxPlayers}", style = MaterialTheme.typography.bodyMedium)

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val alreadyIn = uid != null && (currentRun.playerIds?.contains(uid) == true)

                        if (!alreadyIn) {
                            Button(onClick = {
                                if (uid != null && runId != null) {
                                    scope.launch {
                                        try { joinRun(db, runId, uid) }
                                        catch (e: Exception) { Log.e("JOIN", "Failed", e) }
                                    }
                                }
                            }) { Text("Join") }
                        } else {
                            OutlinedButton(onClick = {
                                if (uid != null && runId != null) {
                                    scope.launch {
                                        try { leaveRun(db, runId, uid) }
                                        catch (e: Exception) { Log.e("LEAVE", "Failed", e) }
                                    }
                                }
                            }) { Text("Leave") }
                        }

                        val lat = court.geo?.lat; val lng = court.geo?.lng
                        OutlinedButton(
                            enabled = lat != null && lng != null,
                            onClick = { if (lat != null && lng != null) openDirections(context, lat, lng, court.name) }
                        ) { Text("Directions") }
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }


    if (error != null) {
            Text(
                "Map error: $error",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(12.dp)
            )
        }
    }



