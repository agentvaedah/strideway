package id.web.agentvaedah.strideway.core.privacy

import id.web.agentvaedah.strideway.core.geo.Geo
import id.web.agentvaedah.strideway.core.model.LatLon

object PrivacyZone {
    /** Drops every point inside the circle so the start/end near home never leaves the device. */
    fun <T> trim(points: List<T>, center: LatLon, radiusM: Double, pos: (T) -> LatLon): List<T> =
        points.filter { Geo.distanceM(pos(it), center) > radiusM }
}
