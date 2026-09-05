package com.antakih.taskpen.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antakih.taskpen.data.local.dao.TaskDao
import com.antakih.taskpen.data.local.entities.TaskEntity
import com.antakih.taskpen.domain.mlkit.DigitalInkHelper
import com.antakih.taskpen.domain.usecases.ParseHandwrittenTextUseCase
import com.google.mlkit.vision.digitalink.Ink
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TaskViewModel @Inject constructor(
    private val taskDao: TaskDao,
    private val parseHandwrittenTextUseCase: ParseHandwrittenTextUseCase,
    private val digitalInkHelper: DigitalInkHelper
) : ViewModel() {

    val pendingTasks: StateFlow<List<TaskEntity>> = taskDao.getPendingTasks()
        .catch { e ->
            Log.e("TaskPenML", "Error al leer tareas desde Room: ${e.message}", e)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        // NADA MÁS ABRIR LA APP: Se descarga/verifica el modelo en segundo plano de forma segura
        viewModelScope.launch {
            try {
                Log.d("TaskPenML", "Iniciando descarga/verificación del modelo de idioma...")
                val success = digitalInkHelper.downloadAndInitModel()
                if (success) {
                    Log.d("TaskPenML", "Modelo de Español listo para usarse")
                } else {
                    Log.e("TaskPenML", "Fallo al preparar el modelo. Revisa permisos o conexión.")
                }
            } catch (e: Throwable) {
                Log.e("TaskPenML", "Excepción durante la descarga del modelo: ${e.message}", e)
            }
        }
    }

    // Recibe la Tinta desde el lienzo, la traduce a texto y la guarda
    fun processInk(ink: Ink, onFinished: () -> Unit) {
        viewModelScope.launch {
            try {
                val recognizedText = digitalInkHelper.recognizeText(ink)

                Log.d("TaskPenML", "=== ML KIT LEYÓ ===\n[$recognizedText]")

                if (recognizedText.isNotBlank()) {
                    val extractedTasks = parseHandwrittenTextUseCase(recognizedText)
                    Log.d("TaskPenML", "Tareas extraídas por el Regex: ${extractedTasks.size}")

                    if (extractedTasks.isNotEmpty()) {
                        taskDao.insertTasks(extractedTasks)
                        Log.d("TaskPenML", "Tarea guardada en Room exitosamente")
                    } else {
                        Log.d("TaskPenML", "Advertencia: ML Kit leyó el texto, pero no coincidió con el formato de tarea.")
                    }
                }
            } catch (e: Throwable) {
                Log.e("TaskPenML", "Error al procesar/guardar la tarea: ${e.message}", e)
            } finally {
                onFinished()
            }
        }
    }

    // Procesa texto sin formato directamente (para pruebas/simulaciones)
    fun processScannedText(rawText: String) {
        viewModelScope.launch {
            try {
                val extractedTasks = parseHandwrittenTextUseCase(rawText)
                if (extractedTasks.isNotEmpty()) {
                    taskDao.insertTasks(extractedTasks)
                }
            } catch (e: Throwable) {
                Log.e("TaskPenML", "Error al insertar tarea escaneada: ${e.message}", e)
            }
        }
    }

    fun completeTask(taskId: String) {
        viewModelScope.launch {
            try {
                taskDao.markTaskAsCompleted(taskId)
            } catch (e: Throwable) {
                Log.e("TaskPenML", "Error al completar la tarea: ${e.message}", e)
            }
        }
    }
}
