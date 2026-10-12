package id.web.agentvaedah.strideway.core.geo

/** Grade over the trailing [windowM] metres, so a single noisy altitude sample cannot spike it. */
class GradeWindow(private val windowM: Double = 50.0) {
    private val samples = ArrayDeque<Pair<Double, Double>>()

    /** Returns grade in percent once the window spans at least half of [windowM]. */
    fun add(cumDistanceM: Double, ele: Double): Double? {
        samples.addLast(cumDistanceM to ele)
        while (samples.size > 2 && cumDistanceM - samples[1].first >= windowM) {
            samples.removeFirst()
        }
        val (d0, e0) = samples.first()
        val run = cumDistanceM - d0
        if (run < windowM / 2) return null
        return (ele - e0) / run * 100.0
    }

    fun reset() = samples.clear()
}
