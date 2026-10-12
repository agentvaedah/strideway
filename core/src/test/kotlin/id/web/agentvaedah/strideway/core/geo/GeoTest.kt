package id.web.agentvaedah.strideway.core.geo

import id.web.agentvaedah.strideway.core.model.LatLon
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeoTest {
    private val monas = LatLon(-6.1754, 106.8272)
    private val bundaranHi = LatLon(-6.1950, 106.8230)

    @Test
    fun haversineMonasToBundaranHi() {
        assertEquals(2228.33, Geo.distanceM(monas, bundaranHi), 0.05)
    }

    @Test
    fun oneDegreeOfLatitude() {
        assertEquals(111_195.08, Geo.distanceM(LatLon(0.0, 0.0), LatLon(1.0, 0.0)), 0.05)
    }

    @Test
    fun bearingMonasToBundaranHi() {
        assertEquals(192.03, Geo.bearingDeg(monas, bundaranHi), 0.01)
    }

    @Test
    fun destinationRoundTrip() {
        val d = Geo.destination(monas, 192.026, 2228.33)
        assertEquals(bundaranHi.lat, d.lat, 1e-5)
        assertEquals(bundaranHi.lon, d.lon, 1e-5)
    }
}

class ElevationGainTest {
    @Test
    fun ignoresNoiseBelowThreshold() {
        val g = ElevationGain(3.0)
        listOf(100.0, 101.0, 99.5, 101.5, 100.0).forEach(g::add)
        assertEquals(0.0, g.gainM, 0.0)
    }

    @Test
    fun countsRealClimbAndDescent() {
        val g = ElevationGain(3.0)
        listOf(100.0, 104.0, 110.0, 105.0, 100.0).forEach(g::add)
        assertEquals(10.0, g.gainM, 1e-9)
        assertEquals(10.0, g.lossM, 1e-9)
    }
}

class GradeWindowTest {
    @Test
    fun nullUntilWindowHalfFull() {
        val w = GradeWindow(50.0)
        assertNull(w.add(0.0, 100.0))
        assertNull(w.add(10.0, 100.5))
    }

    @Test
    fun fivePercentClimb() {
        val w = GradeWindow(50.0)
        var last: Double? = null
        for (i in 0..20) last = w.add(i * 10.0, 100.0 + i * 0.5)
        assertEquals(5.0, last!!, 1e-9)
    }
}

class PolylineTest {
    @Test
    fun encodesGoogleReferenceExample() {
        val pts = listOf(LatLon(38.5, -120.2), LatLon(40.7, -120.95), LatLon(43.252, -126.453))
        assertEquals("_p~iF~ps|U_ulLnnqC_mqNvxq`@", Polyline.encode(pts))
    }

    @Test
    fun decodeRoundTrip() {
        val pts = listOf(LatLon(-6.17540, 106.82720), LatLon(-6.19500, 106.82300))
        val back = Polyline.decode(Polyline.encode(pts))
        assertEquals(pts.size, back.size)
        assertEquals(pts[1].lat, back[1].lat, 1e-6)
        assertEquals(pts[1].lon, back[1].lon, 1e-6)
    }
}

class TileMathTest {
    @Test
    fun monasTileAtZoom12() {
        assertEquals(3263, TileMath.lonToTileX(106.8272, 12))
        assertEquals(2118, TileMath.latToTileY(-6.1754, 12))
    }

    @Test
    fun tileBoundsContainPoint() {
        val x = TileMath.lonToTileX(106.8272, 12)
        val y = TileMath.latToTileY(-6.1754, 12)
        assertTrue(TileMath.tileWest(x, 12) <= 106.8272 && TileMath.tileWest(x + 1, 12) > 106.8272)
        assertTrue(TileMath.tileNorth(y, 12) >= -6.1754 && TileMath.tileNorth(y + 1, 12) < -6.1754)
    }
}
