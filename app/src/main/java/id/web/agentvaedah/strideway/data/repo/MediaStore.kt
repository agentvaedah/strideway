package id.web.agentvaedah.strideway.data.repo

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import id.web.agentvaedah.strideway.data.db.MediaDao
import id.web.agentvaedah.strideway.data.db.MediaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Activity photos, copied into app storage so they survive the original being deleted from the gallery.
 * Files live under filesDir/media/<activityId>/; the database keeps the relative path.
 */
@Singleton
class MediaStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: MediaDao,
) {
    fun forActivity(activityId: Long): Flow<List<MediaEntity>> = dao.observeFor(activityId)

    fun file(m: MediaEntity): File = File(context.filesDir, m.path)

    /** Returns how many of [uris] were copied; unreadable ones are skipped. */
    suspend fun add(activityId: Long, uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "media/$activityId").apply { mkdirs() }
        var copied = 0
        for (uri in uris) {
            val name = "${UUID.randomUUID()}.jpg"
            val ok = runCatching {
                context.contentResolver.openInputStream(uri)!!.use { input ->
                    File(dir, name).outputStream().use { input.copyTo(it) }
                }
            }.isSuccess
            if (ok) {
                dao.insert(MediaEntity(activityId = activityId, path = "media/$activityId/$name", addedMs = System.currentTimeMillis()))
                copied++
            }
        }
        copied
    }

    /** Copies one picked image and returns its file, or null if it could not be read. */
    suspend fun addOne(activityId: Long, uri: Uri): File? = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "media/$activityId").apply { mkdirs() }
        val name = "${UUID.randomUUID()}.jpg"
        val file = File(dir, name)
        val ok = runCatching {
            context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
        }.isSuccess
        if (!ok) return@withContext null
        dao.insert(MediaEntity(activityId = activityId, path = "media/$activityId/$name", addedMs = System.currentTimeMillis()))
        file
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        dao.get(id)?.let { file(it).delete() }
        dao.delete(id)
    }

    /** Rows go with the activity by cascade; this removes the files. */
    suspend fun deleteAll(activityId: Long) = withContext(Dispatchers.IO) {
        File(context.filesDir, "media/$activityId").deleteRecursively()
    }
}
