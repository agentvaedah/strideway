package id.web.agentvaedah.strideway.core.telemetry

/**
 * Current speed over the last [windowMs], for the sparkline under the big speed figure and the
 * up/down arrow beside it. Fed once per GPS sample; not thread-safe, the caller owns it.
 */
class SpeedHistory(private val windowMs: Long = 60_000) {
    private val times = ArrayDeque<Long>()
    private val speeds = ArrayDeque<Double>()

    fun add(timeMs: Long, kmh: Double) {
        if (times.isNotEmpty() && timeMs <= times.last()) return
        times.addLast(timeMs)
        speeds.addLast(kmh)
        while (times.isNotEmpty() && timeMs - times.first() > windowMs) {
            times.removeFirst()
            speeds.removeFirst()
        }
    }

    fun samples(): List<Double> = speeds.toList()

    /** Change against the newest sample at least [backMs] older than [nowMs]; null with too little history. */
    fun trend(nowMs: Long, backMs: Long = 10_000): Double? {
        if (speeds.isEmpty()) return null
        var past: Double? = null
        for (i in times.indices) if (nowMs - times[i] >= backMs) past = speeds[i]
        return past?.let { speeds.last() - it }
    }
}
