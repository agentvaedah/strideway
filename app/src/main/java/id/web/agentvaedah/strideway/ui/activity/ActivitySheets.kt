package id.web.agentvaedah.strideway.ui.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import id.web.agentvaedah.strideway.core.format.Format
import id.web.agentvaedah.strideway.core.model.LatLon
import id.web.agentvaedah.strideway.core.model.Sport
import id.web.agentvaedah.strideway.core.model.TrackPoint
import id.web.agentvaedah.strideway.core.track.TrackEdit
import id.web.agentvaedah.strideway.core.track.TrackStats
import id.web.agentvaedah.strideway.data.db.ActivityEntity
import id.web.agentvaedah.strideway.data.prefs.MapBase
import id.web.agentvaedah.strideway.ui.components.MapHost
import id.web.agentvaedah.strideway.ui.components.MapLayers
import id.web.agentvaedah.strideway.ui.theme.Palette
import org.maplibre.android.maps.Style
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditActivitySheet(a: ActivityEntity, onSave: (String, Sport, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(a.name) }
    var sport by remember { mutableStateOf(a.sport) }
    var description by remember { mutableStateOf(a.description) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Edit aktivitas", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(name, { name = it.take(80) }, label = { Text("Judul") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(sport == Sport.RIDE, { sport = Sport.RIDE }, { Text("Ride") })
                FilterChip(sport == Sport.RUN, { sport = Sport.RUN }, { Text("Run") })
            }
            OutlinedTextField(
                description,
                { description = it.take(1000) },
                label = { Text("Deskripsi") },
                placeholder = { Text("Bagaimana rasanya? Rute, cuaca, teman gowes...") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Batal") }
                Button(onClick = { onSave(name, sport, description); onDismiss() }, enabled = name.isNotBlank()) { Text("Simpan") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * Trims the start and end of a ride, e.g. the minutes spent waiting before the start. The kept part is
 * drawn on the map while dragging and its distance shown, so the result is visible before committing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropSheet(path: List<LatLon>, times: List<Long>, base: MapBase, onCrop: (Long, Long) -> Unit, onDismiss: () -> Unit) {
    var range by remember { mutableStateOf(0f..1f) }
    var confirm by remember { mutableStateOf(false) }
    var style by remember { mutableStateOf<Style?>(null) }
    val kept = TrackEdit.cropRange(path.size, range.start, range.endInclusive)
    val keptPath = if (path.isEmpty()) emptyList() else path.subList(kept.first, kept.last + 1)
    val keptColor = Palette.Accent
    val cutColor = Palette.TextMuted

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Potong aktivitas", style = MaterialTheme.typography.titleLarge)
            MapHost(
                base = base,
                modifier = Modifier.fillMaxWidth().height(220.dp),
                onStyleLoaded = { m, st ->
                    style = st
                    MapLayers.fit(m, path)
                },
            )
            LaunchedEffect(style, kept, keptColor) {
                val st = style ?: return@LaunchedEffect
                MapLayers.setLine(st, "crop-all", path, cutColor, 3f)
                MapLayers.setLine(st, "crop-kept", keptPath, keptColor, 5f, lineStyle = MapLayers.LineStyle.TRACK)
            }
            RangeSlider(value = range, onValueChange = { range = it }, valueRange = 0f..1f)
            if (times.isNotEmpty()) {
                val from = Instant.ofEpochMilli(times[kept.first]).atZone(ZoneId.systemDefault()).format(timeFmt)
                val to = Instant.ofEpochMilli(times[kept.last]).atZone(ZoneId.systemDefault()).format(timeFmt)
                Text(
                    "$from sampai $to · ${Format.km(TrackStats.distanceM(keptPath.map { TrackPoint(it.lat, it.lon) }))} km disimpan",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Batal") }
                Button(onClick = { confirm = true }, enabled = kept.first > 0 || kept.last < path.lastIndex) { Text("Potong") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Potong permanen?") },
            text = { Text("Titik di luar bagian yang dipilih akan dihapus dan statistik dihitung ulang. Tidak bisa dibatalkan; ekspor GPX dulu bila ingin menyimpan aslinya.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onCrop(times[kept.first], times[kept.last])
                    onDismiss()
                }) { Text("Potong", color = Palette.Error) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Batal") } },
        )
    }
}

/** Hides the first/last metres (or the whole map) for everyone who sees the activity, exports included. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapVisibilitySheet(a: ActivityEntity, onSave: (Int, Int, Boolean) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(0, 200, 500, 1000)
    var start by remember { mutableStateOf(a.hideStartM) }
    var end by remember { mutableStateOf(a.hideEndM) }
    var hideMap by remember { mutableStateOf(a.hideMap) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Visibilitas peta", style = MaterialTheme.typography.titleLarge)
            Row(
                Modifier.fillMaxWidth().toggleable(hideMap, role = Role.Switch) { hideMap = it },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Sembunyikan seluruh peta", style = MaterialTheme.typography.bodyLarge)
                    Text("Statistik tetap tampil, jalur tidak", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                }
                Switch(checked = hideMap, onCheckedChange = null)
            }
            if (!hideMap) {
                Text("Sembunyikan awal", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { m -> FilterChip(start == m, { start = m }, { Text(if (m == 0) "Tidak" else "$m m") }) }
                }
                Text("Sembunyikan akhir", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { m -> FilterChip(end == m, { end = m }, { Text(if (m == 0) "Tidak" else "$m m") }) }
                }
                Text(
                    "Berlaku di peta, kartu aktivitas, dan file ekspor, selain Privacy Zone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextMuted,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Batal") }
                Button(onClick = { onSave(start, end, hideMap); onDismiss() }) { Text("Simpan") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
