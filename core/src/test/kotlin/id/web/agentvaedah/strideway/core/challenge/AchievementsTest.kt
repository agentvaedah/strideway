package id.web.agentvaedah.strideway.core.challenge

import id.web.agentvaedah.strideway.core.model.ActivitySummary
import id.web.agentvaedah.strideway.core.model.Sport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class AchievementsTest {
    private val zone = ZoneId.of("Asia/Jakarta")
    private val october = YearMonth.of(2026, 10)

    private fun ride(day: Int, km: Double) =
        ActivitySummary(Sport.RIDE, LocalDateTime.of(2026, 10, day, 6, 0).atZone(zone).toInstant().toEpochMilli(), km * 1000, 3_600_000, 50.0)

    @Test
    fun notJoinedChallengesAreNotAwarded() {
        val list = listOf(ride(1, 120.0))
        val newly = Achievements.newlyEarned(list, october, zone, joined = emptySet(), alreadyEarned = emptySet())
        assertTrue(newly.isEmpty())
    }

    @Test
    fun joinedAndCompletedIsAwardedOnce() {
        val list = listOf(ride(1, 120.0))
        val joined = setOf(ChallengeId.GRAN_FONDO)
        val newly = Achievements.newlyEarned(list, october, zone, joined, alreadyEarned = emptySet())
        assertEquals(listOf(Achievements.key(ChallengeId.GRAN_FONDO, october)), newly.map { it.key })
        val again = Achievements.newlyEarned(list, october, zone, joined, alreadyEarned = newly.map { it.key }.toSet())
        assertTrue(again.isEmpty())
    }

    @Test
    fun keyIsPerMonth() {
        assertEquals("RIDE_500@2026-10", Achievements.key(ChallengeId.RIDE_500, october))
        val parsed = Achievements.parse("RIDE_500@2026-10")!!
        assertEquals(ChallengeId.RIDE_500, parsed.first)
        assertEquals(october, parsed.second)
    }
}
