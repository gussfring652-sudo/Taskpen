import re

file_path = 'app/src/main/java/com/antakih/taskpen/ui/screens/DashboardScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

old_col = """    Column(modifier = modifier.statusBarsPadding().padding(16.dp)) {"""
new_col = """    Column(modifier = modifier.statusBarsPadding().padding(16.dp).verticalScroll(rememberScrollState())) {"""
content = content.replace(old_col, new_col)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Added verticalScroll to LeftLandscapePanel")
