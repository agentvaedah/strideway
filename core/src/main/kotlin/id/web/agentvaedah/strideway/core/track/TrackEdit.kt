package id.web.agentvaedah.strideway.core.track

import id.web.agentvaedah.strideway.core.geo.Geo
import id.web.agentvaedah.strideway.core.model.LatLon
import kotlin.math.roundToInt

object TrackEdit {
    /** Index range kept by a crop between two slider fractions. Always at least two points. */
    fun cropRange(size: Int, startFrac: Float, endFrac: Float): IntRange {
        if (size < 2) return 0 until size
        val last = size - 1
        var a = (minOf(startFrac, endFrac).coerceIn(0f, 1f) * last).roundToInt()
        var b = (maxOf(startFrac, endFrac).coerceIn(0f, 1f) * last).roundToInt()
        if (b - a < 1) {
            if (b < last) b = a + 1 else a = b - 1
        }
        return a..b
    }

    /** Drops the first [hideStartM] and last [hideEndM] metres of a path, the way privacy hides a home. */
    fun visiblePath(path: List<LatLon>, hideStartM: Int, hideEndM: Int): List<LatLon> {
        if (hideStartM <= 0 && hideEndM <= 0) return path
        if (path.size < 2) return emptyList()
        val cum = DoubleArray(path.size)
        for (i in 1 until path.size) cum[i] = cum[i - 1] + Geo.distanceM(path[i - 1], path[i])
        val total = cum.last()
        val from = hideStartM.toDouble()
        val to = total - hideEndM
        if (from >= to) return emptyList()
        return path.filterIndexed { i, _ -> cum[i] >= from - 1e-3 && cum[i] <= to + 1e-3 }
    }
}
