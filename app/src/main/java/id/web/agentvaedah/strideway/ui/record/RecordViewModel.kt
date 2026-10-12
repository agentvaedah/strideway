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
import id.web.agentvaedah.strideway.data.repo.RouteRepository
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DisplayMode { SPLIT, METRICS, MAP }

data class NavRoute(val id: Long, val name: String, val points: List<LatLon>)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecordViewModel @Inject constructor(
    private val controller: RecordingController,
    private val keyBus: HardwareKeyBus,
    private val gps: GpsSource,
    private val ble: BleHub,
    private val settings: SettingsRepository,
    routes: RouteRepository,
) : ViewModel() {
    val state: StateFlow<RecordingState> = controller.state
    val gpsError: StateFlow<String?> = controller.gpsError

    val mapBase: StateFlow<MapBase> = settings.mapBase.stateIn(viewModelScope, SharingStarted.Eagerly, MapBase.STANDARD)
    val athlete: StateFlow<Athlete> = settings.athlete.stateIn(viewModelScope, SharingStarted.Eagerly, Athlete())

    val navRoute: StateFlow<NavRoute?> = settings.activeRouteId.flatMapLatest { id ->
        if (id == null) {
            flowOf(null)
        } else {
            flow {
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

    fun clearNavRoute() {
        viewModelScope.launch { settings.setActiveRouteId(null) }
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
