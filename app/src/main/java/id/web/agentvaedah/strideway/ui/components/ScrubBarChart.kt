package id.web.agentvaedah.strideway.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import id.web.agentvaedah.strideway.ui.theme.MetricText
import id.web.agentvaedah.strideway.ui.theme.Palette

/** One bar: [label] names the period on the axis and in the readout, [value] is already in display units. */
data class BarPoint(val label: String, val value: Double)

/**
 * Single-series column chart the reader can scrub: drag or tap across it and the readout above shows the
 * bar under the finger. Without a selection the readout shows [summary]. Bars are capped at 24 dp with a
 * rounded top and a square base on one hairline baseline.
 */
@Composable
fun ScrubBarChart(
    points: List<BarPoint>,
    format: (Double) -> String,
    summary: String,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
) {
    var selected by remember(points) { mutableStateOf<Int?>(null) }
    val barColor = Palette.Accent
    val dimColor = Palette.Accent.copy(alpha = 0.35f)
    val baseline = Palette.Outline
    val cursor = Palette.TextMuted

    Column(modifier) {
        val sel = selected?.let { points.getOrNull(it) }
        Text(
            if (sel != null) "${sel.label} · ${format(sel.value)}" else summary,
            style = MetricText.Small,
            color = Palette.Text,
        )
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .semantics { contentDescription = description }
                .pointerInput(points) {
                    detectTapGestures { o -> selected = indexAt(o.x, size.width, points.size).takeIf { it != selected } }
                }
                .pointerInput(points) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> selected = indexAt(o.x, size.width, points.size) },
                        onDragEnd = { },
                    ) { change, _ ->
                        change.consume()
                        selected = indexAt(change.position.x, size.width, points.size)
                    }
                },
        ) {
            if (points.isEmpty()) return@Canvas
            val max = points.maxOf { it.value }.coerceAtLeast(1e-9)
            val slot = size.width / points.size
            val barW = minOf(24.dp.toPx(), slot * 0.7f)
            val top = 6.dp.toPx()
            val usable = size.height - top
            val r = minOf(4.dp.toPx(), barW / 2)
            points.forEachIndexed { i, p ->
                val h = (p.value / max * usable).toFloat()
                if (h <= 0f) return@forEachIndexed
                val x = i * slot + (slot - barW) / 2
                val y = size.height - h
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = x, top = y, right = x + barW, bottom = size.height,
                            topLeftCornerRadius = CornerRadius(r, r),
                            topRightCornerRadius = CornerRadius(r, r),
                            bottomLeftCornerRadius = CornerRadius.Zero,
                            bottomRightCornerRadius = CornerRadius.Zero,
                        ),
                    )
                }
                drawPath(path, if (selected == null || selected == i) barColor else dimColor)
            }
            drawLine(baseline, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), strokeWidth = 1.dp.toPx())
            selected?.let { i ->
                val cx = i * slot + slot / 2
                drawLine(cursor, Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 1.dp.toPx())
            }
        }
        // First, middle and last labels only: enough to anchor the time axis without crowding.
        if (points.isNotEmpty()) {
            Row(Modifier.fillMaxWidth()) {
                val idx = listOf(0, points.size / 2, points.lastIndex).distinct()
                idx.forEachIndexed { n, i ->
                    Text(
                        points[i].label,
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.TextMuted,
                        textAlign = when (n) {
                            0 -> TextAlign.Start
                            idx.lastIndex -> TextAlign.End
                            else -> TextAlign.Center
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private fun indexAt(x: Float, width: Int, count: Int): Int? =
    if (count == 0 || width == 0) null else (x / width * count).toInt().coerceIn(0, count - 1)
