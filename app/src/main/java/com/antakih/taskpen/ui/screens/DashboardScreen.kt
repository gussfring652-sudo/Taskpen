package com.antakih.taskpen.ui.screens

import kotlinx.coroutines.launch
import com.antakih.taskpen.ui.screens.TaskDetailScreen
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Clear
import java.util.Calendar
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antakih.taskpen.data.local.entities.CategoryEntity
import com.antakih.taskpen.data.local.entities.TaskEntity
import com.antakih.taskpen.ui.viewmodel.TaskViewModel
import com.antakih.taskpen.ui.viewmodel.ViewContext

sealed class MainPaneState {
    object TaskList : MainPaneState()
    data class TaskDetail(val task: com.antakih.taskpen.data.local.entities.TaskEntity) : MainPaneState()
    }


@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: TaskViewModel,
    onTaskClick: (TaskEntity) -> Unit = {}
) {
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
    var showSettingsFullScreen by remember { androidx.compose.runtime.mutableStateOf(false) }
    var mainPaneState by remember { mutableStateOf<MainPaneState>(MainPaneState.TaskList) }
    var quickViewTask by remember { mutableStateOf<com.antakih.taskpen.data.local.entities.TaskEntity?>(null) }
    var textInputValue by remember { mutableStateOf("") }
    
    var showDrawingSheet by remember { mutableStateOf(false) }
    var showManualTaskSheet by remember { mutableStateOf(false) }

    val displayedTasks = remember(activeContext, filterState, activeTasks, deletedTasks) {
        val baseTasks = when (activeContext) {
            is ViewContext.Trash -> deletedTasks
            is ViewContext.General -> activeTasks.filter { !it.isCompleted }
            is ViewContext.Category -> activeTasks.filter { !it.isCompleted && it.categoryId == (activeContext as ViewContext.Category).categoryId }
            is ViewContext.Completed -> activeTasks.filter { it.isCompleted }
            is ViewContext.Important -> activeTasks.filter { it.isImportant && !it.isCompleted }
            is ViewContext.Today -> activeTasks.filter { 
                !it.isCompleted && it.dueDate != null && android.text.format.DateUtils.isToday(it.dueDate) 
            }
            is ViewContext.Tomorrow -> activeTasks.filter {
                !it.isCompleted && it.dueDate != null && android.text.format.DateUtils.isToday(it.dueDate - 86400000)
            }
            is ViewContext.Postponed -> activeTasks.filter {
                !it.isCompleted && it.dueDate != null && it.dueDate < System.currentTimeMillis() && !android.text.format.DateUtils.isToday(it.dueDate)
            }
        }
        
        val filtered = baseTasks.filter { task ->
            val matchesSearch = if (filterState.searchQuery.isNotBlank()) {
                task.title.contains(filterState.searchQuery, ignoreCase = true) ||
                task.description?.contains(filterState.searchQuery, ignoreCase = true) == true
            } else true
            
            val matchesImportant = if (filterState.showOnlyImportant) task.isImportant else true
            val matchesCategory = if (filterState.selectedCategories.isNotEmpty()) filterState.selectedCategories.contains(task.categoryId) else true
            val matchesTags = if (filterState.selectedTags.isNotEmpty()) filterState.selectedTags.contains(task.subcategoryId) else true
            
            matchesSearch && matchesImportant && matchesCategory && matchesTags
        }
        
        if (filterState.sortByDueDate) {
            filtered.sortedBy { it.dueDate ?: Long.MAX_VALUE }
        } else {
            filtered.sortedByDescending { it.createdAt }
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

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 840

    val mainContent = @Composable {
        when (val state = mainPaneState) {
            is MainPaneState.TaskDetail -> {
                TaskDetailScreen(
                    task = state.task,
                    viewModel = viewModel,
                    onBack = { mainPaneState = MainPaneState.TaskList }
                )
            }

            is MainPaneState.TaskList -> {
        Scaffold(
            topBar = {
                if (!isLandscape) {
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
                }
            },
            bottomBar = {
                BottomAppBar(
                    modifier = Modifier.padding(WindowInsets.ime.asPaddingValues()),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    containerColor = if (isLandscape) Color.Transparent else BottomAppBarDefaults.containerColor
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
                        },
                        colors = TextFieldDefaults.colors()
                    )
                    Spacer(Modifier.width(8.dp))
                    FloatingActionButton(
                        onClick = { showManualTaskSheet = true },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Manual", tint = LocalContentColor.current)
                    }
                    Spacer(Modifier.width(8.dp))
                    FloatingActionButton(
                        onClick = { showDrawingSheet = true },
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(Icons.Default.Create, contentDescription = "Escribir con S-Pen", tint = LocalContentColor.current)
                    }
                }
            },
            containerColor = if (isLandscape) Color.Transparent else MaterialTheme.colorScheme.background
        ) { padding ->
            val actualPadding = if (isLandscape) PaddingValues(bottom = padding.calculateBottomPadding(), top = 16.dp, start = 16.dp, end = 16.dp) else padding
            Column(modifier = Modifier.padding(actualPadding).fillMaxSize()) {
                if (!isLandscape && recentCategories.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(recentCategories, key = { it.id }) { cat ->
                            InputChip(
                                selected = (activeContext as? ViewContext.Category)?.categoryId == cat.id,
                                onClick = { 
                                    if ((activeContext as? ViewContext.Category)?.categoryId == cat.id) viewModel.setContext(ViewContext.General) 
                                    else viewModel.setContext(ViewContext.Category(cat.id)) 
                                },
                                label = { Text(cat.name) }
                            )
                        }
                        item {
                            InputChip(
                                selected = false,
                                onClick = { showAllCategoriesSheet = true },
                                label = { Text("Todas...") }
                            )
                        }
                    }
                }

                if (displayedTasks.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(if (filterState.searchQuery.isNotBlank()) "No se encontraron tareas." else "No hay tareas en esta vista.")
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                        if (filterState.sortByDueDate) {
                            val grouped = displayedTasks.groupBy { task ->
                                if (task.dueDate == null) "Sin Fecha"
                                else if (android.text.format.DateUtils.isToday(task.dueDate)) "Hoy"
                                else if (android.text.format.DateUtils.isToday(task.dueDate - 86400000)) "Mañana"
                                else android.text.format.DateFormat.format("dd MMM yyyy", task.dueDate).toString()
                            }
                            grouped.forEach { (header, tasksInGroup) ->
                                item {
                                    Text(
                                        text = header,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                                items(tasksInGroup, key = { it.id }) { task ->
                                    TaskCard(
                                        task = task,
                                        tags = allTags,
                                        isTrashContext = activeContext is ViewContext.Trash,
                                        isCompletedContext = activeContext is ViewContext.Completed,
                                        onComplete = { viewModel.completeTask(task.id) },
                                        onUncomplete = { viewModel.uncompleteTask(task.id) },
                                        onToggleImportant = { viewModel.toggleTaskImportance(task.id, !task.isImportant) },
                                        onMoveToTrash = { viewModel.moveToTrash(task.id) },
                                        onRestore = { viewModel.restoreTask(task.id) },
                                        onDeletePermanently = { viewModel.permanentlyDeleteTask(task.id) },
                                        onClick = { mainPaneState = MainPaneState.TaskDetail(task) },
                                        onLongClick = { quickViewTask = task }
                                    )
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        } else {
                            items(displayedTasks, key = { it.id }) { task ->
                                TaskCard(
                                    task = task,
                                    tags = allTags,
                                    isTrashContext = activeContext is ViewContext.Trash,
                                    isCompletedContext = activeContext is ViewContext.Completed,
                                    onComplete = { viewModel.completeTask(task.id) },
                                    onUncomplete = { viewModel.uncompleteTask(task.id) },
                                    onToggleImportant = { viewModel.toggleTaskImportance(task.id, !task.isImportant) },
                                    onMoveToTrash = { viewModel.moveToTrash(task.id) },
                                    onRestore = { viewModel.restoreTask(task.id) },
                                    onDeletePermanently = { viewModel.permanentlyDeleteTask(task.id) },
                                    onClick = { mainPaneState = MainPaneState.TaskDetail(task) },
                                        onLongClick = { quickViewTask = task }
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
            }
        }
    }

    if (isLandscape) {
        Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            LeftLandscapePanel(
                activeContext = activeContext,
                filterState = filterState,
                allCategories = allCategories,
                onContextSelected = { viewModel.setContext(it) },
                onSearchChanged = { viewModel.updateFilterState(filterState.copy(searchQuery = it)) },
                onFilterClick = { showFilterDialog = true },
                onTagsClick = { showTagsDialog = true },
                onToggleImportantFilter = { viewModel.updateFilterState(filterState.copy(showOnlyImportant = !filterState.showOnlyImportant)) },
                onSettingsClick = { showSettingsFullScreen = true },
                onCategoryClick = { 
                    if ((activeContext as? ViewContext.Category)?.categoryId == it) viewModel.setContext(ViewContext.General) 
                    else viewModel.setContext(ViewContext.Category(it)) 
                },
                modifier = Modifier.width(360.dp).fillMaxHeight()
            )
            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.background)) {
                mainContent()
            }
        }
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(modifier = Modifier.width(300.dp).verticalScroll(rememberScrollState())) {
                    Spacer(Modifier.height(16.dp))
                    Text("Taskpen", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.headlineMedium)
                    HorizontalDivider()
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
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("Categorías", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleSmall)
                    allCategories.take(5).forEach { cat ->
                        NavigationDrawerItem(
                            label = { Text(cat.name) },
                            selected = (activeContext as? ViewContext.Category)?.categoryId == cat.id,
                            onClick = { viewModel.setContext(ViewContext.Category(cat.id)); scope.launch { drawerState.close() } }
                        )
                    }
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        label = { Text("Ver Categorías...") },
                        selected = false,
                        onClick = { showAllCategoriesSheet = true; scope.launch { drawerState.close() } }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Star, contentDescription = null) },
                        label = { Text("Importantes") },
                        selected = activeContext is ViewContext.Important,
                        onClick = { viewModel.setContext(ViewContext.Important); scope.launch { drawerState.close() } }
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.DoneAll, contentDescription = null) },
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
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("Ajustes") },
                        selected = false,
                        onClick = { showSettingsFullScreen = true; scope.launch { drawerState.close() } }
                    )
                }
            }
        ) {
            mainContent()
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
            onApply = { viewModel.updateFilterState(it); showFilterDialog = false }
        )
    }
    
    if (showTagsDialog) {
        val currentCategory = (activeContext as? ViewContext.Category)?.categoryId
        TagsDialog(
            tags = allTags,
            categories = allCategories,
            currentCategoryId = currentCategory,
            onDismiss = { showTagsDialog = false },
            onAddTag = { categoryId, name, aliases -> 
                viewModel.createTag(categoryId, name, aliases)
            },
            onUpdateTag = { id, categoryId, name, aliases ->
                viewModel.updateTag(id, categoryId, name, aliases)
            },
            onDeleteTag = { id ->
                viewModel.deleteTag(id)
            }
        )
    }

    quickViewTask?.let {
        QuickViewDialog(task = it, viewModel = viewModel, onDismiss = { quickViewTask = null }, onEditClick = { mainPaneState = MainPaneState.TaskDetail(it) })
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
            onSave = { task, subtasks -> 
                val subEntities = subtasks.map { subTitle ->
                    TaskEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        categoryId = task.categoryId,
                        subcategoryId = task.subcategoryId,
                        parentTaskId = task.id,
                        title = subTitle,
                        createdAt = System.currentTimeMillis(),
                        description = null,
                        dueDate = null,
                        hasSpecificTime = false,
                        isCompleted = false,
                        calendarEventId = null
                    )
                }
                viewModel.saveTasks(listOf(task) + subEntities)
            }
        )
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskCard(
    task: TaskEntity,
    tags: List<com.antakih.taskpen.data.local.entities.SubjectEntity>,
    isTrashContext: Boolean = false,
    isCompletedContext: Boolean = false,
    onComplete: () -> Unit = {},
    onUncomplete: () -> Unit = {},
    onToggleImportant: () -> Unit = {},
    onMoveToTrash: () -> Unit = {},
    onRestore: () -> Unit = {},
    onDeletePermanently: () -> Unit = {},
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val dateString = task.dueDate?.let {
        if (task.hasSpecificTime) "${dateFormat.format(Date(it))} - ${timeFormat.format(Date(it))}"
        else dateFormat.format(Date(it))
    } ?: "Sin fecha"

    val assignedTag = tags.find { it.id == task.subcategoryId }

    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isTrashContext) {
                Checkbox(checked = task.isCompleted, onCheckedChange = { 
                    if (task.isCompleted) onUncomplete() else onComplete() 
                })
                Spacer(modifier = Modifier.width(8.dp))
            }
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
            
            if (isTrashContext) {
                IconButton(onClick = onRestore) {
                    Icon(Icons.Default.Restore, contentDescription = "Restaurar")
                }
                IconButton(onClick = onDeletePermanently) {
                    Icon(Icons.Default.DeleteForever, contentDescription = "Eliminar permanentemente", tint = Color.Red)
                }
            } else {
                IconButton(onClick = onToggleImportant) {
                    Icon(
                        imageVector = if (task.isImportant) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Importante",
                        tint = if (task.isImportant) Color.Yellow else LocalContentColor.current
                    )
                }
                IconButton(onClick = onMoveToTrash) {
                    Icon(Icons.Default.Delete, contentDescription = "Mover a papelera")
                }
            }
        }
    }
}

@Composable
fun TagsDialog(
    tags: List<com.antakih.taskpen.data.local.entities.SubjectEntity>,
    categories: List<com.antakih.taskpen.data.local.entities.CategoryEntity>,
    currentCategoryId: String?,
    onDismiss: () -> Unit,
    onAddTag: (categoryId: String?, name: String, aliases: List<String>) -> Unit,
    onUpdateTag: (id: String, categoryId: String?, name: String, aliases: List<String>) -> Unit,
    onDeleteTag: (id: String) -> Unit
) {
    var editingTagId by remember { mutableStateOf<String?>(null) }
    var tagName by remember { mutableStateOf("") }
    var tagAliases by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<String?>(currentCategoryId) }
    var categoryExpanded by remember { mutableStateOf(false) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(if (editingTagId == null) "Nueva Etiqueta" else "Editar Etiqueta", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = tagName,
                    onValueChange = { tagName = it },
                    label = { Text("Nombre (Ej: Robótica)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = tagAliases,
                    onValueChange = { tagAliases = it },
                    label = { Text("Alias separados por coma") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Category selector
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = categories.find { it.id == selectedCategoryId }?.name ?: "Global (Sin Categoría)",
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Categoría") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { categoryExpanded = !categoryExpanded }) {
                                Icon(Icons.Default.ArrowDropDown, "Seleccionar")
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Global (Sin Categoría)") },
                            onClick = { 
                                selectedCategoryId = null
                                categoryExpanded = false 
                            }
                        )
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name) },
                                onClick = { 
                                    selectedCategoryId = cat.id
                                    categoryExpanded = false 
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (editingTagId != null) {
                        TextButton(onClick = {
                            editingTagId = null
                            tagName = ""
                            tagAliases = ""
                            selectedCategoryId = currentCategoryId
                        }) {
                            Text("Cancelar Edición")
                        }
                    }
                    Button(
                        onClick = {
                            if (tagName.isNotBlank()) {
                                val aliasesList = tagAliases.split(",")
                                    .map { it.trim().lowercase() }
                                    .filter { it.isNotEmpty() }
                                if (editingTagId == null) {
                                    onAddTag(selectedCategoryId, tagName.trim(), aliasesList)
                                } else {
                                    onUpdateTag(editingTagId!!, selectedCategoryId, tagName.trim(), aliasesList)
                                    editingTagId = null
                                }
                                tagName = ""
                                tagAliases = ""
                                selectedCategoryId = currentCategoryId
                            }
                        }
                    ) {
                        Text(if (editingTagId == null) "Añadir" else "Guardar")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("Etiquetas actuales:", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyColumn(modifier = Modifier.fillMaxHeight(0.5f)) {
                    items(tags, key = { it.id }) { tag ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(tag.fullName, style = MaterialTheme.typography.bodyLarge)
                                val catName = categories.find { it.id == tag.categoryId }?.name ?: "Global"
                                Text(catName, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                if (tag.aliases.isNotEmpty()) {
                                    Text(tag.aliases.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = Color.Gray.copy(alpha = 0.7f))
                                }
                            }
                            IconButton(onClick = {
                                editingTagId = tag.id
                                tagName = tag.fullName
                                tagAliases = tag.aliases.joinToString(", ")
                                selectedCategoryId = tag.categoryId
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Editar", modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { onDeleteTag(tag.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color.Red, modifier = Modifier.size(20.dp))
                            }
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
    var selectedTagIds by remember { mutableStateOf(currentState.selectedTags) }
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
                
                val tagsByCat = tags.groupBy { it.categoryId }
                // Necesitamos el viewModel o category map... wait, FilterDialog no recibe categories!
                // Pasaremos el mapa si es posible, o simplemente "Categoria N" si no hay nombre.
                // Actually, let's just group them and if categoryId is null say "Global".
                LazyColumn(modifier = Modifier.fillMaxHeight(0.3f)) {
                    tagsByCat.forEach { (catId, catTags) ->
                        item {
                            Text(
                                text = if (catId == null) "Global" else "Categoría específica",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp)
                            )
                        }
                        items(catTags, key = { it.id }) { tag ->
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
                            selectedTags = selectedTagIds,
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


@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ManualTaskSheet(
    allCategories: List<CategoryEntity>,
    allTags: List<com.antakih.taskpen.data.local.entities.SubjectEntity>,
    initialCategoryId: String?,
    onDismiss: () -> Unit,
    onSave: (TaskEntity, List<String>) -> Unit
) {
    var title by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var description by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var isImportant by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var selectedCategoryId by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(initialCategoryId) }
    var selectedTagId by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    
    var dueDateMillis by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Long?>(null) }
    var hasTime by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    
    var subtasks by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(listOf<String>()) }
    var newSubtaskTitle by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    
    var showDatePicker by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showTimePicker by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    
    val dateString = dueDateMillis?.let { java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(it)) } ?: "Sin fecha"
    val timeString = if (hasTime && dueDateMillis != null) java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(dueDateMillis!!)) else "Sin hora"

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
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Default.DateRange, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(dateString)
                }
                TextButton(onClick = { showTimePicker = true }, enabled = dueDateMillis != null) {
                    Icon(Icons.Default.Schedule, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(timeString)
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            Text("Subtareas:", style = MaterialTheme.typography.titleMedium)
            subtasks.forEachIndexed { index, st ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("• $st", modifier = Modifier.weight(1f))
                    IconButton(onClick = { subtasks = subtasks.toMutableList().apply { removeAt(index) } }) {
                        Icon(Icons.Default.Clear, contentDescription = "Eliminar")
                    }
                }
            }
            OutlinedTextField(
                value = newSubtaskTitle,
                onValueChange = { newSubtaskTitle = it },
                label = { Text("Añadir subtarea...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = {
                        if (newSubtaskTitle.isNotBlank()) {
                            subtasks = subtasks + newSubtaskTitle.trim()
                            newSubtaskTitle = ""
                        }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Agregar")
                    }
                }
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
                            dueDate = dueDateMillis,
                            hasSpecificTime = hasTime,
                            isCompleted = false,
                            isImportant = isImportant,
                            isDeleted = false,
                            calendarEventId = null
                        )
                        onSave(task, subtasks)
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
    
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dueDateMillis ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dueDateMillis = datePickerState.selectedDateMillis
                    if (dueDateMillis == null) hasTime = false
                    showDatePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState()
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val cal = Calendar.getInstance()
                    if (dueDateMillis != null) {
                        cal.timeInMillis = dueDateMillis!!
                    }
                    cal.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                    cal.set(Calendar.MINUTE, timePickerState.minute)
                    dueDateMillis = cal.timeInMillis
                    hasTime = true
                    showTimePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancelar") }
            },
            text = { TimePicker(state = timePickerState) }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
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



@Composable
fun MenuButton(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Text(text)
    }
}

@Composable
fun LeftLandscapePanel(
    activeContext: ViewContext,
    filterState: com.antakih.taskpen.ui.viewmodel.FilterState,
    allCategories: List<CategoryEntity>,
    onContextSelected: (ViewContext) -> Unit,
    onSearchChanged: (String) -> Unit,
    onFilterClick: () -> Unit,
    onTagsClick: () -> Unit,
    onToggleImportantFilter: () -> Unit,
    onSettingsClick: () -> Unit,
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.statusBarsPadding().padding(16.dp)) {
        // Top Row: Search + Filter + Star
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = filterState.searchQuery,
                onValueChange = onSearchChanged,
                modifier = Modifier.weight(1f).height(50.dp),
                placeholder = { Text("Search") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onFilterClick) {
                Icon(Icons.Default.FilterList, contentDescription = "Filtrar", tint = Color.White)
            }
            IconButton(onClick = onToggleImportantFilter) {
                Icon(
                    imageVector = if (filterState.showOnlyImportant) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Importante",
                    tint = Color.White
                )
            }
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "Ajustes", tint = Color.White)
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        // 3x2 Grid for buttons
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MenuButton("General", activeContext is ViewContext.General, { onContextSelected(ViewContext.General) }, Modifier.weight(1f))
            MenuButton("Hoy", activeContext is ViewContext.Today, { onContextSelected(ViewContext.Today) }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MenuButton("Mañana", activeContext is ViewContext.Tomorrow, { onContextSelected(ViewContext.Tomorrow) }, Modifier.weight(1f))
            MenuButton("Pospuestas", activeContext is ViewContext.Postponed, { onContextSelected(ViewContext.Postponed) }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MenuButton("Completadas", activeContext is ViewContext.Completed, { onContextSelected(ViewContext.Completed) }, Modifier.weight(1f))
            MenuButton("Papelera", activeContext is ViewContext.Trash, { onContextSelected(ViewContext.Trash) }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MenuButton("Ajustes", false, { onSettingsClick() }, Modifier.weight(1f))
        }
        
        Spacer(Modifier.height(32.dp))
        
        // Categories Header
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Categorías", style = MaterialTheme.typography.headlineMedium, color = Color.White, modifier = Modifier.weight(1f))
            IconButton(onClick = onTagsClick) {
                Icon(Icons.Default.Label, contentDescription = "Etiquetas", tint = Color.White)
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Categories list
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(allCategories, key = { it.id }) { cat ->
                val isSelected = (activeContext as? ViewContext.Category)?.categoryId == cat.id
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onCategoryClick(cat.id) },
                    colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(cat.name, color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickViewDialog(
    task: com.antakih.taskpen.data.local.entities.TaskEntity,
    viewModel: TaskViewModel,
    onDismiss: () -> Unit,
    onEditClick: () -> Unit
) {
    val subtasks by viewModel.getSubtasks(task.id).collectAsState(initial = emptyList())
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(task.title, style = MaterialTheme.typography.titleLarge)
                if (!task.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(task.description, style = MaterialTheme.typography.bodyMedium)
                }
                
                if (subtasks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Subtareas:", style = MaterialTheme.typography.titleMedium)
                    LazyColumn(modifier = Modifier.fillMaxHeight(0.4f)) {
                        items(subtasks, key = { it.id }) { subtask ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = subtask.isCompleted,
                                    onCheckedChange = { 
                                        if (it) viewModel.completeTask(subtask.id) else viewModel.uncompleteTask(subtask.id)
                                    }
                                )
                                Text(subtask.title, style = if (subtask.isCompleted) androidx.compose.ui.text.TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough) else androidx.compose.ui.text.TextStyle.Default)
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cerrar")
                    }
                    Button(onClick = { onEditClick(); onDismiss() }) {
                        Text("Ver Detalle / Editar")
                    }
                }
            }
        }
    }
}
