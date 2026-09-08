import re

file_path = 'app/src/main/java/com/antakih/taskpen/ui/screens/DashboardScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Replace TaskCard invocation in main content
old_invocation = """                            TaskCard(
                                task = task,
                                tags = allTags,
                                onComplete = { viewModel.completeTask(task.id) },
                                onToggleImportant = { viewModel.toggleTaskImportance(task.id, !task.isImportant) },
                                onClick = { onTaskClick(task) }
                            )"""
new_invocation = """                            TaskCard(
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
                                onClick = { onTaskClick(task) }
                            )"""
content = content.replace(old_invocation, new_invocation)

# Replace TaskCard composable
start_marker = "@Composable\nfun TaskCard("
end_marker = "@Composable\nfun TagsDialog("

start_idx = content.find(start_marker)
end_idx = content.find(end_marker)

new_card = """@Composable
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
    onClick: () -> Unit = {}
) {
    val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val dateString = task.dueDate?.let {
        if (task.hasSpecificTime) "${dateFormat.format(Date(it))} - ${timeFormat.format(Date(it))}"
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

"""

if start_idx != -1 and end_idx != -1:
    content = content[:start_idx] + new_card + content[end_idx:]
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(content)
    print("Rewrote TaskCard")
else:
    print("Could not find boundaries")
