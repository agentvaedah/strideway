package id.web.agentvaedah.strideway.data.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import id.web.agentvaedah.strideway.core.model.Track
import id.web.agentvaedah.strideway.core.track.TrackFormats
import id.web.agentvaedah.strideway.core.track.TrackParseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackFileIo @Inject constructor(@ApplicationContext private val context: Context) {
    suspend fun read(uri: Uri): Result<Track> = withContext(Dispatchers.IO) {
        try {
            val name = displayName(uri) ?: "rute"
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext Result.failure(IOException("File tidak bisa dibuka"))
            if (bytes.size > MAX_BYTES) return@withContext Result.failure(IOException("File lebih dari 50 MB"))
            Result.success(TrackFormats.parse(name, bytes))
        } catch (e: TrackParseException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: SecurityException) {
            Result.failure(IOException("Akses ke file ditolak"))
        }
    }

    suspend fun write(uri: Uri, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                ?: return@withContext Result.failure(IOException("File tujuan tidak bisa ditulis"))
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: SecurityException) {
            Result.failure(IOException("Akses ke file ditolak"))
        }
    }

    suspend fun readText(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: return@withContext Result.failure(IOException("File tidak bisa dibuka"))
            Result.success(text)
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: SecurityException) {
            Result.failure(IOException("Akses ke file ditolak"))
        }
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment?.substringAfterLast('/')

    companion object {
        private const val MAX_BYTES = 50 * 1024 * 1024

        /** Safe file name for SAF CreateDocument suggestions. */
        fun fileName(name: String, ext: String): String =
            name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().replace(' ', '_').ifBlank { "strideway" } + "." + ext
    }
}
