package id.web.agentvaedah.strideway.ui.activity

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.web.agentvaedah.strideway.core.share.ShareFormat
import id.web.agentvaedah.strideway.core.share.ShareTemplate
import id.web.agentvaedah.strideway.data.db.MediaEntity
import id.web.agentvaedah.strideway.data.prefs.MapBase
import id.web.agentvaedah.strideway.ui.theme.Palette
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Base maps offered for share images; the rest need a token or add little over these three. */
private val shareBases = listOf(MapBase.STANDARD, MapBase.SATELLITE, MapBase.TOPO)

/**
 * Share an activity as an image: a large preview, a strip of template thumbnails, Feed or Story size, the
 * map background for templates that show one, and the photo. Share sends it to another app; Save puts it
 * in the gallery.
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
    val userBase by vm.mapBase.collectAsState()
    var photo by remember { mutableStateOf(initialPhoto ?: photos.firstOrNull()?.second) }
    var base by remember { mutableStateOf(if (userBase in shareBases) userBase else MapBase.STANDARD) }
    var format by remember { mutableStateOf(ShareFormat.FEED) }
    val request = remember(photo, base) { vm.shareRequest(photo, base) }
    val templates = remember(request) { request?.let { r -> ShareTemplate.entries.filter { vm.shareAvailable(it, r) } }.orEmpty() }
    var template by remember { mutableStateOf(if (photo == null) ShareTemplate.MAP_FULL else ShareTemplate.BOTTOM_STATS) }
    if (templates.isNotEmpty() && template !in templates) template = templates.first()
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val thumbs = remember { mutableStateMapOf<ShareTemplate, Bitmap>() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { photo = vm.importSharePhoto(uri) ?: photo }
    }

    LaunchedEffect(request, template, format) {
        val r = request ?: return@LaunchedEffect
        busy = true
        // A short pause so tapping through thumbnails does not queue a full render for each one.
        delay(150)
        preview = vm.renderShare(r, template, format)
        busy = false
    }
    LaunchedEffect(request) {
        val r = request ?: return@LaunchedEffect
        thumbs.clear()
        for (t in templates) thumbs[t] = vm.renderShare(r, t, ShareFormat.FEED, scale = 0.14f)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding()) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                Text("Bagikan dengan foto", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .heightIn(max = 400.dp)
                        .aspectRatio(format.width.toFloat() / format.height)
                        .clip(MaterialTheme.shapes.large)
                        .previewBackground(template.transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Crossfade(preview, label = "share-preview") { bmp ->
                        if (bmp != null) Image(bmp.asImageBitmap(), contentDescription = "Pratinjau ${template.label}, ${format.label}", modifier = Modifier.fillMaxSize())
                    }
                    if (busy) CircularProgressIndicator(Modifier.size(32.dp))
                }
                Section("Template")
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(templates, key = { it.name }) { t -> TemplateThumb(t, thumbs[t], t == template) { template = t } }
                }
                Section("Ukuran")
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShareFormat.entries.forEach { f -> FilterChip(format == f, { format = f }, { Text(f.label) }) }
                }
                if (template.usesMap) {
                    Section("Latar peta")
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        shareBases.forEach { b -> FilterChip(base == b, { base = b }, { Text(b.label) }) }
                    }
                }
                if (template.usesPhoto) {
                    Section("Foto")
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        photos.forEachIndexed { i, (_, f) -> FilterChip(photo == f, { photo = f }, { Text("Foto ${i + 1}") }) }
                        FilterChip(photo == null, { photo = null }, { Text("Tanpa foto") })
                        OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Dari galeri") }
                    }
                }
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) }
                Text(
                    "Rute mengikuti Privacy Zone dan visibilitas peta aktivitas ini. Latar peta butuh internet saat pertama dibuat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextMuted,
                    modifier = Modifier.padding(16.dp),
                )
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        val bmp = preview ?: return@OutlinedButton
                        scope.launch { message = vm.saveShare(bmp, template) }
                    },
                    enabled = preview != null && !busy,
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text("Simpan ke galeri") }
                Button(
                    onClick = {
                        val bmp = preview ?: return@Button
                        scope.launch {
                            val uri = vm.shareUri(bmp, template)
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = if (template.transparent) "image/png" else "image/jpeg"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(send, "Bagikan aktivitas"))
                        }
                    },
                    enabled = preview != null && !busy,
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text("Bagikan") }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = Palette.TextMuted, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp))
}

@Composable
private fun TemplateThumb(t: ShareTemplate, bmp: Bitmap?, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .width(76.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                this.selected = selected
                contentDescription = "Template ${t.label}"
            }
            .padding(bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .width(68.dp)
                .aspectRatio(ShareFormat.FEED.width.toFloat() / ShareFormat.FEED.height)
                .clip(MaterialTheme.shapes.small)
                .previewBackground(t.transparent)
                .border(2.dp, if (selected) Palette.Accent else Color.Transparent, MaterialTheme.shapes.small),
        ) {
            bmp?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize()) }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            t.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Palette.Text else Palette.TextMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** Transparent cards sit on a dark checkerboard so their white text stays visible in the preview. */
@Composable
private fun Modifier.previewBackground(transparent: Boolean): Modifier =
    if (!transparent) background(Palette.SurfaceHigh)
    else background(Color(0xFF3A3A3F)).drawBehind {
        val cell = 12.dp.toPx()
        var y = 0f
        var row = 0
        while (y < size.height) {
            var x = if (row % 2 == 0) 0f else cell
            while (x < size.width) {
                drawRect(Color(0xFF4A4A50), Offset(x, y), Size(cell, cell))
                x += cell * 2
            }
            y += cell
            row++
        }
    }
