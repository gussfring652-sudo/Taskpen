import re

file_path = 'app/src/main/java/com/antakih/taskpen/ui/screens/DashboardScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# 1. TagsDialog: Show aliases
content = content.replace(
    '''Text(tag.fullName, style = MaterialTheme.typography.bodyLarge)
                                val catName = categories.find { it.id == tag.categoryId }?.name ?: "Global"
                                Text(catName, style = MaterialTheme.typography.bodySmall, color = Color.Gray)''',
    '''Text(tag.fullName, style = MaterialTheme.typography.bodyLarge)
                                val catName = categories.find { it.id == tag.categoryId }?.name ?: "Global"
                                Text(catName, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                if (tag.aliases.isNotEmpty()) {
                                    Text(tag.aliases.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = Color.Gray.copy(alpha = 0.7f))
                                }'''
)

# 2. FilterDialog: Group tags by category
# Currently:
# LazyColumn(modifier = Modifier.fillMaxHeight(0.3f)) {
#     items(tags, key = { it.id }) { tag ->
#         Row(verticalAlignment = Alignment.CenterVertically) {
#             Checkbox(...)

old_filter_list = '''                Spacer(modifier = Modifier.height(8.dp))
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
                }'''

new_filter_list = '''                Spacer(modifier = Modifier.height(8.dp))
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
                }'''
content = content.replace(old_filter_list, new_filter_list)

# Fix FilterState bug in FilterDialog
# It needs to return selectedTags as well. Wait, FilterState has selectedTags!
# Let's see what happens onApply.
content = content.replace(
    '''onApply(
                            com.antakih.taskpen.ui.viewmodel.FilterState(
                                sortByDueDate = sortByDueDate,
                                selectedTagIds = selectedTagIds,
                                showOnlyImportant = showOnlyImportant
                            )
                        )''',
    '''onApply(
                            currentState.copy(
                                sortByDueDate = sortByDueDate,
                                selectedTags = selectedTagIds,
                                showOnlyImportant = showOnlyImportant
                            )
                        )'''
)
# And state read:
content = content.replace(
    'var selectedTagIds by remember { mutableStateOf(currentState.selectedTagIds) }',
    'var selectedTagIds by remember { mutableStateOf(currentState.selectedTags) }'
)

# 3. Add SettingsDialog in DashboardScreen
# Add variable: var showSettingsDialog by remember { mutableStateOf(false) }
content = content.replace(
    'var showTagsDialog by remember { mutableStateOf(false) }',
    'var showTagsDialog by remember { mutableStateOf(false) }\n    var showSettingsDialog by remember { mutableStateOf(false) }'
)
# Add settings onSettingsClick:
content = content.replace(
    'onSettingsClick = { /* TODO: Ajustes */ }',
    'onSettingsClick = { showSettingsDialog = true }'
)
# Add the SettingsDialog code block
settings_dialog_code = '''
    if (showSettingsDialog) {
        val isCaseSensitive by viewModel.isCaseSensitiveTags.collectAsState()
        androidx.compose.ui.window.Dialog(onDismissRequest = { showSettingsDialog = false }) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Ajustes", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    
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
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showSettingsDialog = false }) {
                            Text("Cerrar")
                        }
                    }
                }
            }
        }
    }
'''
content = content.replace('if (showFilterDialog) {', settings_dialog_code + '\n    if (showFilterDialog) {')

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Updated DashboardScreen basics")
