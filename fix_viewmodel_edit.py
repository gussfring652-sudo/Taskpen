import re

file_path = 'app/src/main/java/com/antakih/taskpen/ui/viewmodel/TaskViewModel.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace("            import kotlinx.coroutines.flow.first\n", "")
if "import kotlinx.coroutines.flow.first" not in content:
    content = content.replace("import kotlinx.coroutines.flow.stateIn", "import kotlinx.coroutines.flow.stateIn\nimport kotlinx.coroutines.flow.first")

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Fixed TaskViewModel imports")
