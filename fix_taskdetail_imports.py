import re

file_path = 'app/src/main/java/com/antakih/taskpen/ui/screens/TaskDetailScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

package_line = ""
import_lines = []
other_lines = []

for line in lines:
    if line.startswith("package "):
        package_line = line
    elif line.startswith("import "):
        import_lines.append(line)
    else:
        other_lines.append(line)

new_content = package_line + "\n" + "".join(import_lines) + "\n" + "".join(other_lines)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(new_content)

print("Imports fixed in TaskDetailScreen!")
