package id.web.agentvaedah.strideway.ui.activity

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AltRoute
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.web.agentvaedah.strideway.core.format.Format
import id.web.agentvaedah.strideway.core.model.Sport
import id.web.agentvaedah.strideway.ui.components.BackTopBar
import id.web.agentvaedah.strideway.ui.components.BentoCell
import id.web.agentvaedah.strideway.ui.components.BentoGrid
import id.web.agentvaedah.strideway.ui.components.BentoStat
import id.web.agentvaedah.strideway.ui.components.EmptyState
import id.web.agentvaedah.strideway.ui.components.LineChart
import id.web.agentvaedah.strideway.ui.components.LoadingState
import id.web.agentvaedah.strideway.ui.components.Series
import id.web.agentvaedah.strideway.ui.motion.staggeredEnter
import id.web.agentvaedah.strideway.ui.theme.MetricText
import id.web.agentvaedah.strideway.ui.theme.Palette
import id.web.agentvaedah.strideway.ui.you.Medal
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val detailDate = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy · HH:mm", Locale.forLanguageTag("id-ID"))

private enum class DetailSheet { NONE, EDIT, CROP, VISIBILITY, DELETE, SHARE }

@Composable
fun ActivityDetailScreen(onBack: () -> Unit, vm: ActivityDetailViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val base by vm.mapBase.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var sheet by remember { mutableStateOf(DetailSheet.NONE) }
    var menu by remember { mutableStateOf(false) }
    var sharePhoto by remember { mutableStateOf<File?>(null) }

    val gpxLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.GPX.mime)) { uri ->
        uri?.let { vm.export(it, ExportFormat.GPX) }
    }
    val tcxLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.TCX.mime)) { uri ->
        uri?.let { vm.export(it, ExportFormat.TCX) }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        vm.addPhotos(uris)
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
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Menu aktivitas") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            MenuItem("Bagikan dengan foto", Icons.Outlined.Share) { menu = false; sheet = DetailSheet.SHARE }
                            MenuItem("Tambah media", Icons.Outlined.AddPhotoAlternate) {
                                menu = false
                                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                            MenuItem("Edit aktivitas", Icons.Outlined.Edit) { menu = false; sheet = DetailSheet.EDIT }
                            MenuItem("Potong aktivitas", Icons.Outlined.ContentCut) { menu = false; sheet = DetailSheet.CROP }
                            MenuItem("Visibilitas peta", Icons.Outlined.VisibilityOff) { menu = false; sheet = DetailSheet.VISIBILITY }
                            MenuItem("Simpan sebagai rute", Icons.Outlined.AltRoute) { menu = false; vm.saveAsRoute() }
                            MenuItem("Hitung ulang", Icons.Outlined.Refresh) { menu = false; vm.refresh() }
                            MenuItem("Hapus aktivitas", Icons.Outlined.Delete, danger = true) { menu = false; sheet = DetailSheet.DELETE }
                        }
                    }
                }
            }
            when (val s = state) {
                DetailState.Loading -> LoadingState("Memuat aktivitas")
                DetailState.Missing -> EmptyState("Aktivitas tidak ditemukan", "Aktivitas ini mungkin sudah dihapus.", actionLabel = "Kembali", onAction = onBack)
                is DetailState.Ready -> DetailBody(
                    s,
                    vm,
                    onSharePhoto = { f -> sharePhoto = f; sheet = DetailSheet.SHARE },
                    onExportGpx = { gpxLauncher.launch(vm.suggestedFileName(ExportFormat.GPX)) },
                    onExportTcx = { tcxLauncher.launch(vm.suggestedFileName(ExportFormat.TCX)) },
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    val ready = state as? DetailState.Ready ?: return
    when (sheet) {
        DetailSheet.EDIT -> EditActivitySheet(ready.activity, vm::updateMeta) { sheet = DetailSheet.NONE }
        DetailSheet.CROP -> CropSheet(ready.fullPath, ready.fullTimes, base, vm::crop) { sheet = DetailSheet.NONE }
        DetailSheet.VISIBILITY -> MapVisibilitySheet(ready.activity, vm::setVisibility) { sheet = DetailSheet.NONE }
        DetailSheet.DELETE -> AlertDialog(
            onDismissRequest = { sheet = DetailSheet.NONE },
            title = { Text("Hapus aktivitas ini?") },
            text = { Text("Jalur, foto, statistik, rekor pribadi, dan kontribusinya ke skor kebugaran ikut terhapus. Tidak bisa dibatalkan.") },
            confirmButton = {
                TextButton(onClick = { sheet = DetailSheet.NONE; vm.delete(onBack) }) { Text("Hapus", color = Palette.Error) }
            },
            dismissButton = { TextButton(onClick = { sheet = DetailSheet.NONE }) { Text("Batal") } },
        )
        DetailSheet.SHARE -> {
            val photos by vm.photos.collectAsStateWithLifecycle()
            ShareSheet(vm, photos, initialPhoto = sharePhoto, onDismiss = { sheet = DetailSheet.NONE; sharePhoto = null })
        }
        DetailSheet.NONE -> Unit
    }
}

@Composable
private fun MenuItem(label: String, icon: ImageVector, danger: Boolean = false, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, color = if (danger) Palette.Error else Palette.Text) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = if (danger) Palette.Error else Palette.TextMuted) },
        onClick = onClick,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailBody(
    s: DetailState.Ready,
    vm: ActivityDetailViewModel,
    onSharePhoto: (File) -> Unit,
    onExportGpx: () -> Unit,
    onExportTcx: () -> Unit,
) {
    val base by vm.mapBase.collectAsStateWithLifecycle()
    val achievements by vm.achievements.collectAsStateWithLifecycle()
    val photos by vm.photos.collectAsStateWithLifecycle()
    var progress by remember { mutableStateOf<Float?>(null) }
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
        if (achievements.isNotEmpty()) {
            Text("Pencapaian", style = MaterialTheme.typography.titleSmall, color = Palette.TextMuted, modifier = Modifier.padding(start = 16.dp, top = 10.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(achievements, key = { _, it -> it.first.name }) { i, (key, rank) ->
                    Row(
                        Modifier
                            .staggeredEnter(i)
                            .background(Palette.Surface, MaterialTheme.shapes.large)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Medal(rank, 34.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(key.label, style = MaterialTheme.typography.labelLarge, color = Palette.Text, maxLines = 1)
                            Text(if (rank == 1) "Rekor pribadi" else "Terbaik ke-$rank", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                        }
                    }
                }
            }
        }
        if (a.description.isNotBlank()) {
            Text(a.description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        Spacer(Modifier.height(8.dp))
        if (s.path.size >= 2) {
            RoutePlayer(
                path = s.path,
                playback = s.playback,
                base = base,
                progress = progress,
                onProgress = { progress = it },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                when {
                    a.hideMap -> "Peta aktivitas ini disembunyikan. Ubah lewat menu > Visibilitas peta."
                    s.hiddenByPrivacy > 0 -> "Tidak ada jalur yang bisa ditampilkan karena seluruhnya berada di Privacy Zone."
                    else -> "Tidak ada jalur yang bisa ditampilkan."
                },
                color = Palette.TextMuted,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (s.hiddenByPrivacy > 0 && s.path.size >= 2) {
            Text(
                "${s.hiddenByPrivacy} titik disembunyikan oleh Privacy Zone atau pengaturan visibilitas.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextMuted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        MediaStrip(photos, vm::deletePhoto, onOpen = onSharePhoto)

        Spacer(Modifier.height(16.dp))
        // Distance leads (the number riders share); time and climb sit beside it, the rest below in pairs.
        BentoGrid(
            buildList {
                add(BentoCell(span = 2) { BentoStat("Jarak", Format.km(a.distanceM), "km", valueStyle = MetricText.Huge) })
                add(BentoCell { BentoStat("Waktu bergerak", Format.duration(a.movingMs), caption = "Total ${Format.duration(a.elapsedMs)}", valueStyle = MetricText.Medium) })
                add(BentoCell { BentoStat("Naik", Format.integer(a.elevationGainM), "m", valueStyle = MetricText.Medium) })
                if (a.sport == Sport.RUN) {
                    add(BentoCell { BentoStat("Pace rata-rata", Format.pace(a.avgSpeedKmh / 3.6), "/km", valueStyle = MetricText.Small) })
                } else {
                    add(BentoCell { BentoStat("Kecepatan rata-rata", Format.oneDecimal(a.avgSpeedKmh), "km/j", caption = "Maks ${Format.oneDecimal(a.maxSpeedKmh)}", valueStyle = MetricText.Small) })
                }
                add(BentoCell { BentoStat("Kalori", Format.integer(a.calories), "kkal", valueStyle = MetricText.Small) })
                if (a.avgHr != null) add(BentoCell { BentoStat("HR rata-rata", "${a.avgHr}", "bpm", caption = a.maxHr?.let { "Maks $it" }, valueStyle = MetricText.Small) })
                a.avgPower?.let { pw -> add(BentoCell { BentoStat("Power rata-rata", "$pw", "W", valueStyle = MetricText.Small) }) }
                add(BentoCell { BentoStat(if (a.trimpEstimated) "TRIMP (estimasi)" else "TRIMP", Format.integer(a.trimp), valueStyle = MetricText.Small) })
            },
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        ChartBlock("Elevasi (m)", s.charts.elevation, Palette.TextMuted, fill = true, cursor = progress)
        ChartBlock("Detak jantung (bpm)", s.charts.heartRate, Palette.Error, cursor = progress)
        ChartBlock("Kecepatan (km/j)", s.charts.speedKmh, Palette.Info, cursor = progress)

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
private fun ChartBlock(title: String, values: List<Float>, color: Color, fill: Boolean = false, cursor: Float? = null) {
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
        LineChart(listOf(Series(values, color)), "$title sepanjang aktivitas", height = 90.dp, fillFirst = fill, cursor = cursor)
    }
}

/** Personal-record badge shown on activity cards and the detail header. */
@Composable
fun PrBadge(label: String) {
    Row(
        Modifier.background(Palette.Surface, MaterialTheme.shapes.small).padding(start = 3.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Medal(1, 20.dp)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.Text)
    }
}
