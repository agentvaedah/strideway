package id.web.agentvaedah.strideway.core.telemetry

import id.web.agentvaedah.strideway.core.geo.Geo
import id.web.agentvaedah.strideway.core.model.Athlete
import id.web.agentvaedah.strideway.core.model.LatLon
import id.web.agentvaedah.strideway.core.model.Sport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AutoPauseTest {
    @Test
    fun pausesOnlyAfterMoreThanThreeSecondsSlow() {
        val ap = AutoPause()
        assertFalse(ap.update(2.0, 0))
        assertFalse(ap.update(2.0, 3_000))
        assertTrue(ap.update(2.0, 3_001))
    }

    @Test
    fun resumesImmediatelyWhenMoving() {
        val ap = AutoPause()
        ap.update(0.0, 0)
        ap.update(0.0, 5_000)
        assertFalse(ap.update(2.5, 5_500))
    }

    @Test
    fun shortSlowdownDoesNotPause() {
        val ap = AutoPause()
        ap.update(1.0, 0)
        ap.update(20.0, 2_000)
        assertFalse(ap.update(1.0, 4_500))
    }
}

class DeadReckoningTest {
    private val start = LatLon(-6.2, 106.8)

    @Test
    fun nullWhileGpsIsFresh() {
        val dr = DeadReckoning()
        dr.onGpsFix(start, 10.0, 90.0, 0)
        assertNull(dr.estimate(1_000))
    }

    @Test
    fun propagatesAlongHeadingWhenStale() {
        val dr = DeadReckoning()
        dr.onGpsFix(start, 10.0, 90.0, 0)
        val est = dr.estimate(3_000)
        assertNotNull(est)
        assertEquals(30.0, Geo.distanceM(start, est!!), 0.1)
        assertEquals(90.0, Geo.bearingDeg(start, est), 0.1)
    }

    @Test
    fun givesUpAfterMaxDuration() {
        val dr = DeadReckoning(maxDurationMs = 10_000)
        dr.onGpsFix(start, 10.0, 90.0, 0)
        assertNull(dr.estimate(10_001))
    }

    @Test
    fun brakingSlowsEstimate() {
        val dr = DeadReckoning()
        dr.onGpsFix(start, 10.0, 0.0, 0)
        var t = 1_600L
        dr.estimate(t)
        dr.onForwardAcceleration(-2.0, t)
        while (t < 6_600) {
            t += 20
            dr.onForwardAcceleration(-2.0, t)
        }
        val est = dr.estimate(8_000)!!
        // 16 m before braking, 25 m while braking from 10 m/s to 0, then stopped.
        assertEquals(41.0, Geo.distanceM(start, est), 1.0)
    }
}

class SessionAccumulatorTest {
    private val athlete = Athlete()
    private val origin = LatLon(-6.2, 106.8)

    private fun ride(acc: SessionAccumulator, fromMs: Long, toMs: Long, speedMps: Double, startPos: LatLon): LatLon {
        var pos = startPos
        var t = fromMs
        while (t < toMs) {
            t += 500
            pos = Geo.destination(pos, 0.0, speedMps * 0.5)
            acc.onLocation(t, pos, 10.0, speedMps, interpolated = false)
        }
        return pos
    }

    @Test
    fun distanceAndAverageSpeedWhileMoving() {
        val acc = SessionAccumulator(athlete, Sport.RIDE, 0)
        acc.onLocation(0, origin, 10.0, 0.0, false)
        ride(acc, 0, 60_000, 10.0, origin)
        val m = acc.tick(60_000)
        assertEquals(600.0, m.distanceM, 1.0)
        assertEquals(60_000L, m.movingMs)
        assertEquals(36.0, m.avgSpeedKmh, 0.1)
    }

    @Test
    fun autoPauseStopsMovingTime() {
        val acc = SessionAccumulator(athlete, Sport.RIDE, 0)
        acc.onLocation(0, origin, 10.0, 0.0, false)
        val p = ride(acc, 0, 10_000, 10.0, origin)
        var t = 10_000L
        while (t < 30_000) {
            t += 500
            acc.onLocation(t, p, 10.0, 0.0, false)
        }
        val m = acc.tick(30_000)
        assertTrue(m.autoPaused)
        assertEquals(14_000.0, m.movingMs.toDouble(), 600.0)
        assertEquals(30_000L, m.elapsedMs)
    }

    @Test
    fun manualPauseFreezesDistance() {
        val acc = SessionAccumulator(athlete, Sport.RIDE, 0)
        acc.onLocation(0, origin, 10.0, 0.0, false)
        val p = ride(acc, 0, 10_000, 10.0, origin)
        acc.setManualPause(true, 10_000)
        ride(acc, 10_000, 20_000, 10.0, p)
        val m = acc.tick(20_000)
        assertEquals(100.0, m.distanceM, 1.0)
        assertEquals(10_000L, m.movingMs)
    }

    @Test
    fun rejectsGpsJumps() {
        val acc = SessionAccumulator(athlete, Sport.RIDE, 0)
        acc.onLocation(0, origin, null, 0.0, false)
        acc.onLocation(500, Geo.destination(origin, 0.0, 500.0), null, null, false)
        assertEquals(0.0, acc.tick(500).distanceM, 0.0)
    }

    @Test
    fun keytelCaloriesWithHeartRate() {
        val acc = SessionAccumulator(athlete, Sport.RIDE, 0)
        acc.onLocation(0, origin, 10.0, 0.0, false)
        var pos = origin
        var t = 0L
        while (t < 60_000) {
            t += 500
            acc.onHeartRate(150, t)
            pos = Geo.destination(pos, 0.0, 5.0)
            acc.onLocation(t, pos, 10.0, 10.0, false)
        }
        val m = acc.tick(60_000)
        assertEquals(14.2, m.calories, 0.1)
        assertEquals(150, m.avgHeartRate)
        assertEquals(150, m.heartRate)
    }

    @Test
    fun staleHeartRateIsHidden() {
        val acc = SessionAccumulator(athlete, Sport.RIDE, 0)
        acc.onHeartRate(140, 0)
        assertEquals(140, acc.tick(5_000).heartRate)
        assertNull(acc.tick(5_001).heartRate)
    }
}
