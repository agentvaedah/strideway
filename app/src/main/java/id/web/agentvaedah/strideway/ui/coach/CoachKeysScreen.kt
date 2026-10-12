package id.web.agentvaedah.strideway.ui.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.web.agentvaedah.strideway.data.remote.llm.LlmProviderId
import id.web.agentvaedah.strideway.ui.components.BackTopBar
import id.web.agentvaedah.strideway.ui.components.LoadingState
import id.web.agentvaedah.strideway.ui.theme.Palette

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoachKeysScreen(onBack: () -> Unit, vm: CoachKeysViewModel = hiltViewModel()) {
    val editing by vm.editing.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    val hasKey by vm.hasKey.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    var keyInput by remember(editing?.provider) { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        BackTopBar("Penyedia AI", onBack)
        val cfg = editing
        if (cfg == null) {
            LoadingState("Memuat pengaturan")
            return@Column
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Key tersimpan terenkripsi AES-256-GCM dengan kunci Android Keystore, tidak ikut backup, dan hanya dikirim ke penyedia yang kamu pilih.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextMuted,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LlmProviderId.entries.forEach { p ->
                    FilterChip(
                        selected = cfg.provider == p,
                        onClick = { vm.select(p) },
                        label = { Text(if (active?.provider == p) "${p.label} (aktif)" else p.label) },
                    )
                }
            }
            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it.trim() },
                label = { Text(if (cfg.provider.keyRequired) "API key" else "API key (opsional)") },
                placeholder = { Text(if (hasKey) "Tersimpan. Isi untuk mengganti" else "Tempel key di sini") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = cfg.model,
                onValueChange = { v -> vm.edit { it.copy(model = v.trim()) } },
                label = { Text("Model") },
                supportingText = { Text("Bawaan: ${cfg.provider.defaultModel}") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = cfg.baseUrl,
                onValueChange = { v -> vm.edit { it.copy(baseUrl = v.trim()) } },
                label = { Text("Base URL") },
                supportingText = {
                    Text(
                        if (cfg.provider == LlmProviderId.OLLAMA) {
                            "Alamat Ollama atau 9Router di jaringanmu, berakhiran /v1"
                        } else {
                            "Bawaan: ${cfg.provider.defaultBaseUrl}"
                        },
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            val urlValid = cfg.baseUrl.startsWith("https://") || cfg.baseUrl.startsWith("http://")
            if (!urlValid) Text("Base URL harus diawali http:// atau https://", color = Palette.Error)
            if (cfg.baseUrl.startsWith("http://") && cfg.provider != LlmProviderId.OLLAMA) {
                Text("Alamat http:// tidak terenkripsi. Key bisa terbaca di jaringan.", color = Palette.Warning, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { vm.save(keyInput); keyInput = "" },
                    enabled = urlValid && cfg.model.isNotBlank() && (hasKey || keyInput.isNotBlank() || !cfg.provider.keyRequired),
                ) { Text("Simpan & pakai") }
                OutlinedButton(
                    onClick = { vm.test(keyInput); keyInput = "" },
                    enabled = urlValid && status != KeyStatus.Testing && (hasKey || keyInput.isNotBlank() || !cfg.provider.keyRequired),
                ) { Text("Tes koneksi") }
            }
            if (hasKey) TextButton(onClick = vm::removeKey) { Text("Hapus key ${cfg.provider.label}", color = Palette.Error) }
            when (val s = status) {
                KeyStatus.Idle -> Unit
                KeyStatus.Saved -> Text("Tersimpan. ${cfg.provider.label} sekarang dipakai AI Coach dan AI Route.", color = Palette.Success)
                KeyStatus.Removed -> Text("Key dihapus dari perangkat.", color = Palette.TextMuted)
                KeyStatus.Testing -> Text("Menghubungi ${cfg.provider.label}...", color = Palette.TextMuted)
                KeyStatus.TestOk -> Text("Koneksi berhasil. Tekan Simpan & pakai untuk menjadikannya aktif.", color = Palette.Success)
                is KeyStatus.TestFailed -> Text(s.reason, color = Palette.Error)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
