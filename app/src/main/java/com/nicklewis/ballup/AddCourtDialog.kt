package com.nicklewis.ballup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun AddCourtDialog(
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val db = remember { FirebaseFirestore.getInstance() }

    var name by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("") }       // e.g., "indoor" / "outdoor"
    var address by rememberSaveable { mutableStateOf("") }
    var latText by rememberSaveable { mutableStateOf("") }
    var lngText by rememberSaveable { mutableStateOf("") }
    var lights by rememberSaveable { mutableStateOf(false) }
    var restrooms by rememberSaveable { mutableStateOf(false) }

    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Add court") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text("Type (indoor/outdoor)") }, singleLine = true)
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") }, singleLine = true)

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = latText, onValueChange = { latText = it },
                        label = { Text("Lat") }, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = lngText, onValueChange = { lngText = it },
                        label = { Text("Lng") }, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                Row {
                    Checkbox(checked = lights, onCheckedChange = { lights = it })
                    Spacer(Modifier.width(8.dp)); Text("Lights")
                    Spacer(Modifier.width(16.dp))
                    Checkbox(checked = restrooms, onCheckedChange = { restrooms = it })
                    Spacer(Modifier.width(8.dp)); Text("Restrooms")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    val lat = latText.toDoubleOrNull()
                    val lng = lngText.toDoubleOrNull()
                    if (name.isBlank() || lat == null || lng == null) {
                        error = "Name, lat, and lng are required"; return@TextButton
                    }
                    saving = true; error = null
                    val court = Court(
                        name = name.trim(),
                        type = type.trim(),
                        address = address.trim(),
                        geo = Geo(lat = lat, lng = lng),
                        amenities = Amenities(lights = lights, restrooms = restrooms),
                        createdAt = Timestamp.now(),
                        createdBy = "uid_dev" // TODO: replace with FirebaseAuth uid
                    )
                    db.collection("courts").add(court)
                        .addOnSuccessListener { saving = false; onSaved() }
                        .addOnFailureListener { e -> saving = false; error = e.message ?: "Failed to save" }
                }
            ) { Text(if (saving) "Saving…" else "Save") }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") }
        }
    )
}
