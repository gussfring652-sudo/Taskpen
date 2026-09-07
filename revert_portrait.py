import sys

file_path = 'app/src/main/java/com/antakih/taskpen/ui/screens/DashboardScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Only revert the first occurrence which is in TopAppBar
content = content.replace('Icon(Icons.Default.Label, contentDescription = "Etiquetas", tint = Color.White)', 'Icon(Icons.Default.Label, contentDescription = "Etiquetas")', 1)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Reverted portrait label icon.")
