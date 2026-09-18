import re

file_path = 'app/src/main/java/com/antakih/taskpen/ui/screens/DashboardScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Inject imports properly (after package)
imports_to_add = """
import com.antakih.taskpen.ui.screens.TaskDetailScreen
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.automirrored.filled.ArrowBack
"""
content = re.sub(r'(package com\.antakih\.taskpen\.ui\.screens\n)', r'\1' + imports_to_add, content)

# 1. Add MainPaneState
content = content.replace(
    'import com.antakih.taskpen.ui.viewmodel.ViewContext',
    'import com.antakih.taskpen.ui.viewmodel.ViewContext\n\nsealed class MainPaneState {\n    object TaskList : MainPaneState()\n    data class TaskDetail(val task: com.antakih.taskpen.data.local.entities.TaskEntity) : MainPaneState()\n    object Settings : MainPaneState()\n}'
)
content = content.replace(
    '@OptIn(ExperimentalMaterial3Api::class)',
    '@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)'
)

# 2. States
content = content.replace(
    'var showSettingsDialog by remember { mutableStateOf(false) }',
    'var mainPaneState by remember { mutableStateOf<MainPaneState>(MainPaneState.TaskList) }\n    var quickViewTask by remember { mutableStateOf<com.antakih.taskpen.data.local.entities.TaskEntity?>(null) }'
)

# 3. TopAppBar action
content = content.replace(
    '''IconButton(onClick = { showTagsDialog = true }) {
                                Icon(Icons.AutoMirrored.Filled.Label, contentDescription = "Etiquetas")
                            }''',
    '''IconButton(onClick = { showTagsDialog = true }) {
                                Icon(Icons.AutoMirrored.Filled.Label, contentDescription = "Etiquetas")
                            }
                            IconButton(onClick = { mainPaneState = MainPaneState.Settings }) {
                                Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                            }'''
)

# 4. Landscape settings click
content = content.replace(
    'onSettingsClick = { showSettingsDialog = true }',
    'onSettingsClick = { mainPaneState = MainPaneState.Settings }'
)
content = content.replace(
    'onSettingsClick = { /* TODO: Ajustes */ }',
    'onSettingsClick = { mainPaneState = MainPaneState.Settings }'
)

# 5. mainContent rewrite
old_main_content = '''    val mainContent = @Composable {
        Scaffold('''

new_main_content = '''    val mainContent = @Composable {
        when (val state = mainPaneState) {
            is MainPaneState.TaskDetail -> {
                TaskDetailScreen(
                    task = state.task,
                    viewModel = viewModel,
                    onBack = { mainPaneState = MainPaneState.TaskList }
                )
            }
            is MainPaneState.Settings -> {
                val isCaseSensitive by viewModel.isCaseSensitiveTags.collectAsState()
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Ajustes") },
                            navigationIcon = {
                                IconButton(onClick = { mainPaneState = MainPaneState.TaskList }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        )
                    }
                ) { padding ->
                    Column(modifier = Modifier.padding(padding).padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Sensibilidad a mayúsculas", style = MaterialTheme.typography.bodyLarge)
                                Text("Requiere coincidencia exacta de mayúsculas y minúsculas en las etiquetas.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                            androidx.compose.material3.Switch(
                                checked = isCaseSensitive,
                                onCheckedChange = { viewModel.setCaseSensitiveTags(it) }
                            )
                        }
                    }
                }
            }
            is MainPaneState.TaskList -> {
        Scaffold('''
content = content.replace(old_main_content, new_main_content)

content = content.replace(
    '        }\n    }\n\n    if (isLandscape) {',
    '        }\n            }\n        }\n    }\n\n    if (isLandscape) {'
)

# 6. TaskCard usages
content = content.replace(
    'onClick = { onTaskClick(task) }',
    'onClick = { mainPaneState = MainPaneState.TaskDetail(task) },\n                                        onLongClick = { quickViewTask = task }'
)

# 7. TaskCard definition
old_taskcard_def = '''fun TaskCard(
    task: TaskEntity,
    tags: List<com.antakih.taskpen.data.local.entities.SubjectEntity>,
    isTrashContext: Boolean,
    isCompletedContext: Boolean,
    onComplete: () -> Unit,
    onUncomplete: () -> Unit,
    onToggleImportant: () -> Unit,
    onMoveToTrash: () -> Unit,
    onRestore: () -> Unit,
    onDeletePermanently: () -> Unit,
    onClick: () -> Unit
)'''

new_taskcard_def = '''@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskCard(
    task: TaskEntity,
    tags: List<com.antakih.taskpen.data.local.entities.SubjectEntity>,
    isTrashContext: Boolean,
    isCompletedContext: Boolean,
    onComplete: () -> Unit,
    onUncomplete: () -> Unit,
    onToggleImportant: () -> Unit,
    onMoveToTrash: () -> Unit,
    onRestore: () -> Unit,
    onDeletePermanently: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
)'''
content = content.replace(old_taskcard_def, new_taskcard_def)

content = content.replace(
    'Modifier.fillMaxWidth().clickable { onClick() }',
    'Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)'
)

# 8. Add QuickViewDialog at the bottom
quick_view = '''
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
'''
content += quick_view

# 9. QuickViewDialog instance inside DashboardScreen
content = content.replace(
    'if (showDrawingSheet) {',
    'quickViewTask?.let {\n        QuickViewDialog(task = it, viewModel = viewModel, onDismiss = { quickViewTask = null }, onEditClick = { mainPaneState = MainPaneState.TaskDetail(it) })\n    }\n    if (showDrawingSheet) {'
)

# 10. Remove the old SettingsDialog block.
content = re.sub(r'if \(showSettingsDialog\)\s*\{.*?(?=if \(showFilterDialog\) \{)', '', content, flags=re.DOTALL)


with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Dashboard Script Fixed!")
