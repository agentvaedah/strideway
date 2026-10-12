package id.web.agentvaedah.strideway.ui.record

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.web.agentvaedah.strideway.ui.theme.Palette

/** Large on-screen equivalents of the volume-key controls, sized for gloved fingers. */
@Composable
fun ControlBar(paused: Boolean, onPause: () -> Unit, onStop: () -> Unit, onCycle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.Surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = onCycle, modifier = Modifier.weight(1f).height(52.dp)) { Text("Tampilan") }
        Button(onClick = onPause, modifier = Modifier.weight(1.4f).height(52.dp)) {
            Text(if (paused) "Lanjut" else "Jeda")
        }
        Button(
            onClick = onStop,
            modifier = Modifier.weight(1f).height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.SurfaceHigh, contentColor = Palette.Text),
        ) { Text("Selesai") }
    }
}
