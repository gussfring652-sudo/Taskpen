import sys
import re

file_path = 'app/src/main/java/com/antakih/taskpen/ui/screens/DashboardScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

imports_to_add = """
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
"""

for imp in imports_to_add.strip().split('\n'):
    if imp not in content:
        content = content.replace('import androidx.compose.runtime.*', 'import androidx.compose.runtime.*\n' + imp)

# Replace DashboardScreen logic
# We will use regex to find the body of DashboardScreen and replace it safely.
# It's better to just extract the whole DashboardScreen function and replace it.

start_idx = content.find('@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nfun DashboardScreen(')
end_idx = content.find('@Composable\nfun TaskCard(')

dashboard_screen_full = """@OptIn(ExperimentalMaterial3Api::class)
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
    var textInputValue by remember { mutableStateOf("") }
    
    var showDrawingSheet by remember { mutableStateOf(false) }
    var showManualTaskSheet by remember { mutableStateOf(false) }

    val displayedTasks = remember(activeContext, filterState, activeTasks, deletedTasks) {
        val baseTasks = when (activeContext) {
            is ViewContext.Trash -> deletedTasks
            is ViewContext.General -> activeTasks
            is ViewContext.Category -> activeTasks.filter { it.categoryId == (activeContext as ViewContext.Category).categoryId }
            is ViewContext.Completed -> activeTasks.filter { it.isCompleted }
            is ViewContext.Important -> activeTasks.filter { it.isImportant && !it.isCompleted }
            is ViewContext.Today -> activeTasks.filter { 
                it.dueDate != null && android.text.format.DateUtils.isToday(it.dueDate) 
            }
            is ViewContext.Tomorrow -> activeTasks.filter {
                it.dueDate != null && android.text.format.DateUtils.isToday(it.dueDate - 86400000)
            }
            is ViewContext.Postponed -> activeTasks.filter {
                it.dueDate != null && it.dueDate < System.currentTimeMillis() && !android.text.format.DateUtils.isToday(it.dueDate)
            }
        }
        
        baseTasks.filter { task ->
            val matchesSearch = if (filterState.searchQuery.isNotBlank()) {
                task.title.contains(filterState.searchQuery, ignoreCase = true) ||
                task.description?.contains(filterState.searchQuery, ignoreCase = true) == true
            } else true
            
            val matchesImportant = if (filterState.showOnlyImportant) task.isImportant else true
            val matchesCategory = if (filterState.selectedCategories.isNotEmpty()) filterState.selectedCategories.contains(task.categoryId) else true
            val matchesTags = if (filterState.selectedTags.isNotEmpty()) filterState.selectedTags.contains(task.subcategoryId) else true
            
            matchesSearch && matchesImportant && matchesCategory && matchesTags
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
    val isLandscape = configuration.screenWidthDp >= 600

    val mainContent = @Composable {
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
                        colors = if (isLandscape) TextFieldDefaults.colors(
                            unfocusedContainerColor = Color.White,
                            focusedContainerColor = Color.White
                        ) else TextFieldDefaults.colors()
                    )
                    Spacer(Modifier.width(8.dp))
                    FloatingActionButton(
                        onClick = { showManualTaskSheet = true },
                        containerColor = if (isLandscape) Color(0xFF00B0FF) else MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Manual", tint = if (isLandscape) Color.White else LocalContentColor.current)
                    }
                    Spacer(Modifier.width(8.dp))
                    FloatingActionButton(
                        onClick = { showDrawingSheet = true },
                        containerColor = if (isLandscape) Color(0xFF00B0FF) else MaterialTheme.colorScheme.primary
                    ) {
                        Icon(Icons.Default.Create, contentDescription = "Escribir con S-Pen", tint = if (isLandscape) Color.White else LocalContentColor.current)
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
                        items(displayedTasks, key = { it.id }) { task ->
                            TaskCard(
                                task = task,
                                tags = allTags,
                                onComplete = { viewModel.completeTask(task.id) },
                                onToggleImportant = { viewModel.toggleTaskImportance(task.id, !task.isImportant) },
                                onClick = { onTaskClick(task) }
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }

    if (isLandscape) {
        Row(modifier = Modifier.fillMaxSize().background(Color(0xFF280B45))) {
            LeftLandscapePanel(
                activeContext = activeContext,
                filterState = filterState,
                allCategories = allCategories,
                onContextSelected = { viewModel.setContext(it) },
                onSearchChanged = { viewModel.updateFilterState(filterState.copy(searchQuery = it)) },
                onFilterClick = { showFilterDialog = true },
                onTagsClick = { showTagsDialog = true },
                onToggleImportantFilter = { viewModel.updateFilterState(filterState.copy(showOnlyImportant = !filterState.showOnlyImportant)) },
                onSettingsClick = { /* TODO: Ajustes */ },
                onCategoryClick = { 
                    if ((activeContext as? ViewContext.Category)?.categoryId == it) viewModel.setContext(ViewContext.General) 
                    else viewModel.setContext(ViewContext.Category(it)) 
                },
                modifier = Modifier.width(360.dp).fillMaxHeight()
            )
            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFF5D5270))) {
                mainContent()
            }
        }
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
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
            filterState = filterState,
            onDismiss = { showFilterDialog = false },
            onApply = { viewModel.updateFilterState(it); showFilterDialog = false }
        )
    }
    
    if (showTagsDialog) {
        TagsDialog(
            tags = allTags,
            onDismiss = { showTagsDialog = false },
            onCreateTag = { name, color -> viewModel.createSubject(name, color) },
            onDeleteTag = { id -> viewModel.deleteSubject(id) }
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
"""

content = content[:start_idx] + dashboard_screen_full + content[end_idx:]

left_panel_composable = """
@Composable
fun LeftLandscapePanel(
    activeContext: ViewContext,
    filterState: FilterState,
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
    Column(modifier = modifier.padding(16.dp)) {
        // Top Row: Search + Filter + Star
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = filterState.searchQuery,
                onValueChange = onSearchChanged,
                modifier = Modifier.weight(1f).height(50.dp),
                placeholder = { Text("Search", color = Color.Gray) },
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = Color.White,
                    focusedContainerColor = Color.White,
                    unfocusedTextColor = Color.Black,
                    focusedTextColor = Color.Black
                ),
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray) },
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
            IconButton(onClick = onTagsClick) {
                Icon(Icons.Default.Label, contentDescription = "Etiquetas", tint = Color.White)
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        // 2x2 Grid for buttons
        val cyanColor = Color(0xFF00B0FF)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onContextSelected(ViewContext.Today) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = if (activeContext is ViewContext.Today) Color.White else cyanColor),
                shape = RectangleShape
            ) { Text("Hoy", color = if (activeContext is ViewContext.Today) Color.Black else Color.White) }
            Button(
                onClick = { onContextSelected(ViewContext.Tomorrow) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = if (activeContext is ViewContext.Tomorrow) Color.White else cyanColor),
                shape = RectangleShape
            ) { Text("Mañana", color = if (activeContext is ViewContext.Tomorrow) Color.Black else Color.White) }
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onContextSelected(ViewContext.Completed) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = if (activeContext is ViewContext.Completed) Color.White else cyanColor),
                shape = RectangleShape
            ) { Text("Completadas", color = if (activeContext is ViewContext.Completed) Color.Black else Color.White) }
            Button(
                onClick = { onContextSelected(ViewContext.Postponed) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = if (activeContext is ViewContext.Postponed) Color.White else cyanColor),
                shape = RectangleShape
            ) { Text("Pospuestas", color = if (activeContext is ViewContext.Postponed) Color.Black else Color.White) }
        }
        
        Spacer(Modifier.height(32.dp))
        
        // Categories Header
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Categorías", color = Color.White, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "Ajustes", tint = Color.White)
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Categories list
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(allCategories, key = { it.id }) { cat ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onCategoryClick(cat.id) },
                    colors = CardDefaults.cardColors(containerColor = if ((activeContext as? ViewContext.Category)?.categoryId == cat.id) Color.LightGray else Color.White),
                    shape = RectangleShape
                ) {
                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(cat.name, color = Color.Black)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.Gray)
                    }
                }
            }
        }
    }
}
"""

if 'fun LeftLandscapePanel' not in content:
    content += "\n" + left_panel_composable

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print('Refactoring completed successfully')
