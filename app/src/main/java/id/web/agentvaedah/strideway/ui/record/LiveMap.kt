package id.web.agentvaedah.strideway.ui.record

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import id.web.agentvaedah.strideway.core.format.Format
import id.web.agentvaedah.strideway.core.model.LatLon
import id.web.agentvaedah.strideway.core.model.Sport
import id.web.agentvaedah.strideway.core.route.NavState
import id.web.agentvaedah.strideway.data.prefs.MapBase
import id.web.agentvaedah.strideway.data.remote.weather.WeatherState
import id.web.agentvaedah.strideway.service.GpsFix
import id.web.agentvaedah.strideway.service.RecordingState
import id.web.agentvaedah.strideway.ui.components.MapCompass
import id.web.agentvaedah.strideway.ui.components.MapHost
import id.web.agentvaedah.strideway.ui.components.MapLayers
import id.web.agentvaedah.strideway.ui.components.toLatLng
import id.web.agentvaedah.strideway.ui.theme.Brand
import id.web.agentvaedah.strideway.ui.theme.MetricText
import id.web.agentvaedah.strideway.ui.theme.Palette
import id.web.agentvaedah.strideway.ui.weather.WeatherBadge
import java.util.Locale
import kotlin.math.roundToInt
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

/**
 * Full-screen live map behind the metrics sheet. [bottomInset] is the height the sheet covers, so the
 * rider's arrow sits in the visible part of the map rather than under the sheet.
 */
@Composable
fun LiveMap(
    state: RecordingState.Active,
    base: MapBase,
    navRoute: List<LatLon>,
    nav: NavState?,
    /** Length of the active route while no fix has placed the rider on it; null without a route. */
    waitingRouteM: Double? = null,
    camera: CameraMode,
    tilt3d: Boolean,
    headingUp: Boolean,
    routeAlerts: Boolean,
    bottomInset: Dp,
    onCamera: (CameraMode) -> Unit,
    onToggle3d: () -> Unit,
    onToggleHeadingUp: () -> Unit,
    onToggleAlerts: () -> Unit,
    onSwitchRoute: () -> Unit,
    weather: WeatherState,
    onOpenWeather: () -> Unit,
    modifier: Modifier = Modifier,
    /** Map-only display: the numbers live on the map itself, so speed stays in sight. */
    showSpeed: Boolean = false,
) {
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var mapBearing by remember { mutableFloatStateOf(0f) }
    val onCameraLatest by rememberUpdatedState(onCamera)

    Box(modifier) {
        MapHost(
            base = base,
            allowRotateTilt = true,
            modifier = Modifier.matchParentSize(),
            onMapReady = { map = it },
            onStyleLoaded = { _, st -> style = st },
        )

        DisposableEffect(map) {
            val m = map ?: return@DisposableEffect onDispose { }
            val started = MapLibreMap.OnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) onCameraLatest(CameraMode.FREE)
            }
            val moved = MapLibreMap.OnCameraMoveListener { mapBearing = m.cameraPosition.bearing.toFloat() }
            m.addOnCameraMoveStartedListener(started)
            m.addOnCameraMoveListener(moved)
            onDispose {
                m.removeOnCameraMoveStartedListener(started)
                m.removeOnCameraMoveListener(moved)
            }
        }

        val routeColor = Palette.Info
        val trackColor = Palette.Accent
        LaunchedEffect(style, navRoute, routeColor) {
            val st = style ?: return@LaunchedEffect
            MapLayers.setLine(st, "nav-route", navRoute, routeColor, 5f, lineStyle = MapLayers.LineStyle.ROUTE)
        }
        LaunchedEffect(style, state.path.size, state.position, state.headingDeg, trackColor) {
            val st = style ?: return@LaunchedEffect
            MapLayers.setLine(st, "live-track", state.path, trackColor, 6f, lineStyle = MapLayers.LineStyle.TRACK)
            MapLayers.setHeadingPuck(st, "live-position", state.position, state.headingDeg, trackColor)
        }

        val density = LocalDensity.current
        val insetPx = with(density) { bottomInset.toPx() }.roundToInt()
        LaunchedEffect(map, camera, tilt3d, headingUp, state.position, state.headingDeg, insetPx, navRoute) {
            val m = map ?: return@LaunchedEffect
            when (camera) {
                CameraMode.FREE -> Unit
                CameraMode.OVERVIEW -> {
                    val pts = navRoute + state.path + listOfNotNull(state.position)
                    if (pts.isEmpty()) return@LaunchedEffect
                    val bounds = LatLngBounds.Builder().includes(pts.map { it.toLatLng() }).build()
                    if (pts.distinct().size < 2) {
                        m.animateCamera(CameraUpdateFactory.newLatLngZoom(pts.first().toLatLng(), 15.0), 600)
                    } else {
                        m.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 0.0, 0.0, 120, 160, 120, insetPx + 60), 600)
                    }
                }
                CameraMode.FOLLOW -> {
                    val pos = state.position ?: return@LaunchedEffect
                    // Bottom padding moves the focus point up into the visible map above the sheet.
                    val target = CameraPosition.Builder()
                        .padding(0.0, 0.0, 0.0, insetPx.toDouble())
                        .target(pos.toLatLng())
                        .zoom(if (tilt3d) 17.0 else maxOf(m.cameraPosition.zoom, 15.5).coerceAtMost(17.5))
                        .tilt(if (tilt3d) 60.0 else 0.0)
                        .bearing(if (headingUp) (state.headingDeg ?: m.cameraPosition.bearing.toFloat()).toDouble() else 0.0)
                        .build()
                    m.animateCamera(CameraUpdateFactory.newCameraPosition(target), 600)
                }
            }
        }

        Column(
            Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 12.dp, end = if (nav != null || waitingRouteM != null) 12.dp else 72.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // With a route the turn banner owns the top; the compass and buttons move below it.
            if (nav != null) NavBanner(nav) else waitingRouteM?.let { NavWaitingBanner(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GpsBadge(state.gpsFix)
                WeatherBadge(weather, onOpenWeather)
            }
        }

        Column(
            Modifier.align(Alignment.TopEnd).padding(12.dp).padding(top = if (nav != null) 150.dp else if (waitingRouteM != null) 110.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MapCompass(mapBearing, state.headingDeg, headingUp, onToggleHeadingUp)
            MapControls(
                tilt3d = tilt3d,
                camera = camera,
                hasRoute = navRoute.size >= 2,
                routeAlerts = routeAlerts,
                onToggle3d = onToggle3d,
                onLocate = { onCamera(CameraMode.FOLLOW) },
                onOverview = { onCamera(if (camera == CameraMode.OVERVIEW) CameraMode.FOLLOW else CameraMode.OVERVIEW) },
                onSwitchRoute = onSwitchRoute,
                onToggleAlerts = onToggleAlerts,
            )
        }

        // After the rider pans away, a large button brings the map back to where they are.
        AnimatedVisibility(
            visible = camera == CameraMode.FREE && state.position != null,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = if (showSpeed) 148.dp else 16.dp),
        ) {
            FloatingActionButton(
                onClick = { onCamera(CameraMode.FOLLOW) },
                containerColor = Palette.Surface,
                contentColor = Palette.Info,
                shape = CircleShape,
            ) { Icon(Icons.Outlined.MyLocation, contentDescription = "Kembali ke lokasi saya") }
        }

        if (showSpeed) MapSpeedPanel(state, Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp))
    }
}

/** Speed as the largest figure on a dark chip, time and distance beside it; readable on any base map. */
@Composable
private fun MapSpeedPanel(state: RecordingState.Active, modifier: Modifier = Modifier) {
    val m = state.metrics
    val run = state.sport == Sport.RUN
    val hasFix = state.gpsFix == GpsFix.OK || state.gpsFix == GpsFix.DEAD_RECKONING
    val now = when {
        !hasFix -> "--"
        run -> Format.pace(m.speedKmh / 3.6)
        else -> String.format(Locale.US, "%.1f", m.speedKmh).replace('.', ',')
    }
    val avg = if (run) Format.pace(m.avgSpeedKmh / 3.6) else String.format(Locale.US, "%.1f", m.avgSpeedKmh).replace('.', ',')
    val max = String.format(Locale.US, "%.1f", m.maxSpeedKmh).replace('.', ',')
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(
            Modifier
                .background(Brand.Ink.copy(alpha = 0.92f), MaterialTheme.shapes.large)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .clearAndSetSemantics { contentDescription = if (run) "Pace sekarang $now" else "Kecepatan sekarang $now kilometer per jam" },
        ) {
            Text(if (run) "pace /km" else "km/j", style = MetricText.Label, color = Color(0xFFA6A6AD))
            Text(now, style = MetricText.Huge, color = Color(0xFFF4F4F5), maxLines = 1)
            Text(if (run) "rata $avg" else "rata $avg · maks $max", style = MetricText.Label, color = Color(0xFFA6A6AD), maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.End) {
            MiniStat(clock(m.movingMs).removePrefix("00:"), "waktu")
            MiniStat(String.format(Locale.US, "%.2f", m.distanceM / 1000.0).replace('.', ','), "km")
        }
    }
}

@Composable
private fun MiniStat(value: String, label: String) {
    Column(
        Modifier
            .background(Palette.Surface.copy(alpha = 0.94f), MaterialTheme.shapes.medium)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .clearAndSetSemantics { contentDescription = "$label $value" },
        horizontalAlignment = Alignment.End,
    ) {
        Text(value, style = MetricText.Small, color = Palette.Text, maxLines = 1)
        Text(label, style = MetricText.Label, color = Palette.TextMuted)
    }
}

@Composable
private fun GpsBadge(fix: GpsFix) {
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
        modifier = Modifier
            .background(Palette.Bg.copy(alpha = 0.85f), MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
