package id.web.agentvaedah.strideway.ui.maps

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.vector.ImageVector
import id.web.agentvaedah.strideway.core.geo.Geo
import id.web.agentvaedah.strideway.data.prefs.MapOverlay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.web.agentvaedah.strideway.core.format.Format
import id.web.agentvaedah.strideway.core.model.LatLon
import id.web.agentvaedah.strideway.data.db.RouteSource
import id.web.agentvaedah.strideway.data.prefs.MapBase
import id.web.agentvaedah.strideway.data.prefs.TerrainOverlay
import id.web.agentvaedah.strideway.ui.components.LocalMapboxToken
import id.web.agentvaedah.strideway.ui.components.MapCompass
import id.web.agentvaedah.strideway.ui.components.MapHost
import id.web.agentvaedah.strideway.ui.components.MapStyles
import id.web.agentvaedah.strideway.ui.components.MapLayers
import id.web.agentvaedah.strideway.ui.components.toLatLng
import id.web.agentvaedah.strideway.ui.theme.Palette
import id.web.agentvaedah.strideway.ui.weather.WeatherBadge
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
    onOpenWeather: () -> Unit,
    vm: MapsViewModel = hiltViewModel(),
    planner: MapPlannerViewModel = hiltViewModel(),
) {
    val sport by planner.sport.collectAsStateWithLifecycle()
    val overlays by planner.overlays.collectAsStateWithLifecycle()
    val tilt3d by planner.tilt3d.collectAsStateWithLifecycle()
    val search by planner.search.collectAsStateWithLifecycle()
    val focus by planner.focus.collectAsStateWithLifecycle()
    val from by planner.from.collectAsStateWithLifecycle()
    val to by planner.to.collectAsStateWithLifecycle()
    val plan by planner.plan.collectAsStateWithLifecycle()
    val sort by planner.sort.collectAsStateWithLifecycle()
    val selected by planner.selected.collectAsStateWithLifecycle()
    val places by planner.savedPlaces.collectAsStateWithLifecycle()
    val drawing by planner.drawing.collectAsStateWithLifecycle()
    val draw by planner.draw.collectAsStateWithLifecycle()
    val drawBusy by planner.drawBusy.collectAsStateWithLifecycle()
    val heat by planner.heat.collectAsStateWithLifecycle()
    val plannerMessage by planner.message.collectAsStateWithLifecycle()
    val drawingNow by rememberUpdatedState(drawing)
    val weather by vm.weather.collectAsStateWithLifecycle()
    val heading by vm.heading.collectAsStateWithLifecycle()

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
    var planOpen by rememberSaveable { mutableStateOf(false) }
    var planExpanded by rememberSaveable { mutableStateOf(true) }
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
    LaunchedEffect(plannerMessage) {
        plannerMessage?.let {
            snackbar.showSnackbar(it)
            planner.clearMessage()
        }
    }

    val plannedPreview = (plan as? PlanState.Ready)?.let { planner.sorted(it.options).getOrNull(selected) }?.points?.map { it.latLon }
    val linePoints: List<LatLon> = when {
        drawing -> draw.path()
        plannedPreview != null -> plannedPreview
        else -> draft?.points?.map { it.latLon } ?: shown?.second.orEmpty()
    }

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
                m.addOnMapClickListener { ll ->
                    if (drawingNow) {
                        planner.addPoint(LatLon(ll.latitude, ll.longitude))
                        true
                    } else {
                        false
                    }
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
        val routeColor = Palette.Accent
        val density = LocalDensity.current
        val screenH = LocalConfiguration.current.screenHeightDp
        // The collapsed planner covers about a tenth of the screen; frame the route above it.
        val bottomInsetPx = if (planOpen) with(density) { (screenH * 0.14f).dp.roundToPx() } else 0
        LaunchedEffect(style, linePoints, routeColor, bottomInsetPx) {
            val st = style ?: return@LaunchedEffect
            MapLayers.setLine(st, "route", linePoints, routeColor, 5f, lineStyle = MapLayers.LineStyle.ROUTE)
            // While drawing the camera stays where the rider is working; fitting each tap would jump around.
            if (linePoints.size >= 2 && !drawing) map?.let { MapLayers.fit(it, linePoints, bottomPx = bottomInsetPx) }
        }
        val pinColor = Palette.Info
        LaunchedEffect(style, focus) {
            val st = style ?: return@LaunchedEffect
            MapLayers.setPoint(st, "focus-pin", focus?.pos, pinColor, 8f)
            focus?.let { f -> map?.animateCamera(CameraUpdateFactory.newLatLngZoom(f.pos.toLatLng(), 14.0)) }
        }
        LaunchedEffect(style, overlays, base) {
            val st = style ?: return@LaunchedEffect
            MapLayers.setRasterOverlay(st, "bike-lanes", if (MapOverlay.BIKE_LANES in overlays) MapStyles.BIKE_LANES_URL else null, below = "route-casing")
            if (base == MapBase.STANDARD) MapLayers.setPoiVisible(st, MapOverlay.POI in overlays)
        }
        val heatColor = Palette.Accent
        LaunchedEffect(style, heat, overlays) {
            val st = style ?: return@LaunchedEffect
            MapLayers.setHeatmap(st, "heatmap", if (MapOverlay.HEATMAP in overlays) heat else emptyList(), heatColor)
        }
        LaunchedEffect(map, tilt3d) {
            map?.animateCamera(CameraUpdateFactory.tiltTo(if (tilt3d) 60.0 else 0.0), 500)
        }

        Column(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(12.dp)) {
            MapsSearchBar(
                sport = sport,
                onSport = planner::setSport,
                search = search,
                onSearch = { q ->
                    val c = map?.cameraPosition?.target?.let { LatLon(it.latitude, it.longitude) } ?: vm.lastKnown()
                    planner.search(q, c)
                },
                onPick = planner::focusOn,
                onClear = planner::clearSearch,
            )
            Spacer(Modifier.height(8.dp))
            TopControls(
                onRoutes = { sheet = Sheet.ROUTES },
                onPlan = {
                    if (from == null) vm.lastKnown()?.let { planner.setFrom(Endpoint("Lokasi sekarang", it)) }
                    planOpen = true
                    planExpanded = true
                },
                onImport = { importLauncher.launch(arrayOf("*/*")) },
                onAi = { sheet = Sheet.AI },
            )
            TerrainBanner(terrain, overlay, Modifier.padding(top = 8.dp))
        }

        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            WeatherBadge(weather, onOpenWeather)
            // North-up map: the needle stays up and the label shows where the phone points.
            MapCompass(mapBearing = 0f, heading = heading, headingUp = false)
            RoundButton(Icons.Outlined.Layers, "Lapisan peta") { sheet = Sheet.LAYERS }
            RoundButton(Icons.Outlined.ViewInAr, if (tilt3d) "Tampilan 2D" else "Tampilan 3D", active = tilt3d, onClick = planner::toggle3d)
            RoundButton(Icons.Outlined.MyLocation, "Lokasi sekarang") {
                vm.lastKnown()?.let { map?.animateCamera(CameraUpdateFactory.newLatLngZoom(it.toLatLng(), 15.0)) }
            }
            RoundButton(Icons.Outlined.Draw, if (drawing) "Keluar dari mode gambar" else "Gambar rute sendiri", active = drawing) {
                if (drawing) planner.stopDrawing() else planner.startDrawing()
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            SnackbarHost(snackbar)
            if (drawing) {
                DrawRouteBar(
                    points = draw.waypoints.size,
                    distanceM = draw.path().zipWithNext { a, b -> Geo.distanceM(a, b) }.sum(),
                    busy = drawBusy,
                    onUndo = planner::undo,
                    onClear = planner::clearDraw,
                    onSave = { name -> planner.saveDrawn(name) { id -> vm.show(id) } },
                    onClose = planner::stopDrawing,
                )
            } else if (focus != null && draft == null && !planOpen) {
                PlacePanel(
                    place = focus!!,
                    onSave = { planner.savePlace(focus!!) },
                    onRouteTo = {
                        planner.setTo(focus)
                        if (from == null) vm.lastKnown()?.let { planner.setFrom(Endpoint("Lokasi sekarang", it)) }
                        planner.focusOn(null)
                        planOpen = true
                        planExpanded = true
                    },
                    onClose = { planner.focusOn(null) },
                )
            }
            if (planOpen && !drawing) {
                val readyOptions = (plan as? PlanState.Ready)?.let { planner.sorted(it.options) }.orEmpty()
                PlannerPanel(
                    expanded = planExpanded,
                    onExpandedChange = { planExpanded = it },
                    options = readyOptions,
                    selected = selected,
                    onSelect = planner::select,
                    onClose = { planOpen = false; planner.clearPlan() },
                ) {
                    RoutePlannerSheet(
                        from = from,
                        to = to,
                        here = vm.lastKnown(),
                        focus = focus,
                        saved = places,
                        plan = plan,
                        sort = sort,
                        selected = selected,
                        sorted = planner::sorted,
                        onFrom = planner::setFrom,
                        onTo = planner::setTo,
                        onPlan = planner::plan,
                        onSort = planner::setSort,
                        // Picking an alternative shrinks the panel to its strip so the route is visible on the map.
                        onSelect = { planner.select(it); planExpanded = false },
                        onSave = { o, name -> planner.savePlanned(o, name) { id -> vm.show(id); planOpen = false } },
                    )
                }
            }
            if (!drawing && !planOpen) draft?.let { d ->
                DraftPanel(
                    d,
                    onSave = { name, nav -> vm.saveDraftAsRoute(name, nav); if (nav) onNavigateRoute() },
                    onSaveActivity = { vm.saveDraftAsActivity(onOpenActivity) },
                    onDiscard = vm::discardDraft,
                )
            }
            if (draft == null && !drawing && focus == null && !planOpen) {
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
                MapStyles.attribution(base, LocalMapboxToken.current) +
                    (if (MapOverlay.BIKE_LANES in overlays) " · CyclOSM" else "") +
                    (if (plan is PlanState.Ready || drawing) " · Rute: BRouter" else ""),
                style = MaterialTheme.typography.labelMedium,
                color = Palette.TextMuted,
                modifier = Modifier.background(Palette.Bg.copy(alpha = 0.7f)).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }

    if (sheet != Sheet.NONE) {
        ModalBottomSheet(onDismissRequest = { sheet = Sheet.NONE }, containerColor = Palette.Surface) {
            when (sheet) {
                Sheet.LAYERS -> MapLayersSheet(base, overlays, overlay, { vm.setBase(it) }, planner::toggleOverlay, vm::setOverlay)
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

@Composable
private fun TopControls(onRoutes: () -> Unit, onPlan: () -> Unit, onImport: () -> Unit, onAi: () -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MapButton("Rute", onRoutes)
        MapButton("Rencana rute", onPlan)
        MapButton("Impor", onImport)
        MapButton("AI Route", onAi, accent = true)
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp).background(if (active) Palette.Accent else Palette.Bg.copy(alpha = 0.9f), MaterialTheme.shapes.medium),
    ) { Icon(icon, contentDescription = label, tint = if (active) Palette.OnAccent else Palette.Text) }
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
