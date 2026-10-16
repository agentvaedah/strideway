package id.web.agentvaedah.strideway.core.route

import id.web.agentvaedah.strideway.core.geo.Geo
import id.web.agentvaedah.strideway.core.model.LatLon

/** One line of the turn list: what to do and how far from the start. */
data class Cue(val maneuver: Maneuver, val atM: Double)

/**
 * The whole route's turns in riding order, for the list shown before the start and during a preview.
 * Works on planned routes (BRouter maneuvers) and imported ones alike.
 */
object CueSheet {
    fun of(route: List<LatLon>, maneuvers: List<Maneuver>): List<Cue> {
        if (route.size < 2) return emptyList()
        val cum = DoubleArray(route.size)
        for (i in 1 until route.size) cum[i] = cum[i - 1] + Geo.distanceM(route[i - 1], route[i])
        return maneuvers
            .filter { it.pointIndex in route.indices && it.type != ManeuverType.STRAIGHT }
            .sortedBy { it.pointIndex }
            .map { Cue(it, cum[it.pointIndex]) }
    }

    /** The first cue still ahead at [distanceM] along the route, with the metres left to reach it. */
    fun next(cues: List<Cue>, distanceM: Double): UpcomingManeuver? =
        cues.firstOrNull { it.atM > distanceM + 1.0 }?.let { UpcomingManeuver(it.maneuver, it.atM - distanceM) }
}
