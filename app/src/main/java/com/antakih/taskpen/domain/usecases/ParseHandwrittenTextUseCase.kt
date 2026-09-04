package com.antakih.taskpen.domain.usecases

import com.antakih.taskpen.data.local.entities.TaskEntity
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject

class ParseHandwrittenTextUseCase @Inject constructor() {

    // 1. Regex de la arquitectura base
    private val taskMarkerRegex = Regex("^[-*•]\\s*(.*)")
    private val dateRegex = Regex("""(\d{1,2}[/-]\d{1,2}[/-]\d{2,4})|(\d{1,2}\s+(de\s+)?(?i)(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|octubre|noviembre|diciembre))""")
    private val timeRegex = Regex("""\b(1[0-2]|0?[1-9])\s*[:.]?\s*([0-5][0-9])?\s*(am|pm|a\.m\.|p\.m\.)\b|\b([01]?[0-9]|2[0-3])\s*:\s*([0-5][0-9])\b(?!\s*(am|pm))""", RegexOption.IGNORE_CASE)

    // 2. NUEVO: Regex para días relativos (Ej: el lunes, mañana, hoy)
    private val relativeDayRegex = Regex("""(?i)\b(el\s+)?(lunes|martes|miércoles|miercoles|jueves|viernes|sábado|sabado|domingo|hoy|mañana)\b""")

    // Lista temporal de materias
    private val knownSubjects = listOf("Adm. Proy", "Robótica", "SAP", "RP")

    operator fun invoke(rawText: String): List<TaskEntity> {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val extractedTasks = mutableListOf<TaskEntity>()

        var activeDate: Long? = null
        var activeSubjectId: String? = null

        for (line in lines) {
            val matchedSubject = knownSubjects.find { line.equals(it, ignoreCase = true) }
            if (matchedSubject != null) {
                activeSubjectId = matchedSubject
                continue
            }

            if (dateRegex.matches(line)) {
                activeDate = System.currentTimeMillis() // Placeholder (lo afinaremos después)
                continue
            }

            val taskMatch = taskMarkerRegex.find(line)
            if (taskMatch != null) {
                var taskText = taskMatch.groupValues[1]

                val inlineSubject = knownSubjects.find { taskText.contains(it, ignoreCase = true) }
                if (inlineSubject != null) {
                    activeSubjectId = inlineSubject
                    taskText = taskText.replace(inlineSubject, "", ignoreCase = true).trim()
                }

                // NUEVO: Extraer día relativo y calcular fecha exacta
                val relativeDayMatch = relativeDayRegex.find(taskText)
                if (relativeDayMatch != null) {
                    activeDate = calculateRelativeDate(relativeDayMatch.value)
                    taskText = taskText.replace(relativeDayMatch.value, "", ignoreCase = true).trim()
                }

                // ACTUALIZADO: Extraer hora y limpiar preposiciones ("a las")
                var hasSpecificTime = false
                val timeMatch = timeRegex.find(taskText)
                if (timeMatch != null) {
                    hasSpecificTime = true
                    taskText = taskText.replace(timeMatch.value, "", ignoreCase = true)

                    val prepositionRegex = Regex("""\s+a(\s+las?|)?\s*$""", RegexOption.IGNORE_CASE)
                    taskText = prepositionRegex.replace(taskText, "")
                    taskText = taskText.trim()
                }

                val finalTitle = taskText.replace(Regex("[,\\-:]$"), "").trim()

                // NUEVO: Blindaje. Si activeDate es null, asume que es para hoy.
                val finalDueDate = activeDate ?: System.currentTimeMillis()

                val newTask = TaskEntity(
                    id = UUID.randomUUID().toString(),
                    title = finalTitle,
                    description = null,
                    subjectId = activeSubjectId,
                    createdAt = System.currentTimeMillis(),
                    dueDate = finalDueDate,
                    hasSpecificTime = hasSpecificTime,
                    isCompleted = false,
                    calendarEventId = null
                )
                extractedTasks.add(newTask)
                continue
            }

            if (extractedTasks.isNotEmpty()) {
                val lastTask = extractedTasks.last()
                val newDescription = if (lastTask.description.isNullOrEmpty()) {
                    line
                } else {
                    "${lastTask.description}\n$line"
                }
                extractedTasks[extractedTasks.lastIndex] = lastTask.copy(description = newDescription)
            }
        }

        return extractedTasks
    }

    // NUEVO: Función matemática para calcular días usando el calendario de Android
    private fun calculateRelativeDate(relativeWord: String): Long {
        val calendar = Calendar.getInstance()

        val cleanWord = relativeWord.replace("el ", "", ignoreCase = true).trim().lowercase()

        when (cleanWord) {
            "hoy" -> { }
            "mañana" -> calendar.add(Calendar.DAY_OF_YEAR, 1)
            else -> {
                val targetDayOfWeek = when (cleanWord) {
                    "domingo" -> Calendar.SUNDAY
                    "lunes" -> Calendar.MONDAY
                    "martes" -> Calendar.TUESDAY
                    "miércoles", "miercoles" -> Calendar.WEDNESDAY
                    "jueves" -> Calendar.THURSDAY
                    "viernes" -> Calendar.FRIDAY
                    "sábado", "sabado" -> Calendar.SATURDAY
                    else -> -1
                }

                if (targetDayOfWeek != -1) {
                    val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                    var daysToAdd = targetDayOfWeek - currentDayOfWeek
                    if (daysToAdd <= 0) daysToAdd += 7
                    calendar.add(Calendar.DAY_OF_YEAR, daysToAdd)
                }
            }
        }
        return calendar.timeInMillis
    }
}