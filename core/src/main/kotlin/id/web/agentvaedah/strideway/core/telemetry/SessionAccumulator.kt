package id.web.agentvaedah.strideway.core.telemetry

import id.web.agentvaedah.strideway.core.geo.ElevationGain
import id.web.agentvaedah.strideway.core.geo.Geo
import id.web.agentvaedah.strideway.core.geo.GradeWindow
import id.web.agentvaedah.strideway.core.model.Athlete
import id.web.agentvaedah.strideway.core.model.LatLon
import id.web.agentvaedah.strideway.core.model.Sport
import id.web.agentvaedah.strideway.core.physio.Calories
import id.web.agentvaedah.strideway.core.physio.PowerEstimator
import kotlin.math.roundToInt

/**
 * Turns raw GPS, sensor and barometer samples into live ride/run metrics.
 * Not thread-safe: the recording service feeds it from a single coroutine.
 */
class SessionAccumulator(
    private val athlete: Athlete,
    private val sport: Sport,
    private val startMs: Long,
) {
    private val autoPause = AutoPause()
    private val gain = ElevationGain()
    private val grade = GradeWindow()

    private var clockMs = startMs
    private var movingMs = 0L
    private var manualPaused = false

    private var lastPos: LatLon? = null
    private var lastPosMs = 0L
    private var lastInterpolated = false
    private var distanceM = 0.0
    private var speedMps = 0.0
    private var maxSpeedMps = 0.0
    private var gpsAltitude: Double? = null
    private var baroAltitude: Double? = null
    private var baroMs = NEVER
    private var gradePct: Double? = null

    private var hr: Int? = null
    private var hrMs = NEVER
    private var hrSum = 0L
    private var hrCount = 0
    private var hrMax: Int? = null
    private var cadence: Int? = null
    private var cadenceMs = NEVER
    private var power: Int? = null
    private var powerMs = NEVER
    private var powerSum = 0L
    private var powerCount = 0

    private var calories = 0.0

    private val paused: Boolean get() = manualPaused || autoPause.isPaused

    fun onLocation(timeMs: Long, pos: LatLon, altitudeM: Double?, speedMps: Double?, interpolated: Boolean) {
        advanceClock(timeMs)
        val prev = lastPos
        val dt = (timeMs - lastPosMs) / 1000.0
        val step = if (prev != null) Geo.distanceM(prev, pos) else 0.0
        if (prev != null && dt > 0 && step / dt > MAX_PLAUSIBLE_MPS) return

        val speed = when {
            speedMps != null -> speedMps
            prev != null && dt > 0 -> step / dt
            else -> 0.0
        }.coerceIn(0.0, MAX_PLAUSIBLE_MPS)

        this.speedMps = speed
        lastInterpolated = interpolated
        if (altitudeM != null && !interpolated) gpsAltitude = altitudeM

        val wasPaused = paused
        autoPause.update(speed * 3.6, timeMs)

        if (!wasPaused && !paused) {
            distanceM += step
            if (speed > maxSpeedMps) maxSpeedMps = speed
            currentAltitude(timeMs)?.let { ele ->
                gain.add(ele)
                gradePct = grade.add(distanceM, ele)
            }
        }
        lastPos = pos
        lastPosMs = timeMs
    }

    fun onBarometricAltitude(altitudeM: Double, timeMs: Long) {
        baroAltitude = altitudeM
        baroMs = timeMs
    }

    fun onHeartRate(bpm: Int, timeMs: Long) {
        if (bpm !in 25..250) return
        hr = bpm
        hrMs = timeMs
        if (!paused) {
            hrSum += bpm
            hrCount++
            hrMax = maxOf(hrMax ?: 0, bpm)
        }
    }

    fun onCadence(rpm: Int, timeMs: Long) {
        cadence = rpm
        cadenceMs = timeMs
    }

    fun onPower(watts: Int, timeMs: Long) {
        power = watts
        powerMs = timeMs
        if (!paused) {
            powerSum += watts
            powerCount++
        }
    }

    fun setManualPause(paused: Boolean, timeMs: Long) {
        advanceClock(timeMs)
        manualPaused = paused
        if (!paused) autoPause.reset()
    }

    fun tick(timeMs: Long): LiveMetrics {
        advanceClock(timeMs)
        // GPS silent for a while with no dead-reckoning feed: the athlete has most likely stopped.
        if (lastPos != null && timeMs - lastPosMs > GPS_SILENCE_MS) {
            speedMps = 0.0
            autoPause.update(0.0, timeMs)
        }
        val hrNow = hr.takeIf { timeMs - hrMs <= SENSOR_STALE_MS }
        val powerNow = power.takeIf { timeMs - powerMs <= SENSOR_STALE_MS }
        val estimated = if (sport == Sport.RIDE && !paused) {
            PowerEstimator.watts(speedMps, gradePct ?: 0.0, athlete.weightKg + athlete.bikeWeightKg).roundToInt()
        } else null

        return LiveMetrics(
            elapsedMs = timeMs - startMs,
            movingMs = movingMs,
            distanceM = distanceM,
            speedKmh = if (paused) 0.0 else speedMps * 3.6,
            avgSpeedKmh = if (movingMs > 0) distanceM / (movingMs / 1000.0) * 3.6 else 0.0,
            maxSpeedKmh = maxSpeedMps * 3.6,
            altitudeM = currentAltitude(timeMs),
            elevationGainM = gain.gainM,
            gradePercent = gradePct,
            heartRate = hrNow,
            avgHeartRate = if (hrCount > 0) (hrSum / hrCount).toInt() else null,
            maxHeartRate = hrMax,
            cadence = cadence.takeIf { timeMs - cadenceMs <= SENSOR_STALE_MS },
            powerWatts = powerNow,
            avgPowerWatts = if (powerCount > 0) (powerSum / powerCount).toInt() else null,
            estimatedPowerWatts = estimated,
            calories = calories,
            autoPaused = autoPause.isPaused,
            manualPaused = manualPaused,
            deadReckoning = lastInterpolated,
        )
    }

    private fun currentAltitude(timeMs: Long): Double? =
        if (timeMs - baroMs <= SENSOR_STALE_MS) baroAltitude else gpsAltitude

    private fun advanceClock(timeMs: Long) {
        val dt = timeMs - clockMs
        if (dt <= 0) return
        clockMs = timeMs
        if (paused) return
        movingMs += dt
        val hrForCalories = hr.takeIf { timeMs - hrMs <= HR_CALORIE_WINDOW_MS }
        val perMinute = if (hrForCalories != null) {
            Calories.kcalPerMinute(hrForCalories.toDouble(), athlete.weightKg, athlete.age, athlete.gender)
        } else {
            Calories.kcalPerMinuteMet(sport, athlete.weightKg)
        }
        calories += perMinute * dt / 60_000.0
    }

    private companion object {
        const val MAX_PLAUSIBLE_MPS = 30.0
        const val SENSOR_STALE_MS = 5_000L
        const val HR_CALORIE_WINDOW_MS = 30_000L
        const val GPS_SILENCE_MS = 3_000L
        const val NEVER = Long.MIN_VALUE / 2
    }
}
