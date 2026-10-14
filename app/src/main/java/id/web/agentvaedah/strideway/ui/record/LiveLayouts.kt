package id.web.agentvaedah.strideway.ui.record

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
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
import id.web.agentvaedah.strideway.service.RecordingState
import id.web.agentvaedah.strideway.ui.theme.MetricText
import id.web.agentvaedah.strideway.ui.theme.Palette
import java.util.Locale

/** Always h:mm:ss with two-digit fields, so the big timer never changes width mid-ride. */
fun clock(ms: Long): String {
    val s = (ms.coerceAtLeast(0) / 1000)
    return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
}

private fun decimalId(v: Double, digits: Int) = String.format(Locale.US, "%.${digits}f", v).replace('.', ',')

private val Giant = MetricText.Huge.copy(fontSize = 72.sp, lineHeight = 76.sp)

/**
 * Maximised view, Strava style: the timer and the numbers that matter most, as big as the screen allows.
 * Order: timer, average speed (pace for runs), distance | climb, current elevation | heart rate.
 */
@Composable
fun ExpandedMetrics(
    state: RecordingState.Active,
    athlete: Athlete,
    onMinimize: () -> Unit,
    modifier: Modifier = Modifier,
    nav: NavState? = null,
    workout: WorkoutState? = null,
) {
    val m = state.metrics
    Column(modifier.background(Palette.Bg).verticalScroll(rememberScrollState())) {
        TopStrip(m, athlete, expanded = true, onToggle = onMinimize)
        // Without the map in view, turns still need to be seen.
        nav?.let { NavBanner(it, Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) }
        workout?.let { WorkoutStrip(it) }
        BigCell("Waktu", clock(m.movingMs), null, Giant)
        HorizontalDivider(color = Palette.Outline)
        if (state.sport == Sport.RUN) {
            BigCell("Pace rata-rata", Format.pace(m.avgSpeedKmh / 3.6), "/km", Giant)
        } else {
            BigCell("Kecepatan rata-rata", decimalId(m.avgSpeedKmh, 1), "km/j", Giant)
        }
        HorizontalDivider(color = Palette.Outline)
        Row(Modifier.fillMaxWidth()) {
            BigCell("Jarak", decimalId(m.distanceM / 1000.0, 2), "km", MetricText.Huge, Modifier.weight(1f))
            BigCell("Total naik", Format.integer(m.elevationGainM), "m", MetricText.Huge, Modifier.weight(1f))
        }
        HorizontalDivider(color = Palette.Outline)
        Row(Modifier.fillMaxWidth()) {
            BigCell("Elevasi sekarang", m.altitudeM?.let { Format.integer(it) } ?: "--", "m", MetricText.Huge, Modifier.weight(1f))
            HeartCell(m, athlete, MetricText.Huge, Modifier.weight(1f))
        }
        HorizontalDivider(color = Palette.Outline)
        SecondaryMetrics(state)
    }
}

/** Bottom quarter under the map: status row, then two rows of three compact metrics. */
@Composable
fun MinimizedMetrics(
    state: RecordingState.Active,
    athlete: Athlete,
    onMaximize: () -> Unit,
    modifier: Modifier = Modifier,
    workout: WorkoutState? = null,
) {
    val m = state.metrics
    Column(modifier.background(Palette.Bg)) {
        TopStrip(m, athlete, expanded = false, onToggle = onMaximize)
        workout?.let { WorkoutStrip(it) }
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
            SmallCell("Waktu", clock(m.movingMs).removePrefix("00:"), null, Modifier.weight(1f))
            if (state.sport == Sport.RUN) {
                SmallCell("Pace rata-rata", Format.pace(m.avgSpeedKmh / 3.6), "/km", Modifier.weight(1f))
            } else {
                SmallCell("Rata-rata", decimalId(m.avgSpeedKmh, 1), "km/j", Modifier.weight(1f))
            }
            SmallCell("Jarak", decimalId(m.distanceM / 1000.0, 2), "km", Modifier.weight(1f))
        }
        HorizontalDivider(color = Palette.Outline)
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
            SmallCell("Kecepatan maks", decimalId(m.maxSpeedKmh, 1), "km/j", Modifier.weight(1f))
            SmallCell("Cadence", m.cadence?.toString() ?: "--", "rpm", Modifier.weight(1f))
            SmallCell("Gradien", m.gradePercent?.let { decimalId(it, 1) } ?: "--", "%", Modifier.weight(1f))
        }
    }
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
private fun BigCell(label: String, value: String, unit: String?, style: TextStyle, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 10.dp).clearAndSetSemantics { contentDescription = "$label $value ${unit.orEmpty()}" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = style, color = Palette.Text, maxLines = 1, textAlign = TextAlign.Center)
        Text(if (unit != null) "$label ($unit)" else label, style = MaterialTheme.typography.labelLarge, color = Palette.TextMuted)
    }
}

@Composable
private fun HeartCell(m: LiveMetrics, athlete: Athlete, style: TextStyle, modifier: Modifier) {
    val zone = m.heartRate?.let { Zones.zoneOf(it, Zones.heartRate(athlete.hrMax, athlete.hrRest)) } ?: 0
    Column(
        modifier.fillMaxWidth().padding(vertical = 10.dp).clearAndSetSemantics {
            contentDescription = m.heartRate?.let { "Detak jantung $it bpm, zona $zone" } ?: "Detak jantung tidak tersedia"
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Favorite, contentDescription = null, tint = if (m.heartRate != null) Palette.Error else Palette.TextMuted, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(6.dp))
            Text(m.heartRate?.toString() ?: "--", style = style, color = if (m.heartRate != null) Palette.hrZone(zone) else Palette.TextMuted)
        }
        Text(if (m.heartRate != null) "bpm · zona $zone" else "Sensor HR", style = MaterialTheme.typography.labelLarge, color = Palette.TextMuted)
    }
}

@Composable
private fun SmallCell(label: String, value: String, unit: String?, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().clearAndSetSemantics { contentDescription = "$label $value ${unit.orEmpty()}" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(value, style = MetricText.Medium, color = Palette.Text, maxLines = 1)
        Text(if (unit != null) "$label ($unit)" else label, style = MetricText.Label, color = Palette.TextMuted, maxLines = 1)
    }
}
