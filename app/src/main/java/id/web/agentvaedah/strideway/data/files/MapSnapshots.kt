package id.web.agentvaedah.strideway.data.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.util.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import id.web.agentvaedah.strideway.core.model.LatLon
import id.web.agentvaedah.strideway.data.prefs.MapBase
import id.web.agentvaedah.strideway.ui.components.MapStyles
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.cos
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.snapshotter.MapSnapshot
import org.maplibre.android.snapshotter.MapSnapshotter

/** A rendered base map plus the projection of each route point into its pixels. */
class MapPicture(val bitmap: Bitmap, val route: List<PointF>)

/**
 * Static map pictures of a route, for feed cards and share images. Tiles come from the same base map the
 * app shows, rendered off screen by MapLibre's snapshotter, so they need the network the first time;
 * results are cached in memory and on disk, and a failure returns null so callers fall back to the line alone.
 */
@Singleton
class MapSnapshots @Inject constructor(@ApplicationContext private val context: Context) {
    private val memory = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    // Each snapshotter holds a GL context; two at once keeps a scrolling feed from stalling the phone.
    private val permits = Semaphore(2)
    private val dir: File get() = File(context.cacheDir, "mapthumbs").apply { mkdirs() }

    /**
     * Base map framing [path] inside a [width] x [height] picture, the route fitted into the
     * [inset] rectangle (left, top, right, bottom, in picture pixels) so it clears text drawn on top.
     */
    suspend fun render(
        key: String,
        path: List<LatLon>,
        base: MapBase,
        token: String?,
        width: Int,
        height: Int,
        inset: FloatArray = floatArrayOf(0f, 0f, width.toFloat(), height.toFloat()),
    ): MapPicture? {
        if (path.size < 2 || width < 16 || height < 16) return null
        val camera = frame(path, width, height, inset)
        val id = "$key-${MapStyles.resolve(base, token).name}-${width}x$height-${inset.joinToString("_") { it.toInt().toString() }}"
        val bmp = memory.get(id) ?: fromDisk(id) ?: snapshot(base, token, width, height, camera)?.also { save(id, it) } ?: return null
        memory.put(id, bmp)
        return MapPicture(bmp, path.map { camera.project(it) })
    }

    fun forget(keyPrefix: String) {
        memory.snapshot().keys.filter { it.startsWith("$keyPrefix-") }.forEach(memory::remove)
        dir.listFiles()?.filter { it.name.startsWith("$keyPrefix-") }?.forEach { it.delete() }
    }

    private fun fromDisk(id: String): Bitmap? = File(dir, "$id.png").takeIf(File::exists)?.let { BitmapFactory.decodeFile(it.path) }

    private suspend fun save(id: String, bmp: Bitmap) = withContext(Dispatchers.IO) {
        runCatching { File(dir, "$id.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }

    private suspend fun snapshot(base: MapBase, token: String?, w: Int, h: Int, camera: Camera): Bitmap? = permits.withPermit {
        withTimeoutOrNull(20_000) {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    val options = MapSnapshotter.Options(w, h)
                        .withStyleBuilder(MapStyles.builder(base, token))
                        .withCameraPosition(CameraPosition.Builder().target(LatLng(camera.lat, camera.lon)).zoom(camera.zoom).build())
                        .withPixelRatio(1f)
                        .withLogo(false)
                    val snapshotter = MapSnapshotter(context, options)
                    cont.invokeOnCancellation { snapshotter.cancel() }
                    // Object expressions: the callbacks are plain Kotlin interfaces, not fun interfaces.
                    snapshotter.start(
                        object : MapSnapshotter.SnapshotReadyCallback {
                            override fun onSnapshotReady(snapshot: MapSnapshot) {
                                if (cont.isActive) cont.resume(snapshot.bitmap.copy(Bitmap.Config.ARGB_8888, false))
                            }
                        },
                        object : MapSnapshotter.ErrorHandler {
                            override fun onError(error: String) {
                                if (cont.isActive) cont.resume(null)
                            }
                        },
                    )
                }
            }
        }
    }

    /**
     * Web Mercator camera that fits the route bounds into [inset]. Computed here rather than by the
     * snapshotter's region option so the projection for drawing the route matches the camera exactly.
     */
    private class Camera(val lat: Double, val lon: Double, val zoom: Double, val w: Int, val h: Int) {
        private val scale = TILE * Math.pow(2.0, zoom)
        private val cx = mercX(lon) * scale
        private val cy = mercY(lat) * scale
        fun project(p: LatLon) = PointF((mercX(p.lon) * scale - cx + w / 2.0).toFloat(), (mercY(p.lat) * scale - cy + h / 2.0).toFloat())
    }

    private fun frame(path: List<LatLon>, w: Int, h: Int, inset: FloatArray): Camera {
        val minX = path.minOf { mercX(it.lon) }
        val maxX = path.maxOf { mercX(it.lon) }
        val minY = path.minOf { mercY(it.lat) }
        val maxY = path.maxOf { mercY(it.lat) }
        val boxW = (inset[2] - inset[0]).coerceAtLeast(16f) * 0.86
        val boxH = (inset[3] - inset[1]).coerceAtLeast(16f) * 0.86
        val spanX = max(maxX - minX, 1e-9)
        val spanY = max(maxY - minY, 1e-9)
        val zoom = minOf(Math.log(boxW / (spanX * TILE)) / LN2, Math.log(boxH / (spanY * TILE)) / LN2, 17.0)
        val scale = TILE * Math.pow(2.0, zoom)
        // Shift the camera so the route centre lands on the inset centre, not the picture centre.
        val dx = ((inset[0] + inset[2]) / 2.0 - w / 2.0) / scale
        val dy = ((inset[1] + inset[3]) / 2.0 - h / 2.0) / scale
        val camX = (minX + maxX) / 2 - dx
        val camY = (minY + maxY) / 2 - dy
        return Camera(latOf(camY), lonOf(camX), zoom, w, h)
    }

    private companion object {
        const val TILE = 512.0
        val LN2 = Math.log(2.0)
        fun mercX(lon: Double) = (lon + 180.0) / 360.0
        fun mercY(lat: Double): Double {
            val s = Math.sin(Math.toRadians(lat.coerceIn(-85.0, 85.0)))
            return 0.5 - Math.log((1 + s) / (1 - s)) / (4 * Math.PI)
        }
        fun lonOf(x: Double) = x * 360.0 - 180.0
        fun latOf(y: Double) = Math.toDegrees(Math.atan(Math.sinh(Math.PI * (1 - 2 * y))))
    }
}

/** Keeps a straight-line fallback recognisable: an aspect-correct fit of the route into a box. */
internal fun fitRoute(path: List<LatLon>, left: Float, top: Float, right: Float, bottom: Float): List<PointF> {
    if (path.size < 2) return emptyList()
    val midLat = path.sumOf { it.lat } / path.size
    val kx = cos(Math.toRadians(midLat))
    val xs = path.map { it.lon * kx }
    val ys = path.map { it.lat }
    val minX = xs.min()
    val maxY = ys.max()
    val spanX = max(xs.max() - minX, 1e-9)
    val spanY = max(maxY - ys.min(), 1e-9)
    val bw = right - left
    val bh = bottom - top
    val scale = minOf(bw / spanX, bh / spanY)
    val ox = left + (bw - spanX * scale) / 2
    val oy = top + (bh - spanY * scale) / 2
    return path.indices.map { i -> PointF((ox + (xs[i] - minX) * scale).toFloat(), (oy + (maxY - ys[i]) * scale).toFloat()) }
}
