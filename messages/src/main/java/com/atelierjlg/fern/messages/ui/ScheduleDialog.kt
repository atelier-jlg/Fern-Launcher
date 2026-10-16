package com.atelierjlg.fern.messages.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.atelierjlg.fern.ui.kit.Pill
import com.atelierjlg.fern.ui.theme.Fern
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Programmer un message : raccourcis (ce soir, demain matin…) ou date et heure au choix. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleDialog(onDismiss: () -> Unit, onPick: (Long) -> Unit, canExact: Boolean, onAllowExact: () -> Unit) {
    val c = Fern.colors
    val zone = ZoneId.systemDefault()
    var step by remember { mutableStateOf(0) } // 0 = raccourcis, 1 = date, 2 = heure
    var pickedDate by remember { mutableStateOf(LocalDate.now()) }

    fun at(day: LocalDate, h: Int, m: Int = 0) = day.atTime(h, m).atZone(zone).toInstant().toEpochMilli()
    val today = LocalDate.now()
    val now = System.currentTimeMillis()

    when (step) {
        0 -> AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = c.mousse,
            title = { Text("Envoyer plus tard", style = Fern.type.corps, color = c.creme) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Dans 1 heure", false) { onPick(now + 3_600_000L) }
                    if (LocalTime.now().hour < 19) Pill("Ce soir à 20:00", false) { onPick(at(today, 20)) }
                    Pill("Demain à 08:00", false) { onPick(at(today.plusDays(1), 8)) }
                    Pill("Demain à 12:00", false) { onPick(at(today.plusDays(1), 12)) }
                    Pill("Choisir la date et l'heure…", true) { step = 1 }
                    if (!canExact) {
                        Text(
                            "Pour partir à la minute près, autorise les « alarmes exactes » pour Fern Messages.",
                            style = Fern.type.nomApp, color = c.roseCarmin, modifier = Modifier.padding(top = 6.dp),
                        )
                        Pill("Autoriser", false, onClick = onAllowExact)
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Annuler", color = c.lichen) } },
        )
        1 -> {
            val state = rememberDatePickerState(
                initialSelectedDateMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            )
            DatePickerDialog(
                onDismissRequest = onDismiss,
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { pickedDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                        step = 2
                    }) { Text("Suivant", color = c.pistache) }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler", color = c.lichen) } },
                colors = DatePickerDefaults.colors(containerColor = c.mousse),
            ) {
                DatePicker(
                    state,
                    colors = DatePickerDefaults.colors(
                        containerColor = c.mousse,
                        selectedDayContainerColor = c.pistache,
                        selectedDayContentColor = c.nuit,
                        todayDateBorderColor = c.pistache,
                        todayContentColor = c.pistache,
                        dayContentColor = c.creme,
                        weekdayContentColor = c.lichen,
                        titleContentColor = c.lichen,
                        headlineContentColor = c.creme,
                        navigationContentColor = c.creme,
                        yearContentColor = c.creme,
                        selectedYearContainerColor = c.pistache,
                        selectedYearContentColor = c.nuit,
                    ),
                )
            }
        }
        else -> {
            val state = rememberTimePickerState(initialHour = (LocalTime.now().hour + 1) % 24, initialMinute = 0, is24Hour = true)
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = c.mousse,
                title = { Text("À quelle heure ?", style = Fern.type.corps, color = c.creme) },
                text = {
                    TimePicker(
                        state,
                        colors = TimePickerDefaults.colors(
                            clockDialColor = c.lierre,
                            clockDialSelectedContentColor = c.nuit,
                            clockDialUnselectedContentColor = c.creme,
                            selectorColor = c.pistache,
                            containerColor = c.mousse,
                            timeSelectorSelectedContainerColor = c.pistache,
                            timeSelectorUnselectedContainerColor = c.lierre,
                            timeSelectorSelectedContentColor = c.nuit,
                            timeSelectorUnselectedContentColor = c.creme,
                        ),
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val time = at(pickedDate, state.hour, state.minute)
                        if (time > System.currentTimeMillis()) onPick(time) else onDismiss()
                    }) { Text("Programmer", color = c.pistache) }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler", color = c.lichen) } },
            )
        }
    }
}
