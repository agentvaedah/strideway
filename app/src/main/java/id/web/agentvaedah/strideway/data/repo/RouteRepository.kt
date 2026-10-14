package id.web.agentvaedah.strideway.data.repo

import id.web.agentvaedah.strideway.core.geo.Polyline
import id.web.agentvaedah.strideway.core.model.TrackPoint
import id.web.agentvaedah.strideway.core.track.TrackStats
import id.web.agentvaedah.strideway.core.route.Maneuver
import id.web.agentvaedah.strideway.core.route.ManeuverType
import id.web.agentvaedah.strideway.data.db.ManeuverEntity
import id.web.agentvaedah.strideway.data.db.RouteDao
import id.web.agentvaedah.strideway.data.db.RouteEntity
import id.web.agentvaedah.strideway.data.db.RoutePointEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RouteRepository @Inject constructor(private val dao: RouteDao) {
    fun routes(): Flow<List<RouteEntity>> = dao.observeAll()

    fun route(id: Long): Flow<RouteEntity?> = dao.observe(id)

    suspend fun get(id: Long): RouteEntity? = dao.get(id)

    suspend fun points(routeId: Long): List<RoutePointEntity> = dao.points(routeId)

    /** Turn instructions saved with the route; empty for routes stored before schema 3 or without hints. */
    suspend fun maneuvers(routeId: Long): List<Maneuver> = dao.maneuvers(routeId).mapNotNull { m ->
        runCatching { ManeuverType.valueOf(m.type) }.getOrNull()?.let { Maneuver(m.pointIndex, it, m.exitNumber, m.street) }
    }

    suspend fun save(name: String, source: String, points: List<TrackPoint>, prompt: String?, maneuvers: List<Maneuver> = emptyList()): Long {
        val route = RouteEntity(
            name = name.trim().ifBlank { "Rute tanpa nama" },
            source = source,
            createdMs = System.currentTimeMillis(),
            distanceM = TrackStats.distanceM(points),
            elevationGainM = TrackStats.elevationGainM(points),
            polyline = Polyline.encode(TrackStats.downsample(points.map { it.latLon }, 400)),
            prompt = prompt,
        )
        val id = dao.insertWithPoints(
            route,
            points.mapIndexed { i, p -> RoutePointEntity(routeId = 0, seq = i, lat = p.lat, lon = p.lon, ele = p.ele) },
        )
        if (maneuvers.isNotEmpty()) {
            dao.insertManeuvers(maneuvers.mapIndexed { i, m -> ManeuverEntity(id, i, m.pointIndex, m.type.name, m.exit, m.street) })
        }
        return id
    }

    suspend fun delete(id: Long) = dao.delete(id)
}

fun RoutePointEntity.toTrackPoint() = TrackPoint(lat, lon, ele)
