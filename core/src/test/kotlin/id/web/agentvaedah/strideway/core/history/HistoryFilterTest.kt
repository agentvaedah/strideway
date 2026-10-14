package id.web.agentvaedah.strideway.core.history

import id.web.agentvaedah.strideway.core.model.Sport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

class HistoryFilterTest {
    private val zone = ZoneId.of("Asia/Jakarta")

    private fun item(id: Long, name: String, date: LocalDate, sport: Sport = Sport.RIDE, km: Double = 10.0) =
        HistoryItem(id, name, sport, LocalDateTime.of(date, LocalTime.NOON).atZone(zone).toInstant().toEpochMilli(), km * 1000, 3_600_000, 100.0)

    private val list = listOf(
        item(1, "Gowes Pagi Bogor", LocalDate.of(2026, 10, 3)),
        item(2, "Lari sore", LocalDate.of(2026, 10, 1), Sport.RUN, 5.0),
        item(3, "Ride ke Puncak", LocalDate.of(2026, 9, 30)),
        item(4, "Ride tahun baru", LocalDate.of(2025, 12, 31)),
    )

    @Test
    fun filterBySport() {
        assertEquals(listOf(2L), HistoryFilter(sports = setOf(Sport.RUN)).apply(list, zone).map { it.id })
    }

    @Test
    fun filterByDateRangeInclusive() {
        val f = HistoryFilter(from = LocalDate.of(2026, 9, 30), to = LocalDate.of(2026, 10, 1))
        assertEquals(listOf(2L, 3L), f.apply(list, zone).map { it.id }.sorted())
    }

    @Test
    fun searchIgnoresCaseAndAccents() {
        assertEquals(listOf(3L), HistoryFilter(query = "PÚNCAK").apply(list, zone).map { it.id })
        assertEquals(listOf(1L), HistoryFilter(query = "bogor").apply(list, zone).map { it.id })
    }

    @Test
    fun combinedFilters() {
        val f = HistoryFilter(sports = setOf(Sport.RIDE), from = LocalDate.of(2026, 10, 1), query = "gowes")
        assertEquals(listOf(1L), f.apply(list, zone).map { it.id })
    }

    @Test
    fun groupsByMonthAcrossYearsNewestFirst() {
        val groups = HistoryFilter.groupByMonth(list, zone)
        assertEquals(listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 9), YearMonth.of(2025, 12)), groups.map { it.month })
        val oct = groups.first()
        assertEquals(2, oct.count)
        assertEquals(15.0, oct.distanceKm, 1e-9)
        assertEquals(2.0, oct.movingHours, 1e-9)
        assertEquals(200.0, oct.elevationM, 1e-9)
        assertEquals(listOf(1L, 2L), oct.items.map { it.id })
    }
}
