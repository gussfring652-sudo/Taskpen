import sys

file_path = 'app/src/main/java/com/antakih/taskpen/ui/screens/DashboardScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    lines = f.read().split('\n')

new_lines = []
for line in lines:
    if 'import java.util.Calendar' in line:
        if any('import java.util.Calendar' in l for l in new_lines):
            continue # skip duplicate
    new_lines.append(line)

content = '\n'.join(new_lines)

bad_subtask = """                    TaskEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        categoryId = task.categoryId,
                        subcategoryId = task.subcategoryId,
                        parentTaskId = task.id,
                        title = subTitle,
                        createdAt = System.currentTimeMillis()
                    )"""

good_subtask = """                    TaskEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        categoryId = task.categoryId,
                        subcategoryId = task.subcategoryId,
                        parentTaskId = task.id,
                        title = subTitle,
                        createdAt = System.currentTimeMillis(),
                        description = null,
                        dueDate = null,
                        hasSpecificTime = false,
                        isCompleted = false,
                        calendarEventId = null
                    )"""

content = content.replace(bad_subtask, good_subtask)

# Fix Icons.Filled.Send -> Icons.AutoMirrored.Filled.Send in ManualTaskSheet
content = content.replace('Icons.Filled.Send', 'Icons.AutoMirrored.Filled.Send')

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Fixed duplicate import and missing TaskEntity fields.")
