package id.web.agentvaedah.strideway.core.training

import kotlin.math.roundToInt

enum class Compliance { NONE, UNDER, IN, OVER }

sealed interface WorkoutEvent {
    fun text(): String

    data class StepStarted(val index: Int, val total: Int, val step: Step, val watts: IntRange?) : WorkoutEvent {
        override fun text(): String {
            val dur = if (step.durationS >= 60) "${(step.durationS / 60.0).roundToInt()} menit" else "${step.durationS} detik"
            val target = watts?.let { if (it.first == it.last) ", target ${it.first} watt" else ", target ${it.first} sampai ${it.last} watt" }.orEmpty()
            return "Interval ${index + 1} dari $total, ${step.label}$target, $dur"
        }
    }

    data object Finished : WorkoutEvent {
        override fun text(): String = "Sesi latihan selesai. Kerja bagus"
    }
}

data class WorkoutState(
    val index: Int,
    val step: Step,
    val remainingS: Int,
    val targetWatts: IntRange?,
    val compliance: Compliance,
    val finished: Boolean,
    val events: List<WorkoutEvent>,
)

/** Steps a structured session along moving time (pauses do not eat into an interval) and judges power against the target. */
class WorkoutRunner(private val workout: Workout, private val ftp: Int) {
    private var lastIndex = -1
    private var finishedAnnounced = false
    private val ends = workout.steps.runningFold(0) { acc, s -> acc + s.durationS }.drop(1)

    fun update(movingMs: Long, power: Int?): WorkoutState {
        val t = (movingMs / 1000).toInt()
        val idx = ends.indexOfFirst { t < it }.let { if (it < 0) workout.steps.lastIndex else it }
        val finished = t >= ends.last()
        val step = workout.steps[idx]
        val watts = step.target.watts(ftp)
        val events = ArrayList<WorkoutEvent>()
        if (!finished && idx != lastIndex) {
            if (lastIndex >= 0) events += WorkoutEvent.StepStarted(idx, workout.steps.size, step, watts)
            lastIndex = idx
        }
        if (finished && !finishedAnnounced) {
            finishedAnnounced = true
            events += WorkoutEvent.Finished
        }
        val compliance = when {
            power == null || watts == null -> Compliance.NONE
            // A few watts of slack either side so a steady effort does not flicker between states.
            power < watts.first - 5 -> Compliance.UNDER
            power > watts.last + 5 -> Compliance.OVER
            else -> Compliance.IN
        }
        return WorkoutState(idx, step, (ends[idx] - t).coerceAtLeast(0), watts, compliance, finished, events)
    }
}

object FtpTest {
    /** Classic 20-minute test: FTP is 95% of the average power. */
    fun from20Min(avgPower: Double): Int = (avgPower * 0.95).roundToInt()

    /** Ramp test: FTP is 75% of the best one-minute power. */
    fun fromRamp(best1MinPower: Double): Int = (best1MinPower * 0.75).roundToInt()

    /** Best average over [windowS] one-second samples; null if the ride is shorter. */
    fun bestAverage(perSecond: List<Int>, windowS: Int): Double? {
        if (perSecond.size < windowS || windowS <= 0) return null
        var sum = perSecond.take(windowS).sumOf { it.toLong() }
        var best = sum
        for (i in windowS until perSecond.size) {
            sum += perSecond[i] - perSecond[i - windowS]
            if (sum > best) best = sum
        }
        return best.toDouble() / windowS
    }
}
