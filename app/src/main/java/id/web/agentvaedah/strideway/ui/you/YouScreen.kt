package id.web.agentvaedah.strideway.ui.you

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.web.agentvaedah.strideway.core.model.Gender
import id.web.agentvaedah.strideway.core.physio.Zone
import id.web.agentvaedah.strideway.core.physio.Zones
import id.web.agentvaedah.strideway.ui.components.LoadingState
import id.web.agentvaedah.strideway.ui.components.SectionTitle
import id.web.agentvaedah.strideway.ui.theme.Palette
import java.time.LocalDate

@Composable
fun YouScreen(
    onOpenDevices: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenKeys: () -> Unit,
    vm: YouViewModel = hiltViewModel(),
) {
    val form by vm.form.collectAsStateWithLifecycle()
    val athlete by vm.athlete.collectAsStateWithLifecycle()
    val profileSaved by vm.profileSaved.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val privacy by vm.privacy.collectAsStateWithLifecycle()
    val sensors by vm.sensors.collectAsStateWithLifecycle()
    val status by vm.sensorStatus.collectAsStateWithLifecycle()
    val backupStatus by vm.backupStatus.collectAsStateWithLifecycle()
    var confirmRestore by remember { mutableStateOf<android.net.Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::exportBackup)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        confirmRestore = uri
    }

    val f = form
    if (f == null || athlete == null) {
        LoadingState("Memuat profil")
        return
    }
    val errors = f.errors()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("You", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(16.dp))
        if (!profileSaved) {
            Text(
                "Isi data di bawah agar kalori, zona, dan skor kebugaran dihitung dari tubuhmu, bukan angka bawaan.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Warning,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        SectionTitle("Parameter atlet")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(f.gender == Gender.MALE, { vm.edit { it.copy(gender = Gender.MALE) } }, { Text("Pria") })
                FilterChip(f.gender == Gender.FEMALE, { vm.edit { it.copy(gender = Gender.FEMALE) } }, { Text("Wanita") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Tinggi (cm)", f.height, errors["height"], Modifier.weight(1f)) { v -> vm.edit { it.copy(height = v) } }
                NumberField("Berat (kg)", f.weight, errors["weight"], Modifier.weight(1f), decimal = true) { v -> vm.edit { it.copy(weight = v) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Usia", f.age, errors["age"], Modifier.weight(1f)) { v -> vm.edit { it.copy(age = v) } }
                NumberField("FTP (W)", f.ftp, errors["ftp"], Modifier.weight(1f)) { v -> vm.edit { it.copy(ftp = v) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("HR max", f.hrMax, errors["hrMax"], Modifier.weight(1f)) { v -> vm.edit { it.copy(hrMax = v) } }
                NumberField("HR istirahat", f.hrRest, errors["hrRest"], Modifier.weight(1f)) { v -> vm.edit { it.copy(hrRest = v) } }
            }
            NumberField("Berat sepeda (kg)", f.bikeWeight, errors["bikeWeight"], Modifier.fillMaxWidth(), decimal = true) { v ->
                vm.edit { it.copy(bikeWeight = v) }
            }
            Text(
                "Belum tahu HR max? Perkiraan kasar 208 - 0,7 x usia = ${208 - (0.7 * (f.age.toIntOrNull() ?: 30)).toInt()} bpm. Hasil tes lapangan lebih akurat.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextMuted,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = vm::save, enabled = errors.isEmpty()) { Text("Simpan profil") }
                if (saved) Text("  Tersimpan", color = Palette.Success, style = MaterialTheme.typography.bodyMedium)
                if (errors.isNotEmpty()) Text("  Periksa isian yang merah", color = Palette.Error, style = MaterialTheme.typography.bodySmall)
            }
        }

        val a = f.toAthlete() ?: athlete!!
        SectionTitle("Zona detak jantung")
        Text(
            "Metode Karvonen dari HR istirahat ${a.hrRest} dan HR max ${a.hrMax}.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextMuted,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        ZoneTable(Zones.heartRate(a.hrMax, a.hrRest), "bpm", colored = true)

        SectionTitle("Zona power (Coggan)")
        Text("Dari FTP ${a.ftpWatts} W.", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, modifier = Modifier.padding(horizontal = 16.dp))
        ZoneTable(Zones.power(a.ftpWatts), "W", colored = false)

        SectionTitle("Perangkat & pengaturan")
        val connected = sensors.count { status[it.address]?.connected == true }
        NavRow("Perangkat BLE", if (sensors.isEmpty()) "Belum ada sensor" else "${sensors.size} tersimpan, $connected tersambung", onOpenDevices)
        NavRow(
            "Privacy Zone",
            if (privacy.enabled && privacy.center != null) "Aktif, radius ${privacy.radiusM.toInt()} m" else "Nonaktif",
            onOpenPrivacy,
        )
        NavRow("API key AI Coach", "Kunci milikmu sendiri, terenkripsi di perangkat", onOpenKeys)

        SectionTitle("Backup & restore")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Backup menyimpan aktivitas, rute, profil, dan daftar sensor ke satu file JSON. API key tidak ikut tersimpan.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { exportLauncher.launch("strideway-backup-${LocalDate.now()}.json") }) { Text("Buat backup") }
                OutlinedButton(onClick = { restoreLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Text("Restore") }
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
private fun NumberField(
    label: String,
    value: String,
    error: String?,
    modifier: Modifier,
    decimal: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }.take(6)) },
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = { error?.let { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun ZoneTable(zones: List<Zone>, unit: String, colored: Boolean) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        zones.forEach { z ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(if (colored) Palette.hrZone(z.index) else Palette.TextMuted, MaterialTheme.shapes.extraSmall),
                )
                Spacer(Modifier.width(10.dp))
                Text("Z${z.index}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(32.dp))
                Text(z.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                val range = if (z.high == Int.MAX_VALUE) "> ${z.low - 1}" else "${z.low} - ${z.high}"
                Text("$range $unit", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
            }
        }
    }
}

@Composable
private fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
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
