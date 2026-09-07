import sys

file_path = 'app/src/main/java/com/antakih/taskpen/ui/screens/DashboardScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace(
    "colors = if (isLandscape) TextFieldDefaults.colors(\n                            unfocusedContainerColor = Color.White,\n                            focusedContainerColor = Color.White\n                        ) else TextFieldDefaults.colors()",
    "colors = TextFieldDefaults.colors()"
)
content = content.replace(
    "containerColor = if (isLandscape) Color(0xFF00B0FF) else MaterialTheme.colorScheme.secondaryContainer",
    "containerColor = MaterialTheme.colorScheme.secondaryContainer"
)
content = content.replace(
    "tint = if (isLandscape) Color.White else LocalContentColor.current",
    "tint = LocalContentColor.current"
)
content = content.replace(
    "containerColor = if (isLandscape) Color(0xFF00B0FF) else MaterialTheme.colorScheme.primary",
    "containerColor = MaterialTheme.colorScheme.primary"
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Fixed colors.")
