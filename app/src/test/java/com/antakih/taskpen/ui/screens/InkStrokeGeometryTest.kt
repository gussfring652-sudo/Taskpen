package com.antakih.taskpen.ui.screens

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class InkStrokeGeometryTest {

    private fun createJvmDummyPath(): Path {
        return Proxy.newProxyInstance(
            Path::class.java.classLoader,
            arrayOf(Path::class.java)
        ) { _, _, _ -> null } as Path
    }

    @Test
    fun normalizePressure_handlesEdgeValues() {
        assertEquals(0.5f, normalizePressure(Float.NaN), 1e-5f)
        assertEquals(0.5f, normalizePressure(Float.POSITIVE_INFINITY), 1e-5f)
        assertEquals(0.5f, normalizePressure(Float.NEGATIVE_INFINITY), 1e-5f)
        assertEquals(0.5f, normalizePressure(0f), 1e-5f)
        assertEquals(0.5f, normalizePressure(-0.4f), 1e-5f)
        assertEquals(1.0f, normalizePressure(1.8f), 1e-5f)
        assertEquals(0.75f, normalizePressure(0.75f), 1e-5f)
    }

    @Test
    fun smoothPressure_appliesExponentialWeights() {
        val result = smoothPressure(prevPressure = 0.2f, currPressure = 1.0f)
        assertEquals(0.55f * 0.2f + 0.45f * 1.0f, result, 1e-5f)

        val withNaN = smoothPressure(prevPressure = Float.NaN, currPressure = 0.8f)
        assertEquals(0.55f * 0.5f + 0.45f * 0.8f, withNaN, 1e-5f)
    }

    @Test
    fun shouldAcceptPoint_enforcesMinDistance() {
        assertTrue(shouldAcceptPoint(null, Offset(10f, 10f)))
        assertFalse(shouldAcceptPoint(Offset(10f, 10f), Offset(11f, 11f))) // dist ~1.414 < 2.2
        assertTrue(shouldAcceptPoint(Offset(0f, 0f), Offset(MIN_POINT_DISTANCE_PX, 0f))) // dist == 2.2
        assertTrue(shouldAcceptPoint(Offset(10f, 10f), Offset(13f, 10f))) // dist == 3.0
    }

    @Test
    fun chaikinSubdivideOnce_preservesEndpointsAndInterpolates() {
        assertTrue(chaikinSubdivideOnce(emptyList()).isEmpty())

        val single = listOf(PathPoint(Offset(5f, 6f), 8f, 100L, 0.5f))
        assertEquals(1, chaikinSubdivideOnce(single).size)

        val two = listOf(
            PathPoint(Offset(0f, 0f), 4f, 100L, 0.2f),
            PathPoint(Offset(10f, 0f), 12f, 110L, 0.8f)
        )
        assertEquals(2, chaikinSubdivideOnce(two).size)

        val three = listOf(
            PathPoint(Offset(0f, 0f), 4f, 100L, 0.2f),
            PathPoint(Offset(10f, 0f), 8f, 110L, 0.5f),
            PathPoint(Offset(10f, 10f), 12f, 120L, 0.8f)
        )
        val subdivided = chaikinSubdivideOnce(three)
        // first + 2*(3-1) + last = 6 vertices
        assertEquals(6, subdivided.size)
        assertEquals(0f, subdivided.first().x, 1e-5f)
        assertEquals(0f, subdivided.first().y, 1e-5f)
        assertEquals(10f, subdivided.last().x, 1e-5f)
        assertEquals(10f, subdivided.last().y, 1e-5f)
        // First Q = 0.75 * P0 + 0.25 * P1
        assertEquals(2.5f, subdivided[1].x, 1e-5f)
        assertEquals(0f, subdivided[1].y, 1e-5f)
        assertEquals(5.0f, subdivided[1].width, 1e-5f)
    }

    @Test
    fun computeTaperedRadii_scalesSymmetricallyAndHandlesTwoVertices() {
        val two = listOf(
            StyledVertex(0f, 0f, 10f),
            StyledVertex(10f, 0f, 10f)
        )
        val twoRadii = computeTaperedRadii(two, taperVertexCount = 4, minTaperScale = 0.25f)
        assertEquals(2, twoRadii.size)
        assertTrue(twoRadii[0].isFinite())
        assertTrue(twoRadii[1].isFinite())
        assertEquals(5f * 0.25f, twoRadii[0], 1e-5f)
        assertEquals(5f * 0.25f, twoRadii[1], 1e-5f)

        val ten = List(10) { i -> StyledVertex(i * 10f, 0f, 10f) }
        val tenRadii = computeTaperedRadii(ten, taperVertexCount = 4, minTaperScale = 0.25f)
        // Start ramps up, middle stays full (5f), end ramps down symmetrically
        assertTrue(tenRadii[0] < tenRadii[1])
        assertTrue(tenRadii[1] < tenRadii[2])
        assertTrue(tenRadii[2] < tenRadii[3])
        assertEquals(5f, tenRadii[4], 1e-5f)
        assertEquals(5f, tenRadii[5], 1e-5f)
        assertEquals(tenRadii[0], tenRadii[9], 1e-5f)
        assertEquals(tenRadii[1], tenRadii[8], 1e-5f)
        assertEquals(tenRadii[2], tenRadii[7], 1e-5f)
        assertEquals(tenRadii[3], tenRadii[6], 1e-5f)
    }

    @Test
    fun strokeState_equalsIgnoresCachedPath() {
        val pts = listOf(PathPoint(Offset(1f, 2f), 6f, 100L, 0.5f))
        val s1 = StrokeState(pts, isEraser = false)
        val dummyPath = createJvmDummyPath()
        val s2 = StrokeState(pts, isEraser = false).withCachedPath(dummyPath)

        assertNotNull(s2.cachedPath)
        assertEquals(s1, s2)
        assertEquals(s1.hashCode(), s2.hashCode())
    }
}
