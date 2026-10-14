package id.web.agentvaedah.strideway.ui.you

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.web.agentvaedah.strideway.core.format.Format
import id.web.agentvaedah.strideway.core.model.Sport
import id.web.agentvaedah.strideway.core.records.RecordHolder
import id.web.agentvaedah.strideway.core.records.RecordKey
import id.web.agentvaedah.strideway.core.stats.StatMetric
import id.web.agentvaedah.strideway.data.prefs.BikeType
import id.web.agentvaedah.strideway.ui.components.BentoCell
import id.web.agentvaedah.strideway.ui.components.BentoGrid
import id.web.agentvaedah.strideway.ui.components.BentoStat
import id.web.agentvaedah.strideway.ui.components.LoadingState
import id.web.agentvaedah.strideway.ui.components.ProgressPanel
import id.web.agentvaedah.strideway.ui.components.SectionTitle
import id.web.agentvaedah.strideway.ui.components.formatMetric
import id.web.agentvaedah.strideway.ui.theme.MetricText
import id.web.agentvaedah.strideway.ui.theme.Palette
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("id-ID"))

/** Strava-style You tab: header with profile and gear, then Progress and Aktivitas tabs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouScreen(
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenActivity: (Long) -> Unit,
    vm: ProgressViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("You", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Pengaturan")
            }
        }
        val s = state
        if (s == null) {
            LoadingState("Memuat progres", Modifier.fillMaxSize())
            return@Column
        }
        ProfileHeader(s, onOpenProfile)
        PrimaryTabRow(selectedTabIndex = tab, containerColor = Palette.Bg) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Progress") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Aktivitas") })
        }
        when (tab) {
            0 -> ProgressTab(s, vm, onOpenActivity)
            else -> ActivityHistory(s, vm::setFilter, onOpenActivity)
        }
    }
}

@Composable
private fun ProfileHeader(s: ProgressState, onOpenProfile: () -> Unit) {
    val p = s.profile
    val name = p.name.ifBlank { "Atlet Strideway" }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Ubah profil", onClick = onOpenProfile)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp).background(Palette.Accent, CircleShape), contentAlignment = Alignment.Center) {
            Text(
                name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() },
                style = MaterialTheme.typography.titleLarge,
                color = Palette.OnAccent,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleLarge)
            val bike = when {
                p.bikeType == BikeType.NONE -> "Pelari"
                p.bikeName.isNotBlank() -> "${p.bikeType.label} · ${p.bikeName}"
                else -> p.bikeType.label
            }
            Text(bike, style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
            if (p.name.isBlank()) Text("Ketuk untuk mengisi profil", style = MaterialTheme.typography.bodySmall, color = Palette.Accent)
        }
    }
}

@Composable
private fun ProgressTab(s: ProgressState, vm: ProgressViewModel, onOpenActivity: (Long) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(s.sport == null, { vm.setSport(null) }, { Text("Semua") })
                FilterChip(s.sport == Sport.RIDE, { vm.setSport(Sport.RIDE) }, { Text("Ride") })
                FilterChip(s.sport == Sport.RUN, { vm.setSport(Sport.RUN) }, { Text("Run") })
            }
        }
        item {
            ProgressPanel(
                series = s.series,
                metric = s.metric,
                onRange = vm::setRange,
                onMetric = vm::setMetric,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item {
            SectionTitle("Konsistensi & total")
            // The running weekly streak is what riders check first, so it spans; totals share rows under it.
            BentoGrid(
                listOf(
                    BentoCell(span = 2) {
                        BentoStat(
                            "Minggu beruntun",
                            s.streak.currentWeeks.toString(),
                            "minggu",
                            caption = "Terpanjang ${s.streak.longestWeeks} minggu · ${s.streak.currentDays} hari beruntun",
                            valueStyle = MetricText.Huge,
                        )
                    },
                    BentoCell { BentoStat("Jarak total", formatMetric(StatMetric.DISTANCE, s.allTime.distanceKm), valueStyle = MetricText.Small) },
                    BentoCell { BentoStat("Naik total", formatMetric(StatMetric.ELEVATION, s.allTime.elevationM), valueStyle = MetricText.Small) },
                    BentoCell { BentoStat("Waktu bergerak", formatMetric(StatMetric.TIME, s.allTime.movingHours), valueStyle = MetricText.Small) },
                    BentoCell { BentoStat("Aktivitas", s.allTime.activities.toString(), valueStyle = MetricText.Small) },
                ),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item { SectionTitle("Rekor pribadi") }
        if (s.records.isEmpty()) {
            item {
                Text(
                    "Rekor muncul setelah aktivitas pertama tersimpan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextMuted,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        items(s.records, key = { it.key.name }) { r -> RecordRow(r, onOpenActivity) }
        item { SectionTitle("Penghargaan") }
        if (s.trophies.isEmpty()) {
            item {
                Text(
                    "Belum ada. Ikuti tantangan bulanan di Home; penghargaan muncul di sini saat targetnya tercapai.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextMuted,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        } else {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(s.trophies, key = { "${it.id}@${it.month}" }) { TrophyCard(it) }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
internal fun TrophyCard(t: TrophyItem) {
    val month = t.month.format(monthFmt).replaceFirstChar { it.titlecase(Locale.forLanguageTag("id-ID")) }
    Column(
        Modifier
            .width(132.dp)
            .background(Palette.Surface, MaterialTheme.shapes.medium)
            .padding(12.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Penghargaan ${t.id.title}, $month" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(48.dp).background(Palette.Accent, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = Palette.OnAccent)
        }
        Spacer(Modifier.height(8.dp))
        Text(t.id.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
        Text(month, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
    }
}

@Composable
private fun RecordRow(r: RecordHolder, onOpenActivity: (Long) -> Unit) {
    val value = when (r.key) {
        RecordKey.RIDE_LONGEST, RecordKey.RUN_LONGEST -> formatMetric(StatMetric.DISTANCE, r.value / 1000.0)
        RecordKey.RIDE_ELEVATION -> formatMetric(StatMetric.ELEVATION, r.value)
        RecordKey.RIDE_AVG_SPEED -> "${Format.oneDecimal(r.value).replace('.', ',')} km/j"
        else -> Format.duration((r.value * 1000).toLong())
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Buka aktivitas", onClick = { onOpenActivity(r.activityId) })
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = Palette.Accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(r.key.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MetricText.Small, color = Palette.Text)
    }
}

