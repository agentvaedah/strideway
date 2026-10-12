package id.web.agentvaedah.strideway.ui.activity

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.web.agentvaedah.strideway.core.format.Format
import id.web.agentvaedah.strideway.core.model.Sport
import id.web.agentvaedah.strideway.data.db.ActivityEntity
import id.web.agentvaedah.strideway.ui.components.BackTopBar
import id.web.agentvaedah.strideway.ui.components.EmptyState
import id.web.agentvaedah.strideway.ui.components.LineChart
import id.web.agentvaedah.strideway.ui.components.LoadingState
import id.web.agentvaedah.strideway.ui.components.MapHost
import id.web.agentvaedah.strideway.ui.components.MapLayers
import id.web.agentvaedah.strideway.ui.components.Series
import id.web.agentvaedah.strideway.ui.components.StatPair
import id.web.agentvaedah.strideway.ui.theme.MetricText
import id.web.agentvaedah.strideway.ui.theme.Palette
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val detailDate = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy · HH:mm", Locale.forLanguageTag("id-ID"))

@Composable
fun ActivityDetailScreen(onBack: () -> Unit, vm: ActivityDetailViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    val gpxLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.GPX.mime)) { uri ->
        uri?.let { vm.export(it, ExportFormat.GPX) }
    }
    val tcxLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.TCX.mime)) { uri ->
        uri?.let { vm.export(it, ExportFormat.TCX) }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            BackTopBar("Aktivitas", onBack) {
                if (state is DetailState.Ready) {
                    IconButton(onClick = { renaming = true }) { Icon(Icons.Outlined.Edit, contentDescription = "Ganti nama") }
                    IconButton(onClick = { deleting = true }) { Icon(Icons.Outlined.Delete, contentDescription = "Hapus aktivitas") }
                }
            }
            when (val s = state) {
                DetailState.Loading -> LoadingState("Memuat aktivitas")
                DetailState.Missing -> EmptyState("Aktivitas tidak ditemukan", "Aktivitas ini mungkin sudah dihapus.", actionLabel = "Kembali", onAction = onBack)
                is DetailState.Ready -> DetailBody(
                    s,
                    vm,
                    onExportGpx = { gpxLauncher.launch(vm.suggestedFileName(ExportFormat.GPX)) },
                    onExportTcx = { tcxLauncher.launch(vm.suggestedFileName(ExportFormat.TCX)) },
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    val ready = state as? DetailState.Ready
    if (renaming && ready != null) {
        var name by remember { mutableStateOf(ready.activity.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Ganti nama") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it.take(80) }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = { vm.rename(name); renaming = false }, enabled = name.isNotBlank()) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Batal") } },
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Hapus aktivitas ini?") },
            text = { Text("Jalur, statistik, dan kontribusinya ke skor kebugaran ikut terhapus. Tidak bisa dibatalkan.") },
            confirmButton = {
                TextButton(onClick = { deleting = false; vm.delete(onBack) }) { Text("Hapus", color = Palette.Error) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Batal") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailBody(s: DetailState.Ready, vm: ActivityDetailViewModel, onExportGpx: () -> Unit, onExportTcx: () -> Unit) {
    val base by vm.mapBase.collectAsStateWithLifecycle()
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val a = s.activity

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(a.name, style = MaterialTheme.typography.headlineSmall)
            Text(
                "${if (a.sport == Sport.RIDE) "Ride" else "Run"} · " +
                    Instant.ofEpochMilli(a.startMs).atZone(ZoneId.systemDefault()).format(detailDate),
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextMuted,
            )
        }
        Spacer(Modifier.height(12.dp))
        if (s.path.size >= 2) {
            MapHost(
                base = base,
                modifier = Modifier.fillMaxWidth().height(260.dp),
                onMapReady = { map = it },
                onStyleLoaded = { m, st ->
                    style = st
                    MapLayers.setLine(st, "activity", s.path, Palette.Accent, 4f)
                    MapLayers.fit(m, s.path)
                },
            )
            LaunchedEffect(style, s.path) {
                val st = style ?: return@LaunchedEffect
                MapLayers.setLine(st, "activity", s.path, Palette.Accent, 4f)
                map?.let { MapLayers.fit(it, s.path) }
            }
        } else {
            Text(
                "Tidak ada jalur yang bisa ditampilkan" + if (s.hiddenByPrivacy > 0) " karena seluruhnya berada di Privacy Zone." else ".",
                color = Palette.TextMuted,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (s.hiddenByPrivacy > 0 && s.path.size >= 2) {
            Text(
                "${s.hiddenByPrivacy} titik di dalam Privacy Zone disembunyikan.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextMuted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        Spacer(Modifier.height(16.dp))
        PrimaryStats(a)
        Spacer(Modifier.height(16.dp))
        FlowRow(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatPair("waktu total", Format.duration(a.elapsedMs))
            if (a.sport == Sport.RUN) {
                StatPair("pace rata-rata /km", Format.pace(a.avgSpeedKmh / 3.6))
            } else {
                StatPair("km/j rata-rata", Format.oneDecimal(a.avgSpeedKmh))
            }
            StatPair("km/j maks", Format.oneDecimal(a.maxSpeedKmh))
            StatPair("kkal", Format.integer(a.calories))
            a.avgHr?.let { StatPair("HR rata-rata", "$it") }
            a.maxHr?.let { StatPair("HR maks", "$it") }
            a.avgPower?.let { StatPair("watt rata-rata", "$it") }
            StatPair(if (a.trimpEstimated) "TRIMP (estimasi)" else "TRIMP", Format.integer(a.trimp))
        }

        ChartBlock("Elevasi (m)", s.charts.elevation, Palette.TextMuted, fill = true)
        ChartBlock("Detak jantung (bpm)", s.charts.heartRate, Palette.Error)
        ChartBlock("Kecepatan (km/j)", s.charts.speedKmh, Palette.Info)

        Spacer(Modifier.height(16.dp))
        Text("Ekspor", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
        Text(
            "File untuk diunggah ke Strava, Garmin Connect, atau panitia event.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextMuted,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onExportGpx) { Text("Simpan GPX") }
            OutlinedButton(onClick = onExportTcx) { Text("Simpan TCX") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PrimaryStats(a: ActivityEntity) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Jarak", style = MetricText.Label, color = Palette.TextMuted)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Format.km(a.distanceM), style = MetricText.Large)
                Spacer(Modifier.width(4.dp))
                Text("km", style = MetricText.Label, color = Palette.TextMuted, modifier = Modifier.padding(bottom = 6.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text("Waktu bergerak", style = MetricText.Label, color = Palette.TextMuted)
            Text(Format.duration(a.movingMs), style = MetricText.Large)
        }
        Column(Modifier.weight(0.8f)) {
            Text("Naik", style = MetricText.Label, color = Palette.TextMuted)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Format.integer(a.elevationGainM), style = MetricText.Large)
                Spacer(Modifier.width(4.dp))
                Text("m", style = MetricText.Label, color = Palette.TextMuted, modifier = Modifier.padding(bottom = 6.dp))
            }
        }
    }
}

@Composable
private fun ChartBlock(title: String, values: List<Float>, color: Color, fill: Boolean = false) {
    if (values.size < 2) return
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                "${Format.integer(values.min().toDouble())} - ${Format.integer(values.max().toDouble())}",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextMuted,
            )
        }
        LineChart(listOf(Series(values, color)), "$title sepanjang aktivitas", height = 90.dp, fillFirst = fill)
    }
}
