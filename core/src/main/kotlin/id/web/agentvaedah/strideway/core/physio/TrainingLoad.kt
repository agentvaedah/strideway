package id.web.agentvaedah.strideway.core.physio

import id.web.agentvaedah.strideway.core.model.Gender
import kotlin.math.exp

object TrainingLoad {
    private const val FITNESS_DAYS = 42.0
    private const val FATIGUE_DAYS = 7.0

    /** Banister TRIMP using average heart rate. */
    fun trimp(durationMin: Double, avgHr: Double, hrRest: Int, hrMax: Int, gender: Gender): Double {
        if (hrMax <= hrRest || durationMin <= 0) return 0.0
        val ratio = ((avgHr - hrRest) / (hrMax - hrRest)).coerceIn(0.0, 1.0)
        val weight = when (gender) {
            Gender.MALE -> 0.64 * exp(1.92 * ratio)
            Gender.FEMALE -> 0.86 * exp(1.67 * ratio)
        }
        return durationMin * ratio * weight
    }

    data class DailyLoad(val epochDay: Long, val load: Double)

    data class FitnessPoint(val epochDay: Long, val fitness: Double, val fatigue: Double) {
        val form: Double get() = fitness - fatigue
    }

    /** Exponentially weighted fitness (42 d) and fatigue (7 d), one point per day in [fromDay, toDay]. */
    fun series(loads: List<DailyLoad>, fromDay: Long, toDay: Long): List<FitnessPoint> {
        if (toDay < fromDay) return emptyList()
        val byDay = loads.groupBy { it.epochDay }.mapValues { (_, v) -> v.sumOf { it.load } }
        val start = minOf(byDay.keys.minOrNull() ?: fromDay, fromDay)
        var fitness = 0.0
        var fatigue = 0.0
        val out = ArrayList<FitnessPoint>((toDay - fromDay + 1).toInt())
        for (day in start..toDay) {
            val load = byDay[day] ?: 0.0
            fitness += (load - fitness) / FITNESS_DAYS
            fatigue += (load - fatigue) / FATIGUE_DAYS
            if (day >= fromDay) out += FitnessPoint(day, fitness, fatigue)
        }
        return out
    }
}
