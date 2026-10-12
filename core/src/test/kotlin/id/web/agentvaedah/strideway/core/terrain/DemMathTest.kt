package id.web.agentvaedah.strideway.core.terrain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.atan

class DemMathTest {
    @Test
    fun terrariumDecode() {
        assertEquals(0.0, DemMath.terrariumElevation(128, 0, 0), 0.0)
        assertEquals(300.5, DemMath.terrariumElevation(129, 44, 128), 1e-9)
    }

    @Test
    fun metersPerPixel() {
        assertEquals(156_543.034, DemMath.metersPerPixel(0.0, 0), 1e-3)
        assertEquals(37.995, DemMath.metersPerPixel(-6.2, 12), 1e-3)
    }

    @Test
    fun planeRisingToEastFacesWest() {
        // Elevation grows 1 m per 10 m cell eastward: slope atan(0.1), downslope points west.
        val w = 5
        val h = 5
        val elev = DoubleArray(w * h) { i -> (i % w).toDouble() }
        val r = DemMath.slopeAspect(elev, w, h, 10.0)
        val center = 2 * w + 2
        assertEquals(Math.toDegrees(atan(0.1)).toFloat(), r.slopeDeg[center], 1e-4f)
        assertEquals(270f, r.aspectDeg[center], 1e-3f)
        assertEquals("Barat", DemMath.aspectLabel(r.aspectDeg[center]))
    }

    @Test
    fun planeRisingToSouthFacesNorth() {
        val w = 5
        val h = 5
        val elev = DoubleArray(w * h) { i -> (i / w).toDouble() }
        val r = DemMath.slopeAspect(elev, w, h, 10.0)
        assertEquals(0f, r.aspectDeg[12], 1e-3f)
    }

    @Test
    fun flatGroundHasNoAspect() {
        val r = DemMath.slopeAspect(DoubleArray(9) { 50.0 }, 3, 3, 30.0)
        assertEquals(0f, r.slopeDeg[4], 0f)
        assertEquals(-1f, r.aspectDeg[4], 0f)
        assertEquals(0, DemMath.aspectColor(r.aspectDeg[4], r.slopeDeg[4]))
    }

    @Test
    fun hillshadeDarkensSlopesFacingAwayFromLight() {
        val towardLight = DemMath.hillshadeColor(30f, 315f) ushr 24
        val awayFromLight = DemMath.hillshadeColor(30f, 135f) ushr 24
        val flat = DemMath.hillshadeColor(0f, -1f) ushr 24
        assertEquals(58, flat)
        assertTrue(towardLight < flat, "lit slope should be lighter than flat")
        assertTrue(awayFromLight > flat, "shaded slope should be darker than flat")
    }

    @Test
    fun avalancheBands() {
        assertEquals(0, DemMath.avalancheColor(29.9f))
        assertEquals(0xCCFFEB3B.toInt(), DemMath.avalancheColor(30f))
        assertEquals(0xCC9C27B0.toInt(), DemMath.avalancheColor(50f))
    }
}
