package id.web.agentvaedah.strideway.core.physio

import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin

/** Steady-state cycling power from speed and grade, used when no power meter is paired. */
object PowerEstimator {
    private const val G = 9.80665

    fun watts(
        speedMps: Double,
        gradePercent: Double,
        totalMassKg: Double,
        cda: Double = 0.32,
        crr: Double = 0.005,
        rho: Double = 1.225,
    ): Double {
        if (speedMps <= 0) return 0.0
        val theta = atan(gradePercent / 100.0)
        val gravity = totalMassKg * G * sin(theta) * speedMps
        val rolling = totalMassKg * G * cos(theta) * crr * speedMps
        val aero = 0.5 * rho * cda * speedMps * speedMps * speedMps
        return (gravity + rolling + aero).coerceAtLeast(0.0)
    }
}
