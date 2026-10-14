package id.web.agentvaedah.strideway.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.web.agentvaedah.strideway.core.model.Athlete
import id.web.agentvaedah.strideway.core.model.LatLon
import id.web.agentvaedah.strideway.core.model.Sport
import id.web.agentvaedah.strideway.data.ble.BleHub
import id.web.agentvaedah.strideway.data.location.GpsSource
import id.web.agentvaedah.strideway.data.prefs.MapBase
import id.web.agentvaedah.strideway.data.prefs.SettingsRepository
import id.web.agentvaedah.strideway.data.db.RouteEntity
import id.web.agentvaedah.strideway.core.route.NavState
import id.web.agentvaedah.strideway.core.training.WorkoutState
import id.web.agentvaedah.strideway.data.remote.weather.WeatherRepository
import id.web.agentvaedah.strideway.data.remote.weather.WeatherState
import id.web.agentvaedah.strideway.data.repo.RouteRepository
import id.web.agentvaedah.strideway.service.RecordingCueRunner
import id.web.agentvaedah.strideway.service.WorkoutSession
import id.web.agentvaedah.strideway.core.training.Workout
import id.web.agentvaedah.strideway.service.HardwareKey
import id.web.agentvaedah.strideway.service.HardwareKeyBus
import id.web.agentvaedah.strideway.service.RecordingController
import id.web.agentvaedah.strideway.service.RecordingState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** SPLIT: map 75% with a compact metrics strip. METRICS: big numbers full screen. MAP: map only. */
enum class DisplayMode { SPLIT, METRICS, MAP }

/** FOLLOW tracks the rider heading-up; FREE after the user pans; OVERVIEW fits route and track. */
enum class CameraMode { FOLLOW, FREE, OVERVIEW }

data class NavRoute(val id: Long, val name: String, val points: List<LatLon>)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecordViewModel @Inject constructor(
    private val controller: RecordingController,
    private val keyBus: HardwareKeyBus,
    private val gps: GpsSource,
    private val ble: BleHub,
    private val settings: SettingsRepository,
    cues: RecordingCueRunner,
    private val workouts: WorkoutSession,
    routes: RouteRepository,
    weatherRepo: WeatherRepository,
) : ViewModel() {
    val weather: StateFlow<WeatherState> = weatherRepo.state

    val nav: StateFlow<NavState?> = cues.nav
    /** Session picked on the Latihan tab, shown on the standby screen before Start. */
    val plannedWorkout: StateFlow<Workout?> = workouts.active

    fun clearWorkout() = workouts.select(null)
    val workoutState: StateFlow<WorkoutState?> = cues.workout
    val rerouteLine: StateFlow<List<LatLon>?> = cues.rerouteLine
    val savedRoutes: StateFlow<List<RouteEntity>> = routes.routes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val routeAlerts: StateFlow<Boolean> = settings.voice.map { it.routeAlerts }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _camera = MutableStateFlow(CameraMode.FOLLOW)
    val camera: StateFlow<CameraMode> = _camera.asStateFlow()

    private val _tilt3d = MutableStateFlow(false)
    val tilt3d: StateFlow<Boolean> = _tilt3d.asStateFlow()

    /** Heading-up rotates the map with the rider; north-up keeps north at the top. */
    private val _headingUp = MutableStateFlow(true)
    val headingUp: StateFlow<Boolean> = _headingUp.asStateFlow()

    val state: StateFlow<RecordingState> = controller.state
    val gpsError: StateFlow<String?> = controller.gpsError

    val mapBase: StateFlow<MapBase> = settings.mapBase.stateIn(viewModelScope, SharingStarted.Eagerly, MapBase.STANDARD)
    val athlete: StateFlow<Athlete> = settings.athlete.stateIn(viewModelScope, SharingStarted.Eagerly, Athlete())

    val navRoute: StateFlow<NavRoute?> = settings.activeRouteId.flatMapLatest { id ->
        if (id == null) {
            flowOf<NavRoute?>(null)
        } else {
            flow<NavRoute?> {
                val r = routes.get(id)
                emit(r?.let { NavRoute(it.id, it.name, routes.points(id).map { p -> LatLon(p.lat, p.lon) }) })
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _display = MutableStateFlow(DisplayMode.SPLIT)
    val display: StateFlow<DisplayMode> = _display.asStateFlow()

    private val _sport = MutableStateFlow(Sport.RIDE)
    val sport: StateFlow<Sport> = _sport.asStateFlow()

    private val _recovered = MutableStateFlow<Long?>(null)
    val recovered: StateFlow<Long?> = _recovered.asStateFlow()

    init {
        viewModelScope.launch {
            keyBus.events.collect { key ->
                when (key) {
                    HardwareKey.VOLUME_UP -> if (state.value is RecordingState.Active) controller.togglePause()
                    HardwareKey.VOLUME_DOWN -> cycleDisplay()
                }
            }
        }
        viewModelScope.launch { _recovered.value = controller.recoverInterrupted() }
    }

    fun setKeysActive(active: Boolean) {
        keyBus.recordScreenActive = active
    }

    fun gpsEnabled(): Boolean = gps.isEnabled()

    fun lastKnownPosition(): LatLon? = gps.lastKnown()

    fun blePermissions(): Array<String> = ble.requiredPermissions()

    fun selectSport(s: Sport) {
        _sport.value = s
    }

    fun start() = controller.start(_sport.value)

    fun togglePause() = controller.togglePause()

    fun stop() = controller.stop()

    fun discard() = controller.discard()

    fun acknowledgeFinished() = controller.acknowledgeFinished()

    fun dismissRecovered() {
        _recovered.value = null
    }

    fun clearNavRoute() = selectRoute(null)

    fun selectRoute(id: Long?) {
        viewModelScope.launch { settings.setActiveRouteId(id) }
        if (id != null) _camera.value = CameraMode.OVERVIEW
    }

    fun setCamera(mode: CameraMode) {
        _camera.value = mode
    }

    fun toggle3d() {
        _tilt3d.value = !_tilt3d.value
        _camera.value = CameraMode.FOLLOW
    }

    fun toggleHeadingUp() {
        _headingUp.value = !_headingUp.value
        _camera.value = CameraMode.FOLLOW
    }

    fun toggleRouteAlerts() {
        viewModelScope.launch { settings.setRouteAlerts(!routeAlerts.value) }
    }

    fun setDisplay(mode: DisplayMode) {
        _display.value = mode
    }

    fun cycleDisplay() {
        _display.value = when (_display.value) {
            DisplayMode.SPLIT -> DisplayMode.METRICS
            DisplayMode.METRICS -> DisplayMode.MAP
            DisplayMode.MAP -> DisplayMode.SPLIT
        }
    }

    override fun onCleared() {
        keyBus.recordScreenActive = false
    }
}
