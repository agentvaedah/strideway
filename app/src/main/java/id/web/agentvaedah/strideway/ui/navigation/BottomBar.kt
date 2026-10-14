package id.web.agentvaedah.strideway.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import id.web.agentvaedah.strideway.ui.theme.Palette

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val leftTabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Outlined.Home),
    Tab(Routes.MAPS, "Peta", Icons.Outlined.Map),
)
private val rightTabs = listOf(
    Tab(Routes.TRAINING, "Latihan", Icons.Outlined.FitnessCenter),
    Tab(Routes.YOU, "You", Icons.Outlined.Person),
)

/**
 * Four standard tabs around a raised Record button: recording is the one action
 * that has to be reachable with a glove on, so it gets the largest target and the accent.
 */
@Composable
fun BottomBar(currentRoute: String?, recording: Boolean, onSelect: (String) -> Unit) {
    Column(Modifier.background(Palette.Bg).navigationBarsPadding()) {
        HorizontalDivider(color = Palette.Outline)
        Row(
            Modifier.fillMaxWidth().height(68.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leftTabs.forEach { TabItem(it, currentRoute == it.route, onSelect, Modifier.weight(1f)) }
            RecordTab(currentRoute == Routes.RECORD, recording, { onSelect(Routes.RECORD) }, Modifier.weight(1f))
            rightTabs.forEach { TabItem(it, currentRoute == it.route, onSelect, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun TabItem(tab: Tab, selected: Boolean, onSelect: (String) -> Unit, modifier: Modifier) {
    val color = if (selected) Palette.Text else Palette.TextMuted
    Column(
        modifier
            .height(68.dp)
            .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(tab.route) }),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(tab.icon, contentDescription = null, tint = color)
        Spacer(Modifier.height(4.dp))
        Text(tab.label, style = MaterialTheme.typography.labelMedium, color = color)
        Spacer(Modifier.height(2.dp))
        Box(
            Modifier
                .size(width = 16.dp, height = 2.dp)
                .background(if (selected) Palette.Accent else Palette.Bg),
        )
    }
}

@Composable
private fun RecordTab(selected: Boolean, recording: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier
            .height(68.dp)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .background(Palette.Accent, CircleShape)
                .then(if (selected) Modifier.border(2.dp, Palette.Text, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            // Filled dot = idle (press to record); square = a session is running.
            if (recording) {
                Box(Modifier.size(14.dp).background(Palette.OnAccent, MaterialTheme.shapes.extraSmall))
            } else {
                Box(Modifier.size(16.dp).background(Palette.OnAccent, CircleShape))
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(if (recording) "Merekam" else "Rekam", style = MaterialTheme.typography.labelMedium, color = if (selected) Palette.Text else Palette.TextMuted)
    }
}
