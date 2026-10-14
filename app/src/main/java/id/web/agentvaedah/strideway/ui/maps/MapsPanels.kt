package id.web.agentvaedah.strideway.ui.maps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import id.web.agentvaedah.strideway.core.format.Format
import id.web.agentvaedah.strideway.core.model.LatLon
import id.web.agentvaedah.strideway.core.model.Sport
import id.web.agentvaedah.strideway.core.route.RouteOption
import id.web.agentvaedah.strideway.core.route.RouteSort
import id.web.agentvaedah.strideway.data.db.PlaceEntity
import id.web.agentvaedah.strideway.data.prefs.MapBase
import id.web.agentvaedah.strideway.data.prefs.MapOverlay
import id.web.agentvaedah.strideway.data.prefs.TerrainOverlay
import id.web.agentvaedah.strideway.ui.theme.Palette
import kotlin.math.roundToInt

/** Top-left: sport chooser and a place search box; results drop down below it. */
@Composable
fun MapsSearchBar(
    sport: Sport,
    onSport: (Sport) -> Unit,
    search: SearchState,
    onSearch: (String) -> Unit,
    onPick: (Endpoint) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onSport(if (sport == Sport.RIDE) Sport.RUN else Sport.RIDE) },
                modifier = Modifier.size(48.dp).background(Palette.Bg.copy(alpha = 0.92f), MaterialTheme.shapes.medium),
            ) {
                Icon(
                    if (sport == Sport.RIDE) Icons.Outlined.DirectionsBike else Icons.AutoMirrored.Outlined.DirectionsRun,
                    contentDescription = "Olahraga: ${if (sport == Sport.RIDE) "Ride" else "Run"}. Ketuk untuk ganti",
                    tint = Palette.Accent,
                )
            }
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(80) },
                placeholder = { Text("Cari lokasi") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = ""; onClear() }) { Icon(Icons.Outlined.Close, contentDescription = "Hapus pencarian") }
                    }
                },
                singleLine = true,
                // Nominatim allows one request per second and forbids search-as-you-type, so search on submit.
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Palette.Bg.copy(alpha = 0.95f),
                    unfocusedContainerColor = Palette.Bg.copy(alpha = 0.92f),
                ),
                modifier = Modifier.weight(1f),
            )
        }
        when (search) {
            SearchState.Idle -> Unit
            SearchState.Loading -> Box(Modifier.padding(top = 6.dp).background(Palette.Bg, MaterialTheme.shapes.medium).padding(12.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            is SearchState.Failed -> Text(
                search.message,
                color = Palette.Error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp).background(Palette.Bg, MaterialTheme.shapes.medium).padding(10.dp),
            )
            is SearchState.Results -> Column(
                Modifier.padding(top = 6.dp).fillMaxWidth().heightIn(max = 300.dp).clip(MaterialTheme.shapes.medium).background(Palette.Bg),
            ) {
                if (search.items.isEmpty()) {
                    Text("Tidak ditemukan untuk \"${search.query}\"", color = Palette.TextMuted, modifier = Modifier.padding(12.dp))
                }
                LazyColumn {
                    itemsIndexed(search.items) { _, r ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button) { onPick(Endpoint(r.name, LatLon(r.lat, r.lon))) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Place, contentDescription = null, tint = Palette.TextMuted)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(r.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                Text(r.displayName, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, maxLines = 1)
                            }
                        }
                        HorizontalDivider(color = Palette.Outline)
                    }
                }
            }
        }
    }
}

/** Card for a picked place: save it, or plan a route from here to there. */
@Composable
fun PlacePanel(place: Endpoint, onSave: () -> Unit, onRouteTo: () -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Palette.Bg).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(place.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 2)
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Tutup") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onSave) {
                Icon(Icons.Outlined.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Simpan lokasi")
            }
            Button(onClick = onRouteTo) { Text("Rute ke sini") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MapLayersSheet(
    base: MapBase,
    overlays: Set<MapOverlay>,
    terrain: TerrainOverlay,
    onBase: (MapBase) -> Unit,
    onOverlay: (MapOverlay) -> Unit,
    onTerrain: (TerrainOverlay) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Jenis peta", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(MapBase.STANDARD, MapBase.SATELLITE, MapBase.HYBRID, MapBase.TOPO).forEach { b ->
                FilterChip(base == b, { onBase(b) }, { Text(b.label) })
            }
        }
        Text("Heatmap", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(MapOverlay.HEATMAP in overlays, { onOverlay(MapOverlay.HEATMAP) }, { Text("Pribadi") })
            listOf("Global", "Mingguan", "Malam").forEach { FilterChip(false, {}, { Text(it) }, enabled = false) }
        }
        Text(
            "Heatmap global, mingguan, dan malam butuh data banyak pengguna di server Strideway, belum tersedia. Pribadi dibuat dari aktivitasmu di ponsel ini.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextMuted,
        )
        Text("Lapisan", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                MapOverlay.POI in overlays,
                { onOverlay(MapOverlay.POI) },
                { Text(MapOverlay.POI.label) },
                enabled = base == MapBase.STANDARD,
            )
            FilterChip(MapOverlay.BIKE_LANES in overlays, { onOverlay(MapOverlay.BIKE_LANES) }, { Text(MapOverlay.BIKE_LANES.label) })
        }
        if (base != MapBase.STANDARD) {
            Text("Tempat menarik hanya bisa diatur di peta Standar.", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
        }
        Text("Medan", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(TerrainOverlay.NONE, TerrainOverlay.AVALANCHE, TerrainOverlay.SLOPE, TerrainOverlay.ASPECT, TerrainOverlay.HILLSHADE).forEach { o ->
                FilterChip(terrain == o, { onTerrain(o) }, { Text(o.label) })
            }
        }
    }
}

/** Route planner: endpoints, then alternatives sortable by length, climb or how much is paved. */
@Composable
fun RoutePlannerSheet(
    from: Endpoint?,
    to: Endpoint?,
    here: LatLon?,
    focus: Endpoint?,
    saved: List<PlaceEntity>,
    plan: PlanState,
    sort: RouteSort,
    selected: Int,
    sorted: (List<RouteOption>) -> List<RouteOption>,
    onFrom: (Endpoint?) -> Unit,
    onTo: (Endpoint?) -> Unit,
    onPlan: () -> Unit,
    onSort: (RouteSort) -> Unit,
    onSelect: (Int) -> Unit,
    onSave: (RouteOption, String) -> Unit,
) {
    val choices = buildList {
        here?.let { add(Endpoint("Lokasi sekarang", it)) }
        focus?.let { add(it) }
        saved.forEach { add(Endpoint(it.name, LatLon(it.lat, it.lon))) }
    }.distinctBy { it.name }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Rencana rute", style = MaterialTheme.typography.titleLarge)
        EndpointPicker("Dari", from, choices, onFrom)
        EndpointPicker("Ke", to, choices, onTo)
        if (choices.size < 2) {
            Text(
                "Cari lokasi di kotak pencarian lalu Simpan lokasi agar bisa dipilih di sini.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextMuted,
            )
        }
        Button(onClick = onPlan, enabled = from != null && to != null && from.pos != to.pos && plan != PlanState.Loading) {
            Text("Cari rute")
        }
        when (plan) {
            PlanState.Idle -> Unit
            PlanState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Menghitung hingga 3 alternatif lewat BRouter", style = MaterialTheme.typography.bodyMedium)
            }
            is PlanState.Failed -> Text(plan.message, color = Palette.Error)
            is PlanState.Ready -> {
                Text("Rekomendasi rute, diurutkan menurut", style = MaterialTheme.typography.labelLarge, color = Palette.TextMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RouteSort.entries.forEach { s -> FilterChip(sort == s, { onSort(s) }, { Text(s.label) }) }
                }
                val list = sorted(plan.options)
                list.forEachIndexed { i, o -> OptionCard(o, i, selected == i) { onSelect(i) } }
                var name by remember(from, to) { mutableStateOf("${from?.name ?: ""} ke ${to?.name ?: ""}".take(60)) }
                OutlinedTextField(name, { name = it.take(60) }, label = { Text("Nama rute") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                list.getOrNull(selected)?.let { o -> Button(onClick = { onSave(o, name) }) { Text("Simpan rute ini") } }
                Text("Rute dihitung oleh BRouter dari data OpenStreetMap.", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EndpointPicker(label: String, value: Endpoint?, choices: List<Endpoint>, onPick: (Endpoint?) -> Unit) {
    Column {
        Text("$label: ${value?.name ?: "belum dipilih"}", style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            choices.forEach { c -> FilterChip(value?.name == c.name, { onPick(c) }, { Text(c.name, maxLines = 1) }) }
        }
    }
}

@Composable
private fun OptionCard(o: RouteOption, index: Int, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) Palette.SurfaceHigh else Palette.Bg)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Alternatif ${index + 1}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text("${Format.km(o.lengthM)} km · naik ${Format.integer(o.ascendM)} m", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(6.dp))
        SurfaceBar(o)
        Text(
            "Aspal ${(o.pavedShare * 100).roundToInt()}%" + (o.timeS.takeIf { it > 0 }?.let { " · perkiraan ${Format.duration(it * 1000L)}" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextMuted,
        )
    }
}

/** Paved share in the accent colour, the rest muted; a quick read of how rough the route is. */
@Composable
private fun SurfaceBar(o: RouteOption) {
    Box(Modifier.fillMaxWidth().height(8.dp).clip(MaterialTheme.shapes.extraSmall).background(Palette.Outline)) {
        Box(Modifier.fillMaxWidth(o.pavedShare.toFloat().coerceIn(0f, 1f)).height(8.dp).background(Palette.Accent))
    }
}

/** Bottom bar while drawing a route by tapping the map. */
@Composable
fun DrawRouteBar(
    points: Int,
    distanceM: Double,
    busy: Boolean,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onSave: (String) -> Unit,
    onClose: () -> Unit,
) {
    var name by remember { mutableStateOf("Rute gambar") }
    Column(Modifier.fillMaxWidth().background(Palette.Bg).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Gambar rute", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (points == 0) "Ketuk peta untuk titik awal, lalu titik berikutnya. Rute mengikuti jalan." else "$points titik · ${Format.km(distanceM)} km",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextMuted,
                )
            }
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            IconButton(onClick = onUndo, enabled = points > 0) { Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Batalkan titik terakhir") }
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Tutup mode gambar") }
        }
        if (points >= 2) {
            OutlinedTextField(name, { name = it.take(60) }, label = { Text("Nama rute") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onClear) { Text("Hapus semua") }
                Button(onClick = { onSave(name) }, enabled = !busy) { Text("Simpan rute") }
            }
        }
    }
}
