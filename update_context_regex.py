import re

file_path = 'app/src/main/java/com/antakih/taskpen/domain/usecases/ParseHandwrittenTextUseCase.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

old_regex = r'Regex("(?i)\\\\s+(para|de)\\\\s+([A-Za-z0-9ÁÉÍÓÚáéíóúÑñ]+)\\\\s*$")'
new_regex = r'Regex("(?i)\\\\s+(para|para la|para el|de|de la|del|en|en la|en el)\\\\s+([A-Za-z0-9ÁÉÍÓÚáéíóúÑñ]+)\\\\s*$")'

content = content.replace(old_regex, new_regex)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Updated context regex")
