package id.web.agentvaedah.strideway.core.challenge

import id.web.agentvaedah.strideway.core.geo.Geo
import id.web.agentvaedah.strideway.core.model.ActivitySummary
import id.web.agentvaedah.strideway.core.model.LatLon
import id.web.agentvaedah.strideway.core.model.Sport
import id.web.agentvaedah.strideway.core.privacy.PrivacyZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class ChallengesTest {
    private val zone = ZoneId.of("Asia/Jakarta")
    private val october = YearMonth.of(2026, 10)

    private fun at(day: Int, month: Int = 10) =
        LocalDateTime.of(2026, month, day, 6, 0).atZone(zone).toInstant().toEpochMilli()

    private fun act(sport: Sport, day: Int, km: Double, hours: Double = 1.0, month: Int = 10) =
        ActivitySummary(sport, at(day, month), km * 1000, (hours * 3_600_000).toLong(), 50.0)

    @Test
    fun monthlyTotalsOnlyCountThisMonth() {
        val list = listOf(
            act(Sport.RIDE, 1, 120.0),
            act(Sport.RIDE, 5, 60.0),
            act(Sport.RUN, 6, 21.1),
            act(Sport.RIDE, 30, 300.0, month = 9),
        )
        val p = Challenges.monthly(list, october, zone).associateBy { it.id }
        assertEquals(180.0, p.getValue(ChallengeId.RIDE_500).currentKm, 1e-9)
        assertFalse(p.getValue(ChallengeId.RIDE_500).completed)
        assertTrue(p.getValue(ChallengeId.GRAN_FONDO).completed)
        assertEquals(1.0, p.getValue(ChallengeId.GRAN_FONDO).fraction, 0.0)
        assertEquals(21.1, p.getValue(ChallengeId.RUN_100).currentKm, 1e-9)
    }

    @Test
    fun calendarActiveDaysAndWeeks() {
        val list = listOf(act(Sport.RIDE, 1, 50.0, 2.0), act(Sport.RUN, 2, 10.0, 1.0), act(Sport.RIDE, 12, 40.0, 1.5))
        assertEquals(setOf(1, 2, 12), TrainingCalendar.activeDays(list, october, zone))
        val weeks = TrainingCalendar.weeklyHours(list, october, zone)
        // October 2026 starts on Thursday; the first week starts Monday 28 September.
        assertEquals(LocalDate.of(2026, 9, 28), weeks.first().first)
        assertEquals(3.0, weeks.first().second, 1e-9)
        assertEquals(1.5, weeks.first { it.first == LocalDate.of(2026, 10, 12) }.second, 1e-9)
        assertEquals(5, weeks.size)
    }
}

class PrivacyZoneTest {
    @Test
    fun removesPointsInsideRadius() {
        val home = LatLon(-6.25, 106.85)
        val pts = listOf(0.0, 300.0, 499.0, 501.0, 2000.0).map { Geo.destination(home, 45.0, it) }
        val kept = PrivacyZone.trim(pts, home, 500.0) { it }
        assertEquals(2, kept.size)
    }
}
