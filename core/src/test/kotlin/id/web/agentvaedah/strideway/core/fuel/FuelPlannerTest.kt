package id.web.agentvaedah.strideway.core.fuel

import id.web.agentvaedah.strideway.core.model.Sport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FuelPlannerTest {
    @Test
    fun hotterMeansMoreFluid() {
        val mild = FuelPlanner.plan(Sport.RIDE, durationMin = 120, tempC = 22.0, weightKg = 70.0)
        val hot = FuelPlanner.plan(Sport.RIDE, durationMin = 120, tempC = 33.0, weightKg = 70.0)
        assertTrue(hot.fluidMlPerHour > mild.fluidMlPerHour)
        assertTrue(hot.fluidMlPerHour in 600..1000)
        assertTrue(mild.fluidMlPerHour in 400..700)
    }

    @Test
    fun carbsScaleWithDuration() {
        assertEquals(0, FuelPlanner.plan(Sport.RIDE, 45, 25.0, 70.0).carbGramsPerHour)
        assertEquals(30, FuelPlanner.plan(Sport.RIDE, 90, 25.0, 70.0).carbGramsPerHour)
        assertEquals(60, FuelPlanner.plan(Sport.RIDE, 180, 25.0, 70.0).carbGramsPerHour)
        assertEquals(80, FuelPlanner.plan(Sport.RIDE, 300, 25.0, 70.0).carbGramsPerHour)
    }

    @Test
    fun drinkIntervalShorterWhenHot() {
        val mild = FuelPlanner.plan(Sport.RIDE, 120, 20.0, 70.0)
        val hot = FuelPlanner.plan(Sport.RIDE, 120, 34.0, 70.0)
        assertTrue(hot.drinkEveryMin < mild.drinkEveryMin)
        assertTrue(hot.drinkEveryMin >= 10)
    }

    @Test
    fun electrolytesWhenLongOrHot() {
        assertTrue(FuelPlanner.plan(Sport.RIDE, 150, 30.0, 70.0).electrolytes)
        assertTrue(!FuelPlanner.plan(Sport.RIDE, 50, 20.0, 70.0).electrolytes)
    }

    @Test
    fun reminderSchedule() {
        val r = FuelReminders(drinkEveryMin = 20, eatEveryMin = 45)
        assertNull(r.due(movingMs = 10 * 60_000L))
        assertEquals(FuelReminder.DRINK, r.due(movingMs = 20 * 60_000L))
        assertNull(r.due(movingMs = 21 * 60_000L))
        assertEquals(FuelReminder.DRINK, r.due(movingMs = 40 * 60_000L))
        assertEquals(FuelReminder.EAT, r.due(movingMs = 45 * 60_000L))
        // Both due at once: eating takes precedence, drinking comes with it.
        val both = FuelReminders(drinkEveryMin = 15, eatEveryMin = 30)
        both.due(15 * 60_000L)
        assertEquals(FuelReminder.EAT, both.due(30 * 60_000L))
        assertNull(both.due(31 * 60_000L))
    }

    @Test
    fun eatDisabledWhenZero() {
        val r = FuelReminders(drinkEveryMin = 20, eatEveryMin = 0)
        assertEquals(FuelReminder.DRINK, r.due(20 * 60_000L))
        assertNull(r.due(40 * 60_000L - 1))
    }
}
