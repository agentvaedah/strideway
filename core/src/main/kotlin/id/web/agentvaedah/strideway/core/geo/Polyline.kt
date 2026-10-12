package id.web.agentvaedah.strideway.core.geo

import id.web.agentvaedah.strideway.core.model.LatLon
import kotlin.math.roundToLong

/** Google encoded polyline, precision 1e5. */
object Polyline {
    fun encode(points: List<LatLon>): String {
        val sb = StringBuilder()
        var prevLat = 0L
        var prevLon = 0L
        for (p in points) {
            val lat = (p.lat * 1e5).roundToLong()
            val lon = (p.lon * 1e5).roundToLong()
            encodeValue(lat - prevLat, sb)
            encodeValue(lon - prevLon, sb)
            prevLat = lat
            prevLon = lon
        }
        return sb.toString()
    }

    fun decode(encoded: String): List<LatLon> {
        val out = ArrayList<LatLon>()
        var index = 0
        var lat = 0L
        var lon = 0L
        while (index < encoded.length) {
            val (dLat, i1) = decodeValue(encoded, index)
            val (dLon, i2) = decodeValue(encoded, i1)
            index = i2
            lat += dLat
            lon += dLon
            out += LatLon(lat / 1e5, lon / 1e5)
        }
        return out
    }

    private fun encodeValue(value: Long, sb: StringBuilder) {
        var v = if (value < 0) (value shl 1).inv() else value shl 1
        while (v >= 0x20) {
            sb.append(((0x20 or (v and 0x1f).toInt()) + 63).toChar())
            v = v shr 5
        }
        sb.append((v + 63).toInt().toChar())
    }

    private fun decodeValue(s: String, start: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var i = start
        while (true) {
            if (i >= s.length) throw IllegalArgumentException("Polyline terpotong di indeks $i")
            val b = s[i++].code - 63
            result = result or ((b and 0x1f).toLong() shl shift)
            shift += 5
            if (b < 0x20) break
        }
        val value = if ((result and 1L) != 0L) (result shr 1).inv() else result shr 1
        return value to i
    }
}
