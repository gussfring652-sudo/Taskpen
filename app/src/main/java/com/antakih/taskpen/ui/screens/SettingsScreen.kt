package com.antakih.taskpen.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.antakih.taskpen.data.local.SettingsManager
import com.antakih.taskpen.notifications.SummaryScheduler
import com.antakih.taskpen.ui.viewmodel.TaskViewModel
import kotlinx.coroutines.launch

/**
 * Pantalla de Configuración expandida.
 * Incluye:
 * - Toggle de sensibilidad a mayúsculas en etiquetas
 * - Configuración de horarios de resúmenes matutino y nocturno (con TimePicker)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: TaskViewModel,
    settingsManager: SettingsManager,
    summaryScheduler: SummaryScheduler,
    onDismiss: () -> Unit
) {
    val isCaseSensitive by viewModel.isCaseSensitiveTags.collectAsState()
    val morningHour by viewModel.morningSummaryHour.collectAsState(initial = 8)
    val morningMinute by viewModel.morningSummaryMinute.collectAsState(initial = 0)
    val eveningHour by viewModel.eveningSummaryHour.collectAsState(initial = 21)
    val eveningMinute by viewModel.eveningSummaryMinute.collectAsState(initial = 0)

    var showMorningTimePicker by remember { mutableStateOf(false) }
    var showEveningTimePicker by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Configuración") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
        ) {
            // Sección: General
            Text(
                "GENERAL",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sensibilidad a mayúsculas", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Requiere coincidencia exacta en etiquetas.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                        Switch(
                            checked = isCaseSensitive,
                            onCheckedChange = { viewModel.setCaseSensitiveTags(it) }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    val defaultPriority by viewModel.defaultTaskPriority.collectAsState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Prioridad por defecto", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Para tareas sin prioridad explícita",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                        
                        var expanded by remember { mutableStateOf(false) }
                        val priorities = listOf("Baja", "Media", "Alta")
                        
                        Box {
                            TextButton(onClick = { expanded = true }) {
                                Text(priorities[defaultPriority])
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                priorities.forEachIndexed { index, label ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            viewModel.setDefaultTaskPriority(index)
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Sección: Recordatorios
            Text(
                "RECORDATORIOS",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    // Resumen matutino
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Resumen matutino", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Notificación con las tareas del día",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                        TextButton(onClick = { showMorningTimePicker = true }) {
                            Text(
                                formatTime(morningHour, morningMinute),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // Resumen nocturno
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Resumen nocturno", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Notificación con las tareas pendientes",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                        TextButton(onClick = { showEveningTimePicker = true }) {
                            Text(
                                formatTime(eveningHour, eveningMinute),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }
    }

    // TimePicker para resumen matutino
    if (showMorningTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = morningHour,
            initialMinute = morningMinute
        )
        AlertDialog(
            onDismissRequest = { showMorningTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val h = timePickerState.hour
                    val m = timePickerState.minute
                    scope.launch {
                        settingsManager.setMorningSummaryTime(h, m)
                        summaryScheduler.scheduleMorningSummary(h, m)
                    }
                    showMorningTimePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showMorningTimePicker = false }) { Text("Cancelar") }
            },
            title = { Text("Resumen matutino") },
            text = { TimePicker(state = timePickerState) }
        )
    }

    // TimePicker para resumen nocturno
    if (showEveningTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = eveningHour,
            initialMinute = eveningMinute
        )
        AlertDialog(
            onDismissRequest = { showEveningTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val h = timePickerState.hour
                    val m = timePickerState.minute
                    scope.launch {
                        settingsManager.setEveningSummaryTime(h, m)
                        summaryScheduler.scheduleEveningSummary(h, m)
                    }
                    showEveningTimePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showEveningTimePicker = false }) { Text("Cancelar") }
            },
            title = { Text("Resumen nocturno") },
            text = { TimePicker(state = timePickerState) }
        )
    }
}

private fun formatTime(hour: Int, minute: Int): String {
    val period = if (hour < 12) "AM" else "PM"
    val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
    return String.format("%d:%02d %s", displayHour, minute, period)
}
