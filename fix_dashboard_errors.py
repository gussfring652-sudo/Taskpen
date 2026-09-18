import re

file_path = 'app/src/main/java/com/antakih/taskpen/ui/screens/DashboardScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Fix misplaced imports
# I will gather all imports that are NOT at the top of the file, and move them to the top.
# The `replace_file_content` block had:
# sealed class MainPaneState { ... }
# Then later `import com.antakih.taskpen.ui.screens.TaskDetailScreen`
content = content.replace("import com.antakih.taskpen.ui.screens.TaskDetailScreen", "")
content = "import com.antakih.taskpen.ui.screens.TaskDetailScreen\nimport androidx.compose.material.icons.automirrored.filled.ArrowBack\n" + content

# Remove any other stray imports.
# In `refactor_dashboard_full.py` I did:
# 'import com.antakih.taskpen.ui.viewmodel.ViewContext\nimport com.antakih.taskpen.ui.screens.TaskDetailScreen\nimport androidx.compose.foundation.ExperimentalFoundationApi\nimport androidx.compose.foundation.combinedClickable\n\nsealed class MainPaneState'
# Let's clean it up properly.
content = content.replace("import com.antakih.taskpen.ui.screens.TaskDetailScreen\nimport androidx.compose.foundation.ExperimentalFoundationApi\nimport androidx.compose.foundation.combinedClickable", "")

content = "import androidx.compose.foundation.ExperimentalFoundationApi\nimport androidx.compose.foundation.combinedClickable\n" + content

# 2. Fix TaskCard signature
# We need to make sure `onLongClick` is present.
if "onLongClick: () -> Unit = {}" not in content:
    old_taskcard_def = """fun TaskCard(
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

    new_taskcard_def = """@OptIn(ExperimentalFoundationApi::class)
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
    content = content.replace(old_taskcard_def, new_taskcard_def)

if "combinedClickable" not in content.split("fun TaskCard")[1]:
    content = content.replace(
        'Modifier.fillMaxWidth().clickable { onClick() }',
        'Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)'
    )

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Fixed errors!")
