package com.antakih.taskpen.ui.screens

import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antakih.taskpen.data.local.entities.TaskEntity
import com.antakih.taskpen.ui.viewmodel.TaskViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    task: TaskEntity,
    viewModel: TaskViewModel,
    onBack: () -> Unit
) {
    val subtasks by viewModel.getSubtasks(task.id).collectAsState(initial = emptyList())
    val allTags by viewModel.allTags.collectAsState()
    var showAddSubtask by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var newSubtaskTitle by remember { mutableStateOf("") }

    val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val dateString = task.dueDate?.let {
        if (task.hasSpecificTime) "${dateFormat.format(Date(it))} • ${timeFormat.format(Date(it))}"
        else dateFormat.format(Date(it))
    } ?: "Sin fecha"

    val assignedTag = task.subcategoryId?.let { id -> allTags.find { it.id == id } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(task.title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar")
                    }
                    IconButton(onClick = { 
                        viewModel.moveToTrash(task.id)
                        onBack()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddSubtask = true },
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "Agregar subtarea")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Checkbox(
                            checked = task.isCompleted,
                            onCheckedChange = { viewModel.completeTask(task.id) },
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = task.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            if (assignedTag != null) {
                                Spacer(Modifier.height(8.dp))
                                androidx.compose.material3.Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = assignedTag.fullName,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Vence: $dateString",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!task.description.isNullOrBlank()) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = task.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        IconButton(
                            onClick = { viewModel.toggleTaskImportance(task.id, !task.isImportant) },
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (task.isImportant) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = "Marcar como importante",
                                tint = if (task.isImportant) androidx.compose.ui.graphics.Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (subtasks.isNotEmpty()) {
                item {
                    Text(
                        text = "Subtareas (${subtasks.count { it.isCompleted }}/${subtasks.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            items(subtasks, key = { it.id }) { subtask ->
                SubtaskRow(
                    subtask = subtask,
                    onComplete = { viewModel.completeTask(subtask.id) }
                )
            }

            item { Spacer(Modifier.height(80.dp)) }
        }

        if (showAddSubtask) {
            AlertDialog(
                onDismissRequest = { showAddSubtask = false; newSubtaskTitle = "" },
                title = { Text("Nueva subtarea") },
                text = {
                    OutlinedTextField(
                        value = newSubtaskTitle,
                        onValueChange = { newSubtaskTitle = it },
                        label = { Text("Descripción") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        if (newSubtaskTitle.isNotBlank()) {
                            val subtask = TaskEntity(
                                id = UUID.randomUUID().toString(),
                                title = newSubtaskTitle.trim(),
                                description = null,
                                categoryId = task.categoryId,
                                subcategoryId = task.subcategoryId,
                                parentTaskId = task.id,
                                createdAt = System.currentTimeMillis(),
                                dueDate = task.dueDate,
                                hasSpecificTime = false,
                                isCompleted = false,
                                calendarEventId = null
                            )
                            viewModel.saveTasks(listOf(subtask))
                            newSubtaskTitle = ""
                            showAddSubtask = false
                        }
                    }) { Text("Agregar") }
                },
                dismissButton = {
                    TextButton(onClick = { showAddSubtask = false; newSubtaskTitle = "" }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        if (showEditDialog) {
            val allCategories by viewModel.allCategories.collectAsState()
            EditTaskDialog(
                task = task,
                allCategories = allCategories,
                allTags = allTags,
                onDismiss = { showEditDialog = false },
                onSave = { title, desc, date, catId, tagId ->
                    viewModel.updateTaskDetails(task.id, title, desc, date, catId, tagId)
                },
                onSaveReminder = { priority, offsetMinutes, reminderMode, customCascadeInterval, recurrenceInterval, maxOccurrences, endDate ->
                    viewModel.updateTaskReminder(task.id, priority, offsetMinutes, reminderMode, customCascadeInterval, recurrenceInterval, maxOccurrences, endDate)
                }
            )
        }
    }
}

@Composable
private fun SubtaskRow(subtask: TaskEntity, onComplete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = subtask.isCompleted,
            onCheckedChange = { onComplete() }
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = subtask.title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (subtask.isCompleted)
                MaterialTheme.colorScheme.onSurfaceVariant
            else
                MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTaskDialog(
    task: com.antakih.taskpen.data.local.entities.TaskEntity,
    allCategories: List<com.antakih.taskpen.data.local.entities.CategoryEntity>,
    allTags: List<com.antakih.taskpen.data.local.entities.SubjectEntity>,
    onDismiss: () -> Unit,
    onSave: (title: String, desc: String, dueDate: Long?, categoryId: String?, subcategoryId: String?) -> Unit,
    onSaveReminder: ((priority: Int, offsetMinutes: Int?, reminderMode: Int, customCascadeInterval: Int?, recurrenceInterval: Int?, maxOccurrences: Int?, endDate: Long?) -> Unit)? = null
) {
    var title by remember { mutableStateOf(task.title) }
    var description by remember { mutableStateOf(task.description ?: "") }
    var dueDateMillis by remember { mutableStateOf(task.dueDate) }
    var selectedCategoryId by remember { mutableStateOf(task.categoryId) }
    var selectedTagId by remember { mutableStateOf(task.subcategoryId) }
    var selectedPriority by remember { mutableIntStateOf(task.priority) }
    var selectedReminderMode by remember { mutableIntStateOf(task.reminderMode) }
    var customOffsetValue by remember { mutableStateOf(task.reminderOffsetMinutes) }
    var customCascadeValue by remember { mutableStateOf(task.customCascadeIntervalMinutes) }
    var customRecurrenceValue by remember { mutableStateOf(task.recurrenceIntervalMinutes) }
    var recurrenceMaxOccurrences by remember { mutableStateOf(task.recurrenceMaxOccurrences) }
    var recurrenceEndDate by remember { mutableStateOf(task.recurrenceEndDate) }
    var recurrenceEndType by remember { mutableIntStateOf(when {
        task.recurrenceMaxOccurrences != null -> 1
        task.recurrenceEndDate != null -> 2
        else -> 0
    }) }
    var showRecurrenceDatePicker by remember { mutableStateOf(false) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var tagExpanded by remember { mutableStateOf(false) }

    val dateString = dueDateMillis?.let { java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(it)) } ?: "Sin fecha y hora"

    val priorityLabels = listOf("Baja", "Media", "Alta")
    val priorityColors = listOf(
        androidx.compose.ui.graphics.Color(0xFF4CAF50), // Verde
        androidx.compose.ui.graphics.Color(0xFFFFC107), // Amarillo
        androidx.compose.ui.graphics.Color(0xFFF44336)  // Rojo
    )

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Text("Editar Tarea", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Vence: $dateString", modifier = Modifier.weight(1f))
                    TextButton(onClick = { showDatePicker = true }) { Text("Cambiar") }
                    if (dueDateMillis != null) {
                        IconButton(onClick = { dueDateMillis = null }) {
                            Icon(Icons.Default.Clear, contentDescription = "Quitar fecha")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // --- Prioridad ---
                Text("Prioridad", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    priorityLabels.forEachIndexed { index, label ->
                        FilterChip(
                            selected = selectedPriority == index,
                            onClick = { selectedPriority = index },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = priorityColors[index].copy(alpha = 0.2f),
                                selectedLabelColor = priorityColors[index]
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // --- Recordatorio ---
                if (dueDateMillis != null) {
                    Text("Recordatorio", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Modo de Recordatorio
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedReminderMode == 0,
                            onClick = { selectedReminderMode = 0 },
                            label = { Text("Puntual (1 vez)") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedReminderMode == 1,
                            onClick = { selectedReminderMode = 1 },
                            label = { Text("Cascada (Deadline)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    if (selectedReminderMode == 1) {
                        val cascadeDesc = when (selectedPriority) {
                            2 -> "7d → 3d → 12h antes"
                            1 -> "2d → 1d → 4h antes"
                            else -> "1d → 12h → 1h antes"
                        }
                        Text(
                            text = "Avisos múltiples: $cascadeDesc",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        com.antakih.taskpen.ui.components.ReminderOffsetPicker(
                            label = "Personalizar intervalo de cascada",
                            initialValueMinutes = task.customCascadeIntervalMinutes,
                            isCascadeMode = true,
                            onOffsetChanged = { customCascadeValue = it }
                        )
                    } else {
                        Text(
                            text = "Sonará exactamente en el momento de vencimiento.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        com.antakih.taskpen.ui.components.ReminderOffsetPicker(
                            label = "Avisar minutos/horas antes",
                            initialValueMinutes = task.reminderOffsetMinutes,
                            isCascadeMode = false,
                            onOffsetChanged = { customOffsetValue = it }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                if (dueDateMillis != null) {
                    Text("Recurrencia (Repetición periódica):", style = MaterialTheme.typography.titleMedium)
                    com.antakih.taskpen.ui.components.ReminderOffsetPicker(
                        label = "Repetir tarea",
                        initialValueMinutes = task.recurrenceIntervalMinutes,
                        isCascadeMode = true,
                        onOffsetChanged = { customRecurrenceValue = it }
                    )
                    
                    if (customRecurrenceValue != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Finalizar repetición:", style = MaterialTheme.typography.bodyMedium)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = recurrenceEndType == 0, onClick = { recurrenceEndType = 0; recurrenceMaxOccurrences = null; recurrenceEndDate = null }, label = { Text("Nunca") })
                            FilterChip(selected = recurrenceEndType == 1, onClick = { recurrenceEndType = 1; recurrenceMaxOccurrences = recurrenceMaxOccurrences ?: 5; recurrenceEndDate = null }, label = { Text("N veces") })
                            FilterChip(selected = recurrenceEndType == 2, onClick = { recurrenceEndType = 2; recurrenceEndDate = recurrenceEndDate ?: (System.currentTimeMillis() + 86400000L); recurrenceMaxOccurrences = null }, label = { Text("En fecha") })
                        }
                        if (recurrenceEndType == 1) {
                            OutlinedTextField(
                                value = recurrenceMaxOccurrences?.toString() ?: "",
                                onValueChange = { recurrenceMaxOccurrences = it.toIntOrNull() },
                                label = { Text("Número de repeticiones restantes") },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else if (recurrenceEndType == 2) {
                            val endStr = recurrenceEndDate?.let { java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(it)) } ?: "Seleccionar fecha"
                            Box(modifier = Modifier.fillMaxWidth().clickable { showRecurrenceDatePicker = true }) {
                                OutlinedTextField(
                                    value = endStr,
                                    onValueChange = {},
                                    readOnly = true,
                                    enabled = false,
                                    colors = TextFieldDefaults.colors(
                                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                        disabledContainerColor = Color.Transparent,
                                        disabledIndicatorColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    label = { Text("Fecha límite") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Categoría
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = allCategories.find { it.id == selectedCategoryId }?.name ?: "Global (Sin Categoría)",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Categoría") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { categoryExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Global (Sin Categoría)") },
                            onClick = { selectedCategoryId = null; categoryExpanded = false }
                        )
                        allCategories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name) },
                                onClick = { selectedCategoryId = cat.id; categoryExpanded = false }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Etiqueta
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = allTags.find { it.id == selectedTagId }?.fullName ?: "Ninguna",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Etiqueta") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { tagExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = tagExpanded,
                        onDismissRequest = { tagExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Ninguna") },
                            onClick = { selectedTagId = null; tagExpanded = false }
                        )
                        val tagsToDisplay = if (selectedCategoryId == null) allTags else allTags.filter { it.categoryId == selectedCategoryId || it.categoryId == null }
                        tagsToDisplay.forEach { tag ->
                            DropdownMenuItem(
                                text = { Text(tag.fullName) },
                                onClick = { selectedTagId = tag.id; tagExpanded = false }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Button(onClick = {
                        if (title.isNotBlank()) {
                            onSave(title.trim(), description.trim(), dueDateMillis, selectedCategoryId, selectedTagId)
                            onSaveReminder?.invoke(selectedPriority, customOffsetValue, selectedReminderMode, customCascadeValue, customRecurrenceValue, recurrenceMaxOccurrences, recurrenceEndDate)
                            onDismiss()
                        }
                    }) { Text("Guardar") }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dueDateMillis ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dueDateMillis = datePickerState.selectedDateMillis
                    showDatePicker = false
                    showTimePicker = true // Automatically open time picker after date
                }) { Text("Siguiente") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val cal = java.util.Calendar.getInstance()
        if (dueDateMillis != null) {
            cal.timeInMillis = dueDateMillis!!
        }
        val timePickerState = androidx.compose.material3.rememberTimePickerState(
            initialHour = cal.get(java.util.Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(java.util.Calendar.MINUTE)
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    cal.set(java.util.Calendar.HOUR_OF_DAY, timePickerState.hour)
                    cal.set(java.util.Calendar.MINUTE, timePickerState.minute)
                    dueDateMillis = cal.timeInMillis
                    showTimePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancelar") }
            },
            text = {
                androidx.compose.material3.TimePicker(state = timePickerState)
            }
        )
    }

    if (showRecurrenceDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = recurrenceEndDate ?: (System.currentTimeMillis() + 86400000L))
        DatePickerDialog(
            onDismissRequest = { showRecurrenceDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    recurrenceEndDate = datePickerState.selectedDateMillis
                    showRecurrenceDatePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showRecurrenceDatePicker = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
