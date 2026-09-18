import re

file_path = 'app/src/main/java/com/antakih/taskpen/ui/screens/DashboardScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Add MainPaneState at the top level
if "sealed class MainPaneState" not in content:
    content = content.replace(
        "fun DashboardScreen(",
        """sealed class MainPaneState {
    object TaskList : MainPaneState()
    data class TaskDetail(val task: com.antakih.taskpen.data.local.entities.TaskEntity) : MainPaneState()
    object Settings : MainPaneState()
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen("""
    )

# Replace OptIn if it was already there (just clean it up if needed)

# Add state variables
content = content.replace(
    'var showSettingsDialog by remember { mutableStateOf(false) }',
    '''var mainPaneState by remember { mutableStateOf<MainPaneState>(MainPaneState.TaskList) }
    var quickViewTask by remember { mutableStateOf<com.antakih.taskpen.data.local.entities.TaskEntity?>(null) }'''
)

# Update TopAppBar to add Settings icon
old_top_bar = """                        actions = {
                            IconButton(onClick = { showFilterDialog = true }) {
                                Icon(Icons.Default.FilterList, contentDescription = "Filtros")
                            }
                            IconButton(onClick = { showTagsDialog = true }) {
                                Icon(Icons.Default.Label, contentDescription = "Etiquetas")
                            }
                        },"""
new_top_bar = """                        actions = {
                            IconButton(onClick = { showFilterDialog = true }) {
                                Icon(Icons.Default.FilterList, contentDescription = "Filtros")
                            }
                            IconButton(onClick = { showTagsDialog = true }) {
                                Icon(Icons.Default.Label, contentDescription = "Etiquetas")
                            }
                            IconButton(onClick = { mainPaneState = MainPaneState.Settings }) {
                                Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                            }
                        },"""
content = content.replace(old_top_bar, new_top_bar)

# Also update the bottom bar in landscape to use mainPaneState = Settings
content = content.replace(
    'onSettingsClick = { showSettingsDialog = true }',
    'onSettingsClick = { mainPaneState = MainPaneState.Settings }'
)

# Now, refactor mainContent
old_main_content = """    val mainContent = @Composable {
        Scaffold("""

new_main_content = """    val mainContent = @Composable {
        when (val state = mainPaneState) {
            is MainPaneState.TaskDetail -> {
                com.antakih.taskpen.ui.screens.TaskDetailScreen(
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
                            }
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
                            Switch(
                                checked = isCaseSensitive,
                                onCheckedChange = { viewModel.setCaseSensitiveTags(it) }
                            )
                        }
                    }
                }
            }
            is MainPaneState.TaskList -> {
        Scaffold("""
content = content.replace(old_main_content, new_main_content)

# We need to close the when block for mainContent!
# Let's find where the Scaffold ends.
# Scaffold ends with `}` and then `} else {` for the landscape branch!
# Let's just find the closing of `mainContent = @Composable { ... }`
# Wait, `mainContent` ends right before:
#     if (isLandscape) {
# Let's replace `    if (isLandscape) {` with `        }\n    }\n\n    if (isLandscape) {`
# But wait! I replaced `Scaffold(` with `when { ... TaskList -> Scaffold(`. So I just need to close the `TaskList` branch and the `when`.
content = content.replace(
    '        }\n    }\n\n    if (isLandscape) {',
    '        }\n            }\n        }\n    }\n\n    if (isLandscape) {'
)

# Fix task onClick in the LazyColumn:
# Right now it's: onClick = { onTaskClick(task) }
content = content.replace(
    'onClick = { onTaskClick(task) }',
    'onClick = { mainPaneState = MainPaneState.TaskDetail(task) },\n                                onLongClick = { quickViewTask = task }'
)

# And remove SettingsDialog completely.
# Find `if (showSettingsDialog) {` and remove it
import re
content = re.sub(r'if \(showSettingsDialog\).*?// Categor', '// Categor', content, flags=re.DOTALL)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Updated main states")
