package id.web.agentvaedah.strideway.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import id.web.agentvaedah.strideway.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun initials(name: String): String =
    name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "S" }

/** Round profile picture with a Signal ring; initials on a dark disc until a photo is set. */
@Composable
fun Avatar(photoPath: String?, name: String, size: Dp, modifier: Modifier = Modifier, ring: Boolean = true) {
    var image by remember(photoPath) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(photoPath) {
        image = photoPath?.let { path -> withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path)?.asImageBitmap() } }
    }
    Box(
        modifier
            .size(size)
            .then(if (ring) Modifier.border(size * 0.04f, Palette.Accent, CircleShape) else Modifier)
            .clip(CircleShape)
            .background(Palette.SurfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        val img = image
        if (img != null) {
            Image(img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(initials(name), color = Palette.Text, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.34f).sp)
        }
    }
}
