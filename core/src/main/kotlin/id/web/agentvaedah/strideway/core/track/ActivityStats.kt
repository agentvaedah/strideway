package id.web.agentvaedah.strideway.core.track

import id.web.agentvaedah.strideway.core.geo.Geo
import id.web.agentvaedah.strideway.core.model.Athlete
import id.web.agentvaedah.strideway.core.model.Sport
import id.web.agentvaedah.strideway.core.model.TrackPoint
import id.web.agentvaedah.strideway.core.physio.Calories
import id.web.agentvaedah.strideway.core.physio.TrainingLoad

data class ActivityStats(
    val startMs: Long?,
    val elapsedMs: Long,
    val movingMs: Long,
    val distanceM: Double,
    val elevationGainM: Double,
    val avgSpeedKmh: Double,
    val maxSpeedKmh: Double,
    val avgHr: Int?,
    val maxHr: Int?,
    val avgPower: Int?,
    val calories: Double,
    val trimp: Double,
)

object ActivityStatsCalculator {
    private const val MOVING_KMH = 2.5
    private const val MAX_GAP_MS = 30_000L

    /** Summary for an imported file; tracks without timestamps get distance and climb only. */
    fun compute(points: List<TrackPoint>, sport: Sport, athlete: Athlete): ActivityStats {
        val distance = TrackStats.distanceM(points)
        val gain = TrackStats.elevationGainM(points)
        val timed = points.filter { it.timeMs != null }
        val start = timed.firstOrNull()?.timeMs
        val elapsed = if (timed.size >= 2) timed.last().timeMs!! - start!! else 0L

        var moving = 0L
        var maxKmh = 0.0
        for ((a, b) in timed.zipWithNext()) {
            val dt = b.timeMs!! - a.timeMs!!
            if (dt <= 0 || dt > MAX_GAP_MS) continue
            val kmh = Geo.distanceM(a.latLon, b.latLon) / (dt / 1000.0) * 3.6
            if (kmh >= MOVING_KMH) {
                moving += dt
                if (kmh < 120 && kmh > maxKmh) maxKmh = kmh
            }
        }

        val hrs = points.mapNotNull { it.hr }.filter { it in 25..250 }
        val avgHr = hrs.takeIf { it.isNotEmpty() }?.average()
        val powers = points.mapNotNull { it.power }.filter { it >= 0 }
        val minutes = moving / 60_000.0
        val calories = if (avgHr != null) {
            Calories.kcalPerMinute(avgHr, athlete.weightKg, athlete.age, athlete.gender) * minutes
        } else {
            Calories.kcalPerMinuteMet(sport, athlete.weightKg) * minutes
        }

        return ActivityStats(
            startMs = start,
            elapsedMs = elapsed,
            movingMs = moving,
            distanceM = distance,
            elevationGainM = gain,
            avgSpeedKmh = if (moving > 0) distance / (moving / 1000.0) * 3.6 else 0.0,
            maxSpeedKmh = maxKmh,
            avgHr = avgHr?.toInt(),
            maxHr = hrs.maxOrNull(),
            avgPower = powers.takeIf { it.isNotEmpty() }?.average()?.toInt(),
            calories = calories,
            trimp = TrainingLoad.trimpOrEstimate(minutes, avgHr, athlete),
        )
    }
}
