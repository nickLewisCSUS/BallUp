package com.nicklewis.ballup.ui.courts.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.google.firebase.Timestamp
import com.nicklewis.ballup.model.Run

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartRunDialog(
    visible: Boolean,
    courtId: String,
    onDismiss: () -> Unit,
    onCreate: (Run) -> Unit,
    errorMessage: String? = null,   // NEW: inline error text from screen
) {
    if (!visible) return

    // --- NEW: name state & helpers ---
    val nameMax = 30
    var nameText by remember { mutableStateOf("") }

    // Allow letters, numbers, spaces, and a few common punctuation marks
    val allowedNameRegex = Regex("""^[\p{L}\p{N}\s'’\-_.!?:()]+$""")

    // Very small blocklist to start (server should also validate)
    val badWords = remember {
        listOf(
            "fuck","shit","bitch","asshole","cunt","slut","whore","nigger","fag",
            "retard","kike","spic","chink","twat","cock","dick"
        )
    }

    fun normalizeRunName(raw: String): String =
        raw.trim().replace(Regex("\\s+"), " ")

    fun containsProfanity(s: String): Boolean {
        val lc = s.lowercase()
        return badWords.any { w -> Regex("""\b${Regex.escape(w)}\b""").containsMatchIn(lc) }
    }

    val nameNormalized = normalizeRunName(nameText)
    val nameLenOk = nameNormalized.length in 3..nameMax
    val nameCharsOk = nameNormalized.isEmpty() || allowedNameRegex.matches(nameNormalized)
    val nameCleanOk = nameNormalized.isEmpty() || !containsProfanity(nameNormalized)
    val nameValid = nameLenOk && nameCharsOk && nameCleanOk

    // --- existing state (mode, capacity, times) remains unchanged ---
    val modes = listOf("5v5", "4v4", "3v3", "2v2", "Open gym")
    var mode by remember { mutableStateOf(modes.first()) }

    fun defaultCapacityFor(m: String) = when (m) {
        "5v5" -> 10; "4v4" -> 8; "3v3" -> 6; "2v2" -> 4; else -> 12
    }

    var capacityText by remember { mutableStateOf(defaultCapacityFor(mode).toString()) }
    var userTouchedCapacity by remember { mutableStateOf(false) }
    fun capInt() = capacityText.toIntOrNull() ?: 0
    LaunchedEffect(mode) { if (!userTouchedCapacity) capacityText = defaultCapacityFor(mode).toString() }

    val now = remember { LocalDateTime.now().withSecond(0).withNano(0) }
    val minStart = now.plusMinutes(10)
    val maxStart = now.plusMonths(3)
    val earliestDate = now.toLocalDate()
    val latestDate = now.toLocalDate().plusYears(1)

    var startAt by remember { mutableStateOf(now.plusMinutes(15)) }
    var endAt   by remember { mutableStateOf(now.plusMinutes(15 + 90)) }
    var showStart by remember { mutableStateOf(false) }
    var showEnd   by remember { mutableStateOf(false) }
    var showStartDate by remember { mutableStateOf(false) }

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
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = formValid,
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
                        name       = normalizeRunName(nameText)    // <-- NEW
                    )
                    onCreate(run)
                }
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Start a run") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // --- Run Name input with counter & validation ---
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { input ->
                        // Enforce character whitelist & length progressively as user types
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
                    modifier = Modifier.fillMaxWidth()
                )

                // Mode dropdown … (unchanged)
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

                // Capacity … (unchanged)
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

                // Date/Time pickers … (unchanged)
                OutlinedButton(onClick = { showStartDate = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Start Date: ${startAt.toLocalDate()}")
                }
                OutlinedButton(onClick = { showStart = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Start Time: ${timeLabel(startAt)}")
                }
                OutlinedButton(onClick = { showEnd = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("End Time: ${timeLabel(endAt)}")
                }

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

                // --- NEW: inline server-side error (capacity/limits/etc.) ---
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
            // Build candidate on the start date with picked time
            val t = picked.toLocalTime()
            var fixed = startAt.withHour(t.hour).withMinute(t.minute).withSecond(0).withNano(0)

            // If time is earlier than the start time, move to next day
            if (fixed.isBefore(startAt)) fixed = fixed.plusDays(1)

            // Enforce 15 min minimum and 24h maximum window
            if (!fixed.isAfter(startAt.plusMinutes(15))) fixed = startAt.plusMinutes(15)
            if (fixed.isAfter(startAt.plusHours(24))) fixed = startAt.plusHours(24)

            endAt = fixed
            showEnd = false
        }
    )
    if (showStartDate) StartDatePickerDialog(
        initial = startAt,
        minDate = earliestDate,
        maxDate = latestDate,
        onDismiss = { showStartDate = false },
        onConfirm = { picked ->
            // Apply time-based bounds to the picked start
            val fixedStart = when {
                picked.isBefore(minStart) -> minStart
                picked.isAfter(maxStart)  -> maxStart
                else -> picked
            }

            // Rebuild end using the SAME time-of-day as before, on the new start date
            val endT = endAt.toLocalTime()
            var newEnd = fixedStart.withHour(endT.hour)
                .withMinute(endT.minute)
                .withSecond(0)
                .withNano(0)

            // If end is not after start on that date, roll to next day
            if (!newEnd.isAfter(fixedStart)) newEnd = newEnd.plusDays(1)

            // Clamp to [start + 15 min, start + 24h]
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
fun StartDatePickerDialog(
    initial: LocalDateTime,
    minDate: java.time.LocalDate,   // today
    maxDate: java.time.LocalDate,   // today + 1y
    onDismiss: () -> Unit,
    onConfirm: (LocalDateTime) -> Unit
) {
    val zone = ZoneId.systemDefault()
    val initMillis = initial.atZone(zone).toInstant().toEpochMilli()

    val state = rememberDatePickerState(
        initialSelectedDateMillis = initMillis,
        yearRange = IntRange(minDate.year, maxDate.year)
    )

    // Material3 DatePickerDialog (not generic AlertDialog)
    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis
                if (millis != null) {
                    // interpret selection at UTC midnight to avoid TZ back-shift
                    val pickedUtcDate = java.time.Instant.ofEpochMilli(millis)
                        .atZone(java.time.ZoneOffset.UTC)
                        .toLocalDate()

                    // clamp date to [minDate, maxDate]
                    val clamped = when {
                        pickedUtcDate.isBefore(minDate) -> minDate
                        pickedUtcDate.isAfter(maxDate)  -> maxDate
                        else -> pickedUtcDate
                    }

                    // keep existing time-of-day; swap only the date
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
