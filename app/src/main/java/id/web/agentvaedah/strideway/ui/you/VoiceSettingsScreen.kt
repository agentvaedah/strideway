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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.web.agentvaedah.strideway.data.prefs.SettingsRepository
import id.web.agentvaedah.strideway.data.prefs.TtsProviderId
import id.web.agentvaedah.strideway.data.prefs.VoiceSettings
import id.web.agentvaedah.strideway.data.security.SecureKeyStore
import id.web.agentvaedah.strideway.data.voice.VoicePlayer
import id.web.agentvaedah.strideway.ui.components.BackTopBar
import id.web.agentvaedah.strideway.ui.theme.Palette
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class VoiceSettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val keys: SecureKeyStore,
    private val player: VoicePlayer,
) : ViewModel() {
    val voice: StateFlow<VoiceSettings> = settings.voice.stateIn(viewModelScope, SharingStarted.Eagerly, VoiceSettings())

    val hasKey: StateFlow<Boolean> = settings.voice.flatMapLatest { v ->
        v.provider.keyId?.let { keys.hasKey(it) } ?: flowOf(true)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    fun update(transform: (VoiceSettings) -> VoiceSettings) {
        viewModelScope.launch { settings.saveVoice(transform(voice.value)) }
    }

    fun saveKey(key: String) {
        val id = voice.value.provider.keyId ?: return
        if (key.isBlank()) return
        viewModelScope.launch {
            keys.put(id, key)
            _status.value = "Key tersimpan"
        }
    }

    fun removeKey() {
        val id = voice.value.provider.keyId ?: return
        viewModelScope.launch {
            keys.remove(id)
            _status.value = "Key dihapus"
        }
    }

    fun test() {
        _status.value = "Memutar contoh suara"
        viewModelScope.launch {
            val used = player.test(voice.value)
            _status.value = if (used == voice.value.provider.label) {
                "Diputar dengan $used"
            } else {
                "Suara ${voice.value.provider.label} gagal (key, kuota, atau koneksi). Diputar dengan $used"
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceSettingsScreen(onBack: () -> Unit, vm: VoiceSettingsViewModel = hiltViewModel()) {
    val v by vm.voice.collectAsStateWithLifecycle()
    val hasKey by vm.hasKey.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    var keyInput by remember(v.provider) { mutableStateOf("") }
    var voiceId by remember(v.elevenVoiceId) { mutableStateOf(v.elevenVoiceId) }

    Column(Modifier.fillMaxSize()) {
        BackTopBar("Suara & peringatan", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ToggleRow("Voice over", "Bacakan info latihan saat merekam, juga saat layar mati", v.enabled) { on ->
                vm.update { it.copy(enabled = on) }
            }
            HorizontalDivider(color = Palette.Outline)

            Section("Yang dibacakan")
            ToggleRow("Split tiap kilometer", "Jarak, waktu, dan kecepatan atau pace", v.split) { on -> vm.update { it.copy(split = on) } }
            ToggleRow("Rute", "Keluar rute, kembali ke rute, belokan berikutnya", v.route) { on -> vm.update { it.copy(route = on) } }
            ToggleRow("Jeda dan lanjut", "Termasuk jeda otomatis", v.pause) { on -> vm.update { it.copy(pause = on) } }
            ToggleRow("Zona detak jantung", "Saat masuk zona 4 atau 5, paling sering tiap 2 menit", v.heartRate) { on ->
                vm.update { it.copy(heartRate = on) }
            }
            HorizontalDivider(color = Palette.Outline)

            Section("Peringatan rute")
            ToggleRow("Getar saat keluar rute", "Lebih dari 50 m dari jalur selama 5 detik", v.routeAlerts) { on ->
                vm.update { it.copy(routeAlerts = on) }
            }
            HorizontalDivider(color = Palette.Outline)

            Section("Sumber suara")
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TtsProviderId.entries.forEach { p ->
                        FilterChip(
                            selected = v.provider == p,
                            onClick = { vm.update { it.copy(provider = p) } },
                            label = { Text(p.label) },
                        )
                    }
                }
                Text(
                    when (v.provider) {
                        TtsProviderId.DEVICE -> "Memakai mesin TTS bawaan ponsel. Gratis dan bekerja tanpa internet."
                        TtsProviderId.OPENAI -> "Suara lebih natural, dibayar dari kuota akun OpenAI-mu. Memakai key yang sama dengan AI Coach OpenAI."
                        TtsProviderId.ELEVENLABS -> "Suara paling natural, dibayar dari kuota akun ElevenLabs-mu."
                    } + if (v.provider != TtsProviderId.DEVICE) " Bila gagal atau tanpa sinyal, suara bawaan ponsel dipakai." else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextMuted,
                )
                if (v.provider != TtsProviderId.DEVICE) {
                    Text(
                        if (hasKey) "API key tersimpan" else "Belum ada API key",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (hasKey) Palette.Success else Palette.Warning,
                    )
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text(if (hasKey) "Ganti API key ${v.provider.label}" else "API key ${v.provider.label}") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.saveKey(keyInput); keyInput = "" }, enabled = keyInput.isNotBlank()) { Text("Simpan key") }
                        if (hasKey) TextButton(onClick = vm::removeKey) { Text("Hapus key", color = Palette.Error) }
                    }
                }
                if (v.provider == TtsProviderId.ELEVENLABS) {
                    OutlinedTextField(
                        value = voiceId,
                        onValueChange = { voiceId = it },
                        label = { Text("Voice ID ElevenLabs") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { vm.update { it.copy(elevenVoiceId = voiceId) } }, enabled = voiceId != v.elevenVoiceId) {
                        Text("Simpan voice ID")
                    }
                }
                OutlinedButton(onClick = vm::test) { Text("Tes suara") }
                status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted) }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
