package id.web.agentvaedah.strideway.core.route

import id.web.agentvaedah.strideway.core.geo.Geo
import id.web.agentvaedah.strideway.core.model.LatLon
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RouteFollowerTest {
    private val origin = LatLon(-6.2, 106.8)
    private val corner = Geo.destination(origin, 0.0, 500.0)
    private val end = Geo.destination(corner, 90.0, 500.0)

    /** North 500 m, then a right turn and east 500 m, sampled every 10 m. */
    private val route: List<LatLon> = buildList {
        for (d in 0..500 step 10) add(Geo.destination(origin, 0.0, d.toDouble()))
        for (d in 10..500 step 10) add(Geo.destination(corner, 90.0, d.toDouble()))
    }

    private fun alongNorth(m: Double, offsetEastM: Double = 0.0) =
        Geo.destination(Geo.destination(origin, 0.0, m), 90.0, offsetEastM)

    @Test
    fun distanceAndProgressOnRoute() {
        val f = RouteFollower(route)
        val s = f.update(alongNorth(200.0, 20.0), 0)
        assertEquals(20.0, s.distanceToRouteM, 0.5)
        assertEquals(200.0, s.progressM, 1.0)
        assertEquals(800.0, s.remainingM, 1.0)
    }

    @Test
    fun staysOnRouteJustInsideThreshold() {
        val f = RouteFollower(route)
        var s = f.update(alongNorth(100.0, 49.0), 0)
        s = f.update(alongNorth(110.0, 49.0), 10_000)
        assertEquals(false, s.offRoute)
        assertTrue(s.events.none { it is RouteEvent.LeftRoute })
    }

    @Test
    fun goesOffRouteOnlyAfterHoldTime() {
        val f = RouteFollower(route)
        f.update(alongNorth(100.0), 0)
        var s = f.update(alongNorth(110.0, 60.0), 1_000)
        assertEquals(false, s.offRoute)
        s = f.update(alongNorth(115.0, 60.0), 4_000)
        assertEquals(false, s.offRoute)
        s = f.update(alongNorth(120.0, 60.0), 7_000)
        assertEquals(true, s.offRoute)
        assertEquals(1, s.events.count { it is RouteEvent.LeftRoute })
        // No repeat while still off.
        s = f.update(alongNorth(125.0, 70.0), 9_000)
        assertTrue(s.events.isEmpty())
    }

    @Test
    fun backOnRouteNeedsHysteresis() {
        val f = RouteFollower(route)
        f.update(alongNorth(100.0, 60.0), 0)
        f.update(alongNorth(100.0, 60.0), 6_000)
        var s = f.update(alongNorth(105.0, 40.0), 7_000)
        assertEquals(true, s.offRoute)
        s = f.update(alongNorth(110.0, 25.0), 8_000)
        assertEquals(false, s.offRoute)
        assertEquals(1, s.events.count { it is RouteEvent.BackOnRoute })
    }

    @Test
    fun rightTurnAnnouncedOnceBeforeCorner() {
        val f = RouteFollower(route)
        val events = mutableListOf<RouteEvent>()
        var t = 0L
        for (m in 0..495 step 5) {
            events += f.update(alongNorth(m.toDouble()), t).events
            t += 1_000
        }
        val turns = events.filterIsInstance<RouteEvent.TurnAhead>()
        assertEquals(1, turns.size)
        assertEquals(TurnDirection.RIGHT, turns[0].direction)
        assertTrue(turns[0].inM in 50.0..100.0)
    }

    @Test
    fun loopRouteDoesNotJumpToFarSegment() {
        // Out north 300 m and back on the same line: start of the return overlaps the outbound.
        val loop = buildList {
            for (d in 0..300 step 10) add(Geo.destination(origin, 0.0, d.toDouble()))
            for (d in 290 downTo 0 step 10) add(Geo.destination(origin, 0.0, d.toDouble()))
        }
        val f = RouteFollower(loop)
        var s = f.update(alongNorth(0.0), 0)
        for (m in 10..100 step 10) s = f.update(alongNorth(m.toDouble()), m * 100L)
        assertEquals(100.0, s.progressM, 5.0)
    }

    @Test
    fun emptyRouteIsHarmless() {
        val s = RouteFollower(emptyList()).update(origin, 0)
        assertEquals(false, s.offRoute)
        assertTrue(s.events.isEmpty())
    }
}
