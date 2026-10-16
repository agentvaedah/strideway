package id.web.agentvaedah.strideway.core.route

import id.web.agentvaedah.strideway.core.geo.Geo
import id.web.agentvaedah.strideway.core.model.LatLon
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class RouteFlyoverTest {
    private val start = LatLon(-6.6, 106.8)
    private fun east(m: Double) = Geo.destination(start, 90.0, m)

    @Test
    fun `starts at the first point and ends at the last`() {
        val route = listOf(start, east(2_000.0), Geo.destination(east(2_000.0), 0.0, 2_000.0))
        val f = RouteFlyover(route)
        assertEquals(0.0, Geo.distanceM(f.at(0.0).position, route.first()), 0.5)
        assertEquals(0.0, Geo.distanceM(f.at(1.0).position, route.last()), 0.5)
        assertEquals(4_000.0, f.lengthM, 5.0)
    }

    @Test
    fun `progress is by distance, so the middle is halfway along the road`() {
        val route = listOf(start, east(1_000.0), east(3_000.0))
        val mid = RouteFlyover(route).at(0.5)
        assertEquals(1_500.0, Geo.distanceM(start, mid.position), 2.0)
        assertEquals(1_500.0, mid.distanceM, 0.5)
    }

    @Test
    fun `bearing looks ahead along a straight road`() {
        val f = RouteFlyover(listOf(start, east(5_000.0)))
        assertEquals(90.0, f.at(0.3).bearing, 0.5)
    }

    @Test
    fun `bearing turns smoothly through north instead of spinning the long way`() {
        // Heading 350 then turning to 10: the camera should pass through 0, never through 180.
        val a = Geo.destination(start, 350.0, 1_000.0)
        val b = Geo.destination(a, 10.0, 1_000.0)
        val f = RouteFlyover(listOf(start, a, b))
        var prev = f.at(0.0).bearing
        for (i in 1..200) {
            val now = f.at(i / 200.0).bearing
            val step = abs(((now - prev + 540.0) % 360.0) - 180.0)
            assertTrue(step < 10.0, "bearing jumped $step degrees at step $i")
            prev = now
        }
        val atEnd = f.at(1.0).bearing
        assertTrue(atEnd < 20.0 || atEnd > 340.0, "end bearing $atEnd should be near north")
    }

    @Test
    fun `duration grows with distance and stays within bounds`() {
        assertEquals(20_000L, RouteFlyover(listOf(start, east(500.0))).durationMs)
        assertEquals(45_000L, RouteFlyover(listOf(start, east(400_000.0))).durationMs)
        val mid = RouteFlyover(listOf(start, east(30_000.0))).durationMs
        assertTrue(mid in 20_001L..44_999L)
    }

    @Test
    fun `a two point route still has frames`() {
        val f = RouteFlyover(listOf(start, east(10.0)))
        assertEquals(0.0, Geo.distanceM(f.at(0.0).position, start), 0.5)
        assertTrue(f.lengthM > 9.0)
    }
}
