package id.web.agentvaedah.strideway.ui.activity

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import id.web.agentvaedah.strideway.data.db.MediaEntity
import id.web.agentvaedah.strideway.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Horizontal row of the activity's photos, decoded small so a dozen full-size photos do not exhaust memory. */
@Composable
fun MediaStrip(photos: List<Pair<MediaEntity, File>>, onDelete: (Long) -> Unit, onOpen: (File) -> Unit) {
    if (photos.isEmpty()) return
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(photos, key = { it.first.id }) { (m, file) ->
            Box(
                Modifier
                    .size(140.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(Palette.SurfaceHigh)
                    .clickable(role = Role.Button, onClickLabel = "Bagikan dengan watermark") { onOpen(file) },
            ) {
                Thumbnail(file, Modifier.size(140.dp))
                IconButton(
                    onClick = { onDelete(m.id) },
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(32.dp).background(Palette.Bg.copy(alpha = 0.8f), CircleShape),
                ) { Icon(Icons.Outlined.Close, contentDescription = "Hapus foto", modifier = Modifier.size(18.dp)) }
            }
        }
    }
}

@Composable
private fun Thumbnail(file: File, modifier: Modifier) {
    var bmp by remember(file) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(file) { bmp = withContext(Dispatchers.IO) { decodeSmall(file, 360) } }
    bmp?.let { Image(it.asImageBitmap(), contentDescription = "Foto aktivitas", contentScale = ContentScale.Crop, modifier = modifier) }
}

private fun decodeSmall(file: File, target: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) sample *= 2
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
}
