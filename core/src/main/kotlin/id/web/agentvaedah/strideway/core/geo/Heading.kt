package id.web.agentvaedah.strideway.core.geo

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Picks the direction of travel: GPS course above [minGpsSpeedKmh] (the phone may be mounted at any angle),
 * otherwise the true-north sensor azimuth. Smoothed on the unit circle so 359° and 1° average to 0°.
 */
class HeadingFilter(private val alpha: Double = 0.3, private val minGpsSpeedKmh: Double = 6.0) {
    private var x = 0.0
    private var y = 0.0
    private var has = false

    fun update(gpsCourseDeg: Double?, sensorAzimuthDeg: Double?, speedKmh: Double): Double? {
        val raw = if (gpsCourseDeg != null && speedKmh >= minGpsSpeedKmh) gpsCourseDeg else sensorAzimuthDeg ?: gpsCourseDeg
        if (raw == null) return current()
        val r = Math.toRadians(raw)
        if (!has) {
            x = cos(r); y = sin(r); has = true
        } else {
            x += alpha * (cos(r) - x)
            y += alpha * (sin(r) - y)
        }
        return current()
    }

    fun current(): Double? = if (has) Heading.normalize(Math.toDegrees(atan2(y, x))) else null
}

object Heading {
    private val labels = listOf("U", "TL", "T", "TG", "S", "BD", "B", "BL")

    fun normalize(deg: Double): Double = ((deg % 360.0) + 360.0) % 360.0

    /** Eight-point label in Indonesian: Utara, Timur Laut, Timur, Tenggara, Selatan, Barat Daya, Barat, Barat Laut. */
    fun cardinal(deg: Double): String = labels[((normalize(deg) + 22.5) / 45.0).toInt() % 8]
}
