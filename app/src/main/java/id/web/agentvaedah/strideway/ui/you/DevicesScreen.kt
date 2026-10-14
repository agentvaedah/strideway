package id.web.agentvaedah.strideway.ui.you

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.web.agentvaedah.strideway.data.ble.BleHub
import id.web.agentvaedah.strideway.data.ble.ScannedDevice
import id.web.agentvaedah.strideway.data.ble.SensorStatus
import id.web.agentvaedah.strideway.data.db.SensorDao
import id.web.agentvaedah.strideway.data.db.SensorEntity
import id.web.agentvaedah.strideway.data.location.MotionSensors
import id.web.agentvaedah.strideway.data.prefs.SettingsRepository
import id.web.agentvaedah.strideway.ui.components.BackTopBar
import id.web.agentvaedah.strideway.ui.components.EmptyState
import id.web.agentvaedah.strideway.ui.components.SectionTitle
import id.web.agentvaedah.strideway.ui.theme.Palette
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val ble: BleHub,
    sensorDao: SensorDao,
    private val settings: SettingsRepository,
    motion: MotionSensors,
) : ViewModel() {
    val phone = PhoneSensors(motion.hasCompass, motion.hasBarometer, motion.hasThermometer)
    val saved: StateFlow<List<SensorEntity>> = sensorDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val status: StateFlow<Map<String, SensorStatus>> = ble.status
    val scanResults: StateFlow<List<ScannedDevice>> = ble.scanResults
    val scanning: StateFlow<Boolean> = ble.scanning
    val wheel: StateFlow<Double> = settings.wheelCircumferenceM.stateIn(viewModelScope, SharingStarted.Eagerly, 2.105)

    val supported get() = ble.isSupported()
    fun enabled() = ble.isEnabled()
    fun permissions() = ble.requiredPermissions()
    fun hasPermissions() = ble.hasPermissions()

    fun scan() = ble.startScan()
    fun stopScan() = ble.stopScan()
    fun pair(d: ScannedDevice) = viewModelScope.launch { ble.pair(d) }
    fun forget(address: String) = viewModelScope.launch { ble.forget(address) }
    fun connect(s: SensorEntity) = ble.connect(s.address, s.name, s.kind)
    fun disconnect(address: String) = ble.disconnect(address)

    fun saveWheel(mm: Int) = viewModelScope.launch {
        val m = mm / 1000.0
        settings.setWheelCircumference(m)
        ble.wheelCircumferenceM = m
    }

    override fun onCleared() = ble.stopScan()
}

@Composable
fun DevicesScreen(onBack: () -> Unit, vm: DevicesViewModel = hiltViewModel()) {
    val saved by vm.saved.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val results by vm.scanResults.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val wheel by vm.wheel.collectAsStateWithLifecycle()
    var problem by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        problem = if (result.values.all { it }) {
            if (vm.enabled()) { vm.scan(); null } else "Bluetooth mati. Aktifkan Bluetooth, lalu cari lagi."
        } else {
            "Izin Bluetooth ditolak, jadi sensor tidak bisa dicari. Beri izin Perangkat sekitar di Pengaturan aplikasi."
        }
    }

    DisposableEffect(Unit) { onDispose { vm.stopScan() } }

    Column(Modifier.fillMaxSize()) {
        BackTopBar("Perangkat", onBack)
        if (!vm.supported) {
            EmptyState("Bluetooth LE tidak tersedia", "Perangkat ini tidak mendukung Bluetooth Low Energy, jadi sensor eksternal tidak bisa dipakai.")
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Text(
                    "Mendukung sensor Bluetooth standar: detak jantung, speed/cadence, power meter, serta suhu dan tekanan " +
                        "(Environmental Sensing). Untuk jam tangan atau smartband, aktifkan siaran detak jantung (HR broadcast) " +
                        "di perangkat agar terdeteksi. Barometer, altimeter, dan kompas jam tidak dikirim lewat Bluetooth standar, " +
                        "jadi Strideway memakai sensor ponsel.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextMuted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item { PhoneSensorsSection(vm.phone) }
            item { SectionTitle("Tersimpan") }
            if (saved.isEmpty()) {
                item { Text("Belum ada sensor tersimpan.", color = Palette.TextMuted, modifier = Modifier.padding(horizontal = 16.dp)) }
            }
            items(saved, key = { it.address }) { s ->
                SavedRow(s, status[s.address], onConnect = { vm.connect(s) }, onDisconnect = { vm.disconnect(s.address) }, onForget = { vm.forget(s.address) })
            }
            item {
                SectionTitle("Cari sensor baru") {
                    if (scanning) {
                        TextButton(onClick = vm::stopScan) { Text("Berhenti") }
                    } else {
                        Button(onClick = {
                            if (vm.hasPermissions()) {
                                if (vm.enabled()) { problem = null; vm.scan() } else problem = "Bluetooth mati. Aktifkan Bluetooth, lalu cari lagi."
                            } else {
                                launcher.launch(vm.permissions())
                            }
                        }) { Text("Cari sensor") }
                    }
                }
            }
            problem?.let { item { Text(it, color = Palette.Error, modifier = Modifier.padding(horizontal = 16.dp)) } }
            if (scanning && results.isEmpty()) {
                item {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("  Mencari selama 15 detik. Bangunkan sensor dengan memakai band atau memutar pedal.", color = Palette.TextMuted)
                    }
                }
            }
            if (!scanning && results.isEmpty() && problem == null) {
                item { Text("Tekan Cari sensor saat sensor menyala dan dekat ponsel.", color = Palette.TextMuted, modifier = Modifier.padding(horizontal = 16.dp)) }
            }
            items(results.filter { r -> saved.none { it.address == r.address } }, key = { "scan-" + it.address }) { d ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(d.name, style = MaterialTheme.typography.titleSmall)
                        Text("${d.kind.label}, sinyal ${d.rssi} dBm", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                    }
                    Button(onClick = { vm.pair(d) }) { Text("Tambahkan") }
                }
                HorizontalDivider(color = Palette.Outline)
            }
            item { WheelSetting(wheel, vm::saveWheel) }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun SavedRow(s: SensorEntity, st: SensorStatus?, onConnect: () -> Unit, onDisconnect: () -> Unit, onForget: () -> Unit) {
    val (label, color) = when {
        st?.connected == true -> "Tersambung" to Palette.Success
        st?.connecting == true -> "Menyambung" to Palette.Warning
        st != null -> "Terputus, mencoba lagi" to Palette.Error
        else -> "Tidak aktif. Tersambung otomatis saat mulai rekam" to Palette.TextMuted
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.name, style = MaterialTheme.typography.titleSmall)
                Text(s.kind.label + (st?.batteryPct?.let { ", baterai $it%" } ?: ""), style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                Text(label, style = MaterialTheme.typography.bodySmall, color = color)
            }
            if (st == null) TextButton(onClick = onConnect) { Text("Sambungkan") } else TextButton(onClick = onDisconnect) { Text("Putuskan") }
            TextButton(onClick = onForget) { Text("Lupakan", color = Palette.Error) }
        }
    }
    HorizontalDivider(color = Palette.Outline)
}

@Composable
private fun WheelSetting(currentM: Double, onSave: (Int) -> Unit) {
    var text by remember(currentM) { mutableStateOf((currentM * 1000).toInt().toString()) }
    val mm = text.toIntOrNull()
    val valid = mm != null && mm in 1000..3000
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Keliling roda", style = MaterialTheme.typography.titleSmall)
        Text("Dipakai sensor kecepatan. 700x25c = 2105 mm, 700x28c = 2136 mm, 29x2.2 = 2288 mm.", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(4) },
                label = { Text("mm") },
                singleLine = true,
                isError = !valid,
                supportingText = { if (!valid) Text("Isi 1000 sampai 3000 mm") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { mm?.let(onSave) }, enabled = valid && mm != (currentM * 1000).toInt()) {
                Text("Simpan")
            }
        }
        Text(String.format(Locale.US, "Tersimpan: %.3f m", currentM), style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
    }
}

data class PhoneSensors(val compass: Boolean, val barometer: Boolean, val thermometer: Boolean)

/** What this phone can measure on its own, so the rider knows where altitude, heading and temperature come from. */
@Composable
private fun PhoneSensorsSection(p: PhoneSensors) {
    SectionTitle("Sensor ponsel")
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CapabilityRow("Kompas", p.compass, if (p.compass) "Arah di peta dan saat merekam" else "Arah diambil dari GPS saat bergerak")
        CapabilityRow("Barometer", p.barometer, if (p.barometer) "Tekanan udara dan elevasi yang halus" else "Elevasi memakai GPS")
        CapabilityRow("Altimeter", p.barometer, if (p.barometer) "Dari barometer, dikalibrasi dengan GPS" else "Dari GPS, kurang halus di tanjakan")
        CapabilityRow(
            "Suhu",
            p.thermometer,
            if (p.thermometer) "Sensor suhu ponsel" else "Dari data cuaca, atau sensor Bluetooth suhu bila tersambung",
        )
    }
}

@Composable
private fun CapabilityRow(name: String, available: Boolean, detail: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
        }
        Text(
            if (available) "Ada" else "Tidak ada",
            style = MaterialTheme.typography.labelLarge,
            color = if (available) Palette.Success else Palette.TextMuted,
        )
    }
}
