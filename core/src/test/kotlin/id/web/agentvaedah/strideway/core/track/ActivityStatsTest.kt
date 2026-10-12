package id.web.agentvaedah.strideway.core.track

import id.web.agentvaedah.strideway.core.coach.CoachContext
import id.web.agentvaedah.strideway.core.coach.RecentActivity
import id.web.agentvaedah.strideway.core.geo.Geo
import id.web.agentvaedah.strideway.core.model.Athlete
import id.web.agentvaedah.strideway.core.model.LatLon
import id.web.agentvaedah.strideway.core.model.Sport
import id.web.agentvaedah.strideway.core.model.TrackPoint
import id.web.agentvaedah.strideway.core.physio.TrainingLoad
import id.web.agentvaedah.strideway.core.route.LoopPlanner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZoneId

class ActivityStatsTest {
    private val athlete = Athlete()

    private fun line(seconds: Int, mps: Double, hr: Int?, startMs: Long = 0): List<TrackPoint> {
        var pos = LatLon(-6.2, 106.8)
        return (0..seconds).map { s ->
            if (s > 0) pos = Geo.destination(pos, 0.0, mps)
            TrackPoint(pos.lat, pos.lon, 10.0, startMs + s * 1000L, hr = hr)
        }
    }

    @Test
    fun movingTimeExcludesStops() {
        val moving = line(600, 8.0, 140)
        val last = moving.last()
        val stopped = (1..120).map { last.copy(timeMs = last.timeMs!! + it * 1000L) }
        val s = ActivityStatsCalculator.compute(moving + stopped, Sport.RIDE, athlete)
        assertEquals(720_000L, s.elapsedMs)
        assertEquals(600_000L, s.movingMs)
        assertEquals(4800.0, s.distanceM, 1.0)
        assertEquals(28.8, s.avgSpeedKmh, 0.05)
        assertEquals(140, s.avgHr)
        assertEquals(TrainingLoad.trimp(10.0, 140.0, 60, 190, athlete.gender), s.trimp, 1e-6)
    }

    @Test
    fun untimedRouteHasDistanceOnly() {
        val pts = line(10, 10.0, null).map { it.copy(timeMs = null) }
        val s = ActivityStatsCalculator.compute(pts, Sport.RIDE, athlete)
        assertNull(s.startMs)
        assertEquals(0L, s.movingMs)
        assertEquals(100.0, s.distanceM, 0.5)
        assertEquals(0.0, s.trimp, 0.0)
    }

    @Test
    fun estimatedTrimpWithoutHeartRate() {
        // 60% of reserve between 60 and 190 = 138 bpm.
        assertEquals(
            TrainingLoad.trimp(60.0, 138.0, 60, 190, athlete.gender),
            TrainingLoad.trimpOrEstimate(60.0, null, athlete),
            1e-9,
        )
    }
}

class LoopPlannerTest {
    @Test
    fun triangleIsEquilateralAndSized() {
        val start = LatLon(-6.26, 106.86)
        val (a, b) = LoopPlanner.triangle(start, 39.0)
        val side = 39_000.0 / 1.3 / 3
        assertEquals(side, Geo.distanceM(start, a), 1.0)
        assertEquals(side, Geo.distanceM(start, b), 1.0)
        assertEquals(side, Geo.distanceM(a, b), side * 0.01)
    }
}

class CoachContextTest {
    @Test
    fun promptCarriesProfileLoadAndRecentRides() {
        val prompt = CoachContext.systemPrompt(
            Athlete(age = 34, weightKg = 68.5, ftpWatts = 230),
            TrainingLoad.FitnessPoint(0, 55.4, 70.9),
            listOf(RecentActivity(Sport.RIDE, 1_790_892_000_000L, 52_300.0, 6_300_000, 210.0, 141)),
            ZoneId.of("Asia/Jakarta"),
        )
        assertTrue("34 tahun" in prompt)
        assertTrue("BB 68.5 kg" in prompt)
        assertTrue("FTP 230 W" in prompt)
        assertTrue("Form -15" in prompt)
        assertTrue("2026-10-02 Ride 52.30 km, 1:45:00, naik 210 m, HR rata-rata 141" in prompt)
    }
}
