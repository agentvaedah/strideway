package id.web.agentvaedah.strideway.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.web.agentvaedah.strideway.ui.theme.MetricText
import id.web.agentvaedah.strideway.ui.theme.Palette

@Composable
fun MetricTile(
    label: String,
    value: String,
    unit: String?,
    modifier: Modifier = Modifier,
    valueStyle: TextStyle = MetricText.Large,
    valueColor: Color = Palette.Text,
    caption: String? = null,
    captionColor: Color = Palette.TextMuted,
) {
    Column(
        modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clearAndSetSemantics { contentDescription = "$label $value ${unit.orEmpty()} ${caption.orEmpty()}".trim() },
    ) {
        Text(label, style = MetricText.Label, color = Palette.TextMuted, maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = valueStyle, color = valueColor, maxLines = 1, overflow = TextOverflow.Clip)
            if (unit != null) {
                Spacer(Modifier.width(4.dp))
                Text(unit, style = MetricText.Label, color = Palette.TextMuted, modifier = Modifier.padding(bottom = 6.dp))
            }
        }
        if (caption != null) Text(caption, style = MetricText.Label, color = captionColor, maxLines = 1)
    }
}

/** Compact label/value pair for summary rows on cards. */
@Composable
fun StatPair(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MetricText.Small, color = Palette.Text, maxLines = 1)
        Text(label, style = MetricText.Label, color = Palette.TextMuted, maxLines = 1)
    }
}
