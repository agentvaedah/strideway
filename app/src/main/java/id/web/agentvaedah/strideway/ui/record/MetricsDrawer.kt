package id.web.agentvaedah.strideway.ui.record

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import id.web.agentvaedah.strideway.core.format.Format
import id.web.agentvaedah.strideway.core.model.Athlete
import id.web.agentvaedah.strideway.core.model.Sport
import id.web.agentvaedah.strideway.core.physio.Zones
import id.web.agentvaedah.strideway.data.ble.SensorStatus
import id.web.agentvaedah.strideway.service.RecordingState
import id.web.agentvaedah.strideway.ui.components.MetricTile
import id.web.agentvaedah.strideway.ui.theme.MetricText
import id.web.agentvaedah.strideway.ui.theme.Palette

/**
 * Bottom half of the head unit. Collapsed shows the four PRD core metrics in a 2x2 grid;
 * a swipe up (or the handle) reveals the secondary list.
 */
@Composable
fun MetricsDrawer(state: RecordingState.Active, athlete: Athlete, fullHeight: Boolean, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val m = state.metrics
    val hrZones = Zones.heartRate(athlete.hrMax, athlete.hrRest)
    val zone = m.heartRate?.let { Zones.zoneOf(it, hrZones) } ?: 0

    Column(
        modifier
            .background(Palette.Bg)
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (total < -60f) expanded = true
                        if (total > 60f) expanded = false
                    },
                ) { _, dy -> total += dy }
            },
    ) {
        Handle(expanded) { expanded = !expanded }
        if (m.paused) PausedBanner(m.autoPaused)

        val coreStyle = if (fullHeight) MetricText.Huge else MetricText.Large
        Row(Modifier.fillMaxWidth()) {
            MetricTile(
                label = "Waktu bergerak",
                value = Format.duration(m.movingMs),
                unit = null,
                valueStyle = coreStyle,
                caption = "Total ${Format.duration(m.elapsedMs)}",
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                label = "Jarak",
                value = Format.km(m.distanceM),
                unit = "km",
                valueStyle = coreStyle,
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider(color = Palette.Outline)
        Row(Modifier.fillMaxWidth()) {
            if (state.sport == Sport.RUN) {
                MetricTile(
                    label = "Pace rata-rata",
                    value = Format.pace(m.avgSpeedKmh / 3.6),
                    unit = "/km",
                    valueStyle = coreStyle,
                    caption = "Sekarang ${Format.pace(m.speedKmh / 3.6)}",
                    modifier = Modifier.weight(1f),
                )
            } else {
                MetricTile(
                    label = "Kecepatan rata-rata",
                    value = Format.oneDecimal(m.avgSpeedKmh),
                    unit = "km/j",
                    valueStyle = coreStyle,
                    caption = "Sekarang ${Format.oneDecimal(m.speedKmh)}",
                    modifier = Modifier.weight(1f),
                )
            }
            MetricTile(
                label = "Detak jantung",
                value = m.heartRate?.toString() ?: "--",
                unit = "bpm",
                valueStyle = coreStyle,
                valueColor = if (m.heartRate != null) Palette.hrZone(zone) else Palette.TextMuted,
                caption = if (m.heartRate != null) "Zona $zone" else "Sensor belum tersambung",
                captionColor = if (m.heartRate != null) Palette.hrZone(zone) else Palette.TextMuted,
                modifier = Modifier.weight(1f),
            )
        }

        AnimatedVisibility(expanded || fullHeight) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                HorizontalDivider(color = Palette.Outline)
                val watts = m.powerWatts ?: m.estimatedPowerWatts
                SecondaryRow("Cadence", m.cadence?.toString() ?: "--", "rpm")
                SecondaryRow("Elevasi", m.altitudeM?.let { Format.integer(it) } ?: "--", "m")
                SecondaryRow("Total naik", Format.integer(m.elevationGainM), "m")
                SecondaryRow("Gradien", m.gradePercent?.let { Format.oneDecimal(it) } ?: "--", "%")
                SecondaryRow("Kalori", Format.integer(m.calories), "kkal")
                if (state.sport == Sport.RIDE) {
                    SecondaryRow(
                        if (m.powerWatts != null) "Power" else "Power estimasi",
                        watts?.toString() ?: "--",
                        "W",
                    )
                }
                SecondaryRow("Kecepatan maks", Format.oneDecimal(m.maxSpeedKmh), "km/j")
                SensorRow(state.sensors)
            }
        }
    }
}

@Composable
private fun Handle(expanded: Boolean, onToggle: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        TextButton(onClick = onToggle) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(width = 36.dp, height = 4.dp).background(Palette.Outline, MaterialTheme.shapes.extraSmall))
                Spacer(Modifier.height(2.dp))
                Text(
                    if (expanded) "Sembunyikan metrik lain" else "Geser ke atas untuk metrik lain",
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.TextMuted,
                )
            }
        }
    }
}

@Composable
private fun PausedBanner(auto: Boolean) {
    Text(
        if (auto) "Jeda otomatis: kecepatan di bawah 2,5 km/j" else "Dijeda. Tekan Volume Atas untuk lanjut",
        style = MaterialTheme.typography.labelLarge,
        color = Palette.OnAccent,
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.Warning)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
private fun SecondaryRow(label: String, value: String, unit: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Palette.TextMuted, modifier = Modifier.weight(1f))
        Text(value, style = MetricText.Medium, color = Palette.Text)
        Spacer(Modifier.size(6.dp))
        Text(unit, style = MetricText.Label, color = Palette.TextMuted)
    }
}

@Composable
private fun SensorRow(sensors: List<SensorStatus>) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Sensor BLE", style = MaterialTheme.typography.bodyLarge, color = Palette.TextMuted)
        if (sensors.isEmpty()) {
            Text("Belum ada sensor. Tambahkan di You > Perangkat.", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
        }
        sensors.forEach { s ->
            val (text, color) = when {
                s.connected -> "Tersambung" to Palette.Success
                s.connecting -> "Menyambung" to Palette.Warning
                else -> "Terputus, mencoba lagi" to Palette.Error
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                s.batteryPct?.let { Text("$it%  ", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted) }
                Text(text, style = MaterialTheme.typography.bodySmall, color = color)
            }
        }
    }
}
