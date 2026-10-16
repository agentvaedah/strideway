package id.web.agentvaedah.strideway.ui.record

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TurnLeft
import androidx.compose.material.icons.outlined.TurnRight
import androidx.compose.material.icons.outlined.TurnSlightLeft
import androidx.compose.material.icons.outlined.TurnSlightRight
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.RoundaboutRight
import androidx.compose.material.icons.outlined.UTurnLeft
import androidx.compose.material.icons.outlined.WrongLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp
import id.web.agentvaedah.strideway.core.format.Format
import id.web.agentvaedah.strideway.core.route.ManeuverType
import id.web.agentvaedah.strideway.core.route.NavState
import id.web.agentvaedah.strideway.ui.theme.MetricText
import id.web.agentvaedah.strideway.ui.theme.Palette
import kotlin.math.roundToInt

fun ManeuverType.icon(): ImageVector = when (this) {
    ManeuverType.STRAIGHT -> Icons.Outlined.ArrowUpward
    ManeuverType.SLIGHT_LEFT, ManeuverType.KEEP_LEFT -> Icons.Outlined.TurnSlightLeft
    ManeuverType.LEFT, ManeuverType.SHARP_LEFT -> Icons.Outlined.TurnLeft
    ManeuverType.SLIGHT_RIGHT, ManeuverType.KEEP_RIGHT -> Icons.Outlined.TurnSlightRight
    ManeuverType.RIGHT, ManeuverType.SHARP_RIGHT -> Icons.Outlined.TurnRight
    ManeuverType.U_TURN -> Icons.Outlined.UTurnLeft
    ManeuverType.ROUNDABOUT -> Icons.Outlined.RoundaboutRight
}

private fun distance(m: Double): String = if (m >= 1000) "${Format.oneDecimal(m / 1000).replace('.', ',')} km" else "${((m / 10).roundToInt() * 10)} m"

/**
 * Turn-by-turn banner across the top of the ride screen: the next maneuver large (arrow, distance, street),
 * the one after it small, then progress and time to arrival. Off route it turns red and says how far away.
 */
@Composable
fun NavBanner(s: NavState, modifier: Modifier = Modifier) {
    val next = s.next
    val headline = when {
        s.arrived -> "Sampai di tujuan"
        s.offRoute -> "Keluar rute · ${distance(s.distanceToRouteM)}"
        next != null -> "${distance(next.inM)} · ${next.maneuver.type.phrase(next.maneuver.exit).replaceFirstChar { it.uppercase() }}"
        else -> "Ikuti rute"
    }
    val icon = when {
        s.arrived -> Icons.Outlined.Flag
        s.offRoute -> Icons.Outlined.WrongLocation
        else -> next?.maneuver?.type?.icon() ?: Icons.Outlined.ArrowUpward
    }
    val bg = if (s.offRoute) Palette.Error else Palette.Bg.copy(alpha = 0.94f)
    val fg = if (s.offRoute) Palette.OnAccent else Palette.Text
    Column(
        modifier
            .fillMaxWidth()
            .background(bg, MaterialTheme.shapes.medium)
            .padding(12.dp)
            .clearAndSetSemantics {
                contentDescription = headline + (next?.maneuver?.street?.let { ", ke $it" } ?: "") +
                    ", sisa ${distance(s.remainingM)}"
                liveRegion = LiveRegionMode.Polite
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).background(if (s.offRoute) Palette.Bg else Palette.Accent, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = if (s.offRoute) Palette.Error else Palette.OnAccent, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(headline, style = MetricText.Small, color = fg, maxLines = 2)
                next?.maneuver?.street?.takeIf { !s.offRoute }?.let {
                    Text("ke $it", style = MaterialTheme.typography.bodyMedium, color = if (s.offRoute) fg else Palette.TextMuted, maxLines = 1)
                }
                if (s.offRoute) Text("Kembali ke garis putus-putus, atau tunggu rute dihitung ulang", style = MaterialTheme.typography.bodySmall, color = fg)
            }
        }
        if (!s.offRoute && !s.arrived) {
            Spacer(Modifier.height(8.dp))
            val total = s.progressM + s.remainingM
            LinearProgressIndicator(
                progress = { if (total > 0) (s.progressM / total).toFloat() else 0f },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = Palette.Accent,
                trackColor = Palette.SurfaceHigh,
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Sisa ${distance(s.remainingM)}" + (s.etaS?.let { " · tiba ${Format.duration(it * 1000L)}" } ?: ""),
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.TextMuted,
                    modifier = Modifier.weight(1f),
                )
                s.after?.let { a ->
                    Text("lalu", style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted)
                    Spacer(Modifier.width(4.dp))
                    Icon(a.maneuver.type.icon(), contentDescription = "lalu ${a.maneuver.type.phrase(a.maneuver.exit)}", tint = Palette.TextMuted, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** Route is set but the rider is not placed on it yet; says so instead of leaving the top of the map empty. */
@Composable
fun NavWaitingBanner(routeM: Double, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Palette.Bg.copy(alpha = 0.94f), MaterialTheme.shapes.medium)
            .padding(12.dp)
            .clearAndSetSemantics { contentDescription = "Menunggu GPS untuk navigasi, rute ${distance(routeM)}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.ArrowUpward, contentDescription = null, tint = Palette.TextMuted, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text("Menunggu GPS untuk navigasi", style = MaterialTheme.typography.titleSmall, color = Palette.Text)
            Text("Rute ${distance(routeM)} · belokan muncul setelah posisi ditemukan", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
        }
    }
}
