import re

file_path = 'app/src/main/java/com/antakih/taskpen/ui/screens/TaskDetailScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Inject imports properly (after package)
imports_to_add = """
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
"""
content = re.sub(r'(package com\.antakih\.taskpen\.ui\.screens\n)', r'\1' + imports_to_add, content)

# Replace top bar navigation/actions
old_top_bar = """        topBar = {
            TopAppBar(
                title = { Text(task.title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },"""

new_top_bar = """        topBar = {
            TopAppBar(
                title = { Text(task.title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar")
                    }
                    IconButton(onClick = { 
                        viewModel.moveToTrash(task.id)
                        onBack()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },"""
content = content.replace(old_top_bar, new_top_bar)

# Add showEditDialog state
content = content.replace(
    'var showAddSubtask by remember { mutableStateOf(false) }',
    'var showAddSubtask by remember { mutableStateOf(false) }\n    var showEditDialog by remember { mutableStateOf(false) }'
)

# Add EditTaskDialog definition
edit_dialog_code = """
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTaskDialog(
    task: TaskEntity,
    allCategories: List<com.antakih.taskpen.data.local.entities.CategoryEntity>,
    allTags: List<com.antakih.taskpen.data.local.entities.SubjectEntity>,
    onDismiss: () -> Unit,
    onSave: (title: String, desc: String, dueDate: Long?, categoryId: String?, subcategoryId: String?) -> Unit
) {
    var title by remember { mutableStateOf(task.title) }
    var description by remember { mutableStateOf(task.description ?: "") }
    var dueDateMillis by remember { mutableStateOf(task.dueDate) }
    var selectedCategoryId by remember { mutableStateOf(task.categoryId) }
    var selectedTagId by remember { mutableStateOf(task.subcategoryId) }

    var showDatePicker by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var tagExpanded by remember { mutableStateOf(false) }

    val dateString = dueDateMillis?.let { java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(it)) } ?: "Sin fecha"

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Text("Editar Tarea", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Vence: $dateString", modifier = Modifier.weight(1f))
                    TextButton(onClick = { showDatePicker = true }) { Text("Cambiar") }
                    if (dueDateMillis != null) {
                        IconButton(onClick = { dueDateMillis = null }) {
                            Icon(Icons.Default.Clear, contentDescription = "Quitar fecha")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Categoría
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = allCategories.find { it.id == selectedCategoryId }?.name ?: "Global (Sin Categoría)",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Categoría") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { categoryExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Global (Sin Categoría)") },
                            onClick = { selectedCategoryId = null; categoryExpanded = false }
                        )
                        allCategories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name) },
                                onClick = { selectedCategoryId = cat.id; categoryExpanded = false }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Etiqueta
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = allTags.find { it.id == selectedTagId }?.fullName ?: "Ninguna",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Etiqueta") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { tagExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = tagExpanded,
                        onDismissRequest = { tagExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Ninguna") },
                            onClick = { selectedTagId = null; tagExpanded = false }
                        )
                        val tagsToDisplay = if (selectedCategoryId == null) allTags else allTags.filter { it.categoryId == selectedCategoryId || it.categoryId == null }
                        tagsToDisplay.forEach { tag ->
                            DropdownMenuItem(
                                text = { Text(tag.fullName) },
                                onClick = { selectedTagId = tag.id; tagExpanded = false }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Button(onClick = {
                        if (title.isNotBlank()) {
                            onSave(title.trim(), description.trim(), dueDateMillis, selectedCategoryId, selectedTagId)
                            onDismiss()
                        }
                    }) { Text("Guardar") }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dueDateMillis ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dueDateMillis = datePickerState.selectedDateMillis
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
}
"""

content = content + edit_dialog_code

edit_usage = """
        if (showEditDialog) {
            val allCategories by viewModel.allCategories.collectAsState()
            EditTaskDialog(
                task = task,
                allCategories = allCategories,
                allTags = allTags,
                onDismiss = { showEditDialog = false },
                onSave = { title, desc, date, catId, tagId ->
                    viewModel.updateTaskDetails(task.id, title, desc, date, catId, tagId)
                }
            )
        }
    }
}"""
content = re.sub(r'    \}\n\}', edit_usage, content, count=1)


with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("TaskDetailScreen Script Fixed!")
