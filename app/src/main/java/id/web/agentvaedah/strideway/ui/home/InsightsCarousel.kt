package id.web.agentvaedah.strideway.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import id.web.agentvaedah.strideway.core.format.Format
import id.web.agentvaedah.strideway.core.gear.GearLevel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.web.agentvaedah.strideway.core.stats.StatBucket
import id.web.agentvaedah.strideway.core.stats.StatMetric
import id.web.agentvaedah.strideway.ui.components.BarPoint
import id.web.agentvaedah.strideway.ui.components.ProgressPanel
import id.web.agentvaedah.strideway.ui.components.ScrubBarChart
import id.web.agentvaedah.strideway.ui.components.StatPair
import id.web.agentvaedah.strideway.ui.components.formatMetric
import id.web.agentvaedah.strideway.ui.theme.MetricText
import id.web.agentvaedah.strideway.ui.theme.Palette
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("id-ID"))
private val pages = listOf("Snapshot", "Progres", "Sepeda", "Kalender", "Analisis HR", "Streak")

/** Swipeable insight cards; the tab row doubles as a page indicator and lets the user jump directly. */
@Composable
internal fun InsightsCarousel(s: HomeState.Ready, vm: HomeViewModel, onOpenGarage: () -> Unit) {
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        ScrollableTabRow(
            selectedTabIndex = pager.currentPage,
            edgePadding = 16.dp,
            containerColor = Palette.Bg,
            divider = {},
        ) {
            pages.forEachIndexed { i, title ->
                Tab(selected = pager.currentPage == i, onClick = { scope.launch { pager.animateScrollToPage(i) } }, text = { Text(title) })
            }
        }
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
            verticalAlignment = Alignment.Top,
            // A drag that starts on a chart scrubs the chart; a drag elsewhere on the card changes the page.
            beyondViewportPageCount = 1,
        ) { page ->
            Card {
                when (page) {
                    0 -> WeeklySnapshotCard(s)
                    1 -> ProgressPanel(s.series, s.metric, vm::setRange, vm::setMetric)
                    2 -> BikeCard(s.bike, onOpenGarage)
                    3 -> CalendarSection(s.calendar, vm::previousMonth, vm::nextMonth)
                    4 -> HrCard(s)
                    else -> StreakCard(s)
                }
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 280.dp)
            .padding(top = 8.dp)
            .background(Palette.Surface, MaterialTheme.shapes.large)
            .padding(12.dp),
    ) { content() }
}

@Composable
private fun WeeklySnapshotCard(s: HomeState.Ready) {
    val w = s.weekly
    Text("Minggu ini sejak ${w.weekStart.format(shortDate)}", style = MetricText.Label, color = Palette.TextMuted)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Delta("Jarak", StatMetric.DISTANCE, w.thisWeek, w.lastWeek)
        Delta("Naik", StatMetric.ELEVATION, w.thisWeek, w.lastWeek)
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Delta("Waktu", StatMetric.TIME, w.thisWeek, w.lastWeek)
        Delta("Aktivitas", StatMetric.ACTIVITIES, w.thisWeek, w.lastWeek)
    }
    Spacer(Modifier.height(12.dp))
    ScrubBarChart(
        points = w.days.map { BarPoint(it.start.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("id-ID")), it.distanceKm) },
        format = { formatMetric(StatMetric.DISTANCE, it) },
        summary = "Jarak per hari",
        description = "Jarak per hari minggu ini, total ${formatMetric(StatMetric.DISTANCE, w.thisWeek.distanceKm)}",
        height = 96.dp,
    )
}

@Composable
private fun Delta(label: String, metric: StatMetric, now: StatBucket, before: StatBucket) {
    val a = metric.of(now)
    val b = metric.of(before)
    val text = when {
        b == 0.0 && a == 0.0 -> "sama dengan minggu lalu"
        b == 0.0 -> "minggu lalu kosong"
        else -> {
            val pct = ((a - b) / b * 100).roundToInt()
            if (pct >= 0) "+$pct% dari minggu lalu" else "$pct% dari minggu lalu"
        }
    }
    Column(Modifier.width(150.dp)) {
        Text(label, style = MetricText.Label, color = Palette.TextMuted)
        Text(formatMetric(metric, a), style = MetricText.Small, color = Palette.Text)
        Text(text, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
    }
}

@Composable
private fun HrCard(s: HomeState.Ready) {
    val h = s.hr
    Text("30 hari terakhir", style = MetricText.Label, color = Palette.TextMuted)
    if (h.activitiesWithHr == 0) {
        Spacer(Modifier.height(16.dp))
        Text(
            "Belum ada aktivitas dengan sensor detak jantung. Sambungkan sensor HR atau jam di Pengaturan > Perangkat.",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextMuted,
        )
        return
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        StatPair("HR rata-rata", h.avgHr?.roundToInt()?.toString() ?: "--")
        StatPair("rata-rata tertinggi", h.maxAvgHr?.toString() ?: "--")
        StatPair("aktivitas", h.activitiesWithHr.toString())
    }
    Spacer(Modifier.height(12.dp))
    Text("Waktu per zona", style = MaterialTheme.typography.labelLarge, color = Palette.TextMuted)
    val total = h.hoursByZone.sum().coerceAtLeast(1e-9)
    s.hrZones.forEach { z ->
        val hours = h.hoursByZone[z.index]
        val label = "Z${z.index} ${z.name}: ${formatMetric(StatMetric.TIME, hours)}"
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics(mergeDescendants = true) { contentDescription = label },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).background(Palette.hrZone(z.index), CircleShape))
            Spacer(Modifier.width(8.dp))
            Text("Z${z.index}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(28.dp))
            Box(Modifier.weight(1f).height(8.dp).background(Palette.SurfaceHigh, MaterialTheme.shapes.extraSmall)) {
                Box(
                    Modifier
                        .fillMaxWidth((hours / total).toFloat().coerceIn(0f, 1f))
                        .height(8.dp)
                        .background(Palette.hrZone(z.index), MaterialTheme.shapes.extraSmall),
                )
            }
            Text(
                formatMetric(StatMetric.TIME, hours),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.End,
                modifier = Modifier.width(64.dp),
            )
        }
    }
    Text(
        "Dihitung dari HR rata-rata tiap aktivitas, jadi perkiraan kasar distribusi intensitas.",
        style = MaterialTheme.typography.bodySmall,
        color = Palette.TextMuted,
    )
}

@Composable
private fun StreakCard(s: HomeState.Ready) {
    val st = s.streak
    Text("Konsistensi", style = MetricText.Label, color = Palette.TextMuted)
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.Bottom) {
        Text(st.currentWeeks.toString(), style = MetricText.Huge, color = Palette.Text)
        Spacer(Modifier.width(8.dp))
        Text("minggu beruntun", style = MaterialTheme.typography.bodyLarge, color = Palette.TextMuted, modifier = Modifier.padding(bottom = 10.dp))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        StatPair("terpanjang (minggu)", st.longestWeeks.toString())
        StatPair("hari beruntun", st.currentDays.toString())
        StatPair("terpanjang (hari)", st.longestDays.toString())
    }
    Spacer(Modifier.height(12.dp))
    Text("Jam latihan per minggu", style = MaterialTheme.typography.labelLarge, color = Palette.TextMuted)
    ScrubBarChart(
        points = s.calendar.weeklyHours.map { (start, hours) -> BarPoint(start.format(shortDate), hours) },
        format = { formatMetric(StatMetric.TIME, it) },
        summary = "Bulan ${s.calendar.month.month.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("id-ID"))}",
        description = "Jam latihan tiap minggu di bulan yang dipilih",
        height = 96.dp,
    )
    Text(
        "Satu minggu dihitung bila ada minimal satu aktivitas. Streak tetap hidup sampai minggu ini berakhir.",
        style = MaterialTheme.typography.bodySmall,
        color = Palette.TextMuted,
    )
}

/** Bike wear: what needs attention first, and how far the bike has gone. */
@Composable
private fun BikeCard(b: BikeInsight?, onOpenGarage: () -> Unit) {
    if (b == null) {
        Text("Sepeda", style = MetricText.Label, color = Palette.TextMuted)
        Spacer(Modifier.height(12.dp))
        Text(
            "Profil diatur sebagai pelari. Pilih jenis sepeda di Pengaturan > Profil untuk melacak perawatan.",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextMuted,
        )
        return
    }
    Text(b.bikeName, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        StatPair("km ride", formatMetric(StatMetric.DISTANCE, b.rideKm).removeSuffix(" km"))
        StatPair("lewat jadwal", b.overdue.toString())
        StatPair("segera servis", b.dueSoon.toString())
    }
    Spacer(Modifier.height(12.dp))
    if (b.tracked == 0) {
        Text(
            "Belum ada komponen yang dilacak. Catat rantai, ban, dan rem agar Strideway mengingatkan jadwal servis dari jarak tempuh.",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextMuted,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onOpenGarage) { Text("Atur garasi sepeda") }
        return
    }
    Text("Paling aus", style = MaterialTheme.typography.labelLarge, color = Palette.TextMuted)
    b.worst.forEach { item ->
        val st = item.status
        val color = when (st.level) {
            GearLevel.OK -> Palette.Success
            GearLevel.DUE_SOON -> Palette.Warning
            GearLevel.OVERDUE -> Palette.Error
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(item.entity.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(120.dp), maxLines = 1)
            Box(Modifier.weight(1f).height(8.dp).background(Palette.SurfaceHigh, MaterialTheme.shapes.extraSmall)) {
                Box(Modifier.fillMaxWidth(st.fraction.toFloat().coerceIn(0f, 1f)).height(8.dp).background(color, MaterialTheme.shapes.extraSmall))
            }
            Text(
                if (st.level == GearLevel.OVERDUE) "servis" else "${Format.integer(st.remainingKm)} km",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.End,
                modifier = Modifier.width(64.dp),
            )
        }
    }
    b.next?.let { n ->
        Text(
            if (n.status.level == GearLevel.OVERDUE) "${n.entity.label} sudah lewat jadwal servis." else "Servis berikutnya: ${n.entity.label} dalam ${Format.integer(n.status.remainingKm)} km.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextMuted,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
    TextButton(onClick = onOpenGarage) { Text("Buka garasi") }
}
