package id.web.agentvaedah.strideway.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.web.agentvaedah.strideway.core.challenge.Award
import id.web.agentvaedah.strideway.core.format.Format
import id.web.agentvaedah.strideway.core.physio.TrainingLoad
import id.web.agentvaedah.strideway.ui.components.ActivityRow
import id.web.agentvaedah.strideway.ui.components.BentoCell
import id.web.agentvaedah.strideway.ui.components.BentoGrid
import id.web.agentvaedah.strideway.ui.components.EmptyState
import id.web.agentvaedah.strideway.ui.components.ErrorState
import id.web.agentvaedah.strideway.ui.components.LineChart
import id.web.agentvaedah.strideway.ui.components.LoadingState
import id.web.agentvaedah.strideway.ui.components.SectionTitle
import id.web.agentvaedah.strideway.ui.components.Series
import id.web.agentvaedah.strideway.ui.theme.MetricText
import id.web.agentvaedah.strideway.ui.theme.Palette
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val idLocale = Locale.forLanguageTag("id-ID")
private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", idLocale)

@Composable
fun HomeScreen(
    onOpenActivity: (Long) -> Unit,
    onStartRecording: () -> Unit,
    onImport: () -> Unit,
    onOpenGarage: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    (state as? HomeState.Ready)?.newAwards?.takeIf { it.isNotEmpty() }?.let { awards ->
        AwardDialog(awards, onDismiss = { vm.acknowledgeAwards(awards) })
    }
    when (val s = state) {
        HomeState.Loading -> LoadingState("Memuat aktivitas", Modifier.fillMaxSize())
        is HomeState.Error -> ErrorState(s.message, Modifier.fillMaxSize())
        is HomeState.Ready -> LazyColumn(Modifier.fillMaxSize()) {
            item { Text("Strideway", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(16.dp)) }
            item { FormCard(s.fitness, s.anyEstimatedLoad, hasActivities = s.feed.isNotEmpty()) }
            item { InsightsCarousel(s, vm, onOpenGarage) }
            item { SectionTitle("Tantangan bulan ini") }
            items(s.challenges, key = { it.progress.id.name }) { ChallengeRow(it, onJoin = { j -> vm.setJoined(it.progress.id, j) }) }
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
            items(s.feed, key = { it.activity.id }) { ActivityRow(it.activity, it.path, onClick = { onOpenActivity(it.activity.id) }) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun FormCard(points: List<TrainingLoad.FitnessPoint>, estimated: Boolean, hasActivities: Boolean) {
    val today = points.lastOrNull()
    val form = today?.form?.roundToInt() ?: 0
    // Form decides today's session, so it alone spans the grid; fitness and fatigue explain it below.
    BentoGrid(
        listOf(
            BentoCell(span = 2) {
                Text("Form hari ini", style = MetricText.Label, color = Palette.TextMuted)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text((if (form > 0) "+" else "") + form, style = MetricText.Huge, color = Palette.Text)
                    Spacer(Modifier.width(12.dp))
                    Text(formAdvice(today), style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted, modifier = Modifier.padding(bottom = 10.dp))
                }
                Spacer(Modifier.height(8.dp))
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
            },
            BentoCell {
                LegendValue("Fitness", today?.fitness?.roundToInt() ?: 0, Palette.Info)
                Text("Kebugaran jangka panjang (42 hari)", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
            },
            BentoCell {
                LegendValue("Fatigue", today?.fatigue?.roundToInt() ?: 0, Palette.Accent)
                Text("Kelelahan 7 hari terakhir", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
            },
        ),
        modifier = Modifier.padding(horizontal = 16.dp),
    )
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

/**
 * Challenges are opt-in: an unjoined one shows its rule and a Join button, a joined one shows progress,
 * and a completed one shows the award it earned.
 */
@Composable
private fun ChallengeRow(c: ChallengeCard, onJoin: (Boolean) -> Unit) {
    val p = c.progress
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.id.title, style = MaterialTheme.typography.titleSmall)
                Text(p.id.rule, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
            }
            when {
                c.earned || (c.joined && p.completed) -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = Palette.Accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Tercapai", style = MaterialTheme.typography.labelLarge, color = Palette.Accent)
                }
                c.joined -> TextButton(onClick = { onJoin(false) }) { Text("Keluar", color = Palette.TextMuted) }
                else -> OutlinedButton(onClick = { onJoin(true) }) { Text("Ikuti") }
            }
        }
        if (c.joined) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { p.fraction.toFloat() },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = Palette.Accent,
                trackColor = Palette.SurfaceHigh,
                drawStopIndicator = {},
            )
            Text(
                "${Format.oneDecimal(p.currentKm)} / ${p.targetKm.toInt()} km",
                style = MaterialTheme.typography.labelMedium,
                color = Palette.TextMuted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun AwardDialog(awards: List<Award>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = Palette.Accent, modifier = Modifier.size(40.dp)) },
        title = { Text(if (awards.size == 1) "Tantangan tercapai" else "${awards.size} tantangan tercapai") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                awards.forEach { a ->
                    val month = a.month.format(monthFmt).replaceFirstChar { it.titlecase(idLocale) }
                    Text("${a.id.title} · $month", style = MaterialTheme.typography.bodyLarge)
                }
                Text(
                    "Penghargaan tersimpan di You > Progress.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextMuted,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Mantap") } },
    )
}

@Composable
internal fun CalendarSection(cal: CalendarData, onPrev: () -> Unit, onNext: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Bulan sebelumnya") }
            Text(
                cal.month.format(monthFmt).replaceFirstChar { it.titlecase(idLocale) },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Bulan berikutnya") }
        }
        Row(Modifier.fillMaxWidth()) {
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
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val day = r * 7 + c - lead + 1
                    Box(Modifier.weight(1f).aspectRatio(1.4f), contentAlignment = Alignment.Center) {
                        if (day in 1..days) {
                            val active = day in cal.activeDays
                            Box(
                                Modifier
                                    .size(30.dp)
                                    .background(if (active) Palette.Accent else Palette.Surface, CircleShape)
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
    }
}
