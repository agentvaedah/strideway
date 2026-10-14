package id.web.agentvaedah.strideway.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import id.web.agentvaedah.strideway.core.geo.Heading
import id.web.agentvaedah.strideway.ui.theme.Palette
import kotlin.math.roundToInt

/**
 * The needle always points to true north on screen (it counter-rotates with the map), and the label
 * underneath gives the direction of travel. With [onToggle], a tap switches heading-up and north-up.
 */
@Composable
fun MapCompass(mapBearing: Float, heading: Float?, headingUp: Boolean, onToggle: (() -> Unit)? = null) {
    val travel = heading?.let { "${Heading.cardinal(it.toDouble())} ${it.roundToInt()}°" } ?: "--"
    val label = buildString {
        append(heading?.let { "Arah gerak ${Heading.cardinal(it.toDouble())}, ${it.roundToInt()} derajat. " } ?: "Arah belum diketahui. ")
        when {
            onToggle == null -> append("Utara di atas")
            headingUp -> append("Peta mengikuti arah gerak, ketuk untuk utara di atas")
            else -> append("Utara di atas, ketuk untuk mengikuti arah gerak")
        }
    }
    Column(
        Modifier
            .background(Palette.Bg.copy(alpha = 0.85f), MaterialTheme.shapes.medium)
            .then(if (onToggle != null) Modifier.clickable(role = Role.Button, onClick = onToggle) else Modifier)
            .semantics { contentDescription = label }
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val northColor = Palette.Error
        val southColor = Palette.TextMuted
        val hubColor = Palette.Bg
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(30.dp).rotate(-mapBearing)) {
                val w = size.width
                val h = size.height
                val north = Path().apply {
                    moveTo(w / 2, 0f); lineTo(w * 0.72f, h / 2); lineTo(w * 0.28f, h / 2); close()
                }
                val south = Path().apply {
                    moveTo(w / 2, h); lineTo(w * 0.72f, h / 2); lineTo(w * 0.28f, h / 2); close()
                }
                drawPath(north, northColor)
                drawPath(south, southColor)
                drawCircle(hubColor, radius = 2.dp.toPx(), center = Offset(w / 2, h / 2))
            }
        }
        Text(travel, style = MaterialTheme.typography.labelMedium, color = Palette.Text)
    }
}
