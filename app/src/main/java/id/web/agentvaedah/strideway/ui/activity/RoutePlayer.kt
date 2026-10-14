package id.web.agentvaedah.strideway.ui.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import id.web.agentvaedah.strideway.core.format.Format
import id.web.agentvaedah.strideway.core.model.LatLon
import id.web.agentvaedah.strideway.core.track.FlyoverFrame
import id.web.agentvaedah.strideway.core.track.Playback
import id.web.agentvaedah.strideway.core.track.PlaybackPoint
import id.web.agentvaedah.strideway.core.track.TrackStats
import id.web.agentvaedah.strideway.data.prefs.MapBase
import id.web.agentvaedah.strideway.ui.components.MapHost
import id.web.agentvaedah.strideway.ui.components.MapLayers
import id.web.agentvaedah.strideway.ui.components.StatPair
import id.web.agentvaedah.strideway.ui.components.toLatLng
import id.web.agentvaedah.strideway.ui.theme.Palette
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

private const val FLYOVER_MS = 12_000L
private const val FPS = 30

/**
 * Activity map with Strava-style replay. Tapping the map (or Play) flies the camera along the ride while the
 * line draws itself; the timeline below scrubs to any moment and shows speed, heart rate and elevation then.
 * [progress] is shared with the charts so their cursor follows.
 */
@Composable
fun RoutePlayer(
    path: List<LatLon>,
    playback: List<PlaybackPoint>,
    base: MapBase,
    progress: Float?,
    onProgress: (Float?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var playing by remember { mutableStateOf(false) }
    val lineColor = Palette.Accent
    val ghostColor = Palette.TextMuted
    // Fewer points keep the per-frame GeoJSON update cheap on mid-range phones.
    val pts = remember(playback) { TrackStats.downsample(playback, 1_000) }
    val frames: List<FlyoverFrame> = remember(pts) { Playback.flyover(pts, FLYOVER_MS, FPS) }
    val t0 = pts.firstOrNull()?.timeMs ?: 0L
    val span = (pts.lastOrNull()?.timeMs ?: 0L) - t0

    Column(modifier) {
        Box(Modifier.fillMaxWidth().height(280.dp)) {
            MapHost(
                base = base,
                allowRotateTilt = true,
                modifier = Modifier.fillMaxWidth().height(280.dp),
                onMapReady = { m ->
                    map = m
                    m.addOnMapClickListener {
                        if (frames.isNotEmpty()) playing = !playing
                        true
                    }
                },
                onStyleLoaded = { m, st ->
                    style = st
                    MapLayers.fit(m, path)
                },
            )
            if (frames.isNotEmpty()) {
                IconButton(
                    onClick = { playing = !playing },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).background(Palette.Bg.copy(alpha = 0.88f), MaterialTheme.shapes.medium),
                ) {
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playing) "Jeda animasi rute" else "Putar animasi rute",
                    )
                }
            }
        }

        // Static view and scrubbing: full line faint, travelled part bright, arrow at the current moment.
        LaunchedEffect(style, path, progress, playing, lineColor) {
            val st = style ?: return@LaunchedEffect
            if (playing) return@LaunchedEffect
            val p = progress
            if (p == null || pts.size < 2) {
                MapLayers.setLine(st, "activity-ghost", emptyList(), ghostColor, 3f)
                MapLayers.setLine(st, "activity", path, lineColor, 5f, lineStyle = MapLayers.LineStyle.TRACK)
                MapLayers.setHeadingPuck(st, "activity-now", null, null, lineColor)
            } else {
                val t = t0 + (span * p).toLong()
                val prefix = Playback.prefix(pts, t)
                MapLayers.setLine(st, "activity-ghost", path, ghostColor, 3f)
                MapLayers.setLine(st, "activity", prefix.map { LatLon(it.lat, it.lon) }, lineColor, 5f, lineStyle = MapLayers.LineStyle.TRACK)
                val here = prefix.last()
                MapLayers.setHeadingPuck(st, "activity-now", LatLon(here.lat, here.lon), null, lineColor)
            }
        }

        LaunchedEffect(playing) {
            val m = map ?: return@LaunchedEffect
            val st = style ?: return@LaunchedEffect
            if (!playing || frames.isEmpty()) return@LaunchedEffect
            val start = withFrameNanos { it }
            var last = -1
            while (true) {
                val now = withFrameNanos { it }
                val i = (((now - start) / 1_000_000L) * FPS / 1000).toInt()
                if (i >= frames.size) break
                if (i == last) continue
                last = i
                val f = frames[i]
                val prefix = Playback.prefix(pts, f.timeMs)
                MapLayers.setLine(st, "activity-ghost", path, ghostColor, 3f)
                MapLayers.setLine(st, "activity", prefix.map { LatLon(it.lat, it.lon) }, lineColor, 5f, lineStyle = MapLayers.LineStyle.TRACK)
                MapLayers.setHeadingPuck(st, "activity-now", f.position, f.bearing.toFloat(), lineColor)
                m.moveCamera(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder().target(f.position.toLatLng()).zoom(15.5).tilt(50.0).bearing(f.bearing).build(),
                    ),
                )
                onProgress(f.progress.toFloat())
            }
            playing = false
            m.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().tilt(0.0).bearing(0.0).build()), 600)
            MapLayers.fit(m, path)
        }

        if (pts.size >= 2) {
            Timeline(pts, t0, span, progress, onScrub = { playing = false; onProgress(it) })
        }
    }
}

@Composable
private fun Timeline(pts: List<PlaybackPoint>, t0: Long, span: Long, progress: Float?, onScrub: (Float?) -> Unit) {
    var local by remember { mutableFloatStateOf(progress ?: 0f) }
    val p = progress ?: local
    val sample = Playback.sampleAt(pts, t0 + (span * p).toLong())
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            StatPair("waktu", Format.duration((span * p).toLong()))
            StatPair("km/j", sample?.speedMps?.let { Format.oneDecimal(it * 3.6) } ?: "--")
            StatPair("bpm", sample?.hr?.toString() ?: "--")
            StatPair("elevasi m", sample?.ele?.let { Format.integer(it) } ?: "--")
        }
        Slider(
            value = p,
            onValueChange = { local = it; onScrub(it) },
        )
        Text(
            if (progress == null) "Geser untuk melihat perjalanan menit demi menit. Ketuk peta untuk animasi." else "Pratinjau perjalanan",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextMuted,
        )
    }
}
