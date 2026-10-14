package id.web.agentvaedah.strideway.ui.you

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.web.agentvaedah.strideway.core.format.Format
import id.web.agentvaedah.strideway.core.gear.ComponentType
import id.web.agentvaedah.strideway.core.gear.GearEventKind
import id.web.agentvaedah.strideway.core.gear.GearLevel
import id.web.agentvaedah.strideway.data.db.ComponentEntity
import id.web.agentvaedah.strideway.data.prefs.SettingsRepository
import id.web.agentvaedah.strideway.data.prefs.UserProfile
import id.web.agentvaedah.strideway.data.repo.Garage
import id.web.agentvaedah.strideway.data.repo.GarageItem
import id.web.agentvaedah.strideway.data.repo.GearRepository
import id.web.agentvaedah.strideway.ui.components.BackTopBar
import id.web.agentvaedah.strideway.ui.components.BentoCell
import id.web.agentvaedah.strideway.ui.components.BentoGrid
import id.web.agentvaedah.strideway.ui.components.BentoStat
import id.web.agentvaedah.strideway.ui.components.EmptyState
import id.web.agentvaedah.strideway.ui.components.LoadingState
import id.web.agentvaedah.strideway.ui.theme.MetricText
import id.web.agentvaedah.strideway.ui.theme.Palette
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("id-ID"))

@HiltViewModel
class GarageViewModel @Inject constructor(
    private val gear: GearRepository,
    settings: SettingsRepository,
) : ViewModel() {
    val garage: StateFlow<Garage?> = gear.garage.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val profile: StateFlow<UserProfile> = settings.profile.stateIn(viewModelScope, SharingStarted.Eagerly, UserProfile())

    /** Starts tracking the checked part types from today, with each type's default service interval. */
    fun track(types: Set<ComponentType>) {
        val existing = garage.value?.items.orEmpty().associateBy { it.entity.type }
        viewModelScope.launch {
            for (t in ComponentType.entries) {
                val have = existing[t.name]?.entity
                when {
                    t in types && have == null -> gear.save(
                        ComponentEntity(
                            type = t.name, label = t.label, installMs = System.currentTimeMillis(),
                            kmAtInstall = 0.0, serviceIntervalKm = t.defaultIntervalKm,
                        ),
                    )
                    t in types && !have!!.active -> gear.save(have.copy(active = true))
                    t !in types && have != null && have.active -> gear.save(have.copy(active = false))
                }
            }
        }
    }

    fun update(c: ComponentEntity) {
        viewModelScope.launch { gear.save(c) }
    }

    fun log(c: ComponentEntity, kind: GearEventKind, note: String?) {
        viewModelScope.launch { gear.log(c.id, kind, System.currentTimeMillis(), note) }
    }
}

/** Bike garage: which parts are tracked, how worn each one is, and a service log. */
@Composable
fun GarageScreen(onBack: () -> Unit, vm: GarageViewModel = hiltViewModel()) {
    val garage by vm.garage.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    var choose by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<GarageItem?>(null) }

    Column(Modifier.fillMaxSize()) {
        BackTopBar("Garasi sepeda", onBack) {
            IconButton(onClick = { choose = true }) { Icon(Icons.Outlined.Add, contentDescription = "Pilih komponen") }
        }
        val g = garage
        if (g == null) {
            LoadingState("Memuat garasi")
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        profile.bikeName.ifBlank { profile.bikeType.label },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                    val overdue = g.active.count { it.status.level == GearLevel.OVERDUE }
                    // Overdue parts are the call to action, so that tile spans and turns red when non-zero.
                    BentoGrid(
                        listOf(
                            BentoCell(span = 2) {
                                BentoStat(
                                    "Perlu servis",
                                    overdue.toString(),
                                    "komponen",
                                    caption = if (overdue == 0) "Semua komponen masih dalam jadwal" else "Lewat jadwal servis, cek daftar di bawah",
                                    valueColor = if (overdue > 0) Palette.Error else Palette.Text,
                                )
                            },
                            BentoCell { BentoStat("Ride tercatat", Format.integer(g.totalRideKm), "km", valueStyle = MetricText.Small) },
                            BentoCell { BentoStat("Komponen dilacak", g.active.size.toString(), valueStyle = MetricText.Small) },
                        ),
                    )
                    Text(
                        "Kilometer dihitung dari ride yang tercatat setelah tanggal pasang. Tandai Servis atau Ganti agar hitungannya mulai lagi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextMuted,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            if (g.active.isEmpty()) {
                item {
                    EmptyState(
                        "Belum ada komponen dilacak",
                        "Pilih bagian sepeda yang ingin dirawat, misalnya rantai, ban, dan kampas rem.",
                        actionLabel = "Pilih komponen",
                        onAction = { choose = true },
                    )
                }
            }
            items(g.active.sortedByDescending { it.status.fraction }, key = { it.entity.id }) { item ->
                ComponentRow(item) { editing = item }
            }
        }
    }

    if (choose) {
        ChooseComponentsSheet(
            current = garage?.active.orEmpty().mapNotNull { runCatching { ComponentType.valueOf(it.entity.type) }.getOrNull() }.toSet(),
            onSave = { vm.track(it); choose = false },
            onDismiss = { choose = false },
        )
    }
    editing?.let { item ->
        ComponentSheet(
            item = garage?.items?.firstOrNull { it.entity.id == item.entity.id } ?: item,
            onUpdate = vm::update,
            onLog = { kind, note -> vm.log(item.entity, kind, note) },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun ComponentRow(item: GarageItem, onClick: () -> Unit) {
    val s = item.status
    val color = when (s.level) {
        GearLevel.OK -> Palette.Success
        GearLevel.DUE_SOON -> Palette.Warning
        GearLevel.OVERDUE -> Palette.Error
    }
    val label = when (s.level) {
        GearLevel.OK -> "${Format.integer(s.remainingKm)} km lagi"
        GearLevel.DUE_SOON -> "Segera servis · ${Format.integer(s.remainingKm)} km lagi"
        GearLevel.OVERDUE -> "Lewat ${Format.integer(s.kmSinceService - s.component.serviceIntervalKm)} km"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.entity.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(label, style = MaterialTheme.typography.labelLarge, color = color)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { s.fraction.toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = color,
            trackColor = Palette.SurfaceHigh,
            drawStopIndicator = {},
        )
        Text(
            "${Format.integer(s.kmSinceService)} / ${Format.integer(s.component.serviceIntervalKm)} km sejak servis · total ${Format.integer(s.totalKm)} km",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextMuted,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
    HorizontalDivider(color = Palette.Outline)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChooseComponentsSheet(current: Set<ComponentType>, onSave: (Set<ComponentType>) -> Unit, onDismiss: () -> Unit) {
    var picked by remember { mutableStateOf(current) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding()) {
            Text("Komponen yang dirawat", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
            Text(
                "Centang yang perlu diservis. Interval awal bisa diubah per komponen.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextMuted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(ComponentType.entries) { t ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(t in picked, role = Role.Checkbox) { on -> picked = if (on) picked + t else picked - t }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = t in picked, onCheckedChange = null)
                        Spacer(Modifier.width(12.dp))
                        Text(t.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text("${Format.integer(t.defaultIntervalKm)} km", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Batal") }
                Button(onClick = { onSave(picked) }) { Text("Simpan") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComponentSheet(
    item: GarageItem,
    onUpdate: (ComponentEntity) -> Unit,
    onLog: (GearEventKind, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = item.entity
    var interval by remember(c.id) { mutableStateOf(Format.integer(c.serviceIntervalKm)) }
    var kmAtInstall by remember(c.id) { mutableStateOf(Format.integer(c.kmAtInstall)) }
    var note by remember(c.id) { mutableStateOf("") }
    var confirm by remember { mutableStateOf<GearEventKind?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(c.label, style = MaterialTheme.typography.titleLarge)
            Text(
                "Dipasang ${Instant.ofEpochMilli(c.installMs).atZone(ZoneId.systemDefault()).format(dateFmt)}",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    interval,
                    { interval = it.filter(Char::isDigit).take(6) },
                    label = { Text("Interval servis (km)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    kmAtInstall,
                    { kmAtInstall = it.filter(Char::isDigit).take(6) },
                    label = { Text("Km saat dipasang") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            TextButton(
                onClick = {
                    onUpdate(c.copy(serviceIntervalKm = interval.toDoubleOrNull()?.coerceAtLeast(1.0) ?: c.serviceIntervalKm, kmAtInstall = kmAtInstall.toDoubleOrNull() ?: 0.0))
                },
            ) { Text("Simpan pengaturan") }
            OutlinedTextField(note, { note = it.take(120) }, label = { Text("Catatan (opsional)") }, placeholder = { Text("Bengkel, merek, biaya...") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { confirm = GearEventKind.SERVICED }) { Text("Tandai servis") }
                Button(onClick = { confirm = GearEventKind.REPLACED }) { Text("Ganti baru") }
            }
            if (item.events.isNotEmpty()) {
                Text("Riwayat", style = MaterialTheme.typography.titleSmall)
                item.events.take(10).forEach { e ->
                    val kind = runCatching { GearEventKind.valueOf(e.kind) }.getOrNull()?.label ?: e.kind
                    Text(
                        "${Instant.ofEpochMilli(e.atMs).atZone(ZoneId.systemDefault()).format(dateFmt)} · $kind" + (e.note?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    confirm?.let { kind ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (kind == GearEventKind.SERVICED) "Catat servis hari ini?" else "Catat ganti baru hari ini?") },
            text = {
                Text(
                    if (kind == GearEventKind.SERVICED) "Hitungan km sejak servis mulai dari nol." else "Hitungan km sejak servis dan total km komponen mulai dari nol.",
                )
            },
            confirmButton = { TextButton(onClick = { onLog(kind, note); note = ""; confirm = null }) { Text("Catat") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Batal") } },
        )
    }
}
