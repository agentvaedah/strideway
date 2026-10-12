package id.web.agentvaedah.strideway.core.track

import id.web.agentvaedah.strideway.core.geo.ElevationGain
import id.web.agentvaedah.strideway.core.geo.Geo
import id.web.agentvaedah.strideway.core.model.Track
import id.web.agentvaedah.strideway.core.model.TrackPoint
import java.util.Locale

object TrackFormats {
    /** Picks the parser from the extension, falling back to content sniffing for odd file names. */
    fun parse(fileName: String, bytes: ByteArray): Track {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val track = when {
            ext == "fit" || isFit(bytes) -> FitDecoder.decode(bytes)
            ext == "tcx" -> Tcx.parse(bytes.toString(Charsets.UTF_8))
            ext == "gpx" -> Gpx.parse(bytes.toString(Charsets.UTF_8))
            else -> {
                val text = bytes.toString(Charsets.UTF_8)
                when {
                    "<TrainingCenterDatabase" in text -> Tcx.parse(text)
                    "<gpx" in text -> Gpx.parse(text)
                    else -> throw TrackParseException("Format tidak dikenali. Gunakan .gpx, .tcx, atau .fit")
                }
            }
        }
        return if (track.name == null) track.copy(name = fileName.substringBeforeLast('.')) else track
    }

    private fun isFit(bytes: ByteArray) =
        bytes.size >= 12 && bytes[8] == '.'.code.toByte() && bytes[9] == 'F'.code.toByte() &&
            bytes[10] == 'I'.code.toByte() && bytes[11] == 'T'.code.toByte()
}

object TrackStats {
    fun distanceM(points: List<TrackPoint>): Double =
        points.zipWithNext { a, b -> Geo.distanceM(a.latLon, b.latLon) }.sum()

    fun elevationGainM(points: List<TrackPoint>): Double {
        val gain = ElevationGain()
        points.forEach { p -> p.ele?.let(gain::add) }
        return gain.gainM
    }

    /** Keeps at most [max] points, evenly spaced, always including both ends. */
    fun <T> downsample(points: List<T>, max: Int): List<T> {
        if (points.size <= max || max < 2) return points
        val step = (points.size - 1).toDouble() / (max - 1)
        return List(max) { i -> points[(i * step).toInt().coerceAtMost(points.lastIndex)] }
    }
}
