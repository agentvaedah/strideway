package id.web.agentvaedah.strideway.ui.record

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import id.web.agentvaedah.strideway.core.format.Format
import id.web.agentvaedah.strideway.core.training.Compliance
import id.web.agentvaedah.strideway.core.training.Target
import id.web.agentvaedah.strideway.core.training.WorkoutState
import id.web.agentvaedah.strideway.ui.theme.MetricText
import id.web.agentvaedah.strideway.ui.theme.Palette

/**
 * The interval being ridden: name, target, time left in the step, and whether power is under, in or over
 * the target. Compliance is spelled out as a word, not only shown by colour.
 */
@Composable
fun WorkoutStrip(w: WorkoutState, modifier: Modifier = Modifier) {
    val target = w.targetWatts?.let { if (it.first == it.last) "${it.first} W" else "${it.first}-${it.last} W" }
        ?: (w.step.target as? Target.HrZone)?.let { "Zona HR ${it.zone}" }
        ?: "Bebas"
    val (word, color) = when (w.compliance) {
        Compliance.UNDER -> "Naikkan" to Palette.Warning
        Compliance.IN -> "Pas" to Palette.Success
        Compliance.OVER -> "Turunkan" to Palette.Error
        Compliance.NONE -> "" to Palette.TextMuted
    }
    val stepTotal = w.step.durationS.coerceAtLeast(1)
    Column(
        modifier
            .fillMaxWidth()
            .background(Palette.Surface)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clearAndSetSemantics {
                contentDescription = "${w.step.label}, target $target, sisa ${Format.duration(w.remainingS * 1000L)}" +
                    if (word.isNotEmpty()) ", $word" else ""
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(w.step.label, style = MaterialTheme.typography.labelLarge, color = Palette.TextMuted, maxLines = 1)
                Text(target, style = MetricText.Small, color = Palette.Text)
            }
            if (word.isNotEmpty()) {
                Text(word, style = MaterialTheme.typography.labelLarge, color = color)
                Spacer(Modifier.width(12.dp))
            }
            Text(Format.duration(w.remainingS * 1000L), style = MetricText.Medium, color = Palette.Text)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { 1f - w.remainingS.toFloat() / stepTotal },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = Palette.Accent,
            trackColor = Palette.SurfaceHigh,
            drawStopIndicator = {},
        )
    }
}
