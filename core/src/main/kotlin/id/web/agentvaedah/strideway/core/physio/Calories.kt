package id.web.agentvaedah.strideway.core.physio

import id.web.agentvaedah.strideway.core.model.Gender
import id.web.agentvaedah.strideway.core.model.Sport

object Calories {
    /** Keytel et al. 2005, energy expenditure from heart rate without VO2max. */
    fun kcalPerMinute(hr: Double, weightKg: Double, age: Int, gender: Gender): Double {
        val kj = when (gender) {
            Gender.MALE -> -55.0969 + 0.6309 * hr + 0.1988 * weightKg + 0.2017 * age
            Gender.FEMALE -> -20.4022 + 0.4472 * hr - 0.1263 * weightKg + 0.074 * age
        }
        return (kj / 4.184).coerceAtLeast(0.0)
    }

    /** Used while no heart rate sensor is connected. */
    fun kcalPerMinuteMet(sport: Sport, weightKg: Double): Double {
        val met = when (sport) {
            Sport.RIDE -> 8.0
            Sport.RUN -> 9.8
        }
        return met * 3.5 * weightKg / 200.0
    }
}
