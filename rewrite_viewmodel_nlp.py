import re

file_path = 'app/src/main/java/com/antakih/taskpen/ui/viewmodel/TaskViewModel.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Update processInks
old_process_inks = """                    val tasks = parseHandwrittenTextUseCase(
                        linesWithX = recognizedLines,
                        activeCategoryId = currentCategory,
                        existingTags = existingTags
                    )
                    onResult(tasks)"""

new_process_inks = """                    val result = parseHandwrittenTextUseCase(
                        linesWithX = recognizedLines,
                        activeCategoryId = currentCategory,
                        existingTags = existingTags
                    )
                    // Guardar nuevas etiquetas encontradas explícitamente
                    result.newTags.forEach { subjectDao.insertSubject(it) }
                    onResult(result.tasks)"""
content = content.replace(old_process_inks, new_process_inks)

# Update processText
old_process_text = """                val tasks = parseHandwrittenTextUseCase(
                    linesWithX = lines,
                    activeCategoryId = currentCategory,
                    existingTags = existingTags
                )
                saveTasks(tasks)"""

new_process_text = """                val result = parseHandwrittenTextUseCase(
                    linesWithX = lines,
                    activeCategoryId = currentCategory,
                    existingTags = existingTags
                )
                // Guardar nuevas etiquetas encontradas explícitamente
                result.newTags.forEach { subjectDao.insertSubject(it) }
                saveTasks(result.tasks)"""
content = content.replace(old_process_text, new_process_text)


with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Rewrote TaskViewModel for autocreation")
