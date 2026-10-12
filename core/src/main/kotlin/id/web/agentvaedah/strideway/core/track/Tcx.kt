package id.web.agentvaedah.strideway.core.track

import id.web.agentvaedah.strideway.core.geo.Geo
import id.web.agentvaedah.strideway.core.model.Sport
import id.web.agentvaedah.strideway.core.model.Track
import id.web.agentvaedah.strideway.core.model.TrackPoint
import java.time.Instant
import java.util.Locale

object Tcx {
    fun parse(xml: String): Track {
        val root = MiniXml.parse(xml)
        if (root.name != "TrainingCenterDatabase") throw TrackParseException("Bukan file TCX (root <${root.name}>)")

        val activity = root.firstDescendant("Activity")
        val course = root.firstDescendant("Course")
        val sport = activity?.attrs?.get("Sport")?.let(Gpx::sportFromType)
        val name = course?.child("Name")?.text?.ifBlank { null } ?: activity?.child("Notes")?.text?.ifBlank { null }

        val points = root.descendants("Trackpoint").mapNotNull { tp ->
            val pos = tp.child("Position") ?: return@mapNotNull null
            val lat = pos.child("LatitudeDegrees")?.text?.toDoubleOrNull() ?: return@mapNotNull null
            val lon = pos.child("LongitudeDegrees")?.text?.toDoubleOrNull() ?: return@mapNotNull null
            TrackPoint(
                lat = lat,
                lon = lon,
                ele = tp.child("AltitudeMeters")?.text?.toDoubleOrNull(),
                timeMs = tp.child("Time")?.text?.let(::parseTime),
                hr = tp.child("HeartRateBpm")?.child("Value")?.text?.toIntOrNull(),
                cadence = tp.child("Cadence")?.text?.toIntOrNull() ?: tp.firstDescendant("RunCadence")?.text?.toIntOrNull(),
                power = tp.firstDescendant("Watts")?.text?.toIntOrNull(),
            )
        }
        if (points.isEmpty()) throw TrackParseException("TCX tidak berisi titik lokasi")
        return Track(name, sport, points)
    }

    fun write(track: Track, sport: Sport): String = buildString {
        val first = track.points.firstOrNull()?.timeMs ?: 0L
        val last = track.points.lastOrNull()?.timeMs ?: first
        val distances = cumulativeDistances(track.points)
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<TrainingCenterDatabase xmlns=\"http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2\" ")
        append("xmlns:ns3=\"http://www.garmin.com/xmlschemas/ActivityExtension/v2\">\n")
        append("  <Activities>\n")
        append("    <Activity Sport=\"").append(if (sport == Sport.RUN) "Running" else "Biking").append("\">\n")
        append("      <Id>").append(Instant.ofEpochMilli(first)).append("</Id>\n")
        append("      <Lap StartTime=\"").append(Instant.ofEpochMilli(first)).append("\">\n")
        append("        <TotalTimeSeconds>").append((last - first) / 1000).append("</TotalTimeSeconds>\n")
        append("        <DistanceMeters>").append(fmt(distances.lastOrNull() ?: 0.0)).append("</DistanceMeters>\n")
        append("        <Intensity>Active</Intensity>\n        <TriggerMethod>Manual</TriggerMethod>\n")
        append("        <Track>\n")
        track.points.forEachIndexed { i, p ->
            append("          <Trackpoint>")
            p.timeMs?.let { append("<Time>").append(Instant.ofEpochMilli(it)).append("</Time>") }
            append("<Position><LatitudeDegrees>").append(String.format(Locale.US, "%.7f", p.lat))
            append("</LatitudeDegrees><LongitudeDegrees>").append(String.format(Locale.US, "%.7f", p.lon))
            append("</LongitudeDegrees></Position>")
            p.ele?.let { append("<AltitudeMeters>").append(fmt(it)).append("</AltitudeMeters>") }
            append("<DistanceMeters>").append(fmt(distances[i])).append("</DistanceMeters>")
            p.hr?.let { append("<HeartRateBpm><Value>").append(it).append("</Value></HeartRateBpm>") }
            if (sport == Sport.RIDE) p.cadence?.let { append("<Cadence>").append(it).append("</Cadence>") }
            p.power?.let { append("<Extensions><ns3:TPX><ns3:Watts>").append(it).append("</ns3:Watts></ns3:TPX></Extensions>") }
            append("</Trackpoint>\n")
        }
        append("        </Track>\n      </Lap>\n")
        track.name?.let { append("      <Notes>").append(MiniXml.escape(it)).append("</Notes>\n") }
        append("    </Activity>\n  </Activities>\n</TrainingCenterDatabase>\n")
    }

    private fun fmt(v: Double) = String.format(Locale.US, "%.1f", v)

    private fun cumulativeDistances(points: List<TrackPoint>): List<Double> {
        var sum = 0.0
        return points.mapIndexed { i, p ->
            if (i > 0) sum += Geo.distanceM(points[i - 1].latLon, p.latLon)
            sum
        }
    }
}
