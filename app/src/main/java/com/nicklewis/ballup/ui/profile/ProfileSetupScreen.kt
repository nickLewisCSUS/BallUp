package com.nicklewis.ballup.ui.profile

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

// Represents a court option loaded from Firestore
private data class CourtOption(
    val id: String,
    val name: String
)

@Composable
fun ProfileSetupScreen(
    uid: String,
    displayName: String?,
    onProfileSaved: () -> Unit
) {
    val db = remember { Firebase.firestore }

    // Core profile fields
    var username by remember { mutableStateOf("") }
    var skillLevel by remember { mutableStateOf("Beginner") }
    val skillOptions = listOf("Beginner", "Intermediate", "Advanced")

    // New: play style
    val playStyleOptions = listOf(
        "Shooter",
        "Slasher / Driver",
        "Playmaker",
        "Lockdown Defender",
        "Big Man / Post Player",
        "3-and-D",
        "All-Around",
        "Hustle / Energy"
    )
    var playStyle by remember { mutableStateOf("Select (optional)") }

    // New: height bracket
    val heightOptions = listOf(
        "Select (optional)",
        "Under 5'6\"",
        "5'6\" – 5'9\"",
        "5'10\" – 6'1\"",
        "6'2\" – 6'5\"",
        "6'6\" – 6'9\"",
        "6'10\"+"
    )
    var heightBracket by remember { mutableStateOf("Select (optional)") }

    // New: favorite courts (keep both ID + name)
    var availableCourts by remember { mutableStateOf<List<CourtOption>>(emptyList()) }
    var favoriteCourtIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var courtsLoading by remember { mutableStateOf(false) }

    var isSaving by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    // Load court names from Firestore (best-effort)
    LaunchedEffect(Unit) {
        courtsLoading = true
        db.collection("courts")
            .get()
            .addOnSuccessListener { snap ->
                val courts = snap.documents
                    .mapNotNull { doc ->
                        val name = doc.getString("name") ?: return@mapNotNull null
                        CourtOption(id = doc.id, name = name)
                    }
                    .sortedBy { it.name }

                availableCourts = courts
                courtsLoading = false
            }
            .addOnFailureListener { e ->
                Log.w("ProfileSetup", "Failed to load courts", e)
                courtsLoading = false
            }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "Finish setting up your account",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(8.dp))
        Text("Welcome, ${displayName ?: "Hooper"}")

        Spacer(Modifier.height(24.dp))

        // Username
        OutlinedTextField(
            value = username,
            onValueChange = { username = it.trim() },
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        // Skill level
        Text("Skill level", style = MaterialTheme.typography.labelMedium)
        SkillLevelDropdown(
            selected = skillLevel,
            options = skillOptions,
            onSelectedChange = { skillLevel = it }
        )

        Spacer(Modifier.height(16.dp))

        // Play style (optional)
        Text("Preferred play style (optional)", style = MaterialTheme.typography.labelMedium)
        PlayStyleDropdown(
            selected = playStyle,
            options = playStyleOptions,
            onSelectedChange = { playStyle = it }
        )

        Spacer(Modifier.height(16.dp))

        // Height bracket (optional)
        Text("Height bracket (optional)", style = MaterialTheme.typography.labelMedium)
        HeightBracketDropdown(
            selected = heightBracket,
            options = heightOptions,
            onSelectedChange = { heightBracket = it }
        )

        Spacer(Modifier.height(16.dp))

        // Favorite courts (optional)
        Text("Favorite courts (optional)", style = MaterialTheme.typography.labelMedium)
        if (courtsLoading) {
            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
                Text("Loading courts…", style = MaterialTheme.typography.bodySmall)
            }
        } else if (availableCourts.isEmpty()) {
            Text(
                "No courts found yet. You can add favorites later in Settings.",
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Text(
                "Choose up to 3 courts you hoop at the most.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                availableCourts.forEach { court ->
                    val checked = favoriteCourtIds.contains(court.id)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { isChecked ->
                                favoriteCourtIds =
                                    if (isChecked) {
                                        if (favoriteCourtIds.size >= 3) {
                                            // Ignore extra beyond 3
                                            favoriteCourtIds
                                        } else {
                                            favoriteCourtIds + court.id
                                        }
                                    } else {
                                        favoriteCourtIds - court.id
                                    }
                            }
                        )
                        Text(
                            court.name,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }

                if (favoriteCourtIds.size >= 3) {
                    Text(
                        "You’ve selected the maximum of 3 courts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (errorText != null) {
            Text(
                text = errorText!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = {
                if (!isSaving) {
                    isSaving = true
                    errorText = null

                    // Convert selected IDs to names for storing in user doc
                    val favIds = favoriteCourtIds
                    val favNames = availableCourts
                        .filter { favIds.contains(it.id) }
                        .map { it.name }

                    saveUserProfile(
                        uid = uid,
                        displayName = displayName,
                        username = username,
                        skillLevel = skillLevel,
                        playStyle = playStyle
                            .takeIf { it.isNotBlank() && it != "Select (optional)" },
                        heightBracket = heightBracket
                            .takeIf { it.isNotBlank() && it != "Select (optional)" },
                        favoriteCourtIds = favIds,
                        favoriteCourtNames = favNames,
                        onError = {
                            errorText = it
                            isSaving = false
                        },
                        onSuccess = {
                            isSaving = false
                            onProfileSaved()
                        }
                    )
                }
            },
            enabled = username.isNotBlank() && !isSaving,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text("Continue")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillLevelDropdown(
    selected: String,
    options: List<String>,
    onSelectedChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text("Skill level") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelectedChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayStyleDropdown(
    selected: String,
    options: List<String>,
    onSelectedChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text("Play style") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelectedChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeightBracketDropdown(
    selected: String,
    options: List<String>,
    onSelectedChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text("Height bracket") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelectedChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun saveUserProfile(
    uid: String,
    displayName: String?,
    username: String,
    skillLevel: String,
    playStyle: String?,
    heightBracket: String?,
    favoriteCourtIds: List<String>,
    favoriteCourtNames: List<String>,
    onError: (String) -> Unit,
    onSuccess: () -> Unit
) {
    Log.d("AUTH_FLOW", "saveUserProfile uid=$uid username=$username")
    val db = Firebase.firestore

    if (username.isBlank()) {
        onError("Username can’t be empty")
        return
    }

    // Enforce unique usernames
    db.collection("users")
        .whereEqualTo("username", username)
        .limit(1)
        .get()
        .addOnSuccessListener { query ->
            if (!query.isEmpty) {
                onError("That username is already taken")
                return@addOnSuccessListener
            }

            val now = Timestamp.now()

            val profileData = mutableMapOf<String, Any?>(
                "uid" to uid,
                "displayName" to displayName,
                "username" to username,
                "skillLevel" to skillLevel,
                "createdAt" to now,
                "updatedAt" to now
            )

            if (!playStyle.isNullOrBlank()) {
                profileData["playStyle"] = playStyle
            }
            if (!heightBracket.isNullOrBlank()) {
                profileData["heightBracket"] = heightBracket
            }
            if (favoriteCourtNames.isNotEmpty()) {
                profileData["favoriteCourts"] = favoriteCourtNames
            }
            if (favoriteCourtIds.isNotEmpty()) {
                profileData["favoriteCourtIds"] = favoriteCourtIds
            }

            db.collection("users")
                .document(uid)
                .set(profileData, SetOptions.merge()) // merge keeps stars/tokens
                .addOnSuccessListener {
                    if (favoriteCourtIds.isNotEmpty()) {
                        syncFavoriteCourtsToStars(
                            uid = uid,
                            courtIds = favoriteCourtIds,
                            onComplete = onSuccess
                        )
                    } else {
                        onSuccess()
                    }
                }
                .addOnFailureListener { e ->
                    onError("Failed to save profile: ${e.message}")
                }
        }
        .addOnFailureListener { e ->
            onError("Failed to check username: ${e.message}")
        }
}

/**
 * Sync selected favorite courts to the user's stars subcollection so that
 * StarsViewModel / CourtCard will show them as starred.
 *
 * NOTE: this assumes you store stars under:
 *   users/{uid}/stars/{courtId}
 * If your actual path is different, update starsCol below to match.
 */
private fun syncFavoriteCourtsToStars(
    uid: String,
    courtIds: List<String>,
    onComplete: () -> Unit
) {
    val db = Firebase.firestore
    val batch = db.batch()

    val starsCol = db.collection("users")
        .document(uid)
        .collection("stars")

    courtIds.forEach { courtId ->
        val docRef = starsCol.document(courtId)
        batch.set(docRef, mapOf("courtId" to courtId))
    }

    batch.commit()
        .addOnSuccessListener {
            onComplete()
        }
        .addOnFailureListener { e ->
            Log.w("ProfileSetup", "Favorite->star sync failed", e)
            // Even if stars fail, we still finish profile setup.
            onComplete()
        }
}
