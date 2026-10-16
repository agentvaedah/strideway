package id.web.agentvaedah.strideway.ui.activity

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.web.agentvaedah.strideway.core.format.Format
import id.web.agentvaedah.strideway.core.model.LatLon
import id.web.agentvaedah.strideway.core.model.Sport
import id.web.agentvaedah.strideway.core.model.Track
import id.web.agentvaedah.strideway.core.model.TrackPoint
import id.web.agentvaedah.strideway.core.privacy.PrivacyZone
import id.web.agentvaedah.strideway.core.records.RecordKey
import id.web.agentvaedah.strideway.core.share.ShareFormat
import id.web.agentvaedah.strideway.core.share.ShareRecord
import id.web.agentvaedah.strideway.core.share.ShareTemplate
import id.web.agentvaedah.strideway.core.track.Gpx
import id.web.agentvaedah.strideway.core.track.PlaybackPoint
import id.web.agentvaedah.strideway.core.track.Tcx
import id.web.agentvaedah.strideway.core.track.TrackEdit
import id.web.agentvaedah.strideway.core.track.TrackStats
import id.web.agentvaedah.strideway.data.db.ActivityEntity
import id.web.agentvaedah.strideway.data.db.MediaEntity
import id.web.agentvaedah.strideway.data.db.TrackPointEntity
import id.web.agentvaedah.strideway.data.files.ShareComposer
import id.web.agentvaedah.strideway.data.files.ShareRequest
import id.web.agentvaedah.strideway.data.files.TrackFileIo
import id.web.agentvaedah.strideway.data.prefs.MapBase
import id.web.agentvaedah.strideway.data.prefs.PrivacyZoneSetting
import id.web.agentvaedah.strideway.data.prefs.SettingsRepository
import id.web.agentvaedah.strideway.data.repo.ActivityRepository
import id.web.agentvaedah.strideway.data.repo.MediaStore
import id.web.agentvaedah.strideway.data.repo.RecordsRepository
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ExportFormat(val ext: String, val mime: String) {
    GPX("gpx", "application/gpx+xml"),
    TCX("tcx", "application/vnd.garmin.tcx+xml"),
}

data class Charts(val elevation: List<Float>, val heartRate: List<Float>, val speedKmh: List<Float>)

sealed interface DetailState {
    data object Loading : DetailState
    data object Missing : DetailState
    data class Ready(
        val activity: ActivityEntity,
        /** What the map and exports show: privacy zone and map-visibility trimming applied. */
        val path: List<LatLon>,
        val charts: Charts,
        val hiddenByPrivacy: Int,
        /** Recorded points with time, for replay and the timeline; empty when the map is hidden. */
        val playback: List<PlaybackPoint>,
        /** Every recorded point, for the crop preview. */
        val fullPath: List<LatLon>,
        val fullTimes: List<Long>,
    ) : DetailState
}

@HiltViewModel
class ActivityDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val activities: ActivityRepository,
    private val settings: SettingsRepository,
    private val files: TrackFileIo,
    private val media: MediaStore,
    records: RecordsRepository,
    private val composer: ShareComposer,
) : ViewModel() {
    private val id: Long = checkNotNull(savedState.get<Long>("id"))

    private val _state = MutableStateFlow<DetailState>(DetailState.Loading)
    val state: StateFlow<DetailState> = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val mapBase: StateFlow<MapBase> = settings.mapBase.stateIn(viewModelScope, SharingStarted.Eagerly, MapBase.STANDARD)

    val photos: StateFlow<List<Pair<MediaEntity, File>>> = media.forActivity(id)
        .map { list -> list.map { it to media.file(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val badges: StateFlow<List<RecordKey>> = records.records
        .map { it.badgesFor(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Podium places this activity holds, record first, then second and third places. */
    val achievements: StateFlow<List<Pair<RecordKey, Int>>> = records.records
        .map { set -> set.ranksFor(id).entries.sortedWith(compareBy({ it.value }, { it.key.ordinal })).map { it.key to it.value } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val recordSet = records.records.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val mapboxToken = settings.mapboxToken.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var points: List<TrackPointEntity> = emptyList()
    private var loadedFor: Long = -1

    init {
        viewModelScope.launch {
            combine(activities.activity(id), settings.privacyZone) { a, p -> a to p }.collect { (a, privacy) ->
                if (a == null) {
                    _state.value = DetailState.Missing
                    return@collect
                }
                // Crop and refresh rewrite the row; reload points whenever the stats changed.
                val stamp = a.distanceM.toBits() xor a.elapsedMs
                if (loadedFor != stamp) {
                    points = withContext(Dispatchers.IO) { activities.points(id) }
                    loadedFor = stamp
                }
                val visible = visiblePoints(privacy, a)
                _state.value = DetailState.Ready(
                    activity = a,
                    path = visible.map { LatLon(it.lat, it.lon) },
                    charts = buildCharts(points),
                    hiddenByPrivacy = points.size - visible.size,
                    playback = if (a.hideMap) emptyList() else visible.map { PlaybackPoint(it.lat, it.lon, it.timeMs, it.ele, it.speedMps, it.hr) },
                    fullPath = points.map { LatLon(it.lat, it.lon) },
                    fullTimes = points.map { it.timeMs },
                )
            }
        }
    }

    /** Privacy zone first, then the activity's own start/end hiding; a hidden map shows nothing. */
    private fun visiblePoints(privacy: PrivacyZoneSetting, a: ActivityEntity): List<TrackPointEntity> {
        if (a.hideMap) return emptyList()
        val c = privacy.center
        val zoned = if (privacy.enabled && c != null) PrivacyZone.trim(points, c, privacy.radiusM) { LatLon(it.lat, it.lon) } else points
        if (a.hideStartM <= 0 && a.hideEndM <= 0) return zoned
        val kept = TrackEdit.visiblePath(zoned.map { LatLon(it.lat, it.lon) }, a.hideStartM, a.hideEndM).toHashSet()
        return zoned.filter { LatLon(it.lat, it.lon) in kept }
    }

    private fun buildCharts(pts: List<TrackPointEntity>): Charts {
        val sampled = TrackStats.downsample(pts, 300)
        return Charts(
            elevation = sampled.mapNotNull { it.ele?.toFloat() },
            heartRate = sampled.mapNotNull { it.hr?.toFloat() },
            speedKmh = sampled.mapNotNull { it.speedMps?.let { s -> (s * 3.6).toFloat() } },
        )
    }

    fun updateMeta(name: String, sport: Sport, description: String) {
        viewModelScope.launch {
            activities.updateMeta(id, name, sport, description)
            _message.value = "Aktivitas diperbarui"
        }
    }

    fun setVisibility(hideStartM: Int, hideEndM: Int, hideMap: Boolean) {
        viewModelScope.launch { activities.setVisibility(id, hideStartM, hideEndM, hideMap) }
    }

    fun crop(fromMs: Long, toMs: Long) {
        viewModelScope.launch {
            activities.crop(id, fromMs, toMs, settings.athlete.first())
            loadedFor = -1
            _message.value = "Aktivitas dipotong, statistik dihitung ulang"
        }
    }

    fun refresh() {
        viewModelScope.launch {
            activities.refresh(id, settings.athlete.first())
            loadedFor = -1
            _message.value = "Statistik dihitung ulang dari titik rekaman"
        }
    }

    fun saveAsRoute() {
        viewModelScope.launch {
            _message.value = if (activities.saveAsRoute(id) != null) "Disimpan sebagai rute. Buka di Peta > Rute." else "Jalur terlalu pendek untuk dijadikan rute"
        }
    }

    fun addPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val n = media.add(id, uris)
            _message.value = if (n == uris.size) "$n foto ditambahkan" else "$n dari ${uris.size} foto bisa ditambahkan"
        }
    }

    fun deletePhoto(mediaId: Long) {
        viewModelScope.launch { media.delete(mediaId) }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            activities.delete(id)
            onDone()
        }
    }

    fun suggestedFileName(format: ExportFormat): String {
        val a = (state.value as? DetailState.Ready)?.activity
        return TrackFileIo.fileName(a?.name ?: "aktivitas", format.ext)
    }

    /** Exports honour the privacy zone and map visibility, same as the map, so a shared file never reveals home. */
    fun export(uri: Uri, format: ExportFormat) {
        val a = (state.value as? DetailState.Ready)?.activity ?: return
        viewModelScope.launch {
            val privacy = settings.privacyZone.first()
            val pts = if (a.hideMap) visiblePoints(privacy, a.copy(hideMap = false)) else visiblePoints(privacy, a)
            val track = Track(a.name, a.sport, pts.map { TrackPoint(it.lat, it.lon, it.ele, it.timeMs, it.hr, it.cadence, it.power) })
            val content = when (format) {
                ExportFormat.GPX -> Gpx.write(track)
                ExportFormat.TCX -> Tcx.write(track, a.sport)
            }
            _message.value = files.write(uri, content).fold(
                onSuccess = { "File ${format.ext.uppercase()} tersimpan" },
                onFailure = { "Ekspor gagal: ${it.message}" },
            )
        }
    }

    /** Everything the share image needs, from the same trimmed path the map shows. */
    fun shareRequest(photo: File?, base: MapBase): ShareRequest? {
        val s = state.value as? DetailState.Ready ?: return null
        return ShareRequest(
            activity = s.activity,
            path = s.path,
            elevation = if (s.activity.hideMap) emptyList() else s.charts.elevation,
            photo = photo,
            base = base,
            mapboxToken = mapboxToken.value,
            record = shareRecord(),
        )
    }

    /** The most prestigious record this activity holds: a timed effort over the longest distance first. */
    private fun shareRecord(): ShareRecord? {
        val set = recordSet.value ?: return null
        val held = set.badgesFor(id)
        val key = held.filter { it.isEffort }.maxByOrNull { it.meters } ?: held.firstOrNull() ?: return null
        val holder = set.holders[key] ?: return null
        val value = when {
            key.isEffort -> Format.duration((holder.value * 1000).toLong())
            key == RecordKey.RIDE_AVG_SPEED -> "${Format.oneDecimal(holder.value).replace('.', ',')} km/h"
            key == RecordKey.RIDE_ELEVATION -> "${Format.integer(holder.value)} m"
            else -> "${Format.km(holder.value).replace('.', ',')} km"
        }
        return ShareRecord("Rekor pribadi · ${key.label}", value, null, 1)
    }

    fun shareAvailable(template: ShareTemplate, request: ShareRequest): Boolean = composer.available(template, request)

    suspend fun renderShare(request: ShareRequest, template: ShareTemplate, format: ShareFormat, scale: Float = 1f): Bitmap =
        composer.render(request, template, format, scale)

    suspend fun shareUri(bmp: Bitmap, template: ShareTemplate): Uri = composer.cacheForShare(bmp, template)

    suspend fun saveShare(bmp: Bitmap, template: ShareTemplate): String =
        composer.saveToGallery(bmp, template).fold(
            onSuccess = { "Tersimpan di Galeri, folder Pictures/Strideway" },
            onFailure = { "Gagal menyimpan: ${it.message}" },
        )

    /** A gallery photo picked only for sharing is attached to the activity as well, so it can be reused. */
    suspend fun importSharePhoto(uri: Uri): File? = media.addOne(id, uri)

    fun clearMessage() {
        _message.value = null
    }
}
