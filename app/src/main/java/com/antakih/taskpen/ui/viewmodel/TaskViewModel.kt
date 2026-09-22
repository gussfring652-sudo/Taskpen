package com.antakih.taskpen.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antakih.taskpen.data.local.dao.CategoryDao
import com.antakih.taskpen.data.local.dao.SubjectDao
import com.antakih.taskpen.data.local.SettingsManager
import com.antakih.taskpen.data.local.dao.TaskDao
import com.antakih.taskpen.data.local.entities.CategoryEntity
import com.antakih.taskpen.data.local.entities.SubjectEntity
import com.antakih.taskpen.data.local.entities.TaskEntity
import com.antakih.taskpen.domain.mlkit.DigitalInkHelper
import com.antakih.taskpen.domain.usecases.ParseHandwrittenTextUseCase
import com.antakih.taskpen.notifications.TaskAlarmScheduler
import com.antakih.taskpen.notifications.SummaryScheduler
import com.google.mlkit.vision.digitalink.Ink
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class FilterState(
    val sortByDueDate: Boolean = false,
    val showOnlyImportant: Boolean = false,
    val searchQuery: String = "",
    val selectedCategories: Set<String> = emptySet(),
    val selectedTags: Set<String> = emptySet()
)

sealed class ViewContext {
    object General : ViewContext()
    object Today : ViewContext()
    object Tomorrow : ViewContext()
    object Postponed : ViewContext()
    object Important : ViewContext()
    object Completed : ViewContext()
    object Trash : ViewContext()
    data class Category(val categoryId: String) : ViewContext()
}

@HiltViewModel
class TaskViewModel @Inject constructor(
    private val taskDao: TaskDao,
    private val categoryDao: CategoryDao,
    private val subjectDao: SubjectDao,
    private val parseHandwrittenTextUseCase: ParseHandwrittenTextUseCase,
    private val digitalInkHelper: DigitalInkHelper,
    val settingsManager: SettingsManager,
    private val taskAlarmScheduler: TaskAlarmScheduler,
    val summaryScheduler: SummaryScheduler
) : ViewModel() {

    private val _activeContext = MutableStateFlow<ViewContext>(ViewContext.General)
    val activeContext: StateFlow<ViewContext> = _activeContext.asStateFlow()

    private val _filterState = MutableStateFlow(FilterState())
    val filterState: StateFlow<FilterState> = _filterState.asStateFlow()

    val isCaseSensitiveTags = settingsManager.isCaseSensitiveTags

    fun setCaseSensitiveTags(value: Boolean) {
        settingsManager.setCaseSensitiveTags(value)
    }

    fun updateFilterState(newState: FilterState) {
        _filterState.value = newState
    }
    
    fun setContext(context: ViewContext) {
        _activeContext.value = context
        if (context is ViewContext.Category) {
            viewModelScope.launch { categoryDao.updateLastUsed(context.categoryId) }
        }
    }

    val allCategories: StateFlow<List<CategoryEntity>> = categoryDao.getAllCategories()
        .catch { e -> Log.e("TaskPenML", "Error leyendo categorías: ${e.message}", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentCategories: StateFlow<List<CategoryEntity>> = categoryDao.getRecentCategories(4)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeTasks: StateFlow<List<TaskEntity>> = taskDao.getAllActiveTasks()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        
    val deletedTasks: StateFlow<List<TaskEntity>> = taskDao.getDeletedTasks()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val defaultTaskPriority: StateFlow<Int> = settingsManager.defaultTaskPriority
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    fun setDefaultTaskPriority(priority: Int) {
        viewModelScope.launch {
            settingsManager.setDefaultTaskPriority(priority)
        }
    }

    val allTags: StateFlow<List<SubjectEntity>> = subjectDao.getAllSubjectsOnceFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            try {
                // Delete tasks that have been in trash for more than 30 days
                val thirtyDaysInMillis = 30L * 24 * 60 * 60 * 1000
                taskDao.deleteOldTrashTasks(System.currentTimeMillis() - thirtyDaysInMillis)
            } catch (e: Throwable) {
                Log.e("TaskViewModel", "Error limpiando papelera: ${e.message}")
            }
            
            try {
                Log.d("TaskPenML", "Iniciando descarga/verificación del modelo de idioma...")
                val success = digitalInkHelper.downloadAndInitModel()
                Log.d("TaskPenML", if (success) "Modelo de Español listo" else "Fallo al preparar el modelo.")
            } catch (e: Throwable) {
                Log.e("TaskPenML", "Excepción durante la descarga del modelo: ${e.message}", e)
            }
        }
    }



    
    fun updateCategory(id: String, name: String, colorHex: String) {
        viewModelScope.launch {
            val cat = categoryDao.getCategoryById(id)
            if (cat != null) {
                categoryDao.updateCategory(cat.copy(name = name, colorHex = colorHex))
            }
        }
    }

    fun deleteCategory(id: String) {
        viewModelScope.launch {
            categoryDao.deleteCategory(id)
            // Should also move tasks to Global (null) category?
            val tasks = taskDao.getAllActiveTasks().first()
            tasks.filter { it.categoryId == id }.forEach {
                taskDao.insertTask(it.copy(categoryId = null))
            }
        }
    }

    fun createCategory(name: String, colorHex: String = "#6200EE") {
        viewModelScope.launch {
            val category = CategoryEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                colorHex = colorHex
            )
            categoryDao.insertCategory(category)
        }
    }

    fun createTag(categoryId: String?, name: String, aliases: List<String>) {
        viewModelScope.launch {
            val tag = SubjectEntity(
                id = UUID.randomUUID().toString(),
                categoryId = categoryId,
                fullName = name,
                aliases = aliases,
                semester = null
            )
            subjectDao.insertSubject(tag)
        }
    }

    fun updateTag(id: String, categoryId: String?, name: String, aliases: List<String>) {
        viewModelScope.launch {
            val tag = SubjectEntity(
                id = id,
                categoryId = categoryId,
                fullName = name,
                aliases = aliases,
                semester = null
            )
            subjectDao.updateSubject(tag)
        }
    }

    fun deleteTag(id: String) {
        viewModelScope.launch {
            subjectDao.deleteSubject(id)
        }
    }

    // Reconoce la tinta y su indentación espacial, devuelve la lista de tareas
    fun processInks(inksWithX: List<Pair<Ink, Float>>, onResult: (List<TaskEntity>) -> Unit) {
        viewModelScope.launch {
            try {
                val recognizedLines = mutableListOf<Pair<String, Float>>()
                for ((ink, minX) in inksWithX) {
                    val text = digitalInkHelper.recognizeText(ink)
                    if (text.isNotBlank()) {
                        recognizedLines.add(Pair(text, minX))
                    }
                }
                
                if (recognizedLines.isNotEmpty()) {
                    val currentCategory = (_activeContext.value as? ViewContext.Category)?.categoryId
                    val existingTags = subjectDao.getAllSubjectsOnce()
                    val result = parseHandwrittenTextUseCase(
                        linesWithX = recognizedLines,
                        activeCategoryId = currentCategory,
                        existingTags = existingTags,
                        isCaseSensitive = isCaseSensitiveTags.value,
                        defaultPriority = defaultTaskPriority.value
                    )
                    // Guardar nuevas etiquetas encontradas explícitamente
                    result.newTags.forEach { subjectDao.insertSubject(it) }
                    onResult(result.tasks)
                } else {
                    onResult(emptyList())
                }
            } catch (e: Throwable) {
                Log.e("TaskPenML", "Error al procesar la tinta: ${e.message}", e)
                onResult(emptyList())
            }
        }
    }

    fun processText(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            try {
                val lines = text.split("\n").map { Pair(it, 0f) }
                val currentCategory = (_activeContext.value as? ViewContext.Category)?.categoryId
                val existingTags = subjectDao.getAllSubjectsOnce()
                val result = parseHandwrittenTextUseCase(
                    linesWithX = lines,
                    activeCategoryId = currentCategory,
                    existingTags = existingTags,
                    isCaseSensitive = isCaseSensitiveTags.value,
                    defaultPriority = defaultTaskPriority.value
                )
                // Guardar nuevas etiquetas encontradas explícitamente
                result.newTags.forEach { subjectDao.insertSubject(it) }
                saveTasks(result.tasks)
            } catch (e: Throwable) {
                Log.e("TaskPenML", "Error al procesar texto manual: ${e.message}", e)
            }
        }
    }

    // Mantiene retrocompatibilidad con el diálogo de confirmación (lista plana de tasks)
    fun saveTasks(tasks: List<TaskEntity>) {
        viewModelScope.launch {
            try {
                taskDao.insertTasks(tasks)
                // Programar alarmas en cascada para tareas con dueDate
                tasks.filter { it.dueDate != null && !it.isCompleted }.forEach { task ->
                    taskAlarmScheduler.scheduleAlarm(task)
                }
                Log.d("TaskPenML", "Tareas confirmadas guardadas: ${tasks.size}")
            } catch (e: Throwable) {
                Log.e("TaskPenML", "Error al guardar tareas: ${e.message}", e)
            }
        }
    }

    fun completeTask(taskId: String) {
        viewModelScope.launch {
            try {
                val task = taskDao.getTaskById(taskId)
                if (task != null) {
                    if (task.recurrenceIntervalMinutes != null && task.dueDate != null) {
                        // It's a recurring task
                        val nextDueDate = task.dueDate + (task.recurrenceIntervalMinutes * 60_000L)
                        var shouldClone = true
                        var nextOccurrences = task.recurrenceMaxOccurrences

                        if (task.recurrenceMaxOccurrences != null) {
                            if (task.recurrenceMaxOccurrences <= 1) {
                                shouldClone = false
                            } else {
                                nextOccurrences = task.recurrenceMaxOccurrences - 1
                            }
                        }

                        if (task.recurrenceEndDate != null && nextDueDate > task.recurrenceEndDate) {
                            shouldClone = false
                        }

                        val completedOriginal = task.copy(
                            isCompleted = true,
                            recurrenceIntervalMinutes = null,
                            recurrenceMaxOccurrences = null,
                            recurrenceEndDate = null
                        )
                        taskDao.insertTask(completedOriginal)

                        if (shouldClone) {
                            val nextTask = task.copy(
                                id = UUID.randomUUID().toString(),
                                dueDate = nextDueDate,
                                createdAt = System.currentTimeMillis(),
                                isCompleted = false,
                                recurrenceMaxOccurrences = nextOccurrences
                            )
                            taskDao.insertTask(nextTask)
                            taskAlarmScheduler.scheduleAlarm(nextTask)
                        }
                    } else {
                        // Normal task
                        taskDao.insertTask(task.copy(isCompleted = true))
                    }
                    // Cancel alarm for completed original task
                    taskAlarmScheduler.cancelAlarm(task.id)
                }
            }
            catch (e: Throwable) { Log.e("TaskPenML", "Error al completar tarea: ${e.message}", e) }
        }
    }

    fun toggleTaskImportance(taskId: String, isImportant: Boolean) {
        viewModelScope.launch {
            try { taskDao.updateTaskImportance(taskId, isImportant) }
            catch (e: Throwable) { Log.e("TaskPenML", "Error al actualizar importancia: ${e.message}", e) }
        }
    }

    fun uncompleteTask(taskId: String) {
        viewModelScope.launch {
            try { taskDao.unmarkTaskAsCompleted(taskId) }
            catch (e: Throwable) { Log.e("TaskPenML", "Error al desmarcar tarea: ${e.message}", e) }
        }
    }

    fun moveToTrash(taskId: String) {
        viewModelScope.launch {
            try { taskDao.moveToTrash(taskId) }
            catch (e: Throwable) { Log.e("TaskPenML", "Error al mover a papelera: ${e.message}", e) }
        }
    }

    fun restoreTask(taskId: String) {
        viewModelScope.launch {
            try { taskDao.restoreFromTrash(taskId) }
            catch (e: Throwable) { Log.e("TaskPenML", "Error al restaurar tarea: ${e.message}", e) }
        }
    }

    fun permanentlyDeleteTask(taskId: String) {
        viewModelScope.launch {
            try { taskDao.permanentlyDeleteTask(taskId) }
            catch (e: Throwable) { Log.e("TaskPenML", "Error al eliminar tarea: ${e.message}", e) }
        }
    }

    fun updateTaskDetails(id: String, title: String, description: String?, dueDate: Long?, categoryId: String?, subcategoryId: String?) {
        viewModelScope.launch {
            val allTasks = taskDao.getAllActiveTasks().first()
            val task = allTasks.find { it.id == id }
            if (task != null) {
                val updatedTask = task.copy(title = title, description = description, dueDate = dueDate, categoryId = categoryId, subcategoryId = subcategoryId)
                taskDao.insertTask(updatedTask)
                // Reprogramar alarma si la tarea tiene dueDate
                if (dueDate != null && !updatedTask.isCompleted) {
                    taskAlarmScheduler.scheduleAlarm(updatedTask)
                } else {
                    taskAlarmScheduler.cancelAlarm(id)
                }
            }
        }
    }

    fun updateTaskReminder(taskId: String, priority: Int, offsetMinutes: Int?, reminderMode: Int, customCascadeInterval: Int? = null, recurrenceInterval: Int? = null, recurrenceMaxOccurrences: Int? = null, recurrenceEndDate: Long? = null) {
        viewModelScope.launch {
            try {
                val task = taskDao.getTaskById(taskId)
                if (task != null) {
                    val updatedTask = task.copy(
                        priority = priority,
                        reminderOffsetMinutes = offsetMinutes,
                        reminderMode = reminderMode,
                        customCascadeIntervalMinutes = customCascadeInterval,
                        recurrenceIntervalMinutes = recurrenceInterval,
                        recurrenceMaxOccurrences = recurrenceMaxOccurrences,
                        recurrenceEndDate = recurrenceEndDate
                    )
                    taskDao.insertTask(updatedTask)
                    
                    // Reprogramar alarmas si es necesario
                    taskAlarmScheduler.scheduleAlarm(updatedTask)
                }
            } catch (e: Throwable) {
                Log.e("TaskPenML", "Error al actualizar recordatorio: ${e.message}", e)
            }
        }
    }

    // Flows para la pantalla de Settings
    val morningSummaryHour: Flow<Int> = settingsManager.morningSummaryHour
    val morningSummaryMinute: Flow<Int> = settingsManager.morningSummaryMinute
    val eveningSummaryHour: Flow<Int> = settingsManager.eveningSummaryHour
    val eveningSummaryMinute: Flow<Int> = settingsManager.eveningSummaryMinute

    fun getSubtasks(parentTaskId: String) = taskDao.getSubtasks(parentTaskId)
}
