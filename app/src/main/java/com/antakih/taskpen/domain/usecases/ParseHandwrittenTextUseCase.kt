package com.antakih.taskpen.domain.usecases

import com.antakih.taskpen.data.local.entities.SubjectEntity
import com.antakih.taskpen.data.local.entities.TaskEntity
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject

/**
 * Resultado del parseo: tareas principales + subtareas + nuevas subcategorías a crear
 */
data class ParseResult(
    val tasks: List<TaskEntity>,
    val newSubcategories: List<SubjectEntity>
)

class ParseHandwrittenTextUseCase @Inject constructor() {

    // Marcadores EXPLÍCITOS de tarea (*, •, x). El guion (-) ya NO es marcador de tarea.
    private val taskMarkerRegex = Regex("""^([*•xX])\s*(.*)""")

    // El guion (-), guion medio (–) y guion largo (—) son marcadores de SUBTAREA
    private val subtaskMarkerRegex = Regex("""^[-–—]\s*(.+)""")

    private val dateRegex = Regex(
        """(\d{1,2}[/-]\d{1,2}[/-]\d{2,4})|(\d{1,2}\s+(de\s+)?(?i)(enero|ene|febrero|feb|marzo|mar|abril|abr|mayo|may|junio|jun|julio|jul|agosto|ago|septiembre|sep|octubre|oct|noviembre|nov|diciembre|dic)(\s+(de\s+)?\d{2,4})?)"""
    )
    private val timeRegex = Regex(
        """\b(1[0-2]|0?[1-9])\s*[:.]?\s*([0-5][0-9])?\s*(am|pm|a\.m\.|p\.m\.)\b|\b([01]?[0-9]|2[0-3])\s*:\s*([0-5][0-9])\b(?!\s*(am|pm))""",
        RegexOption.IGNORE_CASE
    )
    private val relativeDayRegex = Regex(
        """(?i)\b(el\s+)?(lunes|martes|miércoles|miercoles|jueves|viernes|sábado|sabado|domingo|hoy|mañana)\b"""
    )

    /**
     * @param rawText     Texto reconocido por ML Kit
     * @param activeCategoryId  ID de la categoría activa (puede ser null para "General")
     * @param existingSubcategories  Lista de subcategorías ya registradas en la BD para búsqueda fuzzy
     */
    operator fun invoke(
        linesWithX: List<Pair<String, Float>>,
        activeCategoryId: String? = null,
        existingSubcategories: List<SubjectEntity> = emptyList()
    ): ParseResult {

        val processedLines = mutableListOf<Pair<String, Float>>()
        for ((rawLine, minX) in linesWithX) {
            val normalized = normalizeMLKitOutput(rawLine)
            for (subLine in normalized.lines()) {
                val trimmed = subLine.trim()
                if (trimmed.isNotEmpty()) {
                    processedLines.add(Pair(trimmed, minX))
                }
            }
        }

        val extractedTasks = mutableListOf<TaskEntity>()
        val newSubcategoriesToCreate = mutableListOf<SubjectEntity>()

        var activeDate: Long? = null
        var activeSubcategoryId: String? = null
        var activeMainTaskX: Float? = null
        var isInsideDescription = false

        for ((line, minX) in processedLines) {
            
            var workingLine = line
            var isImportant = false
            if (workingLine.contains("*") || workingLine.contains("★")) {
                isImportant = true
                workingLine = workingLine.replace("*", "").replace("★", "").trim()
                workingLine = workingLine.replace(Regex("""\s{2,}"""), " ")
            }

            // ── 0. BLOQUE DE DESCRIPCIÓN ENTRE PARÉNTESIS (Ignora sangría)
            if (isInsideDescription) {
                val hasClosing = workingLine.contains(")")
                val cleanLine = workingLine.replace(")", "").trim()
                if (cleanLine.isNotEmpty()) {
                    val lastMainIdx = extractedTasks.indexOfLast { it.parentTaskId == null }
                    if (lastMainIdx != -1) {
                        val lastMain = extractedTasks[lastMainIdx]
                        val newDesc = if (lastMain.description.isNullOrBlank()) cleanLine else "${lastMain.description} $cleanLine"
                        extractedTasks[lastMainIdx] = lastMain.copy(description = newDesc)
                    }
                }
                if (hasClosing) isInsideDescription = false
                continue
            }

            if (workingLine.startsWith("(")) {
                val hasClosing = workingLine.indexOf(")") > 0
                val cleanLine = workingLine.replace("(", "").replace(")", "").trim()
                if (cleanLine.isNotEmpty()) {
                    val lastMainIdx = extractedTasks.indexOfLast { it.parentTaskId == null }
                    if (lastMainIdx != -1) {
                        val lastMain = extractedTasks[lastMainIdx]
                        val newDesc = if (lastMain.description.isNullOrBlank()) cleanLine else "${lastMain.description} $cleanLine"
                        extractedTasks[lastMainIdx] = lastMain.copy(description = newDesc)
                    }
                }
                if (!hasClosing) isInsideDescription = true
                continue
            }

            // ── 1. ¿Es una SUBTAREA? (empieza con - o indentada)
            val subtaskMatch = subtaskMarkerRegex.find(workingLine)
            val isIndented = activeMainTaskX != null && (minX - activeMainTaskX > 40f)

            if (subtaskMatch != null || isIndented) {
                val lastTask = extractedTasks.lastOrNull { it.parentTaskId == null }
                if (lastTask != null) {
                    val subtaskTitle = if (subtaskMatch != null) subtaskMatch.groupValues[1].trim() else workingLine
                    val subtask = TaskEntity(
                        id = UUID.randomUUID().toString(),
                        title = subtaskTitle,
                        description = null,
                        categoryId = activeCategoryId,
                        subcategoryId = lastTask.subcategoryId,
                        parentTaskId = lastTask.id,
                        createdAt = System.currentTimeMillis(),
                        dueDate = lastTask.dueDate,
                        hasSpecificTime = false,
                        isCompleted = false,
                        isImportant = isImportant,
                        calendarEventId = null
                    )
                    extractedTasks.add(subtask)
                }
                continue
            }

            // ── 2. Detectar subcategoría en la línea (fuzzy match con las existentes)
            var detectedSubcategoryId: String? = null
            var detectedSubcategoryName: String? = null

            val allSubjects = existingSubcategories.toMutableList() + newSubcategoriesToCreate
            for (subject in allSubjects) {
                val namesToCheck = listOf(subject.fullName) + subject.aliases
                val found = namesToCheck.find { alias ->
                    Regex("""\b${Regex.escape(alias)}\b""", RegexOption.IGNORE_CASE).containsMatchIn(workingLine)
                }
                if (found != null) {
                    detectedSubcategoryId = subject.id
                    activeSubcategoryId = subject.id
                    workingLine = workingLine.replace(Regex("""\b${Regex.escape(found)}\b""", RegexOption.IGNORE_CASE), "").trim()
                    // Si quedaron dobles espacios por quitar la palabra en medio, limpiarlos
                    workingLine = workingLine.replace(Regex("""\s{2,}"""), " ")
                    break
                }
            }

            // Si no encontró match, ¿la palabra podría ser una nueva subcategoría?
            // Solo la crea si viene acompañada de fecha u hora (es decir, parece una tarea estructurada)
            val hasDate = relativeDayRegex.containsMatchIn(workingLine) || dateRegex.containsMatchIn(workingLine)
            val hasTime = timeRegex.containsMatchIn(workingLine)

            // ── 3. Determinar si la línea es una TAREA PRINCIPAL
            val hasExplicitMarker = taskMarkerRegex.containsMatchIn(line)
            val isFirstTask = extractedTasks.none { it.parentTaskId == null }

            val isTask = hasExplicitMarker || hasDate || hasTime || detectedSubcategoryId != null || isFirstTask

            if (!isTask) {
                // Agregar como descripción de la última tarea principal
                val lastMainTask = extractedTasks.lastOrNull { it.parentTaskId == null }
                if (lastMainTask != null) {
                    val updatedDesc = if (lastMainTask.description.isNullOrEmpty()) line
                                     else "${lastMainTask.description} $line"
                    extractedTasks[extractedTasks.indexOfLast { it.parentTaskId == null }] =
                        lastMainTask.copy(description = updatedDesc)
                }
                continue
            }

            // ── 4. Extraer texto limpio (sin marcador explícito)
            val markerMatch = taskMarkerRegex.find(workingLine)
            var taskText = markerMatch?.groupValues?.get(2) ?: workingLine

            // ── 5. Extraer fecha relativa
            val relativeDayMatch = relativeDayRegex.find(taskText)
            if (relativeDayMatch != null) {
                activeDate = calculateRelativeDate(relativeDayMatch.value)
                taskText = taskText.replace(relativeDayMatch.value, "", ignoreCase = true).trim()
            } else {
                // Extraer fecha absoluta
                val absoluteDateMatch = dateRegex.find(taskText)
                if (absoluteDateMatch != null) {
                    activeDate = parseAbsoluteDate(absoluteDateMatch.value)
                    taskText = taskText.replace(absoluteDateMatch.value, "").trim()
                }
            }

            // ── 6. Extraer hora
            var hasSpecificTime = false
            val timeMatch = timeRegex.find(taskText)
            if (timeMatch != null) {
                hasSpecificTime = true
                activeDate = applyTimeToDate(activeDate, timeMatch.value)
                taskText = taskText.replace(timeMatch.value, "", ignoreCase = true)
                taskText = Regex("""\s+a(\s+las?|)?\s*$""", RegexOption.IGNORE_CASE).replace(taskText, "")
                taskText = taskText.trim()
            }

            // ── 7. Si la subcategoría fue detectada pero no existe en BD, marcarla para crearla
            if (detectedSubcategoryId == null && detectedSubcategoryName != null && activeCategoryId != null) {
                val newSubcat = SubjectEntity(
                    id = UUID.randomUUID().toString(),
                    categoryId = activeCategoryId,
                    fullName = detectedSubcategoryName,
                    aliases = listOf(detectedSubcategoryName.lowercase()),
                    semester = null
                )
                newSubcategoriesToCreate.add(newSubcat)
                detectedSubcategoryId = newSubcat.id
                activeSubcategoryId = newSubcat.id
            }

            // ── 8. Limpiar título
            val extractedTitle = taskText.replace(Regex("""[,\-:]$"""), "").trim()
            val finalTitle = cleanDanglingWords(extractedTitle)

            if (finalTitle.isBlank()) continue

            val newTask = TaskEntity(
                id = UUID.randomUUID().toString(),
                title = finalTitle,
                description = null,
                categoryId = activeCategoryId,
                subcategoryId = detectedSubcategoryId ?: activeSubcategoryId,
                parentTaskId = null,
                createdAt = System.currentTimeMillis(),
                dueDate = activeDate,
                hasSpecificTime = hasSpecificTime,
                isCompleted = false,
                calendarEventId = null
            )
            extractedTasks.add(newTask)
            activeMainTaskX = minX
        }

        return ParseResult(tasks = extractedTasks, newSubcategories = newSubcategoriesToCreate)
    }

    // ────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────

    private fun normalizeMLKitOutput(rawText: String): String {
        // Inserta \n antes de marcadores de tarea/subtarea pegados al texto anterior.
        // Soporta: guion (-), guion medio (–), guion largo (—), asterisco (*), bullet (•), x/X
        // Solo cuando el marcador está precedido por un espacio o un dígito (no parte de una palabra)
        return rawText.replace(Regex("""(?<=\s|\d)([-–—*•xX])"""), "\n$1").trim()
    }

    private fun calculateRelativeDate(relativeWord: String): Long {
        val calendar = Calendar.getInstance()
        applyDefaultTime(calendar)
        val cleanWord = relativeWord.replace("el ", "", ignoreCase = true).trim().lowercase()
        when (cleanWord) {
            "hoy" -> { }
            "mañana" -> calendar.add(Calendar.DAY_OF_YEAR, 1)
            else -> {
                val target = when (cleanWord) {
                    "domingo" -> Calendar.SUNDAY
                    "lunes" -> Calendar.MONDAY
                    "martes" -> Calendar.TUESDAY
                    "miércoles", "miercoles" -> Calendar.WEDNESDAY
                    "jueves" -> Calendar.THURSDAY
                    "viernes" -> Calendar.FRIDAY
                    "sábado", "sabado" -> Calendar.SATURDAY
                    else -> -1
                }
                if (target != -1) {
                    val today = calendar.get(Calendar.DAY_OF_WEEK)
                    var daysToAdd = target - today
                    if (daysToAdd <= 0) daysToAdd += 7
                    calendar.add(Calendar.DAY_OF_YEAR, daysToAdd)
                }
            }
        }
        return calendar.timeInMillis
    }

    private fun applyTimeToDate(baseDate: Long?, timeString: String): Long {
        val calendar = Calendar.getInstance()
        if (baseDate != null) calendar.timeInMillis = baseDate

        // Intentar extraer horas y minutos del string reconocido
        val h24 = Regex("""(\d{1,2}):(\d{2})""").find(timeString)
        val h12 = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm)""", RegexOption.IGNORE_CASE).find(timeString)

        when {
            h24 != null -> {
                calendar.set(Calendar.HOUR_OF_DAY, h24.groupValues[1].toInt())
                calendar.set(Calendar.MINUTE, h24.groupValues[2].toIntOrNull() ?: 0)
            }
            h12 != null -> {
                var hour = h12.groupValues[1].toInt()
                val minute = h12.groupValues[2].toIntOrNull() ?: 0
                val ampm = h12.groupValues[3].lowercase()
                if (ampm == "pm" && hour < 12) hour += 12
                if (ampm == "am" && hour == 12) hour = 0
                calendar.set(Calendar.HOUR_OF_DAY, hour)
                calendar.set(Calendar.MINUTE, minute)
            }
        }
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun parseAbsoluteDate(dateString: String): Long {
        val calendar = Calendar.getInstance()
        val wordRegex = Regex(
            """(\d{1,2})\s+(de\s+)?(enero|ene|febrero|feb|marzo|mar|abril|abr|mayo|may|junio|jun|julio|jul|agosto|ago|septiembre|sep|octubre|oct|noviembre|nov|diciembre|dic)(\s+(de\s+)?(\d{2,4}))?""",
            RegexOption.IGNORE_CASE
        )
        val wordMatch = wordRegex.find(dateString)
        if (wordMatch != null) {
            val day = wordMatch.groupValues[1].toIntOrNull() ?: calendar.get(Calendar.DAY_OF_MONTH)
            val month = monthFromString(wordMatch.groupValues[3].lowercase(), calendar)
            var year = calendar.get(Calendar.YEAR)
            wordMatch.groupValues[6].toIntOrNull()?.let { y ->
                year = if (y < 100) 2000 + y else y
            }
            calendar.set(year, month, day)
            applyDefaultTime(calendar)
            return calendar.timeInMillis
        }
        val cleanDate = dateString.replace(" ", "")
        val parts = cleanDate.split("/", "-")
        if (parts.size >= 2) {
            val day = parts[0].toIntOrNull() ?: calendar.get(Calendar.DAY_OF_MONTH)
            val month = (parts[1].toIntOrNull() ?: (calendar.get(Calendar.MONTH) + 1)) - 1
            var year = calendar.get(Calendar.YEAR)
            parts.getOrNull(2)?.toIntOrNull()?.let { y ->
                year = if (y < 100) 2000 + y else y
            }
            calendar.set(year, month, day)
            applyDefaultTime(calendar)
        }
        return calendar.timeInMillis
    }

    private fun monthFromString(monthStr: String, calendar: Calendar): Int = when (monthStr) {
        "enero", "ene" -> Calendar.JANUARY
        "febrero", "feb" -> Calendar.FEBRUARY
        "marzo", "mar" -> Calendar.MARCH
        "abril", "abr" -> Calendar.APRIL
        "mayo", "may" -> Calendar.MAY
        "junio", "jun" -> Calendar.JUNE
        "julio", "jul" -> Calendar.JULY
        "agosto", "ago" -> Calendar.AUGUST
        "septiembre", "sep" -> Calendar.SEPTEMBER
        "octubre", "oct" -> Calendar.OCTOBER
        "noviembre", "nov" -> Calendar.NOVEMBER
        "diciembre", "dic" -> Calendar.DECEMBER
        else -> calendar.get(Calendar.MONTH)
    }

    private fun applyDefaultTime(calendar: Calendar) {
        // TODO: En un futuro, leer la hora preferida del usuario desde las preferencias (Settings)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
    }

    private fun cleanDanglingWords(text: String): String {
        var clean = text.trim()
        val danglingRegex = "(?i)\\s+(de|en|para|el|la|los|las|a|un|una|del|al|sobre)$".toRegex()
        while (danglingRegex.containsMatchIn(clean)) {
            clean = clean.replace(danglingRegex, "").trim()
        }
        return clean.removeSuffix("-").removeSuffix("*").trim()
    }
}
