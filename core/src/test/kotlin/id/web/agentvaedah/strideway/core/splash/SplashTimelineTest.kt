package id.web.agentvaedah.strideway.core.splash

import id.web.agentvaedah.strideway.core.splash.SplashTimeline.BASELINE
import id.web.agentvaedah.strideway.core.splash.SplashTimeline.CONTOURS
import id.web.agentvaedah.strideway.core.splash.SplashTimeline.FADE_OUT
import id.web.agentvaedah.strideway.core.splash.SplashTimeline.HOP
import id.web.agentvaedah.strideway.core.splash.SplashTimeline.LANDING_SETTLE
import id.web.agentvaedah.strideway.core.splash.SplashTimeline.MAP_OUT
import id.web.agentvaedah.strideway.core.splash.SplashTimeline.RIDE
import id.web.agentvaedah.strideway.core.splash.SplashTimeline.TAGLINE
import id.web.agentvaedah.strideway.core.splash.SplashTimeline.TOTAL
import id.web.agentvaedah.strideway.core.splash.SplashTimeline.UNDERLINE_RETRACT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SplashTimelineTest {
    private val all = listOf(CONTOURS, RIDE, MAP_OUT, BASELINE, HOP, UNDERLINE_RETRACT, LANDING_SETTLE, TAGLINE, FADE_OUT)

    @Test
    fun `splash lasts five seconds and ends with the fade out`() {
        assertEquals(5.0, TOTAL, 1e-9)
        assertEquals(TOTAL, FADE_OUT.end, 1e-9)
    }

    @Test
    fun `every phase is non empty and inside the splash`() {
        all.forEach {
            assertTrue(it.start < it.end, "$it is empty")
            assertTrue(it.start >= 0.0 && it.end <= TOTAL, "$it leaves 0..$TOTAL")
        }
    }

    @Test
    fun `story beats hand over without gaps`() {
        assertEquals(RIDE.end, MAP_OUT.start, 1e-9)
        assertEquals(MAP_OUT.end, BASELINE.start, 1e-9)
        assertEquals(BASELINE.end, HOP.start, 1e-9)
        assertEquals(4.35, HOP.end, 1e-9)
        assertTrue(TAGLINE.end <= FADE_OUT.start)
    }

    @Test
    fun `underline retracts after the dot leaves it and the landing settles before the fade`() {
        assertTrue(UNDERLINE_RETRACT.start > HOP.start)
        assertEquals(HOP.end, LANDING_SETTLE.start, 1e-9)
        assertTrue(LANDING_SETTLE.end <= FADE_OUT.end)
    }

    @Test
    fun `phase progress clamps outside its window`() {
        val p = Phase(1.0, 3.0)
        assertEquals(0.0, p.progress(0.2), 1e-9)
        assertEquals(0.5, p.progress(2.0), 1e-9)
        assertEquals(1.0, p.progress(4.0), 1e-9)
    }

    @Test
    fun `easing curves start at zero and land on one`() {
        listOf(Ease::outCubic, Ease::inOutCubic, Ease::inOutSine, Ease::outBack).forEach { f ->
            assertEquals(0.0, f(0.0), 1e-9)
            assertEquals(1.0, f(1.0), 1e-9)
        }
    }

    @Test
    fun `outBack overshoots before settling`() {
        assertTrue((1..99).any { Ease.outBack(it / 100.0) > 1.0 })
    }

    @Test
    fun `hop lifts to its apex halfway and lands back on the line`() {
        assertEquals(0.0, SplashTimeline.hopLift(0.0), 1e-9)
        assertEquals(1.0, SplashTimeline.hopLift(0.5), 1e-9)
        assertEquals(0.0, SplashTimeline.hopLift(1.0), 1e-9)
    }

    @Test
    fun `a letter reveals as the dot passes it`() {
        val left = 300.0
        assertEquals(0.0, SplashTimeline.letterReveal(revealX = 100.0, letterLeft = left), 1e-9)
        assertEquals(1.0, SplashTimeline.letterReveal(revealX = 900.0, letterLeft = left), 1e-9)
        val steps = (0..80).map { SplashTimeline.letterReveal(revealX = 150.0 + it * 8.0, letterLeft = left) }
        assertTrue(steps.zipWithNext().all { (a, b) -> b >= a }, "reveal must never go backwards")
    }

    @Test
    fun `dot runs the baseline from left to right`() {
        assertEquals(40.0, SplashTimeline.revealX(BASELINE.start, left = 40.0, right = 1000.0), 1e-9)
        assertEquals(1000.0, SplashTimeline.revealX(BASELINE.end, left = 40.0, right = 1000.0), 1e-9)
    }

    @Test
    fun `every letter is fully drawn by the time the dot lands`() {
        val left = 40.0
        val right = 1000.0
        val lastLetterLeft = right - 60.0
        val x = SplashTimeline.revealX(HOP.end, left, right)
        assertEquals(1.0, SplashTimeline.letterReveal(x, lastLetterLeft), 1e-9)
    }

    @Test
    fun `ride progress runs from start to end of the ride`() {
        assertEquals(0.0, SplashTimeline.rideProgress(0.0), 1e-9)
        assertEquals(1.0, SplashTimeline.rideProgress(RIDE.end), 1e-9)
    }
}
