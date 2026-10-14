package id.web.agentvaedah.strideway.data.files

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import id.web.agentvaedah.strideway.R
import id.web.agentvaedah.strideway.core.format.Format
import id.web.agentvaedah.strideway.core.model.LatLon
import id.web.agentvaedah.strideway.data.db.ActivityEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos
import kotlin.math.min

/** Watermark layouts, after the share cards riders know from Strava. */
enum class ShareTemplate(val label: String) {
    BOTTOM_STATS("Statistik bawah"),
    ROUTE_STATS("Rute + statistik"),
    ROUTE_ONLY("Tanpa foto"),
    MINIMAL("Minimal"),
}

enum class ShareFormat(val label: String, val width: Int, val height: Int) {
    FEED("Feed 4:5", 1080, 1350),
    STORY("Story 9:16", 1080, 1920),
}

/** One line of the stats block; [unit] is drawn smaller next to [value]. */
data class ShareStat(val label: String, val value: String, val unit: String)

/**
 * Renders a shareable image: the rider's photo (or none), the route as recorded after privacy trimming, the
 * ride's numbers and the Strideway wordmark. White on a dark scrim keeps everything readable on bright photos,
 * as BRAND.md asks for logos over photos.
 */
@Singleton
class ShareComposer @Inject constructor(@ApplicationContext private val context: Context) {
    private val signal = 0xFFFF4F00.toInt()
    private val ink = 0xFF0A0A0B.toInt()
    private val brand: Typeface? by lazy { ResourcesCompat.getFont(context, R.font.strideway_sans_bold_italic) }
    private val body: Typeface? by lazy { ResourcesCompat.getFont(context, R.font.outfit_medium) }

    fun stats(a: ActivityEntity): List<ShareStat> = buildList {
        add(ShareStat("Distance", Format.km(a.distanceM).replace('.', ','), "km"))
        add(ShareStat("Elev Gain", Format.integer(a.elevationGainM), "m"))
        add(ShareStat("Time", time(a.movingMs), ""))
        a.avgPower?.let { add(ShareStat("Avg Power", "$it", "W")) }
        add(ShareStat("Speed", String.format(Locale.US, "%.1f", a.avgSpeedKmh).replace('.', ','), "km/h"))
    }

    private fun time(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        return if (h > 0) "${h}h ${m}m" else "${m}m ${s % 60}s"
    }

    suspend fun render(
        a: ActivityEntity,
        path: List<LatLon>,
        photo: File?,
        template: ShareTemplate,
        format: ShareFormat,
    ): Bitmap = withContext(Dispatchers.Default) {
        val w = format.width
        val h = format.height
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val transparent = template == ShareTemplate.ROUTE_ONLY
        if (!transparent) {
            val src = photo?.let { decodeFit(it, w, h) }
            if (src != null) drawCover(c, src, w, h) else c.drawColor(ink)
        }
        when (template) {
            ShareTemplate.BOTTOM_STATS -> {
                scrim(c, w, h, fromY = h * 0.55f)
                drawStatsRow(c, stats(a), y = h - 210f, w = w)
                drawWordmark(c, x = 60f, y = h - 92f, height = 48f)
            }
            ShareTemplate.ROUTE_STATS -> {
                scrim(c, w, h, fromY = h * 0.45f)
                // The stats column sits high on the photo, so it gets its own dark band to stay readable.
                c.drawRect(w * 0.62f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
                    shader = LinearGradient(w * 0.55f, 0f, w.toFloat(), 0f, 0x00000000, 0xB3000000.toInt(), Shader.TileMode.CLAMP)
                })
                val box = floatArrayOf(60f, h * 0.12f, w * 0.62f, h * 0.62f)
                drawRoute(c, path, box, stroke = 12f)
                drawStatsColumn(c, stats(a), x = w * 0.66f, top = h * 0.16f)
                drawTitle(c, a.name, x = 60f, y = h - 170f, maxW = w - 120f)
                drawWordmark(c, x = 60f, y = h - 92f, height = 48f)
            }
            ShareTemplate.ROUTE_ONLY -> {
                val box = floatArrayOf(90f, h * 0.10f, w - 90f, h * 0.55f)
                drawRoute(c, path, box, stroke = 16f)
                drawStatsColumn(c, stats(a), x = 90f, top = h * 0.60f, big = true)
                drawWordmark(c, x = 90f, y = h - 120f, height = 56f)
            }
            ShareTemplate.MINIMAL -> {
                scrim(c, w, h, fromY = h * 0.7f)
                val p = text(brand, 160f, Color.WHITE)
                val value = Format.km(a.distanceM).replace('.', ',')
                c.drawText(value, 60f, h - 140f, p)
                val unitX = 60f + p.measureText(value) + 16f
                c.drawText("km", unitX, h - 140f, text(body, 52f, Color.WHITE))
                drawIcon(c, x = w - 60f - 96f, y = h - 230f, size = 96f)
            }
        }
        bmp
    }

    /** Writes [bmp] to the share cache and returns a content Uri other apps can read. */
    suspend fun cacheForShare(bmp: Bitmap, template: ShareTemplate): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val png = template == ShareTemplate.ROUTE_ONLY
        val f = File(dir, "strideway-${System.currentTimeMillis()}.${if (png) "png" else "jpg"}")
        f.outputStream().use { bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 92, it) }
        FileProvider.getUriForFile(context, "${context.packageName}.share", f)
    }

    /** Saves to Pictures/Strideway through MediaStore; no storage permission needed on Android 10+. */
    suspend fun saveToGallery(bmp: Bitmap, template: ShareTemplate): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val png = template == ShareTemplate.ROUTE_ONLY
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "strideway-${System.currentTimeMillis()}.${if (png) "png" else "jpg"}")
                put(MediaStore.Images.Media.MIME_TYPE, if (png) "image/png" else "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Strideway")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("MediaStore menolak file")
            context.contentResolver.openOutputStream(uri)!!.use { bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 92, it) }
            uri
        }
    }

    private fun decodeFit(f: File, w: Int, h: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= w && bounds.outHeight / (sample * 2) >= h) sample *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun drawCover(c: Canvas, src: Bitmap, w: Int, h: Int) {
        val scale = maxOf(w.toFloat() / src.width, h.toFloat() / src.height)
        val m = Matrix().apply {
            postScale(scale, scale)
            postTranslate((w - src.width * scale) / 2f, (h - src.height * scale) / 2f)
        }
        c.drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG))
    }

    private fun scrim(c: Canvas, w: Int, h: Int, fromY: Float) {
        val p = Paint().apply { shader = LinearGradient(0f, fromY, 0f, h.toFloat(), 0x00000000, 0xD9000000.toInt(), Shader.TileMode.CLAMP) }
        c.drawRect(0f, fromY, w.toFloat(), h.toFloat(), p)
    }

    private fun text(face: Typeface?, size: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = face
        textSize = size
        this.color = color
        setShadowLayer(6f, 0f, 2f, 0x66000000)
    }

    private fun drawStatsRow(c: Canvas, stats: List<ShareStat>, y: Float, w: Int) {
        val col = (w - 120f) / stats.size
        stats.forEachIndexed { i, s ->
            val x = 60f + i * col
            c.drawText(s.label, x, y - 66f, text(body, 30f, 0xFFD9D9DD.toInt()))
            val vp = text(brand, if (stats.size > 4) 56f else 64f, Color.WHITE)
            c.drawText(s.value, x, y, vp)
            if (s.unit.isNotEmpty()) c.drawText(s.unit, x + vp.measureText(s.value) + 6f, y, text(body, 28f, Color.WHITE))
        }
    }

    private fun drawStatsColumn(c: Canvas, stats: List<ShareStat>, x: Float, top: Float, big: Boolean = false) {
        var y = top
        val step = if (big) 170f else 150f
        stats.forEach { s ->
            c.drawText(s.label, x, y, text(body, if (big) 34f else 30f, 0xFFD9D9DD.toInt()))
            val vp = text(brand, if (big) 96f else 72f, Color.WHITE)
            c.drawText(s.value, x, y + (if (big) 92f else 74f), vp)
            if (s.unit.isNotEmpty()) c.drawText(s.unit, x + vp.measureText(s.value) + 8f, y + (if (big) 92f else 74f), text(body, if (big) 38f else 30f, Color.WHITE))
            y += step
        }
    }

    private fun drawTitle(c: Canvas, title: String, x: Float, y: Float, maxW: Float) {
        val p = text(body, 44f, Color.WHITE)
        var t = title
        while (t.length > 3 && p.measureText(t) > maxW) t = t.dropLast(2)
        if (t != title) t = t.trimEnd() + "..."
        c.drawText(t, x, y, p)
    }

    /** Route fitted into [box] (left, top, right, bottom), a dark edge under a Signal line, start and end dots. */
    private fun drawRoute(c: Canvas, path: List<LatLon>, box: FloatArray, stroke: Float) {
        if (path.size < 2) return
        val midLat = path.sumOf { it.lat } / path.size
        val kx = cos(Math.toRadians(midLat))
        val xs = path.map { it.lon * kx }
        val ys = path.map { it.lat }
        val spanX = (xs.max() - xs.min()).coerceAtLeast(1e-9)
        val spanY = (ys.max() - ys.min()).coerceAtLeast(1e-9)
        val bw = box[2] - box[0]
        val bh = box[3] - box[1]
        val scale = min(bw / spanX, bh / spanY)
        val ox = box[0] + (bw - spanX * scale) / 2
        val oy = box[1] + (bh - spanY * scale) / 2
        fun px(i: Int) = (ox + (xs[i] - xs.min()) * scale).toFloat()
        fun py(i: Int) = (oy + (ys.max() - ys[i]) * scale).toFloat()
        val p = Path().apply {
            moveTo(px(0), py(0))
            for (i in 1 until path.size) lineTo(px(i), py(i))
        }
        val base = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        c.drawPath(p, Paint(base).apply { color = 0x99000000.toInt(); strokeWidth = stroke + 8f })
        c.drawPath(p, Paint(base).apply { color = signal; strokeWidth = stroke })
        val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        c.drawCircle(px(0), py(0), stroke * 1.1f, dot.apply { color = Color.WHITE })
        c.drawCircle(px(0), py(0), stroke * 0.6f, Paint(dot).apply { color = 0xFF2FB765.toInt() })
        c.drawCircle(px(path.lastIndex), py(path.lastIndex), stroke * 1.1f, Paint(dot).apply { color = Color.WHITE })
        c.drawCircle(px(path.lastIndex), py(path.lastIndex), stroke * 0.6f, Paint(dot).apply { color = ink })
    }

    private fun drawWordmark(c: Canvas, x: Float, y: Float, height: Float) {
        val d = ContextCompat.getDrawable(context, R.drawable.brand_wordmark_white) ?: return
        val width = height * d.intrinsicWidth / d.intrinsicHeight.coerceAtLeast(1)
        c.drawBitmap(d.toBitmap(width.toInt(), height.toInt()), null, Rect(x.toInt(), (y - height).toInt(), (x + width).toInt(), y.toInt()), Paint(Paint.FILTER_BITMAP_FLAG))
    }

    private fun drawIcon(c: Canvas, x: Float, y: Float, size: Float) {
        val d = ContextCompat.getDrawable(context, R.drawable.brand_icon_white) ?: return
        val wpx = size * d.intrinsicWidth / d.intrinsicHeight.coerceAtLeast(1)
        c.drawBitmap(d.toBitmap(wpx.toInt(), size.toInt()), x, y, Paint(Paint.FILTER_BITMAP_FLAG))
    }
}
