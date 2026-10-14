package id.web.agentvaedah.strideway.core.track

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaybackTest {
    private val pts = listOf(
        PlaybackPoint(0.0, 0.0, 0, ele = 10.0, speedMps = 5.0, hr = 100),
        PlaybackPoint(0.001, 0.0, 10_000, ele = 20.0, speedMps = 7.0, hr = 120),
        PlaybackPoint(0.002, 0.0, 20_000, ele = 30.0, speedMps = 9.0, hr = 140),
    )

    @Test
    fun exactSample() {
        val s = Playback.sampleAt(pts, 10_000)!!
        assertEquals(0.001, s.lat, 1e-12)
        assertEquals(120, s.hr)
    }

    @Test
    fun midpointInterpolates() {
        val s = Playback.sampleAt(pts, 5_000)!!
        assertEquals(0.0005, s.lat, 1e-12)
        assertEquals(15.0, s.ele!!, 1e-9)
        assertEquals(6.0, s.speedMps!!, 1e-9)
        assertEquals(110, s.hr)
    }

    @Test
    fun clampsOutsideRange() {
        assertEquals(0.0, Playback.sampleAt(pts, -5)!!.lat, 1e-12)
        assertEquals(0.002, Playback.sampleAt(pts, 99_000)!!.lat, 1e-12)
    }

    @Test
    fun prefixEndsAtInterpolatedPosition() {
        val p = Playback.prefix(pts, 15_000)
        assertEquals(3, p.size)
        assertEquals(0.0015, p.last().lat, 1e-12)
    }

    @Test
    fun flyoverFramesCoverWholeRide() {
        val frames = Playback.flyover(pts, durationMs = 2_000, fps = 30)
        assertEquals(60, frames.size)
        assertTrue(frames.all { it.bearing in 0.0..360.0 })
        assertEquals(1.0, frames.last().progress, 1e-6)
        // Due north the whole way.
        assertEquals(0.0, minOf(frames[30].bearing, 360.0 - frames[30].bearing), 1.0)
    }

    @Test
    fun emptyIsNull() {
        assertEquals(null, Playback.sampleAt(emptyList(), 0))
    }
}
