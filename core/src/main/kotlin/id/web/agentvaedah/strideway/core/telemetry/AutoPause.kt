package id.web.agentvaedah.strideway.core.telemetry

/** PRD 3.1: below 2.5 km/h for more than 3 s stops moving time; resumes as soon as speed recovers. */
class AutoPause(
    private val thresholdKmh: Double = 2.5,
    private val delayMs: Long = 3_000,
) {
    private var slowSinceMs: Long? = null

    var isPaused: Boolean = false
        private set

    fun update(speedKmh: Double, timeMs: Long): Boolean {
        if (speedKmh >= thresholdKmh) {
            slowSinceMs = null
            isPaused = false
        } else {
            val since = slowSinceMs ?: timeMs.also { slowSinceMs = it }
            if (timeMs - since > delayMs) isPaused = true
        }
        return isPaused
    }

    fun reset() {
        slowSinceMs = null
        isPaused = false
    }
}
