package id.web.agentvaedah.strideway.ui.record

import android.Manifest
import android.os.Build
import android.view.WindowManager
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.web.agentvaedah.strideway.core.model.Sport
import id.web.agentvaedah.strideway.service.RecordingState
import id.web.agentvaedah.strideway.ui.theme.Palette

@Composable
fun RecordScreen(onOpenActivity: (Long) -> Unit, vm: RecordViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val recovered by vm.recovered.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Volume keys belong to this screen only while it is on top.
    DisposableEffect(Unit) {
        vm.setKeysActive(true)
        onDispose { vm.setKeysActive(false) }
    }

    val active = state is RecordingState.Active
    DisposableEffect(active) {
        val window = (context as? android.app.Activity)?.window
        if (active) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    LaunchedEffect(state) {
        val s = state
        if (s is RecordingState.Finished) {
            vm.acknowledgeFinished()
            onOpenActivity(s.activityId)
        }
    }

    recovered?.let { id ->
        AlertDialog(
            onDismissRequest = vm::dismissRecovered,
            title = { Text("Rekaman terakhir terputus") },
            text = { Text("Aplikasi tertutup saat merekam. Titik yang sempat tersimpan sudah dijadikan aktivitas.") },
            confirmButton = {
                TextButton(onClick = { vm.dismissRecovered(); onOpenActivity(id) }) { Text("Lihat aktivitas") }
            },
            dismissButton = { TextButton(onClick = vm::dismissRecovered) { Text("Tutup") } },
        )
    }

    when (val s = state) {
        is RecordingState.Active -> LiveDashboard(s, vm)
        else -> ReadyPanel(vm)
    }
}

@Composable
private fun ReadyPanel(vm: RecordViewModel) {
    val sport by vm.sport.collectAsStateWithLifecycle()
    val navRoute by vm.navRoute.collectAsStateWithLifecycle()
    val gpsError by vm.gpsError.collectAsStateWithLifecycle()
    var permissionDenied by remember { mutableStateOf(false) }
    var gpsOff by remember { mutableStateOf(false) }

    val permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        addAll(vm.blePermissions())
    }.distinct().toTypedArray()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val locationOk = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        permissionDenied = !locationOk
        if (locationOk) {
            if (vm.gpsEnabled()) vm.start() else gpsOff = true
        }
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Siap merekam", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Saat merekam, tombol Volume Atas menjeda atau melanjutkan, Volume Bawah berganti tampilan peta dan metrik.",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(selected = sport == Sport.RIDE, onClick = { vm.selectSport(Sport.RIDE) }, label = { Text("Ride") })
            FilterChip(selected = sport == Sport.RUN, onClick = { vm.selectSport(Sport.RUN) }, label = { Text("Run") })
        }
        navRoute?.let { r ->
            Spacer(Modifier.height(16.dp))
            Text("Rute navigasi: ${r.name}", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = vm::clearNavRoute) { Text("Lepas rute") }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { launcher.launch(permissions) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(if (sport == Sport.RIDE) "Mulai ride" else "Mulai run", style = MaterialTheme.typography.titleMedium)
        }
        val message = when {
            permissionDenied -> "Izin lokasi ditolak. Strideway butuh GPS untuk merekam jarak dan rute. Buka Pengaturan > Aplikasi > Strideway > Izin."
            gpsOff -> "GPS mati. Aktifkan Lokasi di panel cepat, lalu tekan Mulai lagi."
            gpsError != null -> gpsError
            else -> null
        }
        if (message != null) {
            Spacer(Modifier.height(16.dp))
            Text(message, color = Palette.Error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun LiveDashboard(s: RecordingState.Active, vm: RecordViewModel) {
    val display by vm.display.collectAsStateWithLifecycle()
    val mapBase by vm.mapBase.collectAsStateWithLifecycle()
    val navRoute by vm.navRoute.collectAsStateWithLifecycle()
    val athlete by vm.athlete.collectAsStateWithLifecycle()
    var confirmStop by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        if (display != DisplayMode.METRICS) {
            LiveMap(
                state = s,
                base = mapBase,
                navRoute = navRoute?.points.orEmpty(),
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
        if (display != DisplayMode.MAP) {
            MetricsDrawer(
                state = s,
                athlete = athlete,
                fullHeight = display == DisplayMode.METRICS,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
        ControlBar(
            paused = s.metrics.manualPaused,
            onPause = vm::togglePause,
            onStop = { confirmStop = true },
            onCycle = vm::cycleDisplay,
        )
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("Selesai merekam?") },
            text = { Text("Simpan untuk menambah aktivitas ke Home, atau buang jika rekaman ini tidak dipakai.") },
            confirmButton = { TextButton(onClick = { confirmStop = false; vm.stop() }) { Text("Simpan") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmStop = false; vm.discard() }) { Text("Buang", color = Palette.Error) }
                    TextButton(onClick = { confirmStop = false }) { Text("Lanjut rekam") }
                }
            },
        )
    }
}
