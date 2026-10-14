package id.web.agentvaedah.strideway.core.route

import id.web.agentvaedah.strideway.core.geo.Geo
import id.web.agentvaedah.strideway.core.model.LatLon
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot

enum class TurnDirection { LEFT, RIGHT }

sealed interface RouteEvent {
    data class LeftRoute(val distanceM: Double) : RouteEvent
    data object BackOnRoute : RouteEvent
    data class TurnAhead(val direction: TurnDirection, val inM: Double) : RouteEvent
}

data class RouteStatus(
    val distanceToRouteM: Double = 0.0,
    val progressM: Double = 0.0,
    val remainingM: Double = 0.0,
    val offRoute: Boolean = false,
    val nextTurn: TurnDirection? = null,
    val nextTurnInM: Double? = null,
    val events: List<RouteEvent> = emptyList(),
)

/**
 * Tracks a rider against a planned route: distance to the line, progress along it, off-route state with
 * hysteresis (leave above [offM] for [holdMs], rejoin below [backM]) and one announcement per turn.
 */
class RouteFollower(
    private val route: List<LatLon>,
    private val offM: Double = 50.0,
    private val backM: Double = 30.0,
    private val holdMs: Long = 5_000,
    private val announceM: Double = 100.0,
) {
    private data class Turn(val atM: Double, val direction: TurnDirection, val change: Double)

    private val cum = DoubleArray(route.size).also { c ->
        for (i in 1 until route.size) c[i] = c[i - 1] + Geo.distanceM(route[i - 1], route[i])
    }
    private val total = if (route.isEmpty()) 0.0 else cum.last()
    private val turns = findTurns()
    private val announced = HashSet<Int>()

    private var progress: Double? = null
    private var offRoute = false
    private var farSinceMs: Long? = null

    fun update(pos: LatLon, nowMs: Long): RouteStatus {
        if (route.size < 2) return RouteStatus()

        // Search near the last known progress so an out-and-back route does not snap to the return leg.
        // When there is no progress yet, or the rider is off route, search the whole line.
        val p = progress
        val windowed = p != null && !offRoute
        var bestD = Double.MAX_VALUE
        var bestProg = 0.0
        for (i in 0 until route.size - 1) {
            if (windowed && (cum[i + 1] < p!! - 50.0 || cum[i] > p + 200.0)) continue
            val (d, t) = project(pos, route[i], route[i + 1])
            if (d < bestD) {
                bestD = d
                bestProg = cum[i] + t * (cum[i + 1] - cum[i])
            }
        }
        progress = bestProg

        val events = ArrayList<RouteEvent>()
        if (offRoute) {
            if (bestD < backM) {
                offRoute = false
                farSinceMs = null
                events += RouteEvent.BackOnRoute
            }
        } else if (bestD > offM) {
            val since = farSinceMs ?: nowMs.also { farSinceMs = it }
            if (nowMs - since >= holdMs) {
                offRoute = true
                events += RouteEvent.LeftRoute(bestD)
            }
        } else {
            farSinceMs = null
        }

        val nextIdx = turns.indexOfFirst { it.atM > bestProg }
        val next = turns.getOrNull(nextIdx)
        val inM = next?.let { it.atM - bestProg }
        if (!offRoute && next != null && inM!! <= announceM && announced.add(nextIdx)) {
            events += RouteEvent.TurnAhead(next.direction, inM)
        }

        return RouteStatus(
            distanceToRouteM = bestD,
            progressM = bestProg,
            remainingM = (total - bestProg).coerceAtLeast(0.0),
            offRoute = offRoute,
            nextTurn = next?.direction,
            nextTurnInM = inM,
            events = events,
        )
    }

    /** Distance in metres from [p] to segment ab and the 0..1 position of the foot along it. */
    private fun project(p: LatLon, a: LatLon, b: LatLon): Pair<Double, Double> {
        val k = Math.toRadians(1.0) * Geo.EARTH_RADIUS_M
        val kx = k * cos(Math.toRadians(p.lat))
        val ax = (a.lon - p.lon) * kx
        val ay = (a.lat - p.lat) * k
        val dx = (b.lon - a.lon) * kx
        val dy = (b.lat - a.lat) * k
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
        return hypot(ax + t * dx, ay + t * dy) to t
    }

    private fun findTurns(): List<Turn> {
        if (route.size < 3) return emptyList()
        val candidates = ArrayList<Turn>()
        for (i in 1 until route.size - 1) {
            var j = i - 1
            while (j > 0 && cum[i] - cum[j] < LOOK_M) j--
            var k = i + 1
            while (k < route.size - 1 && cum[k] - cum[i] < LOOK_M) k++
            if (Geo.distanceM(route[j], route[i]) < 1.0 || Geo.distanceM(route[i], route[k]) < 1.0) continue
            val inB = Geo.bearingDeg(route[j], route[i])
            val outB = Geo.bearingDeg(route[i], route[k])
            val delta = ((outB - inB + 540.0) % 360.0) - 180.0
            if (abs(delta) > TURN_DEG) {
                candidates += Turn(cum[i], if (delta > 0) TurnDirection.RIGHT else TurnDirection.LEFT, abs(delta))
            }
        }
        // Neighbouring vertices around one corner all see the bend; keep the sharpest of each cluster.
        val out = ArrayList<Turn>()
        for (c in candidates) {
            val last = out.lastOrNull()
            if (last != null && c.atM - last.atM <= LOOK_M * 1.5) {
                if (c.change > last.change) out[out.lastIndex] = c
            } else {
                out += c
            }
        }
        return out
    }

    private companion object {
        const val LOOK_M = 20.0
        const val TURN_DEG = 40.0
    }
}
