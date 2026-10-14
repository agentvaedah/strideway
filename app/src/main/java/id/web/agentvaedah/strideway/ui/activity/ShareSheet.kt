package id.web.agentvaedah.strideway.ui.activity

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import id.web.agentvaedah.strideway.data.files.ShareFormat
import id.web.agentvaedah.strideway.data.files.ShareTemplate
import id.web.agentvaedah.strideway.data.db.MediaEntity
import id.web.agentvaedah.strideway.ui.theme.Palette
import kotlinx.coroutines.launch
import java.io.File

/**
 * Share an activity as an image: pick a photo (from this activity or the gallery), a template and a format,
 * see the result, then share it or save it to the gallery.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(
    vm: ActivityDetailViewModel,
    photos: List<Pair<MediaEntity, File>>,
    initialPhoto: File?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var template by remember { mutableStateOf(if (photos.isEmpty() && initialPhoto == null) ShareTemplate.ROUTE_ONLY else ShareTemplate.BOTTOM_STATS) }
    var format by remember { mutableStateOf(ShareFormat.FEED) }
    var photo by remember { mutableStateOf(initialPhoto ?: photos.firstOrNull()?.second) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { photo = vm.importSharePhoto(uri) ?: photo }
    }

    LaunchedEffect(template, format, photo) {
        busy = true
        preview = vm.renderShare(template, format, if (template == ShareTemplate.ROUTE_ONLY) null else photo)
        busy = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState())) {
            Text("Bagikan", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .aspectRatio(format.width.toFloat() / format.height)
                    .align(Alignment.CenterHorizontally)
                    .clip(MaterialTheme.shapes.medium)
                    // A checker-free mid grey shows where the transparent template has no background.
                    .background(Palette.SurfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                preview?.let { Image(it.asImageBitmap(), contentDescription = "Pratinjau gambar ${template.label}", modifier = Modifier.fillMaxWidth()) }
                if (busy) CircularProgressIndicator(Modifier.size(32.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text("Template", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShareTemplate.entries.forEach { t -> FilterChip(template == t, { template = t }, { Text(t.label) }) }
            }
            Text("Ukuran", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShareFormat.entries.forEach { f -> FilterChip(format == f, { format = f }, { Text(f.label) }) }
            }
            if (template != ShareTemplate.ROUTE_ONLY) {
                Text("Foto", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    photos.forEachIndexed { i, (_, f) -> FilterChip(photo == f, { photo = f }, { Text("Foto ${i + 1}") }) }
                    FilterChip(photo == null, { photo = null }, { Text("Tanpa foto") })
                    OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Dari galeri") }
                }
            }
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val bmp = preview ?: return@Button
                        scope.launch {
                            val uri = vm.shareUri(bmp, template)
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = if (template == ShareTemplate.ROUTE_ONLY) "image/png" else "image/jpeg"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(send, "Bagikan aktivitas"))
                        }
                    },
                    enabled = preview != null && !busy,
                ) { Text("Bagikan") }
                OutlinedButton(
                    onClick = {
                        val bmp = preview ?: return@OutlinedButton
                        scope.launch { message = vm.saveShare(bmp, template) }
                    },
                    enabled = preview != null && !busy,
                ) { Text("Simpan ke galeri") }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, modifier = Modifier.padding(horizontal = 16.dp)) }
            Text(
                "Jalur mengikuti Privacy Zone dan visibilitas peta aktivitas ini.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextMuted,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
