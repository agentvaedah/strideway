package id.web.agentvaedah.strideway.core.route

import id.web.agentvaedah.strideway.core.geo.Geo
import id.web.agentvaedah.strideway.core.model.LatLon

data class FlyFrame(val position: LatLon, val bearing: Double, val distanceM: Double)

/**
 * Camera path for previewing a planned route: frames are placed by distance along the road, so routes
 * without timestamps play at an even pace. The camera looks [lookAheadM] down the road and its bearing is
 * the circular mean of several look-ahead directions, so turns ease in and it never spins the long way.
 */
class RouteFlyover(private val route: List<LatLon>, private val lookAheadM: Double = 150.0) {
    private val cum: DoubleArray = DoubleArray(route.size).also { c ->
        for (i in 1 until route.size) c[i] = c[i - 1] + Geo.distanceM(route[i - 1], route[i])
    }

    val lengthM: Double get() = cum.lastOrNull() ?: 0.0

    /** 20 seconds up to 2 km, then about a second per extra two kilometres, capped at 45 seconds. */
    val durationMs: Long get() = (20_000 + (lengthM - 2_000.0).coerceAtLeast(0.0) / 2_000.0 * 1_000).toLong().coerceIn(20_000L, 45_000L)

    fun at(progress: Double): FlyFrame {
        require(route.size >= 2) { "A route needs two points" }
        val d = progress.coerceIn(0.0, 1.0) * lengthM
        val here = pointAt(d)
        // Average several look-ahead directions on the unit circle: smooth through turns and through north.
        var sx = 0.0
        var sy = 0.0
        for (k in 1..5) {
            val ahead = pointAt(d + lookAheadM * k / 5.0)
            val from = if (Geo.distanceM(here, ahead) < 1.0) pointAt(d - lookAheadM * k / 5.0) to here else here to ahead
            if (Geo.distanceM(from.first, from.second) < 1.0) continue
            val b = Math.toRadians(Geo.bearingDeg(from.first, from.second))
            sx += Math.sin(b)
            sy += Math.cos(b)
        }
        val bearing = if (sx == 0.0 && sy == 0.0) Geo.bearingDeg(route[route.size - 2], route.last())
        else (Math.toDegrees(Math.atan2(sx, sy)) + 360.0) % 360.0
        return FlyFrame(here, bearing, d)
    }

    /** Point [d] metres along the route, clamped to the ends. */
    fun pointAt(d: Double): LatLon {
        if (d <= 0) return route.first()
        if (d >= lengthM) return route.last()
        var lo = 0
        var hi = cum.lastIndex
        while (lo < hi - 1) {
            val mid = (lo + hi) / 2
            if (cum[mid] <= d) lo = mid else hi = mid
        }
        val seg = cum[hi] - cum[lo]
        val f = if (seg > 0) (d - cum[lo]) / seg else 0.0
        val a = route[lo]
        val b = route[hi]
        return LatLon(a.lat + (b.lat - a.lat) * f, a.lon + (b.lon - a.lon) * f)
    }

    /** The route up to [d] metres, for drawing the part already flown over. */
    fun prefix(d: Double): List<LatLon> {
        if (d <= 0) return listOf(route.first())
        val out = ArrayList<LatLon>()
        for (i in route.indices) {
            if (cum[i] < d) out += route[i] else break
        }
        out += pointAt(d)
        return out
    }
}
