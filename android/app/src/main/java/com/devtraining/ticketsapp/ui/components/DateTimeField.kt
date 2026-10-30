package com.devtraining.ticketsapp.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.devtraining.ticketsapp.ui.formatDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Read-only field for a date and time, such as when an event starts. Tapping it opens a date picker, then a time
 * picker; [onValueChange] gets the chosen moment in the phone's time zone. Empty until a value is picked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeField(
    value: Instant?,
    onValueChange: (Instant) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val zone = ZoneId.systemDefault()
    var showDatePicker by remember { mutableStateOf(false) }
    // Set when a date has been picked; the time picker is shown until a time is picked too.
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }

    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value?.let { formatDate(it.toString()) } ?: "",
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // A read-only text field still takes the tap, so a layer on top opens the picker instead.
        Box(Modifier.matchParentSize().clickable(enabled = enabled) { showDatePicker = true })
    }

    if (showDatePicker) {
        // The date picker works in UTC midnight millis, whatever the phone's time zone.
        val initialDate = (value ?: Instant.now()).atZone(zone).toLocalDate()
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                LinkButton(
                    "Next",
                    onClick = {
                        pickedDate = dateState.selectedDateMillis?.let {
                            Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        }
                        showDatePicker = false
                    },
                    enabled = dateState.selectedDateMillis != null,
                )
            },
            dismissButton = { LinkButton("Cancel", onClick = { showDatePicker = false }) },
        ) {
            DatePicker(dateState)
        }
    }

    pickedDate?.let { date ->
        val initialTime = value?.atZone(zone)?.toLocalTime()
        val timeState = rememberTimePickerState(
            initialHour = initialTime?.hour ?: DEFAULT_HOUR,
            initialMinute = initialTime?.minute ?: 0,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { pickedDate = null },
            title = { Text("Pick a time") },
            text = { TimePicker(timeState) },
            confirmButton = {
                LinkButton("OK", onClick = {
                    onValueChange(date.atTime(timeState.hour, timeState.minute).atZone(zone).toInstant())
                    pickedDate = null
                })
            },
            dismissButton = { LinkButton("Cancel", onClick = { pickedDate = null }) },
        )
    }
}

/** Events usually start in the evening, so a new time starts at 19:00. */
private const val DEFAULT_HOUR = 19
