package id.web.agentvaedah.strideway.core.stats

import id.web.agentvaedah.strideway.core.model.Sport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ProgressStatsTest {
    private val zone = ZoneId.of("Asia/Jakarta")
    private val today = LocalDate.of(2026, 10, 14) // Wednesday

    private fun act(date: LocalDate, km: Double, gain: Double = 0.0, hours: Double = 1.0, sport: Sport = Sport.RIDE, avgHr: Int? = null) =
        StatActivity(
            sport = sport,
            startMs = LocalDateTime.of(date, java.time.LocalTime.of(6, 0)).atZone(zone).toInstant().toEpochMilli(),
            distanceM = km * 1000,
            elevationGainM = gain,
            movingMs = (hours * 3_600_000).toLong(),
            avgHr = avgHr,
        )

    @Test
    fun rangeStartDates() {
        assertEquals(LocalDate.of(2026, 10, 8), StatRange.D7.start(today))
        assertEquals(LocalDate.of(2026, 9, 15), StatRange.M1.start(today))
        assertEquals(LocalDate.of(2026, 7, 15), StatRange.M3.start(today))
        assertEquals(LocalDate.of(2026, 4, 15), StatRange.M6.start(today))
        assertEquals(LocalDate.of(2026, 1, 1), StatRange.YTD.start(today))
        assertEquals(LocalDate.of(2025, 10, 15), StatRange.Y1.start(today))
    }

    @Test
    fun sevenDayRangeHasDailyBuckets() {
        val list = listOf(act(today, 30.0, 200.0), act(today.minusDays(2), 20.0), act(today.minusDays(9), 99.0))
        val s = ProgressStats.series(list, StatRange.D7, today, zone)
        assertEquals(7, s.buckets.size)
        assertEquals(today, s.buckets.last().start)
        assertEquals(30.0, s.buckets.last().distanceKm, 1e-9)
        assertEquals(20.0, s.buckets[4].distanceKm, 1e-9)
        assertEquals(50.0, s.total.distanceKm, 1e-9)
        assertEquals(200.0, s.total.elevationM, 1e-9)
        assertEquals(2, s.total.activities)
    }

    @Test
    fun longerRangesUseWeeklyBuckets() {
        val s = ProgressStats.series(emptyList(), StatRange.M3, today, zone)
        assertTrue(s.buckets.all { it.start.dayOfWeek == java.time.DayOfWeek.MONDAY })
        assertTrue(s.buckets.size in 13..15)
        assertTrue(!s.buckets.last().start.isAfter(today))
    }

    @Test
    fun metricValueSelectsField() {
        val b = StatBucket(today, distanceKm = 10.0, elevationM = 100.0, movingHours = 2.0, activities = 3)
        assertEquals(10.0, StatMetric.DISTANCE.of(b))
        assertEquals(100.0, StatMetric.ELEVATION.of(b))
        assertEquals(2.0, StatMetric.TIME.of(b))
        assertEquals(3.0, StatMetric.ACTIVITIES.of(b))
    }

    @Test
    fun sportFilter() {
        val list = listOf(act(today, 30.0), act(today, 5.0, sport = Sport.RUN))
        assertEquals(5.0, ProgressStats.series(list, StatRange.D7, today, zone, Sport.RUN).total.distanceKm, 1e-9)
    }

    @Test
    fun weeklyStreakCountsConsecutiveWeeksEndingThisOrLastWeek() {
        val list = listOf(
            act(today, 10.0),
            act(today.minusWeeks(1), 10.0),
            act(today.minusWeeks(2), 10.0),
            act(today.minusWeeks(4), 10.0),
        )
        val s = Streaks.compute(list, today, zone)
        assertEquals(3, s.currentWeeks)
        assertEquals(3, s.longestWeeks)
    }

    @Test
    fun streakSurvivesUntilCurrentWeekEnds() {
        // Nothing yet this week, but last week counted: the streak is still alive.
        val list = listOf(act(today.minusWeeks(1), 10.0), act(today.minusWeeks(2), 10.0))
        assertEquals(2, Streaks.compute(list, today, zone).currentWeeks)
        val broken = listOf(act(today.minusWeeks(2), 10.0))
        assertEquals(0, Streaks.compute(broken, today, zone).currentWeeks)
    }

    @Test
    fun dailyStreak() {
        val list = listOf(act(today, 5.0), act(today.minusDays(1), 5.0), act(today.minusDays(3), 5.0))
        assertEquals(2, Streaks.compute(list, today, zone).currentDays)
    }

    @Test
    fun weeklySnapshotComparesWithPreviousWeek() {
        val list = listOf(act(today, 40.0, 300.0, 2.0), act(today.minusDays(7), 20.0, 100.0, 1.0))
        val w = ProgressStats.weeklySnapshot(list, today, zone)
        assertEquals(40.0, w.thisWeek.distanceKm, 1e-9)
        assertEquals(20.0, w.lastWeek.distanceKm, 1e-9)
        assertEquals(LocalDate.of(2026, 10, 12), w.weekStart)
    }

    @Test
    fun hrZoneTimeSharesFromAverageHr() {
        val zones = id.web.agentvaedah.strideway.core.physio.Zones.heartRate(190, 60)
        val list = listOf(
            act(today, 10.0, hours = 1.0, avgHr = zones[1].low + 1),
            act(today.minusDays(1), 10.0, hours = 3.0, avgHr = zones[2].low + 1),
            act(today.minusDays(2), 10.0, hours = 5.0),
        )
        val h = ProgressStats.hrSummary(list, StatRange.M1, today, zone, zones)
        // zones[1] is Z2 and zones[2] is Z3; hoursByZone is indexed by zone number.
        assertEquals(1.0, h.hoursByZone[2], 1e-9)
        assertEquals(3.0, h.hoursByZone[3], 1e-9)
        assertEquals(2, h.activitiesWithHr)
        assertEquals(((zones[1].low + 1) * 1.0 + (zones[2].low + 1) * 3.0) / 4.0, h.avgHr!!, 1e-9)
    }
}
