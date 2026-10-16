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
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
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
import id.web.agentvaedah.strideway.core.model.LatLon
import id.web.agentvaedah.strideway.core.share.Align
import id.web.agentvaedah.strideway.core.share.Box
import id.web.agentvaedah.strideway.core.share.ShareElement
import id.web.agentvaedah.strideway.core.share.ShareFont
import id.web.agentvaedah.strideway.core.share.ShareFormat
import id.web.agentvaedah.strideway.core.share.ShareInput
import id.web.agentvaedah.strideway.core.share.ShareLayout
import id.web.agentvaedah.strideway.core.share.ShareRecord
import id.web.agentvaedah.strideway.core.share.ShareStat
import id.web.agentvaedah.strideway.core.share.ShareTemplate
import id.web.agentvaedah.strideway.core.share.TextMeasure
import id.web.agentvaedah.strideway.core.share.Tone
import id.web.agentvaedah.strideway.data.db.ActivityEntity
import id.web.agentvaedah.strideway.data.prefs.MapBase
import id.web.agentvaedah.strideway.ui.components.MapStyles
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What a share image is built from, besides the template and size. */
data class ShareRequest(
    val activity: ActivityEntity,
    /** Route after Privacy Zone and map-visibility trimming, so a shared image never reveals home. */
    val path: List<LatLon>,
    val elevation: List<Float>,
    val photo: File?,
    val base: MapBase,
    val mapboxToken: String?,
    val record: ShareRecord?,
)

/**
 * Renders a shareable image. [ShareLayout] places every element so nothing overlaps; this class only paints
 * them: the rider's photo, a real base-map picture under the route on map templates, the numbers in Strideway
 * Sans and the white wordmark. White on dark shades and panels keeps everything readable on bright photos,
 * as BRAND.md asks for logos over photos.
 */
@Singleton
class ShareComposer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val maps: MapSnapshots,
) {
    private val signal = 0xFFFF4F00.toInt()
    private val amber = 0xFFFFB000.toInt()
    private val ink = 0xFF0A0A0B.toInt()
    private val brand: Typeface? by lazy { ResourcesCompat.getFont(context, R.font.strideway_sans_bold_italic) }
    private val body: Typeface? by lazy { ResourcesCompat.getFont(context, R.font.outfit_medium) }

    private val measure = TextMeasure { text, size, font ->
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = if (font == ShareFont.BRAND) brand else body
            textSize = size
        }.measureText(text)
    }

    fun stats(a: ActivityEntity): List<ShareStat> = buildList {
        add(ShareStat("Distance", decimal(a.distanceM / 1000.0, 2), "km"))
        add(ShareStat("Elev Gain", decimal(a.elevationGainM, 0), "m"))
        add(ShareStat("Time", time(a.movingMs), ""))
        a.avgPower?.let { add(ShareStat("Avg Power", decimal(it.toDouble(), 0), "W")) }
        add(ShareStat("Speed", decimal(a.avgSpeedKmh, 1), "km/h"))
    }

    fun input(r: ShareRequest): ShareInput {
        val a = r.activity
        val date = SimpleDateFormat("EEEE, d MMM yyyy · HH.mm", Locale("id", "ID")).format(Date(a.startMs))
        val top = r.elevation.maxOrNull()
        return ShareInput(
            title = a.name,
            date = date.replaceFirstChar { it.uppercase() },
            stats = stats(a),
            elevationMax = if (r.elevation.size >= 2 && top != null) "${decimal(top.toDouble(), 0)} m" else null,
            attribution = MapStyles.attribution(r.base, r.mapboxToken),
            record = r.record,
        )
    }

    /** Templates that need a route, an elevation profile or a record are offered only when the activity has one. */
    fun available(template: ShareTemplate, r: ShareRequest): Boolean {
        val needsRoute = template.usesMap || template == ShareTemplate.ROUTE_ONLY || template == ShareTemplate.BIG_NUMBER
        if (needsRoute && r.path.size < 2) return false
        return ShareLayout.available(template, input(r))
    }

    /** [scale] below 1 renders a small thumbnail of the same layout for the template picker. */
    suspend fun render(r: ShareRequest, template: ShareTemplate, format: ShareFormat, scale: Float = 1f): Bitmap {
        val elements = withContext(Dispatchers.Default) { ShareLayout.layout(template, format, input(r), measure) }
        val pictures = HashMap<ShareElement.MapArea, MapPicture?>()
        for (m in elements.filterIsInstance<ShareElement.MapArea>().filter { it.tiles }) {
            pictures[m] = maps.render(
                key = "share${r.activity.id}",
                path = r.path,
                base = r.base,
                token = r.mapboxToken,
                width = (m.box.width * scale).toInt(),
                height = (m.box.height * scale).toInt(),
                inset = floatArrayOf(
                    (m.routeBox.left - m.box.left) * scale,
                    (m.routeBox.top - m.box.top) * scale,
                    (m.routeBox.right - m.box.left) * scale,
                    (m.routeBox.bottom - m.box.top) * scale,
                ),
            )
        }
        return withContext(Dispatchers.Default) {
            val bmp = Bitmap.createBitmap((format.width * scale).toInt(), (format.height * scale).toInt(), Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.scale(scale, scale)
            if (!template.transparent) c.drawColor(ink)
            val photo = if (template.usesPhoto) r.photo?.let { decodeFit(it, (format.width * scale).toInt(), (format.height * scale).toInt()) } else null
            for (e in elements) draw(c, e, r, photo, (e as? ShareElement.MapArea)?.let(pictures::get), scale)
            bmp
        }
    }

    private fun draw(c: Canvas, e: ShareElement, r: ShareRequest, photo: Bitmap?, picture: MapPicture?, scale: Float) {
        val b = e.box
        when (e) {
            is ShareElement.Photo -> {
                c.save()
                c.clipRect(b.left, b.top, b.right, b.bottom)
                if (photo != null) drawCover(c, photo, b) else drawNoPhoto(c, b)
                c.restore()
            }
            is ShareElement.Shade -> c.drawRect(b.left, b.top, b.right, b.bottom, Paint().apply {
                shader = LinearGradient(0f, b.top, 0f, b.bottom, black(e.topAlpha), black(e.bottomAlpha), Shader.TileMode.CLAMP)
            })
            is ShareElement.MapArea -> drawMap(c, e, r.path, picture, scale)
            is ShareElement.Panel -> c.drawRoundRect(RectF(b.left, b.top, b.right, b.bottom), e.cornerPx, e.cornerPx, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb((e.alpha * 255).toInt(), 10, 10, 11)
            })
            is ShareElement.Label -> {
                val p = text(if (e.font == ShareFont.BRAND) brand else body, e.sizePx, toneColor(e.tone))
                var t = e.text
                // The layout measured with the same Paint, so this only trims a last pixel or two of rounding.
                while (t.length > 2 && p.measureText(t) > b.width) t = t.dropLast(2).trimEnd() + "…"
                val x = when (e.align) {
                    Align.START -> b.left
                    Align.CENTER -> (b.left + b.right - p.measureText(t)) / 2
                    Align.END -> b.right - p.measureText(t)
                }
                c.drawText(t, x, b.top + e.sizePx * 0.95f, p)
            }
            is ShareElement.Value -> {
                var size = e.sizePx
                while (size > 16f && ShareLayout.valueWidth(measure, e.value, e.unit, size) > b.width) size -= 1f
                val vp = text(brand, size, Color.WHITE)
                val up = text(body, size * ShareLayout.UNIT_RATIO, Color.WHITE)
                val total = ShareLayout.valueWidth(measure, e.value, e.unit, size)
                val x = when (e.align) {
                    Align.START -> b.left
                    Align.CENTER -> (b.left + b.right - total) / 2
                    Align.END -> b.right - total
                }
                val baseline = b.top + size * 0.9f
                c.drawText(e.value, x, baseline, vp)
                if (e.unit.isNotEmpty()) c.drawText(e.unit, x + vp.measureText(e.value) + size * 0.12f, baseline, up)
            }
            is ShareElement.Wordmark -> drawable(c, R.drawable.brand_wordmark_white, b, scale)
            is ShareElement.Icon -> drawable(c, R.drawable.brand_icon_white, b, scale)
            is ShareElement.Elevation -> drawElevation(c, r.elevation, b)
            is ShareElement.Medal -> drawMedal(c, e.rank, b)
        }
    }

    private fun drawMap(c: Canvas, m: ShareElement.MapArea, path: List<LatLon>, picture: MapPicture?, scale: Float) {
        val b = m.box
        c.save()
        c.clipPath(Path().apply { addRoundRect(RectF(b.left, b.top, b.right, b.bottom), m.cornerPx, m.cornerPx, Path.Direction.CW) })
        val points: List<PointF> = if (m.tiles && picture != null) {
            c.drawBitmap(picture.bitmap, null, RectF(b.left, b.top, b.right, b.bottom), Paint(Paint.FILTER_BITMAP_FLAG))
            picture.route.map { PointF(b.left + it.x / scale, b.top + it.y / scale) }
        } else {
            // Tiles not reachable (offline) or a transparent card: the route alone, fitted into its box.
            if (m.tiles) c.drawRect(b.left, b.top, b.right, b.bottom, Paint().apply { color = 0xFF1D1D20.toInt() })
            fitRoute(path, m.routeBox.left, m.routeBox.top, m.routeBox.right, m.routeBox.bottom)
        }
        drawRoute(c, points, m.strokePx, b)
        c.restore()
    }

    private fun drawRoute(c: Canvas, pts: List<PointF>, stroke: Float, area: Box) {
        if (pts.size < 2) return
        val p = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
        }
        val base = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        c.drawPath(p, Paint(base).apply { color = 0xBF0A0A0B.toInt(); strokeWidth = stroke + 10f })
        c.drawPath(p, Paint(base).apply {
            strokeWidth = stroke
            shader = LinearGradient(area.left, 0f, area.right, 0f, signal, amber, Shader.TileMode.CLAMP)
        })
        val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        c.drawCircle(pts.first().x, pts.first().y, stroke * 1.3f, Paint(dot).apply { color = Color.WHITE })
        c.drawCircle(pts.first().x, pts.first().y, stroke * 0.7f, Paint(dot).apply { color = 0xFF2FB765.toInt() })
        c.drawCircle(pts.last().x, pts.last().y, stroke * 1.3f, Paint(dot).apply { color = Color.WHITE })
        c.drawCircle(pts.last().x, pts.last().y, stroke * 0.7f, Paint(dot).apply { color = ink })
    }

    private fun drawElevation(c: Canvas, ele: List<Float>, b: Box) {
        if (ele.size < 2) return
        val lo = ele.min()
        val span = (ele.max() - lo).coerceAtLeast(1f)
        val step = b.width / (ele.size - 1)
        val line = Path()
        ele.forEachIndexed { i, v ->
            val x = b.left + i * step
            val y = b.bottom - (v - lo) / span * b.height
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        val area = Path(line).apply {
            lineTo(b.right, b.bottom)
            lineTo(b.left, b.bottom)
            close()
        }
        c.drawPath(area, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, b.top, 0f, b.bottom, 0xE6FF4F00.toInt(), 0x0DFF4F00, Shader.TileMode.CLAMP)
        })
        c.drawPath(line, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            strokeJoin = Paint.Join.ROUND
            color = Color.WHITE
        })
    }

    /** Gold, silver and bronze discs with the rank in Ink; every pairing is above 5:1. */
    private fun drawMedal(c: Canvas, rank: Int, b: Box) {
        val fill = when (rank) {
            1 -> 0xFFE0A400.toInt()
            2 -> 0xFF9AA0A6.toInt()
            else -> 0xFFB8733A.toInt()
        }
        val cx = (b.left + b.right) / 2
        val cy = (b.top + b.bottom) / 2
        val rad = b.width / 2
        c.drawCircle(cx, cy, rad, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill })
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(body, Typeface.BOLD)
            textSize = rad
            color = ink
            textAlign = Paint.Align.CENTER
        }
        c.drawText(rank.toString(), cx, cy + rad * 0.35f, p)
    }

    /** Without a photo the card is not empty: Ink fading into a dark Signal tint at the bottom. */
    private fun drawNoPhoto(c: Canvas, b: Box) {
        c.drawRect(b.left, b.top, b.right, b.bottom, Paint().apply {
            shader = LinearGradient(0f, b.top, 0f, b.bottom, 0xFF141416.toInt(), 0xFF3A1A0A.toInt(), Shader.TileMode.CLAMP)
        })
    }

    private fun toneColor(t: Tone) = when (t) {
        Tone.PRIMARY -> Color.WHITE
        Tone.MUTED -> 0xFFD9D9DD.toInt()
        Tone.ACCENT -> amber
    }

    private fun black(a: Float) = Color.argb((a.coerceIn(0f, 1f) * 255).toInt(), 0, 0, 0)

    /** Writes [bmp] to the share cache and returns a content Uri other apps can read. */
    suspend fun cacheForShare(bmp: Bitmap, template: ShareTemplate): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val png = template.transparent
        val f = File(dir, "strideway-${System.currentTimeMillis()}.${if (png) "png" else "jpg"}")
        f.outputStream().use { bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 92, it) }
        FileProvider.getUriForFile(context, "${context.packageName}.share", f)
    }

    /** Saves to Pictures/Strideway through MediaStore; no storage permission needed on Android 10+. */
    suspend fun saveToGallery(bmp: Bitmap, template: ShareTemplate): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val png = template.transparent
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

    private fun text(face: Typeface?, size: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = face
        textSize = size
        this.color = color
        setShadowLayer(6f, 0f, 2f, 0x66000000)
    }

    private fun decodeFit(f: File, w: Int, h: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= w && bounds.outHeight / (sample * 2) >= h) sample *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun drawCover(c: Canvas, src: Bitmap, b: Box) {
        val s = maxOf(b.width / src.width, b.height / src.height)
        val m = Matrix().apply {
            postScale(s, s)
            postTranslate(b.left + (b.width - src.width * s) / 2f, b.top + (b.height - src.height * s) / 2f)
        }
        c.drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG))
    }

    private fun drawable(c: Canvas, res: Int, b: Box, scale: Float) {
        val d = ContextCompat.getDrawable(context, res) ?: return
        val bmp = d.toBitmap((b.width * scale).toInt().coerceAtLeast(1), (b.height * scale).toInt().coerceAtLeast(1))
        c.drawBitmap(bmp, null, Rect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt()), Paint(Paint.FILTER_BITMAP_FLAG))
    }

    private fun time(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        return if (h > 0) "${h}h ${m}m" else "${m}m ${s % 60}s"
    }

    /** Indonesian number style: dot for thousands, comma for decimals ("1.234,56"). */
    private fun decimal(v: Double, places: Int): String =
        String.format(Locale.US, "%,.${places}f", v).replace(',', ' ').replace('.', ',').replace(' ', '.')
}
