package id.web.agentvaedah.strideway.ui.activity

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.web.agentvaedah.strideway.core.model.LatLon
import id.web.agentvaedah.strideway.core.model.Track
import id.web.agentvaedah.strideway.core.model.TrackPoint
import id.web.agentvaedah.strideway.core.privacy.PrivacyZone
import id.web.agentvaedah.strideway.core.track.Gpx
import id.web.agentvaedah.strideway.core.track.Tcx
import id.web.agentvaedah.strideway.core.track.TrackStats
import id.web.agentvaedah.strideway.data.db.ActivityEntity
import id.web.agentvaedah.strideway.data.db.TrackPointEntity
import id.web.agentvaedah.strideway.data.files.TrackFileIo
import id.web.agentvaedah.strideway.data.prefs.MapBase
import id.web.agentvaedah.strideway.data.prefs.PrivacyZoneSetting
import id.web.agentvaedah.strideway.data.prefs.SettingsRepository
import id.web.agentvaedah.strideway.data.repo.ActivityRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class ExportFormat(val ext: String, val mime: String) {
    GPX("gpx", "application/gpx+xml"),
    TCX("tcx", "application/vnd.garmin.tcx+xml"),
}

data class Charts(val elevation: List<Float>, val heartRate: List<Float>, val speedKmh: List<Float>)

sealed interface DetailState {
    data object Loading : DetailState
    data object Missing : DetailState
    data class Ready(val activity: ActivityEntity, val path: List<LatLon>, val charts: Charts, val hiddenByPrivacy: Int) : DetailState
}

@HiltViewModel
class ActivityDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val activities: ActivityRepository,
    private val settings: SettingsRepository,
    private val files: TrackFileIo,
) : ViewModel() {
    private val id: Long = checkNotNull(savedState.get<Long>("id"))

    private val _state = MutableStateFlow<DetailState>(DetailState.Loading)
    val state: StateFlow<DetailState> = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val mapBase: StateFlow<MapBase> = settings.mapBase.stateIn(viewModelScope, SharingStarted.Eagerly, MapBase.STANDARD)

    private var points: List<TrackPointEntity> = emptyList()

    init {
        viewModelScope.launch {
            activities.activity(id).collect { a ->
                if (a == null) {
                    _state.value = DetailState.Missing
                    return@collect
                }
                if (points.isEmpty()) points = withContext(Dispatchers.IO) { activities.points(id) }
                val privacy = settings.privacyZone.first()
                val visible = visiblePoints(privacy)
                _state.value = DetailState.Ready(
                    activity = a,
                    path = visible.map { LatLon(it.lat, it.lon) },
                    charts = buildCharts(points),
                    hiddenByPrivacy = points.size - visible.size,
                )
            }
        }
    }

    private fun visiblePoints(privacy: PrivacyZoneSetting): List<TrackPointEntity> {
        val c = privacy.center
        return if (privacy.enabled && c != null) PrivacyZone.trim(points, c, privacy.radiusM) { LatLon(it.lat, it.lon) } else points
    }

    private fun buildCharts(pts: List<TrackPointEntity>): Charts {
        val sampled = TrackStats.downsample(pts, 300)
        return Charts(
            elevation = sampled.mapNotNull { it.ele?.toFloat() },
            heartRate = sampled.mapNotNull { it.hr?.toFloat() },
            speedKmh = sampled.mapNotNull { it.speedMps?.let { s -> (s * 3.6).toFloat() } },
        )
    }

    fun rename(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { activities.rename(id, name) }
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

    /** Exports honour the privacy zone, same as the map, so a shared file never reveals home. */
    fun export(uri: Uri, format: ExportFormat) {
        val a = (state.value as? DetailState.Ready)?.activity ?: return
        viewModelScope.launch {
            val privacy = settings.privacyZone.first()
            val track = Track(
                a.name,
                a.sport,
                visiblePoints(privacy).map { TrackPoint(it.lat, it.lon, it.ele, it.timeMs, it.hr, it.cadence, it.power) },
            )
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

    fun clearMessage() {
        _message.value = null
    }
}
