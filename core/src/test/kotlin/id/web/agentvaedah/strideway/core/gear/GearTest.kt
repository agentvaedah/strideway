package id.web.agentvaedah.strideway.core.gear

import id.web.agentvaedah.strideway.core.model.Sport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GearTest {
    private fun ride(atMs: Long, km: Double, sport: Sport = Sport.RIDE) = GearRide(sport, atMs, km * 1000)

    @Test
    fun allFifteenTypesHaveLabelsAndIntervals() {
        assertEquals(15, ComponentType.entries.size)
        assertEquals("Rantai", ComponentType.CHAIN.label)
        assertEquals(3000.0, ComponentType.CHAIN.defaultIntervalKm)
    }

    @Test
    fun countsRidesAfterInstallOnly() {
        val rides = listOf(ride(50, 100.0), ride(150, 40.0), ride(200, 30.0, Sport.RUN))
        val c = GearComponent(1, ComponentType.CHAIN, installMs = 100, kmAtInstall = 500.0, serviceIntervalKm = 3000.0)
        val s = Gear.status(c, emptyList(), rides)
        assertEquals(40.0, s.kmSinceService, 1e-9)
        assertEquals(540.0, s.totalKm, 1e-9)
    }

    @Test
    fun serviceResetsCounterButNotTotal() {
        val rides = listOf(ride(150, 40.0), ride(250, 10.0))
        val c = GearComponent(1, ComponentType.CHAIN, installMs = 100, kmAtInstall = 0.0, serviceIntervalKm = 100.0)
        val s = Gear.status(c, listOf(GearEvent(GearEventKind.SERVICED, 200)), rides)
        assertEquals(10.0, s.kmSinceService, 1e-9)
        assertEquals(50.0, s.totalKm, 1e-9)
    }

    @Test
    fun replacedResetsBoth() {
        val rides = listOf(ride(150, 40.0), ride(250, 10.0))
        val c = GearComponent(1, ComponentType.TIRES, installMs = 100, kmAtInstall = 0.0, serviceIntervalKm = 100.0)
        val s = Gear.status(c, listOf(GearEvent(GearEventKind.REPLACED, 200)), rides)
        assertEquals(10.0, s.totalKm, 1e-9)
    }

    @Test
    fun levels() {
        fun level(km: Double) = Gear.status(
            GearComponent(1, ComponentType.CHAIN, 0, 0.0, 100.0), emptyList(), listOf(ride(1, km)),
        ).level
        assertEquals(GearLevel.OK, level(84.9))
        assertEquals(GearLevel.DUE_SOON, level(85.0))
        assertEquals(GearLevel.OVERDUE, level(100.0))
    }

    @Test
    fun remainingAndOrdering() {
        val rides = listOf(ride(1, 90.0))
        val chain = Gear.status(GearComponent(1, ComponentType.CHAIN, 0, 0.0, 100.0), emptyList(), rides)
        val tire = Gear.status(GearComponent(2, ComponentType.TIRES, 0, 0.0, 1000.0), emptyList(), rides)
        assertEquals(10.0, chain.remainingKm, 1e-9)
        assertEquals(listOf(1L, 2L), Gear.byUrgency(listOf(tire, chain)).map { it.component.id })
    }
}
