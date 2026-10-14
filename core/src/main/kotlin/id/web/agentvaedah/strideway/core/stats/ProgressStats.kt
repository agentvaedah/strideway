package id.web.agentvaedah.strideway.core.stats

import id.web.agentvaedah.strideway.core.model.Sport
import id.web.agentvaedah.strideway.core.physio.Zone
import id.web.agentvaedah.strideway.core.physio.Zones
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** The fields of an activity that progress charts need. */
data class StatActivity(
    val sport: Sport,
    val startMs: Long,
    val distanceM: Double,
    val elevationGainM: Double,
    val movingMs: Long,
    val avgHr: Int? = null,
)

enum class StatRange(val label: String) {
    D7("7H"), M1("1B"), M3("3B"), M6("6B"), YTD("YTD"), Y1("1T");

    /** First day included, counting today as the last day. */
    fun start(today: LocalDate): LocalDate = when (this) {
        D7 -> today.minusDays(6)
        M1 -> today.minusMonths(1).plusDays(1)
        M3 -> today.minusMonths(3).plusDays(1)
        M6 -> today.minusMonths(6).plusDays(1)
        YTD -> today.withDayOfYear(1)
        Y1 -> today.minusYears(1).plusDays(1)
    }

    val daily: Boolean get() = this == D7 || this == M1
}

enum class StatMetric(val label: String, val unit: String) {
    DISTANCE("Jarak", "km"),
    ELEVATION("Elevasi", "m"),
    TIME("Waktu", "jam"),
    ACTIVITIES("Aktivitas", "");

    fun of(b: StatBucket): Double = when (this) {
        DISTANCE -> b.distanceKm
        ELEVATION -> b.elevationM
        TIME -> b.movingHours
        ACTIVITIES -> b.activities.toDouble()
    }
}

data class StatBucket(
    val start: LocalDate,
    val distanceKm: Double = 0.0,
    val elevationM: Double = 0.0,
    val movingHours: Double = 0.0,
    val activities: Int = 0,
) {
    operator fun plus(a: StatActivity) = copy(
        distanceKm = distanceKm + a.distanceM / 1000.0,
        elevationM = elevationM + a.elevationGainM,
        movingHours = movingHours + a.movingMs / 3_600_000.0,
        activities = activities + 1,
    )
}

data class StatSeries(val range: StatRange, val buckets: List<StatBucket>, val total: StatBucket)

data class WeeklySnapshot(val weekStart: LocalDate, val thisWeek: StatBucket, val lastWeek: StatBucket, val days: List<StatBucket>)

/** Hours spent per HR zone, attributed by each activity's average HR (zone 0 = below Z1). */
data class HrSummary(val hoursByZone: DoubleArray, val avgHr: Double?, val maxAvgHr: Int?, val activitiesWithHr: Int)

object ProgressStats {
    private fun date(a: StatActivity, zone: ZoneId): LocalDate = Instant.ofEpochMilli(a.startMs).atZone(zone).toLocalDate()

    private fun monday(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /**
     * Totals per day for 7 days and one month, per Monday-starting week for longer ranges.
     * [sport] null includes every sport.
     */
    fun series(list: List<StatActivity>, range: StatRange, today: LocalDate, zone: ZoneId, sport: Sport? = null): StatSeries {
        val from = range.start(today)
        val inRange = list.filter { (sport == null || it.sport == sport) && date(it, zone) in from..today }
        val starts = if (range.daily) {
            generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.toList()
        } else {
            generateSequence(monday(from)) { it.plusWeeks(1) }.takeWhile { !it.isAfter(today) }.toList()
        }
        val keyOf: (LocalDate) -> LocalDate = if (range.daily) { d -> d } else ::monday
        val grouped = inRange.groupBy { keyOf(date(it, zone)) }
        val buckets = starts.map { s -> grouped[s].orEmpty().fold(StatBucket(s)) { b, a -> b + a } }
        val total = inRange.fold(StatBucket(from)) { b, a -> b + a }
        return StatSeries(range, buckets, total)
    }

    fun weeklySnapshot(list: List<StatActivity>, today: LocalDate, zone: ZoneId): WeeklySnapshot {
        val start = monday(today)
        val thisWeek = list.filter { date(it, zone) in start..start.plusDays(6) }
        val lastWeek = list.filter { date(it, zone) in start.minusWeeks(1)..start.minusDays(1) }
        val days = (0L until 7L).map { i ->
            val d = start.plusDays(i)
            thisWeek.filter { date(it, zone) == d }.fold(StatBucket(d)) { b, a -> b + a }
        }
        return WeeklySnapshot(
            weekStart = start,
            thisWeek = thisWeek.fold(StatBucket(start)) { b, a -> b + a },
            lastWeek = lastWeek.fold(StatBucket(start.minusWeeks(1))) { b, a -> b + a },
            days = days,
        )
    }

    fun hrSummary(list: List<StatActivity>, range: StatRange, today: LocalDate, zone: ZoneId, zones: List<Zone>): HrSummary {
        val from = range.start(today)
        val withHr = list.filter { it.avgHr != null && date(it, zone) in from..today }
        val hours = DoubleArray(zones.size + 1)
        withHr.forEach { a -> hours[Zones.zoneOf(a.avgHr!!, zones)] += a.movingMs / 3_600_000.0 }
        val totalHours = withHr.sumOf { it.movingMs / 3_600_000.0 }
        val avg = if (totalHours > 0) withHr.sumOf { it.avgHr!! * it.movingMs / 3_600_000.0 } / totalHours else null
        return HrSummary(hours, avg, withHr.maxOfOrNull { it.avgHr!! }, withHr.size)
    }
}

data class StreakInfo(val currentWeeks: Int, val longestWeeks: Int, val currentDays: Int, val longestDays: Int)

object Streaks {
    /**
     * A week counts when it has at least one activity. The current streak stays alive through the
     * running week: it may end at this week or, if this week is still empty, at last week.
     */
    fun compute(list: List<StatActivity>, today: LocalDate, zone: ZoneId): StreakInfo {
        val days = list.map { Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate() }.filter { !it.isAfter(today) }.toSortedSet()
        val weeks = days.map { it.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }.toSortedSet()
        val thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        fun run(set: Set<LocalDate>, from: LocalDate, step: (LocalDate) -> LocalDate): Int {
            var n = 0
            var d = from
            while (d in set) { n++; d = step(d) }
            return n
        }
        fun longest(sorted: List<LocalDate>, unit: ChronoUnit): Int {
            var best = 0
            var cur = 0
            var prev: LocalDate? = null
            for (d in sorted) {
                cur = if (prev != null && unit.between(prev, d) == 1L) cur + 1 else 1
                best = maxOf(best, cur)
                prev = d
            }
            return best
        }

        val weekAnchor = if (thisWeek in weeks) thisWeek else thisWeek.minusWeeks(1)
        val dayAnchor = if (today in days) today else today.minusDays(1)
        return StreakInfo(
            currentWeeks = run(weeks, weekAnchor) { it.minusWeeks(1) },
            longestWeeks = longest(weeks.toList(), ChronoUnit.WEEKS),
            currentDays = run(days, dayAnchor) { it.minusDays(1) },
            longestDays = longest(days.toList(), ChronoUnit.DAYS),
        )
    }
}
