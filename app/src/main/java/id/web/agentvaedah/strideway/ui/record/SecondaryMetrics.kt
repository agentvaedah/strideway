package id.web.agentvaedah.strideway.ui.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.web.agentvaedah.strideway.core.format.Format
import id.web.agentvaedah.strideway.core.model.Sport
import id.web.agentvaedah.strideway.data.ble.SensorStatus
import id.web.agentvaedah.strideway.service.RecordingState
import id.web.agentvaedah.strideway.ui.theme.MetricText
import id.web.agentvaedah.strideway.ui.theme.Palette

/** Everything beyond the headline numbers and the grid, shown below them in the maximised view. */
@Composable
fun SecondaryMetrics(state: RecordingState.Active) {
    val m = state.metrics
    Column {
        val watts = m.powerWatts ?: m.estimatedPowerWatts
        SecondaryRow("Cadence", m.cadence?.toString() ?: "--", "rpm")
        SecondaryRow("Kalori", Format.integer(m.calories), "kkal")
        if (state.sport == Sport.RIDE) {
            SecondaryRow(if (m.powerWatts != null) "Power" else "Power estimasi", watts?.toString() ?: "--", "W")
        }
        SecondaryRow("Waktu total", Format.duration(m.elapsedMs), "")
        state.temperatureC?.let { SecondaryRow("Suhu sensor", Format.oneDecimal(it), "°C") }
        state.pressureHpa?.let { SecondaryRow("Tekanan udara", Format.integer(it), "hPa") }
        state.humidityPct?.let { SecondaryRow("Kelembapan", Format.integer(it), "%") }
        SensorRow(state.sensors)
        Spacer(Modifier.height(16.dp))
    }
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
