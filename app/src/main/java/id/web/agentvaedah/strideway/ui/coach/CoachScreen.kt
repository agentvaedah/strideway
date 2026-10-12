package id.web.agentvaedah.strideway.ui.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.web.agentvaedah.strideway.data.db.ChatMessageEntity
import id.web.agentvaedah.strideway.data.db.ChatRole
import id.web.agentvaedah.strideway.ui.components.EmptyState
import id.web.agentvaedah.strideway.ui.components.LoadingState
import id.web.agentvaedah.strideway.ui.theme.Palette

private val starters = listOf(
    "Paha sering kram di km 60, apa penyebab dan cara mencegahnya?",
    "Bagaimana pacing untuk half marathon target 2 jam?",
    "Berapa banyak minum dan karbohidrat per jam untuk ride 100 km di siang hari?",
    "Lutut depan nyeri setelah ride panjang, apakah sadel terlalu rendah?",
)

@Composable
fun CoachScreen(onOpenKeys: () -> Unit, vm: CoachViewModel = hiltViewModel()) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    val cfg by vm.llmSettings.collectAsStateWithLifecycle()
    val hasKey by vm.hasKey.collectAsStateWithLifecycle()
    val sending by vm.sending.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val needsKey = cfg.provider.keyRequired && !hasKey

    LaunchedEffect(messages?.size, sending) {
        val count = (messages?.size ?: 0) + if (sending) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("AI Coach", style = MaterialTheme.typography.headlineLarge)
                Text("${cfg.provider.label} · ${cfg.model}", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
            }
            if (!messages.isNullOrEmpty()) TextButton(onClick = { confirmClear = true }) { Text("Hapus chat") }
            IconButton(onClick = onOpenKeys) { Icon(Icons.Outlined.VpnKey, contentDescription = "Atur API key dan model") }
        }
        HorizontalDivider(color = Palette.Outline, modifier = Modifier.padding(top = 8.dp))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val list = messages
            when {
                list == null -> LoadingState("Memuat percakapan")
                needsKey && list.isEmpty() -> EmptyState(
                    title = "Pakai API key milikmu sendiri",
                    body = "AI Coach memanggil penyedia AI langsung dari ponsel memakai key-mu (OpenAI, Gemini, Groq, Anthropic, atau Ollama lokal). " +
                        "Key dienkripsi di Android Keystore dan tidak pernah dikirim ke server Strideway.",
                    actionLabel = "Masukkan API key",
                    onAction = onOpenKeys,
                )
                list.isEmpty() -> Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Tanyakan soal biomekanika, kram, bike fitting, pacing lari, pemakaian brifter, nutrisi, dan hidrasi. " +
                            "Coach membaca profil dan 5 aktivitas terakhirmu.",
                        color = Palette.TextMuted,
                    )
                    starters.forEach { q ->
                        OutlinedButton(onClick = { vm.send(q) }, enabled = !sending, modifier = Modifier.fillMaxWidth()) {
                            Text(q, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(list, key = { it.id }) { Bubble(it, onOpenKeys) }
                    if (sending) {
                        item {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text("  Coach sedang menulis jawaban", color = Palette.TextMuted)
                            }
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.take(4_000) },
                placeholder = { Text("Tulis pertanyaan") },
                modifier = Modifier.weight(1f),
                maxLines = 5,
                enabled = !needsKey,
            )
            Spacer(Modifier.size(8.dp))
            FilledIconButton(
                onClick = { vm.send(input); input = "" },
                enabled = input.isNotBlank() && !sending && !needsKey,
                modifier = Modifier.size(52.dp),
            ) { Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Kirim") }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Hapus seluruh percakapan?") },
            text = { Text("Riwayat chat di ponsel ini dihapus. Coach tidak lagi mengingat pertanyaan sebelumnya.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; vm.clear() }) { Text("Hapus", color = Palette.Error) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Batal") } },
        )
    }
}

@Composable
private fun Bubble(m: ChatMessageEntity, onOpenKeys: () -> Unit) {
    val user = m.role == ChatRole.USER
    val error = m.role == ChatRole.ERROR
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = if (user) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            Modifier
                .widthIn(max = 340.dp)
                .background(
                    when {
                        user -> Palette.SurfaceHigh
                        error -> Palette.Error.copy(alpha = 0.12f)
                        else -> Palette.Surface
                    },
                    MaterialTheme.shapes.large,
                )
                .padding(12.dp),
        ) {
            SelectionContainer {
                Text(m.content, style = MaterialTheme.typography.bodyLarge, color = if (error) Palette.Error else Palette.Text)
            }
            if (!user && m.provider != null) {
                Text(m.provider, style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted, modifier = Modifier.padding(top = 4.dp))
            }
            if (error) TextButton(onClick = onOpenKeys) { Text("Periksa pengaturan AI") }
        }
    }
}
