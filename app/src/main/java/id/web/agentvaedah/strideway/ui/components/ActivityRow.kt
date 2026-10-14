package id.web.agentvaedah.strideway.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import id.web.agentvaedah.strideway.core.format.Format
import id.web.agentvaedah.strideway.core.model.LatLon
import id.web.agentvaedah.strideway.core.model.Sport
import id.web.agentvaedah.strideway.data.db.ActivityEntity
import id.web.agentvaedah.strideway.ui.activity.PrBadge
import id.web.agentvaedah.strideway.ui.theme.Palette
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFmt = DateTimeFormatter.ofPattern("EEE, d MMM yyyy · HH:mm", Locale.forLanguageTag("id-ID"))

/**
 * Feed entry used on Home and in You > Aktivitas: name, date, PR badges, four stats and an offline route sketch.
 * The sketch is drawn from stored coordinates, so the feed works without map tiles or network.
 */
@Composable
fun ActivityRow(
    a: ActivityEntity,
    path: List<LatLon>,
    onClick: () -> Unit,
    badges: List<String> = emptyList(),
    mapHeight: Dp = 120.dp,
) {
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
        if (badges.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                badges.take(3).forEach { PrBadge(it) }
                if (badges.size > 3) Text("+${badges.size - 3}", style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            StatPair("km", Format.km(a.distanceM))
            StatPair("waktu", Format.duration(a.movingMs))
            StatPair("naik m", Format.integer(a.elevationGainM))
            StatPair("kkal", Format.integer(a.calories))
        }
        if (!a.hideMap) {
            Spacer(Modifier.height(8.dp))
            TrackThumbnail(path, Modifier.fillMaxWidth().height(mapHeight).clip(MaterialTheme.shapes.medium))
        }
    }
    HorizontalDivider(color = Palette.Outline)
}
