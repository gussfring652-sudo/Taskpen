package com.antakih.taskpen.domain.usecases

import android.util.Log
import com.antakih.taskpen.data.local.entities.SubjectEntity
import com.antakih.taskpen.data.local.entities.TaskEntity
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject

class ParseHandwrittenTextUseCase @Inject constructor() {

    private val subtaskMarkerRegex = Regex("^[-—–]\\s*(.+)")

    data class Result(
        val tasks: List<TaskEntity>,
        val newTags: List<SubjectEntity>
    )

    operator fun invoke(
        linesWithX: List<Pair<String, Float>>,
        activeCategoryId: String? = null,
        existingTags: List<SubjectEntity> = emptyList(),
        isCaseSensitive: Boolean = false,
        defaultPriority: Int = 1
    ): Result {

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
        val newTagsToSave = mutableListOf<SubjectEntity>()
        val currentKnownTags = existingTags.toMutableList()
        
        var activeMainTaskX: Float? = null
        var isInsideDescription = false

        for ((line, minX) in processedLines) {
            var workingLine = line
            var isImportant = false
            if (workingLine.contains("*") || workingLine.contains("★")) {
                isImportant = true
                workingLine = workingLine.replace("*", "").replace("★", "").trim()
                workingLine = workingLine.replace(Regex("\\s{2,}"), " ")
            }

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
                        subcategoryId = null,
                        parentTaskId = lastTask.id,
                        createdAt = System.currentTimeMillis(),
                        dueDate = lastTask.dueDate,
                        hasSpecificTime = false,
                        isCompleted = false,
                        isImportant = isImportant,
                        isDeleted = false,
                        calendarEventId = null
                    )
                    extractedTasks.add(subtask)
                }
                continue
            }

            var extractedTitle = workingLine.replace(Regex("[,\\-:]$"), "").trim()

            // 0. Parse Date and Time
            val (textAfterDate, parsedDate, dateMode) = extractDate(extractedTitle)
            val (textAfterTime, parsedTime, timeMode) = extractTime(textAfterDate)
            extractedTitle = textAfterTime
            
            var dueDateMillis: Long? = null
            var hasTime = false
            // Default mode is 1 (Cascade/Deadline). 
            // If either date or time was parsed with "en X" (mode 0), we set it to Exact (0).
            var reminderMode = if (dateMode == 0 || timeMode == 0) 0 else 1
            
            if (parsedDate != null || parsedTime != null) {
                val cal = Calendar.getInstance()
                if (parsedDate != null) {
                    cal.timeInMillis = parsedDate.timeInMillis
                }
                
                if (parsedTime != null) {
                    cal.set(Calendar.HOUR_OF_DAY, parsedTime.first)
                    cal.set(Calendar.MINUTE, parsedTime.second)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    hasTime = true
                    
                    // Si se dio hora pero no fecha, y la hora ya pasó, es para mañana
                    if (parsedDate == null && cal.timeInMillis < System.currentTimeMillis()) {
                        cal.add(Calendar.DAY_OF_YEAR, 1)
                    }
                } else {
                    cal.set(Calendar.HOUR_OF_DAY, 23)
                    cal.set(Calendar.MINUTE, 59)
                    cal.set(Calendar.SECOND, 59)
                    cal.set(Calendar.MILLISECOND, 999)
                }
                dueDateMillis = cal.timeInMillis
            }

            var tagId: String? = null
            var autoCategoryId: String? = activeCategoryId
            
            // 1. Buscar explícitamente #etiqueta o [etiqueta]
            val explicitTagRegex = Regex("(?i)[#\\[]([A-Za-z0-9ÁÉÍÓÚáéíóúÑñ]+)\\]?")
            val explicitMatch = explicitTagRegex.find(extractedTitle)
            if (explicitMatch != null) {
                val tagName = explicitMatch.groupValues[1]
                val matchedTag = currentKnownTags.find { 
                    if (isCaseSensitive) {
                        it.fullName == tagName || it.aliases.contains(tagName)
                    } else {
                        it.fullName.lowercase() == tagName.lowercase() || it.aliases.map { a -> a.lowercase() }.contains(tagName.lowercase())
                    }
                }
                if (matchedTag != null) {
                    tagId = matchedTag.id
                    autoCategoryId = matchedTag.categoryId ?: activeCategoryId
                } else {
                    val newTag = SubjectEntity(
                        id = UUID.randomUUID().toString(),
                        fullName = tagName,
                        aliases = emptyList(),
                        categoryId = activeCategoryId
                    )
                    newTagsToSave.add(newTag)
                    currentKnownTags.add(newTag)
                    tagId = newTag.id
                    autoCategoryId = activeCategoryId
                }
                extractedTitle = extractedTitle.replace(explicitMatch.value, "").trim()
            } else {
                // 2. Buscar por contexto al final (para X, de X, en X) SOLO en etiquetas existentes
                // Ahora no afecta al modo, eso ya se extrajo antes.
                val contextRegexStr = if (isCaseSensitive) {
                    "\\s+(para|para la|para el|de|de la|del|en|en la|en el)\\s+([A-Za-z0-9ÁÉÍÓÚáéíóúÑñ]+)\\s*$"
                } else {
                    "(?i)\\s+(para|para la|para el|de|de la|del|en|en la|en el)\\s+([A-Za-z0-9ÁÉÍÓÚáéíóúÑñ]+)\\s*$"
                }
                val contextRegex = Regex(contextRegexStr)
                contextRegex.find(extractedTitle)?.let { contextMatch ->
                    val foundWord = contextMatch.groupValues[2]
                    val matchedTag = currentKnownTags.find { 
                        if (isCaseSensitive) {
                            it.fullName == foundWord || it.aliases.contains(foundWord)
                        } else {
                            it.fullName.lowercase() == foundWord.lowercase() || it.aliases.map { a -> a.lowercase() }.contains(foundWord.lowercase())
                        }
                    }
                    if (matchedTag != null) {
                        tagId = matchedTag.id
                        autoCategoryId = matchedTag.categoryId ?: activeCategoryId
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
                categoryId = autoCategoryId,
                subcategoryId = tagId,
                parentTaskId = null,
                createdAt = System.currentTimeMillis(),
                dueDate = dueDateMillis,
                hasSpecificTime = hasTime,
                isCompleted = false,
                isImportant = isImportant,
                isDeleted = false,
                priority = defaultPriority,
                reminderMode = reminderMode,
                reminderOffsetMinutes = null,
                customCascadeIntervalMinutes = null,
                snoozeUntil = null,
                calendarEventId = null
            )
            extractedTasks.add(newTask)
            activeMainTaskX = minX
        }

        return Result(tasks = extractedTasks, newTags = newTagsToSave)
    }

    private fun normalizeMLKitOutput(rawText: String): String {
        return rawText.replace(Regex("(?<=\\s|\\d)([-—–*★xX])"), "\n$1").trim()
    }

    private fun cleanDanglingWords(text: String): String {
        var clean = text.trim()
        val danglingRegex = Regex("(?i)\\s+(de|en|para|el|la|los|las|a|un|una|del|al|sobre)$")
        while (danglingRegex.containsMatchIn(clean)) {
            clean = clean.replace(danglingRegex, "").trim()
        }
        return clean.removeSuffix("-").removeSuffix("*").trim()
    }

    private fun getMonthNumber(monthName: String): Int {
        return when (monthName.lowercase()) {
            "enero" -> 0
            "febrero" -> 1
            "marzo" -> 2
            "abril" -> 3
            "mayo" -> 4
            "junio" -> 5
            "julio" -> 6
            "agosto" -> 7
            "septiembre" -> 8
            "octubre" -> 9
            "noviembre" -> 10
            "diciembre" -> 11
            else -> 0
        }
    }

    private fun getDayOfWeek(dayName: String): Int {
        return when (dayName.lowercase().replace("é", "e").replace("á", "a")) {
            "domingo" -> Calendar.SUNDAY
            "lunes" -> Calendar.MONDAY
            "martes" -> Calendar.TUESDAY
            "miercoles" -> Calendar.WEDNESDAY
            "jueves" -> Calendar.THURSDAY
            "viernes" -> Calendar.FRIDAY
            "sabado" -> Calendar.SATURDAY
            else -> Calendar.MONDAY
        }
    }

    private fun extractDate(text: String): Triple<String, Calendar?, Int> {
        var cleanedText = text
        var cal: Calendar? = null
        val now = Calendar.getInstance()

        val dateSlashRegex = Regex("(?i)\\b(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?\\b")
        dateSlashRegex.find(cleanedText)?.let { match ->
            cal = Calendar.getInstance()
            val day = match.groupValues[1].toInt()
            val month = match.groupValues[2].toInt() - 1
            var yearStr = match.groupValues[3]
            var year = now.get(Calendar.YEAR)
            if (yearStr.isNotEmpty()) {
                year = yearStr.toInt()
                if (year < 100) year += 2000
            }
            cal?.set(year, month, day)
            cleanedText = cleanedText.replace(match.value, "")
            return Triple(cleanedText.trim(), cal, 1)
        }

        val dateTextRegex = Regex("(?i)\\b(\\d{1,2})\\s+de\\s+(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|octubre|noviembre|diciembre)(?:\\s+de\\s+(\\d{2,4}))?\\b")
        dateTextRegex.find(cleanedText)?.let { match ->
            cal = Calendar.getInstance()
            val day = match.groupValues[1].toInt()
            val month = getMonthNumber(match.groupValues[2])
            var yearStr = match.groupValues[3]
            var year = now.get(Calendar.YEAR)
            if (yearStr.isNotEmpty()) {
                year = yearStr.toInt()
                if (year < 100) year += 2000
            }
            cal?.set(year, month, day)
            cleanedText = cleanedText.replace(match.value, "")
            return Triple(cleanedText.trim(), cal, 1)
        }

        val dateTomorrowRegex = Regex("(?i)\\b(?:para\\s+)?mañana\\b")
        dateTomorrowRegex.find(cleanedText)?.let { match ->
            cal = Calendar.getInstance()
            cal?.add(Calendar.DAY_OF_YEAR, 1)
            cleanedText = cleanedText.replace(match.value, "")
            return Triple(cleanedText.trim(), cal, 1)
        }

        val dateWeekdayRegex = Regex("(?i)\\b(?:para el|próximo|proximo|el|para|este)\\s+(lunes|martes|miércoles|miercoles|jueves|viernes|sábado|sabado|domingo)\\b")
        dateWeekdayRegex.find(cleanedText)?.let { match ->
            cal = Calendar.getInstance()
            val targetDay = getDayOfWeek(match.groupValues[1])
            var currentDay = cal!!.get(Calendar.DAY_OF_WEEK)
            var daysToAdd = targetDay - currentDay
            if (daysToAdd <= 0) daysToAdd += 7
            cal?.add(Calendar.DAY_OF_YEAR, daysToAdd)
            cleanedText = cleanedText.replace(match.value, "")
            return Triple(cleanedText.trim(), cal, 1)
        }

        val dateInXRegex = Regex("(?i)\\ben\\s+(\\d+)\\s+(día|dia|mes|semana)(?:s|es)?\\b")
        dateInXRegex.find(cleanedText)?.let { match ->
            cal = Calendar.getInstance()
            val amount = match.groupValues[1].toInt()
            val unit = match.groupValues[2].lowercase()
            when {
                unit == "mes" -> cal?.add(Calendar.MONTH, amount)
                unit == "semana" -> cal?.add(Calendar.WEEK_OF_YEAR, amount)
                else -> cal?.add(Calendar.DAY_OF_YEAR, amount)
            }
            cleanedText = cleanedText.replace(match.value, "")
            return Triple(cleanedText.trim(), cal, 0)
        }

        return Triple(cleanedText, null, 1)
    }

    private fun extractTime(text: String): Triple<String, Pair<Int, Int>?, Int> {
        var cleanedText = text
        var timePair: Pair<Int, Int>? = null

        val timeInXRegex = Regex("(?i)\\ben\\s+(\\d+)\\s+(hora|minuto)s?\\b")
        timeInXRegex.find(cleanedText)?.let { match ->
            val now = Calendar.getInstance()
            val amount = match.groupValues[1].toInt()
            val unit = match.groupValues[2].lowercase()
            if (unit == "hora") {
                now.add(Calendar.HOUR_OF_DAY, amount)
            } else if (unit == "minuto") {
                now.add(Calendar.MINUTE, amount)
            }
            timePair = Pair(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
            cleanedText = cleanedText.replace(match.value, "")
            return Triple(cleanedText.trim(), timePair, 0)
        }

        val timeColonRegex = Regex("(?i)\\b(?:a las\\s+)?(\\d{1,2}):(\\d{2})(?:\\s*(am|pm|a\\.m\\.|p\\.m\\.|hrs|horas))?\\b")
        timeColonRegex.find(cleanedText)?.let { match ->
            var hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toInt()
            val ampm = match.groupValues[3].lowercase().replace(".", "")
            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0
            timePair = Pair(hour, minute)
            cleanedText = cleanedText.replace(match.value, "")
            return Triple(cleanedText.trim(), timePair, 1)
        }

        val timeLiteralRegex = Regex("(?i)\\b(?:a las\\s+)?(\\d{1,2})\\s*(am|pm|a\\.m\\.|p\\.m\\.)\\b")
        timeLiteralRegex.find(cleanedText)?.let { match ->
            var hour = match.groupValues[1].toInt()
            val ampm = match.groupValues[2].lowercase().replace(".", "")
            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0
            timePair = Pair(hour, 0)
            cleanedText = cleanedText.replace(match.value, "")
            return Triple(cleanedText.trim(), timePair, 1)
        }

        return Triple(cleanedText, null, 1)
    }
}
