package id.web.agentvaedah.strideway.core.heatmap

import id.web.agentvaedah.strideway.core.model.LatLon
import kotlin.math.cos
import kotlin.math.floor

data class HeatCell(val lat: Double, val lon: Double, val weight: Double)

/** Personal heatmap from the user's own tracks: points binned into a grid, weight relative to the busiest cell. */
object Heatmap {
    fun bin(points: List<LatLon>, cellM: Double = 50.0, maxCells: Int = 20_000): List<HeatCell> {
        if (points.isEmpty()) return emptyList()
        val dLat = cellM / 111_320.0
        val midLat = points.sumOf { it.lat } / points.size
        val dLon = cellM / (111_320.0 * cos(Math.toRadians(midLat)).coerceAtLeast(0.01))
        val counts = HashMap<Long, Int>()
        for (p in points) {
            val key = (floor(p.lat / dLat).toLong() shl 32) xor (floor(p.lon / dLon).toLong() and 0xffffffffL)
            counts[key] = (counts[key] ?: 0) + 1
        }
        val kept = counts.entries.sortedByDescending { it.value }.take(maxCells)
        val max = kept.first().value.toDouble()
        return kept.map { (key, n) ->
            val iy = (key shr 32).toInt()
            val ix = key.toInt()
            HeatCell((iy + 0.5) * dLat, (ix + 0.5) * dLon, n / max)
        }
    }
}
