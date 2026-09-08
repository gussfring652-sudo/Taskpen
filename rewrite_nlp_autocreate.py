import re

file_path = 'app/src/main/java/com/antakih/taskpen/domain/usecases/ParseHandwrittenTextUseCase.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Update signature and add Result data class
old_sig = """    operator fun invoke(
        linesWithX: List<Pair<String, Float>>,
        activeCategoryId: String? = null,
        existingTags: List<SubjectEntity> = emptyList()
    ): List<TaskEntity> {"""

new_sig = """    data class Result(
        val tasks: List<TaskEntity>,
        val newTags: List<SubjectEntity>
    )

    operator fun invoke(
        linesWithX: List<Pair<String, Float>>,
        activeCategoryId: String? = null,
        existingTags: List<SubjectEntity> = emptyList()
    ): Result {"""

content = content.replace(old_sig, new_sig)

# 2. Add mutable new tags list and update existing tags to be mutable for the loop
old_extracted_tasks = "        val extractedTasks = mutableListOf<TaskEntity>()"
new_extracted_tasks = """        val extractedTasks = mutableListOf<TaskEntity>()
        val newTagsToSave = mutableListOf<SubjectEntity>()
        val currentKnownTags = existingTags.toMutableList()"""
content = content.replace(old_extracted_tasks, new_extracted_tasks)

# 3. Update the tagging logic to autocreate explicitly
old_tagging = """            // 1. Buscar explícitamente #etiqueta o [etiqueta]
            val explicitMatch = Regex("(#|\\[)([A-Za-z0-9ÁÉÍÓÚáéíóúÑñ]+)(\\])?").find(extractedTitle)
            if (explicitMatch != null) {
                val foundWord = explicitMatch.groupValues[2].trim().lowercase()
                val matchedTag = existingTags.find { it.fullName.lowercase() == foundWord || it.aliases.contains(foundWord) }
                if (matchedTag != null) {
                    tagId = matchedTag.id
                    extractedTitle = extractedTitle.replace(explicitMatch.value, "").trim()
                }
            }
            
            // 2. Buscar por contexto al final (para X, de X)
            if (tagId == null) {
                val contextMatch = Regex("(?i)\\s+(para|de)\\s+([A-Za-z0-9ÁÉÍÓÚáéíóúÑñ]+)\\s*$").find(extractedTitle)
                if (contextMatch != null) {
                    val foundWord = contextMatch.groupValues[2].trim().lowercase()
                    val matchedTag = existingTags.find { it.fullName.lowercase() == foundWord || it.aliases.contains(foundWord) }
                    if (matchedTag != null) {
                        tagId = matchedTag.id
                        extractedTitle = extractedTitle.replace(contextMatch.value, "").trim()
                    }
                }
            }"""

new_tagging = """            // 1. Buscar explícitamente #etiqueta o [etiqueta]
            val explicitMatch = Regex("(#|\\[)([A-Za-z0-9ÁÉÍÓÚáéíóúÑñ]+)(\\])?").find(extractedTitle)
            if (explicitMatch != null) {
                val originalWord = explicitMatch.groupValues[2].trim()
                val foundWord = originalWord.lowercase()
                val matchedTag = currentKnownTags.find { it.fullName.lowercase() == foundWord || it.aliases.contains(foundWord) }
                
                if (matchedTag != null) {
                    tagId = matchedTag.id
                } else {
                    // AUTO-CREAR LA ETIQUETA
                    val newTag = SubjectEntity(
                        id = UUID.randomUUID().toString(),
                        categoryId = activeCategoryId,
                        fullName = originalWord,
                        aliases = emptyList(),
                        semester = null
                    )
                    newTagsToSave.add(newTag)
                    currentKnownTags.add(newTag)
                    tagId = newTag.id
                }
                extractedTitle = extractedTitle.replace(explicitMatch.value, "").trim()
            }
            
            // 2. Buscar por contexto al final (para X, de X) SOLO en etiquetas existentes
            if (tagId == null) {
                val contextMatch = Regex("(?i)\\s+(para|de)\\s+([A-Za-z0-9ÁÉÍÓÚáéíóúÑñ]+)\\s*$").find(extractedTitle)
                if (contextMatch != null) {
                    val foundWord = contextMatch.groupValues[2].trim().lowercase()
                    val matchedTag = currentKnownTags.find { it.fullName.lowercase() == foundWord || it.aliases.contains(foundWord) }
                    if (matchedTag != null) {
                        tagId = matchedTag.id
                        extractedTitle = extractedTitle.replace(contextMatch.value, "").trim()
                    }
                }
            }"""
content = content.replace(old_tagging, new_tagging)

# 4. Return the new wrapper instead of extractedTasks
old_return = "        return extractedTasks"
new_return = "        return Result(tasks = extractedTasks, newTags = newTagsToSave)"
content = content.replace(old_return, new_return)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Rewrote ParseHandwrittenTextUseCase for autocreation")
