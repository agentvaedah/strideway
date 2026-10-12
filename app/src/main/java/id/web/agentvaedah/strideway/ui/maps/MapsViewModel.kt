package id.web.agentvaedah.strideway.ui.maps

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.web.agentvaedah.strideway.core.model.LatLon
import id.web.agentvaedah.strideway.core.model.Sport
import id.web.agentvaedah.strideway.core.model.Track
import id.web.agentvaedah.strideway.core.model.TrackPoint
import id.web.agentvaedah.strideway.core.route.LoopPlanner
import id.web.agentvaedah.strideway.core.route.RouteIntentException
import id.web.agentvaedah.strideway.core.route.RouteIntents
import id.web.agentvaedah.strideway.core.track.Gpx
import id.web.agentvaedah.strideway.core.track.TrackStats
import id.web.agentvaedah.strideway.data.db.RouteEntity
import id.web.agentvaedah.strideway.data.db.RouteSource
import id.web.agentvaedah.strideway.data.files.TrackFileIo
import id.web.agentvaedah.strideway.data.location.GpsSource
import id.web.agentvaedah.strideway.data.prefs.MapBase
import id.web.agentvaedah.strideway.data.prefs.SettingsRepository
import id.web.agentvaedah.strideway.data.prefs.TerrainOverlay
import id.web.agentvaedah.strideway.data.remote.llm.LlmClient
import id.web.agentvaedah.strideway.data.remote.llm.LlmMessage
import id.web.agentvaedah.strideway.data.remote.llm.LlmResult
import id.web.agentvaedah.strideway.data.remote.routing.RoutingClient
import id.web.agentvaedah.strideway.data.remote.routing.RoutingException
import id.web.agentvaedah.strideway.data.repo.ActivityRepository
import id.web.agentvaedah.strideway.data.repo.RouteRepository
import id.web.agentvaedah.strideway.data.repo.toTrackPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A route on screen that is not saved yet: an import awaiting a decision, or an AI result. */
data class DraftRoute(
    val name: String,
    val points: List<TrackPoint>,
    val distanceM: Double,
    val gainM: Double,
    val source: String,
    val prompt: String?,
    val importedTrack: Track? = null,
)

sealed interface AiRouteState {
    data object Idle : AiRouteState
    data class Working(val step: String) : AiRouteState
    data class Failed(val message: String) : AiRouteState
}

sealed interface TerrainState {
    data object Off : TerrainState
    data object Loading : TerrainState
    data class Shown(val image: TerrainImage) : TerrainState
    data class TooFar(val minZoom: Int) : TerrainState
    data class Failed(val message: String) : TerrainState
}

@HiltViewModel
class MapsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val routes: RouteRepository,
    private val activities: ActivityRepository,
    private val files: TrackFileIo,
    private val llm: LlmClient,
    private val routing: RoutingClient,
    private val terrain: TerrainRenderer,
    private val gps: GpsSource,
) : ViewModel() {
    val mapBase: StateFlow<MapBase> = settings.mapBase.stateIn(viewModelScope, SharingStarted.Eagerly, MapBase.STANDARD)
    val overlay: StateFlow<TerrainOverlay> = settings.terrainOverlay.stateIn(viewModelScope, SharingStarted.Eagerly, TerrainOverlay.NONE)
    val savedRoutes: StateFlow<List<RouteEntity>?> = routes.routes().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val activeRouteId: StateFlow<Long?> = settings.activeRouteId.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _draft = MutableStateFlow<DraftRoute?>(null)
    val draft: StateFlow<DraftRoute?> = _draft.asStateFlow()

    private val _shownRoute = MutableStateFlow<Pair<RouteEntity, List<LatLon>>?>(null)
    val shownRoute: StateFlow<Pair<RouteEntity, List<LatLon>>?> = _shownRoute.asStateFlow()

    private val _ai = MutableStateFlow<AiRouteState>(AiRouteState.Idle)
    val ai: StateFlow<AiRouteState> = _ai.asStateFlow()

    private val _terrain = MutableStateFlow<TerrainState>(TerrainState.Off)
    val terrainState: StateFlow<TerrainState> = _terrain.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var terrainJob: Job? = null
    private var aiJob: Job? = null
    private var lastCamera: Triple<Double, Double, Double>? = null

    fun lastKnown(): LatLon? = gps.lastKnown()

    fun setBase(b: MapBase) = viewModelScope.launch { settings.setMapBase(b) }

    fun setOverlay(o: TerrainOverlay) {
        viewModelScope.launch {
            settings.setTerrainOverlay(o)
            lastCamera?.let { (lat, lon, z) -> refreshTerrain(o, lat, lon, z) }
        }
    }

    fun onCameraIdle(lat: Double, lon: Double, zoom: Double) {
        lastCamera = Triple(lat, lon, zoom)
        refreshTerrain(overlay.value, lat, lon, zoom)
    }

    private fun refreshTerrain(o: TerrainOverlay, lat: Double, lon: Double, zoom: Double) {
        terrainJob?.cancel()
        if (o == TerrainOverlay.NONE) {
            _terrain.value = TerrainState.Off
            return
        }
        if (zoom < TerrainRenderer.MIN_Z) {
            _terrain.value = TerrainState.TooFar(TerrainRenderer.MIN_Z)
            return
        }
        _terrain.value = TerrainState.Loading
        terrainJob = viewModelScope.launch {
            val result = terrain.render(o, lat, lon, zoom)
            // runCatching in the renderer also captures cancellation; a superseded job must not publish.
            ensureActive()
            _terrain.value = result.fold(
                onSuccess = { TerrainState.Shown(it) },
                onFailure = { TerrainState.Failed("Data elevasi tidak bisa dimuat. ${it.message ?: "Periksa koneksi."}") },
            )
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            files.read(uri).fold(
                onSuccess = { t ->
                    _shownRoute.value = null
                    _draft.value = DraftRoute(
                        name = t.name ?: "Rute impor",
                        points = t.points,
                        distanceM = TrackStats.distanceM(t.points),
                        gainM = TrackStats.elevationGainM(t.points),
                        source = RouteSource.IMPORT,
                        prompt = null,
                        importedTrack = t,
                    )
                },
                onFailure = { _message.value = "Impor gagal: ${it.message}" },
            )
        }
    }

    fun saveDraftAsRoute(name: String, navigate: Boolean) {
        val d = _draft.value ?: return
        viewModelScope.launch {
            val id = routes.save(name, d.source, d.points, d.prompt)
            _draft.value = null
            if (navigate) settings.setActiveRouteId(id)
            show(id)
            _message.value = if (navigate) "Rute disimpan dan dipasang untuk navigasi" else "Rute disimpan"
        }
    }

    /** Imported files that carry timestamps are past rides/runs; store them in the activity feed. */
    fun saveDraftAsActivity(onSaved: (Long) -> Unit) {
        val t = _draft.value?.importedTrack ?: return
        viewModelScope.launch {
            val id = activities.importTrack(t, settings.athlete.first(), Sport.RIDE)
            _draft.value = null
            onSaved(id)
        }
    }

    fun discardDraft() {
        _draft.value = null
    }

    fun show(routeId: Long) {
        viewModelScope.launch {
            val r = routes.get(routeId) ?: return@launch
            _shownRoute.value = r to routes.points(routeId).map { LatLon(it.lat, it.lon) }
        }
    }

    fun hideShown() {
        _shownRoute.value = null
    }

    fun setNavigation(routeId: Long?) = viewModelScope.launch { settings.setActiveRouteId(routeId) }

    fun deleteRoute(routeId: Long) {
        viewModelScope.launch {
            if (activeRouteId.value == routeId) settings.setActiveRouteId(null)
            if (_shownRoute.value?.first?.id == routeId) _shownRoute.value = null
            routes.delete(routeId)
        }
    }

    fun exportRoute(uri: Uri, routeId: Long) {
        viewModelScope.launch {
            val r = routes.get(routeId) ?: return@launch
            val track = Track(r.name, null, routes.points(routeId).map { it.toTrackPoint() })
            _message.value = files.write(uri, Gpx.write(track)).fold(
                onSuccess = { "GPX tersimpan" },
                onFailure = { "Ekspor gagal: ${it.message}" },
            )
        }
    }

    fun exportFileName(routeId: Long): String =
        TrackFileIo.fileName(savedRoutes.value?.firstOrNull { it.id == routeId }?.name ?: "rute", "gpx")

    fun generate(prompt: String, near: LatLon?) {
        val text = prompt.trim()
        if (text.length < 8) {
            _ai.value = AiRouteState.Failed("Tulis permintaan rute yang lebih lengkap, misalnya titik awal dan jarak.")
            return
        }
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            try {
                _ai.value = AiRouteState.Working("Membaca permintaan")
                val reply = llm.complete(RouteIntents.SYSTEM_PROMPT, listOf(LlmMessage("user", text)))
                val intent = when (reply) {
                    is LlmResult.Ok -> RouteIntents.parse(reply.text)
                    is LlmResult.Failure -> {
                        _ai.value = AiRouteState.Failed(reply.reason)
                        return@launch
                    }
                }

                _ai.value = AiRouteState.Working("Mencari ${intent.start}")
                val start = routing.geocode(intent.start, near)
                    ?: return@launch fail("Tempat \"${intent.start}\" tidak ditemukan. Tulis nama yang lebih spesifik.")
                val stops = mutableListOf(start.pos)
                for (w in intent.waypoints) {
                    _ai.value = AiRouteState.Working("Mencari $w")
                    val p = routing.geocode(w, start.pos) ?: return@launch fail("Tempat \"$w\" tidak ditemukan.")
                    stops += p.pos
                }
                val end = intent.end?.let { e ->
                    _ai.value = AiRouteState.Working("Mencari $e")
                    routing.geocode(e, start.pos) ?: return@launch fail("Tempat \"$e\" tidak ditemukan.")
                }
                when {
                    end != null -> stops += end.pos
                    intent.loop && intent.waypoints.isEmpty() && intent.targetKm != null -> {
                        stops += LoopPlanner.triangle(start.pos, intent.targetKm!!)
                        stops += start.pos
                    }
                    intent.loop || stops.size == 1 -> stops += start.pos
                }
                if (stops.size < 2 || stops.distinct().size < 2) return@launch fail("Butuh minimal dua titik berbeda untuk membuat rute.")

                _ai.value = AiRouteState.Working("Menghitung rute di jalan")
                val points = routing.route(stops, RouteIntents.brouterProfile(intent))
                _shownRoute.value = null
                _draft.value = DraftRoute(
                    name = buildName(intent.start, intent.targetKm),
                    points = points,
                    distanceM = TrackStats.distanceM(points),
                    gainM = TrackStats.elevationGainM(points),
                    source = RouteSource.AI,
                    prompt = text,
                )
                _ai.value = AiRouteState.Idle
            } catch (e: RouteIntentException) {
                fail(e.message ?: "Jawaban AI tidak bisa dipakai")
            } catch (e: RoutingException) {
                fail(e.message ?: "Rute gagal dibuat")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail("Rute gagal dibuat: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun cancelGenerate() {
        aiJob?.cancel()
        _ai.value = AiRouteState.Idle
    }

    fun clearAiError() {
        if (_ai.value is AiRouteState.Failed) _ai.value = AiRouteState.Idle
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun fail(message: String) {
        _ai.value = AiRouteState.Failed(message)
    }

    private fun buildName(start: String, km: Double?): String {
        val place = start.substringBefore(',').trim()
        return if (km != null) "AI: $place ${km.toInt()} km" else "AI: dari $place"
    }
}
