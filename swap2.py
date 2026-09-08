import re

file_path = 'app/src/main/java/com/antakih/taskpen/ui/screens/DashboardScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Find Search Row and replace Tags with Settings
old_search_row = re.compile(r'IconButton\(onClick = onTagsClick\) \{\s*Icon\(Icons\.Default\.Label, contentDescription = "Etiquetas", tint = Color\.White\)\s*\}')
new_search_row = 'IconButton(onClick = onSettingsClick) {\n                Icon(Icons.Default.Settings, contentDescription = "Ajustes", tint = Color.White)\n            }'
content = old_search_row.sub(new_search_row, content)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Swapped second button")
