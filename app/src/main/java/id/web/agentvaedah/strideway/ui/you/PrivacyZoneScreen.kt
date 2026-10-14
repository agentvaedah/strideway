package id.web.agentvaedah.strideway.ui.you

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.web.agentvaedah.strideway.core.model.LatLon
import id.web.agentvaedah.strideway.data.location.GpsSource
import id.web.agentvaedah.strideway.data.prefs.MapBase
import id.web.agentvaedah.strideway.data.prefs.PrivacyZoneSetting
import id.web.agentvaedah.strideway.data.prefs.SettingsRepository
import id.web.agentvaedah.strideway.ui.components.BackTopBar
import id.web.agentvaedah.strideway.ui.components.MapHost
import id.web.agentvaedah.strideway.ui.components.MapLayers
import id.web.agentvaedah.strideway.ui.components.toLatLng
import id.web.agentvaedah.strideway.ui.theme.Palette
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import javax.inject.Inject

@HiltViewModel
class PrivacyZoneViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val gps: GpsSource,
) : ViewModel() {
    val zone: StateFlow<PrivacyZoneSetting?> = settings.privacyZone.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun lastKnown(): LatLon? = gps.lastKnown()

    fun save(z: PrivacyZoneSetting) = viewModelScope.launch { settings.savePrivacyZone(z) }
}

@Composable
fun PrivacyZoneScreen(onBack: () -> Unit, vm: PrivacyZoneViewModel = hiltViewModel()) {
    val stored by vm.zone.collectAsStateWithLifecycle()
    val initial = stored ?: return
    var enabled by remember(initial) { mutableStateOf(initial.enabled) }
    var center by remember(initial) { mutableStateOf(initial.center) }
    var radius by remember(initial) { mutableFloatStateOf(initial.radiusM.toFloat()) }
    var style by remember { mutableStateOf<Style?>(null) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var savedNote by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        BackTopBar("Privacy Zone", onBack)
        Text(
            "Titik dalam lingkaran ini tidak tampil di sketsa feed, peta aktivitas, maupun file ekspor, " +
                "jadi lokasi rumah tidak terbaca dari awal dan akhir rute. Ketuk peta untuk memindahkan pusat.",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextMuted,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Aktifkan", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = { enabled = it; savedNote = false })
        }
        Text("Radius ${radius.toInt()} m", modifier = Modifier.padding(horizontal = 16.dp))
        Slider(
            value = radius,
            onValueChange = { radius = (it / 50f).toInt() * 50f; savedNote = false },
            valueRange = 200f..1000f,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Box(Modifier.fillMaxWidth().weight(1f)) {
            MapHost(
                base = MapBase.STANDARD,
                modifier = Modifier.matchParentSize(),
                onMapReady = { m ->
                    map = m
                    val start = center ?: vm.lastKnown()
                    start?.let { m.moveCamera(CameraUpdateFactory.newLatLngZoom(it.toLatLng(), 14.0)) }
                    m.addOnMapClickListener { p ->
                        center = LatLon(p.latitude, p.longitude)
                        savedNote = false
                        true
                    }
                },
                onStyleLoaded = { _, st -> style = st },
            )
            if (center == null) {
                Text(
                    "Ketuk lokasi rumah di peta",
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(12.dp)
                        .background(Palette.Bg.copy(alpha = 0.85f), MaterialTheme.shapes.small)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        val zoneColor = Palette.Warning
        LaunchedEffect(style, center, radius, zoneColor) {
            val st = style ?: return@LaunchedEffect
            val c = center
            MapLayers.setLine(st, "privacy-ring", if (c != null) MapLayers.ring(c, radius.toDouble()) else emptyList(), zoneColor, 3f)
            MapLayers.setPoint(st, "privacy-center", c, zoneColor, 5f)
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    vm.save(PrivacyZoneSetting(enabled && center != null, center, radius.toDouble()))
                    savedNote = true
                },
                enabled = !enabled || center != null,
            ) { Text("Simpan") }
            if (savedNote) Text("  Tersimpan", color = Palette.Success)
            if (enabled && center == null) Text("  Pilih pusat dulu di peta", color = Palette.Warning)
        }
    }
}
