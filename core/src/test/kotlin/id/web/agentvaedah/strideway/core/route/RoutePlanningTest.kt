package id.web.agentvaedah.strideway.core.route

import id.web.agentvaedah.strideway.core.model.LatLon
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BrouterParserTest {
    // Trimmed real-shape BRouter geojson: messages header row, then rows with Distance and WayTags.
    private val json = """
        {"type":"FeatureCollection","features":[{"type":"Feature",
          "properties":{"track-length":"1500","filtered ascend":"42","total-time":"300",
            "messages":[["Longitude","Latitude","Elevation","Distance","CostPerKm","WayTags"],
              ["106800000","-6200000","100","1000","1000","highway=secondary surface=asphalt"],
              ["106810000","-6200000","110","400","1500","highway=track surface=gravel"],
              ["106820000","-6200000","120","100","1500","highway=path"]]},
          "geometry":{"type":"LineString","coordinates":[[106.80,-6.20,100],[106.81,-6.20,110],[106.82,-6.20,120]]}}]}
    """.trimIndent()

    @Test
    fun parsesSummaryAndPoints() {
        val r = BrouterParser.parse(json)!!
        assertEquals(1500.0, r.lengthM, 1e-9)
        assertEquals(42.0, r.ascendM, 1e-9)
        assertEquals(300, r.timeS)
        assertEquals(3, r.points.size)
        assertEquals(110.0, r.points[1].ele!!, 1e-9)
    }

    @Test
    fun surfaceShares() {
        val r = BrouterParser.parse(json)!!
        assertEquals(1000.0 / 1500, r.surfaceShare.getValue("asphalt"), 1e-9)
        assertEquals(400.0 / 1500, r.surfaceShare.getValue("gravel"), 1e-9)
        assertEquals(100.0 / 1500, r.surfaceShare.getValue("unknown"), 1e-9)
        assertEquals(1000.0 / 1500, r.pavedShare, 1e-9)
    }

    @Test
    fun missingMessagesStillGivesPoints() {
        val bare = """{"features":[{"properties":{"track-length":"10"},"geometry":{"coordinates":[[1.0,2.0],[1.1,2.0]]}}]}"""
        val r = BrouterParser.parse(bare)!!
        assertEquals(2, r.points.size)
        assertTrue(r.surfaceShare.isEmpty())
        assertEquals(0.0, r.pavedShare, 1e-9)
    }

    @Test
    fun garbageIsNull() {
        assertEquals(null, BrouterParser.parse("not json"))
    }
}

class RouteOptionsTest {
    private fun opt(len: Double, up: Double, paved: Double, offset: Double = 0.0) = RouteOption(
        points = listOf(RoutePoint(0.0, offset), RoutePoint(0.01, offset)),
        lengthM = len, ascendM = up, timeS = 0, surfaceShare = emptyMap(), pavedShare = paved,
    )

    @Test
    fun sorting() {
        val list = listOf(opt(3000.0, 50.0, 0.2, 0.0), opt(2000.0, 90.0, 0.9, 0.05), opt(2500.0, 10.0, 0.5, 0.1))
        assertEquals(listOf(2000.0, 2500.0, 3000.0), RouteOptions.sort(list, RouteSort.LENGTH).map { it.lengthM })
        assertEquals(listOf(10.0, 50.0, 90.0), RouteOptions.sort(list, RouteSort.ASCENT).map { it.ascendM })
        assertEquals(listOf(0.9, 0.5, 0.2), RouteOptions.sort(list, RouteSort.PAVED).map { it.pavedShare })
    }

    @Test
    fun nearDuplicatesDropped() {
        val list = listOf(opt(3000.0, 50.0, 0.2), opt(3001.0, 50.0, 0.2), opt(2500.0, 10.0, 0.5, 0.1))
        assertEquals(2, RouteOptions.distinct(list).size)
    }
}

class DrawRouteDraftTest {
    private val a = LatLon(0.0, 0.0)
    private val b = LatLon(0.0, 0.01)
    private val c = LatLon(0.0, 0.02)

    @Test
    fun onlyNewSegmentNeedsFetching() {
        var d = DrawRouteDraft().add(a).add(b)
        assertEquals(listOf(a to b), d.missingSegments())
        d = d.withSegment(a, b, listOf(a, LatLon(0.0, 0.005), b))
        d = d.add(c)
        assertEquals(listOf(b to c), d.missingSegments())
    }

    @Test
    fun undoRemovesLastWaypointAndSegment() {
        val d = DrawRouteDraft().add(a).add(b).withSegment(a, b, listOf(a, b)).add(c).undo()
        assertEquals(listOf(a, b), d.waypoints)
        assertTrue(d.missingSegments().isEmpty())
        assertEquals(listOf(a, b), d.path())
    }

    @Test
    fun pathJoinsSegmentsWithoutDuplicatingJoints() {
        val d = DrawRouteDraft().add(a).add(b).add(c).withSegment(a, b, listOf(a, b)).withSegment(b, c, listOf(b, c))
        assertEquals(listOf(a, b, c), d.path())
    }

    @Test
    fun clearEmpties() {
        assertTrue(DrawRouteDraft().add(a).add(b).clear().waypoints.isEmpty())
    }
}
