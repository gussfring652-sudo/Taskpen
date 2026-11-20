package com.antakih.taskpen.ui.screens

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

internal const val MIN_POINT_DISTANCE_PX = 2.2f
internal const val PRESSURE_PREV_WEIGHT = 0.55f
internal const val PRESSURE_CURR_WEIGHT = 0.45f
internal const val MIN_STROKE_WIDTH_PX = 2.0f
private const val MIN_TAPER_RADIUS_PX = 0.5f
private const val DEGENERATE_SEGMENT_EPSILON = 1e-4f

internal data class StyledVertex(
    val x: Float,
    val y: Float,
    val width: Float
)

internal fun normalizePressure(raw: Float): Float {
    return if (!raw.isFinite() || raw <= 0f) 0.5f else raw.coerceIn(0f, 1f)
}

internal fun smoothPressure(prevPressure: Float, currPressure: Float): Float {
    val prev = normalizePressure(prevPressure)
    val curr = normalizePressure(currPressure)
    return (PRESSURE_PREV_WEIGHT * prev + PRESSURE_CURR_WEIGHT * curr).coerceIn(0f, 1f)
}

internal fun shouldAcceptPoint(
    lastPosition: Offset?,
    candidatePosition: Offset,
    minDistance: Float = MIN_POINT_DISTANCE_PX
): Boolean {
    if (lastPosition == null) return true
    val dx = candidatePosition.x - lastPosition.x
    val dy = candidatePosition.y - lastPosition.y
    return hypot(dx, dy) >= minDistance
}

internal fun chaikinSubdivideOnce(points: List<PathPoint>): List<StyledVertex> {
    if (points.isEmpty()) return emptyList()
    if (points.size < 3) {
        return points.map { StyledVertex(it.position.x, it.position.y, it.width) }
    }

    val result = ArrayList<StyledVertex>((points.size - 1) * 2 + 2)
    val first = points.first()
    result.add(StyledVertex(first.position.x, first.position.y, first.width))

    for (i in 0 until points.size - 1) {
        val p0 = points[i]
        val p1 = points[i + 1]

        val qx = 0.75f * p0.position.x + 0.25f * p1.position.x
        val qy = 0.75f * p0.position.y + 0.25f * p1.position.y
        val qw = 0.75f * p0.width + 0.25f * p1.width
        result.add(StyledVertex(qx, qy, qw))

        val rx = 0.25f * p0.position.x + 0.75f * p1.position.x
        val ry = 0.25f * p0.position.y + 0.75f * p1.position.y
        val rw = 0.25f * p0.width + 0.75f * p1.width
        result.add(StyledVertex(rx, ry, rw))
    }

    val last = points.last()
    result.add(StyledVertex(last.position.x, last.position.y, last.width))
    return result
}

internal fun computeTaperedRadii(
    vertices: List<StyledVertex>,
    taperVertexCount: Int = 4,
    minTaperScale: Float = 0.25f
): FloatArray {
    val n = vertices.size
    val radii = FloatArray(n) { i -> max(vertices[i].width / 2f, MIN_TAPER_RADIUS_PX) }
    if (n < 2 || taperVertexCount <= 0) return radii

    val count = min(taperVertexCount, n / 2)
    if (count <= 0) return radii

    for (i in 0 until count) {
        val t = i.toFloat() / count.toFloat()
        val scale = minTaperScale + (1f - minTaperScale) * t
        radii[i] = max(radii[i] * scale, MIN_TAPER_RADIUS_PX)
        val endIdx = n - 1 - i
        radii[endIdx] = max(radii[endIdx] * scale, MIN_TAPER_RADIUS_PX)
    }

    return radii
}

internal fun buildVariableWidthStrokePath(points: List<PathPoint>): Path {
    val path = Path()
    if (points.isEmpty()) return path

    if (points.size == 1) {
        val p = points.first()
        val r = max(p.width / 2f, 1f)
        path.addOval(Rect(center = p.position, radius = r))
        return path
    }

    val vertices = chaikinSubdivideOnce(points)
    val n = vertices.size

    // Check if all vertices are effectively coincident (e.g. micro-tap dot)
    var firstValidTx = 0f
    var firstValidTy = 0f
    var hasNonDegenerateSegment = false
    for (i in 0 until n - 1) {
        val dx = vertices[i + 1].x - vertices[i].x
        val dy = vertices[i + 1].y - vertices[i].y
        val len = hypot(dx, dy)
        if (len >= DEGENERATE_SEGMENT_EPSILON) {
            firstValidTx = dx / len
            firstValidTy = dy / len
            hasNonDegenerateSegment = true
            break
        }
    }

    if (!hasNonDegenerateSegment) {
        val p = points.first()
        val maxWidth = points.maxOf { it.width }
        val r = max(maxWidth / 2f, 1f)
        path.addOval(Rect(center = p.position, radius = r))
        return path
    }

    val radii = computeTaperedRadii(vertices)
    val leftX = FloatArray(n)
    val leftY = FloatArray(n)
    val rightX = FloatArray(n)
    val rightY = FloatArray(n)

    var lastTx = firstValidTx
    var lastTy = firstValidTy
    var startTx = firstValidTx
    var startTy = firstValidTy
    var endTx = firstValidTx
    var endTy = firstValidTy

    for (i in 0 until n) {
        val dx: Float
        val dy: Float
        when (i) {
            0 -> {
                dx = vertices[1].x - vertices[0].x
                dy = vertices[1].y - vertices[0].y
            }
            n - 1 -> {
                dx = vertices[n - 1].x - vertices[n - 2].x
                dy = vertices[n - 1].y - vertices[n - 2].y
            }
            else -> {
                dx = vertices[i + 1].x - vertices[i - 1].x
                dy = vertices[i + 1].y - vertices[i - 1].y
            }
        }

        val len = hypot(dx, dy)
        val tx: Float
        val ty: Float
        if (len >= DEGENERATE_SEGMENT_EPSILON) {
            tx = dx / len
            ty = dy / len
            lastTx = tx
            lastTy = ty
        } else {
            tx = lastTx
            ty = lastTy
        }

        if (i == 0) {
            startTx = tx
            startTy = ty
        }
        if (i == n - 1) {
            endTx = tx
            endTy = ty
        }

        val nx = -ty
        val ny = tx
        val r = radii[i]
        val vx = vertices[i].x
        val vy = vertices[i].y

        leftX[i] = vx + nx * r
        leftY[i] = vy + ny * r
        rightX[i] = vx - nx * r
        rightY[i] = vy - ny * r
    }

    path.moveTo(leftX[0], leftY[0])
    for (i in 1 until n) {
        path.lineTo(leftX[i], leftY[i])
    }

    // Rounded end cap via quadraticTo
    val lastV = vertices[n - 1]
    val lastR = radii[n - 1]
    path.quadraticTo(
        lastV.x + endTx * lastR,
        lastV.y + endTy * lastR,
        rightX[n - 1],
        rightY[n - 1]
    )

    for (i in n - 2 downTo 0) {
        path.lineTo(rightX[i], rightY[i])
    }

    // Rounded start cap via quadraticTo
    val firstV = vertices[0]
    val firstR = radii[0]
    path.quadraticTo(
        firstV.x - startTx * firstR,
        firstV.y - startTy * firstR,
        leftX[0],
        leftY[0]
    )

    path.close()
    return path
}
