package com.nicklewis.ballup.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    onBack: () -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val uid = auth.currentUser?.uid

    var username by remember { mutableStateOf("") }
    var skillLevel by remember { mutableStateOf("") }
    var playStyle by remember { mutableStateOf("") }
    var heightBracket by remember { mutableStateOf("") }
    var favoriteCourts by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Dropdown options
    val skillLevelOptions = listOf("Beginner", "Intermediate", "Advanced", "Pro / Semi-Pro")
    val playStyleOptions = listOf(
        "All-around",
        "Shooter / Spot-up",
        "Slasher / Driver",
        "Big Man / Post Player",
        "Point Guard / Floor General"
    )
    val heightOptions = listOf(
        "Under 5'6\"",
        "5'6\" – 5'9\"",
        "5'10\" – 6'1\"",
        "6'2\" – 6'5\"",
        "6'6\" and up"
    )

    var skillExpanded by remember { mutableStateOf(false) }
    var playStyleExpanded by remember { mutableStateOf(false) }
    var heightExpanded by remember { mutableStateOf(false) }

    // 🔹 Load current user profile from Firestore
    LaunchedEffect(uid) {
        if (uid == null) {
            errorText = "No user is signed in."
            isLoading = false
            return@LaunchedEffect
        }

        db.collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { doc ->
                if (doc != null && doc.exists()) {
                    username = doc.getString("username") ?: ""
                    skillLevel = doc.getString("skillLevel") ?: ""
                    playStyle = doc.getString("playStyle") ?: ""
                    heightBracket = doc.getString("heightBracket") ?: ""

                    val favList = doc.get("favoriteCourts") as? List<*>
                    favoriteCourts = favList
                        ?.filterIsInstance<String>()
                        ?.joinToString(", ")
                        ?: ""
                }
                isLoading = false
            }
            .addOnFailureListener { e ->
                errorText = "Failed to load profile: ${e.message ?: "Unknown error"}"
                isLoading = false
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Profile") },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (!isSaving) onBack()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(16.dp)
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .imePadding()
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                // Username (free text)
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Skill level dropdown
                ExposedDropdownMenuBox(
                    expanded = skillExpanded,
                    onExpandedChange = { skillExpanded = !skillExpanded }
                ) {
                    OutlinedTextField(
                        value = skillLevel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Skill level") },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = skillExpanded)
                        }
                    )
                    ExposedDropdownMenu(
                        expanded = skillExpanded,
                        onDismissRequest = { skillExpanded = false }
                    ) {
                        skillLevelOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    skillLevel = option
                                    skillExpanded = false
                                }
                            )
                        }
                    }
                }

                // Play style dropdown
                ExposedDropdownMenuBox(
                    expanded = playStyleExpanded,
                    onExpandedChange = { playStyleExpanded = !playStyleExpanded }
                ) {
                    OutlinedTextField(
                        value = playStyle,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Play style") },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = playStyleExpanded)
                        }
                    )
                    ExposedDropdownMenu(
                        expanded = playStyleExpanded,
                        onDismissRequest = { playStyleExpanded = false }
                    ) {
                        playStyleOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    playStyle = option
                                    playStyleExpanded = false
                                }
                            )
                        }
                    }
                }

                // Height bracket dropdown
                ExposedDropdownMenuBox(
                    expanded = heightExpanded,
                    onExpandedChange = { heightExpanded = !heightExpanded }
                ) {
                    OutlinedTextField(
                        value = heightBracket,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Height bracket") },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = heightExpanded)
                        }
                    )
                    ExposedDropdownMenu(
                        expanded = heightExpanded,
                        onDismissRequest = { heightExpanded = false }
                    ) {
                        heightOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    heightBracket = option
                                    heightExpanded = false
                                }
                            )
                        }
                    }
                }

                // Favorite courts (still free text for now)
                OutlinedTextField(
                    value = favoriteCourts,
                    onValueChange = { favoriteCourts = it },
                    label = { Text("Favorite courts") },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("You can list multiple courts, comma-separated") }
                )

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (uid == null) {
                            errorText = "No user is signed in."
                            return@Button
                        }

                        isSaving = true
                        errorText = null

                        val favoriteCourtsList = favoriteCourts
                            .split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }

                        val updates = mapOf(
                            "username" to username,
                            "skillLevel" to skillLevel,
                            "playStyle" to playStyle,
                            "heightBracket" to heightBracket,
                            "favoriteCourts" to favoriteCourtsList,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )

                        db.collection("users")
                            .document(uid)
                            .update(updates)
                            .addOnSuccessListener {
                                isSaving = false
                                scope.launch {
                                    snackbarHostState.showSnackbar("Profile updated")
                                }
                                // stay on screen so they see the snackbar; user can tap back
                            }
                            .addOnFailureListener { e ->
                                isSaving = false
                                errorText =
                                    "Failed to save profile: ${e.message ?: "Unknown error"}"
                            }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving
                ) {
                    Text(if (isSaving) "Saving..." else "Save")
                }
            }
        }
    }
}

