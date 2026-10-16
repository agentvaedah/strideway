package id.web.agentvaedah.strideway.data.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The rider's profile picture: copied out of the gallery, turned upright, cropped square from the centre and
 * stored at 512 px inside the app, so it survives the original being deleted. Not part of the JSON backup.
 */
@Singleton
class ProfilePhoto @Inject constructor(@ApplicationContext private val context: Context) {
    private val dir: File get() = File(context.filesDir, "profile")

    /** Returns the stored file, or null when the picture could not be read. */
    suspend fun save(uri: Uri): File? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIZE) sample *= 2
            val src = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
                ?: return@runCatching null
            val rotation = resolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
            val side = min(src.width, src.height)
            val m = Matrix().apply {
                postRotate(rotation.toFloat())
                val s = SIZE.toFloat() / side
                postScale(s, s)
            }
            val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side, m, true)
            dir.mkdirs()
            // Unique name per save, so Compose reloads the picture instead of showing the cached one.
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, "avatar-${System.currentTimeMillis()}.jpg")
            out.outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            out
        }.getOrNull()
    }

    fun delete() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private companion object { const val SIZE = 512 }
}
