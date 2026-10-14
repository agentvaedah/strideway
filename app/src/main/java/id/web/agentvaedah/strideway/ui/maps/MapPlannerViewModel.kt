package id.web.agentvaedah.strideway.ui.maps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.web.agentvaedah.strideway.core.geo.PlaceResult
import id.web.agentvaedah.strideway.core.heatmap.HeatCell
import id.web.agentvaedah.strideway.core.heatmap.Heatmap
import id.web.agentvaedah.strideway.core.model.LatLon
import id.web.agentvaedah.strideway.core.model.Sport
import id.web.agentvaedah.strideway.core.model.TrackPoint
import id.web.agentvaedah.strideway.core.route.DrawRouteDraft
import id.web.agentvaedah.strideway.core.route.RouteOption
import id.web.agentvaedah.strideway.core.route.RouteOptions
import id.web.agentvaedah.strideway.core.route.RouteSort
import id.web.agentvaedah.strideway.data.db.PlaceEntity
import id.web.agentvaedah.strideway.data.db.RouteSource
import id.web.agentvaedah.strideway.data.db.TrackPointDao
import id.web.agentvaedah.strideway.data.prefs.MapOverlay
import id.web.agentvaedah.strideway.data.prefs.SettingsRepository
import id.web.agentvaedah.strideway.data.remote.routing.RoutingClient
import id.web.agentvaedah.strideway.data.remote.routing.RoutingException
import id.web.agentvaedah.strideway.data.repo.PlaceRepository
import id.web.agentvaedah.strideway.data.repo.RouteRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Start or end of a planned route: the rider's position, a search result or a saved place. */
data class Endpoint(val name: String, val pos: LatLon)

sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Results(val query: String, val items: List<PlaceResult>) : SearchState
    data class Failed(val message: String) : SearchState
}

sealed interface PlanState {
    data object Idle : PlanState
    data object Loading : PlanState
    data class Ready(val options: List<RouteOption>) : PlanState
    data class Failed(val message: String) : PlanState
}

/** Search, saved places, the route planner with alternatives, drawing a route by hand and the personal heatmap. */
@HiltViewModel
class MapPlannerViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val routing: RoutingClient,
    private val routes: RouteRepository,
    private val places: PlaceRepository,
    private val pointDao: TrackPointDao,
) : ViewModel() {
    val sport: StateFlow<Sport> = settings.mapsSport.stateIn(viewModelScope, SharingStarted.Eagerly, Sport.RIDE)
    val overlays: StateFlow<Set<MapOverlay>> = settings.mapOverlays.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val tilt3d: StateFlow<Boolean> = settings.maps3d.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val savedPlaces: StateFlow<List<PlaceEntity>> = places.places.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _search = MutableStateFlow<SearchState>(SearchState.Idle)
    val search: StateFlow<SearchState> = _search.asStateFlow()

    /** Last place picked from search: shown as a pin, offered as a route end and for bookmarking. */
    private val _focus = MutableStateFlow<Endpoint?>(null)
    val focus: StateFlow<Endpoint?> = _focus.asStateFlow()

    private val _from = MutableStateFlow<Endpoint?>(null)
    val from: StateFlow<Endpoint?> = _from.asStateFlow()
    private val _to = MutableStateFlow<Endpoint?>(null)
    val to: StateFlow<Endpoint?> = _to.asStateFlow()

    private val _plan = MutableStateFlow<PlanState>(PlanState.Idle)
    val plan: StateFlow<PlanState> = _plan.asStateFlow()
    private val _sort = MutableStateFlow(RouteSort.LENGTH)
    val sort: StateFlow<RouteSort> = _sort.asStateFlow()
    private val _selected = MutableStateFlow(0)
    val selected: StateFlow<Int> = _selected.asStateFlow()

    private val _drawing = MutableStateFlow(false)
    val drawing: StateFlow<Boolean> = _drawing.asStateFlow()
    private val _draw = MutableStateFlow(DrawRouteDraft())
    val draw: StateFlow<DrawRouteDraft> = _draw.asStateFlow()
    private val _drawBusy = MutableStateFlow(false)
    val drawBusy: StateFlow<Boolean> = _drawBusy.asStateFlow()

    private val _heat = MutableStateFlow<List<HeatCell>>(emptyList())
    val heat: StateFlow<List<HeatCell>> = _heat.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var planJob: Job? = null
    private var heatLoaded = false

    init {
        viewModelScope.launch {
            overlays.collect { if (MapOverlay.HEATMAP in it) loadHeat() }
        }
    }

    private fun profile(): String = if (sport.value == Sport.RUN) "hiking-beta" else "trekking"

    fun setSport(s: Sport) {
        viewModelScope.launch { settings.setMapsSport(s) }
        if (_plan.value is PlanState.Ready) plan()
    }

    fun toggleOverlay(o: MapOverlay) {
        viewModelScope.launch { settings.setMapOverlay(o, o !in overlays.value) }
    }

    fun toggle3d() {
        viewModelScope.launch { settings.setMaps3d(!tilt3d.value) }
    }

    fun search(query: String, near: LatLon?) {
        val q = query.trim()
        if (q.length < 3) return
        _search.value = SearchState.Loading
        viewModelScope.launch {
            _search.value = try {
                SearchState.Results(q, routing.search(q, near))
            } catch (e: RoutingException) {
                SearchState.Failed(e.message ?: "Pencarian gagal")
            }
        }
    }

    fun clearSearch() {
        _search.value = SearchState.Idle
    }

    fun focusOn(e: Endpoint?) {
        _focus.value = e
        _search.value = SearchState.Idle
    }

    fun savePlace(e: Endpoint) {
        viewModelScope.launch {
            places.save(e.name, e.pos.lat, e.pos.lon)
            _message.value = "\"${e.name}\" disimpan"
        }
    }

    fun deletePlace(id: Long) {
        viewModelScope.launch { places.delete(id) }
    }

    fun setFrom(e: Endpoint?) { _from.value = e }

    fun setTo(e: Endpoint?) { _to.value = e }

    fun setSort(s: RouteSort) {
        _sort.value = s
        _selected.value = 0
    }

    fun select(i: Int) { _selected.value = i }

    fun sorted(options: List<RouteOption>): List<RouteOption> = RouteOptions.sort(options, _sort.value)

    /** Fetches up to three alternatives between [from] and [to] for the chosen sport. */
    fun plan() {
        val a = _from.value ?: return
        val b = _to.value ?: return
        planJob?.cancel()
        _plan.value = PlanState.Loading
        _selected.value = 0
        planJob = viewModelScope.launch {
            _plan.value = try {
                PlanState.Ready(routing.alternatives(listOf(a.pos, b.pos), profile()))
            } catch (e: RoutingException) {
                PlanState.Failed(e.message ?: "Rute gagal dihitung")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PlanState.Failed("Rute gagal dihitung: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun clearPlan() {
        planJob?.cancel()
        _plan.value = PlanState.Idle
    }

    fun savePlanned(option: RouteOption, name: String, onSaved: (Long) -> Unit) {
        viewModelScope.launch {
            val id = routes.save(name, RouteSource.PLANNED, option.points.map { TrackPoint(it.lat, it.lon, it.ele) }, null, option.maneuvers)
            _message.value = "Rute disimpan"
            clearPlan()
            onSaved(id)
        }
    }

    fun startDrawing() {
        _drawing.value = true
        _draw.value = DrawRouteDraft()
    }

    fun stopDrawing() {
        _drawing.value = false
        _draw.value = DrawRouteDraft()
    }

    /** Adds a tapped point and snaps the new leg to roads; a failed leg stays straight so drawing can go on. */
    fun addPoint(p: LatLon) {
        _draw.value = _draw.value.add(p)
        val missing = _draw.value.missingSegments()
        if (missing.isEmpty()) return
        viewModelScope.launch {
            _drawBusy.value = true
            for ((a, b) in missing) {
                try {
                    val leg = routing.route(listOf(a, b), profile()).map { LatLon(it.lat, it.lon) }
                    if (a in _draw.value.waypoints && b in _draw.value.waypoints) _draw.value = _draw.value.withSegment(a, b, leg)
                } catch (e: RoutingException) {
                    _message.value = "Satu ruas tidak bisa mengikuti jalan, digambar lurus"
                    _draw.value = _draw.value.withSegment(a, b, listOf(a, b))
                }
            }
            _drawBusy.value = false
        }
    }

    fun undo() { _draw.value = _draw.value.undo() }

    fun clearDraw() { _draw.value = _draw.value.clear() }

    fun saveDrawn(name: String, onSaved: (Long) -> Unit) {
        val path = _draw.value.path()
        if (path.size < 2) return
        viewModelScope.launch {
            val id = routes.save(name, RouteSource.DRAWN, path.map { TrackPoint(it.lat, it.lon) }, null)
            _message.value = "Rute gambar disimpan"
            stopDrawing()
            onSaved(id)
        }
    }

    private fun loadHeat() {
        if (heatLoaded) return
        heatLoaded = true
        viewModelScope.launch {
            _heat.value = withContext(Dispatchers.Default) {
                Heatmap.bin(pointDao.allPositions().map { LatLon(it.lat, it.lon) })
            }
            if (_heat.value.isEmpty()) _message.value = "Heatmap pribadi kosong: belum ada aktivitas dengan jalur"
        }
    }

    fun clearMessage() { _message.value = null }
}
