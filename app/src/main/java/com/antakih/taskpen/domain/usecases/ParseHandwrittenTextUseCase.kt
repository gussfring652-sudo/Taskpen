package com.antakih.taskpen.domain.usecases

import com.antakih.taskpen.data.local.entities.TaskEntity
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject

class ParseHandwrittenTextUseCase @Inject constructor() {

    private val subtaskMarkerRegex = Regex("^[-—–]\\s*(.+)")

    operator fun invoke(
        linesWithX: List<Pair<String, Float>>,
        activeCategoryId: String? = null
    ): List<TaskEntity> {

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

        return extractedTasks
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
}
