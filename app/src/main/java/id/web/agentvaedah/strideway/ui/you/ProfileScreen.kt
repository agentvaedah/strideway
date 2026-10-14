package id.web.agentvaedah.strideway.ui.you

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.web.agentvaedah.strideway.core.model.Gender
import id.web.agentvaedah.strideway.data.prefs.BikeType
import id.web.agentvaedah.strideway.core.physio.Zone
import id.web.agentvaedah.strideway.core.physio.Zones
import id.web.agentvaedah.strideway.ui.components.BackTopBar
import id.web.agentvaedah.strideway.ui.components.LoadingState
import id.web.agentvaedah.strideway.ui.components.SectionTitle
import id.web.agentvaedah.strideway.ui.theme.Palette

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(onBack: () -> Unit, onOpenGarage: () -> Unit, vm: YouViewModel = hiltViewModel()) {
    val form by vm.form.collectAsStateWithLifecycle()
    val athlete by vm.athlete.collectAsStateWithLifecycle()
    val profileSaved by vm.profileSaved.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()

    val f = form
    if (f == null || athlete == null) {
        LoadingState("Memuat profil")
        return
    }
    val errors = f.errors()

    Column(Modifier.fillMaxSize()) {
        BackTopBar("Profil", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            if (!profileSaved) {
                Text(
                    "Isi data di bawah agar kalori, zona, dan skor kebugaran dihitung dari tubuhmu, bukan angka bawaan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Warning,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            SectionTitle("Identitas")
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = f.name,
                    onValueChange = { v -> vm.edit { it.copy(name = v.take(40)) } },
                    label = { Text("Nama") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Jenis sepeda", style = MaterialTheme.typography.labelLarge, color = Palette.TextMuted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BikeType.entries.forEach { b ->
                        FilterChip(f.bikeType == b, { vm.edit { it.copy(bikeType = b) } }, { Text(b.label) })
                    }
                }
                if (f.bikeType != BikeType.NONE) {
                    OutlinedTextField(
                        value = f.bikeName,
                        onValueChange = { v -> vm.edit { it.copy(bikeName = v.take(40)) } },
                        label = { Text("Nama sepeda (opsional)") },
                        placeholder = { Text("Contoh: Polygon Strattos S5") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (f.bikeType != BikeType.NONE) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    OutlinedButton(onClick = onOpenGarage) { Text("Garasi sepeda: komponen & servis") }
                }
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

            Spacer(Modifier.height(32.dp))
        }
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
