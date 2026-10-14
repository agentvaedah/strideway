package id.web.agentvaedah.strideway.service

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import id.web.agentvaedah.strideway.core.coach.CueConfig
import id.web.agentvaedah.strideway.core.coach.VoiceCues
import id.web.agentvaedah.strideway.core.fuel.FuelPlanner
import id.web.agentvaedah.strideway.core.fuel.FuelReminder
import id.web.agentvaedah.strideway.core.fuel.FuelReminders
import id.web.agentvaedah.strideway.core.model.LatLon
import id.web.agentvaedah.strideway.core.model.Sport
import id.web.agentvaedah.strideway.core.route.Maneuver
import id.web.agentvaedah.strideway.core.route.NavEvent
import id.web.agentvaedah.strideway.core.route.NavState
import id.web.agentvaedah.strideway.core.route.Navigator
import id.web.agentvaedah.strideway.core.training.Workout
import id.web.agentvaedah.strideway.core.training.WorkoutRunner
import id.web.agentvaedah.strideway.core.training.WorkoutState
import id.web.agentvaedah.strideway.core.physio.Zones
import id.web.agentvaedah.strideway.data.prefs.FuelSettings
import id.web.agentvaedah.strideway.data.prefs.SettingsRepository
import id.web.agentvaedah.strideway.data.prefs.VoiceSettings
import id.web.agentvaedah.strideway.data.remote.weather.WeatherRepository
import id.web.agentvaedah.strideway.data.remote.weather.WeatherState
import id.web.agentvaedah.strideway.data.remote.routing.RoutingClient
import id.web.agentvaedah.strideway.data.repo.RouteRepository
import id.web.agentvaedah.strideway.data.repo.TrainingRepository
import id.web.agentvaedah.strideway.data.voice.VoicePlayer
import id.web.agentvaedah.strideway.di.AppScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private data class LoadedRoute(val points: List<LatLon>, val maneuvers: List<Maneuver>)

private data class CueInputs(
    val state: RecordingState,
    val route: LoadedRoute,
    val voice: VoiceSettings,
    val fuel: FuelSettings,
)

/**
 * Runs beside a recording: navigates the active route, runs a structured workout, and turns metrics into voice cues and vibration.
 * Lives in the app scope like [RecordingController] so cues keep coming with the screen off.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class RecordingCueRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val scope: CoroutineScope,
    private val controller: RecordingController,
    private val settings: SettingsRepository,
    private val routes: RouteRepository,
    private val voice: VoicePlayer,
    private val weather: WeatherRepository,
    private val routing: RoutingClient,
    private val workouts: WorkoutSession,
    private val training: TrainingRepository,
) {
    private val _nav = MutableStateFlow<NavState?>(null)

    /** Turn-by-turn state; null when no route is loaded or nothing is recording. */
    val nav: StateFlow<NavState?> = _nav.asStateFlow()

    private val _workout = MutableStateFlow<WorkoutState?>(null)

    /** Current interval of the structured session being ridden, if one was started. */
    val workout: StateFlow<WorkoutState?> = _workout.asStateFlow()

    private var job: Job? = null

    private val activeRoute = settings.activeRouteId.flatMapLatest { id ->
        flow {
            if (id == null) {
                emit(LoadedRoute(emptyList(), emptyList()))
            } else {
                val pts = routes.points(id).map { p -> LatLon(p.lat, p.lon) }
                // Planned routes carry BRouter's instructions; imported and drawn ones are read from their shape.
                val m = routes.maneuvers(id).ifEmpty { Navigator.fromGeometry(pts) }
                emit(LoadedRoute(pts, m))
            }
        }
    }

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            val athlete = settings.athlete.first()
            val zones = Zones.heartRate(athlete.hrMax, athlete.hrRest)
            var cues: VoiceCues? = null
            var cueConfig: VoiceSettings? = null
            var navigator: Navigator? = null
            var navRoute: LoadedRoute? = null
            var offSinceMs: Long? = null
            var rerouting = false
            var workoutRunner: WorkoutRunner? = null
            var workoutFor: Workout? = null
            var reminders: FuelReminders? = null
            var reminderConfig: FuelSettings? = null
            var weatherRequested = false

            combine(controller.state, activeRoute, settings.voice, settings.fuel) { s, r, v, f -> CueInputs(s, r, v, f) }.collect { (s, route, v, fuel) ->
                if (s !is RecordingState.Active) {
                    _nav.value = null
                    _workout.value = null
                    return@collect
                }
                if (!weatherRequested) {
                    // Temperature drives the drink interval; one fetch near the start is enough for a ride.
                    s.position?.let { p ->
                        weatherRequested = true
                        launch { weather.refresh(p.lat, p.lon) }
                    }
                }
                if (cues == null || cueConfig != v) {
                    // Rebuilding resets split tracking; acceptable because settings rarely change mid-ride.
                    cues = VoiceCues(s.sport, zones, CueConfig(v.split, v.pause, v.heartRate, v.route))
                    cueConfig = v
                }
                if (route !== navRoute) {
                    navigator = if (route.points.size >= 2) Navigator(route.points, route.maneuvers) else null
                    navRoute = route
                    offSinceMs = null
                }

                val now = System.currentTimeMillis()
                val spoken = ArrayList<String>()
                cues!!.update(s.metrics, now).forEach { spoken += it.text }

                if (fuel.enabled) {
                    if (reminders == null || reminderConfig != fuel) {
                        // Plan for a long session at the current temperature; user overrides win when set.
                        val temp = (weather.state.value as? WeatherState.Ready)?.report?.current?.tempC
                        val plan = FuelPlanner.plan(s.sport, durationMin = 180, tempC = temp, weightKg = athlete.weightKg)
                        reminders = FuelReminders(
                            drinkEveryMin = fuel.drinkEveryMin.takeIf { it > 0 } ?: plan.drinkEveryMin,
                            eatEveryMin = fuel.eatEveryMin.takeIf { it > 0 } ?: plan.eatEveryMin,
                        ).also { r -> r.due(s.metrics.movingMs) }
                        reminderConfig = fuel
                    }
                    when (reminders!!.due(s.metrics.movingMs)) {
                        FuelReminder.DRINK -> spoken += "Waktunya minum, beberapa teguk"
                        FuelReminder.EAT -> spoken += "Waktunya makan dan minum"
                        null -> Unit
                    }
                }

                val pos = s.position
                val n = navigator
                if (n != null && pos != null) {
                    val st = n.update(pos, now, s.metrics.speedKmh / 3.6)
                    _nav.value = st
                    if (v.routeAlerts && v.route) {
                        st.events.forEach { e ->
                            if (e is NavEvent.OffRoute) vibrate()
                            spoken += e.text()
                        }
                    }
                    // Stay off route for 10 s and the rest of the route is recalculated from here to its end.
                    if (st.offRoute) {
                        val since = offSinceMs ?: now.also { offSinceMs = it }
                        if (!rerouting && now - since > REROUTE_AFTER_MS && route.points.isNotEmpty()) {
                            rerouting = true
                            if (v.enabled && v.route) voice.say("Menghitung ulang rute")
                            launch {
                                val fresh = runCatching { routing.alternatives(listOf(pos, route.points.last()), profileFor(s.sport), 1).firstOrNull() }.getOrNull()
                                if (fresh != null && fresh.points.size >= 2) {
                                    val pts = fresh.points.map { it.latLon }
                                    navigator = Navigator(pts, fresh.maneuvers.ifEmpty { Navigator.fromGeometry(pts) })
                                    navRoute = LoadedRoute(pts, fresh.maneuvers)
                                    rerouteLine.value = pts
                                }
                                offSinceMs = null
                                rerouting = false
                            }
                        }
                    } else {
                        offSinceMs = null
                    }
                } else {
                    _nav.value = null
                }

                val w = workouts.active.value
                if (w != null) {
                    if (workoutFor !== w) {
                        workoutRunner = WorkoutRunner(w, athlete.ftpWatts)
                        workoutFor = w
                        if (v.enabled) spoken += "Sesi ${w.name} dimulai. ${w.steps.first().label}"
                    }
                    val ws = workoutRunner!!.update(s.metrics.movingMs, s.metrics.powerWatts)
                    _workout.value = ws
                    ws.events.forEach { spoken += it.text() }
                    if (ws.finished) {
                        workouts.plannedSessionId?.let { id -> launch { training.markDone(id, s.activityId) } }
                        workouts.finish(s.activityId)
                    }
                } else {
                    _workout.value = null
                    workoutRunner = null
                    workoutFor = null
                }

                if (v.enabled) spoken.forEach(voice::say)
            }
        }
    }

    /** A recalculated route after leaving the planned one, drawn instead of the saved route until the ride ends. */
    val rerouteLine = MutableStateFlow<List<LatLon>?>(null)

    fun stop() {
        job?.cancel()
        job = null
        _nav.value = null
        _workout.value = null
        rerouteLine.value = null
    }

    private fun profileFor(sport: Sport) = if (sport == Sport.RUN) "hiking-beta" else "trekking"

    private companion object {
        const val REROUTE_AFTER_MS = 10_000L
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        } ?: return
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1))
    }
}
