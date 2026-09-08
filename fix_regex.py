import re

file_path = 'app/src/main/java/com/antakih/taskpen/domain/usecases/ParseHandwrittenTextUseCase.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Replace "\b" with "\\b" and "\d" with "\\d" and "\s" with "\\s"
content = content.replace('(?i)\b', '(?i)\\\\b')
content = content.replace('?\b', '?\\\\b')
content = content.replace(')\b', ')\\\\b')
content = content.replace('\d', '\\\\d')
content = content.replace('\s', '\\\\s')
content = content.replace('\.', '\\\\.')

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Fixed Regex Escapes")
