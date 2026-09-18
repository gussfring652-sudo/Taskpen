import re

file_path = 'app/src/main/java/com/antakih/taskpen/ui/screens/DashboardScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace(
    'onSettingsClick = { showSettingsDialog = true }',
    'onSettingsClick = { mainPaneState = MainPaneState.Settings }'
)
content = content.replace(
    'onSettingsClick = { /* TODO: Ajustes */ }',
    'onSettingsClick = { mainPaneState = MainPaneState.Settings }'
)
with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Updated settings click")
