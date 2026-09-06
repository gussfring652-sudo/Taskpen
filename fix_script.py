import sys

file_path = 'app/src/main/java/com/antakih/taskpen/ui/screens/DashboardScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

imports = """import androidx.compose.material3.AlertDialog
"""
import_idx = content.find('import ')
if 'import androidx.compose.material3.AlertDialog' not in content:
    content = content[:import_idx] + imports + content[import_idx:]

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print('Success')
