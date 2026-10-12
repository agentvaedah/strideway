package id.web.agentvaedah.strideway.ui.record

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import id.web.agentvaedah.strideway.core.model.LatLon
import id.web.agentvaedah.strideway.data.prefs.MapBase
import id.web.agentvaedah.strideway.service.GpsFix
import id.web.agentvaedah.strideway.service.RecordingState
import id.web.agentvaedah.strideway.ui.components.LineChart
import id.web.agentvaedah.strideway.ui.components.MapHost
import id.web.agentvaedah.strideway.ui.components.MapLayers
import id.web.agentvaedah.strideway.ui.components.Series
import id.web.agentvaedah.strideway.ui.components.toLatLng
import id.web.agentvaedah.strideway.ui.theme.Palette
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

@Composable
fun LiveMap(state: RecordingState.Active, base: MapBase, navRoute: List<LatLon>, modifier: Modifier = Modifier) {
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var centered by remember { mutableStateOf(false) }

    Box(modifier) {
        MapHost(
            base = base,
            modifier = Modifier.matchParentSize(),
            onMapReady = { map = it },
            onStyleLoaded = { _, st -> style = st },
        )

        LaunchedEffect(style, navRoute) {
            val st = style ?: return@LaunchedEffect
            MapLayers.setLine(st, "nav-route", navRoute, Palette.Info, 5f)
        }
        LaunchedEffect(style, state.path.size, state.position) {
            val st = style ?: return@LaunchedEffect
            MapLayers.setLine(st, "live-track", state.path, Palette.Accent, 5f)
            MapLayers.setPoint(st, "live-position", state.position, Palette.Accent)
            val m = map ?: return@LaunchedEffect
            val pos = state.position ?: return@LaunchedEffect
            if (!centered) {
                m.moveCamera(CameraUpdateFactory.newLatLngZoom(pos.toLatLng(), 16.0))
                centered = true
            } else {
                m.animateCamera(CameraUpdateFactory.newLatLng(pos.toLatLng()), 400)
            }
        }

        Compass(state.headingDeg, Modifier.align(Alignment.TopEnd).padding(12.dp))
        GpsBadge(state.gpsFix, Modifier.align(Alignment.TopStart).padding(12.dp))

        if (state.elevationProfile.size >= 2) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .background(Palette.Bg.copy(alpha = 0.78f), MaterialTheme.shapes.small)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                LineChart(
                    series = listOf(Series(state.elevationProfile, Palette.TextMuted)),
                    description = "Profil elevasi sejauh ini",
                    height = 40.dp,
                    fillFirst = true,
                )
            }
        }
    }
}

@Composable
private fun Compass(heading: Float?, modifier: Modifier) {
    val label = heading?.let { "Arah ${it.toInt()} derajat" } ?: "Arah belum diketahui"
    Box(
        modifier
            .size(48.dp)
            .background(Palette.Bg.copy(alpha = 0.85f), CircleShape)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(30.dp).rotate(heading ?: 0f)) {
            val w = size.width
            val h = size.height
            val north = Path().apply {
                moveTo(w / 2, 0f); lineTo(w * 0.72f, h / 2); lineTo(w * 0.28f, h / 2); close()
            }
            val south = Path().apply {
                moveTo(w / 2, h); lineTo(w * 0.72f, h / 2); lineTo(w * 0.28f, h / 2); close()
            }
            drawPath(north, if (heading != null) Palette.Accent else Palette.TextMuted)
            drawPath(south, Palette.TextMuted)
            drawCircle(Palette.Bg, radius = 2.dp.toPx(), center = Offset(w / 2, h / 2))
        }
    }
}

@Composable
private fun GpsBadge(fix: GpsFix, modifier: Modifier) {
    val (text, color) = when (fix) {
        GpsFix.SEARCHING -> "Mencari GPS" to Palette.Warning
        GpsFix.OK -> "GPS" to Palette.Success
        GpsFix.DEAD_RECKONING -> "Estimasi IMU" to Palette.Warning
        GpsFix.LOST -> "GPS hilang" to Palette.Error
    }
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = modifier
            .background(Palette.Bg.copy(alpha = 0.85f), MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
