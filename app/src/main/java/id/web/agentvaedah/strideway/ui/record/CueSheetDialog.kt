package id.web.agentvaedah.strideway.ui.record

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import id.web.agentvaedah.strideway.core.route.Cue
import id.web.agentvaedah.strideway.ui.theme.MetricText
import id.web.agentvaedah.strideway.ui.theme.Palette
import java.util.Locale

private fun km(m: Double) = String.format(Locale.US, "%.1f", m / 1000).replace('.', ',')

/** Every turn of the route with its distance from the start, read before setting off. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CueSheetDialog(name: String, cues: List<Cue>, lengthM: Double, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding()) {
            Text("Daftar belokan", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 16.dp))
            Text("$name · ${km(lengthM)} km · ${cues.size} belokan", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, modifier = Modifier.padding(horizontal = 16.dp))
            LazyColumn(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                itemsIndexed(cues) { i, c ->
                    val phrase = c.maneuver.type.phrase(c.maneuver.exit).replaceFirstChar { it.uppercase() }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).clearAndSetSemantics {
                            contentDescription = "Belokan ${i + 1}, di kilometer ${km(c.atM)}: $phrase"
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(c.maneuver.type.icon(), contentDescription = null, tint = Palette.Accent, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(phrase, style = MaterialTheme.typography.bodyLarge)
                            c.maneuver.street?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted) }
                        }
                        Text("${km(c.atM)} km", style = MetricText.Small, color = Palette.TextMuted)
                    }
                    HorizontalDivider(color = Palette.Outline)
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Flag, contentDescription = null, tint = Palette.Text, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Sampai di tujuan", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text("${km(lengthM)} km", style = MetricText.Small, color = Palette.TextMuted)
                    }
                }
            }
        }
    }
}
