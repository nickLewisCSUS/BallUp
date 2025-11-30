@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.nicklewis.ballup.ui.map

import android.Manifest
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObject
import com.nicklewis.ballup.model.Court
import com.nicklewis.ballup.model.Run
import com.nicklewis.ballup.ui.runs.StartRunDialog
import com.nicklewis.ballup.ui.runs.RunsViewModel
import com.nicklewis.ballup.util.centerOnLastKnown
import com.nicklewis.ballup.util.enableMyLocation
import com.nicklewis.ballup.util.hasLocationPermission
import com.nicklewis.ballup.vm.StarsViewModel

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

    // Cache of uid -> username (or fallback)
    val userNames = remember { mutableStateMapOf<String, String>() }

    val cam: MapCameraVM = viewModel()
    var savedCenter by cam.center
    var savedZoom by cam.zoom
    var userMoved by rememberSaveable { mutableStateOf(false) }
    var didAutoFit by rememberSaveable { mutableStateOf(false) }

    val runsViewModel: RunsViewModel = viewModel()

    // Stars / favorites
    val starsVm: StarsViewModel = viewModel()
    val starredIds by starsVm.starred.collectAsState()
    var showStarredOnly by rememberSaveable { mutableStateOf(false) }

    // Filter dropdown state
    var filtersExpanded by remember { mutableStateOf(false) }

    // Start-run dialog state
    var showCreate by remember { mutableStateOf<String?>(null) }
    var dialogError by remember { mutableStateOf<String?>(null) }

    // ----- Firestore listeners for courts & runs -----
    LaunchedEffect(Unit) {
        db.collection("courts")
            .orderBy("name", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    error = e.message
                    return@addSnapshotListener
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
                    error = e.message
                    return@addSnapshotListener
                }
                runs = snap?.documents
                    ?.map { d -> d.id to (d.toObject(Run::class.java) ?: Run()) }
                    .orEmpty()
            }
    }

    // Whenever runs change, fetch usernames for any hostIds / playerIds we don't know yet
    LaunchedEffect(runs) {
        val ids = mutableSetOf<String>()
        runs.forEach { (_, run) ->
            run.hostId?.let(ids::add)
            run.playerIds.orEmpty().forEach(ids::add)
        }

        ids.forEach { uid ->
            if (!userNames.containsKey(uid)) {
                db.collection("users").document(uid).get()
                    .addOnSuccessListener { snap ->
                        val name = snap.getString("username")
                            ?: snap.getString("displayName")
                            ?: uid.takeLast(6)
                        userNames[uid] = name
                    }
                    .addOnFailureListener { e ->
                        Log.e("Map", "Failed to load user profile for $uid", e)
                        if (!userNames.containsKey(uid)) {
                            userNames[uid] = uid.takeLast(6)
                        }
                    }
            }
        }
    }

    // Map state
    var selected by remember { mutableStateOf<Pair<String, Court>?>(null) }
    val context = LocalContext.current
    val mapView = rememberMapViewWithLifecycle()
    var gmap by remember { mutableStateOf<com.google.android.gms.maps.GoogleMap?>(null) }
    val fused = remember { LocationServices.getFusedLocationProviderClient(context) }

    // ----- Location permissions -----
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

    // ----- Map + filters UI -----
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                MapsInitializer.initialize(it)
                mapView.getMapAsync { m ->
                    m.uiSettings.isZoomControlsEnabled = true
                    m.uiSettings.isMyLocationButtonEnabled = true
                    gmap = m

                    m.setOnMarkerClickListener { marker ->
                        (marker.tag as? Pair<String, Court>)?.let { selected = it }
                        marker.showInfoWindow()
                        true
                    }

                    if (hasLocationPermission(context)) enableMyLocation(m, context)
                    if (savedCenter != null && savedZoom != null) {
                        m.moveCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                savedCenter!!,
                                savedZoom!!
                            )
                        )
                    } else if (hasLocationPermission(context)) {
                        centerOnLastKnown(m, fused, context)
                    }

                    m.setOnCameraMoveStartedListener { reason ->
                        if (reason ==
                            com.google.android.gms.maps.GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE
                        ) userMoved = true
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

        // Compact "Filters" button (top-left) with dropdown menu
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 12.dp)
        ) {
            FilledTonalButton(
                onClick = { filtersExpanded = true },
                shape = MaterialTheme.shapes.large,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(text = "Filters")
            }

            DropdownMenu(
                expanded = filtersExpanded,
                onDismissRequest = { filtersExpanded = false }
            ) {
                // Indoor
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Checkbox(
                                checked = showIndoor,
                                onCheckedChange = null
                            )
                            Text("Indoor courts")
                        }
                    },
                    onClick = { onToggleIndoor() }
                )

                // Outdoor
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Checkbox(
                                checked = showOutdoor,
                                onCheckedChange = null
                            )
                            Text("Outdoor courts")
                        }
                    },
                    onClick = { onToggleOutdoor() }
                )

                // Starred only
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Checkbox(
                                checked = showStarredOnly,
                                onCheckedChange = null
                            )
                            Text("Starred courts only")
                        }
                    },
                    onClick = { showStarredOnly = !showStarredOnly }
                )
            }
        }
    }

    // ----- Markers + auto-fit behavior -----
    LaunchedEffect(
        gmap,
        courts,
        runs,
        showIndoor,
        showOutdoor,
        showStarredOnly,
        starredIds
    ) {
        val map = gmap ?: return@LaunchedEffect
        map.clear()

        // Filter by indoor/outdoor
        val base = courts.filter { (_, c) ->
            when (c.type?.trim()?.lowercase()) {
                "indoor" -> showIndoor
                "outdoor" -> showOutdoor
                else -> false
            }
        }

        // Optional: starred only
        val filtered = if (!showStarredOnly) {
            base
        } else {
            base.filter { (id, _) -> id in starredIds }
        }

        val nowMs = System.currentTimeMillis()
        val points = mutableListOf<LatLng>()

        filtered.forEach { (id, c) ->
            val lat = c.geo?.lat
            val lng = c.geo?.lng
            if (lat != null && lng != null) {
                val p = LatLng(lat, lng)

                val hasLiveRun = runs.any { (_, run) ->
                    if (run.courtId != id || run.status != "active") return@any false

                    val startMs = run.startsAt?.toDate()?.time ?: return@any false
                    val endMs = run.endsAt?.toDate()?.time ?: startMs

                    nowMs in startMs..endMs
                }

                val opts = MarkerOptions()
                    .position(p)
                    .title(c.name.orEmpty())
                    .snippet("${c.type.orEmpty()} • ${c.address.orEmpty()}")

                if (hasLiveRun) {
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
                if (points.size == 1)
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(points.first(), 15f))
                else {
                    val b = com.google.android.gms.maps.model.LatLngBounds.Builder()
                    points.forEach(b::include)
                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), 150))
                }
                didAutoFit = true
            }
        }
    }

    // ----- Bottom sheet for selected court -----
    selected?.let { selectedCourt ->
        CourtBottomSheet(
            selected = selectedCourt,
            runs = runs,
            userNames = userNames,
            starredIds = starredIds,
            starsVm = starsVm,
            onOpenRunDetails = onOpenRunDetails,
            onDismiss = { selected = null },
            onStartRunHere = { courtId ->
                dialogError = null
                showCreate = courtId
            }
        )
    }

    // ----- StartRunDialog (same flow as CourtsListScreen) -----
    showCreate?.let { courtId ->
        StartRunDialog(
            visible = true,
            courtId = courtId,
            onDismiss = {
                dialogError = null
                showCreate = null
            },
            onCreate = { run ->
                runsViewModel.createRunWithCapacity(
                    run = run,
                    onSuccess = {
                        dialogError = null
                        showCreate = null
                    },
                    onError = { e ->
                        Log.e("RunsVM", "create failed", e)
                        dialogError = humanizeCreateRunError(e)
                    }
                )
            },
            errorMessage = dialogError
        )
    }

    // ----- Error text -----
    error?.let {
        Text(
            "Map error: $it",
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(12.dp)
        )
    }
}

/* ---------- Helpers ---------- */

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

private fun humanizeCreateRunError(t: Throwable): String {
    val msg = t.message ?: ""
    return when {
        "all surfaces are in use" in msg ->
            "This court is full at that time — all surfaces are in use."
        "Daily host limit" in msg ->
            "Daily host limit reached for this court."
        "maximum number of upcoming runs" in msg ->
            "You already have the maximum number of upcoming runs scheduled."
        "Not signed in" in msg ->
            "You must be signed in to create a run."
        "Missing courtId" in msg ->
            "Couldn’t create run: court is missing."
        "Missing start" in msg ->
            "Please pick a start time."
        "Missing end" in msg ->
            "Please pick an end time."
        msg.contains("FAILED_PRECONDITION", ignoreCase = true) &&
                msg.contains("index", ignoreCase = true) ->
            "Run search needs to finish setting up. Try again in a moment."
        else -> "Couldn't start the run. Please adjust the time or try again."
    }
}
