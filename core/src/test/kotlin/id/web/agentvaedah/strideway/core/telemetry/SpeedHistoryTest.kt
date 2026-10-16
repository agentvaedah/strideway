package id.web.agentvaedah.strideway.core.telemetry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpeedHistoryTest {
    @Test
    fun `keeps only the last window of samples`() {
        val h = SpeedHistory(windowMs = 60_000)
        for (t in 0..120 step 1) h.add(t * 1000L, t.toDouble())
        val s = h.samples()
        assertEquals(61, s.size)
        assertEquals(60.0, s.first(), 1e-9)
        assertEquals(120.0, s.last(), 1e-9)
    }

    @Test
    fun `trend compares now with ten seconds ago`() {
        val h = SpeedHistory()
        h.add(0, 20.0)
        h.add(5_000, 22.0)
        h.add(10_000, 25.0)
        assertEquals(5.0, h.trend(10_000)!!, 1e-9)
    }

    @Test
    fun `no trend until ten seconds of history exist`() {
        val h = SpeedHistory()
        h.add(0, 20.0)
        h.add(4_000, 22.0)
        assertNull(h.trend(4_000))
    }

    @Test
    fun `samples older than the window never come back after a pause gap`() {
        val h = SpeedHistory(windowMs = 60_000)
        h.add(0, 30.0)
        h.add(200_000, 10.0)
        assertEquals(listOf(10.0), h.samples())
    }

    @Test
    fun `out of order timestamps are ignored`() {
        val h = SpeedHistory()
        h.add(10_000, 20.0)
        h.add(5_000, 99.0)
        assertTrue(99.0 !in h.samples())
    }
}
