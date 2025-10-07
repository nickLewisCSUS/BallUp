package com.nicklewis.ballup.ui.courts.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import java.util.Date
import androidx.compose.ui.unit.dp

@Composable
fun RunCreateDialog(
    onDismiss: () -> Unit,
    onCreate: (startsAtMillis: Long?, endsAtMillis: Long?, mode: String, maxPlayers: Int) -> Unit
) {
    var mode by remember { mutableStateOf("5v5") }
    var max by remember { mutableStateOf(10) }
    val now = remember { System.currentTimeMillis() }
    var start by remember { mutableStateOf(now) }
    var end by remember { mutableStateOf(now + 90 * 60_000) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start a run") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Mode: $mode")      // TODO: replace with chips/dropdown
                Text("Capacity: $max")   // TODO: replace with stepper/slider
                Text("Starts: " + android.text.format.DateFormat.format("EEE, MMM d h:mm a", Date(start)))
                Text("Ends: "   + android.text.format.DateFormat.format("EEE, MMM d h:mm a", Date(end)))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (end <= start) return@TextButton
                onCreate(start, end, mode, max)
            }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
