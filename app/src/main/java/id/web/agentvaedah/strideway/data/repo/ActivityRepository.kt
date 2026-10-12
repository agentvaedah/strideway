package id.web.agentvaedah.strideway.data.repo

import id.web.agentvaedah.strideway.core.geo.Polyline
import id.web.agentvaedah.strideway.core.model.ActivitySummary
import id.web.agentvaedah.strideway.core.model.Athlete
import id.web.agentvaedah.strideway.core.model.LatLon
import id.web.agentvaedah.strideway.core.model.Sport
import id.web.agentvaedah.strideway.core.model.Track
import id.web.agentvaedah.strideway.core.model.TrackPoint
import id.web.agentvaedah.strideway.core.physio.TrainingLoad
import id.web.agentvaedah.strideway.core.telemetry.LiveMetrics
import id.web.agentvaedah.strideway.core.track.ActivityStatsCalculator
import id.web.agentvaedah.strideway.core.track.TrackStats
import id.web.agentvaedah.strideway.data.db.ActivityDao
import id.web.agentvaedah.strideway.data.db.ActivityEntity
import id.web.agentvaedah.strideway.data.db.ActivitySource
import id.web.agentvaedah.strideway.data.db.TrackPointDao
import id.web.agentvaedah.strideway.data.db.TrackPointEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivityRepository @Inject constructor(
    private val activityDao: ActivityDao,
    private val pointDao: TrackPointDao,
) {
    fun activities(): Flow<List<ActivityEntity>> = activityDao.observeFinished()

    fun activity(id: Long): Flow<ActivityEntity?> = activityDao.observe(id)

    suspend fun get(id: Long): ActivityEntity? = activityDao.get(id)

    suspend fun points(id: Long): List<TrackPointEntity> = pointDao.forActivity(id)

    fun summaries(): Flow<List<ActivitySummary>> = activities().map { list ->
        list.map { ActivitySummary(it.sport, it.startMs, it.distanceM, it.movingMs, it.trimp) }
    }

    suspend fun createLive(sport: Sport, startMs: Long): Long =
        activityDao.insert(ActivityEntity(sport = sport, name = defaultName(sport, startMs), startMs = startMs))

    suspend fun appendPoints(points: List<TrackPointEntity>) {
        if (points.isNotEmpty()) pointDao.insertAll(points)
    }

    suspend fun interruptedSession(): ActivityEntity? = activityDao.interrupted()

    /** Writes final metrics for a recording; uses live metrics so numbers match what the rider saw. */
    suspend fun finish(id: Long, m: LiveMetrics, athlete: Athlete) {
        val existing = activityDao.get(id) ?: return
        val pts = pointDao.forActivity(id)
        val minutes = m.movingMs / 60_000.0
        val avgHr = m.avgHeartRate?.toDouble()
        activityDao.update(
            existing.copy(
                elapsedMs = m.elapsedMs.coerceAtLeast(1),
                movingMs = m.movingMs,
                distanceM = m.distanceM,
                elevationGainM = m.elevationGainM,
                calories = m.calories,
                avgHr = m.avgHeartRate,
                maxHr = m.maxHeartRate,
                avgSpeedKmh = m.avgSpeedKmh,
                maxSpeedKmh = m.maxSpeedKmh,
                avgPower = m.avgPowerWatts,
                trimp = TrainingLoad.trimpOrEstimate(minutes, avgHr, athlete),
                trimpEstimated = avgHr == null,
                polyline = encodePath(pts.map { LatLon(it.lat, it.lon) }),
            ),
        )
    }

    /** Recovers a session whose process died mid-ride by recomputing everything from stored points. */
    suspend fun finishFromPoints(id: Long, athlete: Athlete) {
        val existing = activityDao.get(id) ?: return
        val pts = pointDao.forActivity(id)
        if (pts.size < 2) {
            activityDao.delete(id)
            return
        }
        saveStats(existing, pts.map { it.toTrackPoint() }, athlete)
    }

    suspend fun importTrack(track: Track, athlete: Athlete, fallbackSport: Sport): Long {
        val sport = track.sport ?: fallbackSport
        val start = track.points.firstNotNullOfOrNull { it.timeMs } ?: System.currentTimeMillis()
        val id = activityDao.insert(
            ActivityEntity(
                sport = sport,
                name = track.name ?: defaultName(sport, start),
                startMs = start,
                source = ActivitySource.IMPORT,
            ),
        )
        var t = start
        pointDao.insertAll(
            track.points.map { p ->
                // Untimed points get synthetic 1 s spacing so ordering survives the round trip.
                val time = p.timeMs ?: t.also { t += 1000 }
                TrackPointEntity(
                    activityId = id, timeMs = time, lat = p.lat, lon = p.lon, ele = p.ele,
                    hr = p.hr, cadence = p.cadence, power = p.power,
                )
            },
        )
        saveStats(activityDao.get(id)!!, track.points, athlete)
        return id
    }

    private suspend fun saveStats(entity: ActivityEntity, points: List<TrackPoint>, athlete: Athlete) {
        val s = ActivityStatsCalculator.compute(points, entity.sport, athlete)
        activityDao.update(
            entity.copy(
                elapsedMs = s.elapsedMs.coerceAtLeast(1),
                movingMs = s.movingMs,
                distanceM = s.distanceM,
                elevationGainM = s.elevationGainM,
                calories = s.calories,
                avgHr = s.avgHr,
                maxHr = s.maxHr,
                avgSpeedKmh = s.avgSpeedKmh,
                maxSpeedKmh = s.maxSpeedKmh,
                avgPower = s.avgPower,
                trimp = s.trimp,
                trimpEstimated = s.avgHr == null,
                polyline = encodePath(points.map { it.latLon }),
            ),
        )
    }

    suspend fun rename(id: Long, name: String) = activityDao.rename(id, name.trim())

    suspend fun delete(id: Long) = activityDao.delete(id)

    private fun encodePath(path: List<LatLon>): String = Polyline.encode(TrackStats.downsample(path, MAX_POLYLINE_POINTS))

    private fun TrackPointEntity.toTrackPoint() = TrackPoint(lat, lon, ele, timeMs, hr, cadence, power)

    companion object {
        private const val MAX_POLYLINE_POINTS = 400

        fun defaultName(sport: Sport, startMs: Long): String {
            val hour = Instant.ofEpochMilli(startMs).atZone(ZoneId.systemDefault()).hour
            val part = when (hour) {
                in 4..10 -> "pagi"
                in 11..14 -> "siang"
                in 15..17 -> "sore"
                else -> "malam"
            }
            return if (sport == Sport.RIDE) "Ride $part" else "Run $part"
        }
    }
}
