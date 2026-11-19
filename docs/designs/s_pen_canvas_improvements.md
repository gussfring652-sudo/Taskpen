# Mejora de sensibilidad del S-Pen, rechazo de palma y renderizado de tinta en el lienzo

*   **Author:** antak
*   **Status:** Proposed
*   **Date:** 2026-11-19

--------------------------------------------------------------------------------

## Overview

Este documento define las mejoras al lienzo de escritura a mano (`DrawingScreen.kt`) de Taskpen para capturar entrada de alta frecuencia del S-Pen mediante `pointerInteropFilter`, aplicar rechazo de palma a nivel de `MotionEvent` y reemplazar el renderizado actual de círculos y segmentos discretos por un `Path` cerrado de grosor variable suavizado con subdivisión Chaikin y cacheado al completar cada trazo. Todas las mejoras se mantienen encapsuladas dentro del módulo del lienzo sin alterar `TaskViewModel`, el reconocimiento de escritura con ML Kit Digital Ink, el borrador por trazo ni el historial Undo/Redo.

## The Problem

La implementación actual del lienzo en `DrawingScreen.kt` presenta tres limitaciones en la experiencia de escritura con S-Pen:

1. **Captura de entrada limitada y mezcla de relojes**: El uso de `pointerInput` / `awaitEachGesture` lee presión y puntos históricos de Compose sin filtrar micro-movimientos por distancia mínima ni aplicar suavizado exponencial directo sobre la presión cruda del `MotionEvent`. Además, el primer punto del trazo registra `System.currentTimeMillis()` mientras los puntos subsecuentes usan `uptimeMillis`, mezclando dos dominios de tiempo distintos dentro del mismo trazo enviado a ML Kit.
2. **Rechazo de palma dependiente del gesto de Compose**: Aunque existe una comprobación `down.type == PointerType.Touch`, la detección a nivel de `MotionEvent.getToolType(0)` permite distinguir con precisión `TOOL_TYPE_STYLUS` y `TOOL_TYPE_ERASER` frente a toques de dedo/palma antes de que el gesto sea consumido.
3. **Renderizado costoso y con artefactos visuales**: `drawVariableStroke` dibuja por cada par de puntos consecutivos un `drawCircle` y un `drawLine` con `StrokeCap.Round` en cada recomposición/frame del `Canvas` sobre todos los trazos del historial. Esto produce contornos escalonados en trazos rápidos, carece de afilado natural en puntas (*taper*) y genera trabajo repetitivo de dibujo en cada frame para trazos ya terminados.

## Goals

*   **Captura de alta frecuencia con S-Pen**: Leer todos los puntos históricos de `MotionEvent` (`historySize`, `getHistoricalX/Y/Pressure/EventTime`) y el evento actual, filtrando ruido espacial con distancia mínima (~`2.2f`), aplicando suavizado exponencial de presión (`0.55f * prev + 0.45f * curr`) y preservando `timestamp` consistente (`eventTime` / `historicalEventTime`) en cada punto.
*   **Rechazo de palma configurable**: Filtrar eventos por tipo de herramienta del stylus (`TOOL_TYPE_STYLUS` y `TOOL_TYPE_ERASER`) mediante un parámetro `stylusOnly: Boolean = true` enlazado al control actual "Palma: ON/OFF", permitiendo pruebas con dedo o emulador cuando está desactivado y conservando el borrado con botón lateral del S-Pen (`BUTTON_STYLUS_PRIMARY` / `BUTTON_STYLUS_SECONDARY`).
*   **Renderizado de tinta de grosor variable con caché**: Generar un `Path` cerrado por trazo aplicando 1 pasada de subdivisión Chaikin, bordes izquierdo/derecho según el grosor variable, extremos redondeados y afilado en puntas (*taper*), con fallback a círculo/óvalo para trazos de 1 punto. Precalcular y cachear el `Path` al finalizar el trazo (`ACTION_UP`) para no recalcular Chaikin en cada frame del `Canvas`.
*   **Compatibilidad total sin regresiones**: Mantener sin cambios las firmas públicas de `TaskViewModel`, el flujo de guardado, el historial Undo/Redo, el borrador por trazo (usando los puntos centrales crudos) y el envío de puntos crudos (`x, y, timestamp` originales, sin subdivisión Chaikin) a ML Kit Digital Ink.
*   **Compilación limpia**: Compilar el proyecto sin errores ni warnings nuevos, precedido de un commit de respaldo del progreso actual una vez definido el estado de Git.

## Non-Goals

*   No modificar `TaskViewModel`, la base de datos Room, los casos de uso de NLP ni la lógica de ML Kit (`DigitalInkHelper`, `ParseHandwrittenTextUseCase`).
*   No cambiar la geometría ni el algoritmo de colisión del borrador por trazo (`strokeIntersects` seguirá evaluando los puntos centrales crudos).
*   No refactorizar otras pantallas (`DashboardScreen`, `TaskDetailScreen`, `SettingsScreen`), el tema de colores ni añadir librerías externas de dibujo.
*   No rediseñar la barra de herramientas ni los diálogos de confirmación existentes en `DrawingScreen`.

## Open Questions

*   Ninguna pendiente. Los archivos `README.md` sin rastrear del intento parcial de `/egm-bootstrap` se descartaron antes del commit de respaldo.

## Critical User Journeys (CUJs)

### CUJ 1: Escritura continua con S-Pen y rechazo de palma activo

*   **Given**: El usuario abre `DrawingScreen` con `Palma: ON` (`stylusOnly = true`) en un dispositivo con S-Pen.
*   **When**: Apoya la palma sobre el lienzo mientras escribe varias líneas con variaciones de presión, incluyendo puntos (`i`, `j`, puntos finales) y levanta el lápiz al terminar cada trazo.
*   **Then**: Los toques de palma/dedo no dibujan trazos (permitiendo desplazar el lienzo), el S-Pen captura puntos históricos sin saltos visibles, cada trazo muestra grosor variable suave con extremos redondeados/afilados y al soltar el lápiz su `Path` queda cacheado sin recalcularse en frames posteriores.

### CUJ 2: Borrado por trazo, Undo/Redo y reconocimiento ML Kit

*   **Given**: El usuario tiene varios trazos escritos en el lienzo.
*   **When**: Borra un trazo con el botón lateral del S-Pen o con el botón "Borrar", utiliza Deshacer (`<-`) / Rehacer (`->`) y luego presiona "Transformar a Tareas".
*   **Then**: El borrador elimina trazos completos usando los puntos centrales originales, Undo/Redo restaura el historial y sus `Path` cacheados sin duplicados ni pérdidas, y ML Kit recibe únicamente los puntos crudos (`x, y, timestamp` originales) generando las tareas borrador esperadas.

### CUJ 3: Escritura con dedo o emulador con rechazo de palma desactivado

*   **Given**: El usuario abre `DrawingScreen` en un dispositivo sin S-Pen o emulador y cambia el botón a `Palma: OFF` (`stylusOnly = false`).
*   **When**: Dibuja trazos con dedo o ratón (donde la presión puede ser constante, p. ej. `1.0f` o `0.5f`).
*   **Then**: El lienzo captura y renderiza los trazos correctamente con grosor válido sin fallos ni trazos invisibles, y permite convertirlos a tareas.

## Technical Plan (Architecture)

### 1. Flujo de datos del lienzo

Todos los cambios permanecen dentro de `app/src/main/java/com/antakih/taskpen/ui/screens/DrawingScreen.kt` y un archivo auxiliar interno `InkStrokeGeometry.kt` en el mismo paquete `com.antakih.taskpen.ui.screens` para aislar las funciones puras de muestreo, subdivisión Chaikin y construcción de `Path`.

```text
MotionEvent (pointerInteropFilter, stylusOnly = palmRejectionEnabled)
  │
  ├── [Filtro de palma]
  │     Si stylusOnly == true y toolType !in {TOOL_TYPE_STYLUS, TOOL_TYPE_ERASER}
  │     -> return false (deja pasar el toque al horizontalScroll / verticalScroll)
  │
  ├── [Detección de modo borrador]
  │     isActuallyErasing = isEraserMode
  │         || toolType == TOOL_TYPE_ERASER
  │         || (buttonState and (BUTTON_STYLUS_PRIMARY | BUTTON_STYLUS_SECONDARY)) != 0
  │
  ├── [Extracción de muestras de alta frecuencia]
  │     1. Iterar i en 0 until event.historySize:
  │        (getHistoricalX(i), getHistoricalY(i), getHistoricalPressure(i), getHistoricalEventTime(i))
  │     2. Agregar muestra actual:
  │        (event.x, event.y, event.pressure, event.eventTime)
  │
  ├── [Si isActuallyErasing == true]
  │     Aplicar strokeIntersects(stroke, samplePos, eraserWidth / 2f) sobre activeStrokes
  │     (usa exclusivamente los puntos centrales crudos de cada StrokeState)
  │
  └── [Si isActuallyErasing == false]
        ├── Filtro de distancia mínima (~2.2f px respecto al último punto aceptado)
        ├── Suavizado exponencial de presión:
        │     smoothedPressure = 0.55f * prevPressure + 0.45f * currPressure
        ├── Cálculo de grosor:
        │     width = (2f + basePenWidth * smoothedPressure).coerceAtLeast(2f)
        ├── Guardar PathPoint(position, width, timestamp, pressure = smoothedPressure)
        │     en currentStrokeState.points (puntos crudos)
        │
        ├── [Durante ACTION_MOVE] -> Renderizado ligero en vivo (segmentos/círculos)
        ├── [Al recibir ACTION_UP] -> buildVariableWidthStrokePath(points)
        │     (1 pasada Chaikin -> normales izq/der + taper + extremos redondeados)
        │     -> cachear Path en el StrokeState finalizado -> commitStrokes(...)
        └── [Al presionar "Transformar a Tareas"]
              Construir Ink.Point(p.position.x, p.position.y, p.timestamp)
              únicamente desde stroke.points (crudos, nunca desde Chaikin ni Path)
              -> viewModel.processInks(inksWithX)
```

### 2. Modelo de datos y compatibilidad

*   **`PathPoint`**: Se conserva la firma existente añadiendo `pressure` con valor por defecto para no romper construcciones actuales:
    *   `data class PathPoint(val position: Offset, val width: Float, val timestamp: Long = System.currentTimeMillis(), val pressure: Float = 0.5f)`
*   **`StrokeState`**: Se conserva el constructor primario (`val points: List<PathPoint>, val isEraser: Boolean = false`) y se almacena el `Path` precalculado en una propiedad fuera del constructor primario (`var cachedPath: Path? = null`, inicializada mediante `withCachedPath(path: Path)` al finalizar el trazo).
    *   **Por qué fuera del constructor primario**: `commitStrokes` verifica `if (newStrokes == strokesHistory[historyIndex]) return`. Mantener `cachedPath` fuera de `equals`/`hashCode` evita comparaciones por referencia de `android.graphics.Path` y mantiene intacto el comportamiento del historial Undo/Redo.
*   **`DrawingScreen` y `TaskViewModel`**: La firma `DrawingScreen(viewModel: TaskViewModel, onFinished: () -> Unit = {}, stylusOnly: Boolean = true)` agrega `stylusOnly` con valor por defecto `true` para mantener llamadas existentes como `DashboardScreen.kt:882` sin cambios, inicializando `palmRejectionEnabled`. Ninguna firma de `TaskViewModel` cambia.

### 3. Captura con `pointerInteropFilter`

*   Se reemplaza `.pointerInput(palmRejectionEnabled, isEraserMode)` por `.pointerInteropFilter` (con `@OptIn(ExperimentalComposeUiApi::class)`).
*   **Ciclo de eventos**:
    *   `ACTION_DOWN`: Evalúa rechazo de palma (`stylusOnly` y `event.getToolType(0)`). Si se rechaza, retorna `false` para no bloquear el scroll del `Box` contenedor. Si se acepta, determina `isActuallyErasing`, reinicia `lastAcceptedPos` y `lastSmoothedPressure`, inicializa `activeStrokes = strokesHistory[historyIndex]` y procesa el primer punto con `event.eventTime` (unificando el reloj con `getHistoricalEventTime` en `uptimeMillis`).
    *   `ACTION_MOVE`: Si no hay trazo/borrado activo (porque `ACTION_DOWN` fue rechazado), retorna `false`. De lo contrario, procesa en orden todas las muestras históricas (`0 until event.historySize`) seguidas del punto actual, descartando puntos cuya distancia al último punto aceptado sea `< 2.2f` (en modo escritura) y aplicando `0.55f * prev + 0.45f * curr` a la presión.
    *   `ACTION_UP`: Procesa la última muestra si corresponde. En modo escritura, calcula una sola vez `cachedPath = buildVariableWidthStrokePath(finalPoints)`, crea el `StrokeState` final con ese caché y llama a `commitStrokes(activeStrokes + finalizedStroke)`. En modo borrador, llama a `commitStrokes(activeStrokes)`. Limpia `currentStrokeState = null`.
    *   `ACTION_CANCEL`: Descarta el trazo en curso (`currentStrokeState = null`) sin alterar `strokesHistory`, evitando trazos corruptos si el sistema cancela el gesto (p. ej. al iniciar un gesto del sistema o interceptar el scroll).
*   **Consistencia de scroll**: Al retornar `false` para eventos `TOOL_TYPE_FINGER` cuando `stylusOnly == true`, el usuario puede desplazar el lienzo `2000.dp x 2000.dp` con el dedo mientras escribe con el S-Pen sin activar trazos accidentales.

### 4. Subdivisión Chaikin y construcción del `Path` cerrado (en `ACTION_UP`)

La función pura `buildVariableWidthStrokePath(points: List<PathPoint>): Path` se ejecuta únicamente al cerrar el trazo (`ACTION_UP`) y cuando un trazo del historial no tenga aún `cachedPath` (fallback defensivo):

1.  **Caso degenerado (0 puntos)**: Retorna un `Path` vacío.
2.  **Caso de 1 punto (puntos de `i`, `j`, puntuación) o todos los puntos coincidentes**: Construye un óvalo/círculo centrado en `p.position` con radio `max(p.width / 2f, 1f)` (`Path.addOval`).
3.  **Subdivisión Chaikin (1 pasada)**:
    *   Para `points.size >= 3`, genera una nueva lista conservando el primer y último punto exactos, y reemplazando cada segmento `(P_i, P_{i+1})` por:
        *   `Q = 0.75 * P_i + 0.25 * P_{i+1}` (interpolando también `width` con los mismos pesos `0.75 / 0.25`)
        *   `R = 0.25 * P_i + 0.75 * P_{i+1}` (interpolando también `width` con `0.25 / 0.75`)
    *   Para `points.size == 2`, se omiten cortes intermedios y se usa el segmento directo.
4.  **Afilado en puntas (*taper*)**:
    *   Aplica un factor multiplicador sobre el radio (`width / 2f`) en los extremos inicial y final del trazo (sobre los primeros/últimos vértices de la lista suavizada, escalando desde `0.25x` hasta `1.0x`), manteniendo un radio mínimo positivo (`>= 0.5f`) para evitar contornos colapsados.
5.  **Contorno cerrado de grosor variable (bordes izquierdo y derecho + extremos redondeados)**:
    *   Para cada vértice suavizado `i`, calcula la tangente normalizada (usando dirección hacia adelante en el inicio, hacia atrás en el final y diferencia central `P_{i+1} - P_{i-1}` en puntos intermedios; si la longitud es cercana a 0, reutiliza la normal anterior).
    *   Calcula la normal perpendicular `(-ty, tx)` y genera `leftPoints[i] = P_i + normal * radius_i` y `rightPoints[i] = P_i - normal * radius_i`.
    *   Construye el `Path` cerrado:
        *   Recorre `leftPoints` desde `0` hasta `n - 1`.
        *   Cierra la punta final con un semicírculo/arco (`arcTo`) o curva cuadrática redondeada entre `leftPoints.last()` y `rightPoints.last()` centrada en el último punto.
        *   Recorre `rightPoints` en orden inverso desde `n - 1` hasta `0`.
        *   Cierra la punta inicial con un semicírculo/arco redondeado entre `rightPoints.first()` y `leftPoints.first()`, y llama a `path.close()`.
6.  **Renderizado en `Canvas`**:
    *   **Trazos completados (`strokes`)**: `drawPath(path = stroke.cachedPath ?: ..., color = Color.Black, style = Fill)` — una sola llamada de dibujo por trazo, sin recalcular Chaikin.
    *   **Trazo activo (`currentStrokeState`)**: Renderizado ligero en vivo (`drawLiveStroke`) mediante segmentos/círculos simples durante `ACTION_MOVE`, reemplazado inmediatamente por el `cachedPath` al soltar el S-Pen (`ACTION_UP`).

## Alternatives Considered

### Alternative A: Mantener `pointerInput` / `awaitEachGesture` de Compose

*   **Pros**: Evita `@OptIn(ExperimentalComposeUiApi::class)` y mantiene la captura completamente dentro de la abstracción de punteros de Compose.
*   **Cons**: No ofrece acceso directo uniforme a `MotionEvent.getToolType(0)` ni al lote completo de `getHistoricalX/Y/Pressure/EventTime` antes de la resolución de gestos de Compose, lo que dificulta separar el rechazo de palma del scroll del contenedor.
*   **Reason for Rejection**: Rejected by author: se requiere lectura directa de puntos históricos del S-Pen (`event.historySize`, `getHistorical*`) y filtrado por `MotionEvent.TOOL_TYPE_STYLUS` mediante `pointerInteropFilter`.

### Alternative B: Calcular Chaikin y el `Path` de grosor variable en cada frame durante `ACTION_MOVE` (con o sin umbral de 250 puntos)

*   **Pros**: Elimina cualquier diferencia visual entre el trazo en curso y el trazo finalizado al levantar el lápiz (`ACTION_UP`); sin umbral, requiere una sola función de renderizado.
*   **Cons**: En pantallas con digitalizador de S-Pen a 120–240 Hz, recalcular la subdivisión Chaikin, normales y arcos en cada evento `ACTION_MOVE` sobre el trazo acumulado genera asignaciones por frame y riesgo de lag en trazos continuos largos. Añadir un umbral de 250 puntos mitiga el peor caso pero agrega lógica condicional extra.
*   **Reason for Rejection**: Rejected by author: se eligió renderizado ligero en vivo durante `ACTION_MOVE` y cálculo único de Chaikin + caché en `ACTION_UP` para minimizar la carga por frame al escribir.

### Alternative C: Enviar puntos suavizados por Chaikin al motor ML Kit Digital Ink o al borrador

*   **Pros**: Mantendría una sola lista de puntos compartida entre renderizado, borrado y reconocimiento.
*   **Cons**: Duplica el número de vértices por segmento, introduce timestamps interpolados artificiales que pueden afectar el reconocimiento de ML Kit Digital Ink y duplica las iteraciones de distancia en `strokeIntersects`.
*   **Reason for Rejection**: Rejected by author: el reconocedor debe recibir únicamente puntos crudos con su `(x, y, timestamp)` original y el borrador debe mantener su detección sobre los puntos centrales crudos.

--------------------------------------------------------------------------------

## Non-Functional Requirements

*   **Performance & Latency**:
    *   Los trazos completados no deben ejecutar subdivisión Chaikin ni construcción de contornos durante `Canvas { ... }`; solo invocan `drawPath` sobre el `Path` cacheado.
    *   El filtro de distancia mínima (`~2.2f`) evita acumular puntos redundantes cuando el S-Pen permanece casi inmóvil, acotando memoria en `strokesHistory` y latencia en `strokeIntersects` y ML Kit.
*   **Backward Compatibility**:
    *   `PathPoint` conserva compatibilidad binaria/fuente mediante valor por defecto `pressure: Float = 0.5f`.
    *   `DrawingScreen` mantiene su firma compatible con llamadas existentes (`DashboardScreen.kt:882`) y no altera ninguna firma de `TaskViewModel`.

--------------------------------------------------------------------------------

## Detailed Implementation Plan

### Mandatory Pre-Step (Version Control)

*   Descartar los archivos `README.md` sin rastrear bajo `app/src/main/java/com/antakih/taskpen/` y crear un commit de respaldo con `docs/designs/s_pen_canvas_improvements.md` en la rama activa (`feature/notification-system`) antes de editar código fuente.

### Phase 1: Pure Ink Geometry & JVM Unit Tests

#### 1. `app/src/main/java/com/antakih/taskpen/ui/screens/InkStrokeGeometry.kt` (Create)

*   **Type / Scope**: Archivo Kotlin interno (`internal`) de geometría pura y construcción de `Path` en el paquete `com.antakih.taskpen.ui.screens`.
*   **Proposed Changes**:
    *   **Constantes internas**:
        *   `MIN_POINT_DISTANCE_PX = 2.2f`
        *   `PRESSURE_PREV_WEIGHT = 0.55f`
        *   `PRESSURE_CURR_WEIGHT = 0.45f`
        *   `MIN_STROKE_WIDTH_PX = 2.0f`
    *   **Helpers puros de muestreo (testeables en JVM)**:
        *   `internal fun normalizePressure(raw: Float): Float`: retorna `0.5f` si `!raw.isFinite() || raw <= 0f`, o `raw.coerceIn(0f, 1f)` en caso contrario.
        *   `internal fun smoothPressure(prevPressure: Float, currPressure: Float): Float`: normaliza ambas entradas con `normalizePressure`, calcula `PRESSURE_PREV_WEIGHT * prev + PRESSURE_CURR_WEIGHT * curr` y acota el resultado a `[0f, 1f]`.
        *   `internal fun shouldAcceptPoint(lastPosition: Offset?, candidatePosition: Offset, minDistance: Float = MIN_POINT_DISTANCE_PX): Boolean`: retorna `true` si `lastPosition == null` o si la distancia euclidiana `>= minDistance`.
    *   **Subdivisión Chaikin (1 pasada) sobre vértices con grosor**:
        *   `internal data class StyledVertex(val x: Float, val y: Float, val width: Float)`
        *   `internal fun chaikinSubdivideOnce(points: List<PathPoint>): List<StyledVertex>`:
            *   Si `points.size < 3`, convierte directamente cada `PathPoint` a `StyledVertex`.
            *   Si `points.size >= 3`, conserva el primer punto exacto, para cada segmento `(p_i, p_{i+1})` emite `Q = 0.75 * p_i + 0.25 * p_{i+1}` y `R = 0.25 * p_i + 0.75 * p_{i+1}` (interpolando `x`, `y` y `width`), y conserva el último punto exacto.
    *   **Afilado en puntas (*taper*)**:
        *   `internal fun computeTaperedRadii(vertices: List<StyledVertex>, taperVertexCount: Int = 4, minTaperScale: Float = 0.25f): FloatArray`:
            *   Calcula el radio base `width / 2f` para cada vértice.
            *   Para `count = min(taperVertexCount, vertices.size / 2)` y cada `i in 0 until count`, calcula `val scale = minTaperScale + (1f - minTaperScale) * (i.toFloat() / count.toFloat())` (seguro cuando `count == 1`, evitando división entre cero) y aplica `scale` simétricamente al vértice inicial `i` y al vértice final `vertices.size - 1 - i` (rampa ascendente al inicio y descendente al final).
            *   Acota cada radio resultante a `>= 0.5f`.
    *   **Construcción del `Path` cerrado de grosor variable**:
        *   `internal fun buildVariableWidthStrokePath(points: List<PathPoint>): Path`:
            *   `points.isEmpty()`: retorna `Path()` vacío.
            *   `points.size == 1` o todos los vértices colapsados a la misma posición: agrega un óvalo centrado en `p.position` con radio `max(p.width / 2f, 1f)` mediante `addOval(Rect(center = p.position, radius = r))`.
            *   `points.size >= 2`:
                1. Ejecutar `chaikinSubdivideOnce(points)` y `computeTaperedRadii(vertices)`.
                2. Calcular tangentes normalizadas (`forward` en `0`, `backward` en `n - 1`, diferencia central en `1..n-2`) y normales perpendiculares `(-ty, tx)`, reutilizando la última normal válida si la longitud del segmento es `< 1e-4f`.
                3. Generar listas `leftPoints` (`P_i + normal * radius_i`) y `rightPoints` (`P_i - normal * radius_i`).
                4. Construir el contorno cerrado: `moveTo(leftPoints.first())`, `lineTo` por `leftPoints[1..n-1]`, punta final redondeada mediante `quadraticTo(endCapCtrlX, endCapCtrlY, rightPoints.last().x, rightPoints.last().y)` con punto de control `end + endTangent * endRadius`, `lineTo` por `rightPoints[n-2 downTo 0]`, punta inicial redondeada mediante `quadraticTo(startCapCtrlX, startCapCtrlY, leftPoints.first().x, leftPoints.first().y)` con punto de control `start - startTangent * startRadius`, y `path.close()`.

#### 2. `app/src/test/java/com/antakih/taskpen/ui/screens/InkStrokeGeometryTest.kt` (Create)

*   **Type / Scope**: Pruebas unitarias JUnit 4 en JVM (sin dependencias adicionales en `app/build.gradle.kts`).
*   **Proposed Changes**:
    *   Probar `normalizePressure` y `smoothPressure` (pesos `PRESSURE_PREV_WEIGHT` / `PRESSURE_CURR_WEIGHT`, límites `[0f, 1f]`, entrada `NaN`/infinito/negativa/mayor a `1f`).
    *   Probar `shouldAcceptPoint` (primer punto siempre aceptado, punto `< 2.2f` rechazado, punto `>= 2.2f` aceptado).
    *   Probar `chaikinSubdivideOnce` (0, 1, 2 y 3+ puntos: conservación de extremos e interpolación `0.75/0.25` de coordenadas y grosor).
    *   Probar `computeTaperedRadii` (rampa ascendente al inicio, descendente al final, caso `vertices.size == 2` sin `NaN` y radio mínimo `>= 0.5f`).
    *   Probar que `StrokeState.equals` ignora `cachedPath` usando un doble JVM puro de `androidx.compose.ui.graphics.Path` creado con `java.lang.reflect.Proxy.newProxyInstance(Path::class.java.classLoader, arrayOf(Path::class.java)) { _, _, _ -> null } as Path` (evitando invocar `android.graphics.Path()` no mockeado en JVM).

*   **Phase 1 Checkpoint**: Ejecutar `.\gradlew.bat testDebugUnitTest` y confirmar que todas las pruebas unitarias pasan.

### Phase 2: `DrawingScreen.kt` Input & Cached Rendering Integration

#### 3. `app/src/main/java/com/antakih/taskpen/ui/screens/DrawingScreen.kt` (Modify)

*   **Type / Scope**: Composable principal del lienzo de escritura a mano y sus modelos locales.
*   **Proposed Changes**:
    *   **Modelos de datos**:
        *   Actualizar `PathPoint`: agregar `val pressure: Float = 0.5f` al final del constructor primario manteniendo `position: Offset`, `width: Float` y `timestamp: Long = System.currentTimeMillis()`.
        *   Actualizar `StrokeState`: mantener el constructor primario `(val points: List<PathPoint>, val isEraser: Boolean = false)` y declarar en el cuerpo `var cachedPath: Path? = null` junto con un método auxiliar interno `fun withCachedPath(path: Path): StrokeState` (que copia y asigna `cachedPath`) para no alterar `equals`/`hashCode` usados por `commitStrokes`.
    *   **Firma del Composable**:
        *   Anotar `DrawingScreen` con `@OptIn(ExperimentalComposeUiApi::class)` y actualizar su firma a `DrawingScreen(viewModel: TaskViewModel, onFinished: () -> Unit = {}, stylusOnly: Boolean = true)` manteniendo compatibilidad con `DashboardScreen.kt:882`.
        *   Inicializar `var palmRejectionEnabled by remember(stylusOnly) { mutableStateOf(stylusOnly) }`, manteniendo intacto el botón `"Palma: ON" / "Palma: OFF"`.
    *   **Detección de botón lateral de stylus**:
        *   Definir `private fun isMotionEventStylusButtonPressed(event: MotionEvent): Boolean` que evalúa `(event.buttonState and (MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_STYLUS_SECONDARY or MotionEvent.BUTTON_SECONDARY or MotionEvent.BUTTON_TERTIARY)) != 0` (conservando paridad completa con `DrawingScreen.kt:444-449`).
    *   **Captura con `pointerInteropFilter` + `RequestDisallowInterceptTouchEvent` + soporte multi-puntero**:
        *   Instanciar `val disallowIntercept = remember { RequestDisallowInterceptTouchEvent() }` y reemplazar `.pointerInput(palmRejectionEnabled, isEraserMode)` por `.pointerInteropFilter(requestDisallowInterceptTouchEvent = disallowIntercept)` para impedir que el `Box` con `horizontalScroll`/`verticalScroll` intercepte y cancele trazos aceptados en `PointerEventPass.Main`.
        *   Mantener estado mutable del gesto en curso dentro de `remember`: `isGestureActive`, `activePointerId` (`MotionEvent.INVALID_POINTER_ID`), `isGestureErasing`, `lastAcceptedPos`, `lastSmoothedPressure`, y `mutableCurrentPoints = mutableListOf<PathPoint>()`.
        *   Evaluar `when (event.actionMasked)`:
            *   `MotionEvent.ACTION_DOWN`, `MotionEvent.ACTION_POINTER_DOWN`:
                *   Si `isGestureActive`, retornar `true` (ignora toques secundarios mientras un trazo/borrado ya está activo).
                *   Leer `actionIndex = event.actionIndex` y `toolType = event.getToolType(actionIndex)`.
                *   Si `palmRejectionEnabled && toolType != MotionEvent.TOOL_TYPE_STYLUS && toolType != MotionEvent.TOOL_TYPE_ERASER`, retornar `false` (deja pasar el toque de dedo/palma al scroll del `Box` sin activar `disallowIntercept`).
                *   Aceptar el puntero: `activePointerId = event.getPointerId(actionIndex)`, `isGestureActive = true`, `disallowIntercept(true)`.
                *   Detectar borrador: `isGestureErasing = isEraserMode || toolType == MotionEvent.TOOL_TYPE_ERASER || isMotionEventStylusButtonPressed(event)`.
                *   Reiniciar estado: `activeStrokes = strokesHistory[historyIndex]`, `mutableCurrentPoints.clear()`.
                *   En modo borrador: filtrar `activeStrokes` con `strokeIntersects(it, Offset(event.getX(actionIndex), event.getY(actionIndex)), eraserWidth / 2f)` y asignar `currentStrokeState = StrokeState(emptyList(), true)`.
                *   En modo escritura: calcular `initialPressure = normalizePressure(event.getPressure(actionIndex))`, `width = (MIN_STROKE_WIDTH_PX + basePenWidth * initialPressure).coerceAtLeast(MIN_STROKE_WIDTH_PX)`, `initialPos = Offset(event.getX(actionIndex), event.getY(actionIndex))`, asignar `lastAcceptedPos = initialPos`, `lastSmoothedPressure = initialPressure`, añadir `PathPoint(initialPos, width, event.eventTime, initialPressure)` a `mutableCurrentPoints` y actualizar `currentStrokeState = StrokeState(mutableCurrentPoints.toList(), false)`.
                *   Retornar `true`.
            *   `MotionEvent.ACTION_MOVE`:
                *   Si `!isGestureActive`, retornar `false`.
                *   Resolver `val idx = event.findPointerIndex(activePointerId)`; si `idx == -1`, retornar `true`.
                *   Iterar `i` en `0 until event.historySize` leyendo `(event.getHistoricalX(idx, i), event.getHistoricalY(idx, i), event.getHistoricalPressure(idx, i), event.getHistoricalEventTime(i))`, y finalmente el punto actual `(event.getX(idx), event.getY(idx), event.getPressure(idx), event.eventTime)`.
                *   En modo borrador: aplicar `strokeIntersects` por cada muestra histórica y actual sobre `activeStrokes`.
                *   En modo escritura: para cada muestra, validar `shouldAcceptPoint(lastAcceptedPos, pos, MIN_POINT_DISTANCE_PX)`, calcular `smoothed = smoothPressure(lastSmoothedPressure, rawPressure)`, actualizar `lastSmoothedPressure = smoothed` y `lastAcceptedPos = pos`, derivar `width = (MIN_STROKE_WIDTH_PX + basePenWidth * smoothed).coerceAtLeast(MIN_STROKE_WIDTH_PX)`, añadir el `PathPoint` crudo a `mutableCurrentPoints` y publicar una copia inmutable `currentStrokeState = StrokeState(mutableCurrentPoints.toList(), false)`.
                *   Retornar `true`.
            *   `MotionEvent.ACTION_UP`, `MotionEvent.ACTION_POINTER_UP`:
                *   Si `!isGestureActive`, retornar `false`.
                *   Si `event.getPointerId(event.actionIndex) != activePointerId`, retornar `true`.
                *   Procesar la muestra final del puntero `actionIndex`: en escritura, añadirla a `mutableCurrentPoints` si pasa `shouldAcceptPoint`; en borrador, filtrar `activeStrokes` con `strokeIntersects`.
                *   En escritura: tomar snapshot `val finalPoints = mutableCurrentPoints.toList()`; si `finalPoints.isNotEmpty()`, calcular una sola vez `val path = buildVariableWidthStrokePath(finalPoints)`, crear `finalizedStroke = StrokeState(finalPoints, isEraser = false).withCachedPath(path)` y llamar `commitStrokes(activeStrokes + finalizedStroke)`.
                *   En borrador: llamar `commitStrokes(activeStrokes)`.
                *   Limpiar estado: `mutableCurrentPoints.clear()`, `currentStrokeState = null`, `isGestureActive = false`, `activePointerId = MotionEvent.INVALID_POINTER_ID`, `disallowIntercept(false)`, retornar `true`.
            *   `MotionEvent.ACTION_CANCEL`:
                *   Descartar gesto en curso: `mutableCurrentPoints.clear()`, `currentStrokeState = null`, `isGestureActive = false`, `activePointerId = MotionEvent.INVALID_POINTER_ID`, `disallowIntercept(false)` sin modificar `strokesHistory`, retornar `false`.
            *   `else -> isGestureActive`.
    *   **Renderizado en `Canvas`**:
        *   Renombrar `drawVariableStroke` a `drawLiveStroke(stroke: StrokeState)` (evitando warnings de función sin uso) y asegurar guardia `if (stroke.isEraser || stroke.points.isEmpty()) return` al inicio. Usar `drawLiveStroke` únicamente para `currentStrokeState` (dibuja segmentos con `drawLine` y puntos con `drawCircle` sin subdivisión Chaikin).
        *   Agregar `private fun DrawScope.drawCachedStroke(stroke: StrokeState)` con guardia `if (stroke.isEraser || stroke.points.isEmpty()) return`, que dibuja `stroke.cachedPath ?: buildVariableWidthStrokePath(stroke.points).also { stroke.cachedPath = it }` con `drawPath(path = path, color = Color.Black, style = Fill)` para los trazos completados (`strokes`).
    *   **Preservación estricta e imports**:
        *   Mantener sin cambios `strokeIntersects` (sobre `stroke.points`), la agrupación por líneas y construcción de `Ink.Point.create(point.position.x, point.position.y, point.timestamp)` (puntos crudos), el diálogo de confirmación, `viewModel.processInks`, `viewModel.saveTasks`, Undo/Redo y el botón "Limpiar".
        *   Agregar imports necesarios: `androidx.compose.ui.ExperimentalComposeUiApi`, `androidx.compose.ui.input.pointer.RequestDisallowInterceptTouchEvent`, `androidx.compose.ui.input.pointer.pointerInteropFilter`, `androidx.compose.ui.graphics.drawscope.Fill`.
        *   Eliminar imports no utilizados (`awaitEachGesture`, `awaitFirstDown`, `PointerEvent`, `PointerType`, `isSecondaryPressed`, `isTertiaryPressed`, `pointerInput`, `Build`, `RequiresApi`, `ArrowBack`, `ArrowForward`) y la función reemplazada `isStylusButtonPressed(PointerEvent, PointerType)`.

*   **Phase 2 Checkpoint**: Ejecutar `.\gradlew.bat assembleDebug testDebugUnitTest` sin errores ni warnings nuevos en `DrawingScreen.kt` ni `InkStrokeGeometry.kt`.

### Verification Plan

*   **Compilation/Build**:
    *   Ejecutar `.\gradlew.bat assembleDebug` (o `./gradlew assembleDebug`) sin errores ni warnings nuevos en `DrawingScreen.kt` ni `InkStrokeGeometry.kt`.
*   **Unit Tests**:
    *   Ejecutar `.\gradlew.bat testDebugUnitTest` (incluyendo `InkStrokeGeometryTest` y los tests existentes `NLPTest`, `NLPTest2`, `NLPTest3`, `ExampleUnitTest`).
*   **Manual / On-Device Verification (S-Pen y emulador)**:
    *   **CUJ 1**: Con `Palma: ON`, apoyar palma/dedo desplaza el lienzo sin generar tinta (incluso apoyando la palma antes de tocar con el S-Pen); al escribir trazos curvos rápidos con S-Pen se observan curvas continuas sin quiebres poligonales ni cancelación por scroll, grosor variable con *taper* en ambos extremos, puntos aislados (`i`, `.`) visibles y respuesta sin bloqueos visibles con >= 20 trazos en pantalla.
    *   **CUJ 2**: Borrar trazos con botón lateral del S-Pen y con el botón "Borrar", ejecutar Undo/Redo (`<-` / `->`) y pulsar "Transformar a Tareas" verificando que el texto escrito se muestra en el diálogo de confirmación y se persiste al guardar.
    *   **CUJ 3**: Con `Palma: OFF`, dibujar con dedo/ratón en dispositivo o emulador y confirmar trazado y conversión a tareas sin errores.

--------------------------------------------------------------------------------

*Drafted with EGM*
