package com.antakih.taskpen.domain.usecases

import com.antakih.taskpen.data.local.entities.TaskEntity
import com.antakih.taskpen.data.local.entities.SubjectEntity
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
        existingTags: List<SubjectEntity> = emptyList()
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

            val extractedTitle = workingLine.replace(Regex("[,\\-:]$"), "").trim()
            val finalTitle = cleanDanglingWords(extractedTitle)

            if (finalTitle.isBlank()) continue

            val newTask = TaskEntity(
                id = UUID.randomUUID().toString(),
                title = finalTitle,
                description = null,
                categoryId = activeCategoryId,
                subcategoryId = null,
                parentTaskId = null,
                createdAt = System.currentTimeMillis(),
                dueDate = null,
                hasSpecificTime = false,
                isCompleted = false,
                isImportant = isImportant,
                isDeleted = false,
                calendarEventId = null
            )
            extractedTasks.add(newTask)
            activeMainTaskX = minX
        }

        return Result(tasks = extractedTasks, newTags = newTagsToSave)
    }

    private fun normalizeMLKitOutput(rawText: String): String {
        return rawText.replace(Regex("(?<=\\s|\\d)([-—–*•xX])"), "\n$1").trim()
    }

    private fun cleanDanglingWords(text: String): String {
        var clean = text.trim()
        val danglingRegex = "(?i)\\s+(de|en|para|el|la|los|las|a|un|una|del|al|sobre)$".toRegex()
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

    private fun extractDate(text: String): Pair<String, Calendar?> {
        var cleanedText = text
        var cal: Calendar? = null
        val now = Calendar.getInstance()

        // 1. DD/MM/YYYY or DD/MM/YY
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
            return Pair(cleanedText.trim(), cal)
        }

        // 2. 13 de octubre de 2026
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
            return Pair(cleanedText.trim(), cal)
        }

        // 3. mañana
        val dateTomorrowRegex = Regex("(?i)\\b(?:para\\s+)?mañana\\b")
        dateTomorrowRegex.find(cleanedText)?.let { match ->
            cal = Calendar.getInstance()
            cal?.add(Calendar.DAY_OF_YEAR, 1)
            cleanedText = cleanedText.replace(match.value, "")
            return Pair(cleanedText.trim(), cal)
        }

        // 4. dias de la semana
        val dateWeekdayRegex = Regex("(?i)\\b(?:para el|próximo|proximo|el|para|este)\\s+(lunes|martes|miércoles|miercoles|jueves|viernes|sábado|sabado|domingo)\\b")
        dateWeekdayRegex.find(cleanedText)?.let { match ->
            cal = Calendar.getInstance()
            val targetDay = getDayOfWeek(match.groupValues[1])
            var currentDay = cal!!.get(Calendar.DAY_OF_WEEK)
            var daysToAdd = targetDay - currentDay
            if (daysToAdd <= 0) daysToAdd += 7
            cal?.add(Calendar.DAY_OF_YEAR, daysToAdd)
            cleanedText = cleanedText.replace(match.value, "")
            return Pair(cleanedText.trim(), cal)
        }

        // 5. en X dias
        val dateInXDaysRegex = Regex("(?i)\\ben\\s+(\\d+)\\s+días?\\b")
        dateInXDaysRegex.find(cleanedText)?.let { match ->
            cal = Calendar.getInstance()
            val days = match.groupValues[1].toInt()
            cal?.add(Calendar.DAY_OF_YEAR, days)
            cleanedText = cleanedText.replace(match.value, "")
            return Pair(cleanedText.trim(), cal)
        }

        return Pair(cleanedText, null)
    }

    private fun extractTime(text: String): Pair<String, Pair<Int, Int>?> {
        var cleanedText = text
        var timePair: Pair<Int, Int>? = null

        // 1. en X horas
        val timeInXHoursRegex = Regex("(?i)\\ben\\s+(\\d+)\\s+horas?\\b")
        timeInXHoursRegex.find(cleanedText)?.let { match ->
            val now = Calendar.getInstance()
            val hours = match.groupValues[1].toInt()
            now.add(Calendar.HOUR_OF_DAY, hours)
            timePair = Pair(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
            cleanedText = cleanedText.replace(match.value, "")
            return Pair(cleanedText.trim(), timePair)
        }

        // 2. 14:30 or 2:30 pm
        val timeColonRegex = Regex("(?i)\\b(?:a las\\s+)?(\\d{1,2}):(\\d{2})(?:\\s*(am|pm|a\\.m\\.|p\\.m\\.|hrs|horas))?\\b")
        timeColonRegex.find(cleanedText)?.let { match ->
            var hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toInt()
            val ampm = match.groupValues[3].lowercase().replace(".", "")
            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0
            timePair = Pair(hour, minute)
            cleanedText = cleanedText.replace(match.value, "")
            return Pair(cleanedText.trim(), timePair)
        }

        // 3. 3 pm
        val timeLiteralRegex = Regex("(?i)\\b(?:a las\\s+)?(\\d{1,2})\\s*(am|pm|a\\.m\\.|p\\.m\\.)\\b")
        timeLiteralRegex.find(cleanedText)?.let { match ->
            var hour = match.groupValues[1].toInt()
            val ampm = match.groupValues[2].lowercase().replace(".", "")
            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0
            timePair = Pair(hour, 0)
            cleanedText = cleanedText.replace(match.value, "")
            return Pair(cleanedText.trim(), timePair)
        }

        return Pair(cleanedText, null)
    }
}
