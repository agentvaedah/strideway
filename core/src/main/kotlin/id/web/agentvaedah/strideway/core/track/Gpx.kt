package id.web.agentvaedah.strideway.core.track

import id.web.agentvaedah.strideway.core.model.Sport
import id.web.agentvaedah.strideway.core.model.Track
import id.web.agentvaedah.strideway.core.model.TrackPoint
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.Locale

object Gpx {
    fun parse(xml: String): Track {
        val root = MiniXml.parse(xml)
        if (root.name != "gpx") throw TrackParseException("Bukan file GPX (root <${root.name}>)")

        val trk = root.child("trk")
        val name = trk?.child("name")?.text?.ifBlank { null }
            ?: root.child("rte")?.child("name")?.text?.ifBlank { null }
            ?: root.child("metadata")?.child("name")?.text?.ifBlank { null }
        val sport = trk?.child("type")?.text?.let(::sportFromType)

        val nodes = root.descendants("trkpt").ifEmpty { root.descendants("rtept") }.ifEmpty { root.children("wpt") }
        val points = nodes.mapNotNull(::toPoint)
        if (points.isEmpty()) throw TrackParseException("GPX tidak berisi titik lokasi")
        return Track(name, sport, points)
    }

    fun write(track: Track): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<gpx version=\"1.1\" creator=\"Strideway\" xmlns=\"http://www.topografix.com/GPX/1/1\" ")
        append("xmlns:gpxtpx=\"http://www.garmin.com/xmlschemas/TrackPointExtension/v1\">\n")
        append("  <trk>\n")
        track.name?.let { append("    <name>").append(MiniXml.escape(it)).append("</name>\n") }
        track.sport?.let { append("    <type>").append(if (it == Sport.RUN) "running" else "cycling").append("</type>\n") }
        append("    <trkseg>\n")
        for (p in track.points) {
            append("      <trkpt lat=\"").append(coord(p.lat)).append("\" lon=\"").append(coord(p.lon)).append("\">")
            p.ele?.let { append("<ele>").append(String.format(Locale.US, "%.1f", it)).append("</ele>") }
            p.timeMs?.let { append("<time>").append(Instant.ofEpochMilli(it)).append("</time>") }
            if (p.hr != null || p.cadence != null) {
                append("<extensions><gpxtpx:TrackPointExtension>")
                p.hr?.let { append("<gpxtpx:hr>").append(it).append("</gpxtpx:hr>") }
                p.cadence?.let { append("<gpxtpx:cad>").append(it).append("</gpxtpx:cad>") }
                append("</gpxtpx:TrackPointExtension></extensions>")
            }
            append("</trkpt>\n")
        }
        append("    </trkseg>\n  </trk>\n</gpx>\n")
    }

    private fun toPoint(n: XmlNode): TrackPoint? {
        val lat = n.attrs["lat"]?.toDoubleOrNull() ?: return null
        val lon = n.attrs["lon"]?.toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return TrackPoint(
            lat = lat,
            lon = lon,
            ele = n.child("ele")?.text?.toDoubleOrNull(),
            timeMs = n.child("time")?.text?.let(::parseTime),
            hr = n.firstDescendant("hr")?.text?.toIntOrNull(),
            cadence = n.firstDescendant("cad")?.text?.toIntOrNull(),
            power = n.firstDescendant("power")?.text?.toIntOrNull(),
        )
    }

    private fun coord(v: Double) = String.format(Locale.US, "%.7f", v)

    internal fun sportFromType(type: String): Sport? {
        val t = type.lowercase(Locale.ROOT)
        return when {
            "run" in t || "lari" in t || "walk" in t || "hik" in t -> Sport.RUN
            "bik" in t || "cycl" in t || "ride" in t || "sepeda" in t -> Sport.RIDE
            else -> null
        }
    }
}

internal fun parseTime(text: String): Long? = try {
    Instant.parse(text.trim()).toEpochMilli()
} catch (e: DateTimeParseException) {
    try {
        OffsetDateTime.parse(text.trim()).toInstant().toEpochMilli()
    } catch (e: DateTimeParseException) {
        null
    }
}
