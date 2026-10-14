package id.web.agentvaedah.strideway.core.records

import id.web.agentvaedah.strideway.core.geo.Geo
import id.web.agentvaedah.strideway.core.model.LatLon
import id.web.agentvaedah.strideway.core.model.Sport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BestEffortsTest {
    private val origin = LatLon(-6.2, 106.8)

    /** Points every [stepM] metres along a north line; [stepS] gives the seconds taken to reach point i. */
    private fun track(totalM: Int, stepM: Int, stepS: (Int) -> Long): List<EffortPoint> {
        var t = 0L
        return (0..totalM step stepM).mapIndexed { i, d ->
            if (i > 0) t += stepS(i) * 1000
            val p = Geo.destination(origin, 0.0, d.toDouble())
            EffortPoint(p.lat, p.lon, t)
        }
    }

    @Test
    fun constantSpeedGivesExactTime() {
        // 10 m every 2 s = 5 m/s, so 5 km in 1000 s.
        val e = BestEfforts.fastest(track(12_000, 10) { 2 }, 5_000.0)!!
        assertEquals(1000.0, e.seconds, 2.0)
    }

    @Test
    fun tooShortIsNull() {
        assertNull(BestEfforts.fastest(track(4_000, 10) { 2 }, 5_000.0))
    }

    @Test
    fun findsFastMiddleSegment() {
        // First 3 km and last 3 km slow (4 s per 10 m), the middle 5 km fast (1 s per 10 m).
        val pts = track(11_000, 10) { i -> if (i * 10 in 3_001..8_000) 1 else 4 }
        val e = BestEfforts.fastest(pts, 5_000.0)!!
        assertEquals(500.0, e.seconds, 5.0)
        assertTrue(e.startIdx in 290..310, "start ${e.startIdx}")
    }

    @Test
    fun emptyOrSinglePoint() {
        assertNull(BestEfforts.fastest(emptyList(), 1_000.0))
        assertNull(BestEfforts.fastest(listOf(EffortPoint(0.0, 0.0, 0)), 1_000.0))
    }

    @Test
    fun targetsPerSport() {
        assertEquals(listOf(5_000.0, 10_000.0, 20_000.0, 40_000.0), BestEfforts.targets(Sport.RIDE).map { it.meters })
        assertEquals(listOf(1_000.0, 5_000.0, 10_000.0, 21_097.5), BestEfforts.targets(Sport.RUN).map { it.meters })
    }

    @Test
    fun computeAllReturnsOnlyReachableTargets() {
        val all = BestEfforts.computeAll(track(12_000, 10) { 2 }, Sport.RIDE)
        assertEquals(setOf(RecordKey.RIDE_5K, RecordKey.RIDE_10K), all.keys)
    }
}

class PersonalRecordsTest {
    private fun act(id: Long, start: Long, km: Double, gain: Double = 0.0, movingS: Long = 3600, sport: Sport = Sport.RIDE) =
        RecordActivity(id, sport, start, km * 1000, gain, movingS * 1000)

    @Test
    fun firstActivityEarnsEveryApplicableRecord() {
        val a = act(1, 0, 30.0, 300.0)
        val efforts = listOf(EffortRow(1, RecordKey.RIDE_5K, 600.0), EffortRow(1, RecordKey.RIDE_10K, 1250.0))
        val keys = PersonalRecords.compute(listOf(a), efforts).badgesFor(1).toSet()
        assertEquals(
            setOf(RecordKey.RIDE_LONGEST, RecordKey.RIDE_ELEVATION, RecordKey.RIDE_AVG_SPEED, RecordKey.RIDE_5K, RecordKey.RIDE_10K),
            keys,
        )
    }

    @Test
    fun slowerSecondEarnsNothing() {
        val a = act(1, 0, 30.0, 300.0)
        val b = act(2, 1, 20.0, 100.0)
        val efforts = listOf(EffortRow(1, RecordKey.RIDE_5K, 600.0), EffortRow(2, RecordKey.RIDE_5K, 700.0))
        assertTrue(PersonalRecords.compute(listOf(a, b), efforts).badgesFor(2).isEmpty())
    }

    @Test
    fun fasterEffortTakesRecord() {
        val efforts = listOf(EffortRow(1, RecordKey.RIDE_5K, 600.0), EffortRow(2, RecordKey.RIDE_5K, 550.0))
        val pr = PersonalRecords.compute(listOf(act(1, 0, 6.0), act(2, 1, 6.0)), efforts)
        assertEquals(2L, pr.holders.getValue(RecordKey.RIDE_5K).activityId)
    }

    @Test
    fun ridesAndRunsAreSeparate() {
        val pr = PersonalRecords.compute(listOf(act(1, 0, 30.0), act(2, 1, 10.0, sport = Sport.RUN)), emptyList())
        assertEquals(1L, pr.holders.getValue(RecordKey.RIDE_LONGEST).activityId)
        assertEquals(2L, pr.holders.getValue(RecordKey.RUN_LONGEST).activityId)
    }

    @Test
    fun tieGoesToEarlierActivity() {
        val pr = PersonalRecords.compute(listOf(act(2, 10, 30.0), act(1, 5, 30.0)), emptyList())
        assertEquals(1L, pr.holders.getValue(RecordKey.RIDE_LONGEST).activityId)
    }

    @Test
    fun avgSpeedNeedsMinimumDistance() {
        val blip = act(1, 0, 0.3, movingS = 20)
        val real = act(2, 1, 20.0, movingS = 3600)
        assertEquals(2L, PersonalRecords.compute(listOf(blip, real), emptyList()).holders.getValue(RecordKey.RIDE_AVG_SPEED).activityId)
    }

    @Test
    fun deletedHolderPromotesNext() {
        val list = listOf(act(1, 0, 30.0), act(2, 1, 20.0))
        val afterDelete = PersonalRecords.compute(list.filter { it.id != 1L }, emptyList())
        assertEquals(2L, afterDelete.holders.getValue(RecordKey.RIDE_LONGEST).activityId)
    }

    @Test
    fun labels() {
        assertEquals("5 km tercepat", RecordKey.RIDE_5K.label)
        assertEquals("Ride terjauh", RecordKey.RIDE_LONGEST.label)
    }
}
