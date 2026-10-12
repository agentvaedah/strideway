package id.web.agentvaedah.strideway.core.sensor

import kotlin.math.roundToInt

private const val STOP_AFTER_MS = 3_000L

/** Crank cadence from cumulative CSC counters; event time is in 1/1024 s and rolls over at 16 bits. */
class CadenceCalculator {
    private var prevRevs: Int? = null
    private var prevTime = 0
    private var lastChangeMs = 0L
    private var lastRpm: Int? = null

    fun update(crankRevs: Int, eventTime1024: Int, nowMs: Long): Int? {
        val revs0 = prevRevs
        if (revs0 == null) {
            prevRevs = crankRevs
            prevTime = eventTime1024
            lastChangeMs = nowMs
            return null
        }
        if (eventTime1024 == prevTime) {
            return if (nowMs - lastChangeMs > STOP_AFTER_MS) 0.also { lastRpm = 0 } else lastRpm
        }
        val dRevs = (crankRevs - revs0) and 0xFFFF
        val dt = ((eventTime1024 - prevTime) and 0xFFFF) / 1024.0
        prevRevs = crankRevs
        prevTime = eventTime1024
        lastChangeMs = nowMs
        val rpm = (dRevs / dt * 60.0).roundToInt()
        lastRpm = if (rpm in 0..250) rpm else lastRpm
        return lastRpm
    }
}

/** Wheel speed from cumulative CSC wheel revolutions (uint32) and 1/1024 s event time. */
class WheelSpeedCalculator(var circumferenceM: Double = 2.105) {
    private var prevRevs: Long? = null
    private var prevTime = 0
    private var lastChangeMs = 0L
    private var lastMps: Double? = null

    fun update(revs: Long, eventTime1024: Int, nowMs: Long): Double? {
        val revs0 = prevRevs
        if (revs0 == null) {
            prevRevs = revs
            prevTime = eventTime1024
            lastChangeMs = nowMs
            return null
        }
        if (eventTime1024 == prevTime) {
            return if (nowMs - lastChangeMs > STOP_AFTER_MS) 0.0.also { lastMps = 0.0 } else lastMps
        }
        val dRevs = (revs - revs0) and 0xFFFFFFFFL
        val dt = ((eventTime1024 - prevTime) and 0xFFFF) / 1024.0
        prevRevs = revs
        prevTime = eventTime1024
        lastChangeMs = nowMs
        val mps = dRevs * circumferenceM / dt
        lastMps = if (mps in 0.0..40.0) mps else lastMps
        return lastMps
    }
}
