package id.web.agentvaedah.strideway.ui.record

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AltRoute
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material.icons.outlined.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import id.web.agentvaedah.strideway.ui.theme.Palette

/** Small round buttons stacked on the right edge of the live map. Active toggles are drawn in the accent colour. */
@Composable
fun MapControls(
    tilt3d: Boolean,
    camera: CameraMode,
    hasRoute: Boolean,
    routeAlerts: Boolean,
    onToggle3d: () -> Unit,
    onLocate: () -> Unit,
    onOverview: () -> Unit,
    onSwitchRoute: () -> Unit,
    onToggleAlerts: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MapButton(Icons.Outlined.ViewInAr, if (tilt3d) "Matikan tampilan 3D" else "Tampilan 3D", tilt3d, onToggle3d)
        MapButton(Icons.Outlined.MyLocation, "Kembali ke lokasi sekarang", camera == CameraMode.FOLLOW, onLocate)
        MapButton(Icons.Outlined.ZoomOutMap, "Lihat seluruh rute", camera == CameraMode.OVERVIEW, onOverview)
        MapButton(Icons.Outlined.AltRoute, "Ganti rute", false, onSwitchRoute)
        if (hasRoute) {
            MapButton(
                if (routeAlerts) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsOff,
                if (routeAlerts) "Matikan peringatan rute" else "Nyalakan peringatan rute",
                routeAlerts,
                onToggleAlerts,
            )
        }
    }
}

@Composable
private fun MapButton(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .background(if (active) Palette.Accent else Palette.Bg.copy(alpha = 0.85f), MaterialTheme.shapes.medium),
    ) {
        Icon(icon, contentDescription = label, tint = if (active) Palette.OnAccent else Palette.Text)
    }
}
