package id.web.agentvaedah.strideway.core.share

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Share card layouts, after the cards riders know from Strava. */
/** [maxStats] caps how many figures a compact card shows; the rest stay on the full-size templates. */
enum class ShareTemplate(
    val label: String,
    val usesPhoto: Boolean,
    val usesMap: Boolean,
    val transparent: Boolean = false,
    val maxStats: Int = Int.MAX_VALUE,
) {
    BOTTOM_STATS("Statistik bawah", usesPhoto = true, usesMap = false),
    ROUTE_STATS("Rute + statistik", usesPhoto = true, usesMap = true),
    ROUTE_ONLY("Tanpa foto", usesPhoto = false, usesMap = false, transparent = true),
    MINIMAL("Minimal", usesPhoto = true, usesMap = false),
    MAP_FULL("Peta penuh", usesPhoto = false, usesMap = true),
    PHOTO_MAP("Foto + peta", usesPhoto = true, usesMap = true),
    BIG_NUMBER("Angka besar", usesPhoto = true, usesMap = false),
    ELEVATION("Elevasi", usesPhoto = true, usesMap = false, maxStats = 3),
    CARD("Kartu", usesPhoto = true, usesMap = true, maxStats = 3),
    RECORD("Rekor", usesPhoto = true, usesMap = false),
}

enum class ShareFormat(val label: String, val width: Int, val height: Int) {
    FEED("Feed 4:5", 1080, 1350),
    STORY("Story 9:16", 1080, 1920),
}

/** One figure; [unit] is drawn smaller after [value]. The first stat of a card is always the distance. */
data class ShareStat(val label: String, val value: String, val unit: String)

/** A personal record set on this activity, for the Rekor card. [rank] 1 is the record itself. */
data class ShareRecord(val title: String, val value: String, val note: String?, val rank: Int)

data class ShareInput(
    val title: String,
    val date: String,
    val stats: List<ShareStat>,
    /** Highest point of the elevation profile, already formatted; null when the activity has no elevation. */
    val elevationMax: String? = null,
    /** Map credit drawn on tile backgrounds. */
    val attribution: String? = null,
    val record: ShareRecord? = null,
)

enum class ShareFont { BRAND, BODY }
enum class Tone { PRIMARY, MUTED, ACCENT }
enum class Align { START, CENTER, END }

/** Width of [text] at [sizePx]; the app measures with Paint, tests with [EstimatedMeasure]. */
fun interface TextMeasure {
    fun width(text: String, sizePx: Float, font: ShareFont): Float
}

data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    fun intersects(o: Box): Boolean = left < o.right - EPS && o.left < right - EPS && top < o.bottom - EPS && o.top < bottom - EPS
    fun contains(o: Box): Boolean = o.left >= left - EPS && o.right <= right + EPS && o.top >= top - EPS && o.bottom <= bottom + EPS

    private companion object { const val EPS = 0.5f }
}

/** Drawing instructions in canvas pixels, back to front. */
sealed interface ShareElement {
    val box: Box

    /** The rider's photo, cropped to cover [box]. */
    data class Photo(override val box: Box) : ShareElement

    /** Vertical black gradient that keeps white text readable on bright photos. */
    data class Shade(override val box: Box, val topAlpha: Float, val bottomAlpha: Float) : ShareElement

    /**
     * Route, over map tiles when [tiles] is set. The route is fitted into [routeBox] so it stays clear of
     * whatever sits on top of a full-bleed map. [background] maps fill a canvas edge and may sit under text.
     */
    data class MapArea(
        override val box: Box,
        val routeBox: Box,
        val tiles: Boolean,
        val cornerPx: Float,
        val strokePx: Float,
        val background: Boolean,
    ) : ShareElement

    /** Dark rounded card that text sits on. */
    data class Panel(override val box: Box, val cornerPx: Float, val alpha: Float) : ShareElement

    data class Label(
        override val box: Box,
        val text: String,
        val sizePx: Float,
        val font: ShareFont,
        val tone: Tone,
        val align: Align,
    ) : ShareElement

    /** Big figure in the brand face with a smaller unit after it. */
    data class Value(override val box: Box, val value: String, val unit: String, val sizePx: Float, val align: Align) : ShareElement

    data class Wordmark(override val box: Box) : ShareElement
    data class Icon(override val box: Box) : ShareElement
    data class Elevation(override val box: Box) : ShareElement
    data class Medal(override val box: Box, val rank: Int) : ShareElement
}

/**
 * Places every element of a share card so that nothing overlaps and nothing leaves the 60 px safe area.
 * Blocks stack from the bottom up, figures shrink until they fit their column, and when a template still
 * does not fit (long titles, five stats on a short canvas) the whole card is laid out again at a smaller scale.
 */
object ShareLayout {
    const val MARGIN = 60f
    const val LINE = 1.25f
    const val VALUE_LINE = 1.1f
    const val UNIT_RATIO = 0.42f
    const val WORDMARK_RATIO = 5.33f
    const val ICON_RATIO = 1.085f
    private const val MIN_TEXT = 16f

    fun available(template: ShareTemplate, input: ShareInput): Boolean = when (template) {
        ShareTemplate.ELEVATION -> input.elevationMax != null && input.stats.size >= 3
        ShareTemplate.RECORD -> input.record != null
        else -> input.stats.isNotEmpty()
    }

    fun layout(template: ShareTemplate, format: ShareFormat, input: ShareInput, measure: TextMeasure): List<ShareElement> {
        require(available(template, input)) { "${template.name} needs data this activity does not have" }
        var k = 1f
        var last: List<ShareElement> = emptyList()
        while (k >= 0.45f) {
            val b = Builder(format.width.toFloat(), format.height.toFloat(), measure, k)
            b.build(template, input)
            last = b.out
            if (b.fits && problems(last, format, measure).isEmpty()) return last
            k -= 0.05f
        }
        return last
    }

    /** Every rule a laid-out card must satisfy; empty means the card is clean. */
    fun problems(elements: List<ShareElement>, format: ShareFormat, measure: TextMeasure): List<String> {
        val w = format.width.toFloat()
        val h = format.height.toFloat()
        val safe = Box(MARGIN, MARGIN, w - MARGIN, h - MARGIN)
        val out = ArrayList<String>()
        val fg = elements.filter { it.isForeground }
        for (e in fg) if (!safe.contains(e.box)) out += "${e.name} leaves the safe area: ${e.box}"
        for (i in fg.indices) for (j in i + 1 until fg.size) {
            val a = fg[i]
            val b = fg[j]
            if (!a.box.intersects(b.box)) continue
            val nested = (a.isContainer && a.box.contains(b.box)) || (b.isContainer && b.box.contains(a.box))
            if (!nested) out += "${a.name} overlaps ${b.name}"
        }
        for (e in fg) {
            val textW = when (e) {
                is ShareElement.Label -> measure.width(e.text, e.sizePx, e.font)
                is ShareElement.Value -> valueWidth(measure, e.value, e.unit, e.sizePx)
                else -> continue
            }
            if (textW > e.box.width + 0.5f) out += "${e.name} text is wider than its box"
        }
        return out
    }

    fun valueWidth(measure: TextMeasure, value: String, unit: String, size: Float): Float =
        measure.width(value, size, ShareFont.BRAND) + if (unit.isEmpty()) 0f else size * 0.12f + measure.width(unit, size * UNIT_RATIO, ShareFont.BODY)

    private val ShareElement.isForeground: Boolean
        get() = when (this) {
            is ShareElement.Photo, is ShareElement.Shade -> false
            is ShareElement.MapArea -> !background
            else -> true
        }

    private val ShareElement.isContainer: Boolean get() = this is ShareElement.Panel || this is ShareElement.MapArea

    private val ShareElement.name: String
        get() = when (this) {
            is ShareElement.Label -> "label \"$text\""
            is ShareElement.Value -> "value \"$value\""
            else -> this::class.simpleName ?: "element"
        }

    private class Grid(val cols: Int, val colW: Float, val gapX: Float, val gapY: Float, val labelPx: Float, val valuePx: Float, val rows: Int) {
        val rowH: Float get() = labelPx * LINE + 6f + valuePx * VALUE_LINE
        val height: Float get() = rows * rowH + (rows - 1).coerceAtLeast(0) * gapY
    }

    private class Builder(val w: Float, val h: Float, val m: TextMeasure, val k: Float) {
        val out = ArrayList<ShareElement>()
        var fits = true
        val left = MARGIN
        val right = w - MARGIN
        val story = h > 1500f

        fun build(t: ShareTemplate, input: ShareInput) {
            when (t) {
                ShareTemplate.BOTTOM_STATS -> bottomStats(input)
                ShareTemplate.ROUTE_STATS -> routeStats(input)
                ShareTemplate.ROUTE_ONLY -> routeOnly(input)
                ShareTemplate.MINIMAL -> minimal(input)
                ShareTemplate.MAP_FULL -> mapFull(input)
                ShareTemplate.PHOTO_MAP -> photoMap(input)
                ShareTemplate.BIG_NUMBER -> bigNumber(input)
                ShareTemplate.ELEVATION -> elevation(input)
                ShareTemplate.CARD -> card(input)
                ShareTemplate.RECORD -> record(input)
            }
        }

        // ---- Building blocks ----

        fun need(ok: Boolean) { if (!ok) fits = false }

        /** Single-line text: shrinks to the floor, then ellipsizes. Returns the line height used. */
        fun text(text: String, x: Float, top: Float, width: Float, size: Float, font: ShareFont = ShareFont.BODY, tone: Tone = Tone.PRIMARY, align: Align = Align.START): Float {
            var s = size
            val floor = max(MIN_TEXT, size * 0.7f)
            while (s > floor && m.width(text, s, font) > width) s -= 1f
            var t = text
            if (m.width(t, s, font) > width) {
                while (t.isNotEmpty() && m.width("$t...", s, font) > width) t = t.dropLast(1)
                t = t.trimEnd() + "..."
            }
            val lh = s * LINE
            out += ShareElement.Label(Box(x, top, x + width, top + lh), t, s, font, tone, align)
            return lh
        }

        fun textHeight(size: Float) = size * LINE

        /** Largest value size, at most [max], at which every figure of [stats] fits [width]. */
        fun fitValues(stats: List<ShareStat>, width: Float, max: Float): Float {
            var s = max
            while (s > 20f && stats.any { valueWidth(m, it.value, it.unit, s) > width }) s -= 1f
            return s
        }

        fun grid(stats: List<ShareStat>, cols: Int, width: Float, maxValue: Float, label: Float, gapX: Float = 32f * k, gapY: Float = 26f * k): Grid {
            val c = cols.coerceIn(1, max(1, stats.size))
            val colW = (width - (c - 1) * gapX) / c
            var lp = label
            while (lp > MIN_TEXT && stats.any { m.width(it.label, lp, ShareFont.BODY) > colW }) lp -= 1f
            return Grid(c, colW, gapX, gapY, lp, fitValues(stats, colW, maxValue), ceil(stats.size / c.toFloat()).toInt())
        }

        fun placeGrid(g: Grid, stats: List<ShareStat>, x: Float, top: Float, align: Align = Align.START) {
            stats.forEachIndexed { i, s ->
                val cx = x + (i % g.cols) * (g.colW + g.gapX)
                val cy = top + (i / g.cols) * (g.rowH + g.gapY)
                text(s.label, cx, cy, g.colW, g.labelPx, tone = Tone.MUTED, align = align)
                val vy = cy + g.labelPx * LINE + 6f
                out += ShareElement.Value(Box(cx, vy, cx + g.colW, vy + g.valuePx * VALUE_LINE), s.value, s.unit, g.valuePx, align)
            }
        }

        fun wordmark(x: Float, top: Float, height: Float): Float {
            val wd = height * WORDMARK_RATIO
            out += ShareElement.Wordmark(Box(x, top, x + wd, top + height))
            return wd
        }

        fun photo() { out += ShareElement.Photo(Box(0f, 0f, w, h)) }
        /** Shade sits right above the photo, under everything else, though it is sized after the text it protects. */
        fun shade(fromY: Float, top: Float = 0f, bottom: Float = 0.85f) {
            val at = out.indexOfFirst { it is ShareElement.Photo } + 1
            out.add(at, ShareElement.Shade(Box(0f, fromY.coerceIn(0f, h), w, h), top, bottom))
        }

        /** Title and date stacked upward from [bottom]; returns the new top. */
        fun titleBlock(input: ShareInput, bottom: Float, x: Float = left, width: Float = right - left, align: Align = Align.START): Float {
            var y = bottom - textHeight(28f * k)
            text(input.date, x, y, width, 28f * k, tone = Tone.MUTED, align = align)
            y -= 8f * k + textHeight(44f * k)
            text(input.title, x, y, width, 44f * k, align = align)
            return y
        }

        fun attribution(input: ShareInput, map: Box, atTop: Boolean) {
            val credit = input.attribution ?: return
            val size = 18f * k
            val wd = min(440f * k, map.width - 24f * k)
            val y = if (atTop) map.top + 12f * k else map.bottom - 12f * k - textHeight(size)
            text(credit, map.right - 12f * k - wd, y, wd, size, tone = Tone.MUTED, align = Align.END)
        }

        // ---- Templates ----

        fun bottomStats(input: ShareInput) {
            photo()
            val g = grid(input.stats, 3, right - left, 76f * k, 28f * k)
            var y = h - MARGIN - 48f * k
            wordmark(left, y, 48f * k)
            y -= 32f * k + g.height
            placeGrid(g, input.stats, left, y)
            val top = titleBlock(input, y - 28f * k)
            shade(top - 160f * k)
            need(top >= h * 0.25f)
        }

        fun routeStats(input: ShareInput) {
            photo()
            val n = input.stats.size
            val rowH = (if (story) 720f else 560f) * k
            val mapW = (if (story) 520f else 470f) * k
            var y = h - MARGIN - 48f * k
            wordmark(left, y, 48f * k)
            y -= 32f * k + rowH
            val map = Box(left, y, left + mapW, y + rowH)
            out += ShareElement.MapArea(map, map, tiles = true, cornerPx = 32f * k, strokePx = 12f * k, background = false)
            attribution(input, map, atTop = false)
            // Stats share the map's height, one cell each, so the column never runs past the map.
            val colX = left + mapW + 40f * k
            val colW = right - colX
            val gap = 14f * k
            val cell = (rowH - (n - 1) * gap) / n
            var label = 26f * k
            while (label > MIN_TEXT && input.stats.any { m.width(it.label, label, ShareFont.BODY) > colW }) label -= 1f
            val byHeight = (cell - label * LINE - 6f) / VALUE_LINE
            val value = fitValues(input.stats, colW, min(70f * k, byHeight))
            need(byHeight >= 24f)
            input.stats.forEachIndexed { i, s ->
                val cy = y + i * (cell + gap)
                text(s.label, colX, cy, colW, label, tone = Tone.MUTED)
                val vy = cy + label * LINE + 6f
                out += ShareElement.Value(Box(colX, vy, right, vy + value * VALUE_LINE), s.value, s.unit, value, Align.START)
            }
            val top = titleBlock(input, y - 28f * k)
            shade(top - 160f * k)
            need(top >= MARGIN)
        }

        fun routeOnly(input: ShareInput) {
            val g = grid(input.stats, 2, right - left, (if (story) 100f else 86f) * k, 30f * k, gapX = 40f * k, gapY = 24f * k)
            var y = h - MARGIN - 56f * k
            wordmark(left, y, 56f * k)
            y -= 40f * k + g.height
            placeGrid(g, input.stats, left, y)
            val mapBottom = y - 40f * k
            val map = Box(left, MARGIN, right, mapBottom)
            need(map.height >= 260f)
            out += ShareElement.MapArea(map, map, tiles = false, cornerPx = 0f, strokePx = 18f * k, background = false)
        }

        fun minimal(input: ShareInput) {
            photo()
            val iconH = 96f * k
            val iconW = iconH * ICON_RATIO
            out += ShareElement.Icon(Box(right - iconW, h - MARGIN - iconH, right, h - MARGIN))
            val width = right - left - iconW - 32f * k
            val caption = input.stats.drop(1).joinToString(" · ") { listOf(it.value, it.unit).filter(String::isNotEmpty).joinToString(" ") }
            var y = h - MARGIN
            if (caption.isNotEmpty()) {
                y -= textHeight(34f * k)
                text(caption, left, y, width, 34f * k, tone = Tone.MUTED)
                y -= 12f * k
            }
            val d = input.stats.first()
            val size = fitValues(listOf(d), width, 190f * k)
            y -= size * VALUE_LINE
            out += ShareElement.Value(Box(left, y, left + width, y + size * VALUE_LINE), d.value, d.unit, size, Align.START)
            shade(y - 200f * k)
        }

        fun mapFull(input: ShareInput) {
            val chipH = 40f * k + 28f * k
            val chip = Box(left, MARGIN, left + 40f * k * WORDMARK_RATIO + 40f * k, MARGIN + chipH)
            val pad = 44f * k
            val innerW = right - left - 2 * pad
            val g = grid(input.stats, 3, innerW, 70f * k, 28f * k)
            val panelH = pad + textHeight(44f * k) + 8f * k + textHeight(28f * k) + 26f * k + g.height + pad
            val panel = Box(left, h - MARGIN - panelH, right, h - MARGIN)
            val routeBox = Box(left + 20f * k, chip.bottom + 40f * k, right - 20f * k, panel.top - 40f * k)
            need(routeBox.height >= 220f)
            out += ShareElement.MapArea(Box(0f, 0f, w, h), routeBox, tiles = true, cornerPx = 0f, strokePx = 16f * k, background = true)
            out += ShareElement.Panel(chip, 20f * k, 0.92f)
            wordmark(chip.left + 20f * k, chip.top + 14f * k, 40f * k)
            input.attribution?.let { credit ->
                val x = chip.right + 24f * k
                text(credit, x, MARGIN + (chipH - textHeight(18f * k)) / 2, right - x, 18f * k, tone = Tone.MUTED, align = Align.END)
            }
            out += ShareElement.Panel(panel, 40f * k, 0.88f)
            var y = panel.top + pad
            y += text(input.title, panel.left + pad, y, innerW, 44f * k) + 8f * k
            y += text(input.date, panel.left + pad, y, innerW, 28f * k, tone = Tone.MUTED) + 26f * k
            placeGrid(g, input.stats, panel.left + pad, y)
        }

        fun photoMap(input: ShareInput) {
            val split = h * 0.56f
            out += ShareElement.Photo(Box(0f, 0f, w, split))
            out += ShareElement.Shade(Box(0f, 0f, w, 260f * k), 0.5f, 0f)
            val pad = 36f * k
            val innerW = right - left - 2 * pad
            val head = input.stats.take(3)
            val rest = input.stats.drop(3)
            val g = grid(head, 3, innerW, 70f * k, 28f * k)
            val caption = rest.joinToString(" · ") { listOf(it.label, it.value, it.unit).filter(String::isNotEmpty).joinToString(" ") }
            val panelH = pad + g.height + (if (caption.isEmpty()) 0f else 20f * k + textHeight(30f * k)) + pad
            val panel = Box(left, split - panelH / 2, right, split + panelH / 2)
            val routeBox = Box(left, panel.bottom + 40f * k, right, h - MARGIN - 40f * k)
            need(routeBox.height >= 160f)
            out += ShareElement.MapArea(Box(0f, split, w, h), routeBox, tiles = true, cornerPx = 0f, strokePx = 12f * k, background = true)
            input.attribution?.let { text(it, right - 440f * k, h - MARGIN - textHeight(18f * k), 440f * k, 18f * k, tone = Tone.MUTED, align = Align.END) }
            wordmark(left, MARGIN, 48f * k)
            out += ShareElement.Panel(panel, 36f * k, 0.9f)
            placeGrid(g, head, panel.left + pad, panel.top + pad)
            if (caption.isNotEmpty()) text(caption, panel.left + pad, panel.top + pad + g.height + 20f * k, innerW, 30f * k, tone = Tone.MUTED)
        }

        fun bigNumber(input: ShareInput) {
            photo()
            out += ShareElement.Shade(Box(0f, 0f, w, h), 0.55f, 0.55f)
            val wmH = 48f * k
            val wmW = wmH * WORDMARK_RATIO
            wordmark((w - wmW) / 2, MARGIN, wmH)
            val rest = input.stats.drop(1)
            var bottom = h - MARGIN
            if (rest.isNotEmpty()) {
                val g = grid(rest, min(4, rest.size), right - left, 54f * k, 26f * k, gapX = 24f * k)
                bottom -= g.height
                placeGrid(g, rest, left, bottom, Align.CENTER)
            }
            val d = input.stats.first()
            val value = fitValues(listOf(ShareStat(d.label, d.value, "")), right - left, 300f * k)
            val routeH = (if (story) 340f else 220f) * k
            val block = textHeight(44f * k) + 8f * k + value * VALUE_LINE + textHeight(48f * k) + 30f * k + routeH
            val areaTop = MARGIN + wmH + 40f * k
            val areaBottom = bottom - 40f * k
            need(block <= areaBottom - areaTop)
            var y = areaTop + ((areaBottom - areaTop) - block).coerceAtLeast(0f) / 2
            y += text(input.title, left, y, right - left, 44f * k, align = Align.CENTER) + 8f * k
            out += ShareElement.Value(Box(left, y, right, y + value * VALUE_LINE), d.value, "", value, Align.CENTER)
            y += value * VALUE_LINE
            y += text(d.unit.uppercase(), left, y, right - left, 48f * k, align = Align.CENTER) + 30f * k
            val routeW = min(560f * k, right - left)
            val route = Box((w - routeW) / 2, y, (w + routeW) / 2, y + routeH)
            out += ShareElement.MapArea(route, route, tiles = false, cornerPx = 0f, strokePx = 14f * k, background = false)
        }

        fun elevation(input: ShareInput) {
            photo()
            wordmark(left, MARGIN, 48f * k)
            val half = (right - left) / 2
            var y = h - MARGIN - textHeight(26f * k)
            val d = input.stats.first()
            text("0 ${d.unit}".trim(), left, y, half, 26f * k, tone = Tone.MUTED)
            text("${d.value} ${d.unit}".trim(), left + half, y, half, 26f * k, tone = Tone.MUTED, align = Align.END)
            val chartH = (if (story) 380f else 250f) * k
            y -= 8f * k + chartH
            out += ShareElement.Elevation(Box(left, y, right, y + chartH))
            y -= 8f * k + textHeight(26f * k)
            text(input.elevationMax.orEmpty(), left, y, half, 26f * k, tone = Tone.MUTED)
            val trio = listOf(input.stats[1], input.stats[0], input.stats[2])
            val g = grid(trio, 3, right - left, 76f * k, 28f * k)
            y -= 24f * k + g.height
            placeGrid(g, trio, left, y)
            val top = titleBlock(input, y - 28f * k)
            shade(top - 160f * k)
            need(top >= MARGIN + 48f * k + 40f * k)
        }

        fun card(input: ShareInput) {
            photo()
            val cardW = min(660f * k, right - left)
            val pad = 28f * k
            val innerW = cardW - 2 * pad
            val trio = listOfNotNull(input.stats.getOrNull(0), input.stats.getOrNull(2), input.stats.getOrNull(1))
            val g = grid(trio, 3, innerW, 54f * k, 24f * k, gapX = 20f * k)
            val mapH = 300f * k
            val footH = max(36f * k, textHeight(24f * k))
            val cardH = pad + mapH + 22f * k + g.height + 22f * k + footH + pad
            val panel = Box(left, h - MARGIN - cardH, left + cardW, h - MARGIN)
            out += ShareElement.Panel(panel, 44f * k, 0.9f)
            val map = Box(panel.left + pad, panel.top + pad, panel.right - pad, panel.top + pad + mapH)
            out += ShareElement.MapArea(map, map, tiles = true, cornerPx = 24f * k, strokePx = 10f * k, background = false)
            attribution(input, map, atTop = false)
            var y = map.bottom + 22f * k
            placeGrid(g, trio, map.left, y)
            y += g.height + 22f * k
            val wmW = wordmark(map.left, y + (footH - 36f * k) / 2, 36f * k)
            val dateX = map.left + wmW + 16f * k
            text(input.date, dateX, y + (footH - textHeight(24f * k)) / 2, map.right - dateX, 24f * k, tone = Tone.MUTED, align = Align.END)
        }

        fun record(input: ShareInput) {
            val r = input.record ?: return
            photo()
            wordmark(left, MARGIN, 48f * k)
            val g = grid(input.stats, 3, right - left, 60f * k, 26f * k)
            var y = h - MARGIN - g.height
            placeGrid(g, input.stats, left, y)
            val medal = 150f * k
            val textX = left + medal + 28f * k
            val textW = right - textX
            val value = fitValues(listOf(ShareStat("", r.value, "")), textW, 150f * k)
            val textH = textHeight(36f * k) + value * VALUE_LINE + (if (r.note == null) 0f else textHeight(32f * k))
            val rowH = max(medal, textH)
            y -= 32f * k + rowH
            out += ShareElement.Medal(Box(left, y + (rowH - medal) / 2, left + medal, y + (rowH + medal) / 2), r.rank)
            var ty = y + (rowH - textH) / 2
            ty += text(r.title, textX, ty, textW, 36f * k)
            out += ShareElement.Value(Box(textX, ty, right, ty + value * VALUE_LINE), r.value, "", value, Align.START)
            ty += value * VALUE_LINE
            r.note?.let { text(it, textX, ty, textW, 32f * k, tone = Tone.ACCENT) }
            shade(y - 200f * k)
            need(y >= MARGIN + 48f * k + 40f * k)
        }
    }
}

/**
 * Conservative per-character widths for Strideway Sans Bold Italic and Outfit, used by tests and as a
 * first pass; the renderer re-measures with Paint and shrinks further if the real glyphs run wider.
 */
object EstimatedMeasure : TextMeasure {
    override fun width(text: String, sizePx: Float, font: ShareFont): Float = text.sumOf { c ->
        val em = when (font) {
            ShareFont.BRAND -> when {
                c == ' ' -> 0.3
                c in ".,:'" -> 0.32
                c.isDigit() -> 0.64
                c.isUpperCase() -> 0.7
                else -> 0.62
            }
            ShareFont.BODY -> when {
                c == ' ' -> 0.27
                c in ".,:;'!|il" -> 0.3
                c in "mwMW" -> 0.88
                c.isUpperCase() -> 0.7
                c.isDigit() -> 0.6
                c == '·' -> 0.4
                else -> 0.57
            }
        }
        em
    }.toFloat() * sizePx
}
