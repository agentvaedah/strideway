package id.web.agentvaedah.strideway.core.track

import id.web.agentvaedah.strideway.core.geo.Geo
import id.web.agentvaedah.strideway.core.geo.Heading
import id.web.agentvaedah.strideway.core.model.LatLon
import kotlin.math.roundToInt

data class PlaybackPoint(
    val lat: Double,
    val lon: Double,
    val timeMs: Long,
    val ele: Double? = null,
    val speedMps: Double? = null,
    val hr: Int? = null,
)

data class FlyoverFrame(val position: LatLon, val bearing: Double, val progress: Double, val timeMs: Long)

/** Replays a recorded ride: values at any moment, the line drawn so far, and camera frames for a flyover. */
object Playback {
    private fun index(points: List<PlaybackPoint>, tMs: Long): Int {
        var lo = 0
        var hi = points.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (points[mid].timeMs <= tMs) lo = mid else hi = mid - 1
        }
        return lo
    }

    fun sampleAt(points: List<PlaybackPoint>, tMs: Long): PlaybackPoint? {
        if (points.isEmpty()) return null
        if (tMs <= points.first().timeMs) return points.first()
        if (tMs >= points.last().timeMs) return points.last()
        val i = index(points, tMs)
        val a = points[i]
        val b = points[i + 1]
        val span = (b.timeMs - a.timeMs).toDouble()
        val f = if (span > 0) (tMs - a.timeMs) / span else 0.0
        fun lerp(x: Double?, y: Double?) = if (x != null && y != null) x + (y - x) * f else x ?: y
        return PlaybackPoint(
            lat = a.lat + (b.lat - a.lat) * f,
            lon = a.lon + (b.lon - a.lon) * f,
            timeMs = tMs,
            ele = lerp(a.ele, b.ele),
            speedMps = lerp(a.speedMps, b.speedMps),
            hr = lerp(a.hr?.toDouble(), b.hr?.toDouble())?.roundToInt(),
        )
    }

    /** Points up to [tMs] plus the interpolated current position, for drawing the line as it grows. */
    fun prefix(points: List<PlaybackPoint>, tMs: Long): List<PlaybackPoint> {
        if (points.isEmpty()) return emptyList()
        val i = index(points, tMs)
        val head = points.subList(0, i + 1)
        val now = sampleAt(points, tMs) ?: return head
        return if (head.last().timeMs == now.timeMs) head else head + now
    }

    /**
     * Evenly spaced frames over the whole ride. The camera looks a little ahead along the track and its
     * bearing is smoothed on the circle, so turns ease in instead of snapping.
     */
    fun flyover(points: List<PlaybackPoint>, durationMs: Long, fps: Int): List<FlyoverFrame> {
        if (points.size < 2) return emptyList()
        val count = (durationMs * fps / 1000).toInt().coerceAtLeast(2)
        val t0 = points.first().timeMs
        val span = points.last().timeMs - t0
        val ahead = span / 40
        var bearing: Double? = null
        return (1..count).map { n ->
            val progress = n.toDouble() / count
            val t = t0 + (span * progress).toLong()
            val here = sampleAt(points, t)!!
            val look = sampleAt(points, (t + ahead).coerceAtMost(points.last().timeMs))!!
            val raw = if (look.lat == here.lat && look.lon == here.lon) {
                bearing ?: 0.0
            } else {
                Geo.bearingDeg(LatLon(here.lat, here.lon), LatLon(look.lat, look.lon))
            }
            val prev = bearing
            bearing = if (prev == null) raw else Heading.normalize(prev + ((raw - prev + 540.0) % 360.0 - 180.0) * 0.15)
            FlyoverFrame(LatLon(here.lat, here.lon), bearing!!, progress, t)
        }
    }
}
