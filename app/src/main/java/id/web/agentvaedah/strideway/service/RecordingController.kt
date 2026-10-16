package id.web.agentvaedah.strideway.service

import android.content.Context
import android.content.Intent
import android.hardware.GeomagneticField
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import id.web.agentvaedah.strideway.core.coach.VoiceCues
import id.web.agentvaedah.strideway.core.geo.Geo
import id.web.agentvaedah.strideway.core.geo.HeadingFilter
import id.web.agentvaedah.strideway.core.model.Athlete
import id.web.agentvaedah.strideway.core.model.LatLon
import id.web.agentvaedah.strideway.core.model.Sport
import id.web.agentvaedah.strideway.core.telemetry.DeadReckoning
import id.web.agentvaedah.strideway.core.telemetry.LiveMetrics
import id.web.agentvaedah.strideway.core.telemetry.SessionAccumulator
import id.web.agentvaedah.strideway.core.telemetry.SpeedHistory
import id.web.agentvaedah.strideway.core.track.TrackStats
import id.web.agentvaedah.strideway.data.ble.BleHub
import id.web.agentvaedah.strideway.data.ble.SensorReading
import id.web.agentvaedah.strideway.data.ble.SensorStatus
import id.web.agentvaedah.strideway.data.db.TrackPointEntity
import id.web.agentvaedah.strideway.data.location.GpsSample
import id.web.agentvaedah.strideway.data.location.GpsSource
import id.web.agentvaedah.strideway.data.location.MotionSample
import id.web.agentvaedah.strideway.data.location.MotionSensors
import id.web.agentvaedah.strideway.data.prefs.SettingsRepository
import id.web.agentvaedah.strideway.data.repo.ActivityRepository
import id.web.agentvaedah.strideway.data.voice.VoicePlayer
import id.web.agentvaedah.strideway.di.AppScope
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class GpsFix { SEARCHING, OK, DEAD_RECKONING, LOST }

sealed interface RecordingState {
    data object Idle : RecordingState

    data class Active(
        val activityId: Long,
        val sport: Sport,
        val metrics: LiveMetrics,
        val path: List<LatLon>,
        val position: LatLon?,
        val headingDeg: Float?,
        val elevationProfile: List<Float>,
        val gpsFix: GpsFix,
        val sensors: List<SensorStatus>,
        /** From a BLE Environmental Sensing device; null without one. */
        val temperatureC: Double? = null,
        /** Phone barometer when present, else a BLE environmental sensor. */
        val pressureHpa: Double? = null,
        val humidityPct: Double? = null,
        /** Current speed in km/h over the last minute, oldest first, for the sparkline. */
        val speedHistory: List<Double> = emptyList(),
        /** Change in km/h against ten seconds ago; null until that much history exists. */
        val speedTrendKmh: Double? = null,
    ) : RecordingState

    data class Finished(val activityId: Long) : RecordingState
}

/**
 * Owns one recording session. Lives in the app scope so the UI can come and go;
 * the foreground service only keeps the process alive and shows the notification.
 * All accumulator access is serialised through [lock].
 */
@Singleton
class RecordingController @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val scope: CoroutineScope,
    private val gps: GpsSource,
    private val motion: MotionSensors,
    private val ble: BleHub,
    private val activities: ActivityRepository,
    private val settings: SettingsRepository,
    private val voice: VoicePlayer,
) {
    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    private val _gpsError = MutableStateFlow<String?>(null)
    val gpsError: StateFlow<String?> = _gpsError.asStateFlow()

    private val lock = Mutex()
    private val starting = AtomicBoolean(false)

    /** True between start() and the first published Active state; the service uses it on launch. */
    val isStartingOrActive: Boolean get() = starting.get() || _state.value is RecordingState.Active
    private var session: Session? = null
    private val jobs = mutableListOf<Job>()

    private class Session(
        val activityId: Long,
        val sport: Sport,
        val athlete: Athlete,
        val acc: SessionAccumulator,
        val dr: DeadReckoning,
    ) {
        val path = ArrayList<LatLon>()
        val profile = ArrayList<Float>()
        val buffer = ArrayList<TrackPointEntity>()
        var position: LatLon? = null
        var heading: Float? = null
        val headingFilter = HeadingFilter()
        var gpsCourse: Double? = null
        var declination: Float? = null
        var fix = GpsFix.SEARCHING
        var lastGpsMs = 0L
        var lastSpeed = 0.0
        var lastAltitude: Double? = null
        var baroOffset: Double? = null
        var lastProfileDistance = 0.0
        var temperatureC: Double? = null
        var pressureHpa: Double? = null
        var humidityPct: Double? = null
        val speeds = SpeedHistory()
    }

    fun start(sport: Sport) {
        if (_state.value is RecordingState.Active) return
        // Guards double taps and the interrupted-session recovery while the row is being created.
        if (!starting.compareAndSet(false, true)) return
        // Started before the DB insert so the call happens while the app is still in the foreground.
        ContextCompat.startForegroundService(context, Intent(context, RecordingService::class.java))
        scope.launch {
            val athlete = settings.athlete.first()
            ble.wheelCircumferenceM = settings.wheelCircumferenceM.first()
            val startMs = System.currentTimeMillis()
            val id = activities.createLive(sport, startMs)
            lock.withLock {
                session = Session(id, sport, athlete, SessionAccumulator(athlete, sport, startMs), DeadReckoning())
            }
            _gpsError.value = null
            publish(startMs)
            starting.set(false)
            speak(VoiceCues.startLine(sport))
            ble.connectSaved()
            launchCollectors()
        }
    }

    private fun launchCollectors() {
        jobs += scope.launch {
            gps.updates()
                .catch { _gpsError.value = it.message ?: "GPS tidak tersedia" }
                .collect { onGps(it) }
        }
        jobs += scope.launch { motion.samples().collect { onMotion(it) } }
        jobs += scope.launch { ble.readings.collect { onSensor(it) } }
        jobs += scope.launch {
            while (isActive) {
                delay(TICK_MS)
                onTick(System.currentTimeMillis())
            }
        }
        jobs += scope.launch {
            while (isActive) {
                delay(FLUSH_MS)
                flush()
            }
        }
    }

    private suspend fun onGps(s: GpsSample) = lock.withLock {
        val ses = session ?: return@withLock
        val speed = s.speedMps ?: 0.0
        val bearing = s.bearingDeg?.toDouble() ?: ses.position?.let { Geo.bearingDeg(it, s.pos) } ?: 0.0
        ses.dr.onGpsFix(s.pos, speed, bearing, s.timeMs)
        ses.fix = GpsFix.OK
        ses.lastGpsMs = s.timeMs
        ses.lastSpeed = speed
        ses.speeds.add(s.timeMs, speed * 3.6)
        if (s.altitudeM != null) ses.lastAltitude = s.altitudeM
        if (ses.declination == null) {
            ses.declination = GeomagneticField(s.pos.lat.toFloat(), s.pos.lon.toFloat(), (s.altitudeM ?: 0.0).toFloat(), s.timeMs).declination
        }
        ses.gpsCourse = s.bearingDeg?.toDouble()?.takeIf { speed > 1.0 }
        if (speed * 3.6 >= 6.0) ses.heading = ses.headingFilter.update(ses.gpsCourse, null, speed * 3.6)?.toFloat()
        record(ses, s.timeMs, s.pos, s.altitudeM, s.speedMps, interpolated = false)
    }

    private suspend fun onMotion(m: MotionSample) = lock.withLock {
        val ses = session ?: return@withLock
        when (m) {
            is MotionSample.Heading -> {
                // Rotation vector gives magnetic north; GPS course and the map use true north.
                val trueAzimuth = m.degrees.toDouble() + (ses.declination ?: 0f)
                ses.dr.onHeading(trueAzimuth)
                val speedKmh = if (ses.fix == GpsFix.OK) ses.lastSpeed * 3.6 else 0.0
                ses.heading = ses.headingFilter.update(ses.gpsCourse, trueAzimuth, speedKmh)?.toFloat()
            }
            is MotionSample.Acceleration -> {
                val h = Math.toRadians((ses.heading ?: 0f).toDouble())
                // Project world-frame acceleration onto the direction of travel.
                val forward = m.east * sin(h) + m.north * cos(h)
                ses.dr.onForwardAcceleration(forward, m.timeMs)
            }
            is MotionSample.Pressure -> {
                ses.pressureHpa = m.hpa
                // Barometer is smooth but has no absolute datum; anchor it to GPS altitude once.
                val gpsAlt = ses.lastAltitude
                if (ses.baroOffset == null && gpsAlt != null && ses.fix == GpsFix.OK) ses.baroOffset = gpsAlt - m.altitudeM
                ses.baroOffset?.let { ses.acc.onBarometricAltitude(m.altitudeM + it, m.timeMs) }
            }
        }
    }

    private suspend fun onSensor(r: SensorReading) = lock.withLock {
        val ses = session ?: return@withLock
        when (r) {
            is SensorReading.HeartRate -> ses.acc.onHeartRate(r.bpm, r.timeMs)
            is SensorReading.Cadence -> ses.acc.onCadence(r.rpm, r.timeMs)
            is SensorReading.Power -> ses.acc.onPower(r.watts, r.timeMs)
            is SensorReading.WheelSpeed -> Unit
            is SensorReading.Temperature -> ses.temperatureC = r.celsius
            is SensorReading.Pressure -> if (!motion.hasBarometer) ses.pressureHpa = r.hpa
            is SensorReading.Humidity -> ses.humidityPct = r.percent
        }
    }

    private suspend fun onTick(now: Long) {
        lock.withLock {
            val ses = session ?: return@withLock
            if (ses.fix != GpsFix.SEARCHING && ses.dr.isStale(now)) {
                val est = ses.dr.estimate(now)
                if (est != null) {
                    ses.fix = GpsFix.DEAD_RECKONING
                    record(ses, now, est, null, null, interpolated = true)
                } else {
                    ses.fix = GpsFix.LOST
                }
            }
        }
        publish(now)
    }

    private fun record(ses: Session, timeMs: Long, pos: LatLon, alt: Double?, speed: Double?, interpolated: Boolean) {
        ses.acc.onLocation(timeMs, pos, alt, speed, interpolated)
        ses.position = pos
        val m = ses.acc.tick(timeMs)
        if (!m.paused) {
            ses.path += pos
            ses.buffer += TrackPointEntity(
                activityId = ses.activityId,
                timeMs = timeMs,
                lat = pos.lat,
                lon = pos.lon,
                ele = m.altitudeM,
                speedMps = m.speedKmh / 3.6,
                hr = m.heartRate,
                cadence = m.cadence,
                power = m.powerWatts,
                interpolated = interpolated,
            )
            val altitude = m.altitudeM
            if (altitude != null && m.distanceM - ses.lastProfileDistance >= PROFILE_STEP_M) {
                ses.lastProfileDistance = m.distanceM
                ses.profile += altitude.toFloat()
                if (ses.profile.size > PROFILE_MAX) ses.profile.removeAt(0)
            }
        }
    }

    private suspend fun publish(now: Long) {
        val snapshot = lock.withLock {
            val ses = session ?: return
            RecordingState.Active(
                activityId = ses.activityId,
                sport = ses.sport,
                metrics = ses.acc.tick(now),
                path = TrackStats.downsample(ses.path, LIVE_PATH_MAX),
                position = ses.position,
                headingDeg = ses.heading,
                elevationProfile = ses.profile.toList(),
                gpsFix = ses.fix,
                sensors = ble.status.value.values.toList(),
                temperatureC = ses.temperatureC,
                pressureHpa = ses.pressureHpa,
                humidityPct = ses.humidityPct,
                speedHistory = ses.speeds.samples(),
                speedTrendKmh = ses.speeds.trend(ses.lastGpsMs),
            )
        }
        _state.value = snapshot
    }

    private suspend fun flush() {
        val batch = lock.withLock {
            val ses = session ?: return
            ses.buffer.toList().also { ses.buffer.clear() }
        }
        // Points already removed from the buffer must reach the DB even if stop() cancels this job.
        withContext(NonCancellable + Dispatchers.IO) { activities.appendPoints(batch) }
    }

    fun togglePause() {
        scope.launch {
            lock.withLock {
                val ses = session ?: return@withLock
                val now = System.currentTimeMillis()
                ses.acc.setManualPause(!ses.acc.tick(now).manualPaused, now)
            }
            publish(System.currentTimeMillis())
        }
    }

    fun stop() {
        scope.launch {
            // Before endSession: the cue runner stops with the service, but the voice player is app-wide.
            if (session != null) speak(VoiceCues.STOP_LINE)
            val ses = endSession() ?: return@launch
            val metrics = ses.acc.tick(System.currentTimeMillis())
            val empty = metrics.distanceM < MIN_SAVE_DISTANCE_M && ses.path.size < 2
            withContext(Dispatchers.IO) {
                activities.appendPoints(ses.buffer)
                if (empty) activities.delete(ses.activityId) else activities.finish(ses.activityId, metrics, ses.athlete)
            }
            _state.value = if (empty) RecordingState.Idle else RecordingState.Finished(ses.activityId)
        }
    }

    fun discard() {
        scope.launch {
            if (session != null) speak(VoiceCues.STOP_LINE)
            val ses = endSession() ?: return@launch
            withContext(Dispatchers.IO) { activities.delete(ses.activityId) }
            _state.value = RecordingState.Idle
        }
    }

    private suspend fun speak(text: String) {
        if (settings.voice.first().enabled) voice.say(text)
    }

    fun acknowledgeFinished() {
        if (_state.value is RecordingState.Finished) _state.value = RecordingState.Idle
    }

    /** Saves a session that was cut off when the process died, using what reached the database. */
    suspend fun recoverInterrupted(): Long? = withContext(Dispatchers.IO) {
        if (session != null || starting.get()) return@withContext null
        val stale = activities.interruptedSession() ?: return@withContext null
        activities.finishFromPoints(stale.id, settings.athlete.first())
        activities.get(stale.id)?.id
    }

    private suspend fun endSession(): Session? {
        jobs.forEach { it.cancel() }
        jobs.clear()
        val ses = lock.withLock { session.also { session = null } } ?: return null
        ble.disconnectAll()
        context.stopService(Intent(context, RecordingService::class.java))
        return ses
    }

    private companion object {
        const val TICK_MS = 1_000L
        const val FLUSH_MS = 5_000L
        const val PROFILE_STEP_M = 25.0
        const val PROFILE_MAX = 240
        const val MIN_SAVE_DISTANCE_M = 10.0
        const val LIVE_PATH_MAX = 2_000
    }
}

