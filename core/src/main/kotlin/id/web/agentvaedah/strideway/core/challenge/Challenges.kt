package id.web.agentvaedah.strideway.core.challenge

import id.web.agentvaedah.strideway.core.model.ActivitySummary
import id.web.agentvaedah.strideway.core.model.Sport
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class ChallengeId(val title: String, val targetKm: Double, val rule: String) {
    RIDE_500("Ride 500 km", 500.0, "Total jarak sepeda bulan ini"),
    GRAN_FONDO("Gran Fondo 100 km", 100.0, "Satu ride sejauh 100 km atau lebih"),
    RUN_100("Run 100 km", 100.0, "Total jarak lari bulan ini"),
}

data class ChallengeProgress(val id: ChallengeId, val currentKm: Double, val targetKm: Double) {
    val fraction: Double get() = (currentKm / targetKm).coerceIn(0.0, 1.0)
    val completed: Boolean get() = currentKm >= targetKm
}

object Challenges {
    fun monthly(activities: List<ActivitySummary>, month: YearMonth, zone: ZoneId): List<ChallengeProgress> {
        val inMonth = activities.filter { YearMonth.from(Instant.ofEpochMilli(it.startMs).atZone(zone)) == month }
        val rideKm = inMonth.filter { it.sport == Sport.RIDE }.sumOf { it.distanceM } / 1000.0
        val runKm = inMonth.filter { it.sport == Sport.RUN }.sumOf { it.distanceM } / 1000.0
        val longestRideKm = inMonth.filter { it.sport == Sport.RIDE }.maxOfOrNull { it.distanceM / 1000.0 } ?: 0.0
        return listOf(
            ChallengeProgress(ChallengeId.RIDE_500, rideKm, ChallengeId.RIDE_500.targetKm),
            ChallengeProgress(ChallengeId.GRAN_FONDO, longestRideKm, ChallengeId.GRAN_FONDO.targetKm),
            ChallengeProgress(ChallengeId.RUN_100, runKm, ChallengeId.RUN_100.targetKm),
        )
    }
}

object TrainingCalendar {
    fun activeDays(activities: List<ActivitySummary>, month: YearMonth, zone: ZoneId): Set<Int> =
        activities.map { Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate() }
            .filter { YearMonth.from(it) == month }
            .map { it.dayOfMonth }
            .toSet()

    /** Moving hours per Monday-starting week that overlaps [month], in order. */
    fun weeklyHours(activities: List<ActivitySummary>, month: YearMonth, zone: ZoneId): List<Pair<LocalDate, Double>> {
        val firstMonday = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val last = month.atEndOfMonth()
        val weeks = generateSequence(firstMonday) { it.plusWeeks(1) }.takeWhile { !it.isAfter(last) }.toList()
        val hoursByWeek = activities.groupBy {
            Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        }.mapValues { (_, list) -> list.sumOf { it.movingMs } / 3_600_000.0 }
        return weeks.map { it to (hoursByWeek[it] ?: 0.0) }
    }
}

data class Award(val key: String, val id: ChallengeId, val month: YearMonth)

/**
 * Challenges are opt-in: only joined ones can be earned. An award is keyed by challenge and month,
 * so the same challenge can be earned again next month.
 */
object Achievements {
    fun key(id: ChallengeId, month: YearMonth): String = "${id.name}@$month"

    fun parse(key: String): Pair<ChallengeId, YearMonth>? {
        val parts = key.split('@')
        if (parts.size != 2) return null
        val id = runCatching { ChallengeId.valueOf(parts[0]) }.getOrNull() ?: return null
        val month = runCatching { YearMonth.parse(parts[1]) }.getOrNull() ?: return null
        return id to month
    }

    fun newlyEarned(
        activities: List<ActivitySummary>,
        month: YearMonth,
        zone: ZoneId,
        joined: Set<ChallengeId>,
        alreadyEarned: Set<String>,
    ): List<Award> = Challenges.monthly(activities, month, zone)
        .filter { it.completed && it.id in joined && key(it.id, month) !in alreadyEarned }
        .map { Award(key(it.id, month), it.id, month) }
}
