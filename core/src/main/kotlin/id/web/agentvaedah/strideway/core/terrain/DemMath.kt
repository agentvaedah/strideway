package id.web.agentvaedah.strideway.core.terrain

import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Terrain analytics on Terrarium-encoded DEM tiles. Colours are ARGB ints. */
object DemMath {
    fun terrariumElevation(r: Int, g: Int, b: Int): Double = r * 256.0 + g + b / 256.0 - 32768.0

    fun metersPerPixel(lat: Double, zoom: Int, tileSize: Int = 256): Double =
        156_543.03392 * cos(Math.toRadians(lat)) / (1 shl zoom) * (256.0 / tileSize)

    class SlopeAspect(val slopeDeg: FloatArray, val aspectDeg: FloatArray)

    /**
     * Horn (1981) 3x3 gradient. Aspect is the downslope compass direction (0 = north, clockwise),
     * or -1 on flat cells. Edge cells reuse their nearest neighbour.
     */
    fun slopeAspect(elev: DoubleArray, width: Int, height: Int, cellM: Double): SlopeAspect {
        require(elev.size == width * height) { "elev size ${elev.size} != $width x $height" }
        val slope = FloatArray(elev.size)
        val aspect = FloatArray(elev.size)
        fun z(x: Int, y: Int) = elev[y.coerceIn(0, height - 1) * width + x.coerceIn(0, width - 1)]
        for (y in 0 until height) {
            for (x in 0 until width) {
                val a = z(x - 1, y - 1); val b = z(x, y - 1); val c = z(x + 1, y - 1)
                val d = z(x - 1, y); val f = z(x + 1, y)
                val g = z(x - 1, y + 1); val h = z(x, y + 1); val i = z(x + 1, y + 1)
                val dzdx = ((c + 2 * f + i) - (a + 2 * d + g)) / (8 * cellM)
                val dzdy = ((g + 2 * h + i) - (a + 2 * b + c)) / (8 * cellM)
                val idx = y * width + x
                slope[idx] = Math.toDegrees(atan(sqrt(dzdx * dzdx + dzdy * dzdy))).toFloat()
                aspect[idx] = if (dzdx == 0.0 && dzdy == 0.0) {
                    -1f
                } else {
                    // Image y grows southward; downslope vector is (-dzdx east, +dzdy north).
                    val deg = Math.toDegrees(atan2(-dzdx, dzdy))
                    ((deg + 360.0) % 360.0).toFloat()
                }
            }
        }
        return SlopeAspect(slope, aspect)
    }

    private val slopeStops = listOf(
        0f to 0x00000000,
        3f to 0x664CAF50,
        6f to 0x99CDDC39.toInt(),
        10f to 0xAAFFC107.toInt(),
        15f to 0xBBFF5722.toInt(),
        25f to 0xCCB71C1C.toInt(),
    )

    /** Cycling-oriented ramp: green under 6°, amber around 10°, red past 15°. */
    fun slopeColor(deg: Float): Int {
        var color = slopeStops.first().second
        for ((limit, c) in slopeStops) if (deg >= limit) color = c
        return color
    }

    /** Avalanche-style hazard bands (30-35°, 35-40°, 40-45°, >45°); below 30° transparent. */
    fun avalancheColor(deg: Float): Int = when {
        deg < 30f -> 0x00000000
        deg < 35f -> 0xCCFFEB3B.toInt()
        deg < 40f -> 0xCCFF9800.toInt()
        deg < 45f -> 0xCCF44336.toInt()
        else -> 0xCC9C27B0.toInt()
    }

    private val aspectColors = intArrayOf(
        0xCC2196F3.toInt(), // N
        0xCC00BCD4.toInt(), // NE
        0xCC4CAF50.toInt(), // E
        0xCCCDDC39.toInt(), // SE
        0xCCFFC107.toInt(), // S
        0xCCFF5722.toInt(), // SW
        0xCCE91E63.toInt(), // W
        0xCC673AB7.toInt(), // NW
    )

    /** Eight-way aspect wheel; flat ground (under 2°) stays transparent. */
    fun aspectColor(aspect: Float, slope: Float): Int {
        if (aspect < 0 || slope < 2f) return 0x00000000
        val sector = (((aspect + 22.5f) % 360f) / 45f).toInt().coerceIn(0, 7)
        return aspectColors[sector]
    }

    /**
     * Shaded relief lit from the north-west at 45°; returned as black with alpha so it darkens
     * whatever base map sits underneath instead of washing it out.
     */
    fun hillshadeColor(slopeDeg: Float, aspectDeg: Float, azimuthDeg: Double = 315.0, altitudeDeg: Double = 45.0): Int {
        val zenith = Math.toRadians(90.0 - altitudeDeg)
        val slope = Math.toRadians(slopeDeg.toDouble())
        val facing = if (aspectDeg < 0) 0.0 else sin(zenith) * sin(slope) * cos(Math.toRadians(azimuthDeg - aspectDeg))
        val light = (cos(zenith) * cos(slope) + facing).coerceIn(0.0, 1.0)
        val alpha = ((1.0 - light) * 200).toInt().coerceIn(0, 255)
        return alpha shl 24
    }

    fun aspectLabel(aspect: Float): String {
        if (aspect < 0) return "Datar"
        val names = arrayOf("Utara", "Timur laut", "Timur", "Tenggara", "Selatan", "Barat daya", "Barat", "Barat laut")
        return names[(((aspect + 22.5f) % 360f) / 45f).toInt().coerceIn(0, 7)]
    }
}
