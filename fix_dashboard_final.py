import re

file_path = 'app/src/main/java/com/antakih/taskpen/ui/screens/DashboardScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Fix import position
content = content.replace("import kotlinx.coroutines.launch", "")
content = content.replace("package com.antakih.taskpen.ui.screens\n\n", "package com.antakih.taskpen.ui.screens\n\nimport kotlinx.coroutines.launch\n")

# Fix TaskCard signature correctly
old_taskcard = """@Composable
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
)"""

new_taskcard = """@OptIn(ExperimentalFoundationApi::class)
@Composable
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
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {}
)"""

content = content.replace(old_taskcard, new_taskcard)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)

print("Fixed Dashboard final errors!")
