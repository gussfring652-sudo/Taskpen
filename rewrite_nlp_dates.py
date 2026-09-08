import re

file_path = 'app/src/main/java/com/antakih/taskpen/domain/usecases/ParseHandwrittenTextUseCase.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Add imports if missing
if "import java.util.Calendar" not in content:
    content = content.replace("import java.util.UUID", "import java.util.UUID\nimport java.util.Calendar")

# 1. We replace the loop body for extractedTasks to include date/time parsing
old_extraction = """            // 1. Buscar explícitamente #etiqueta o [etiqueta]
            val explicitMatch = Regex("(#|\\[)([A-Za-z0-9ÁÉÍÓÚáéíóúÑñ]+)(\\])?").find(extractedTitle)"""

new_extraction = """            // 0. Parse Date and Time
            val (textAfterDate, parsedDate) = extractDate(extractedTitle)
            val (textAfterTime, parsedTime) = extractTime(textAfterDate)
            extractedTitle = textAfterTime
            
            var dueDateMillis: Long? = null
            var hasTime = false
            
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

            // 1. Buscar explícitamente #etiqueta o [etiqueta]
            val explicitMatch = Regex("(#|\\[)([A-Za-z0-9ÁÉÍÓÚáéíóúÑñ]+)(\\])?").find(extractedTitle)"""
content = content.replace(old_extraction, new_extraction)

# Update TaskEntity creation to use dueDateMillis and hasTime
old_task_entity = """            val newTask = TaskEntity(
                id = UUID.randomUUID().toString(),
                title = finalTitle,
                description = null,
                categoryId = activeCategoryId,
                subcategoryId = tagId,
                parentTaskId = null,
                createdAt = System.currentTimeMillis(),
                dueDate = null,
                hasSpecificTime = false,"""

new_task_entity = """            val newTask = TaskEntity(
                id = UUID.randomUUID().toString(),
                title = finalTitle,
                description = null,
                categoryId = activeCategoryId,
                subcategoryId = tagId,
                parentTaskId = null,
                createdAt = System.currentTimeMillis(),
                dueDate = dueDateMillis,
                hasSpecificTime = hasTime,"""
content = content.replace(old_task_entity, new_task_entity)

# 2. Append helper functions before the closing brace of the class
helpers = """
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
        val dateSlashRegex = Regex("(?i)\\\\b(\\\\d{1,2})/(\\\\d{1,2})(?:/(\\\\d{2,4}))?\\\\b")
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
        val dateTextRegex = Regex("(?i)\\\\b(\\\\d{1,2})\\\\s+de\\\\s+(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|octubre|noviembre|diciembre)(?:\\\\s+de\\\\s+(\\\\d{2,4}))?\\\\b")
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
        val dateTomorrowRegex = Regex("(?i)\\\\b(?:para\\\\s+)?mañana\\\\b")
        dateTomorrowRegex.find(cleanedText)?.let { match ->
            cal = Calendar.getInstance()
            cal?.add(Calendar.DAY_OF_YEAR, 1)
            cleanedText = cleanedText.replace(match.value, "")
            return Pair(cleanedText.trim(), cal)
        }

        // 4. dias de la semana
        val dateWeekdayRegex = Regex("(?i)\\\\b(?:para el|próximo|proximo|el|para|este)\\\\s+(lunes|martes|miércoles|miercoles|jueves|viernes|sábado|sabado|domingo)\\\\b")
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
        val dateInXDaysRegex = Regex("(?i)\\\\ben\\\\s+(\\\\d+)\\\\s+días?\\\\b")
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
        val timeInXHoursRegex = Regex("(?i)\\\\ben\\\\s+(\\\\d+)\\\\s+horas?\\\\b")
        timeInXHoursRegex.find(cleanedText)?.let { match ->
            val now = Calendar.getInstance()
            val hours = match.groupValues[1].toInt()
            now.add(Calendar.HOUR_OF_DAY, hours)
            timePair = Pair(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
            cleanedText = cleanedText.replace(match.value, "")
            return Pair(cleanedText.trim(), timePair)
        }

        // 2. 14:30 or 2:30 pm
        val timeColonRegex = Regex("(?i)\\\\b(?:a las\\\\s+)?(\\\\d{1,2}):(\\\\d{2})(?:\\\\s*(am|pm|a\\\\.m\\\\.|p\\\\.m\\\\.|hrs|horas))?\\\\b")
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
        val timeLiteralRegex = Regex("(?i)\\\\b(?:a las\\\\s+)?(\\\\d{1,2})\\\\s*(am|pm|a\\\\.m\\\\.|p\\\\.m\\\\.)\\\\b")
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
}"""

content = re.sub(r'}\s*$', helpers, content)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Rewrote NLP with Dates")
