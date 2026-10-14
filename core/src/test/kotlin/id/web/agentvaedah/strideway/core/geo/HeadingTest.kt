package id.web.agentvaedah.strideway.core.geo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HeadingTest {
    @Test
    fun usesGpsCourseWhenMovingFast() {
        val h = HeadingFilter(alpha = 1.0)
        assertEquals(90.0, h.update(gpsCourseDeg = 90.0, sensorAzimuthDeg = 10.0, speedKmh = 20.0)!!, 1e-9)
    }

    @Test
    fun usesSensorAzimuthWhenSlow() {
        val h = HeadingFilter(alpha = 1.0)
        assertEquals(10.0, h.update(gpsCourseDeg = 90.0, sensorAzimuthDeg = 10.0, speedKmh = 3.0)!!, 1e-9)
    }

    @Test
    fun nullWhenNoSource() {
        assertNull(HeadingFilter().update(null, null, 0.0))
    }

    @Test
    fun smoothingWrapsAcrossNorth() {
        val h = HeadingFilter(alpha = 0.5)
        h.update(null, 359.0, 0.0)
        val v = h.update(null, 1.0, 0.0)!!
        // Halfway between 359 and 1 is 0, not 180.
        val diff = minOf(v, 360.0 - v)
        assertEquals(0.0, diff, 1e-6)
    }

    @Test
    fun compassLabels() {
        assertEquals("U", Heading.cardinal(0.0))
        assertEquals("TL", Heading.cardinal(44.0))
        assertEquals("T", Heading.cardinal(90.0))
        assertEquals("S", Heading.cardinal(180.0))
        assertEquals("BL", Heading.cardinal(315.0))
        assertEquals("U", Heading.cardinal(350.0))
    }
}
