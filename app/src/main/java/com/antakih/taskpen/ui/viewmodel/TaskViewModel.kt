package com.antakih.taskpen.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antakih.taskpen.data.local.dao.CategoryDao
import com.antakih.taskpen.data.local.dao.SubjectDao
import com.antakih.taskpen.data.local.dao.TaskDao
import com.antakih.taskpen.data.local.entities.CategoryEntity
import com.antakih.taskpen.data.local.entities.TaskEntity
import com.antakih.taskpen.domain.mlkit.DigitalInkHelper
import com.antakih.taskpen.domain.usecases.ParseHandwrittenTextUseCase
import com.antakih.taskpen.domain.usecases.ParseResult
import com.google.mlkit.vision.digitalink.Ink
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class TaskViewModel @Inject constructor(
    private val taskDao: TaskDao,
    private val categoryDao: CategoryDao,
    private val subjectDao: SubjectDao,
    private val parseHandwrittenTextUseCase: ParseHandwrittenTextUseCase,
    private val digitalInkHelper: DigitalInkHelper
) : ViewModel() {

    // Categoría actualmente seleccionada (null = vista global)
    private val _activeCategoryId = MutableStateFlow<String?>(null)
    val activeCategoryId: StateFlow<String?> = _activeCategoryId.asStateFlow()

    val allCategories: StateFlow<List<CategoryEntity>> = categoryDao.getAllCategories()
        .catch { e -> Log.e("TaskPenML", "Error leyendo categorías: ${e.message}", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingTasks: StateFlow<List<TaskEntity>> = taskDao.getPendingTasks()
        .catch { e -> Log.e("TaskPenML", "Error al leer tareas: ${e.message}", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            try {
                Log.d("TaskPenML", "Iniciando descarga/verificación del modelo de idioma...")
                val success = digitalInkHelper.downloadAndInitModel()
                Log.d("TaskPenML", if (success) "Modelo de Español listo" else "Fallo al preparar el modelo.")
            } catch (e: Throwable) {
                Log.e("TaskPenML", "Excepción durante la descarga del modelo: ${e.message}", e)
            }
        }
    }

    fun setActiveCategory(categoryId: String?) {
        _activeCategoryId.value = categoryId
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

    // Reconoce la tinta y su indentación espacial, devuelve el ParseResult para confirmación
    fun processInks(inksWithX: List<Pair<Ink, Float>>, onResult: (ParseResult) -> Unit) {
        viewModelScope.launch {
            try {
                val recognizedLines = mutableListOf<Pair<String, Float>>()
                for ((ink, minX) in inksWithX) {
                    val text = digitalInkHelper.recognizeText(ink)
                    if (text.isNotBlank()) {
                        recognizedLines.add(Pair(text, minX))
                    }
                }
                
                Log.d("TaskPenML", "=== ML KIT LEYÓ ===\n${recognizedLines.joinToString("\n") { "[${it.second}] ${it.first}" }}")

                if (recognizedLines.isNotEmpty()) {
                    val existingSubcategories = subjectDao.getAllSubjectsOnce()
                    val result = parseHandwrittenTextUseCase(
                        linesWithX = recognizedLines,
                        activeCategoryId = _activeCategoryId.value,
                        existingSubcategories = existingSubcategories
                    )
                    Log.d("TaskPenML", "Tareas: ${result.tasks.size}, Nuevas subcategorías: ${result.newSubcategories.size}")
                    onResult(result)
                } else {
                    onResult(ParseResult(emptyList(), emptyList()))
                }
            } catch (e: Throwable) {
                Log.e("TaskPenML", "Error al procesar la tinta: ${e.message}", e)
                onResult(ParseResult(emptyList(), emptyList()))
            }
        }
    }

    // Guarda las tareas confirmadas (y crea las nuevas subcategorías detectadas)
    fun saveParseResult(result: ParseResult) {
        viewModelScope.launch {
            try {
                if (result.newSubcategories.isNotEmpty()) {
                    subjectDao.insertSubjects(result.newSubcategories)
                    Log.d("TaskPenML", "Nuevas subcategorías creadas: ${result.newSubcategories.map { it.fullName }}")
                }
                taskDao.insertTasks(result.tasks)
                Log.d("TaskPenML", "Tareas guardadas exitosamente: ${result.tasks.size}")
            } catch (e: Throwable) {
                Log.e("TaskPenML", "Error al guardar tareas: ${e.message}", e)
            }
        }
    }

    // Mantiene retrocompatibilidad con el diálogo de confirmación (lista plana de tasks)
    fun saveTasks(tasks: List<TaskEntity>) {
        viewModelScope.launch {
            try {
                taskDao.insertTasks(tasks)
                Log.d("TaskPenML", "Tareas confirmadas guardadas: ${tasks.size}")
            } catch (e: Throwable) {
                Log.e("TaskPenML", "Error al guardar tareas: ${e.message}", e)
            }
        }
    }

    fun completeTask(taskId: String) {
        viewModelScope.launch {
            try { taskDao.markTaskAsCompleted(taskId) }
            catch (e: Throwable) { Log.e("TaskPenML", "Error al completar tarea: ${e.message}", e) }
        }
    }

    fun getSubtasks(parentTaskId: String) = taskDao.getSubtasks(parentTaskId)
}
