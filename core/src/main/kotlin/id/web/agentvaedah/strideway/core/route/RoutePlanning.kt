package id.web.agentvaedah.strideway.core.route

import id.web.agentvaedah.strideway.core.geo.Geo
import id.web.agentvaedah.strideway.core.model.LatLon
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class RoutePoint(val lat: Double, val lon: Double, val ele: Double? = null) {
    val latLon: LatLon get() = LatLon(lat, lon)
}

data class RouteOption(
    val points: List<RoutePoint>,
    val lengthM: Double,
    val ascendM: Double,
    val timeS: Int,
    /** Share of length per OSM surface value; ways without one count as "unknown". */
    val surfaceShare: Map<String, Double>,
    val pavedShare: Double,
    /** Turn instructions from BRouter voicehints; empty when the server sent none. */
    val maneuvers: List<Maneuver> = emptyList(),
)

object BrouterParser {
    private val json = Json { ignoreUnknownKeys = true }
    private val paved = setOf("asphalt", "concrete", "paved", "paving_stones", "sett", "concrete:plates", "concrete:lanes")

    /** Parses one BRouter GeoJSON answer. Columns of the per-way messages are found by header name. */
    fun parse(text: String): RouteOption? = try {
        val feature = json.parseToJsonElement(text).jsonObject["features"]!!.jsonArray[0].jsonObject
        val props = feature["properties"] as? JsonObject
        val coords = feature["geometry"]!!.jsonObject["coordinates"]!!.jsonArray
        val points = coords.map { c ->
            val a = c.jsonArray
            RoutePoint(a[1].jsonPrimitive.doubleOrNull!!, a[0].jsonPrimitive.doubleOrNull!!, a.getOrNull(2)?.jsonPrimitive?.doubleOrNull)
        }
        fun prop(name: String) = props?.get(name)?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
        val length = prop("track-length") ?: 0.0
        val shares = surfaceShares(props?.get("messages") as? JsonArray, length)
        RouteOption(
            points = points,
            lengthM = length,
            ascendM = prop("filtered ascend") ?: 0.0,
            timeS = prop("total-time")?.toInt() ?: 0,
            surfaceShare = shares,
            pavedShare = shares.filterKeys { it in paved }.values.sum(),
            maneuvers = maneuvers(props?.get("voicehints") as? JsonArray, points.size),
        )
    } catch (e: Exception) {
        null
    }

    /** voicehints rows are [pointIndex, command, exitNumber, distanceToNext, angle] (turnInstructionMode=2). */
    private fun maneuvers(hints: JsonArray?, pointCount: Int): List<Maneuver> = hints.orEmpty().mapNotNull { row ->
        val r = row as? JsonArray ?: return@mapNotNull null
        val idx = r.getOrNull(0)?.jsonPrimitive?.doubleOrNull?.toInt() ?: return@mapNotNull null
        val cmd = r.getOrNull(1)?.jsonPrimitive?.doubleOrNull?.toInt() ?: return@mapNotNull null
        val type = ManeuverType.fromBrouter(cmd) ?: return@mapNotNull null
        if (idx !in 0 until pointCount || type == ManeuverType.STRAIGHT) return@mapNotNull null
        val exit = kotlin.math.abs(r.getOrNull(2)?.jsonPrimitive?.doubleOrNull?.toInt() ?: 0)
        Maneuver(idx, type, exit)
    }

    private fun surfaceShares(messages: JsonArray?, length: Double): Map<String, Double> {
        if (messages == null || messages.size < 2) return emptyMap()
        val header = messages[0].jsonArray.map { it.jsonPrimitive.content }
        val distCol = header.indexOf("Distance")
        val tagsCol = header.indexOf("WayTags")
        if (distCol < 0 || tagsCol < 0) return emptyMap()
        val meters = HashMap<String, Double>()
        for (row in messages.drop(1)) {
            val r = row.jsonArray
            val d = r.getOrNull(distCol)?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: continue
            val tags = r.getOrNull(tagsCol)?.jsonPrimitive?.contentOrNull.orEmpty()
            val surface = tags.split(' ').firstOrNull { it.startsWith("surface=") }?.substringAfter('=') ?: "unknown"
            meters[surface] = (meters[surface] ?: 0.0) + d
        }
        val total = if (length > 0) length else meters.values.sum()
        if (total <= 0) return emptyMap()
        return meters.mapValues { it.value / total }
    }
}

enum class RouteSort(val label: String) { LENGTH("Jarak"), ASCENT("Tanjakan"), PAVED("Aspal") }

object RouteOptions {
    fun sort(list: List<RouteOption>, by: RouteSort): List<RouteOption> = when (by) {
        RouteSort.LENGTH -> list.sortedBy { it.lengthM }
        RouteSort.ASCENT -> list.sortedBy { it.ascendM }
        RouteSort.PAVED -> list.sortedByDescending { it.pavedShare }
    }

    /** BRouter alternatives can come back identical; keep one when lengths and shapes match. */
    fun distinct(list: List<RouteOption>): List<RouteOption> {
        val out = ArrayList<RouteOption>()
        for (o in list) {
            if (out.none { same(it, o) }) out += o
        }
        return out
    }

    private fun same(a: RouteOption, b: RouteOption): Boolean {
        if (kotlin.math.abs(a.lengthM - b.lengthM) > maxOf(50.0, a.lengthM * 0.01)) return false
        val sa = sample(a.points)
        val sb = sample(b.points)
        return sa.zip(sb).all { (p, q) -> Geo.distanceM(p, q) < 100.0 }
    }

    private fun sample(points: List<RoutePoint>): List<LatLon> {
        if (points.isEmpty()) return emptyList()
        return (0..10).map { points[(it * (points.size - 1)) / 10].latLon }
    }
}

/**
 * A route drawn by tapping: waypoints plus the road-snapped segment between each consecutive pair.
 * Immutable so undo is just dropping the last waypoint.
 */
data class DrawRouteDraft(
    val waypoints: List<LatLon> = emptyList(),
    private val segments: Map<Pair<LatLon, LatLon>, List<LatLon>> = emptyMap(),
) {
    fun add(p: LatLon) = copy(waypoints = waypoints + p)

    fun undo(): DrawRouteDraft {
        if (waypoints.isEmpty()) return this
        val kept = waypoints.dropLast(1)
        val pairs = kept.zipWithNext().toSet()
        return DrawRouteDraft(kept, segments.filterKeys { it in pairs })
    }

    fun clear() = DrawRouteDraft()

    fun withSegment(a: LatLon, b: LatLon, path: List<LatLon>) = copy(segments = segments + ((a to b) to path))

    fun missingSegments(): List<Pair<LatLon, LatLon>> = waypoints.zipWithNext().filter { it !in segments }

    /** Joined path; a segment not fetched yet is drawn as a straight line. */
    fun path(): List<LatLon> {
        if (waypoints.size < 2) return waypoints
        val out = ArrayList<LatLon>()
        for (pair in waypoints.zipWithNext()) {
            val seg = segments[pair] ?: listOf(pair.first, pair.second)
            if (out.isNotEmpty() && out.last() == seg.first()) out.addAll(seg.drop(1)) else out.addAll(seg)
        }
        return out
    }
}
