package com.antakih.taskpen.ui.screens

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.input.pointer.RequestDisallowInterceptTouchEvent
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.unit.dp
import com.antakih.taskpen.ui.viewmodel.TaskViewModel
import com.google.mlkit.vision.digitalink.Ink

// Estructura para guardar un punto individual con su posición, grosor, timestamp para ML Kit y presión
data class PathPoint(
    val position: Offset,
    val width: Float,
    val timestamp: Long = System.currentTimeMillis(),
    val pressure: Float = 0.5f
)

// Estructura para guardar un trazo completo (cachedPath fuera de equals/hashCode para preservar Undo/Redo)
data class StrokeState(
    val points: List<PathPoint>,
    val isEraser: Boolean = false
) {
    var cachedPath: Path? = null

    internal fun withCachedPath(path: Path): StrokeState {
        return copy().also { it.cachedPath = path }
    }
}

private class ActiveGestureTracker {
    var isActive: Boolean = false
    var activePointerId: Int = MotionEvent.INVALID_POINTER_ID
    var isErasing: Boolean = false
    var lastAcceptedPos: Offset? = null
    var lastSmoothedPressure: Float = 0.5f
    val currentPoints: MutableList<PathPoint> = mutableListOf()

    fun reset() {
        isActive = false
        activePointerId = MotionEvent.INVALID_POINTER_ID
        isErasing = false
        lastAcceptedPos = null
        lastSmoothedPressure = 0.5f
        currentPoints.clear()
    }
}

private fun strokeIntersects(stroke: StrokeState, eraserPos: Offset, eraserRadius: Float): Boolean {
    for (point in stroke.points) {
        val dist = (point.position - eraserPos).getDistance()
        if (dist <= eraserRadius + (point.width / 2f)) {
            return true
        }
    }
    return false
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DrawingScreen(
    viewModel: TaskViewModel,
    onFinished: () -> Unit = {},
    stylusOnly: Boolean = true
) {
    // Memoria de trazos para renderizar en pantalla e historial para Undo/Redo
    var strokesHistory by remember { mutableStateOf(listOf(emptyList<StrokeState>())) }
    var historyIndex by remember { mutableIntStateOf(0) }
    var activeStrokes by remember { mutableStateOf(emptyList<StrokeState>()) }
    var currentStrokeState by remember { mutableStateOf<StrokeState?>(null) }
    val strokes = if (currentStrokeState != null) activeStrokes else strokesHistory[historyIndex]

    fun commitStrokes(newStrokes: List<StrokeState>) {
        if (newStrokes == strokesHistory[historyIndex]) return
        val newHistory = strokesHistory.take(historyIndex + 1).toMutableList()
        newHistory.add(newStrokes)
        strokesHistory = newHistory
        historyIndex = newHistory.size - 1
    }

    // Controles de estado
    var isEraserMode by remember { mutableStateOf(false) }
    var palmRejectionEnabled by remember(stylusOnly) { mutableStateOf(stylusOnly) }
    val gestureTracker = remember { ActiveGestureTracker() }
    val disallowIntercept = remember { RequestDisallowInterceptTouchEvent() }

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
                .padding(8.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { if (historyIndex > 0) historyIndex-- },
                enabled = historyIndex > 0,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) { Text("<-") }

            Button(
                onClick = { if (historyIndex < strokesHistory.size - 1) historyIndex++ },
                enabled = historyIndex < strokesHistory.size - 1,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) { Text("->") }

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
                onClick = { commitStrokes(emptyList()) },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) { Text("Limpiar") }
        }

        // Lienzo (Canvas) Desplazable
        val vScrollState = rememberScrollState()
        val hScrollState = rememberScrollState()
        
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.White)
                .horizontalScroll(hScrollState)
                .verticalScroll(vScrollState)
        ) {
            Canvas(
                modifier = Modifier
                    .size(2000.dp, 2000.dp) // Tamaño máximo fijo del lienzo
                    .pointerInteropFilter(requestDisallowInterceptTouchEvent = disallowIntercept) { event ->
                        fun processWritingSample(x: Float, y: Float, rawPressure: Float, timestamp: Long) {
                            val pos = Offset(x, y)
                            if (!shouldAcceptPoint(gestureTracker.lastAcceptedPos, pos, MIN_POINT_DISTANCE_PX)) {
                                return
                            }
                            val smoothed = smoothPressure(gestureTracker.lastSmoothedPressure, rawPressure)
                            gestureTracker.lastSmoothedPressure = smoothed
                            gestureTracker.lastAcceptedPos = pos
                            val width = (MIN_STROKE_WIDTH_PX + basePenWidth * smoothed).coerceAtLeast(MIN_STROKE_WIDTH_PX)
                            gestureTracker.currentPoints.add(PathPoint(pos, width, timestamp, smoothed))
                        }

                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                                if (gestureTracker.isActive) {
                                    return@pointerInteropFilter true
                                }
                                val actionIndex = event.actionIndex
                                val toolType = event.getToolType(actionIndex)
                                val isStylusTool = toolType == MotionEvent.TOOL_TYPE_STYLUS ||
                                    toolType == MotionEvent.TOOL_TYPE_ERASER
                                if (palmRejectionEnabled && !isStylusTool) {
                                    return@pointerInteropFilter false
                                }

                                gestureTracker.reset()
                                gestureTracker.isActive = true
                                gestureTracker.activePointerId = event.getPointerId(actionIndex)
                                gestureTracker.isErasing = isEraserMode ||
                                    toolType == MotionEvent.TOOL_TYPE_ERASER ||
                                    isMotionEventStylusButtonPressed(event)
                                disallowIntercept(true)

                                activeStrokes = strokesHistory[historyIndex]
                                val startPos = Offset(event.getX(actionIndex), event.getY(actionIndex))

                                if (gestureTracker.isErasing) {
                                    activeStrokes = activeStrokes.filterNot {
                                        strokeIntersects(it, startPos, eraserWidth / 2f)
                                    }
                                    currentStrokeState = StrokeState(emptyList(), true)
                                } else {
                                    val initialPressure = normalizePressure(event.getPressure(actionIndex))
                                    val initialWidth = (MIN_STROKE_WIDTH_PX + basePenWidth * initialPressure)
                                        .coerceAtLeast(MIN_STROKE_WIDTH_PX)
                                    gestureTracker.lastAcceptedPos = startPos
                                    gestureTracker.lastSmoothedPressure = initialPressure
                                    gestureTracker.currentPoints.add(
                                        PathPoint(startPos, initialWidth, event.eventTime, initialPressure)
                                    )
                                    currentStrokeState = StrokeState(gestureTracker.currentPoints.toList(), false)
                                }
                                true
                            }

                            MotionEvent.ACTION_MOVE -> {
                                if (!gestureTracker.isActive) return@pointerInteropFilter false
                                val idx = event.findPointerIndex(gestureTracker.activePointerId)
                                if (idx == -1) return@pointerInteropFilter true

                                if (gestureTracker.isErasing) {
                                    var filtered = activeStrokes
                                    for (i in 0 until event.historySize) {
                                        val histPos = Offset(event.getHistoricalX(idx, i), event.getHistoricalY(idx, i))
                                        filtered = filtered.filterNot {
                                            strokeIntersects(it, histPos, eraserWidth / 2f)
                                        }
                                    }
                                    val currPos = Offset(event.getX(idx), event.getY(idx))
                                    activeStrokes = filtered.filterNot {
                                        strokeIntersects(it, currPos, eraserWidth / 2f)
                                    }
                                } else {
                                    val prevCount = gestureTracker.currentPoints.size
                                    for (i in 0 until event.historySize) {
                                        processWritingSample(
                                            x = event.getHistoricalX(idx, i),
                                            y = event.getHistoricalY(idx, i),
                                            rawPressure = event.getHistoricalPressure(idx, i),
                                            timestamp = event.getHistoricalEventTime(i)
                                        )
                                    }
                                    processWritingSample(
                                        x = event.getX(idx),
                                        y = event.getY(idx),
                                        rawPressure = event.getPressure(idx),
                                        timestamp = event.eventTime
                                    )
                                    if (gestureTracker.currentPoints.size != prevCount) {
                                        currentStrokeState = StrokeState(gestureTracker.currentPoints.toList(), false)
                                    }
                                }
                                true
                            }

                            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                                if (!gestureTracker.isActive) return@pointerInteropFilter false
                                val actionIndex = event.actionIndex
                                if (event.getPointerId(actionIndex) != gestureTracker.activePointerId) {
                                    return@pointerInteropFilter true
                                }

                                if (gestureTracker.isErasing) {
                                    val endPos = Offset(event.getX(actionIndex), event.getY(actionIndex))
                                    activeStrokes = activeStrokes.filterNot {
                                        strokeIntersects(it, endPos, eraserWidth / 2f)
                                    }
                                    commitStrokes(activeStrokes)
                                } else {
                                    processWritingSample(
                                        x = event.getX(actionIndex),
                                        y = event.getY(actionIndex),
                                        rawPressure = event.getPressure(actionIndex),
                                        timestamp = event.eventTime
                                    )
                                    val finalPoints = gestureTracker.currentPoints.toList()
                                    if (finalPoints.isNotEmpty()) {
                                        val cachedPath = buildVariableWidthStrokePath(finalPoints)
                                        val finalizedStroke = StrokeState(finalPoints, isEraser = false)
                                            .withCachedPath(cachedPath)
                                        commitStrokes(activeStrokes + finalizedStroke)
                                    }
                                }

                                gestureTracker.reset()
                                currentStrokeState = null
                                disallowIntercept(false)
                                true
                            }

                            MotionEvent.ACTION_CANCEL -> {
                                gestureTracker.reset()
                                currentStrokeState = null
                                disallowIntercept(false)
                                false
                            }

                            else -> gestureTracker.isActive
                        }
                    }
            ) {
                // Dibuja el historial de trazos usando el Path precalculado y cacheado
                strokes.forEach { stroke ->
                    drawCachedStroke(stroke)
                }

                // Dibuja el trazo actual en vivo sin recalcular Chaikin en cada frame
                currentStrokeState?.let { stroke ->
                    drawLiveStroke(stroke)
                }
            }
        } // Cierre del Box con Scroll

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
                    // Ordenamos los trazos espacialmente (de izquierda a derecha)
                    // Esto arregla el problema de cuando el usuario borra una palabra y la vuelve a escribir al principio
                    val sortedByX = lineStrokes.sortedBy { stroke ->
                        stroke.points.minOfOrNull { it.position.x } ?: Float.MAX_VALUE
                    }
                    sortedByX.forEach { stroke ->
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
                        viewModel.saveTasks(draftTasks)
                        showConfirmationDialog = false
                        commitStrokes(emptyList())
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

// Renderiza un trazo completado usando su Path cerrado precalculado y cacheado
private fun DrawScope.drawCachedStroke(stroke: StrokeState) {
    if (stroke.isEraser || stroke.points.isEmpty()) return
    val path = stroke.cachedPath ?: buildVariableWidthStrokePath(stroke.points).also {
        stroke.cachedPath = it
    }
    drawPath(
        path = path,
        color = Color.Black,
        style = Fill
    )
}

// Renderizado ligero en vivo durante ACTION_MOVE (sin subdivisión Chaikin por frame)
private fun DrawScope.drawLiveStroke(stroke: StrokeState) {
    if (stroke.isEraser || stroke.points.isEmpty()) return

    if (stroke.points.size == 1) {
        val p = stroke.points.first()
        drawCircle(
            color = Color.Black,
            radius = p.width / 2f,
            center = p.position
        )
    } else {
        for (i in 0 until stroke.points.size - 1) {
            val p1 = stroke.points[i]
            val p2 = stroke.points[i + 1]
            val avgWidth = (p1.width + p2.width) / 2f

            drawLine(
                color = Color.Black,
                start = p1.position,
                end = p2.position,
                strokeWidth = avgWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

// Detecta el botón lateral del Stylus / S-Pen o botones secundarios a partir de MotionEvent
private fun isMotionEventStylusButtonPressed(event: MotionEvent): Boolean {
    val mask = MotionEvent.BUTTON_STYLUS_PRIMARY or
        MotionEvent.BUTTON_STYLUS_SECONDARY or
        MotionEvent.BUTTON_SECONDARY or
        MotionEvent.BUTTON_TERTIARY
    return (event.buttonState and mask) != 0
}
