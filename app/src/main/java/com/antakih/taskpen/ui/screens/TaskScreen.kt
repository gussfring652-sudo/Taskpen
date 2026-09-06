package com.antakih.taskpen.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
    val activeTags by viewModel.activeTags.collectAsState()
    val filterState by viewModel.filterState.collectAsState()

    // Filtrar tareas raíz (sin padre) por categoría seleccionada
    val categoryTasks = if (category == null) {
        allTasks.filter { it.parentTaskId == null && it.categoryId == null }
    } else {
        allTasks.filter { it.parentTaskId == null && it.categoryId == category.id }
    }

    // Aplicar filtros
    val filteredTasks = categoryTasks.filter { task ->
        val matchesTag = filterState.selectedTagIds.isEmpty() || filterState.selectedTagIds.contains(task.subcategoryId)
        val matchesImportant = !filterState.showOnlyImportant || task.isImportant
        matchesTag && matchesImportant
    }

    // Aplicar ordenamiento
    val tasks = if (filterState.sortByDueDate) {
        filteredTasks.sortedWith(compareBy<TaskEntity> { it.dueDate == null }.thenBy { it.dueDate })
    } else {
        filteredTasks.sortedByDescending { it.createdAt }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showBottomSheet by remember { mutableStateOf(false) }
    var selectedTask by remember { mutableStateOf<TaskEntity?>(null) }
    var showTagsDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(category?.name ?: "General", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { showFilterDialog = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filtros y Orden")
                    }
                    IconButton(onClick = { showTagsDialog = true }) {
                        Icon(Icons.Default.Label, contentDescription = "Administrar Etiquetas")
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
                        tags = activeTags,
                        onComplete = { viewModel.completeTask(task.id) },
                        onToggleImportant = { viewModel.toggleTaskImportance(task.id, !task.isImportant) },
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

        if (showFilterDialog) {
            FilterDialog(
                tags = activeTags,
                currentState = filterState,
                onDismiss = { showFilterDialog = false },
                onApply = { newState ->
                    viewModel.updateFilterState(newState)
                }
            )
        }

        if (showTagsDialog) {
            TagsDialog(
                tags = activeTags,
                onDismiss = { showTagsDialog = false },
                onAddTag = { name, aliases ->
                    viewModel.createTag(category?.id ?: "", name, aliases)
                }
            )
        }
    }
}

@Composable
fun TaskCard(
    task: TaskEntity,
    tags: List<com.antakih.taskpen.data.local.entities.SubjectEntity>,
    onComplete: () -> Unit,
    onToggleImportant: () -> Unit,
    onClick: () -> Unit = {}
) {
    val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val dateString = task.dueDate?.let {
        if (task.hasSpecificTime) "${dateFormat.format(Date(it))} • ${timeFormat.format(Date(it))}"
        else dateFormat.format(Date(it))
    } ?: "Sin fecha"

    val assignedTag = tags.find { it.id == task.subcategoryId }

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
                if (assignedTag != null) {
                    androidx.compose.material3.Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                    ) {
                        Text(
                            text = assignedTag.fullName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = "Vence: $dateString",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onToggleImportant) {
                Icon(
                    imageVector = if (task.isImportant) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = "Marcar como importante",
                    tint = if (task.isImportant) androidx.compose.ui.graphics.Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun TagsDialog(
    tags: List<com.antakih.taskpen.data.local.entities.SubjectEntity>,
    onDismiss: () -> Unit,
    onAddTag: (name: String, aliases: List<String>) -> Unit
) {
    var newTagName by remember { mutableStateOf("") }
    var newTagAliases by remember { mutableStateOf("") }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Administrar Etiquetas", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = newTagName,
                    onValueChange = { newTagName = it },
                    label = { Text("Nombre de Etiqueta (Ej: Robótica)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newTagAliases,
                    onValueChange = { newTagAliases = it },
                    label = { Text("Alias separados por coma (Ej: rb, robot)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (newTagName.isNotBlank()) {
                            val aliasesList = newTagAliases.split(",")
                                .map { it.trim().lowercase() }
                                .filter { it.isNotEmpty() }
                            onAddTag(newTagName.trim(), aliasesList)
                            newTagName = ""
                            newTagAliases = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Añadir Etiqueta")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("Etiquetas actuales:", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyColumn(modifier = Modifier.fillMaxHeight(0.4f)) {
                    items(tags, key = { it.id }) { tag ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = tag.fullName,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Alias: " + tag.aliases.joinToString(", "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Cerrar")
                }
            }
        }
    }
}

@Composable
fun FilterDialog(
    tags: List<com.antakih.taskpen.data.local.entities.SubjectEntity>,
    currentState: com.antakih.taskpen.ui.viewmodel.FilterState,
    onDismiss: () -> Unit,
    onApply: (com.antakih.taskpen.ui.viewmodel.FilterState) -> Unit
) {
    var sortByDueDate by remember { mutableStateOf(currentState.sortByDueDate) }
    var selectedTagIds by remember { mutableStateOf(currentState.selectedTagIds) }
    var showOnlyImportant by remember { mutableStateOf(currentState.showOnlyImportant) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Filtros y Orden", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))

                Text("Ordenar por:", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = !sortByDueDate, onClick = { sortByDueDate = false })
                    Text("Fecha de Creación")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = sortByDueDate, onClick = { sortByDueDate = true })
                    Text("Fecha de Vencimiento")
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))

                Text("Filtros:", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = showOnlyImportant, onCheckedChange = { showOnlyImportant = it })
                    Text("Solo Importantes (★)")
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("Etiquetas:", style = MaterialTheme.typography.bodyMedium)
                LazyColumn(modifier = Modifier.fillMaxHeight(0.3f)) {
                    items(tags, key = { it.id }) { tag ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = selectedTagIds.contains(tag.id),
                                onCheckedChange = { isChecked ->
                                    val newSet = selectedTagIds.toMutableSet()
                                    if (isChecked) newSet.add(tag.id) else newSet.remove(tag.id)
                                    selectedTagIds = newSet
                                }
                            )
                            Text(tag.fullName)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        onApply(com.antakih.taskpen.ui.viewmodel.FilterState(
                            sortByDueDate = sortByDueDate,
                            selectedTagIds = selectedTagIds,
                            showOnlyImportant = showOnlyImportant
                        ))
                        onDismiss()
                    }) {
                        Text("Aplicar")
                    }
                }
            }
        }
    }
}
