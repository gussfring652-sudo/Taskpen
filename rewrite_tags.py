import re

file_path = 'app/src/main/java/com/antakih/taskpen/ui/screens/DashboardScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Replace the TagsDialog call
old_call = """    if (showTagsDialog) {
        TagsDialog(
            tags = allTags,
            onDismiss = { showTagsDialog = false },
            onAddTag = { name, aliases -> 
                // Using an empty/default color for now or whatever addSubject needs
            }
        )
    }"""
new_call = """    if (showTagsDialog) {
        val currentCategory = (activeContext as? ViewContext.Category)?.categoryId
        TagsDialog(
            tags = allTags,
            categories = categories,
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
    }"""
content = content.replace(old_call, new_call)

# Now completely replace the TagsDialog composable.
# Using regex to match from `@Composable fun TagsDialog` up to the end of the file or next composable.
tags_dialog_pattern = re.compile(r'@Composable\s+fun TagsDialog\(.*?^$', re.MULTILINE | re.DOTALL)

new_dialog = """@Composable
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
            }
        }
    }
}"""
content = tags_dialog_pattern.sub(new_dialog, content)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Rewrote TagsDialog")
