package id.web.agentvaedah.strideway.core.splash

/** A window on the splash clock, in seconds. */
data class Phase(val start: Double, val end: Double) {
    /** 0 before [start], 1 after [end], linear in between. */
    fun progress(t: Double): Double = ((t - start) / (end - start)).coerceIn(0.0, 1.0)
}

object Ease {
    fun outCubic(u: Double): Double { val v = 1 - u; return 1 - v * v * v }
    fun inOutCubic(u: Double): Double = if (u < 0.5) 4 * u * u * u else 1 - Math.pow(-2 * u + 2, 3.0) / 2
    fun inOutSine(u: Double): Double = -(Math.cos(Math.PI * u) - 1) / 2

    /** Overshoots a little before settling; used for the dot popping in. */
    fun outBack(u: Double): Double {
        val c1 = 1.70158
        val c3 = c1 + 1
        val v = u - 1
        return 1 + c3 * v * v * v + c1 * v * v
    }
}

/**
 * Five-second launch splash, a condensed cut of tools/motion-splash: the GPS dot pings, contours open
 * around it, it rides the first climb, then runs under the wordmark and lands as the dot of the i.
 */
object SplashTimeline {
    const val TOTAL = 5.0

    val PINGS = listOf(0.10, 0.30)
    val CONTOURS = Phase(0.15, 1.40)
    val RIDE = Phase(0.50, 2.70)
    val MAP_OUT = Phase(2.70, 3.20)
    val BASELINE = Phase(3.20, 3.90)
    val HOP = Phase(3.90, 4.35)
    val UNDERLINE_RETRACT = Phase(4.05, 4.60)
    val LANDING_SETTLE = Phase(HOP.end, 4.85)
    val TAGLINE = Phase(4.25, 4.65)
    val FADE_OUT = Phase(4.75, TOTAL)

    // Letters keep revealing for a moment after the dot reaches the end of the line.
    private val REVEAL_TAIL = Phase(BASELINE.end, BASELINE.end + 0.35)

    fun rideProgress(t: Double): Double = Ease.inOutSine(RIDE.progress(t))

    /** 0 on the line, 1 at the top of the hop. */
    fun hopLift(u: Double): Double = 4 * u * (1 - u)

    /** Edge behind which letters appear: the dot while it runs the line, then a short overrun. */
    fun revealX(t: Double, left: Double, right: Double): Double =
        if (t <= BASELINE.end) {
            left + (right - left) * Ease.inOutCubic(BASELINE.progress(t))
        } else {
            right + 0.5 * (right - left) * Ease.outCubic(REVEAL_TAIL.progress(t))
        }

    /** How far a letter has risen in (0..1); [em] is the wordmark font size in the same units. */
    fun letterReveal(revealX: Double, letterLeft: Double, em: Double = 190.0): Double {
        val lead = 0.37 * em
        val span = 1.58 * em
        return Ease.outCubic(((revealX - (letterLeft - lead)) / span).coerceIn(0.0, 1.0))
    }
}
