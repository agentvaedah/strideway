package id.web.agentvaedah.strideway.core.history

import id.web.agentvaedah.strideway.core.model.Sport
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class HistoryItem(
    val id: Long,
    val name: String,
    val sport: Sport,
    val startMs: Long,
    val distanceM: Double,
    val movingMs: Long,
    val elevationGainM: Double,
)

data class MonthGroup(
    val month: YearMonth,
    val items: List<HistoryItem>,
    val count: Int,
    val distanceKm: Double,
    val movingHours: Double,
    val elevationM: Double,
)

/** Empty [sports] means every sport; [from]/[to] are inclusive local dates; [query] matches names loosely. */
data class HistoryFilter(
    val sports: Set<Sport> = emptySet(),
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val query: String = "",
) {
    val isActive: Boolean get() = sports.isNotEmpty() || from != null || to != null || query.isNotBlank()

    fun apply(list: List<HistoryItem>, zone: ZoneId): List<HistoryItem> {
        val q = fold(query)
        return list.filter { a ->
            val date = Instant.ofEpochMilli(a.startMs).atZone(zone).toLocalDate()
            (sports.isEmpty() || a.sport in sports) &&
                (from == null || !date.isBefore(from)) &&
                (to == null || !date.isAfter(to)) &&
                (q.isEmpty() || fold(a.name).contains(q))
        }
    }

    companion object {
        /** Newest month first; items inside a month newest first. */
        fun groupByMonth(list: List<HistoryItem>, zone: ZoneId): List<MonthGroup> =
            list.groupBy { YearMonth.from(Instant.ofEpochMilli(it.startMs).atZone(zone)) }
                .toSortedMap(compareByDescending { it })
                .map { (m, items) ->
                    val sorted = items.sortedByDescending { it.startMs }
                    MonthGroup(
                        month = m,
                        items = sorted,
                        count = sorted.size,
                        distanceKm = sorted.sumOf { it.distanceM } / 1000.0,
                        movingHours = sorted.sumOf { it.movingMs } / 3_600_000.0,
                        elevationM = sorted.sumOf { it.elevationGainM },
                    )
                }

        private val marks = Regex("\\p{M}+")

        private fun fold(s: String): String = marks.replace(Normalizer.normalize(s.trim(), Normalizer.Form.NFD), "").lowercase()
    }
}
