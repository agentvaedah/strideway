package id.web.agentvaedah.strideway.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import id.web.agentvaedah.strideway.ui.theme.MetricText
import id.web.agentvaedah.strideway.ui.theme.Palette

/** One cell of a [BentoGrid]: [span] 2 takes the full row, 1 shares it with a neighbour. */
class BentoCell(val span: Int = 1, val content: @Composable () -> Unit)

/**
 * Two-column grid for groups of numbers. Cells differ in size on purpose: the figure that matters most on a
 * screen spans the row, supporting figures share it. Rows wrap automatically and cells in a row match heights.
 * Lays out as plain rows, so it sits inside a LazyColumn or scrolling Column without nested scrolling.
 */
@Composable
fun BentoGrid(cells: List<BentoCell>, modifier: Modifier = Modifier) {
    val rows = ArrayList<List<BentoCell>>()
    var pending: BentoCell? = null
    for (c in cells) {
        if (c.span >= 2) {
            pending?.let { rows += listOf(it) }
            pending = null
            rows += listOf(c)
        } else if (pending == null) {
            pending = c
        } else {
            rows += listOf(pending!!, c)
            pending = null
        }
    }
    pending?.let { rows += listOf(it) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(GAP)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(GAP)) {
                row.forEach { cell ->
                    Column(
                        Modifier
                            .weight(if (row.size == 1 && cell.span < 2) 0.5f else 1f)
                            .fillMaxHeight()
                            .background(Palette.Surface, MaterialTheme.shapes.large)
                            .padding(14.dp),
                    ) { cell.content() }
                }
                // A lone small cell keeps its half width instead of stretching into a banner.
                if (row.size == 1 && row[0].span < 2) Spacer(Modifier.weight(0.5f))
            }
        }
    }
}

private val GAP = 10.dp

/** Label above a big value with its unit; [caption] adds one line of context under it. */
@Composable
fun BentoStat(
    label: String,
    value: String,
    unit: String? = null,
    caption: String? = null,
    valueStyle: TextStyle = MetricText.Large,
    valueColor: Color = Palette.Text,
) {
    Column(Modifier.clearAndSetSemantics { contentDescription = listOfNotNull(label, value, unit, caption).joinToString(" ") }) {
        Text(label, style = MetricText.Label, color = Palette.TextMuted, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = valueStyle, color = valueColor, maxLines = 1)
            if (unit != null) {
                Spacer(Modifier.width(4.dp))
                Text(unit, style = MetricText.Label, color = Palette.TextMuted, modifier = Modifier.padding(bottom = 6.dp))
            }
        }
        if (caption != null) Text(caption, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, maxLines = 2)
    }
}
