package id.web.agentvaedah.strideway.ui.maps

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import id.web.agentvaedah.strideway.core.geo.TileMath
import id.web.agentvaedah.strideway.core.terrain.DemMath
import id.web.agentvaedah.strideway.data.prefs.TerrainOverlay
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngQuad
import java.util.LinkedHashMap
import javax.inject.Inject
import javax.inject.Singleton

class TerrainImage(val bitmap: Bitmap, val quad: LatLngQuad)

/**
 * Builds slope / avalanche / aspect / hillshade overlays on the phone from AWS Terrarium DEM
 * tiles, so terrain analytics need no backend. Renders a 3x3 tile block around the view centre.
 */
@Singleton
class TerrainRenderer @Inject constructor(private val http: HttpClient) {
    private val cache = object : LinkedHashMap<String, DoubleArray>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, DoubleArray>?) = size > 24
    }

    suspend fun render(overlay: TerrainOverlay, lat: Double, lon: Double, zoom: Double): Result<TerrainImage> = runCatching {
        require(overlay != TerrainOverlay.NONE)
        val z = zoom.toInt().coerceIn(MIN_Z, MAX_Z)
        val cx = TileMath.lonToTileX(lon, z)
        val cy = TileMath.latToTileY(lat, z)
        val n = 1 shl z
        val xs = (cx - 1..cx + 1).map { Math.floorMod(it, n) }
        val ys = (cy - 1..cy + 1).map { it.coerceIn(0, n - 1) }

        val tiles = coroutineScope {
            ys.flatMap { y -> xs.map { x -> async { tile(z, x, y) } } }.awaitAll()
        }

        withContext(Dispatchers.Default) {
            val size = TILE * 3
            val elev = DoubleArray(size * size)
            tiles.forEachIndexed { i, t ->
                val ox = (i % 3) * TILE
                val oy = (i / 3) * TILE
                for (row in 0 until TILE) System.arraycopy(t, row * TILE, elev, (oy + row) * size + ox, TILE)
            }
            val cell = DemMath.metersPerPixel(lat, z)
            val sa = DemMath.slopeAspect(elev, size, size, cell)
            val pixels = IntArray(size * size) { i ->
                when (overlay) {
                    TerrainOverlay.SLOPE -> DemMath.slopeColor(sa.slopeDeg[i])
                    TerrainOverlay.AVALANCHE -> DemMath.avalancheColor(sa.slopeDeg[i])
                    TerrainOverlay.ASPECT -> DemMath.aspectColor(sa.aspectDeg[i], sa.slopeDeg[i])
                    TerrainOverlay.HILLSHADE -> DemMath.hillshadeColor(sa.slopeDeg[i], sa.aspectDeg[i])
                    TerrainOverlay.NONE -> 0
                }
            }
            val bitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
            val west = TileMath.tileWest(xs.first(), z)
            val east = TileMath.tileWest(xs.first() + 3, z)
            val north = TileMath.tileNorth(ys.first(), z)
            val south = TileMath.tileNorth(ys.first() + 3, z)
            TerrainImage(
                bitmap,
                LatLngQuad(LatLng(north, west), LatLng(north, east), LatLng(south, east), LatLng(south, west)),
            )
        }
    }

    private suspend fun tile(z: Int, x: Int, y: Int): DoubleArray {
        val key = "$z/$x/$y"
        synchronized(cache) { cache[key] }?.let { return it }
        val response = http.get("https://s3.amazonaws.com/elevation-tiles-prod/terrarium/$z/$x/$y.png")
        if (!response.status.isSuccess()) error("Tile elevasi $key gagal dimuat (${response.status.value})")
        val bytes = response.readRawBytes()
        val elev = withContext(Dispatchers.Default) {
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("Tile elevasi $key rusak")
            val px = IntArray(TILE * TILE)
            bmp.getPixels(px, 0, TILE, 0, 0, TILE, TILE)
            bmp.recycle()
            DoubleArray(px.size) { i ->
                val c = px[i]
                DemMath.terrariumElevation((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)
            }
        }
        synchronized(cache) { cache[key] = elev }
        return elev
    }

    companion object {
        const val MIN_Z = 10
        const val MAX_Z = 15
        private const val TILE = 256
    }
}
