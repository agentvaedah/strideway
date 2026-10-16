package id.web.agentvaedah.strideway.ui.you

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.web.agentvaedah.strideway.core.model.Sport
import id.web.agentvaedah.strideway.core.stats.StatMetric
import id.web.agentvaedah.strideway.data.prefs.BikeType
import id.web.agentvaedah.strideway.ui.components.Avatar
import id.web.agentvaedah.strideway.ui.components.BentoCell
import id.web.agentvaedah.strideway.ui.components.BentoGrid
import id.web.agentvaedah.strideway.ui.components.BentoStat
import id.web.agentvaedah.strideway.ui.components.LoadingState
import id.web.agentvaedah.strideway.ui.components.ProgressPanel
import id.web.agentvaedah.strideway.ui.components.SectionTitle
import id.web.agentvaedah.strideway.ui.components.formatMetric
import id.web.agentvaedah.strideway.ui.motion.Motion
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
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(vm::setPhoto) }

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
        ProfileHeader(
            s,
            onOpenProfile,
            onPickPhoto = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onRemovePhoto = vm::removePhoto,
        )
        PrimaryTabRow(selectedTabIndex = tab, containerColor = Palette.Bg) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Progress") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Aktivitas") })
        }
        AnimatedContent(tab, transitionSpec = { fadeIn(tween(Motion.MEDIUM)) togetherWith fadeOut(tween(Motion.SHORT)) }, label = "you-tab") { t ->
            when (t) {
                0 -> ProgressTab(s, vm, onOpenActivity)
                else -> ActivityHistory(s, vm::setFilter, onOpenActivity)
            }
        }
    }
}

@Composable
private fun ProfileHeader(s: ProgressState, onOpenProfile: () -> Unit, onPickPhoto: () -> Unit, onRemovePhoto: () -> Unit) {
    val p = s.profile
    val name = p.name.ifBlank { "Atlet Strideway" }
    var menu by remember { mutableStateOf(false) }
    var bioOpen by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Avatar(p.photoPath, name, 96.dp, Modifier.clickable(role = Role.Button, onClickLabel = "Ganti foto profil") { menu = true })
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(34.dp)
                    .background(Palette.Text, CircleShape)
                    .border(3.dp, Palette.Bg, CircleShape)
                    .clickable(role = Role.Button, onClickLabel = "Ganti foto profil") { menu = true },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = Palette.Bg, modifier = Modifier.size(18.dp)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Pilih dari galeri") }, onClick = { menu = false; onPickPhoto() })
                if (p.photoPath != null) DropdownMenuItem(text = { Text("Hapus foto") }, onClick = { menu = false; onRemovePhoto() })
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(name, style = MaterialTheme.typography.headlineSmall)
        val bike = when {
            p.bikeType == BikeType.NONE -> "Pelari"
            p.bikeName.isNotBlank() -> "${p.bikeType.label} · ${p.bikeName}"
            else -> p.bikeType.label
        }
        Text(bike, style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            ProfileCount(s.totalActivities.toString(), "Aktivitas", Modifier.weight(1f))
            ProfileCount(s.streak.currentWeeks.toString(), "Mgg beruntun", Modifier.weight(1f))
            ProfileCount("–", "Followers", Modifier.weight(1f), muted = true)
            ProfileCount("–", "Following", Modifier.weight(1f), muted = true)
        }
        Text(
            "Followers dan Following aktif setelah akun Strideway online tersedia.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (p.bio.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                p.bio,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (bioOpen) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().animateContentSize(),
            )
            if (p.bio.length > 110 || p.bio.count { it == '\n' } >= 3) {
                TextButton(onClick = { bioOpen = !bioOpen }, modifier = Modifier.align(Alignment.Start)) {
                    Text(if (bioOpen) "Tutup" else "Lihat selengkapnya")
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = onOpenProfile, modifier = Modifier.fillMaxWidth()) {
            Text(if (p.name.isBlank() || p.bio.isBlank()) "Lengkapi profil" else "Edit profil")
        }
    }
}

@Composable
private fun ProfileCount(value: String, label: String, modifier: Modifier, muted: Boolean = false) {
    Column(modifier.semantics(mergeDescendants = true) { }, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MetricText.Medium, color = if (muted) Palette.TextMuted else Palette.Text)
        Text(label, style = MetricText.Label, color = Palette.TextMuted, maxLines = 1)
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
        } else {
            item { RecordsSection(s.recordTop, s.recordPrevious, s.activityById, onOpenActivity) }
        }
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

