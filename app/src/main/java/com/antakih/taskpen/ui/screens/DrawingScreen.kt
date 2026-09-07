package com.antakih.taskpen.ui.screens

import android.os.Build
import android.view.MotionEvent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.antakih.taskpen.ui.viewmodel.TaskViewModel
import com.google.mlkit.vision.digitalink.Ink
import androidx.compose.foundation.verticalScroll

// Estructura para guardar un punto individual con su posición, grosor y timestamp para ML Kit
data class PathPoint(
    val position: Offset,
    val width: Float,
    val timestamp: Long = System.currentTimeMillis()
)

// Estructura para guardar un trazo completo
data class StrokeState(
    val points: List<PathPoint>,
    val isEraser: Boolean = false // Mantenemos por compatibilidad, aunque ya no guardaremos trazos de borrador
)

private fun strokeIntersects(stroke: StrokeState, eraserPos: Offset, eraserRadius: Float): Boolean {
    for (point in stroke.points) {
        val dist = (point.position - eraserPos).getDistance()
        if (dist <= eraserRadius + (point.width / 2f)) {
            return true
        }
    }
    return false
}

@Composable
fun DrawingScreen(viewModel: TaskViewModel, onFinished: () -> Unit = {}) {
    // Memoria de trazos para renderizar en pantalla
    var strokes by remember { mutableStateOf(emptyList<StrokeState>()) }
    var currentStrokeState by remember { mutableStateOf<StrokeState?>(null) }

    // Controles de estado
    var isEraserMode by remember { mutableStateOf(false) }
    var palmRejectionEnabled by remember { mutableStateOf(true) }

    // Configuración de grosor base para el trazo
    val basePenWidth = 14f
    val eraserWidth = 60f

    // Estado para la interfaz de confirmación
    var isProcessing by remember { mutableStateOf(false) }
    var showConfirmationDialog by remember { mutableStateOf(false) }
    var draftTasks by remember { mutableStateOf<List<com.antakih.taskpen.data.local.entities.TaskEntity>>(emptyList()) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
        // Barra de Herramientas
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = { isEraserMode = false },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!isEraserMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
            ) { Text("Escribir") }

            Button(
                onClick = { isEraserMode = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isEraserMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
            ) { Text("Borrar") }

            Button(onClick = { palmRejectionEnabled = !palmRejectionEnabled }) {
                Text(if (palmRejectionEnabled) "Palma: ON" else "Palma: OFF")
            }

            Button(
                onClick = {
                    strokes = emptyList()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) { Text("Limpiar") }
        }

        // Lienzo (Canvas)
        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.White)
                .graphicsLayer(alpha = 0.99f)
                .pointerInput(palmRejectionEnabled, isEraserMode) {
                    awaitEachGesture {
                        val down = awaitFirstDown()

                        if (palmRejectionEnabled && down.type == PointerType.Touch) {
                            return@awaitEachGesture
                        }

                        // Leemos el estado exacto del hardware en el instante del toque
                        val event = awaitPointerEvent()

                        // Detecta si el tipo es Eraser nativo o si el botón lateral del Stylus/S-Pen está presionado
                        val isHardwareEraser = isStylusButtonPressed(event, down.type)
                        val isActuallyErasing = isEraserMode || isHardwareEraser

                        // Calculamos el grosor según la presión recibida
                        var lastWidth = if (isActuallyErasing) {
                            eraserWidth
                        } else {
                            (2f + basePenWidth * down.pressure).coerceAtLeast(2f)
                        }

                        var currentStroke: StrokeState? = null

                        if (isActuallyErasing) {
                            strokes = strokes.filterNot { strokeIntersects(it, down.position, lastWidth / 2f) }
                        } else {
                            val initialPoint = PathPoint(down.position, lastWidth, System.currentTimeMillis())
                            currentStroke = StrokeState(listOf(initialPoint), false)
                            currentStrokeState = currentStroke
                        }

                        do {
                            val dragEvent = awaitPointerEvent()
                            val drag = dragEvent.changes.firstOrNull()
                            if (drag != null) {
                                val rawWidth = if (isActuallyErasing) {
                                    eraserWidth
                                } else {
                                    (2f + basePenWidth * drag.pressure).coerceAtLeast(2f)
                                }
                                // Filtro de suavizado
                                val smoothedWidth = lastWidth * 0.7f + rawWidth * 0.3f
                                lastWidth = smoothedWidth

                                if (isActuallyErasing) {
                                    strokes = strokes.filterNot { strokeIntersects(it, drag.position, smoothedWidth / 2f) }
                                } else {
                                    val newPoint = PathPoint(drag.position, smoothedWidth, System.currentTimeMillis())
                                    currentStroke?.let {
                                        currentStroke = it.copy(points = it.points + newPoint)
                                        currentStrokeState = currentStroke
                                    }
                                }

                                drag.consume()
                            }
                        } while (dragEvent.changes.any { it.pressed })

                        if (!isActuallyErasing) {
                            currentStrokeState?.let {
                                strokes = strokes + it
                            }
                            currentStrokeState = null
                        }
                    }
                }
        ) {
            // Dibuja el historial de trazos
            strokes.forEach { stroke ->
                drawVariableStroke(stroke)
            }

            // Dibuja el trazo actual en tiempo real
            currentStrokeState?.let { stroke ->
                drawVariableStroke(stroke)
            }
        }

        // Botón para procesar el texto con ML Kit
        Button(
            onClick = {
                if (strokes.isEmpty()) {
                    onFinished()
                    return@Button
                }

                // 1. Calcular Y promedio y altura de cada trazo
                val strokeCenters = strokes.map { stroke ->
                    val ys = stroke.points.map { it.position.y }
                    val top = ys.minOrNull() ?: 0f
                    val bottom = ys.maxOrNull() ?: 0f
                    val center = (top + bottom) / 2f
                    val height = bottom - top
                    Triple(stroke, center, height)
                }

                // 2. Calcular la altura promedio de los trazos (excluyendo trazos muy pequeños como puntos)
                val avgStrokeHeight = strokeCenters
                    .map { it.third }
                    .filter { it > 8f }
                    .average()
                    .toFloat()
                    .coerceAtLeast(30f)

                // El umbral es adaptativo: si los centroides difieren más del 70% de la altura promedio
                // de un trazo, son renglones distintos. Esto se adapta al tamaño de la letra del usuario.
                val lineThreshold = avgStrokeHeight * 0.70f

                // 3. Ordenar por la coordenada Y promedio
                val sortedStrokes = strokeCenters.sortedBy { it.second }

                // 4. Agrupar trazos en líneas con umbral adaptativo
                val lines = mutableListOf<List<StrokeState>>()
                var currentLine = mutableListOf<StrokeState>()
                var currentCenter = -1f

                sortedStrokes.forEach { (stroke, center, _) ->
                    if (currentLine.isEmpty()) {
                        currentLine.add(stroke)
                        currentCenter = center
                    } else {
                        if (kotlin.math.abs(center - currentCenter) < lineThreshold) {
                            currentLine.add(stroke)
                            currentCenter = (currentCenter * (currentLine.size - 1) + center) / currentLine.size
                        } else {
                            lines.add(currentLine)
                            currentLine = mutableListOf(stroke)
                            currentCenter = center
                        }
                    }
                }
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine)
                }

                // 5. Construir un objeto Ink por cada línea y calcular su X mínimo (indentación)
                val inksWithX = lines.map { lineStrokes ->
                    val minX = lineStrokes.minOfOrNull { stroke ->
                        stroke.points.minOfOrNull { it.position.x } ?: Float.MAX_VALUE
                    } ?: 0f

                    val inkBuilder = Ink.builder()
                    // Ordenamos los trazos cronológicamente (como los dibujó el usuario)
                    // Esto es VITAL para que ML Kit entienda la escritura
                    val sortedByTime = lineStrokes.sortedBy { stroke ->
                        stroke.points.firstOrNull()?.timestamp ?: 0L
                    }
                    sortedByTime.forEach { stroke ->
                        val strokeBuilder = Ink.Stroke.builder()
                        stroke.points.forEach { point ->
                            strokeBuilder.addPoint(Ink.Point.create(point.position.x, point.position.y, point.timestamp))
                        }
                        inkBuilder.addStroke(strokeBuilder.build())
                    }
                    Pair(inkBuilder.build(), minX)
                }
                
                isProcessing = true
                viewModel.processInks(inksWithX) { tasks ->
                    draftTasks = tasks
                    isProcessing = false
                    showConfirmationDialog = true
                }
            },
            enabled = !isProcessing,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            if (isProcessing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(if (isProcessing) "Procesando..." else "Transformar a Tareas")
        }
    }

    if (showConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmationDialog = false },
            title = { Text("Revisar Tareas Reconocidas") },
            text = {
                Column(modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    if (draftTasks.isEmpty()) {
                        Text("No se detectaron tareas legibles.")
                    } else {
                        draftTasks.forEachIndexed { index, task ->
                            val isSubtask = task.parentTaskId != null
                            val labelText = if (isSubtask) "Subtarea" else "Tarea principal"
                            val paddingStart = if (isSubtask) 24.dp else 0.dp

                            OutlinedTextField(
                                value = task.title,
                                onValueChange = { newTitle ->
                                    val mutableTasks = draftTasks.toMutableList()
                                    mutableTasks[index] = task.copy(title = newTitle)
                                    draftTasks = mutableTasks
                                },
                                label = { Text(labelText) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = paddingStart, bottom = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (draftTasks.isNotEmpty()) {
                    Button(onClick = {
                        // Guardamos con las correcciones del usuario en los títulos
                        viewModel.saveTasks(draftTasks)
                        showConfirmationDialog = false
                        strokes = emptyList()
                        onFinished()
                    }) {
                        Text("Guardar y Cerrar")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmationDialog = false }) {
                    Text(if (draftTasks.isEmpty()) "OK" else "Cancelar")
                }
            }
        )
    }
} // Cierra el Box
} // Cierra la función DrawingScreen

// Función para renderizar un trazo considerando la presión y uniones suavizadas
private fun DrawScope.drawVariableStroke(stroke: StrokeState) {
    val color = if (stroke.isEraser) Color.Transparent else Color.Black
    val blendMode = if (stroke.isEraser) BlendMode.Clear else BlendMode.SrcOver

    if (stroke.points.isEmpty()) return

    if (stroke.points.size == 1) {
        val p = stroke.points.first()
        drawCircle(
            color = color,
            radius = p.width / 2f,
            center = p.position,
            blendMode = blendMode
        )
    } else {
        for (i in 0 until stroke.points.size - 1) {
            val p1 = stroke.points[i]
            val p2 = stroke.points[i + 1]
            val avgWidth = (p1.width + p2.width) / 2f

            drawCircle(
                color = color,
                radius = p1.width / 2f,
                center = p1.position,
                blendMode = blendMode
            )

            drawLine(
                color = color,
                start = p1.position,
                end = p2.position,
                strokeWidth = avgWidth,
                cap = StrokeCap.Round,
                blendMode = blendMode
            )
        }

        val lastPoint = stroke.points.last()
        drawCircle(
            color = color,
            radius = lastPoint.width / 2f,
            center = lastPoint.position,
            blendMode = blendMode
        )
    }
}

// Función auxiliar para detectar el botón lateral del Stylus / S-Pen a nivel de Android y Compose
private fun isStylusButtonPressed(event: PointerEvent, pointerType: PointerType): Boolean {
    if (pointerType == PointerType.Eraser) return true

    val motionEvent = event.motionEvent
    if (motionEvent != null) {
        val buttonState = motionEvent.buttonState
        val isPrimaryStylus = (buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0
        val isSecondaryStylus = (buttonState and MotionEvent.BUTTON_STYLUS_SECONDARY) != 0
        if (isPrimaryStylus || isSecondaryStylus) return true
    }

    return event.buttons.isSecondaryPressed || event.buttons.isTertiaryPressed
}
