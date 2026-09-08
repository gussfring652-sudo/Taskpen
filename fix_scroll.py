import re

file_path = 'app/src/main/java/com/antakih/taskpen/ui/screens/DashboardScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

old_drawer = """                ModalDrawerSheet(modifier = Modifier.width(300.dp)) {"""
new_drawer = """                ModalDrawerSheet(modifier = Modifier.width(300.dp).verticalScroll(rememberScrollState())) {"""
content = content.replace(old_drawer, new_drawer)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Added verticalScroll to ModalDrawerSheet")
