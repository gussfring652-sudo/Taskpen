package com.antakih.taskpen.ui.screens

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antakih.taskpen.data.local.entities.CategoryEntity
import com.antakih.taskpen.data.local.entities.TaskEntity
import com.antakih.taskpen.ui.viewmodel.TaskViewModel
import com.antakih.taskpen.ui.viewmodel.ViewContext
import java.util.Calendar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: TaskViewModel) {
    val activeContext by viewModel.activeContext.collectAsState()
    val filterState by viewModel.filterState.collectAsState()
    val allCategories by viewModel.allCategories.collectAsState()
    val recentCategories by viewModel.recentCategories.collectAsState()
    val allTags by viewModel.allTags.collectAsState()
    
    val activeTasks by viewModel.activeTasks.collectAsState()
    val deletedTasks by viewModel.deletedTasks.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    var showAllCategoriesSheet by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showTagsDialog by remember { mutableStateOf(false) }
    var textInputValue by remember { mutableStateOf("") }
    
    // Bottom sheets for actions
    var showDrawingSheet by remember { mutableStateOf(false) }
    var showManualTaskSheet by remember { mutableStateOf(false) }

    // Derive displayed tasks based on ViewContext and Filters
    val displayedTasks = remember(activeContext, filterState, activeTasks, deletedTasks) {
        val baseTasks = when (activeContext) {
            is ViewContext.Trash -> deletedTasks
            is ViewContext.General -> activeTasks
            is ViewContext.Category -> activeTasks.filter { it.categoryId == (activeContext as ViewContext.Category).categoryId }
            is ViewContext.Completed -> activeTasks.filter { it.isCompleted }
            is ViewContext.Important -> activeTasks.filter { it.isImportant && !it.isCompleted }
            is ViewContext.Today -> activeTasks.filter { 
                !it.isCompleted && it.dueDate?.let { date -> 
                    val cal = Calendar.getInstance()
                    val todayStart = cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
                    val todayEnd = cal.apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59) }.timeInMillis
                    date in todayStart..todayEnd
                } == true
            }
            is ViewContext.Tomorrow -> activeTasks.filter { 
                !it.isCompleted && it.dueDate?.let { date -> 
                    val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
                    val tomStart = cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
                    val tomEnd = cal.apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59) }.timeInMillis
                    date in tomStart..tomEnd
                } == true
            }
            is ViewContext.Postponed -> activeTasks.filter { 
                !it.isCompleted && it.dueDate?.let { date -> 
                    val cal = Calendar.getInstance()
                    val todayStart = cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
                    date < todayStart
                } == true
            }
        }
        
        // Exclude completed from standard views unless context is Completed
        val filteredForCompletion = if (activeContext !is ViewContext.Completed && activeContext !is ViewContext.Trash) {
            baseTasks.filter { !it.isCompleted }
        } else baseTasks

        val finalFiltered = filteredForCompletion.filter { task ->
            val matchesTag = filterState.selectedTagIds.isEmpty() || filterState.selectedTagIds.contains(task.subcategoryId)
            val matchesImportant = !filterState.showOnlyImportant || task.isImportant
            matchesTag && matchesImportant
        }

        if (filterState.sortByDueDate) {
            finalFiltered.sortedWith(compareBy<TaskEntity> { it.dueDate == null }.thenBy { it.dueDate })
        } else {
            finalFiltered.sortedByDescending { it.createdAt }
        }
    }

    val title = when (activeContext) {
        is ViewContext.General -> "General"
        is ViewContext.Today -> "Hoy"
        is ViewContext.Tomorrow -> "Mañana"
        is ViewContext.Postponed -> "Pospuestas"
        is ViewContext.Important -> "Importantes"
        is ViewContext.Completed -> "Completadas"
        is ViewContext.Trash -> "Papelera"
        is ViewContext.Category -> allCategories.find { it.id == (activeContext as ViewContext.Category).categoryId }?.name ?: "Categoría"
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                Spacer(Modifier.height(16.dp))
                Text("Taskpen", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.headlineMedium)
                Divider()
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Inbox, contentDescription = null) },
                    label = { Text("General") },
                    selected = activeContext is ViewContext.General,
                    onClick = { viewModel.setContext(ViewContext.General); scope.launch { drawerState.close() } }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Today, contentDescription = null) },
                    label = { Text("Hoy") },
                    selected = activeContext is ViewContext.Today,
                    onClick = { viewModel.setContext(ViewContext.Today); scope.launch { drawerState.close() } }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Event, contentDescription = null) },
                    label = { Text("Mañana") },
                    selected = activeContext is ViewContext.Tomorrow,
                    onClick = { viewModel.setContext(ViewContext.Tomorrow); scope.launch { drawerState.close() } }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                    label = { Text("Pospuestas") },
                    selected = activeContext is ViewContext.Postponed,
                    onClick = { viewModel.setContext(ViewContext.Postponed); scope.launch { drawerState.close() } }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Star, contentDescription = null) },
                    label = { Text("Importantes") },
                    selected = activeContext is ViewContext.Important,
                    onClick = { viewModel.setContext(ViewContext.Important); scope.launch { drawerState.close() } }
                )
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
                    label = { Text("Completadas") },
                    selected = activeContext is ViewContext.Completed,
                    onClick = { viewModel.setContext(ViewContext.Completed); scope.launch { drawerState.close() } }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    label = { Text("Papelera") },
                    selected = activeContext is ViewContext.Trash,
                    onClick = { viewModel.setContext(ViewContext.Trash); scope.launch { drawerState.close() } }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menú")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showFilterDialog = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filtros")
                        }
                        IconButton(onClick = { showTagsDialog = true }) {
                            Icon(Icons.Default.Label, contentDescription = "Etiquetas")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                )
            },
            bottomBar = {
                BottomAppBar(
                    modifier = Modifier.padding(WindowInsets.ime.asPaddingValues()),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    OutlinedTextField(
                        value = textInputValue,
                        onValueChange = { textInputValue = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Escribe una tarea...") },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = {
                                viewModel.processText(textInputValue)
                                textInputValue = ""
                            }) {
                                Icon(Icons.Default.Send, contentDescription = "Agregar")
                            }
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    FloatingActionButton(
                        onClick = { showManualTaskSheet = true },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Manual")
                    }
                    Spacer(Modifier.width(8.dp))
                    FloatingActionButton(
                        onClick = { showDrawingSheet = true },
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(Icons.Default.Create, contentDescription = "Escribir con S-Pen")
                    }
                }
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                // Recent Categories Row
                if (recentCategories.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(recentCategories) { cat ->
                            InputChip(
                                selected = (activeContext as? ViewContext.Category)?.categoryId == cat.id,
                                onClick = { if ((activeContext as? ViewContext.Category)?.categoryId == cat.id) viewModel.setContext(ViewContext.General) else viewModel.setContext(ViewContext.Category(cat.id)) },
                                label = { Text(cat.name) }
                            )
                        }
                        item {
                            InputChip(
                                selected = false,
                                onClick = { showAllCategoriesSheet = true },
                                label = { Text("Todas...") },
                                trailingIcon = { Icon(Icons.Default.Folder, null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                    }
                } else {
                    Button(onClick = { showAllCategoriesSheet = true }, modifier = Modifier.padding(16.dp)) {
                        Text("Ver Categorías")
                    }
                }

                // Task List
                if (displayedTasks.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No hay tareas aquí.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                        items(displayedTasks, key = { it.id }) { task ->
                            TaskCard(
                                task = task,
                                tags = allTags,
                                onComplete = { viewModel.completeTask(task.id) },
                                onToggleImportant = { viewModel.toggleTaskImportance(task.id, !task.isImportant) }
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheets and Dialogs instances...
    if (showAllCategoriesSheet) {
        AllCategoriesSheet(
            allCategories = allCategories,
            onDismiss = { showAllCategoriesSheet = false },
            onCategorySelected = { id -> 
                viewModel.setContext(ViewContext.Category(id))
                showAllCategoriesSheet = false
            },
            onCreateCategory = { name, color -> 
                viewModel.createCategory(name, color) 
            }
        )
    }

    if (showFilterDialog) {
        FilterDialog(
            tags = allTags,
            currentState = filterState,
            onDismiss = { showFilterDialog = false },
            onApply = { viewModel.updateFilterState(it) }
        )
    }

    if (showTagsDialog) {
        TagsDialog(
            tags = allTags,
            onDismiss = { showTagsDialog = false },
            onAddTag = { name, aliases -> 
                val currentCategory = (activeContext as? ViewContext.Category)?.categoryId
                if (currentCategory != null) {
                    viewModel.createTag(currentCategory, name, aliases)
                }
            }
        )
    }

    if (showDrawingSheet) {
        ModalBottomSheet(onDismissRequest = { showDrawingSheet = false }, modifier = Modifier.fillMaxHeight(0.6f)) {
            DrawingScreen(viewModel = viewModel, onFinished = { showDrawingSheet = false })
        }
    }
    
    if (showManualTaskSheet) {
        ManualTaskSheet(
            allCategories = allCategories,
            allTags = allTags,
            initialCategoryId = (activeContext as? ViewContext.Category)?.categoryId,
            onDismiss = { showManualTaskSheet = false },
            onSave = { task -> 
                viewModel.saveTasks(listOf(task)) 
            }
        )
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
                HorizontalDivider()
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualTaskSheet(
    allCategories: List<CategoryEntity>,
    allTags: List<com.antakih.taskpen.data.local.entities.SubjectEntity>,
    initialCategoryId: String?,
    onDismiss: () -> Unit,
    onSave: (TaskEntity) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isImportant by remember { mutableStateOf(false) }
    var selectedCategoryId by remember { mutableStateOf(initialCategoryId) }
    var selectedTagId by remember { mutableStateOf<String?>(null) }
    
    // For date picking (simplified for now as strings or just leave empty)
    // In a real app we'd use DatePicker
    
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState())) {
            Text("Crear Tarea", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Título de la Tarea") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Descripción (opcional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            Spacer(Modifier.height(8.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isImportant, onCheckedChange = { isImportant = it })
                Text("Marcar como Importante (★)")
            }
            
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val task = TaskEntity(
                            id = java.util.UUID.randomUUID().toString(),
                            categoryId = selectedCategoryId,
                            subcategoryId = selectedTagId,
                            parentTaskId = null,
                            title = title.trim(),
                            description = description.trim().takeIf { it.isNotBlank() },
                            createdAt = System.currentTimeMillis(),
                            dueDate = null,
                            hasSpecificTime = false,
                            isCompleted = false,
                            isImportant = isImportant,
                            isDeleted = false,
                            calendarEventId = null
                        )
                        onSave(task)
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Guardar Tarea")
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllCategoriesSheet(
    allCategories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onCategorySelected: (String) -> Unit,
    onCreateCategory: (String, String) -> Unit
) {
    var showCreateForm by remember { mutableStateOf(false) }
    var newCatName by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            if (showCreateForm) {
                Text("Nueva Categoría", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = newCatName,
                    onValueChange = { newCatName = it },
                    label = { Text("Nombre") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { showCreateForm = false }) { Text("Cancelar") }
                    Button(onClick = {
                        if (newCatName.isNotBlank()) {
                            onCreateCategory(newCatName.trim(), "#6200EE")
                            showCreateForm = false
                            onDismiss()
                        }
                    }) { Text("Crear") }
                }
            } else {
                Text("Todas las categorías", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.fillMaxHeight(0.5f)) {
                    items(allCategories, key = { it.id }) { cat ->
                        ListItem(
                            headlineContent = { Text(cat.name) },
                            modifier = Modifier.clickable { onCategorySelected(cat.id) }
                        )
                    }
                    item {
                        ListItem(
                            headlineContent = { Text("Crear nueva categoría...", color = MaterialTheme.colorScheme.primary) },
                            leadingContent = { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.clickable { showCreateForm = true }
                        )
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
