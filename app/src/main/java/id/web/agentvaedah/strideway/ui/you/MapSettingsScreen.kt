package id.web.agentvaedah.strideway.ui.you

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.web.agentvaedah.strideway.data.prefs.MapBase
import id.web.agentvaedah.strideway.data.prefs.SettingsRepository
import id.web.agentvaedah.strideway.ui.components.BackTopBar
import id.web.agentvaedah.strideway.ui.theme.Palette
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapSettingsViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    val base: StateFlow<MapBase> = settings.mapBase.stateIn(viewModelScope, SharingStarted.Eagerly, MapBase.STANDARD)
    val token: StateFlow<String?> = settings.mapboxToken.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setBase(b: MapBase) { viewModelScope.launch { settings.setMapBase(b) } }

    fun saveToken(t: String?) { viewModelScope.launch { settings.setMapboxToken(t) } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MapSettingsScreen(onBack: () -> Unit, vm: MapSettingsViewModel = hiltViewModel()) {
    val base by vm.base.collectAsStateWithLifecycle()
    val token by vm.token.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val hasToken = !token.isNullOrBlank()

    Column(Modifier.fillMaxSize()) {
        BackTopBar("Peta", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Peta dasar", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MapBase.entries.forEach { b ->
                    FilterChip(
                        selected = base == b,
                        onClick = { vm.setBase(b) },
                        enabled = !b.needsMapbox || hasToken,
                        label = { Text(b.label) },
                    )
                }
            }
            Text(
                "Standar memakai OpenFreeMap dan OSM memakai tile OpenStreetMap resmi; keduanya gratis. " +
                    "Satelit dan Hybrid dari Esri. Pilihan ini juga dipakai peta saat merekam.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextMuted,
            )

            Text("Mapbox", style = MaterialTheme.typography.titleMedium)
            Text(
                "Mapbox butuh access token dari akun Mapbox milikmu (gratis sampai kuota bulanan, lalu berbayar ke akunmu). " +
                    "Buat token publik di account.mapbox.com, berawalan pk.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextMuted,
            )
            Text(
                if (hasToken) "Token tersimpan" else "Belum ada token. Pilihan Mapbox dinonaktifkan.",
                style = MaterialTheme.typography.labelLarge,
                color = if (hasToken) Palette.Success else Palette.Warning,
            )
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.trim() },
                label = { Text(if (hasToken) "Ganti token Mapbox" else "Token Mapbox") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                isError = input.isNotEmpty() && !input.startsWith("pk."),
                supportingText = { if (input.isNotEmpty() && !input.startsWith("pk.")) Text("Token publik Mapbox berawalan pk.") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.saveToken(input); input = "" }, enabled = input.startsWith("pk.")) { Text("Simpan token") }
                if (hasToken) {
                    TextButton(onClick = {
                        vm.saveToken(null)
                        if (base.needsMapbox) vm.setBase(MapBase.STANDARD)
                    }) { Text("Hapus token", color = Palette.Error) }
                }
            }
        }
    }
}
