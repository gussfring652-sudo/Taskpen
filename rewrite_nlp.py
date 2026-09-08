import re

file_path = 'app/src/main/java/com/antakih/taskpen/domain/usecases/ParseHandwrittenTextUseCase.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Add SubjectEntity import
content = content.replace('import com.antakih.taskpen.data.local.entities.TaskEntity', 
'import com.antakih.taskpen.data.local.entities.TaskEntity\nimport com.antakih.taskpen.data.local.entities.SubjectEntity')

# Update invoke signature
old_sig = """    operator fun invoke(
        linesWithX: List<Pair<String, Float>>,
        activeCategoryId: String? = null
    ): List<TaskEntity> {"""
new_sig = """    operator fun invoke(
        linesWithX: List<Pair<String, Float>>,
        activeCategoryId: String? = null,
        existingTags: List<SubjectEntity> = emptyList()
    ): List<TaskEntity> {"""
content = content.replace(old_sig, new_sig)

# Add logic for finding tags
old_loop_tail = """            val extractedTitle = workingLine.replace(Regex("[,\\-:]$"), "").trim()
            val finalTitle = cleanDanglingWords(extractedTitle)

            if (finalTitle.isBlank()) continue

            val newTask = TaskEntity(
                id = UUID.randomUUID().toString(),
                title = finalTitle,
                description = null,
                categoryId = activeCategoryId,
                subcategoryId = null,"""
new_loop_tail = """            var extractedTitle = workingLine.replace(Regex("[,\\-:]$"), "").trim()
            var tagId: String? = null

            // 1. Buscar explícitamente #etiqueta o [etiqueta]
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
            }

            val finalTitle = cleanDanglingWords(extractedTitle)

            if (finalTitle.isBlank()) continue

            val newTask = TaskEntity(
                id = UUID.randomUUID().toString(),
                title = finalTitle,
                description = null,
                categoryId = activeCategoryId,
                subcategoryId = tagId,"""
content = content.replace(old_loop_tail, new_loop_tail)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Rewrote NLP")
