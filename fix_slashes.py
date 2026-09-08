import re

file_path = 'app/src/main/java/com/antakih/taskpen/domain/usecases/ParseHandwrittenTextUseCase.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace(r'\\\s', r'\\s')
content = content.replace(r'\\\d', r'\\d')

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Fixed slashes")
