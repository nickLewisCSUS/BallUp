// ui/courts/components/StartRunDialog.kt
package com.nicklewis.ballup.ui.courts.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.nicklewis.ballup.model.Run
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartRunDialog(
    visible: Boolean,
    courtId: String,
    onDismiss: () -> Unit,
    onCreate: (Run) -> Unit,
    errorMessage: String? = null,   // inline error text from screen
) {
    if (!visible) return

    // --- Submit guard to prevent double-taps ---
    var submitting by remember { mutableStateOf(false) }
    LaunchedEffect(errorMessage) { if (!errorMessage.isNullOrBlank()) submitting = false }

    // --- Name state & helpers ---
    val nameMax = 30
    var nameText by remember { mutableStateOf("") }

    val allowedNameRegex = Regex("""^[\p{L}\p{N}\s'’\-_.!?:()]+$""")
    val badWords = remember {
        listOf(
            "fuck","shit","bitch","asshole","cunt","slut","whore","nigger","fag",
            "retard","kike","spic","chink","twat","cock","dick"
        )
    }
    fun normalizeRunName(raw: String) = raw.trim().replace(Regex("\\s+"), " ")
    fun containsProfanity(s: String): Boolean {
        val lc = s.lowercase()
        return badWords.any { w -> Regex("""\b${Regex.escape(w)}\b""").containsMatchIn(lc) }
    }

    val nameNormalized = normalizeRunName(nameText)
    val nameLenOk = nameNormalized.length in 3..nameMax
    val nameCharsOk = nameNormalized.isEmpty() || allowedNameRegex.matches(nameNormalized)
    val nameCleanOk = nameNormalized.isEmpty() || !containsProfanity(nameNormalized)
    val nameValid = nameLenOk && nameCharsOk && nameCleanOk

    // --- Mode / capacity ---
    val modes = listOf("5v5", "4v4", "3v3", "2v2", "Open gym")
    var mode by remember { mutableStateOf(modes.first()) }

    fun defaultCapacityFor(m: String) = when (m) {
        "5v5" -> 10; "4v4" -> 8; "3v3" -> 6; "2v2" -> 4; else -> 12
    }

    var capacityText by remember { mutableStateOf(defaultCapacityFor(mode).toString()) }
    var userTouchedCapacity by remember { mutableStateOf(false) }
    fun capInt() = capacityText.toIntOrNull() ?: 0
    LaunchedEffect(mode) { if (!userTouchedCapacity) capacityText = defaultCapacityFor(mode).toString() }

    // --- Time state & validation (original flow) ---
    val now = remember { LocalDateTime.now().withSecond(0).withNano(0) }
    val minStart = now.plusMinutes(10)
    val maxStart = now.plusMonths(3)
    val earliestDate = now.toLocalDate()
    val latestDate = now.toLocalDate().plusYears(1)

    var startAt by remember { mutableStateOf(now.plusMinutes(15)) }
    var endAt   by remember { mutableStateOf(now.plusMinutes(15 + 90)) }
    var showStart by remember { mutableStateOf(false) }
    var showEnd   by remember { mutableStateOf(false) }
    var showStartDate by remember { mutableStateOf(false) }   // back to a real date picker

    fun timeLabel(dt: LocalDateTime) =
        dt.format(DateTimeFormatter.ofPattern("EEE, MMM d h:mm a"))

    val capValid = capInt() in 2..50
    val timeValid =
        startAt.isAfter(minStart) &&
                startAt.isBefore(maxStart) &&
                endAt.isAfter(startAt.plusMinutes(15)) &&
                endAt.isBefore(startAt.plusHours(24))

    val formValid = capValid && timeValid && nameValid

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },

        confirmButton = {
            TextButton(
                enabled = formValid && !submitting,
                onClick = {
                    val zone = ZoneId.systemDefault()
                    val run = Run(
                        courtId    = courtId,
                        status     = "active",
                        startsAt   = Timestamp(startAt.atZone(zone).toInstant().epochSecond, 0),
                        endsAt     = Timestamp(endAt.atZone(zone).toInstant().epochSecond, 0),
                        hostId     = null,
                        mode       = mode,
                        maxPlayers = capInt(),
                        playerIds  = emptyList(),
                        name       = normalizeRunName(nameText)
                    )
                    submitting = true
                    onCreate(run)
                }
            ) {
                if (submitting) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Text("Creating…")
                    }
                } else {
                    Text("Create")
                }
            }
        },

        dismissButton = {
            TextButton(enabled = !submitting, onClick = onDismiss) { Text("Cancel") }
        },

        title = { Text("Create run") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // Run Name
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { input ->
                        val filtered = input.filter { ch ->
                            ch.isLetterOrDigit() || ch.isWhitespace() || "'’-_ .!?:()".contains(ch)
                        }.take(nameMax)
                        nameText = filtered
                    },
                    label = { Text("Run name") },
                    singleLine = true,
                    isError = nameText.isNotEmpty() && !nameValid,
                    supportingText = {
                        val remaining = (nameMax - nameNormalized.length).coerceAtLeast(0)
                        when {
                            nameText.isEmpty() -> Text("$nameMax characters max")
                            !nameLenOk -> Text("Name must be 3–$nameMax characters")
                            !nameCharsOk -> Text("Only letters, numbers, spaces, and - _ . ! ? : ( ) ' allowed")
                            !nameCleanOk -> Text("Please choose a different name")
                            else -> Text("$remaining left")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    )
                )

                // Mode dropdown
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = mode, onValueChange = {}, readOnly = true,
                        label = { Text("Mode") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        modes.forEach { m ->
                            DropdownMenuItem(text = { Text(m) }, onClick = {
                                mode = m; expanded = false; userTouchedCapacity = false
                            })
                        }
                    }
                }

                // Capacity
                OutlinedTextField(
                    value = capacityText,
                    onValueChange = {
                        userTouchedCapacity = true
                        capacityText = it.filter { ch -> ch.isDigit() }.take(2)
                    },
                    label = { Text("Capacity") },
                    isError = !capValid,
                    supportingText = { if (!capValid) Text("Enter 2–50 players") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Date/Time pickers — keep pill style, original behavior
                FilledTonalButton(
                    onClick = { showStartDate = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) { Text("Start Date: ${startAt.toLocalDate()}") }

                FilledTonalButton(
                    onClick = { showStart = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) { Text("Start Time: ${timeLabel(startAt)}") }

                FilledTonalButton(
                    onClick = { showEnd = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) { Text("End Time: ${timeLabel(endAt)}") }

                // Time validation
                if (!timeValid) {
                    val msg = when {
                        startAt.isBefore(minStart) -> "Start time must be at least 10 minutes from now."
                        startAt.isAfter(maxStart) -> "Start date can’t be more than 3 months ahead."
                        !endAt.isAfter(startAt.plusMinutes(15)) -> "End time must be at least 15 minutes after start."
                        !endAt.isBefore(startAt.plusHours(24)) -> "Run can’t last more than 24 hours."
                        else -> "Invalid time selection."
                    }
                    Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                // Inline server-side error
                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    )

    // --- Time dialogs (unchanged behavior) ---
    if (showStart) TimePickerDialog(
        initial = startAt,
        onDismiss = { showStart = false },
        onConfirm = { picked ->
            val fixed = when {
                picked.isBefore(minStart) -> minStart
                picked.isAfter(maxStart) -> maxStart
                else -> picked
            }
            startAt = fixed
            if (!fixed.isBefore(endAt)) endAt = fixed.plusMinutes(90)
            showStart = false
        }
    )
    if (showEnd) TimePickerDialog(
        initial = endAt,
        onDismiss = { showEnd = false },
        onConfirm = { picked ->
            val t = picked.toLocalTime()
            var fixed = startAt.withHour(t.hour).withMinute(t.minute).withSecond(0).withNano(0)
            if (fixed.isBefore(startAt)) fixed = fixed.plusDays(1)
            if (!fixed.isAfter(startAt.plusMinutes(15))) fixed = startAt.plusMinutes(15)
            if (fixed.isAfter(startAt.plusHours(24))) fixed = startAt.plusHours(24)
            endAt = fixed
            showEnd = false
        }
    )

    // Original start-date picker (full date, not month-only)
    if (showStartDate) StartDatePickerDialog(
        initial = startAt,
        minDate = earliestDate,
        maxDate = latestDate,
        onDismiss = { showStartDate = false },
        onConfirm = { picked ->
            val fixedStart = when {
                picked.isBefore(minStart) -> minStart
                picked.isAfter(maxStart)  -> maxStart
                else -> picked
            }
            // keep end’s time-of-day; rebuild on new date and clamp within [15m, 24h]
            val endT = endAt.toLocalTime()
            var newEnd = fixedStart.withHour(endT.hour).withMinute(endT.minute).withSecond(0).withNano(0)
            if (!newEnd.isAfter(fixedStart)) newEnd = newEnd.plusDays(1)
            if (newEnd.isBefore(fixedStart.plusMinutes(15))) newEnd = fixedStart.plusMinutes(15)
            if (newEnd.isAfter(fixedStart.plusHours(24)))   newEnd = fixedStart.plusHours(24)

            startAt = fixedStart
            endAt = newEnd
            showStartDate = false
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    initial: LocalDateTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalDateTime) -> Unit
) {
    var tmp by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(tmp) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Pick time") },
        text = {
            val state = rememberTimePickerState(
                initialHour = tmp.hour,
                initialMinute = tmp.minute,
                is24Hour = false
            )
            TimePicker(state = state)
            LaunchedEffect(state.hour, state.minute) {
                tmp = tmp.withHour(state.hour).withMinute(state.minute).withSecond(0).withNano(0)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartDatePickerDialog(
    initial: LocalDateTime,
    minDate: LocalDate,
    maxDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDateTime) -> Unit
) {
    val zone = ZoneId.systemDefault()
    val initMillis = initial.atZone(zone).toInstant().toEpochMilli()

    val state = rememberDatePickerState(
        initialSelectedDateMillis = initMillis,
        yearRange = IntRange(minDate.year, maxDate.year)
    )

    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis
                if (millis != null) {
                    val pickedUtcDate = java.time.Instant.ofEpochMilli(millis)
                        .atZone(java.time.ZoneOffset.UTC)
                        .toLocalDate()

                    val clamped = when {
                        pickedUtcDate.isBefore(minDate) -> minDate
                        pickedUtcDate.isAfter(maxDate)  -> maxDate
                        else -> pickedUtcDate
                    }

                    val fixed = initial.withYear(clamped.year)
                        .withMonth(clamped.monthValue)
                        .withDayOfMonth(clamped.dayOfMonth)

                    onConfirm(fixed)
                } else {
                    onDismiss()
                }
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        DatePicker(state = state, showModeToggle = true)
    }
}

