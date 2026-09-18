import re

file_path = 'app/src/main/java/com/antakih/taskpen/ui/screens/DashboardScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

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
                            androidx.compose.material3.Switch(
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

# Find the end of `mainContent = @Composable {` block. It ends right before `if (isLandscape) {`
content = content.replace(
    '        }\n    }\n\n    if (isLandscape) {',
    '        }\n            }\n        }\n    }\n\n    if (isLandscape) {'
)

# In TaskCard calls inside DashboardScreen.kt:
content = content.replace(
    'onClick = { onTaskClick(task) }',
    'onClick = { mainPaneState = MainPaneState.TaskDetail(task) },\n                                onLongClick = { quickViewTask = task }'
)

# And remove SettingsDialog completely.
content = re.sub(r'if \(showSettingsDialog\)\s*\{.*?\n\s*\}', '', content, flags=re.DOTALL)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Updated main states")
