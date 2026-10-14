package id.web.agentvaedah.strideway.ui.you

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.web.agentvaedah.strideway.data.prefs.ThemeMode
import id.web.agentvaedah.strideway.ui.components.BackTopBar
import id.web.agentvaedah.strideway.ui.components.SectionTitle
import id.web.agentvaedah.strideway.ui.theme.Palette
import java.time.LocalDate

/** Everything that is configured once and then left alone, reached from the gear on the You tab. */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenDevices: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenKeys: () -> Unit,
    onOpenVoice: () -> Unit,
    onOpenMaps: () -> Unit,
    onOpenFuel: () -> Unit,
    onOpenGarage: () -> Unit,
    vm: YouViewModel = hiltViewModel(),
) {
    val privacy by vm.privacy.collectAsStateWithLifecycle()
    val sensors by vm.sensors.collectAsStateWithLifecycle()
    val status by vm.sensorStatus.collectAsStateWithLifecycle()
    val backupStatus by vm.backupStatus.collectAsStateWithLifecycle()
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    var confirmRestore by remember { mutableStateOf<android.net.Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::exportBackup)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        confirmRestore = uri
    }

    Column(Modifier.fillMaxSize()) {
        BackTopBar("Pengaturan", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionTitle("Akun")
            NavRow("Profil dan parameter atlet", profile.name.ifBlank { "Nama, usia, jenis sepeda, zona" }, onOpenProfile)
            NavRow("Garasi sepeda", "Komponen, jarak tempuh, dan jadwal servis", onOpenGarage)

            SectionTitle("Privasi")
            NavRow(
                "Privacy Zone",
                if (privacy.enabled && privacy.center != null) "Aktif, radius ${privacy.radiusM.toInt()} m" else "Nonaktif",
                onOpenPrivacy,
            )

            SectionTitle("Tampilan")
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { m ->
                    FilterChip(selected = themeMode == m, onClick = { vm.setThemeMode(m) }, label = { Text(m.label) })
                }
            }
            NavRow("Peta", "OSM, satelit, Mapbox dengan token sendiri", onOpenMaps)

            SectionTitle("Saat merekam")
            NavRow("Suara & peringatan rute", "Voice over dan getar saat keluar rute", onOpenVoice)
            NavRow("Minum & asupan energi", "Pengingat minum dan makan, saran dari AI", onOpenFuel)
            val connected = sensors.count { status[it.address]?.connected == true }
            NavRow(
                "Perangkat & sensor",
                if (sensors.isEmpty()) "Belum ada sensor" else "${sensors.size} tersimpan, $connected tersambung",
                onOpenDevices,
            )

            SectionTitle("AI")
            NavRow("Penyedia AI & API key", "Kunci milikmu sendiri, terenkripsi di perangkat", onOpenKeys)

            SectionTitle("Backup & restore")
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Backup menyimpan aktivitas, rute, profil, dan daftar sensor ke satu file JSON. API key tidak ikut tersimpan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextMuted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { exportLauncher.launch("strideway-backup-${LocalDate.now()}.json") }) { Text("Buat backup") }
                    OutlinedButton(onClick = { restoreLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
                        Text("Restore")
                    }
                }
                when (val b = backupStatus) {
                    BackupStatus.Working -> Text("Memproses...", color = Palette.TextMuted)
                    is BackupStatus.Done -> Text(b.message, color = Palette.Success)
                    is BackupStatus.Failed -> Text(b.message, color = Palette.Error)
                    BackupStatus.Idle -> Unit
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    confirmRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text("Ganti data dengan backup?") },
            text = { Text("Semua aktivitas dan rute di ponsel ini akan dihapus lalu diganti isi file backup. Tindakan ini tidak bisa dibatalkan.") },
            confirmButton = {
                TextButton(onClick = { confirmRestore = null; vm.restoreBackup(uri) }) { Text("Ganti data", color = Palette.Error) }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("Batal") } },
        )
    }
}

@Composable
internal fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = Palette.TextMuted)
    }
    HorizontalDivider(color = Palette.Outline, modifier = Modifier.padding(horizontal = 16.dp))
}
