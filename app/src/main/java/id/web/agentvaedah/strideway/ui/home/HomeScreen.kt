package id.web.agentvaedah.strideway.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.web.agentvaedah.strideway.core.challenge.ChallengeProgress
import id.web.agentvaedah.strideway.core.format.Format
import id.web.agentvaedah.strideway.core.model.Sport
import id.web.agentvaedah.strideway.core.physio.TrainingLoad
import id.web.agentvaedah.strideway.ui.components.EmptyState
import id.web.agentvaedah.strideway.ui.components.ErrorState
import id.web.agentvaedah.strideway.ui.components.LineChart
import id.web.agentvaedah.strideway.ui.components.LoadingState
import id.web.agentvaedah.strideway.ui.components.SectionTitle
import id.web.agentvaedah.strideway.ui.components.Series
import id.web.agentvaedah.strideway.ui.components.StatPair
import id.web.agentvaedah.strideway.ui.components.TrackThumbnail
import id.web.agentvaedah.strideway.ui.theme.MetricText
import id.web.agentvaedah.strideway.ui.theme.Palette
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val idLocale = Locale.forLanguageTag("id-ID")
private val dateFmt = DateTimeFormatter.ofPattern("EEE, d MMM yyyy · HH:mm", idLocale)
private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", idLocale)
private val weekFmt = DateTimeFormatter.ofPattern("d MMM", idLocale)

@Composable
fun HomeScreen(
    onOpenActivity: (Long) -> Unit,
    onStartRecording: () -> Unit,
    onImport: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    when (val s = state) {
        HomeState.Loading -> LoadingState("Memuat aktivitas", Modifier.fillMaxSize())
        is HomeState.Error -> ErrorState(s.message, Modifier.fillMaxSize())
        is HomeState.Ready -> LazyColumn(Modifier.fillMaxSize()) {
            item { Text("Strideway", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(16.dp)) }
            item { FormCard(s.fitness, s.anyEstimatedLoad, hasActivities = s.feed.isNotEmpty()) }
            item { SectionTitle("Tantangan bulan ini") }
            items(s.challenges, key = { it.id.name }) { ChallengeRow(it) }
            item { CalendarSection(s.calendar, vm::previousMonth, vm::nextMonth) }
            item { SectionTitle("Aktivitas") }
            if (s.feed.isEmpty()) {
                item {
                    EmptyState(
                        title = "Belum ada aktivitas",
                        body = "Rekam ride atau run pertamamu, atau impor file .gpx, .tcx, .fit dari event Audax lewat tab Peta.",
                        actionLabel = "Mulai rekam",
                        onAction = onStartRecording,
                        secondaryLabel = "Impor file",
                        onSecondary = onImport,
                    )
                }
            }
            items(s.feed, key = { it.activity.id }) { FeedRow(it) { onOpenActivity(it.activity.id) } }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun FormCard(points: List<TrainingLoad.FitnessPoint>, estimated: Boolean, hasActivities: Boolean) {
    val today = points.lastOrNull()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(Palette.Surface, MaterialTheme.shapes.large)
            .padding(16.dp),
    ) {
        Text("Form hari ini", style = MetricText.Label, color = Palette.TextMuted)
        val form = today?.form?.roundToInt() ?: 0
        Row(verticalAlignment = Alignment.Bottom) {
            Text((if (form > 0) "+" else "") + form, style = MetricText.Huge, color = Palette.Text)
            Spacer(Modifier.width(12.dp))
            Text(formAdvice(today), style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted, modifier = Modifier.padding(bottom = 10.dp))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            LegendValue("Fitness", today?.fitness?.roundToInt() ?: 0, Palette.Info)
            LegendValue("Fatigue", today?.fatigue?.roundToInt() ?: 0, Palette.Accent)
        }
        Spacer(Modifier.height(12.dp))
        if (hasActivities) {
            LineChart(
                series = listOf(
                    Series(points.map { it.fitness.toFloat() }, Palette.Info),
                    Series(points.map { it.fatigue.toFloat() }, Palette.Accent),
                    Series(points.map { it.form.toFloat() }, Palette.Text, dashed = true),
                ),
                description = "Fitness, fatigue, dan form 42 hari terakhir",
                height = 110.dp,
                zeroLine = true,
            )
            Text("42 hari terakhir. Garis putus-putus: form.", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
        } else {
            Text("Grafik muncul setelah aktivitas pertama tersimpan.", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
        }
        if (estimated) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Sebagian aktivitas tanpa sensor detak jantung; bebannya diperkirakan dari durasi.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextMuted,
            )
        }
    }
}

private fun formAdvice(p: TrainingLoad.FitnessPoint?): String {
    val form = p?.form ?: return "Belum ada data latihan"
    return when {
        form > 15 -> "Segar. Cocok untuk event atau tes"
        form > 5 -> "Siap untuk sesi berat"
        form > -10 -> "Seimbang, beban terjaga"
        form > -30 -> "Sedang membangun. Jaga tidur dan makan"
        else -> "Beban tinggi. Pertimbangkan hari pemulihan"
    }
}

@Composable
private fun LegendValue(label: String, value: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text("$label $value", style = MaterialTheme.typography.labelLarge, color = Palette.Text)
    }
}

@Composable
private fun ChallengeRow(p: ChallengeProgress) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.id.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (p.completed) {
                Text(
                    "Lencana diraih",
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.OnAccent,
                    modifier = Modifier
                        .background(Palette.Accent, MaterialTheme.shapes.extraSmall)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            } else {
                Text(
                    "${Format.oneDecimal(p.currentKm)} / ${p.targetKm.toInt()} km",
                    style = MaterialTheme.typography.labelLarge,
                    color = Palette.TextMuted,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { p.fraction.toFloat() },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = if (p.completed) Palette.Accent else Palette.Info,
            trackColor = Palette.SurfaceHigh,
            drawStopIndicator = {},
        )
        Text(p.id.rule, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
    }
}

@Composable
private fun CalendarSection(cal: CalendarData, onPrev: () -> Unit, onNext: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Bulan sebelumnya") }
            Text(
                cal.month.format(monthFmt).replaceFirstChar { it.titlecase(idLocale) },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Bulan berikutnya") }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min").forEach {
                Text(it, style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        val first = cal.month.atDay(1)
        val lead = first.dayOfWeek.value - DayOfWeek.MONDAY.value
        val days = cal.month.lengthOfMonth()
        val cells = lead + days
        val rows = (cells + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                for (c in 0 until 7) {
                    val day = r * 7 + c - lead + 1
                    Box(Modifier.weight(1f).aspectRatio(1.4f), contentAlignment = Alignment.Center) {
                        if (day in 1..days) {
                            val active = day in cal.activeDays
                            Box(
                                Modifier
                                    .size(30.dp)
                                    .background(if (active) Palette.Accent else Palette.Bg, CircleShape)
                                    .semantics { contentDescription = if (active) "Tanggal $day, ada latihan" else "Tanggal $day" },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "$day",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (active) Palette.OnAccent else Palette.TextMuted,
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Jam latihan per minggu", style = MaterialTheme.typography.labelLarge, color = Palette.TextMuted, modifier = Modifier.padding(horizontal = 16.dp))
        val maxHours = cal.weeklyHours.maxOfOrNull { it.second }?.coerceAtLeast(1.0) ?: 1.0
        cal.weeklyHours.forEach { (start, hours) ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(start.format(weekFmt), style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, modifier = Modifier.width(56.dp))
                Box(Modifier.weight(1f).height(8.dp).background(Palette.SurfaceHigh, MaterialTheme.shapes.extraSmall)) {
                    Box(
                        Modifier
                            .fillMaxWidth((hours / maxHours).toFloat().coerceIn(0f, 1f))
                            .height(8.dp)
                            .background(Palette.Info, MaterialTheme.shapes.extraSmall),
                    )
                }
                Text(Format.duration((hours * 3_600_000).toLong()), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(64.dp), textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
private fun FeedRow(item: FeedItem, onClick: () -> Unit) {
    val a = item.activity
    val date = Instant.ofEpochMilli(a.startMs).atZone(ZoneId.systemDefault()).format(dateFmt)
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(a.name, style = MaterialTheme.typography.titleMedium)
        Text(
            "${if (a.sport == Sport.RIDE) "Ride" else "Run"} · $date",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextMuted,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            StatPair("km", Format.km(a.distanceM))
            StatPair("waktu", Format.duration(a.movingMs))
            StatPair("naik m", Format.integer(a.elevationGainM))
            StatPair("kkal", Format.integer(a.calories))
        }
        Spacer(Modifier.height(8.dp))
        TrackThumbnail(item.path, Modifier.fillMaxWidth().height(120.dp))
    }
    HorizontalDivider(color = Palette.Outline)
}
