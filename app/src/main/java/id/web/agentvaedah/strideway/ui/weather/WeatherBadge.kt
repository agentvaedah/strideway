package id.web.agentvaedah.strideway.ui.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import id.web.agentvaedah.strideway.core.weather.WeatherCodes
import id.web.agentvaedah.strideway.data.remote.weather.WeatherState
import id.web.agentvaedah.strideway.ui.theme.Palette
import kotlin.math.roundToInt

/** Small pill on a map: weather icon, temperature and wind. Tapping opens the full weather screen. */
@Composable
fun WeatherBadge(state: WeatherState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = (state as? WeatherState.Ready)?.report?.current
    val label = c?.let {
        "${it.kind.label}, ${it.tempC.roundToInt()} derajat, angin ${it.windKmh.roundToInt()} km per jam ${WeatherCodes.windFrom(it.windDirDeg)}. Ketuk untuk detail cuaca"
    } ?: "Cuaca belum dimuat. Ketuk untuk membuka cuaca"
    Row(
        modifier
            .background(Palette.Bg.copy(alpha = 0.88f), MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (c == null) {
            Text("Cuaca", style = MaterialTheme.typography.labelLarge, color = Palette.TextMuted)
            return@Row
        }
        Icon(c.kind.icon(c.isDay), contentDescription = null, tint = Palette.Accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text("${c.tempC.roundToInt()}°", style = MaterialTheme.typography.labelLarge, color = Palette.Text)
        Spacer(Modifier.width(8.dp))
        Text("${c.windKmh.roundToInt()} km/j", style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted)
    }
}
