@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.nicklewis.ballup.ui.map

import android.Manifest
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObject
import com.nicklewis.ballup.map.MapCameraVM
import com.nicklewis.ballup.model.Court
import com.nicklewis.ballup.model.Run
import com.nicklewis.ballup.util.centerOnLastKnown
import com.nicklewis.ballup.util.enableMyLocation
import com.nicklewis.ballup.util.hasLocationPermission
import com.nicklewis.ballup.util.openDirections
import com.nicklewis.ballup.firebase.joinRun
import com.nicklewis.ballup.firebase.leaveRun
import com.nicklewis.ballup.firebase.updateMaxPlayers
import com.nicklewis.ballup.firebase.updateMode
import com.nicklewis.ballup.firebase.endRun
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
@Composable
fun CourtsMapScreen(
    showIndoor: Boolean,
    showOutdoor: Boolean,
    onToggleIndoor: () -> Unit,
    onToggleOutdoor: () -> Unit,
    onOpenRunDetails: (runId: String) -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    var courts by remember { mutableStateOf(listOf<Pair<String, Court>>()) }
    var runs by remember { mutableStateOf(listOf<Pair<String, Run>>()) }
    var error by remember { mutableStateOf<String?>(null) }

    val cam: MapCameraVM = viewModel()
    var savedCenter by cam.center
    var savedZoom by cam.zoom
    var userMoved by rememberSaveable { mutableStateOf(false) }
    var didAutoFit by rememberSaveable { mutableStateOf(false) }

    // Firestore listeners
    LaunchedEffect(Unit) {
        db.collection("courts")
            .orderBy("name", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, e ->
                if (e != null) { error = e.message; return@addSnapshotListener }
                courts = snap?.documents
                    ?.map { d -> d.id to (d.toObject<Court>() ?: Court()) }
                    .orEmpty()
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

    // Map state
    var selected by remember { mutableStateOf<Pair<String, Court>?>(null) }
    val context = LocalContext.current
    val mapView = rememberMapViewWithLifecycle()
    var gmap by remember { mutableStateOf<com.google.android.gms.maps.GoogleMap?>(null) }
    val fused = remember { LocationServices.getFusedLocationProviderClient(context) }

    // Permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            gmap?.let { map ->
                enableMyLocation(map, context)
                if (savedCenter == null || savedZoom == null) {
                    centerOnLastKnown(map, fused, context)
                }
            }
        }
    }
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
            modifier = Modifier.fillMaxSize(),
            factory = {
                MapsInitializer.initialize(it)
                mapView.getMapAsync { m ->
                    m.uiSettings.isZoomControlsEnabled = true
                    gmap = m

                    m.setOnMarkerClickListener { marker ->
                        (marker.tag as? Pair<String, Court>)?.let { selected = it }
                        marker.showInfoWindow()
                        true
                    }

                    if (hasLocationPermission(context)) {
                        enableMyLocation(m, context)
                    }
                    if (savedCenter != null && savedZoom != null) {
                        m.moveCamera(CameraUpdateFactory.newLatLngZoom(savedCenter!!, savedZoom!!))
                    } else if (hasLocationPermission(context)) {
                        centerOnLastKnown(m, fused, context)
                    }

                    m.setOnCameraMoveStartedListener { reason ->
                        if (reason ==
                            com.google.android.gms.maps.GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                            userMoved = true
                        }
                    }
                    m.setOnCameraIdleListener {
                        val pos = m.cameraPosition
                        savedCenter = pos.target
                        savedZoom = pos.zoom
                    }
                }
                mapView
            }
        )

        FloatingActionButton(
            onClick = { gmap?.let { map -> centerOnLastKnown(map, fused, context) } },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) { Icon(Icons.Default.LocationSearching, contentDescription = "My location") }

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

    // Markers + fit
    LaunchedEffect(gmap, courts, runs, showIndoor, showOutdoor) {
        val map = gmap ?: return@LaunchedEffect
        map.clear()

        val filtered = courts.filter { (_, c) ->
            when (c.type?.trim()?.lowercase()) {
                "indoor" -> showIndoor
                "outdoor" -> showOutdoor
                else -> false
            }
        }

        val points = mutableListOf<LatLng>()
        filtered.forEach { (id, c) ->
            val lat = c.geo?.lat; val lng = c.geo?.lng
            if (lat != null && lng != null) {
                val p = LatLng(lat, lng)
                val hasActive = runs.any { it.second.courtId == id && it.second.status == "active" }

                val opts = MarkerOptions()
                    .position(p)
                    .title(c.name.orEmpty())
                    .snippet("${c.type.orEmpty()} • ${c.address.orEmpty()}")

                if (hasActive) {
                    opts.icon(
                        com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(
                            com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_GREEN
                        )
                    )
                }

                val mk = map.addMarker(opts)
                mk?.tag = id to c
                points += p
            }
        }

        if (savedCenter != null && savedZoom != null) return@LaunchedEffect

        if (points.isEmpty()) {
            val sac = LatLng(38.5816, -121.4944)
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(sac, 12f))
            map.addMarker(MarkerOptions().position(sac).title("Sacramento"))
            return@LaunchedEffect
        }

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

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val scope = rememberCoroutineScope()

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
                                "playerIds" to listOfNotNull(hostId)
                            )
                            db.collection("runs").add(run)
                                .addOnSuccessListener { selected = null }
                        }) { Text("Start run here") }

                        val lat = court.geo?.lat; val lng = court.geo?.lng
                        val context = LocalContext.current  // <-- read in composable scope

                        OutlinedButton(
                            enabled = lat != null && lng != null,
                            onClick = {
                                if (lat != null && lng != null) {
                                    openDirections(context, lat, lng, court.name)
                                }
                            }
                        ) { Text("Directions") }
                    }
                } else {
                    Text(
                        "Pickup running: ${currentRun.playerCount}/${currentRun.maxPlayers}",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    AttendeeChips(
                        currentRun = currentRun,
                        isHost = (uid != null && currentRun.hostId == uid),
                        runId = runId,
                        uid = uid
                    )

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

                        val lat = court.geo?.lat
                        val lng = court.geo?.lng

                        OutlinedButton(
                            enabled = lat != null && lng != null,
                            onClick = {
                                if (lat != null && lng != null) {
                                    openDirections(context, lat, lng, court.name)
                                }
                            }
                        ) { Text("Directions") }
                    }

                    Spacer(Modifier.height(12.dp))

                    // NEW: same RunDetails experience as from CourtList
                    Button(
                        onClick = { if (runId != null) onOpenRunDetails(runId) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("View run details")
                    }

                    Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
    error?.let {
        Text(
            "Map error: $it",
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(12.dp)
        )
    }
}
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
private fun AttendeeChips(
    currentRun: Run,
    isHost: Boolean,
    runId: String?,
    uid: String?
) {
    val scope = rememberCoroutineScope()
    val db = FirebaseFirestore.getInstance()

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        currentRun.playerIds.orEmpty().forEach { pid ->
            AssistChip(
                onClick = { },
                label = {
                    val tag = if (pid == currentRun.hostId) "Host • " else ""
                    Text(tag + pid.takeLast(6))
                },
                trailingIcon = if (isHost && pid != currentRun.hostId) {
                    {
                        androidx.compose.material3.IconButton(
                            onClick = {
                                if (runId != null && uid != null) {
                                    scope.launch {
                                        try {
                                            com.nicklewis.ballup.firebase.kickPlayer(db, runId, uid, pid)
                                        } catch (e: Exception) { Log.e("RUN", "kick", e) }
                                    }
                                }
                            }
                        ) { Icon(Icons.Default.Close, contentDescription = "Kick") }
                    }
                } else null
            )
        }
    }

    Spacer(Modifier.height(8.dp))
}
