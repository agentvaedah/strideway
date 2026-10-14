package id.web.agentvaedah.strideway.core.training

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate

class WorkoutLibraryTest {
    @Test
    fun everyLevelHasSessions() {
        Level.entries.forEach { lvl -> assertTrue(WorkoutLibrary.forLevel(lvl).size >= 6, "level $lvl") }
    }

    @Test
    fun idsUniqueAndLookupWorks() {
        val ids = WorkoutLibrary.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertNotNull(WorkoutLibrary.byId("ftp20"))
    }

    @Test
    fun scalingKeepsWarmupAndScalesWork() {
        val base = WorkoutLibrary.byId("sweetspot")!!
        val beginner = base.scaled(Level.PEMULA)
        val elite = base.scaled(Level.ELITE)
        assertTrue(elite.durationS > beginner.durationS)
        assertEquals(base.steps.first().durationS, beginner.steps.first().durationS)
    }

    @Test
    fun targetsResolveFromFtpAndHr() {
        val step = Step(60, Target.PowerPct(0.9, 0.95), label = "Sweet spot")
        assertEquals(180..190, step.target.watts(200))
        val hr = Step(60, Target.HrZone(2), label = "Z2")
        assertNull(hr.target.watts(200))
    }
}

class WorkoutRunnerTest {
    private val w = Workout(
        "t", "Tes", Level.MENENGAH, "uji",
        listOf(Step(60, Target.Free, label = "Pemanasan"), Step(120, Target.PowerPct(1.0, 1.05), label = "Kerja"), Step(60, Target.Free, label = "Pendinginan")),
    )

    @Test
    fun stepsAdvanceOnMovingTime() {
        val r = WorkoutRunner(w, ftp = 200)
        assertEquals(0, r.update(30_000, power = null).index)
        val s = r.update(61_000, power = 210)
        assertEquals(1, s.index)
        assertEquals(119, s.remainingS)
        assertEquals(200..210, s.targetWatts)
    }

    @Test
    fun complianceAgainstTarget() {
        val r = WorkoutRunner(w, ftp = 200)
        assertEquals(Compliance.UNDER, r.update(70_000, 150).compliance)
        assertEquals(Compliance.IN, r.update(80_000, 205).compliance)
        assertEquals(Compliance.OVER, r.update(90_000, 260).compliance)
        assertEquals(Compliance.NONE, r.update(10_000, null).compliance)
    }

    @Test
    fun stepChangeAnnouncedOnceAndFinish() {
        val r = WorkoutRunner(w, ftp = 200)
        r.update(0, null)
        val change = r.update(61_000, null).events
        assertEquals(1, change.count { it is WorkoutEvent.StepStarted })
        assertTrue(r.update(62_000, null).events.isEmpty())
        val done = r.update(241_000, null)
        assertTrue(done.finished)
        assertEquals(1, done.events.count { it is WorkoutEvent.Finished })
    }

    @Test
    fun announcementText() {
        val e = WorkoutEvent.StepStarted(1, 3, w.steps[1], 200..210)
        assertEquals("Interval 2 dari 3, Kerja, target 200 sampai 210 watt, 2 menit", e.text())
    }
}

class FtpTestCalcTest {
    @Test
    fun twentyMinuteTest() {
        assertEquals(238, FtpTest.from20Min(avgPower = 250.0))
    }

    @Test
    fun rampTest() {
        assertEquals(225, FtpTest.fromRamp(best1MinPower = 300.0))
    }

    @Test
    fun best20MinFromSamples() {
        val samples = List(1500) { i -> if (i in 200 until 1400) 250 else 100 }
        assertEquals(250.0, FtpTest.bestAverage(samples, 1200)!!, 1e-9)
        assertNull(FtpTest.bestAverage(List(100) { 200 }, 1200))
    }
}

class TrainingPlanTest {
    private val start = LocalDate.of(2026, 10, 19) // Monday

    @Test
    fun ruleBasedPlanHasPhasesAndTaper() {
        val plan = PlanGenerator.generate(
            goal = "Gran Fondo 100 km",
            start = start,
            event = start.plusWeeks(8).plusDays(5),
            days = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY),
            level = Level.MENENGAH,
        )
        assertEquals(9, plan.weeks.size)
        assertEquals(Phase.BASE, plan.weeks.first().phase)
        assertEquals(Phase.TAPER, plan.weeks.last().phase)
        assertTrue(plan.weeks.all { w -> w.sessions.all { it.date.dayOfWeek in setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY) } })
        assertTrue(plan.weeks.flatMap { it.sessions }.all { WorkoutLibrary.byId(it.workoutId) != null })
        assertTrue(plan.weeks[3].sessions.any { it.workoutId == "ftp20" || it.workoutId == "ramp" } || plan.weeks.any { w -> w.sessions.any { it.workoutId == "ftp20" } })
    }

    @Test
    fun longestSessionOnWeekend() {
        val plan = PlanGenerator.generate("Tetap bugar", start, start.plusWeeks(4), setOf(DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY), Level.PEMULA)
        val sunday = plan.weeks[1].sessions.first { it.date.dayOfWeek == DayOfWeek.SUNDAY }
        assertEquals("longride", sunday.workoutId)
    }

    @Test
    fun aiJsonParsesAndRejectsUnknownWorkouts() {
        val json = """{"weeks":[{"phase":"BUILD","sessions":[{"day":"TUESDAY","workout":"sweetspot"},{"day":"FRIDAY","workout":"dancing"}]}]}"""
        val plan = PlanGenerator.parseAi(json, "Target", start)!!
        assertEquals(1, plan.weeks.single().sessions.size)
        assertEquals(start.plusDays(1), plan.weeks.single().sessions.single().date)
        assertNull(PlanGenerator.parseAi("not json", "x", start))
    }

    @Test
    fun noDaysMeansNoSessions() {
        val plan = PlanGenerator.generate("x", start, start.plusWeeks(2), emptySet(), Level.LANJUT)
        assertFalse(plan.weeks.any { it.sessions.isNotEmpty() })
    }
}
