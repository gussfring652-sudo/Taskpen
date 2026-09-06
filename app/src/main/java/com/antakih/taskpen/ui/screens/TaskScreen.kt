package com.antakih.taskpen.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antakih.taskpen.data.local.entities.CategoryEntity
import com.antakih.taskpen.data.local.entities.TaskEntity
import com.antakih.taskpen.ui.viewmodel.TaskViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskScreen(
    viewModel: TaskViewModel,
    category: CategoryEntity?,       // null = "General"
    onBack: () -> Unit
) {
    val allTasks by viewModel.pendingTasks.collectAsState()

    // Filtrar tareas raíz (sin padre) por categoría seleccionada
    val tasks = if (category == null) {
        allTasks.filter { it.parentTaskId == null && it.categoryId == null }
    } else {
        allTasks.filter { it.parentTaskId == null && it.categoryId == category.id }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showBottomSheet by remember { mutableStateOf(false) }
    var selectedTask by remember { mutableStateOf<TaskEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(category?.name ?: "General", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showBottomSheet = true }) {
                Icon(Icons.Default.Create, contentDescription = "Abrir lienzo")
            }
        }
    ) { padding ->
        if (selectedTask != null) {
            TaskDetailScreen(
                task = selectedTask!!,
                viewModel = viewModel,
                onBack = { selectedTask = null }
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (tasks.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Sin tareas aún.\nUsa el bolígrafo para escribir.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                items(tasks, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        onComplete = { viewModel.completeTask(task.id) },
                        onClick = { selectedTask = task }
                    )
                }
            }
        }

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState,
                modifier = Modifier.fillMaxHeight(0.6f)
            ) {
                DrawingScreen(
                    viewModel = viewModel,
                    onFinished = { showBottomSheet = false }
                )
            }
        }
    }
}

@Composable
fun TaskCard(task: TaskEntity, onComplete: () -> Unit, onClick: () -> Unit = {}) {
    val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val dateString = task.dueDate?.let {
        if (task.hasSpecificTime) "${dateFormat.format(Date(it))} • ${timeFormat.format(Date(it))}"
        else dateFormat.format(Date(it))
    } ?: "Sin fecha"

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = task.isCompleted, onCheckedChange = { onComplete() })
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = task.title, style = MaterialTheme.typography.titleMedium)
                if (task.subcategoryId != null) {
                    Text(
                        text = task.subcategoryId,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "Vence: $dateString",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
