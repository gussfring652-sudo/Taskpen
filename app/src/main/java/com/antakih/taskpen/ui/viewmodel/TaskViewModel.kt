package com.antakih.taskpen.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antakih.taskpen.data.local.dao.TaskDao
import com.antakih.taskpen.data.local.entities.TaskEntity
import com.antakih.taskpen.domain.usecases.ParseHandwrittenTextUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TaskViewModel @Inject constructor(
    private val taskDao: TaskDao,
    private val parseHandwrittenTextUseCase: ParseHandwrittenTextUseCase
) : ViewModel() {

    // Lee las tareas pendientes desde Room.
    // StateFlow avisa automáticamente a la pantalla (Compose) cada vez que hay un cambio en la base de datos.
    val pendingTasks: StateFlow<List<TaskEntity>> = taskDao.getPendingTasks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Esta es la función que llamaremos cuando el S-Pen termine de escanear
    fun processScannedText(rawText: String) {
        viewModelScope.launch {
            // 1. La máquina de estados extrae las tareas, fechas y contextos
            val extractedTasks = parseHandwrittenTextUseCase(rawText)

            // 2. Si detectó al menos una tarea, la guardamos en SQLite
            if (extractedTasks.isNotEmpty()) {
                taskDao.insertTasks(extractedTasks)
            }
        }
    }

    // Para marcar una tarea como completada (el checkbox de la interfaz)
    fun completeTask(taskId: String) {
        viewModelScope.launch {
            taskDao.markTaskAsCompleted(taskId)
        }
    }
}