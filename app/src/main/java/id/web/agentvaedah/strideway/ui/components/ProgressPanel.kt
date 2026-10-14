package id.web.agentvaedah.strideway.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.web.agentvaedah.strideway.core.format.Format
import id.web.agentvaedah.strideway.core.stats.StatMetric
import id.web.agentvaedah.strideway.core.stats.StatRange
import id.web.agentvaedah.strideway.core.stats.StatSeries
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFmt = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("id-ID"))

fun formatMetric(metric: StatMetric, v: Double): String = when (metric) {
    StatMetric.DISTANCE -> "${Format.oneDecimal(v).replace('.', ',')} km"
    StatMetric.ELEVATION -> "${Format.integer(v)} m"
    StatMetric.TIME -> Format.duration((v * 3_600_000).toLong())
    StatMetric.ACTIVITIES -> "${v.toInt()} aktivitas"
}

/** Range chips (7H to 1T), metric chips, and the scrubbable chart for one [StatSeries]. */
@Composable
fun ProgressPanel(
    series: StatSeries,
    metric: StatMetric,
    onRange: (StatRange) -> Unit,
    onMetric: (StatMetric) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatRange.entries.forEach { r -> FilterChip(series.range == r, { onRange(r) }, { Text(r.label) }) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatMetric.entries.forEach { m -> FilterChip(metric == m, { onMetric(m) }, { Text(m.label) }) }
        }
        val prefix = if (series.range.daily) "" else "Minggu "
        ScrubBarChart(
            points = series.buckets.map { BarPoint(prefix + it.start.format(dayFmt), metric.of(it)) },
            format = { formatMetric(metric, it) },
            summary = "Total ${formatMetric(metric, metric.of(series.total))}",
            description = "${metric.label} per ${if (series.range.daily) "hari" else "minggu"}, rentang ${series.range.label}. " +
                "Total ${formatMetric(metric, metric.of(series.total))}. Geser untuk melihat tiap batang.",
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}
