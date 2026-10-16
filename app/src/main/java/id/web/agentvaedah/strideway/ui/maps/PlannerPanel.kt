package id.web.agentvaedah.strideway.ui.maps

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import id.web.agentvaedah.strideway.core.format.Format
import id.web.agentvaedah.strideway.core.route.RouteOption
import id.web.agentvaedah.strideway.ui.theme.Palette

/**
 * Route planner docked at the bottom of the map without a scrim, so the map stays usable. Expanded it shows
 * the full planner (up to 60% of the screen); collapsed it shrinks to a strip of about a tenth of the screen
 * that names the chosen alternative and lets the rider step through the others while looking at the map.
 */
@Composable
fun PlannerPanel(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    options: List<RouteOption>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onClose: () -> Unit,
    onPreview: (() -> Unit)? = null,
    onNavigate: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val screenH = LocalConfiguration.current.screenHeightDp.dp
    Column(
        Modifier
            .fillMaxWidth()
            .animateContentSize()
            .background(Palette.Surface, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .pointerInput(expanded) {
                var drag = 0f
                detectVerticalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = {
                        if (drag > 40f) onExpandedChange(false)
                        if (drag < -40f) onExpandedChange(true)
                    },
                ) { _, dy -> drag += dy }
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = if (expanded) "Perkecil rencana rute" else "Perbesar rencana rute") {
                    onExpandedChange(!expanded)
                }
                .padding(top = 8.dp, bottom = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(width = 40.dp, height = 4.dp).background(Palette.Outline, MaterialTheme.shapes.extraSmall))
        }
        if (expanded) {
            Row(Modifier.fillMaxWidth().padding(end = 4.dp), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { onExpandedChange(false) }) { Icon(Icons.Outlined.ExpandMore, contentDescription = "Perkecil, lihat peta") }
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Tutup rencana rute") }
            }
            Column(Modifier.heightIn(max = screenH * 0.6f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
                content()
            }
        } else {
            CollapsedStrip(options, selected, onSelect, onExpand = { onExpandedChange(true) }, onClose = onClose, minHeight = screenH * 0.1f)
            if (options.isNotEmpty() && (onPreview != null || onNavigate != null)) {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    onPreview?.let { OutlinedButton(onClick = it, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.ViewInAr, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Pratinjau 3D") } }
                    onNavigate?.let { Button(onClick = it, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Navigation, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Navigasi") } }
                }
            }
        }
    }
}

@Composable
private fun CollapsedStrip(
    options: List<RouteOption>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    minHeight: Dp,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = minHeight).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val o = options.getOrNull(selected)
        if (options.size > 1) {
            IconButton(onClick = { onSelect((selected - 1 + options.size) % options.size) }) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Alternatif sebelumnya")
            }
        } else {
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f).clickable(role = Role.Button, onClick = onExpand)) {
            if (o != null) {
                Text(
                    "Alternatif ${selected + 1} dari ${options.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.TextMuted,
                )
                Text(
                    "${Format.km(o.lengthM)} km · naik ${Format.integer(o.ascendM)} m · aspal ${(o.pavedShare * 100).toInt()}%",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                )
            } else {
                Text("Rencana rute", style = MaterialTheme.typography.titleSmall)
                Text("Ketuk untuk memilih titik awal dan tujuan", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
            }
        }
        if (options.size > 1) {
            IconButton(onClick = { onSelect((selected + 1) % options.size) }) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Alternatif berikutnya")
            }
        }
        IconButton(onClick = onExpand) { Icon(Icons.Outlined.ExpandLess, contentDescription = "Perbesar rencana rute") }
        IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Tutup rencana rute") }
    }
}
