package id.web.agentvaedah.strideway.ui.you

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.web.agentvaedah.strideway.core.format.Format
import id.web.agentvaedah.strideway.core.model.Sport
import id.web.agentvaedah.strideway.core.records.Better
import id.web.agentvaedah.strideway.core.records.RecordKey
import id.web.agentvaedah.strideway.core.records.RecordRank
import id.web.agentvaedah.strideway.data.db.ActivityEntity
import id.web.agentvaedah.strideway.ui.motion.pressScale
import id.web.agentvaedah.strideway.ui.theme.MetricText
import id.web.agentvaedah.strideway.ui.theme.Palette
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val dayFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("id-ID"))
private val Gold = Color(0xFFE0A400)
private val Silver = Color(0xFF9AA0A6)
private val Bronze = Color(0xFFB8733A)
private val MedalInk = Color(0xFF0A0A0B)

/** Short title for a card: the distance for timed efforts, otherwise what is measured. */
private val RecordKey.short: String
    get() = when (this) {
        RecordKey.RIDE_LONGEST, RecordKey.RUN_LONGEST -> "Terjauh"
        RecordKey.RIDE_ELEVATION -> "Naik terbanyak"
        RecordKey.RIDE_AVG_SPEED -> "Rata-rata tercepat"
        RecordKey.RUN_HALF -> "Half marathon"
        else -> "${(meters / 1000).toInt()} km"
    }

fun recordValue(key: RecordKey, v: Double): String = when (key) {
    RecordKey.RIDE_LONGEST, RecordKey.RUN_LONGEST -> "${Format.km(v).replace('.', ',')} km"
    RecordKey.RIDE_ELEVATION -> "${Format.integer(v)} m"
    RecordKey.RIDE_AVG_SPEED -> "${Format.oneDecimal(v).replace('.', ',')} km/j"
    else -> Format.duration((v * 1000).toLong())
}

/** "0:42 lebih cepat" for times, "+3,2 km" for amounts; null when there was nothing before. */
private fun delta(key: RecordKey, now: Double, before: Double?): String? {
    before ?: return null
    val d = abs(now - before)
    return when {
        key.isEffort -> "${Format.duration((d * 1000).toLong())} lebih cepat"
        key == RecordKey.RIDE_AVG_SPEED -> "+${Format.oneDecimal(d).replace('.', ',')} km/j"
        key == RecordKey.RIDE_ELEVATION -> "+${Format.integer(d)} m"
        else -> "+${Format.km(d).replace('.', ',')} km"
    }
}

@Composable
fun Medal(rank: Int, size: Dp, modifier: Modifier = Modifier) {
    val fill = when (rank) { 1 -> Gold; 2 -> Silver; else -> Bronze }
    Box(
        modifier
            .size(size)
            .background(fill, CircleShape)
            .border(BorderStroke(size * 0.06f, Color.Black.copy(alpha = 0.12f)), CircleShape)
            .semantics { contentDescription = "Peringkat $rank" },
        contentAlignment = Alignment.Center,
    ) {
        Text(rank.toString(), color = MedalInk, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.46f).sp)
    }
}

/**
 * Personal records the way Strava shows them: one card per distance with a medal, the record in the brand
 * face and how much it improved on the one before; tapping opens the top three. Distances not ridden yet
 * appear as dashed targets so the next goal is visible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsSection(
    top: Map<RecordKey, List<RecordRank>>,
    previous: Map<RecordKey, Double>,
    activities: Map<Long, ActivityEntity>,
    onOpenActivity: (Long) -> Unit,
) {
    var sport by remember { mutableStateOf(if (top.keys.any { it.sport == Sport.RIDE } || top.isEmpty()) Sport.RIDE else Sport.RUN) }
    var open by remember { mutableStateOf<RecordKey?>(null) }
    val keys = RecordKey.entries.filter { it.sport == sport }
    // Timed distances first (the cards riders chase), then the totals.
    val efforts = keys.filter { it.isEffort }
    val totals = keys.filter { !it.isEffort }

    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(sport == Sport.RIDE, { sport = Sport.RIDE }, { Text("Ride") })
        FilterChip(sport == Sport.RUN, { sport = Sport.RUN }, { Text("Run") })
    }
    Spacer(Modifier.height(10.dp))
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(efforts, key = { it.name }) { k ->
            val best = top[k]?.firstOrNull()
            if (best == null) LockedCard(k) else RecordCard(k, best, delta(k, best.value, previous[k]), activities[best.activityId]?.name) { open = k }
        }
    }
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        totals.forEach { k ->
            val best = top[k]?.firstOrNull()
            Column(
                Modifier
                    .weight(1f)
                    .pressScale()
                    .background(Palette.Surface, MaterialTheme.shapes.large)
                    .clickable(enabled = best != null, role = Role.Button) { open = k }
                    .padding(12.dp),
            ) {
                Text(k.short, style = MetricText.Label, color = Palette.TextMuted, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Text(best?.let { recordValue(k, it.value) } ?: "--", style = MetricText.Small, color = Palette.Text, maxLines = 1)
            }
        }
    }

    open?.let { k ->
        ModalBottomSheet(onDismissRequest = { open = null }) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                Text(k.label, style = MaterialTheme.typography.headlineSmall)
                Text(
                    if (k.better == Better.LOWER) "Tiga waktu terbaik" else "Tiga terbaik",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextMuted,
                )
                Spacer(Modifier.height(12.dp))
                top[k].orEmpty().forEach { r ->
                    val a = activities[r.activityId]
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .pressScale()
                            .background(Palette.Surface, MaterialTheme.shapes.medium)
                            .clickable(role = Role.Button, onClickLabel = "Buka aktivitas") { open = null; onOpenActivity(r.activityId) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Medal(r.rank, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a?.name ?: "Aktivitas", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text(date(r.startMs), style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                        }
                        Text(recordValue(k, r.value), style = MetricText.Medium, color = Palette.Text)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordCard(k: RecordKey, best: RecordRank, delta: String?, activityName: String?, onClick: () -> Unit) {
    Column(
        Modifier
            .width(196.dp)
            .pressScale()
            .background(Palette.Surface, MaterialTheme.shapes.large)
            .clickable(role = Role.Button, onClickLabel = "Lihat tiga terbaik", onClick = onClick)
            .padding(14.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Rekor ${k.label}, ${recordValue(k, best.value)}${delta?.let { ", $it" }.orEmpty()}" },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(k.short, style = MaterialTheme.typography.titleSmall, color = Palette.TextMuted, modifier = Modifier.weight(1f))
            Medal(1, 34.dp)
        }
        Spacer(Modifier.height(10.dp))
        Text(recordValue(k, best.value), style = MetricText.Large, color = Palette.Text, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(delta ?: "Rekor pertama", style = MaterialTheme.typography.labelLarge, color = Palette.Accent, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Text(listOfNotNull(date(best.startMs), activityName).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, maxLines = 1)
    }
}

@Composable
private fun LockedCard(k: RecordKey) {
    val verb = if (k.sport == Sport.RIDE) "Gowes" else "Lari"
    Column(
        Modifier
            .width(196.dp)
            .border(BorderStroke(1.5.dp, Palette.Outline), MaterialTheme.shapes.large)
            .padding(14.dp),
    ) {
        Text(k.short, style = MaterialTheme.typography.titleSmall, color = Palette.TextMuted)
        Spacer(Modifier.height(18.dp))
        Text("$verb ${k.short} untuk membuka rekor ini", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
    }
}

private fun date(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(dayFmt)
