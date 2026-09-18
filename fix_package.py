import re

file_path = 'app/src/main/java/com/antakih/taskpen/ui/screens/DashboardScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace("package com.antakih.taskpen.ui.screens\n\n", "")
content = "package com.antakih.taskpen.ui.screens\n\n" + content

# Also, there's another stray import block if I look carefully.
# Wait, the `replace_file_content` block had:
# ```
# import com.antakih.taskpen.ui.viewmodel.ViewContext
# import com.antakih.taskpen.ui.screens.TaskDetailScreen
# ```
# Did I accidentally leave it there? Let's check for any `import` AFTER a class definition.
# It was on line 52. Let's just fix the top ones.
content = re.sub(r'(import com\.antakih\.taskpen\.ui\.screens\.TaskDetailScreen.*?)\nsealed class MainPaneState', r'\nsealed class MainPaneState', content, flags=re.DOTALL)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Fixed package order!")
