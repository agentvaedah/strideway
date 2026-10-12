package id.web.agentvaedah.strideway.core.physio

import id.web.agentvaedah.strideway.core.model.Gender
import id.web.agentvaedah.strideway.core.model.Sport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.pow

class CaloriesTest {
    @Test
    fun keytelMaleMatchesPrdFormula() {
        assertEquals(14.2221, Calories.kcalPerMinute(150.0, 70.0, 30, Gender.MALE), 1e-3)
    }

    @Test
    fun keytelFemale() {
        assertEquals(9.8757, Calories.kcalPerMinute(150.0, 60.0, 30, Gender.FEMALE), 1e-3)
    }

    @Test
    fun neverNegative() {
        assertEquals(0.0, Calories.kcalPerMinute(30.0, 50.0, 20, Gender.MALE), 0.0)
    }

    @Test
    fun metFallbackForRide() {
        assertEquals(9.8, Calories.kcalPerMinuteMet(Sport.RIDE, 70.0), 1e-9)
    }
}

class ZonesTest {
    @Test
    fun heartRateZonesFromHrMax() {
        val z = Zones.heartRate(190, null)
        assertEquals(listOf(95, 114, 133, 152, 171), z.map { it.low })
        assertEquals(190, z.last().high)
    }

    @Test
    fun heartRateZonesKarvonen() {
        val z = Zones.heartRate(190, 60)
        assertEquals(listOf(125, 138, 151, 164, 177), z.map { it.low })
    }

    @Test
    fun zoneOfPicksHighestMatchingZone() {
        val z = Zones.heartRate(190, null)
        assertEquals(0, Zones.zoneOf(80, z))
        assertEquals(1, Zones.zoneOf(95, z))
        assertEquals(4, Zones.zoneOf(160, z))
        assertEquals(5, Zones.zoneOf(205, z))
    }

    @Test
    fun cogganSevenPowerZones() {
        val z = Zones.power(200)
        assertEquals(7, z.size)
        assertEquals(listOf(0, 111, 151, 181, 211, 241, 301), z.map { it.low })
        assertEquals(listOf(110, 150, 180, 210, 240, 300, Int.MAX_VALUE), z.map { it.high })
    }
}

class TrainingLoadTest {
    @Test
    fun banisterTrimpMale() {
        assertEquals(100.440, TrainingLoad.trimp(60.0, 150.0, 60, 190, Gender.MALE), 1e-2)
    }

    @Test
    fun banisterTrimpFemale() {
        assertEquals(113.517, TrainingLoad.trimp(60.0, 150.0, 60, 190, Gender.FEMALE), 1e-2)
    }

    @Test
    fun trimpIsZeroWhenHrAtRest() {
        assertEquals(0.0, TrainingLoad.trimp(60.0, 55.0, 60, 190, Gender.MALE), 0.0)
    }

    @Test
    fun fitnessAndFatigueAfterSevenDays() {
        val loads = (0L..6L).map { TrainingLoad.DailyLoad(it, 100.0) }
        val s = TrainingLoad.series(loads, 0, 6)
        assertEquals(7, s.size)
        assertEquals(15.5223, s.last().fitness, 1e-3)
        assertEquals(66.0083, s.last().fatigue, 1e-3)
        assertEquals(-50.486, s.last().form, 1e-3)
    }

    @Test
    fun sameDayLoadsAreSummed() {
        val a = TrainingLoad.series(listOf(TrainingLoad.DailyLoad(0, 50.0), TrainingLoad.DailyLoad(0, 50.0)), 0, 0)
        val b = TrainingLoad.series(listOf(TrainingLoad.DailyLoad(0, 100.0)), 0, 0)
        assertEquals(b.single().fitness, a.single().fitness, 1e-9)
    }

    @Test
    fun windowStartingAfterHistoryKeepsDecayedValues() {
        val s = TrainingLoad.series(listOf(TrainingLoad.DailyLoad(0, 100.0)), 10, 10)
        assertEquals(1, s.size)
        val expected = 100.0 / 7 * (6.0 / 7).pow(10)
        assertEquals(expected, s.single().fatigue, 1e-9)
    }
}

class PowerEstimatorTest {
    @Test
    fun flatRoad30Kmh() {
        assertEquals(146.115, PowerEstimator.watts(30 / 3.6, 0.0, 80.0), 1e-2)
    }

    @Test
    fun climbFivePercent() {
        assertEquals(193.743, PowerEstimator.watts(15 / 3.6, 5.0, 80.0), 1e-2)
    }

    @Test
    fun steepDescentIsZeroNotNegative() {
        assertEquals(0.0, PowerEstimator.watts(10.0, -10.0, 80.0), 0.0)
    }
}
