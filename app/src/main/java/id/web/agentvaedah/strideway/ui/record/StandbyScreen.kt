package id.web.agentvaedah.strideway.ui.record

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.AltRoute
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.web.agentvaedah.strideway.core.model.LatLon
import id.web.agentvaedah.strideway.core.model.Sport
import id.web.agentvaedah.strideway.ui.components.MapHost
import id.web.agentvaedah.strideway.ui.components.MapLayers
import id.web.agentvaedah.strideway.ui.components.toLatLng
import id.web.agentvaedah.strideway.ui.theme.Palette
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

/**
 * Pre-ride screen in the Strava layout: the map around the rider, the chosen sport, a big Start button
 * and Add Route. Nothing records until Start is pressed and permissions are granted.
 */
@Composable
fun StandbyScreen(vm: RecordViewModel) {
    val sport by vm.sport.collectAsStateWithLifecycle()
    val navRoute by vm.navRoute.collectAsStateWithLifecycle()
    val gpsError by vm.gpsError.collectAsStateWithLifecycle()
    val mapBase by vm.mapBase.collectAsStateWithLifecycle()
    val savedRoutes by vm.savedRoutes.collectAsStateWithLifecycle()
    val workout by vm.plannedWorkout.collectAsStateWithLifecycle()
    var permissionDenied by remember { mutableStateOf(false) }
    var gpsOff by remember { mutableStateOf(false) }
    var pickRoute by remember { mutableStateOf(false) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val here: LatLon? = remember(map) { vm.lastKnownPosition() }

    val permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        addAll(vm.blePermissions())
    }.distinct().toTypedArray()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val locationOk = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        permissionDenied = !locationOk
        if (locationOk) {
            if (vm.gpsEnabled()) vm.start() else gpsOff = true
        }
    }

    val routeColor = Palette.Info
    val meColor = Palette.Accent
    Box(Modifier.fillMaxSize()) {
        MapHost(
            base = mapBase,
            modifier = Modifier.fillMaxSize(),
            onMapReady = { map = it },
            onStyleLoaded = { _, st -> style = st },
        )
        LaunchedEffect(style, navRoute, here, routeColor) {
            val st = style ?: return@LaunchedEffect
            val pts = navRoute?.points.orEmpty()
            MapLayers.setLine(st, "standby-route", pts, routeColor, 5f, lineStyle = MapLayers.LineStyle.ROUTE)
            MapLayers.setHeadingPuck(st, "standby-me", here, null, meColor)
            val m = map ?: return@LaunchedEffect
            when {
                pts.size >= 2 -> MapLayers.fit(m, pts + listOfNotNull(here), paddingPx = 140)
                here != null -> m.moveCamera(CameraUpdateFactory.newLatLngZoom(here.toLatLng(), 15.0))
            }
        }

        IconButton(
            onClick = { here?.let { map?.animateCamera(CameraUpdateFactory.newLatLngZoom(it.toLatLng(), 15.0)) } },
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).background(Palette.Bg.copy(alpha = 0.88f), CircleShape),
        ) { Icon(Icons.Outlined.MyLocation, contentDescription = "Lokasi sekarang") }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Palette.Bg.copy(alpha = 0.94f), MaterialTheme.shapes.large)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            workout?.let { w ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.FitnessCenter, contentDescription = null, tint = Palette.Accent)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(w.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text("Sesi latihan, interval berjalan otomatis", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                    }
                    IconButton(onClick = vm::clearWorkout) { Icon(Icons.Outlined.Close, contentDescription = "Batalkan sesi latihan") }
                }
            }
            navRoute?.let { r ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AltRoute, contentDescription = null, tint = Palette.Info)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text("Rute navigasi aktif", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                    }
                    IconButton(onClick = vm::clearNavRoute) { Icon(Icons.Outlined.Close, contentDescription = "Lepas rute") }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SportButton(
                    icon = if (sport == Sport.RIDE) Icons.Outlined.DirectionsBike else Icons.AutoMirrored.Outlined.DirectionsRun,
                    label = if (sport == Sport.RIDE) "Ride" else "Run",
                    onClick = { vm.selectSport(if (sport == Sport.RIDE) Sport.RUN else Sport.RIDE) },
                )
                Button(
                    onClick = { launcher.launch(permissions) },
                    modifier = Modifier.size(96.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Palette.OnAccent),
                ) { Text("Mulai", style = MaterialTheme.typography.titleMedium) }
                SportButton(
                    icon = Icons.Outlined.AltRoute,
                    label = if (navRoute == null) "Tambah rute" else "Ganti rute",
                    onClick = { pickRoute = true },
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Volume Atas jeda/lanjut, Volume Bawah ganti tampilan.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextMuted,
                textAlign = TextAlign.Center,
            )
            val message = when {
                permissionDenied -> "Izin lokasi ditolak. Strideway butuh GPS untuk merekam jarak dan rute. Buka Pengaturan > Aplikasi > Strideway > Izin."
                gpsOff -> "GPS mati. Aktifkan Lokasi di panel cepat, lalu tekan Mulai lagi."
                gpsError != null -> gpsError
                else -> null
            }
            if (message != null) {
                Spacer(Modifier.height(8.dp))
                Text(message, color = Palette.Error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
        }
    }

    if (pickRoute) {
        RouteSwitchSheet(
            routes = savedRoutes,
            activeId = navRoute?.id,
            onSelect = { id -> vm.selectRoute(id); pickRoute = false },
            onDismiss = { pickRoute = false },
        )
    }
}

@Composable
private fun SportButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(56.dp).background(Palette.SurfaceHigh, CircleShape),
        ) { Icon(icon, contentDescription = label, tint = Palette.Text) }
        Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted)
    }
}
