import re

file_path = 'app/src/main/java/com/antakih/taskpen/ui/screens/DashboardScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

new_col = """    Column(modifier = modifier.statusBarsPadding().padding(16.dp).verticalScroll(rememberScrollState())) {"""
old_col = """    Column(modifier = modifier.statusBarsPadding().padding(16.dp)) {"""
content = content.replace(new_col, old_col)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Reverted verticalScroll from LeftLandscapePanel")
