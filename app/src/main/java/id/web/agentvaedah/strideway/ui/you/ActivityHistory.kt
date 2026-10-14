package id.web.agentvaedah.strideway.ui.you

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import id.web.agentvaedah.strideway.core.history.HistoryFilter
import id.web.agentvaedah.strideway.core.model.Sport
import id.web.agentvaedah.strideway.core.stats.StatMetric
import id.web.agentvaedah.strideway.ui.components.ActivityRow
import id.web.agentvaedah.strideway.ui.components.EmptyState
import id.web.agentvaedah.strideway.ui.components.formatMetric
import id.web.agentvaedah.strideway.ui.theme.MetricText
import id.web.agentvaedah.strideway.ui.theme.Palette
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val idLocale = Locale.forLanguageTag("id-ID")
private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", idLocale)
private val shortFmt = DateTimeFormatter.ofPattern("d MMM yy", idLocale)

/** You > Aktivitas: search, sport and date filters, then activities grouped by month with monthly totals. */
@Composable
fun ActivityHistory(s: ProgressState, onFilter: (HistoryFilter) -> Unit, onOpenActivity: (Long) -> Unit) {
    if (s.totalActivities == 0) {
        EmptyState("Belum ada aktivitas", "Aktivitas yang direkam atau diimpor akan muncul di sini, terbaru di atas.")
        return
    }
    val byId = remember(s.activities) { s.activities.associateBy { it.first.id } }
    LazyColumn(Modifier.fillMaxSize()) {
        item { FilterBar(s.filter, onFilter) }
        if (s.history.isEmpty()) {
            item {
                EmptyState(
                    "Tidak ada yang cocok",
                    "Ubah kata kunci atau filter untuk melihat aktivitas lain.",
                    actionLabel = "Hapus filter",
                    onAction = { onFilter(HistoryFilter()) },
                )
            }
        }
        s.history.forEach { g ->
            item(key = "m-${g.month}") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Palette.Surface)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(g.month.format(monthFmt).replaceFirstChar { it.titlecase(idLocale) }, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${g.count} aktivitas · ${formatMetric(StatMetric.DISTANCE, g.distanceKm)} · " +
                            "${formatMetric(StatMetric.TIME, g.movingHours)} · ${formatMetric(StatMetric.ELEVATION, g.elevationM)}",
                        style = MetricText.Label,
                        color = Palette.TextMuted,
                    )
                }
            }
            items(g.items, key = { it.id }) { h ->
                val (a, path) = byId[h.id] ?: return@items
                ActivityRow(a, path, onClick = { onOpenActivity(a.id) }, badges = s.badges[a.id].orEmpty(), mapHeight = 180.dp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBar(f: HistoryFilter, onFilter: (HistoryFilter) -> Unit) {
    var query by remember(f.query) { mutableStateOf(f.query) }
    var pickDates by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(60); onFilter(f.copy(query = query)) },
            placeholder = { Text("Cari nama aktivitas") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = ""; onFilter(f.copy(query = "")) }) { Icon(Icons.Outlined.Close, contentDescription = "Hapus pencarian") }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onFilter(f.copy(query = query)) }),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(f.sports.isEmpty(), { onFilter(f.copy(sports = emptySet())) }, { Text("Semua") })
            FilterChip(f.sports == setOf(Sport.RIDE), { onFilter(f.copy(sports = setOf(Sport.RIDE))) }, { Text("Ride") })
            FilterChip(f.sports == setOf(Sport.RUN), { onFilter(f.copy(sports = setOf(Sport.RUN))) }, { Text("Run") })
            FilterChip(
                selected = f.from != null || f.to != null,
                onClick = { pickDates = true },
                leadingIcon = { Icon(Icons.Outlined.DateRange, contentDescription = null) },
                label = {
                    Text(
                        if (f.from == null && f.to == null) {
                            "Tanggal"
                        } else {
                            "${f.from?.format(shortFmt) ?: "awal"} - ${f.to?.format(shortFmt) ?: "kini"}"
                        },
                    )
                },
            )
            if (f.isActive) TextButton(onClick = { query = ""; onFilter(HistoryFilter()) }) { Text("Reset") }
        }
    }
    if (pickDates) {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = f.from?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            initialSelectedEndDateMillis = f.to?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        )
        // The picker reports UTC midnight of the chosen day; convert back without shifting the date.
        fun day(ms: Long?) = ms?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
        DatePickerDialog(
            onDismissRequest = { pickDates = false },
            confirmButton = {
                TextButton(onClick = {
                    onFilter(f.copy(from = day(state.selectedStartDateMillis), to = day(state.selectedEndDateMillis) ?: day(state.selectedStartDateMillis)))
                    pickDates = false
                }) { Text("Terapkan") }
            },
            dismissButton = {
                TextButton(onClick = { onFilter(f.copy(from = null, to = null)); pickDates = false }) { Text("Semua tanggal") }
            },
        ) {
            DateRangePicker(state, title = { Text("Pilih rentang", Modifier.padding(16.dp)) }, showModeToggle = false)
        }
    }
}
