package id.web.agentvaedah.strideway.core.track

import id.web.agentvaedah.strideway.core.geo.Geo
import id.web.agentvaedah.strideway.core.model.LatLon
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrackEditTest {
    private val origin = LatLon(-6.2, 106.8)
    private val path = (0..100).map { Geo.destination(origin, 0.0, it * 10.0) } // 1 km in 10 m steps

    @Test
    fun fullRangeKeepsAll() {
        assertEquals(0..100, TrackEdit.cropRange(101, 0f, 1f))
    }

    @Test
    fun cropPicksIndices() {
        assertEquals(25..75, TrackEdit.cropRange(101, 0.25f, 0.75f))
    }

    @Test
    fun crossedHandlesStillKeepTwoPoints() {
        val r = TrackEdit.cropRange(101, 0.6f, 0.4f)
        assertTrue(r.last - r.first >= 1)
    }

    @Test
    fun visiblePathTrimsByDistance() {
        val v = TrackEdit.visiblePath(path, 200, 300)
        assertEquals(51, v.size)
        assertEquals(path[20], v.first())
        assertEquals(path[70], v.last())
    }

    @Test
    fun hideZeroIsUnchanged() {
        assertEquals(path, TrackEdit.visiblePath(path, 0, 0))
    }

    @Test
    fun hideMoreThanTotalIsEmpty() {
        assertTrue(TrackEdit.visiblePath(path, 600, 600).isEmpty())
    }
}
