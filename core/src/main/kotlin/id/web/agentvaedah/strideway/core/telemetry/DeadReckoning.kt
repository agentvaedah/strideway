package id.web.agentvaedah.strideway.core.telemetry

import id.web.agentvaedah.strideway.core.geo.Geo
import id.web.agentvaedah.strideway.core.model.LatLon

/**
 * Bridges GPS gaps (tunnels, flyovers) by integrating speed along the last heading.
 * Forward acceleration from the IMU adjusts speed; estimates stop after [maxDurationMs]
 * because accelerometer drift grows quadratically.
 */
class DeadReckoning(
    private val staleAfterMs: Long = 1_500,
    private val maxDurationMs: Long = 30_000,
) {
    private var lastFix: LatLon? = null
    private var lastFixMs: Long = 0
    private var position: LatLon? = null
    private var positionMs: Long = 0
    private var speedMps: Double = 0.0
    private var headingDeg: Double = 0.0
    private var lastAccMs: Long? = null

    fun onGpsFix(pos: LatLon, speedMps: Double, bearingDeg: Double, timeMs: Long) {
        lastFix = pos
        lastFixMs = timeMs
        position = pos
        positionMs = timeMs
        this.speedMps = speedMps.coerceAtLeast(0.0)
        if (speedMps > 1.0) headingDeg = bearingDeg
        lastAccMs = null
    }

    fun onHeading(bearingDeg: Double) {
        headingDeg = bearingDeg
    }

    fun onForwardAcceleration(accMps2: Double, timeMs: Long) {
        if (!isStale(timeMs)) {
            lastAccMs = timeMs
            return
        }
        val prev = lastAccMs
        lastAccMs = timeMs
        if (prev == null) return
        val dt = (timeMs - prev) / 1000.0
        if (dt <= 0 || dt > 0.5) return
        advanceTo(timeMs)
        speedMps = (speedMps + accMps2 * dt).coerceIn(0.0, 30.0)
    }

    /** Null while GPS is fresh, before the first fix, or after the gap exceeds [maxDurationMs]. */
    fun estimate(timeMs: Long): LatLon? {
        if (lastFix == null || !isStale(timeMs)) return null
        if (timeMs - lastFixMs > maxDurationMs) return null
        advanceTo(timeMs)
        return position
    }

    fun isStale(timeMs: Long): Boolean = timeMs - lastFixMs > staleAfterMs

    private fun advanceTo(timeMs: Long) {
        val pos = position ?: return
        val dt = (timeMs - positionMs) / 1000.0
        if (dt <= 0) return
        position = Geo.destination(pos, headingDeg, speedMps * dt)
        positionMs = timeMs
    }
}
