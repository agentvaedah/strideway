package id.web.agentvaedah.strideway.ui.record

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.web.agentvaedah.strideway.core.format.Format
import id.web.agentvaedah.strideway.core.model.Athlete
import id.web.agentvaedah.strideway.core.model.Sport
import id.web.agentvaedah.strideway.core.physio.Zones
import id.web.agentvaedah.strideway.core.route.NavState
import id.web.agentvaedah.strideway.core.telemetry.LiveMetrics
import id.web.agentvaedah.strideway.core.training.WorkoutState
import id.web.agentvaedah.strideway.service.GpsFix
import id.web.agentvaedah.strideway.service.RecordingState
import id.web.agentvaedah.strideway.ui.components.BentoCell
import id.web.agentvaedah.strideway.ui.components.BentoGrid
import id.web.agentvaedah.strideway.ui.components.BentoStat
import id.web.agentvaedah.strideway.ui.theme.Brand
import id.web.agentvaedah.strideway.ui.theme.MetricText
import id.web.agentvaedah.strideway.ui.theme.Palette
import java.util.Locale
import kotlin.math.abs

/** Always h:mm:ss with two-digit fields, so the big timer never changes width mid-ride. */
fun clock(ms: Long): String {
    val s = (ms.coerceAtLeast(0) / 1000)
    return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
}

private fun decimalId(v: Double, digits: Int) = String.format(Locale.US, "%.${digits}f", v).replace('.', ',')

private val Giant = MetricText.Huge.copy(fontSize = 104.sp, lineHeight = 104.sp)
private val Hero = MetricText.Huge.copy(fontSize = 52.sp, lineHeight = 54.sp)

/** Current speed only means something with a fix; without one the figure reads "--", never a stale 0. */
private fun liveSpeed(state: RecordingState.Active): String? =
    if (state.gpsFix == GpsFix.OK || state.gpsFix == GpsFix.DEAD_RECKONING) decimalId(state.metrics.speedKmh, 1) else null

private fun livePace(state: RecordingState.Active): String? =
    if (state.gpsFix == GpsFix.OK) Format.pace(state.metrics.speedKmh / 3.6) else null

/**
 * Maximised view: what is the speed right now, big and centred, then where it sits between zero, the
 * average and the maximum, the last minute as a sparkline, and the rest of the ride in a two-column grid.
 */
@Composable
fun ExpandedMetrics(
    state: RecordingState.Active,
    athlete: Athlete,
    onMinimize: () -> Unit,
    modifier: Modifier = Modifier,
    nav: NavState? = null,
    workout: WorkoutState? = null,
    waitingRouteM: Double? = null,
) {
    val m = state.metrics
    val run = state.sport == Sport.RUN
    Column(modifier.background(Palette.Bg).verticalScroll(rememberScrollState())) {
        TopStrip(m, athlete, expanded = true, onToggle = onMinimize)
        // Without the map in view, turns still need to be seen.
        if (nav != null) NavBanner(nav, Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        else waitingRouteM?.let { NavWaitingBanner(it, Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) }
        workout?.let { WorkoutStrip(it) }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clearAndSetSemantics {
                contentDescription = if (run) "Pace sekarang ${livePace(state) ?: "belum ada"} per kilometer"
                else "Kecepatan sekarang ${liveSpeed(state) ?: "belum ada"} kilometer per jam"
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(if (run) "Pace sekarang" else "Kecepatan sekarang", style = MaterialTheme.typography.labelLarge, color = Palette.TextMuted)
            Row(verticalAlignment = Alignment.Bottom) {
                Text((if (run) livePace(state) else liveSpeed(state)) ?: "--", style = Giant, color = Palette.Text, maxLines = 1)
                Spacer(Modifier.width(6.dp))
                Text(if (run) "/km" else "km/j", style = MetricText.Medium, color = Palette.TextMuted, modifier = Modifier.padding(bottom = 14.dp))
            }
            if (!run) SpeedGauge(m.speedKmh, m.avgSpeedKmh, m.maxSpeedKmh, Modifier.fillMaxWidth().padding(top = 4.dp))
        }
        SpeedSparkline(state.speedHistory, m.avgSpeedKmh, Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 16.dp))
        Spacer(Modifier.height(10.dp))
        val watts = m.powerWatts ?: m.estimatedPowerWatts
        BentoGrid(
            buildList {
                add(BentoCell { BentoStat("Waktu", clock(m.movingMs), valueStyle = MetricText.Large) })
                add(BentoCell { BentoStat("Jarak", decimalId(m.distanceM / 1000.0, 2), "km", valueStyle = MetricText.Large) })
                if (run) {
                    add(BentoCell { BentoStat("Pace rata-rata", Format.pace(m.avgSpeedKmh / 3.6), "/km", valueStyle = MetricText.Medium) })
                } else {
                    add(BentoCell { BentoStat("Rata-rata", decimalId(m.avgSpeedKmh, 1), "km/j", valueStyle = MetricText.Medium) })
                    add(BentoCell { BentoStat("Maks", decimalId(m.maxSpeedKmh, 1), "km/j", valueStyle = MetricText.Medium) })
                }
                add(BentoCell { HeartStat(m, athlete) })
                if (!run) add(BentoCell { BentoStat(if (m.powerWatts != null) "Power" else "Power estimasi", watts?.toString() ?: "--", "W", valueStyle = MetricText.Medium) })
                add(BentoCell { BentoStat("Naik", Format.integer(m.elevationGainM), "m", valueStyle = MetricText.Medium) })
                add(BentoCell { BentoStat("Gradien", m.gradePercent?.let { decimalId(it, 1) } ?: "--", "%", valueStyle = MetricText.Medium) })
            },
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Spacer(Modifier.height(8.dp))
        SecondaryMetrics(state)
    }
}

/**
 * Under the map: current speed large on the left with its trend, the last minute as a thin sparkline, and
 * one row of time, distance and average. Kept compact so the map keeps most of the screen.
 */
@Composable
fun MinimizedMetrics(
    state: RecordingState.Active,
    athlete: Athlete,
    onMaximize: () -> Unit,
    modifier: Modifier = Modifier,
    workout: WorkoutState? = null,
) {
    val m = state.metrics
    val run = state.sport == Sport.RUN
    Column(modifier.background(Palette.Bg)) {
        TopStrip(m, athlete, expanded = false, onToggle = onMaximize)
        workout?.let { WorkoutStrip(it) }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).clearAndSetSemantics {
                contentDescription = if (run) "Pace sekarang ${livePace(state) ?: "belum ada"}" else "Kecepatan sekarang ${liveSpeed(state) ?: "belum ada"} kilometer per jam"
            },
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f)) {
                Text(if (run) "Pace sekarang" else "Kecepatan", style = MetricText.Label, color = Palette.TextMuted)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text((if (run) livePace(state) else liveSpeed(state)) ?: "--", style = Hero, color = Palette.Text, maxLines = 1)
                    Spacer(Modifier.width(4.dp))
                    Text(if (run) "/km" else "km/j", style = MetricText.Label, color = Palette.TextMuted, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
            state.speedTrendKmh?.takeIf { !run && abs(it) >= 0.5 }?.let { t -> TrendChip(t, Modifier.padding(bottom = 10.dp)) }
        }
        SpeedSparkline(state.speedHistory, m.avgSpeedKmh, Modifier.fillMaxWidth().height(24.dp).padding(horizontal = 16.dp))
        Row(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallCell("Waktu", clock(m.movingMs).removePrefix("00:"), null, Modifier.weight(1f))
            SmallCell("Jarak", decimalId(m.distanceM / 1000.0, 2), "km", Modifier.weight(1f))
            if (run) SmallCell("Pace rata", Format.pace(m.avgSpeedKmh / 3.6), "/km", Modifier.weight(1f))
            else SmallCell("Rata · maks", "${decimalId(m.avgSpeedKmh, 1)} / ${decimalId(m.maxSpeedKmh, 0)}", "km/j", Modifier.weight(1f))
        }
    }
}

/** Arrow and change against ten seconds ago; colour plus the arrow, so it does not rely on colour alone. */
@Composable
private fun TrendChip(deltaKmh: Double, modifier: Modifier = Modifier) {
    val up = deltaKmh > 0
    Text(
        "${if (up) "▲" else "▼"} ${decimalId(abs(deltaKmh), 1)}",
        style = MaterialTheme.typography.labelLarge,
        color = if (up) Palette.Success else Palette.Warning,
        modifier = modifier.background(Palette.SurfaceHigh, MaterialTheme.shapes.small).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** Bar from zero to the ride's maximum, filled to the current speed, with a tick at the average. */
@Composable
private fun SpeedGauge(now: Double, avg: Double, max: Double, modifier: Modifier = Modifier) {
    val track = Palette.SurfaceHigh
    val tick = Palette.Text
    val top = max.coerceAtLeast(1.0)
    val fill by animateFloatAsState((now / top).toFloat().coerceIn(0f, 1f), tween(250), label = "gauge")
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(10.dp)) {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(track, cornerRadius = r)
            drawRoundRect(Brush.horizontalGradient(listOf(Brand.Signal, Color(0xFFFFB000))), size = Size(size.width * fill, size.height), cornerRadius = r)
            val x = (avg / top).toFloat().coerceIn(0f, 1f) * size.width
            drawRect(tick, topLeft = Offset(x - 1.5.dp.toPx(), -3.dp.toPx()), size = Size(3.dp.toPx(), size.height + 6.dp.toPx()))
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("0", style = MetricText.Label, color = Palette.TextMuted)
            Text("rata-rata ${decimalId(avg, 1)}", style = MetricText.Label, color = Palette.TextMuted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Text("maks ${decimalId(max, 1)}", style = MetricText.Label, color = Palette.TextMuted)
        }
    }
}

/** The last minute of current speed; the dashed line is the ride average. Decorative, so hidden from talkback. */
@Composable
private fun SpeedSparkline(samples: List<Double>, avg: Double, modifier: Modifier = Modifier) {
    val accent = Palette.Accent
    val muted = Palette.TextMuted
    Canvas(modifier.clearAndSetSemantics { }) {
        if (samples.size < 2) return@Canvas
        val hi = maxOf(samples.max(), avg, 1.0)
        val lo = minOf(samples.min(), avg).coerceAtLeast(0.0)
        val span = (hi - lo).coerceAtLeast(1.0)
        fun y(v: Double) = (size.height - (v - lo) / span * size.height * 0.9 - size.height * 0.05).toFloat()
        val step = size.width / (samples.size - 1)
        val line = Path()
        samples.forEachIndexed { i, v -> if (i == 0) line.moveTo(0f, y(v)) else line.lineTo(i * step, y(v)) }
        val area = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(accent.copy(alpha = 0.3f), accent.copy(alpha = 0f))))
        drawPath(line, accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawLine(muted, Offset(0f, y(avg)), Offset(size.width, y(avg)), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))
    }
}

@Composable
private fun HeartStat(m: LiveMetrics, athlete: Athlete) {
    val zone = m.heartRate?.let { Zones.zoneOf(it, Zones.heartRate(athlete.hrMax, athlete.hrRest)) } ?: 0
    BentoStat(
        "Detak jantung",
        m.heartRate?.toString() ?: "--",
        if (m.heartRate != null) "bpm · Z$zone" else null,
        valueStyle = MetricText.Medium,
        valueColor = if (m.heartRate != null) Palette.hrZone(zone) else Palette.TextMuted,
    )
}

/** Heart rate on the left, pause state in the middle, size toggle on the right. */
@Composable
private fun TopStrip(m: LiveMetrics, athlete: Athlete, expanded: Boolean, onToggle: () -> Unit) {
    val zone = m.heartRate?.let { Zones.zoneOf(it, Zones.heartRate(athlete.hrMax, athlete.hrRest)) } ?: 0
    val hrColor = if (m.heartRate != null) Palette.hrZone(zone) else Palette.TextMuted
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).clearAndSetSemantics {
                contentDescription = m.heartRate?.let { "Detak jantung $it bpm, zona $zone" } ?: "Sensor detak jantung belum tersambung"
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Favorite, contentDescription = null, tint = if (m.heartRate != null) Palette.Error else Palette.TextMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(m.heartRate?.toString() ?: "--", style = MetricText.Small, color = hrColor)
            Spacer(Modifier.width(4.dp))
            Text("bpm", style = MetricText.Label, color = Palette.TextMuted)
        }
        val status = when {
            m.manualPaused -> "Dijeda"
            m.autoPaused -> "Jeda otomatis"
            else -> "Merekam"
        }
        Text(
            status,
            style = MaterialTheme.typography.labelLarge,
            color = if (m.paused) Palette.OnAccent else Palette.Success,
            modifier = Modifier
                .background(if (m.paused) Palette.Warning else Color.Transparent, MaterialTheme.shapes.small)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            IconButton(onClick = onToggle) {
                Icon(
                    if (expanded) Icons.Outlined.CloseFullscreen else Icons.Outlined.OpenInFull,
                    contentDescription = if (expanded) "Perkecil, tampilkan peta" else "Perbesar metrik",
                    tint = Palette.Text,
                )
            }
        }
    }
}

@Composable
private fun SmallCell(label: String, value: String, unit: String?, modifier: Modifier) {
    Column(
        modifier
            .fillMaxHeight()
            .background(Palette.Surface, MaterialTheme.shapes.medium)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .clearAndSetSemantics { contentDescription = "$label $value ${unit.orEmpty()}" },
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, style = MetricText.Label, color = Palette.TextMuted, maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MetricText.Small, color = Palette.Text, maxLines = 1)
            if (unit != null) {
                Spacer(Modifier.width(3.dp))
                Text(unit, style = MetricText.Label, color = Palette.TextMuted, maxLines = 1)
            }
        }
    }
}
