package com.antakih.taskpen.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val activeTags by viewModel.activeTags.collectAsState()
    var showAddSubtask by remember { mutableStateOf(false) }
    var newSubtaskTitle by remember { mutableStateOf("") }

    val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val dateString = task.dueDate?.let {
        if (task.hasSpecificTime) "${dateFormat.format(Date(it))} • ${timeFormat.format(Date(it))}"
        else dateFormat.format(Date(it))
    } ?: "Sin fecha"

    val assignedTag = activeTags.find { it.id == task.subcategoryId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(task.title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
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
                        Column {
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
