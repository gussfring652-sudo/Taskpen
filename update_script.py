import sys

file_path = 'app/src/main/java/com/antakih/taskpen/ui/screens/DashboardScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Update imports
imports = """
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Clear
import java.util.Calendar
"""
import_idx = content.find('import ')
if 'import androidx.compose.material3.DatePicker' not in content:
    content = content[:import_idx] + imports.lstrip() + content[import_idx:]


# 2. Find and replace the caller
caller_old = """    if (showManualTaskSheet) {
        ManualTaskSheet(
            allCategories = allCategories,
            allTags = allTags,
            initialCategoryId = (activeContext as? ViewContext.Category)?.categoryId,
            onDismiss = { showManualTaskSheet = false },
            onSave = { task -> 
                viewModel.saveTasks(listOf(task)) 
            }
        )
    }"""

caller_new = """    if (showManualTaskSheet) {
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
                        createdAt = System.currentTimeMillis()
                    )
                }
                viewModel.saveTasks(listOf(task) + subEntities)
            }
        )
    }"""
content = content.replace(caller_old, caller_new)


# 3. Find and replace ManualTaskSheet function
start_idx = content.find('@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nfun ManualTaskSheet(')
end_idx = content.find('\n}\n', start_idx) + 3

new_manual_sheet = """@OptIn(ExperimentalMaterial3Api::class)
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
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Agregar")
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
"""

content = content[:start_idx] + new_manual_sheet + content[end_idx:]

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)

print('Success')
