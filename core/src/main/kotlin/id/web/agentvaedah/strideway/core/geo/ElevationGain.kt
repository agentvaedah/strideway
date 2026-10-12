package id.web.agentvaedah.strideway.core.geo

/**
 * Accumulates climbing with a hysteresis band so GPS/barometer noise
 * does not add phantom metres on flat roads.
 */
class ElevationGain(private val threshold: Double = 3.0) {
    private var reference: Double? = null

    var gainM: Double = 0.0
        private set
    var lossM: Double = 0.0
        private set

    fun add(ele: Double) {
        val ref = reference
        if (ref == null) {
            reference = ele
            return
        }
        val delta = ele - ref
        if (delta >= threshold) {
            gainM += delta
            reference = ele
        } else if (delta <= -threshold) {
            lossM += -delta
            reference = ele
        }
    }
}
