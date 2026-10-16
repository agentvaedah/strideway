package id.web.agentvaedah.strideway.ui.training

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.web.agentvaedah.strideway.core.format.Format
import id.web.agentvaedah.strideway.core.training.Level
import id.web.agentvaedah.strideway.core.training.Step
import id.web.agentvaedah.strideway.core.training.Target
import id.web.agentvaedah.strideway.core.training.Workout
import id.web.agentvaedah.strideway.data.repo.StoredPlan
import id.web.agentvaedah.strideway.ui.components.BentoCell
import id.web.agentvaedah.strideway.ui.components.BentoGrid
import id.web.agentvaedah.strideway.ui.components.BentoStat
import id.web.agentvaedah.strideway.ui.components.EmptyState
import id.web.agentvaedah.strideway.ui.components.SectionTitle
import id.web.agentvaedah.strideway.ui.theme.MetricText
import id.web.agentvaedah.strideway.ui.theme.Palette
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private val idLocale = Locale.forLanguageTag("id-ID")
private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", idLocale)

/**
 * Latihan tab: the training plan, the session library and the AI coach in one place. Sessions are chosen
 * here and ridden from Record, where intervals step automatically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingScreen(onOpenCoach: () -> Unit, onRide: () -> Unit, vm: TrainingViewModel = hiltViewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val ftp by vm.ftpResult.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    // The workout being looked at, and the planned-session row it came from (null from the library).
    var detail by remember { mutableStateOf<Pair<Workout, Long?>?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Latihan", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onOpenCoach) { Icon(Icons.Outlined.Forum, contentDescription = "Buka AI Coach") }
        }
        selected?.let { w ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).background(Palette.Surface, MaterialTheme.shapes.medium).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Siap dikayuh", style = MetricText.Label, color = Palette.TextMuted)
                    Text(w.name, style = MaterialTheme.typography.titleMedium)
                }
                TextButton(onClick = vm::cancelRide) { Text("Batal") }
                Button(onClick = onRide) { Text("Ke Rekam") }
            }
        }
        PrimaryTabRow(selectedTabIndex = tab, containerColor = Palette.Bg) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Rencana") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Sesi") })
        }
        when (tab) {
            0 -> PlanTab(vm, onOpen = { w, id -> detail = w to id })
            else -> LibraryTab(vm, onOpen = { detail = it to null })
        }
    }

    detail?.let { (w, sessionId) ->
        WorkoutSheet(w, vm.athlete.value.ftpWatts, onRide = { vm.ride(w, sessionId); detail = null; onRide() }, onDismiss = { detail = null })
    }
    ftp?.let { r ->
        AlertDialog(
            onDismissRequest = vm::dismissFtp,
            title = { Text("FTP baru ${r.newFtp} W") },
            text = { Text("Sebelumnya ${r.oldFtp} W. Perbarui FTP agar zona power dan target sesi mengikuti kondisimu sekarang?") },
            confirmButton = { TextButton(onClick = { vm.applyFtp(r) }) { Text("Perbarui zona") } },
            dismissButton = { TextButton(onClick = vm::dismissFtp) { Text("Nanti") } },
        )
    }
}

@Composable
private fun PlanTab(vm: TrainingViewModel, onOpen: (Workout, Long) -> Unit) {
    val plan by vm.plan.collectAsStateWithLifecycle()
    val build by vm.build.collectAsStateWithLifecycle()
    var wizard by remember { mutableStateOf(false) }
    val p = plan
    if (p == null) {
        Column(Modifier.fillMaxSize()) {
            EmptyState(
                "Belum ada rencana latihan",
                "Tentukan target, tanggal event, dan hari latihan. Rencana dibagi menjadi fase dasar, membangun, puncak, dan tapering.",
                actionLabel = "Buat rencana",
                onAction = { wizard = true },
            )
        }
    } else {
        PlanView(p, vm, onOpen, onNew = { wizard = true })
    }
    if (build is PlanBuild.Working) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Menyusun rencana") },
            text = { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(); Spacer(Modifier.padding(8.dp)); Text("Sebentar...") } },
            confirmButton = {},
        )
    }
    (build as? PlanBuild.Done)?.note?.let { note ->
        Text(note, color = Palette.Warning, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
    }
    if (wizard) PlanWizard(onDismiss = { wizard = false }) { goal, event, days, level, ai -> vm.makePlan(goal, event, days, level, ai); wizard = false }
}

@Composable
private fun PlanView(p: StoredPlan, vm: TrainingViewModel, onOpen: (Workout, Long) -> Unit, onNew: () -> Unit) {
    val today = LocalDate.now()
    val done = p.sessions.count { it.doneActivityId != null || it.date.isBefore(today) }
    val next = p.sessions.firstOrNull { !it.date.isBefore(today) && it.doneActivityId == null }
    val phase = p.phases.lastOrNull { !it.first.isAfter(today) }?.second
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            BentoGrid(
                listOf(
                    BentoCell(span = 2) {
                        Text(p.goal, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Event ${p.event.format(dayFmt)} · ${ChronoUnit.DAYS.between(today, p.event).coerceAtLeast(0)} hari lagi",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.TextMuted,
                        )
                    },
                    BentoCell { BentoStat("Fase sekarang", phase?.label ?: "Belum mulai", valueStyle = MetricText.Small) },
                    BentoCell { BentoStat("Sesi", "$done/${p.sessions.size}", caption = "terlewati atau selesai", valueStyle = MetricText.Small) },
                ),
            )
        }
        next?.let { s ->
            val w = vm.workoutFor(s, p.level)
            if (w != null) item {
                SectionTitle("Berikutnya")
                SessionRow(s.date, w, highlight = true) { onOpen(w, s.id) }
            }
        }
        item { SectionTitle("Jadwal") }
        items(p.sessions, key = { it.id }) { s ->
            val w = vm.workoutFor(s, p.level) ?: return@items
            SessionRow(s.date, w, highlight = false, done = s.doneActivityId != null) { onOpen(w, s.id) }
        }
        item {
            Row(Modifier.padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onNew) { Text("Buat ulang rencana") }
                TextButton(onClick = vm::deletePlan) { Text("Hapus rencana", color = Palette.Error) }
            }
        }
    }
}

@Composable
private fun SessionRow(date: LocalDate, w: Workout, highlight: Boolean, done: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (highlight) Palette.Surface else Palette.Bg, MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(date.format(dayFmt).replaceFirstChar { it.titlecase(idLocale) }, style = MetricText.Label, color = Palette.TextMuted)
            Text(w.name, style = MaterialTheme.typography.titleSmall)
            Text("${Format.duration(w.durationS * 1000L)} · ${w.focus}", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, maxLines = 1)
        }
        if (done) Icon(Icons.Outlined.CheckCircle, contentDescription = "Selesai", tint = Palette.Success)
    }
    HorizontalDivider(color = Palette.Outline)
}

@Composable
private fun LibraryTab(vm: TrainingViewModel, onOpen: (Workout) -> Unit) {
    val level by vm.level.collectAsStateWithLifecycle()
    val list by vm.library.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Level.entries.forEach { l -> FilterChip(level == l, { vm.setLevel(l) }, { Text(l.label) }) }
            }
            Text(
                "Durasi kerja menyesuaikan level. Target watt dihitung dari FTP ${vm.athlete.value.ftpWatts} W di profil; tanpa power meter, ikuti zona HR yang sama.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextMuted,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        items(list, key = { it.id }) { w ->
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                SessionRowSimple(w) { onOpen(w) }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SessionRowSimple(w: Workout, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Palette.Surface, MaterialTheme.shapes.medium).clickable(role = Role.Button, onClick = onClick).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(w.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(Format.duration(w.durationS * 1000L), style = MetricText.Label, color = Palette.TextMuted)
        }
        Text(w.focus, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
        Spacer(Modifier.height(8.dp))
        IntensityStrip(w, Modifier.fillMaxWidth().height(28.dp))
    }
}

/** Blocks as wide as each step and as tall as its intensity, so the shape of a session reads at a glance. */
@Composable
internal fun IntensityStrip(w: Workout, modifier: Modifier) {
    val accent = Palette.Accent
    val muted = Palette.TextMuted.copy(alpha = 0.4f)
    Canvas(modifier.semantics { contentDescription = "Profil intensitas ${w.name}" }) {
        val total = w.durationS.toFloat().coerceAtLeast(1f)
        var x = 0f
        w.steps.forEach { s ->
            val width = size.width * s.durationS / total
            val pct = when (val t = s.target) {
                is Target.PowerPct -> ((t.low + t.high) / 2).toFloat()
                is Target.HrZone -> 0.5f + t.zone * 0.1f
                Target.Free -> 0.9f
            }.coerceIn(0.2f, 1.6f)
            val h = size.height * (pct / 1.6f)
            drawRect(if (s.work) accent else muted, Offset(x, size.height - h), Size((width - 1f).coerceAtLeast(1f), h))
            x += width
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutSheet(w: Workout, ftp: Int, onRide: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
            Text(w.name, style = MaterialTheme.typography.headlineSmall)
            Text(w.focus, style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
            Spacer(Modifier.height(12.dp))
            val workMin = w.steps.filter { it.work }.sumOf { it.durationS } / 60
            BentoGrid(
                listOf(
                    BentoCell(span = 2) { IntensityStrip(w, Modifier.fillMaxWidth().height(56.dp)) },
                    BentoCell { BentoStat("Total", Format.duration(w.durationS * 1000L), valueStyle = MetricText.Small) },
                    BentoCell { BentoStat("Kerja inti", "$workMin", "menit", valueStyle = MetricText.Small) },
                ),
            )
            Spacer(Modifier.height(12.dp))
            // Consecutive identical steps are folded into "3x" so long interval sets stay readable.
            val folded = fold(w)
            folded.forEach { (count, s) ->
                val target = s.target.watts(ftp)?.let { "${it.first}-${it.last} W" } ?: if (s.target is Target.HrZone) "Zona HR ${(s.target as Target.HrZone).zone}" else "bebas"
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text((if (count > 1) "${count}x " else "") + s.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("${Format.duration(s.durationS * 1000L)} · $target", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRide, modifier = Modifier.fillMaxWidth()) { Text("Kayuh sesi ini") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun fold(w: Workout): List<Pair<Int, Step>> {
    val out = ArrayList<Pair<Int, Step>>()
    var i = 0
    val steps = w.steps
    while (i < steps.size) {
        // Find the shortest repeating block starting here (1 or 2 steps).
        val two = if (i + 3 < steps.size && steps[i] == steps[i + 2] && steps[i + 1] == steps[i + 3]) 2 else 0
        if (two == 2) {
            var n = 1
            while (i + 2 * n + 1 < steps.size && steps[i + 2 * n] == steps[i] && steps[i + 2 * n + 1] == steps[i + 1]) n++
            out += n to steps[i]
            out += n to steps[i + 1]
            i += 2 * n
        } else {
            var n = 1
            while (i + n < steps.size && steps[i + n] == steps[i]) n++
            out += n to steps[i]
            i += n
        }
    }
    return out
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PlanWizard(onDismiss: () -> Unit, onCreate: (String, LocalDate, Set<DayOfWeek>, Level, Boolean) -> Unit) {
    var goal by remember { mutableStateOf("") }
    var event by remember { mutableStateOf(LocalDate.now().plusWeeks(8)) }
    var days by remember { mutableStateOf(setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY)) }
    var level by remember { mutableStateOf(Level.MENENGAH) }
    var useAi by remember { mutableStateOf(true) }
    var pickDate by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Rencana latihan", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(goal, { goal = it.take(60) }, label = { Text("Target") }, placeholder = { Text("Gran Fondo 100 km, naik FTP, ...") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { pickDate = true }) { Text("Tanggal event: ${event.format(dayFmt)}") }
            Text("Hari latihan", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DayOfWeek.entries.forEach { d ->
                    FilterChip(d in days, { days = if (d in days) days - d else days + d }, { Text(d.getDisplayName(TextStyle.SHORT, idLocale)) })
                }
            }
            Text("Level", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Level.entries.forEach { l -> FilterChip(level == l, { level = l }, { Text(l.label) }) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Susun dengan AI", style = MaterialTheme.typography.bodyLarge)
                    Text("Memakai penyedia AI Coach dan kondisi form-mu. Tanpa AI, aturan bawaan dipakai.", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                }
                Switch(checked = useAi, onCheckedChange = { useAi = it })
            }
            Button(
                onClick = { onCreate(goal.ifBlank { "Bugar dan konsisten" }, event, days, level, useAi) },
                enabled = days.isNotEmpty() && event.isAfter(LocalDate.now()),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Buat rencana") }
            Spacer(Modifier.height(12.dp))
        }
    }
    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = event.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { event = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickDate = false
                }) { Text("Pilih") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Batal") } },
        ) { DatePicker(state) }
    }
}
