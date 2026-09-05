package com.antakih.taskpen.ui.screens

import android.view.MotionEvent
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

// Estructura para guardar un punto individual con su posición y el grosor derivado de la presión
data class PathPoint(
    val position: Offset,
    val width: Float
)

// Estructura para guardar un trazo completo compuesto por puntos con presión dinámica
data class StrokeState(
    val points: List<PathPoint>,
    val isEraser: Boolean
)

@Composable
fun DrawingScreen(viewModel: TaskViewModel) {
    // Memoria de trazos para renderizar en pantalla
    var strokes by remember { mutableStateOf(emptyList<StrokeState>()) }
    var currentStrokeState by remember { mutableStateOf<StrokeState?>(null) }

    // ML KIT: Objeto Ink.Builder donde se acumulan las coordenadas físicas y timestamps para el modelo de IA
    var inkBuilder by remember { mutableStateOf(Ink.builder()) }

    // Controles de estado
    var isEraserMode by remember { mutableStateOf(false) }
    var palmRejectionEnabled by remember { mutableStateOf(true) }

    // Configuración de grosor base para el trazo
    val basePenWidth = 14f
    val eraserWidth = 60f

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
                    // ML KIT: Vacía también los trazos guardados para la IA
                    inkBuilder = Ink.builder()
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

                        // ML KIT: Si estamos escribiendo (no borrando), iniciamos un nuevo trazo para la IA
                        var strokeBuilder: Ink.Stroke.Builder? = null
                        if (!isActuallyErasing) {
                            strokeBuilder = Ink.Stroke.builder()
                            strokeBuilder.addPoint(
                                Ink.Point.create(down.position.x, down.position.y, System.currentTimeMillis())
                            )
                        }

                        // Calculamos el grosor según la presión recibida
                        var lastWidth = if (isActuallyErasing) {
                            eraserWidth
                        } else {
                            (2f + basePenWidth * down.pressure).coerceAtLeast(2f)
                        }

                        val initialPoint = PathPoint(down.position, lastWidth)
                        var currentStroke = StrokeState(listOf(initialPoint), isActuallyErasing)
                        currentStrokeState = currentStroke

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

                                val newPoint = PathPoint(drag.position, smoothedWidth)
                                currentStroke = currentStroke.copy(points = currentStroke.points + newPoint)
                                currentStrokeState = currentStroke

                                // ML KIT: Registramos las coordenadas y timestamp de movimiento
                                if (!isActuallyErasing) {
                                    strokeBuilder?.addPoint(
                                        Ink.Point.create(drag.position.x, drag.position.y, System.currentTimeMillis())
                                    )
                                }

                                drag.consume()
                            }
                        } while (dragEvent.changes.any { it.pressed })

                        currentStrokeState?.let {
                            strokes = strokes + it
                        }
                        currentStrokeState = null

                        // ML KIT: Al levantar el S-Pen, cerramos el trazo y lo agregamos a inkBuilder
                        if (!isActuallyErasing && strokeBuilder != null) {
                            inkBuilder.addStroke(strokeBuilder.build())
                        }
                    }
                }
        ) {
            // Dibuja el historial de trazos con sensibilidad a la presión
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
                val inkToProcess = inkBuilder.build()
                viewModel.processInk(inkToProcess) {
                    // Al finalizar la conversión y guardado en DB, limpiamos el lienzo
                    strokes = emptyList()
                    inkBuilder = Ink.builder()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("Transformar a Tareas")
        }
    }
}

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
