package id.web.agentvaedah.strideway.core.geo

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/** Web Mercator slippy-map tile helpers. */
object TileMath {
    fun lonToTileX(lon: Double, z: Int): Int {
        val n = 1 shl z
        return floor((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
    }

    fun latToTileY(lat: Double, z: Int): Int {
        val n = 1 shl z
        val r = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
        return floor((1.0 - ln(tan(r) + 1.0 / cos(r)) / PI) / 2.0 * n).toInt().coerceIn(0, n - 1)
    }

    fun tileWest(x: Int, z: Int): Double = x.toDouble() / (1 shl z) * 360.0 - 180.0

    fun tileNorth(y: Int, z: Int): Double {
        val n = PI - 2.0 * PI * y / (1 shl z)
        return Math.toDegrees(atan(sinh(n)))
    }
}
