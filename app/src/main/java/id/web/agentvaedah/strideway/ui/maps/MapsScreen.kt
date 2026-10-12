package id.web.agentvaedah.strideway.ui.maps

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.web.agentvaedah.strideway.core.format.Format
import id.web.agentvaedah.strideway.core.model.LatLon
import id.web.agentvaedah.strideway.data.db.RouteSource
import id.web.agentvaedah.strideway.data.prefs.MapBase
import id.web.agentvaedah.strideway.data.prefs.TerrainOverlay
import id.web.agentvaedah.strideway.ui.components.MapHost
import id.web.agentvaedah.strideway.ui.components.MapLayers
import id.web.agentvaedah.strideway.ui.components.toLatLng
import id.web.agentvaedah.strideway.ui.theme.Palette
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

private enum class Sheet { NONE, LAYERS, ROUTES, AI }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapsScreen(
    onNavigateRoute: () -> Unit,
    onOpenActivity: (Long) -> Unit,
    onOpenKeys: () -> Unit,
    vm: MapsViewModel = hiltViewModel(),
) {
    val base by vm.mapBase.collectAsStateWithLifecycle()
    val overlay by vm.overlay.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val shown by vm.shownRoute.collectAsStateWithLifecycle()
    val terrain by vm.terrainState.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val ai by vm.ai.collectAsStateWithLifecycle()

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var sheet by rememberSaveable { mutableStateOf(Sheet.NONE) }
    var exportId by remember { mutableLongStateOf(-1L) }
    val snackbar = remember { SnackbarHostState() }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::import) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
        if (uri != null && exportId >= 0) vm.exportRoute(uri, exportId)
    }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    val linePoints: List<LatLon> = draft?.points?.map { it.latLon } ?: shown?.second.orEmpty()

    Box(Modifier.fillMaxSize()) {
        MapHost(
            base = base,
            modifier = Modifier.fillMaxSize(),
            onMapReady = { m ->
                map = m
                vm.lastKnown()?.let { m.moveCamera(CameraUpdateFactory.newLatLngZoom(it.toLatLng(), 13.0)) }
                m.addOnCameraIdleListener {
                    val c = m.cameraPosition
                    c.target?.let { t -> vm.onCameraIdle(t.latitude, t.longitude, c.zoom) }
                }
            },
            onStyleLoaded = { m, st ->
                style = st
                m.cameraPosition.target?.let { t -> vm.onCameraIdle(t.latitude, t.longitude, m.cameraPosition.zoom) }
            },
        )

        LaunchedEffect(style, terrain) {
            val st = style ?: return@LaunchedEffect
            val img = (terrain as? TerrainState.Shown)?.image
            MapLayers.setImage(st, "terrain", img?.quad, img?.bitmap, if (overlay == TerrainOverlay.HILLSHADE) 0.8f else 0.55f, below = "route-casing")
        }
        LaunchedEffect(style, linePoints) {
            val st = style ?: return@LaunchedEffect
            MapLayers.setLine(st, "route", linePoints, Palette.Accent, 4f)
            if (linePoints.size >= 2) map?.let { MapLayers.fit(it, linePoints) }
        }

        TopControls(
            onLayers = { sheet = Sheet.LAYERS },
            onRoutes = { sheet = Sheet.ROUTES },
            onImport = { importLauncher.launch(arrayOf("*/*")) },
            onAi = { sheet = Sheet.AI },
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
        )
        TerrainBanner(terrain, overlay, Modifier.align(Alignment.TopCenter).padding(top = 64.dp))

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            SnackbarHost(snackbar)
            draft?.let { d ->
                DraftPanel(
                    d,
                    onSave = { name, nav -> vm.saveDraftAsRoute(name, nav); if (nav) onNavigateRoute() },
                    onSaveActivity = { vm.saveDraftAsActivity(onOpenActivity) },
                    onDiscard = vm::discardDraft,
                )
            }
            if (draft == null) {
                shown?.let { (r, _) ->
                    ShownPanel(
                        name = r.name,
                        distanceM = r.distanceM,
                        gainM = r.elevationGainM,
                        onNavigate = { vm.setNavigation(r.id); onNavigateRoute() },
                        onClose = vm::hideShown,
                    )
                }
            }
            Text(
                attribution(base),
                style = MaterialTheme.typography.labelMedium,
                color = Palette.TextMuted,
                modifier = Modifier.background(Palette.Bg.copy(alpha = 0.7f)).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }

    if (sheet != Sheet.NONE) {
        ModalBottomSheet(onDismissRequest = { sheet = Sheet.NONE }, containerColor = Palette.Surface) {
            when (sheet) {
                Sheet.LAYERS -> LayersSheet(base, overlay, vm::setBase, vm::setOverlay)
                Sheet.ROUTES -> RoutesSheet(
                    vm,
                    onShow = { vm.show(it); sheet = Sheet.NONE },
                    onNavigate = { vm.setNavigation(it); sheet = Sheet.NONE; onNavigateRoute() },
                    onExport = { id -> exportId = id; exportLauncher.launch(vm.exportFileName(id)) },
                    onImport = { sheet = Sheet.NONE; importLauncher.launch(arrayOf("*/*")) },
                )
                Sheet.AI -> AiRouteSheet(
                    state = ai,
                    onGenerate = { prompt ->
                        val center = map?.cameraPosition?.target?.let { LatLon(it.latitude, it.longitude) } ?: vm.lastKnown()
                        vm.generate(prompt, center)
                    },
                    onCancel = vm::cancelGenerate,
                    onOpenKeys = { sheet = Sheet.NONE; onOpenKeys() },
                    onClearError = vm::clearAiError,
                )
                Sheet.NONE -> Unit
            }
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
        // An AI result closes the sheet so the route is visible on the map.
        LaunchedEffect(draft) { if (draft != null && sheet == Sheet.AI) sheet = Sheet.NONE }
    }
}

private fun attribution(base: MapBase) = when (base) {
    MapBase.STANDARD -> "© OpenFreeMap © OpenStreetMap"
    MapBase.SATELLITE, MapBase.HYBRID -> "Citra © Esri, Maxar, Earthstar Geographics"
}

@Composable
private fun TopControls(onLayers: () -> Unit, onRoutes: () -> Unit, onImport: () -> Unit, onAi: () -> Unit, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MapButton("Layer", onLayers)
        MapButton("Rute", onRoutes)
        MapButton("Impor", onImport)
        MapButton("AI Route", onAi, accent = true)
    }
}

@Composable
private fun MapButton(label: String, onClick: () -> Unit, accent: Boolean = false) {
    if (accent) {
        Button(onClick = onClick) { Text(label) }
    } else {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Bg.copy(alpha = 0.9f), contentColor = Palette.Text),
        ) { Text(label) }
    }
}

@Composable
private fun TerrainBanner(state: TerrainState, overlay: TerrainOverlay, modifier: Modifier) {
    val text = when (state) {
        TerrainState.Off -> return
        TerrainState.Loading -> "Menghitung ${overlay.label.lowercase()} dari data elevasi"
        is TerrainState.TooFar -> "Perbesar peta ke zoom ${state.minZoom} untuk melihat ${overlay.label.lowercase()}"
        is TerrainState.Failed -> state.message
        is TerrainState.Shown -> legend(overlay) ?: return
    }
    Row(
        modifier
            .background(Palette.Bg.copy(alpha = 0.88f), MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state == TerrainState.Loading) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (state is TerrainState.Failed) Palette.Error else Palette.Text)
    }
}

private fun legend(o: TerrainOverlay): String? = when (o) {
    TerrainOverlay.SLOPE -> "Hijau < 6°, kuning 6-10°, oranye 10-15°, merah > 15°"
    TerrainOverlay.AVALANCHE -> "Kuning 30-35°, oranye 35-40°, merah 40-45°, ungu > 45°"
    TerrainOverlay.ASPECT -> "Biru = menghadap utara, kuning = selatan, hijau = timur, merah muda = barat"
    else -> null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LayersSheet(base: MapBase, overlay: TerrainOverlay, onBase: (MapBase) -> Unit, onOverlay: (TerrainOverlay) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Peta dasar", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MapBase.entries.forEach { b -> FilterChip(base == b, { onBase(b) }, { Text(b.label) }) }
        }
        Spacer(Modifier.height(8.dp))
        Text("Analisis medan", style = MaterialTheme.typography.titleMedium)
        Text(
            "Dihitung di ponsel dari data elevasi terbuka (AWS Terrarium). Arah lereng membantu menebak paparan matahari dan angin.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextMuted,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TerrainOverlay.entries.forEach { o -> FilterChip(overlay == o, { onOverlay(o) }, { Text(o.label) }) }
        }
    }
}

@Composable
private fun RoutesSheet(
    vm: MapsViewModel,
    onShow: (Long) -> Unit,
    onNavigate: (Long) -> Unit,
    onExport: (Long) -> Unit,
    onImport: () -> Unit,
) {
    val routes by vm.savedRoutes.collectAsStateWithLifecycle()
    val active by vm.activeRouteId.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf<Long?>(null) }

    Column(Modifier.padding(horizontal = 16.dp)) {
        Text("Rute tersimpan", style = MaterialTheme.typography.titleMedium)
        val list = routes
        when {
            list == null -> CircularProgressIndicator(Modifier.padding(16.dp))
            list.isEmpty() -> Column(Modifier.padding(vertical = 16.dp)) {
                Text("Belum ada rute. Impor GPX/TCX/FIT dari panitia event atau buat dengan AI Route.", color = Palette.TextMuted)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onImport) { Text("Impor file rute") }
            }
            else -> LazyColumn(Modifier.fillMaxWidth().height(420.dp)) {
                items(list, key = { it.id }) { r ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${Format.km(r.distanceM)} km · naik ${Format.integer(r.elevationGainM)} m · " +
                                        if (r.source == RouteSource.AI) "AI Route" else "Impor",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Palette.TextMuted,
                                )
                                if (active == r.id) Text("Dipakai untuk navigasi", style = MaterialTheme.typography.labelMedium, color = Palette.Accent)
                            }
                        }
                        Row {
                            TextButton(onClick = { onShow(r.id) }) { Text("Lihat") }
                            if (active == r.id) {
                                TextButton(onClick = { vm.setNavigation(null) }) { Text("Lepas navigasi") }
                            } else {
                                TextButton(onClick = { onNavigate(r.id) }) { Text("Navigasi") }
                            }
                            TextButton(onClick = { onExport(r.id) }) { Text("GPX") }
                            TextButton(onClick = { confirmDelete = r.id }) { Text("Hapus", color = Palette.Error) }
                        }
                    }
                    HorizontalDivider(color = Palette.Outline)
                }
            }
        }
    }

    confirmDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Hapus rute?") },
            text = { Text("Rute ini dihapus dari ponsel. File asli yang pernah diimpor tidak terpengaruh.") },
            confirmButton = { TextButton(onClick = { vm.deleteRoute(id); confirmDelete = null }) { Text("Hapus", color = Palette.Error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Batal") } },
        )
    }
}

@Composable
private fun AiRouteSheet(
    state: AiRouteState,
    onGenerate: (String) -> Unit,
    onCancel: () -> Unit,
    onOpenKeys: () -> Unit,
    onClearError: () -> Unit,
) {
    var prompt by rememberSaveable { mutableStateOf("") }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("AI Route", style = MaterialTheme.typography.titleMedium)
        Text(
            "Tulis rute yang kamu mau. AI (dengan API key-mu) membaca titik dan preferensi, lalu BRouter menghitung jalurnya di jalan sungguhan.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextMuted,
        )
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it.take(600); onClearError() },
            placeholder = { Text("Rute road bike 50 km dari Kalibata, aspal mulus, lewati Monas dan Sudirman, elevasi landai") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
            enabled = state !is AiRouteState.Working,
        )
        when (state) {
            is AiRouteState.Working -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("  ${state.step}", modifier = Modifier.weight(1f))
                TextButton(onClick = onCancel) { Text("Batal") }
            }
            is AiRouteState.Failed -> {
                Text(state.message, color = Palette.Error)
                if ("API key" in state.message || "key" in state.message.lowercase()) {
                    TextButton(onClick = onOpenKeys) { Text("Atur API key") }
                }
            }
            AiRouteState.Idle -> Unit
        }
        Button(
            onClick = { onGenerate(prompt) },
            enabled = prompt.isNotBlank() && state !is AiRouteState.Working,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Buat rute") }
    }
}

@Composable
private fun DraftPanel(
    d: DraftRoute,
    onSave: (String, Boolean) -> Unit,
    onSaveActivity: () -> Unit,
    onDiscard: () -> Unit,
) {
    var name by remember(d) { mutableStateOf(d.name) }
    val timed = d.importedTrack?.points?.count { it.timeMs != null }?.let { it >= 2 } == true
    Column(
        Modifier
            .fillMaxWidth()
            .background(Palette.Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "${Format.km(d.distanceM)} km · naik ${Format.integer(d.gainM)} m · ${d.points.size} titik",
            style = MaterialTheme.typography.titleSmall,
        )
        OutlinedTextField(value = name, onValueChange = { name = it.take(80) }, label = { Text("Nama rute") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSave(name, true) }) { Text("Simpan & navigasi") }
            OutlinedButton(onClick = { onSave(name, false) }) { Text("Simpan") }
            TextButton(onClick = onDiscard) { Text("Buang") }
        }
        if (timed) {
            TextButton(onClick = onSaveActivity) { Text("File ini berisi waktu. Simpan sebagai aktivitas di Home") }
        }
    }
}

@Composable
private fun ShownPanel(name: String, distanceM: Double, gainM: Double, onNavigate: () -> Unit, onClose: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.Surface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            Text("${Format.km(distanceM)} km · naik ${Format.integer(gainM)} m", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
        }
        TextButton(onClick = onNavigate) { Text("Navigasi") }
        TextButton(onClick = onClose) { Text("Tutup") }
    }
}
