package id.web.agentvaedah.strideway.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import id.web.agentvaedah.strideway.core.model.LatLon
import id.web.agentvaedah.strideway.data.files.MapPicture
import id.web.agentvaedah.strideway.data.files.MapSnapshots
import id.web.agentvaedah.strideway.data.prefs.MapBase
import id.web.agentvaedah.strideway.data.prefs.SettingsRepository
import id.web.agentvaedah.strideway.ui.theme.Brand
import kotlinx.coroutines.flow.first

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MapThumbnailEntryPoint {
    fun mapSnapshots(): MapSnapshots
    fun settings(): SettingsRepository
}

/**
 * Route on a real base map, as on Strava's feed. The picture is rendered once by [MapSnapshots] and cached;
 * until it arrives, and whenever tiles cannot be fetched, the offline [TrackThumbnail] sketch shows instead.
 * OSM and OpenTopoMap ask for light use, so thumbnails of those bases use OpenFreeMap.
 */
@Composable
fun MapThumbnail(key: String, path: List<LatLon>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val entry = remember { EntryPointAccessors.fromApplication(context.applicationContext, MapThumbnailEntryPoint::class.java) }
    val token = LocalMapboxToken.current
    BoxWithConstraints(modifier.semantics { contentDescription = "Peta rute aktivitas" }) {
        val density = LocalDensity.current
        val w = with(density) { maxWidth.roundToPx() }
        val h = with(density) { maxHeight.roundToPx() }
        var picture by remember(key, w, h) { mutableStateOf<MapPicture?>(null) }
        LaunchedEffect(key, w, h, path.size) {
            if (path.size < 2 || w <= 0 || h <= 0) return@LaunchedEffect
            val chosen = entry.settings().mapBase.first()
            val base = if (chosen == MapBase.OSM || chosen == MapBase.TOPO) MapBase.STANDARD else chosen
            val pad = with(density) { 14.dp.toPx() }
            picture = entry.mapSnapshots().render(key, path, base, token, w, h, floatArrayOf(pad, pad, w - pad, h - pad))
        }
        Crossfade(picture, label = "map-thumb") { pic ->
            if (pic == null) {
                TrackThumbnail(path, Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize()) {
                    Image(pic.bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                    RouteOverlay(pic, Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun RouteOverlay(pic: MapPicture, modifier: Modifier) {
    Canvas(modifier) {
        val pts = pic.route
        if (pts.size < 2) return@Canvas
        val sx = size.width / pic.bitmap.width
        val sy = size.height / pic.bitmap.height
        val p = Path().apply {
            moveTo(pts[0].x * sx, pts[0].y * sy)
            for (i in 1 until pts.size) lineTo(pts[i].x * sx, pts[i].y * sy)
        }
        val w = 3.dp.toPx()
        drawPath(p, Brand.Ink.copy(alpha = 0.8f), style = Stroke(w + 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(
            p,
            Brush.horizontalGradient(listOf(Brand.Signal, Color(0xFFFFB000))),
            style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        val start = Offset(pts.first().x * sx, pts.first().y * sy)
        val end = Offset(pts.last().x * sx, pts.last().y * sy)
        drawCircle(Color.White, 5.dp.toPx(), start)
        drawCircle(Color(0xFF2FB765), 3.dp.toPx(), start)
        drawCircle(Color.White, 5.dp.toPx(), end)
        drawCircle(Brand.Ink, 3.dp.toPx(), end)
    }
}
