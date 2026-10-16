package id.web.agentvaedah.strideway.core.records

import id.web.agentvaedah.strideway.core.geo.Geo
import id.web.agentvaedah.strideway.core.model.LatLon
import id.web.agentvaedah.strideway.core.model.Sport

/** How a record compares: fastest efforts are times (lower wins), the rest are amounts (higher wins). */
enum class Better { LOWER, HIGHER }

enum class RecordKey(val sport: Sport, val label: String, val better: Better, val meters: Double = 0.0) {
    RIDE_LONGEST(Sport.RIDE, "Ride terjauh", Better.HIGHER),
    RIDE_ELEVATION(Sport.RIDE, "Elevasi terbanyak", Better.HIGHER),
    RIDE_AVG_SPEED(Sport.RIDE, "Kecepatan rata-rata tertinggi", Better.HIGHER),
    RIDE_5K(Sport.RIDE, "5 km tercepat", Better.LOWER, 5_000.0),
    RIDE_10K(Sport.RIDE, "10 km tercepat", Better.LOWER, 10_000.0),
    RIDE_20K(Sport.RIDE, "20 km tercepat", Better.LOWER, 20_000.0),
    RIDE_40K(Sport.RIDE, "40 km tercepat", Better.LOWER, 40_000.0),
    RUN_LONGEST(Sport.RUN, "Lari terjauh", Better.HIGHER),
    RUN_1K(Sport.RUN, "1 km tercepat", Better.LOWER, 1_000.0),
    RUN_5K(Sport.RUN, "5 km tercepat", Better.LOWER, 5_000.0),
    RUN_10K(Sport.RUN, "10 km tercepat", Better.LOWER, 10_000.0),
    RUN_HALF(Sport.RUN, "Half marathon tercepat", Better.LOWER, 21_097.5),
    ;

    val isEffort: Boolean get() = meters > 0
}

data class EffortPoint(val lat: Double, val lon: Double, val timeMs: Long)

/** Fastest time over a distance: [seconds] between points [startIdx] and [endIdx] (interpolated at the end). */
data class Effort(val seconds: Double, val startIdx: Int, val endIdx: Int)

object BestEfforts {
    /** Bumped when the algorithm changes so stored efforts get recomputed. */
    const val VERSION = 1

    fun targets(sport: Sport): List<RecordKey> = RecordKey.entries.filter { it.sport == sport && it.isEffort }

    /**
     * Two pointers over cumulative distance. For each start, the end advances to the first point that
     * reaches the target, and the crossing time is interpolated inside that last segment. Pauses count,
     * as on Strava, because they are part of the elapsed time between the two points.
     */
    fun fastest(points: List<EffortPoint>, meters: Double): Effort? {
        if (points.size < 2) return null
        val cum = DoubleArray(points.size)
        for (i in 1 until points.size) {
            cum[i] = cum[i - 1] + Geo.distanceM(LatLon(points[i - 1].lat, points[i - 1].lon), LatLon(points[i].lat, points[i].lon))
        }
        if (cum.last() < meters) return null
        var best: Effort? = null
        var j = 1
        for (i in 0 until points.size - 1) {
            if (j <= i) j = i + 1
            while (j < points.size && cum[j] - cum[i] < meters) j++
            if (j >= points.size) break
            val segLen = cum[j] - cum[j - 1]
            val need = meters - (cum[j - 1] - cum[i])
            val frac = if (segLen > 0) (need / segLen).coerceIn(0.0, 1.0) else 1.0
            val endMs = points[j - 1].timeMs + (points[j].timeMs - points[j - 1].timeMs) * frac
            val secs = (endMs - points[i].timeMs) / 1000.0
            if (secs > 0 && (best == null || secs < best.seconds)) best = Effort(secs, i, j)
        }
        return best
    }

    fun computeAll(points: List<EffortPoint>, sport: Sport): Map<RecordKey, Effort> =
        targets(sport).mapNotNull { k -> fastest(points, k.meters)?.let { k to it } }.toMap()
}

data class RecordActivity(
    val id: Long,
    val sport: Sport,
    val startMs: Long,
    val distanceM: Double,
    val elevationGainM: Double,
    val movingMs: Long,
)

data class EffortRow(val activityId: Long, val key: RecordKey, val seconds: Double)

data class RecordHolder(val key: RecordKey, val activityId: Long, val value: Double, val startMs: Long)

/** One of the three best results for a record; [rank] 1 is the record itself. */
data class RecordRank(val rank: Int, val activityId: Long, val value: Double, val startMs: Long)

/**
 * [holders] are the current records; [top] keeps the three best per record, one per activity; [previous]
 * is the record value that stood before the current holder set it, for "0:42 faster" lines.
 */
class PersonalRecordSet(
    val holders: Map<RecordKey, RecordHolder>,
    val top: Map<RecordKey, List<RecordRank>> = emptyMap(),
    val previous: Map<RecordKey, Double> = emptyMap(),
) {
    private val byActivity = holders.values.groupBy({ it.activityId }, { it.key })

    fun badgesFor(activityId: Long): List<RecordKey> = byActivity[activityId].orEmpty().sortedBy { it.ordinal }

    /** Every record this activity places on the podium for, with its place. */
    fun ranksFor(activityId: Long): Map<RecordKey, Int> =
        top.mapNotNull { (k, list) -> list.firstOrNull { it.activityId == activityId }?.let { k to it.rank } }.toMap()
}

/**
 * Current personal records over the whole history. The first activity of a sport holds every record it
 * qualifies for, so a new user sees PRs straight away. Ties stay with the earlier activity.
 */
object PersonalRecords {
    private const val MIN_AVG_SPEED_M = 5_000.0

    fun compute(activities: List<RecordActivity>, efforts: List<EffortRow>): PersonalRecordSet {
        val byId = activities.associateBy { it.id }
        val candidates = ArrayList<RecordHolder>()
        for (a in activities) {
            val longest = if (a.sport == Sport.RIDE) RecordKey.RIDE_LONGEST else RecordKey.RUN_LONGEST
            if (a.distanceM > 0) candidates += RecordHolder(longest, a.id, a.distanceM, a.startMs)
            if (a.sport == Sport.RIDE) {
                if (a.elevationGainM > 0) candidates += RecordHolder(RecordKey.RIDE_ELEVATION, a.id, a.elevationGainM, a.startMs)
                if (a.distanceM >= MIN_AVG_SPEED_M && a.movingMs > 0) {
                    candidates += RecordHolder(RecordKey.RIDE_AVG_SPEED, a.id, a.distanceM / (a.movingMs / 1000.0) * 3.6, a.startMs)
                }
            }
        }
        for (e in efforts) {
            val a = byId[e.activityId] ?: continue
            if (a.sport == e.key.sport) candidates += RecordHolder(e.key, a.id, e.seconds, a.startMs)
        }
        val holders = HashMap<RecordKey, RecordHolder>()
        val top = HashMap<RecordKey, List<RecordRank>>()
        val previous = HashMap<RecordKey, Double>()
        for ((key, list) in candidates.groupBy { it.key }) {
            val cmp = compareBy<RecordHolder> { if (key.better == Better.LOWER) it.value else -it.value }.thenBy { it.startMs }
            // One place per activity, even if a key were somehow stored twice for it.
            val ranked = list.sortedWith(cmp).distinctBy { it.activityId }
            val best = ranked.first()
            holders[key] = best
            top[key] = ranked.take(3).mapIndexed { i, h -> RecordRank(i + 1, h.activityId, h.value, h.startMs) }
            list.filter { it.startMs < best.startMs }.minWithOrNull(cmp)?.let { previous[key] = it.value }
        }
        return PersonalRecordSet(holders, top, previous)
    }
}
