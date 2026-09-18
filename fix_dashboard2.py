import re

file_path = 'app/src/main/java/com/antakih/taskpen/ui/screens/DashboardScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Fix sorting:
content = content.replace(
    'filtered.sortedWith(compareBy(nullsLast()) { it.dueDate })',
    'filtered.sortedBy { it.dueDate ?: Long.MAX_VALUE }'
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Fixed sortedWith")
