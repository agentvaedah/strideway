package id.web.agentvaedah.strideway.core.route

import id.web.agentvaedah.strideway.core.geo.Geo
import id.web.agentvaedah.strideway.core.model.LatLon

object LoopPlanner {
    /** Roads are longer than straight lines; empirically about 1.3x in Jakarta-style grids. */
    private const val ROAD_FACTOR = 1.3

    /**
     * Two extra corners forming an equilateral triangle with [start], sized so the routed loop
     * lands near [targetKm]. [headingDeg] sets which way the loop leaves.
     */
    fun triangle(start: LatLon, targetKm: Double, headingDeg: Double = 45.0): List<LatLon> {
        val side = targetKm * 1000.0 / ROAD_FACTOR / 3.0
        val a = Geo.destination(start, headingDeg, side)
        val b = Geo.destination(start, headingDeg + 60.0, side)
        return listOf(a, b)
    }
}
