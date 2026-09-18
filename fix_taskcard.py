import re

file_path = 'app/src/main/java/com/antakih/taskpen/ui/screens/DashboardScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

old_signature = """fun TaskCard(
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
)"""
new_signature = """@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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
)"""
content = content.replace(old_signature, new_signature)

old_clickable = "Modifier.fillMaxWidth().clickable { onClick() }"
new_clickable = "Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)"
content = content.replace(old_clickable, new_clickable)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Updated TaskCard")
