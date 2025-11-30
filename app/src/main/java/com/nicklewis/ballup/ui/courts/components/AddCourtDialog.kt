package com.nicklewis.ballup.ui.courts.components

import android.location.Geocoder
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.nicklewis.ballup.model.Amenities
import com.nicklewis.ballup.model.Court
import com.nicklewis.ballup.model.Geo
import com.nicklewis.ballup.util.hasLocationPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

// ---- Court name validation helpers (client side) ----
private val COURT_NAME_ALLOWED_CHARS = Regex("^[A-Za-z0-9 .,'-]+$")
private val COURT_NAME_HAS_SPACE = Regex(".*\\s+.*")          // at least two words
private val COURT_NAME_HAS_VOWEL = Regex("(?i).*[aeiou].*")   // must contain a vowel

@Composable
fun AddCourtDialog(
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by rememberSaveable { mutableStateOf("") }

    // type as selection
    var type by rememberSaveable { mutableStateOf("outdoor") }

    var address by rememberSaveable { mutableStateOf("") }

    // location stored internally; user never sees raw numbers
    var lat by rememberSaveable { mutableStateOf<Double?>(null) }
    var lng by rememberSaveable { mutableStateOf<Double?>(null) }

    var surfacesText by rememberSaveable { mutableStateOf("1") }

    var lights by rememberSaveable { mutableStateOf(false) }
    var restrooms by rememberSaveable { mutableStateOf(false) }

    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var showMapPicker by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Add court") },
        text = {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (error != null) {
                    Text(
                        error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Court name (real park or gym)") },
                    singleLine = true
                )

                // --- Indoor / Outdoor selector ---
                Text(
                    "Court type",
                    style = MaterialTheme.typography.labelMedium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = type == "indoor",
                        onClick = { type = "indoor" },
                        label = { Text("Indoor") }
                    )
                    FilterChip(
                        selected = type == "outdoor",
                        onClick = { type = "outdoor" },
                        label = { Text("Outdoor") }
                    )
                }

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") },
                    singleLine = true
                )

                // --- Map-based location only ---
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val locationLabel =
                        if (lat != null && lng != null) "Location selected"
                        else "Location not set"

                    Text(
                        locationLabel,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    TextButton(onClick = { showMapPicker = true }) {
                        Text(if (lat != null && lng != null) "Edit on map" else "Pick on map")
                    }
                }

                OutlinedTextField(
                    value = surfacesText,
                    onValueChange = { surfacesText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Number of surfaces (1–10)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Row {
                    Checkbox(checked = lights, onCheckedChange = { lights = it })
                    Spacer(Modifier.width(8.dp)); Text("Lights")
                    Spacer(Modifier.width(16.dp))
                    Checkbox(checked = restrooms, onCheckedChange = { restrooms = it })
                    Spacer(Modifier.width(8.dp)); Text("Restrooms")
                }

                if (saving) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    if (saving) return@TextButton

                    scope.launch {
                        saving = true
                        error = null

                        // ---- Name validation (no gibberish / silly names) ----
                        val cleanedName = name.trim()
                        val lowerName = cleanedName.lowercase()

                        // Very small “joke/test” blocklist
                        val bannedTokens = listOf(
                            "test", "asdf", "qwerty",
                            "lol", "lmao",
                            "butt", "poop"
                        )

                        when {
                            cleanedName.length < 3 -> {
                                error =
                                    "Court name looks too short. Please use the real park or gym name."
                                saving = false
                                return@launch
                            }

                            !COURT_NAME_ALLOWED_CHARS.matches(cleanedName) ||
                                    !COURT_NAME_HAS_SPACE.matches(cleanedName) ||
                                    !COURT_NAME_HAS_VOWEL.matches(cleanedName) -> {
                                error =
                                    "Please use a real location-style name, like “McBean Park Courts”."
                                saving = false
                                return@launch
                            }

                            bannedTokens.any { it in lowerName } -> {
                                error =
                                    "Please use the real court or park name (no placeholders)."
                                saving = false
                                return@launch
                            }
                        }

                        // ---- Surfaces validation (1–10 only) ----
                        val rawSurfaces = surfacesText.toIntOrNull()
                        if (rawSurfaces == null || rawSurfaces < 1 || rawSurfaces > 10) {
                            error = "Number of surfaces must be between 1 and 10."
                            saving = false
                            return@launch
                        }
                        val surfaces = rawSurfaces

                        // =====================================================
                        //  Address + location validation (ALWAYS validate text)
                        // =====================================================
                        if (address.isBlank()) {
                            error = "Enter a valid address or pick the location on the map."
                            saving = false
                            return@launch
                        }

                        // Geocode the current address text to make sure it’s real.
                        var geocodedLat: Double? = null
                        var geocodedLng: Double? = null
                        try {
                            val geocoder = Geocoder(context)
                            val results = withContext(Dispatchers.IO) {
                                geocoder.getFromLocationName(address, 1)
                            }

                            if (!results.isNullOrEmpty()) {
                                geocodedLat = results[0].latitude
                                geocodedLng = results[0].longitude
                            } else {
                                error =
                                    "Couldn’t find that address. Please adjust it or pick the location on the map."
                                saving = false
                                return@launch
                            }
                        } catch (e: Exception) {
                            error =
                                "Problem validating address. Please check it or pick on the map."
                            saving = false
                            return@launch
                        }

                        // Use existing map location if set; otherwise fall back to geocoded result.
                        var finalLat = lat ?: geocodedLat
                        var finalLng = lng ?: geocodedLng

                        // If we still somehow have no coordinates, block the save.
                        if (finalLat == null || finalLng == null) {
                            error =
                                "We couldn’t get a location for that address. Please pick it on the map."
                            saving = false
                            return@launch
                        }

                        // Keep state in sync
                        lat = finalLat
                        lng = finalLng

                        if (cleanedName.isBlank()) {
                            error = "Name and a valid location are required."
                            saving = false
                            return@launch
                        }

                        val court = Court(
                            name = cleanedName,
                            type = type.trim(), // "indoor" or "outdoor"
                            address = address.trim(),
                            geo = Geo(lat = finalLat, lng = finalLng),
                            surfaces = surfaces,
                            amenities = Amenities(lights = lights, restrooms = restrooms),
                            createdAt = Timestamp.now(),
                            createdBy = "uid_dev" // TODO: replace with FirebaseAuth uid
                        )

                        try {
                            db.collection("courts").add(court).await()
                            saving = false
                            onSaved()
                        } catch (e: Exception) {
                            saving = false
                            error = e.message ?: "Failed to save"
                        }
                    }
                }
            ) { Text(if (saving) "Saving…" else "Save") }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") }
        }
    )

    // Separate map picker dialog (user never sees numbers)
    if (showMapPicker) {
        AlertDialog(
            onDismissRequest = { showMapPicker = false },
            title = { Text("Tap on map to set court location") },
            text = {
                CourtLocationPicker(
                    lat = lat,
                    lng = lng,
                    onPicked = { newLat, newLng, newAddress, suggestedName ->
                        lat = newLat
                        lng = newLng
                        if (!newAddress.isNullOrBlank()) {
                            address = newAddress
                        }
                        // Auto-suggest a professional name ONLY if the field is still blank
                        if (name.isBlank() && !suggestedName.isNullOrBlank()) {
                            name = suggestedName
                        }
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { showMapPicker = false }) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
private fun CourtLocationPicker(
    lat: Double?,
    lng: Double?,
    onPicked: (Double, Double, String?, String?) -> Unit
) {
    val context = LocalContext.current
    val mapView = rememberMapViewWithLifecycle()
    val fused = remember { LocationServices.getFusedLocationProviderClient(context) }

    var googleMap by remember { mutableStateOf<GoogleMap?>(null) }
    var userLatLng by remember { mutableStateOf<LatLng?>(null) }

    // Try to grab the user's last known location
    LaunchedEffect(Unit) {
        if (!hasLocationPermission(context)) return@LaunchedEffect

        try {
            val loc = fused.lastLocation.await()
            if (loc != null) {
                userLatLng = LatLng(loc.latitude, loc.longitude)
            }
        } catch (_: SecurityException) {
            // permission issue → ignore, we'll fall back
        } catch (_: Exception) {
            // any other issue → ignore, we'll fall back
        }
    }

    // Whenever we know either a pre-selected court location OR user location, center the map
    LaunchedEffect(lat, lng, userLatLng) {
        val map = googleMap ?: return@LaunchedEffect

        val start = when {
            lat != null && lng != null -> LatLng(lat, lng)       // already picked in dialog
            userLatLng != null          -> userLatLng!!          // center on user
            else                        -> LatLng(38.5816, -121.4944) // Sacramento fallback
        }

        map.clear()
        // If we already have an explicit court location, show its marker
        if (lat != null && lng != null) {
            map.addMarker(MarkerOptions().position(start))
        }

        map.moveCamera(CameraUpdateFactory.newLatLngZoom(start, 13f))
    }

    // Map + overlay "recenter" button
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
    ) {
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { ctx ->
                MapsInitializer.initialize(ctx)
                mapView.apply {
                    getMapAsync { map ->
                        googleMap = map
                        map.uiSettings.isZoomControlsEnabled = true

                        map.setOnMapClickListener { latLng ->
                            // Reverse geocode address + derive a suggested court name
                            var resolvedAddress: String? = null
                            var suggestedName: String? = null
                            try {
                                val geocoder = Geocoder(context)
                                val results =
                                    geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                                if (!results.isNullOrEmpty()) {
                                    val addr = results[0]
                                    resolvedAddress = addr.getAddressLine(0)

                                    val feature = addr.featureName ?: ""
                                    val subLocality = addr.subLocality ?: ""
                                    val locality = addr.locality ?: ""

                                    // Build a nice base like "McBean Memorial Park"
                                    val base = listOf(feature, subLocality, locality)
                                        .map { it.trim() }
                                        .filter { it.isNotEmpty() }
                                        .distinct()
                                        .firstOrNull()

                                    if (!base.isNullOrEmpty()) {
                                        suggestedName =
                                            if (base.contains("court", ignoreCase = true) ||
                                                base.contains("gym", ignoreCase = true)
                                            ) {
                                                base
                                            } else if (base.contains("park", ignoreCase = true)) {
                                                if (base.contains("court", ignoreCase = true) ||
                                                    base.contains("courts", ignoreCase = true)
                                                ) {
                                                    base
                                                } else {
                                                    "$base Courts"
                                                }
                                            } else {
                                                "$base Courts"
                                            }
                                    }
                                }
                            } catch (_: Exception) {
                                // ignore geocoder failures
                            }

                            onPicked(
                                latLng.latitude,
                                latLng.longitude,
                                resolvedAddress,
                                suggestedName
                            )

                            map.clear()
                            map.addMarker(MarkerOptions().position(latLng))
                            map.animateCamera(
                                CameraUpdateFactory.newLatLngZoom(latLng, 15f)
                            )
                        }
                    }
                }
            }
        )

        // Recenter button (shows when we know user location)
        if (userLatLng != null) {
            IconButton(
                onClick = {
                    val map = googleMap ?: return@IconButton
                    val target = userLatLng ?: return@IconButton
                    map.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(target, 15f)
                    )
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.MyLocation,
                    contentDescription = "Recenter on my location"
                )
            }
        }
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
