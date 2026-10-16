package id.web.agentvaedah.strideway.ui.maps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.web.agentvaedah.strideway.core.model.LatLon
import id.web.agentvaedah.strideway.core.route.CueSheet
import id.web.agentvaedah.strideway.core.route.Maneuver
import id.web.agentvaedah.strideway.core.route.Navigator
import id.web.agentvaedah.strideway.core.route.RouteFlyover
import id.web.agentvaedah.strideway.data.prefs.MapBase
import id.web.agentvaedah.strideway.ui.components.MapHost
import id.web.agentvaedah.strideway.ui.components.MapLayers
import id.web.agentvaedah.strideway.ui.components.toLatLng
import id.web.agentvaedah.strideway.ui.record.icon
import id.web.agentvaedah.strideway.ui.theme.MetricText
import id.web.agentvaedah.strideway.ui.theme.Palette
import java.util.Locale
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

/**
 * Full-screen fly-along of a planned or saved route: the camera rides the road at 60° tilt, the part already
 * flown over turns into the riding colour, and the next turn shows on a card. Play, pause, scrub and speed
 * up; at the end the camera eases back to the whole route.
 */
@Composable
fun RoutePreview(
    name: String,
    route: List<LatLon>,
    maneuvers: List<Maneuver>,
    base: MapBase,
    onNavigate: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    if (route.size < 2) return
    val fly = remember(route) { RouteFlyover(route) }
    val cues = remember(route, maneuvers) { CueSheet.of(route, maneuvers.ifEmpty { Navigator.fromGeometry(route) }) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var playing by remember { mutableStateOf(true) }
    var speed by remember { mutableFloatStateOf(1f) }
    val trackColor = Palette.Accent
    val routeColor = Palette.Info

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Palette.Bg)) {
            MapHost(
                base = base,
                allowRotateTilt = true,
                modifier = Modifier.fillMaxSize(),
                onMapReady = { map = it },
                onStyleLoaded = { _, st -> style = st },
            )

            LaunchedEffect(style, route) {
                val st = style ?: return@LaunchedEffect
                MapLayers.setLine(st, "preview-route", route, routeColor, 5f, lineStyle = MapLayers.LineStyle.ROUTE)
            }
            LaunchedEffect(style, progress) {
                val st = style ?: return@LaunchedEffect
                val f = fly.at(progress.toDouble())
                MapLayers.setLine(st, "preview-done", fly.prefix(f.distanceM), trackColor, 6f, lineStyle = MapLayers.LineStyle.TRACK)
                MapLayers.setHeadingPuck(st, "preview-puck", f.position, f.bearing.toFloat(), trackColor)
            }

            // The camera follows every frame while playing; moveCamera per frame is smoother than chained animations.
            LaunchedEffect(map, playing, speed) {
                val m = map ?: return@LaunchedEffect
                if (!playing) return@LaunchedEffect
                if (progress >= 1f) progress = 0f
                var last = withFrameNanos { it }
                while (playing && progress < 1f) {
                    val now = withFrameNanos { it }
                    val dt = (now - last) / 1_000_000f
                    last = now
                    progress = (progress + dt * speed / fly.durationMs).coerceAtMost(1f)
                    val f = fly.at(progress.toDouble())
                    m.moveCamera(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder().target(f.position.toLatLng()).zoom(15.8).tilt(60.0).bearing(f.bearing).build(),
                        ),
                    )
                }
                if (progress >= 1f) {
                    playing = false
                    m.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(m.cameraPosition.target).tilt(0.0).bearing(0.0).zoom(m.cameraPosition.zoom).build()), 600)
                    MapLayers.fit(m, route, paddingPx = 120)
                }
            }

            val here = fly.at(progress.toDouble())
            val next = CueSheet.next(cues, here.distanceM)
            Column(Modifier.align(Alignment.TopStart).fillMaxWidth().statusBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).background(Palette.Bg.copy(alpha = 0.92f), MaterialTheme.shapes.medium).padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text(
                            "${km(here.distanceM)} dari ${km(fly.lengthM)} km",
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.TextMuted,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "Tutup pratinjau") }
                }
                next?.let { up ->
                    Row(
                        Modifier
                            .background(Palette.Bg.copy(alpha = 0.94f), MaterialTheme.shapes.medium)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .semantics { contentDescription = "Belokan berikut dalam ${meters(up.inM)}: ${up.maneuver.type.phrase(up.maneuver.exit)}" },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(up.maneuver.type.icon(), contentDescription = null, tint = Palette.Accent, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(meters(up.inM), style = MetricText.Medium, color = Palette.Text)
                            Text(up.maneuver.type.phrase(up.maneuver.exit).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, color = Palette.Text)
                        }
                    }
                }
            }

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Palette.Surface, MaterialTheme.shapes.large)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Slider(
                    value = progress,
                    onValueChange = { v ->
                        playing = false
                        progress = v
                        val f = fly.at(v.toDouble())
                        map?.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(f.position.toLatLng()).zoom(15.8).tilt(60.0).bearing(f.bearing).build()))
                    },
                    modifier = Modifier.semantics { contentDescription = "Posisi pratinjau rute" },
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledIconButton(onClick = { playing = !playing }, modifier = Modifier.size(52.dp)) {
                        Icon(
                            when {
                                playing -> Icons.Outlined.Pause
                                progress >= 1f -> Icons.Outlined.Replay
                                else -> Icons.Outlined.PlayArrow
                            },
                            contentDescription = if (playing) "Jeda" else "Putar",
                        )
                    }
                    listOf(1f, 2f, 4f).forEach { s -> FilterChip(speed == s, { speed = s }, { Text("${s.toInt()}×") }) }
                    Spacer(Modifier.weight(1f))
                    if (onNavigate != null) TextButton(onClick = onNavigate) { Text("Navigasi") }
                }
            }
        }
    }
}

private fun km(m: Double) = String.format(Locale.US, "%.1f", m / 1000).replace('.', ',')
private fun meters(m: Double) = if (m >= 1000) "${km(m)} km" else "${(m / 10).toInt() * 10} m"
