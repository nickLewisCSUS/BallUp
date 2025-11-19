package com.nicklewis.ballup.ui.profile

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.nicklewis.ballup.model.UserProfile

@Composable
fun ProfileSetupScreen(
    uid: String,
    displayName: String?,
    onProfileSaved: () -> Unit
) {
    val db = remember { Firebase.firestore }

    var username by remember { mutableStateOf("") }
    var skillLevel by remember { mutableStateOf("Beginner") }
    val skillOptions = listOf("Beginner", "Intermediate", "Advanced")

    var isSaving by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Finish setting up your account",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(8.dp))
        Text("Welcome, ${displayName ?: "Hooper"}")

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it.trim() },
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        Text("Skill level")
        SkillLevelDropdown(
            selected = skillLevel,
            options = skillOptions,
            onSelectedChange = { skillLevel = it }
        )

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
                    saveUserProfile(
                        uid = uid,
                        displayName = displayName,
                        username = username,
                        skillLevel = skillLevel,
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

private fun saveUserProfile(
    uid: String,
    displayName: String?,
    username: String,
    skillLevel: String,
    onError: (String) -> Unit,
    onSuccess: () -> Unit
) {
    Log.d("AUTH_FLOW", "saveUserProfile uid=$uid username=$username")
    val db = Firebase.firestore

    if (username.isBlank()) {
        onError("Username can’t be empty")
        return
    }

    // Optional: enforce unique usernames
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

            val profile = UserProfile(
                uid = uid,
                displayName = displayName,
                username = username,
                skillLevel = skillLevel,
                createdAt = now,
                updatedAt = now
            )

            // This is the "JSON" you were asking about, written from code:
            db.collection("users")
                .document(uid)
                .set(profile, SetOptions.merge()) // merge keeps stars/tokens
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { e ->
                    onError("Failed to save profile: ${e.message}")
                }
        }
        .addOnFailureListener { e ->
            onError("Failed to check username: ${e.message}")
        }
}

