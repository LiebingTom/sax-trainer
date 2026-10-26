package com.example.saxtrainer.feature.reedlogbook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.example.saxtrainer.core.designsystem.component.FieldRow
import com.example.saxtrainer.core.designsystem.theme.Dimens
import com.example.saxtrainer.core.reed.Reed
import com.example.saxtrainer.core.reed.ReedLogbook
import com.example.saxtrainer.core.reed.isFreeForSession
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Entering a session played without the clock running - forgotten, or played away from the phone:
 * the day, when it started and how long it lasted.
 *
 * Opens on half an hour ending about now. Adding is held back, with the reason under the fields,
 * while the time lies in the future or shares any of another session's - the same rule the logbook
 * keeps, see [isFreeForSession].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddSessionDialog(
    reed: Reed,
    logbook: ReedLogbook,
    now: Instant,
    zone: TimeZone,
    onAdd: (start: Instant, end: Instant) -> Unit,
    onCancel: () -> Unit,
) {
    val today = remember(zone) { now.toLocalDateTime(zone).date }
    val defaultStart = remember(zone) { (now - DEFAULT_LENGTH.minutes).toLocalDateTime(zone) }
    var date by remember { mutableStateOf(defaultStart.date) }
    var time by remember { mutableStateOf(LocalTime(defaultStart.hour, defaultStart.minute)) }
    var minutes by remember { mutableStateOf(DEFAULT_LENGTH.toString()) }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }

    val length = ReedText.parseMinutes(minutes)
    val start = LocalDateTime(date, time).toInstant(zone)
    val end = length?.let { start + it.minutes }
    val problem = when {
        end == null -> null
        end > now -> "That runs on past now."
        !logbook.isFreeForSession(start, end, now) -> "Another session was played in this time."
        else -> null
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Add a session") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                Text("A session played on ${ReedText.titleOf(reed)} without the clock running.")
                FieldRow(label = "Day", value = ReedText.dayOf(date), onClick = { pickingDate = true })
                FieldRow(label = "Started at", value = ReedText.timeOf(time), onClick = { pickingTime = true })
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Length in minutes") },
                    isError = length == null,
                    supportingText = if (length == null) {
                        { Text("Whole minutes, 1 to ${ReedText.MAX_SESSION_MINUTES}") }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                problem?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (end != null) onAdd(start, end) },
                enabled = end != null && problem == null,
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )

    if (pickingDate) {
        // The picker counts in UTC midnights, whatever the zone: a day, not a moment.
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = dayOfUtcMillis(utcTimeMillis) <= today
                override fun isSelectableYear(year: Int): Boolean = year <= today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = dayOfUtcMillis(it) }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state)
        }
    }
    if (pickingTime) {
        val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            title = { Text("Started at") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    time = LocalTime(state.hour, state.minute)
                    pickingTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingTime = false }) { Text("Cancel") } },
        )
    }
}

private fun dayOfUtcMillis(millis: Long): LocalDate = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date

/** Minutes a new entry starts out at: a usual practice. */
private const val DEFAULT_LENGTH = 30
