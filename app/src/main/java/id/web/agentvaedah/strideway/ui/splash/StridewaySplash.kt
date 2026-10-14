package id.web.agentvaedah.strideway.ui.splash

import android.graphics.Bitmap
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import id.web.agentvaedah.strideway.R
import id.web.agentvaedah.strideway.core.splash.Ease
import id.web.agentvaedah.strideway.core.splash.Phase
import id.web.agentvaedah.strideway.core.splash.SplashTimeline
import id.web.agentvaedah.strideway.ui.theme.LocalStridewayColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint

/**
 * Five-second launch splash. The system splash shows a single dot; this continues from that dot:
 * pings, contours opening around it, the first climb, then the wordmark, where the dot lands on the i.
 * Timing lives in core's [SplashTimeline]. A tap anywhere skips to the app.
 */
@Composable
fun StridewaySplash(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = LocalStridewayColors.current
    val appName = stringResource(R.string.app_name)
    val tagline = stringResource(R.string.splash_tagline)
    val skipLabel = stringResource(R.string.splash_skip)
    val finish by rememberUpdatedState(onFinished)

    // Contours are a bonus: if the scene is not parsed yet the dot and wordmark still play.
    val scene by produceState<SplashScene?>(null) {
        value = withContext(Dispatchers.Default) { runCatching { SplashScene.load(context) }.getOrNull() }
    }
    val fonts = remember {
        ResourcesCompat.getFont(context, R.font.strideway_sans_bold_italic) to ResourcesCompat.getFont(context, R.font.outfit_regular)
    }
    val clock = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        clock.animateTo(SplashTimeline.TOTAL.toFloat(), tween((SplashTimeline.TOTAL * 1000).toInt(), easing = LinearEasing))
        finish()
    }

    Spacer(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - SplashTimeline.FADE_OUT.progress(clock.value.toDouble()).toFloat() }
            .semantics { contentDescription = appName }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = skipLabel,
            ) { finish() }
            .drawWithCache {
                val sc = scene
                val wm = Wordmark(appName, tagline, size.width, size.height, fonts.first, fonts.second)
                val dotR = 8.dp.toPx() // same as res/drawable/splash_dot.xml
                val routeMeasure = PathMeasure().apply { sc?.let { setPath(it.route, false) } }
                val trail = Path()
                val ahead = Path()
                val unit = 1.dp.toPx()
                onDrawBehind {
                    val t = clock.value.toDouble()
                    drawRect(colors.bg)
                    val cam = camera(t, sc)
                    if (t < SplashTimeline.MAP_OUT.end) drawMap(t, cam, sc, routeMeasure, trail, ahead, colors.textMuted, colors.text, colors.accent, unit)
                    SplashTimeline.PINGS.forEach { drawPing(t, it, cam.anchor, colors.accent, size.width * 0.52f, 1.2, unit) }
                    drawWordmark(t, wm, colors.text, colors.textMuted, colors.accent, unit)
                    drawRider(t, cam, wm, dotR, colors.bg, colors.accent, unit)
                }
            },
    )
}

private class Camera(val anchor: Offset, val rider: Offset, val riderS: Float, val rotationRad: Float, val scale: Float)

private fun DrawScope.camera(t: Double, scene: SplashScene?): Camera {
    val zoom = 1.05f + 0.10f * Ease.outCubic(Phase(0.0, 2.0).progress(t)).toFloat()
    // The dot starts dead centre (where the system splash put it), then the camera drops it a little lower.
    val anchor = Offset(size.width / 2f, size.height * (0.5f + 0.08f * Ease.inOutCubic(Phase(0.4, 1.6).progress(t)).toFloat()))
    val s = (SplashTimeline.rideProgress(t) * (scene?.routeLength ?: 0f)).toFloat()
    val rider = scene?.pointAt(s) ?: Offset.Zero
    val heading = scene?.headingAt(s) ?: (-PI / 2).toFloat()
    return Camera(anchor, rider, s, (-PI / 2).toFloat() - heading, size.width / SplashScene.WORLD_WIDTH * zoom)
}

private fun DrawScope.drawMap(
    t: Double, cam: Camera, scene: SplashScene?, measure: PathMeasure, trail: Path, ahead: Path,
    muted: Color, text: Color, accent: Color, unit: Float,
) {
    val a = 1f - Ease.inOutCubic(SplashTimeline.MAP_OUT.progress(t)).toFloat()
    if (a <= 0f || scene == null) return
    val k = cam.scale
    fun world(block: DrawScope.() -> Unit) {
        withTransform({
            translate(cam.anchor.x, cam.anchor.y)
            rotate(Math.toDegrees(cam.rotationRad.toDouble()).toFloat(), pivot = Offset.Zero)
            scale(k, k, pivot = Offset.Zero)
            translate(-cam.rider.x, -cam.rider.y)
        }, block)
    }

    // Contours open from the first GPS fix: draw them in a layer and mask it with a growing radial edge.
    val reveal = SplashTimeline.CONTOURS.progress(t).toFloat()
    val drawContours: DrawScope.() -> Unit = {
        drawPath(scene.minor, muted.copy(alpha = 0.24f * a), style = Stroke(width = 0.6f * unit / k))
        drawPath(scene.major, muted.copy(alpha = 0.5f * a), style = Stroke(width = 0.9f * unit / k))
    }
    if (reveal < 1f) {
        drawIntoCanvas { it.saveLayer(Rect(Offset.Zero, size), Paint()) }
        world(drawContours)
        val maxR = hypot(size.width, size.height)
        val r = Ease.outCubic(reveal.toDouble()).toFloat() * maxR + 1f
        val soft = size.width * 0.24f
        drawRect(
            brush = Brush.radialGradient(
                0f to Color.Black, max(0f, (r - soft) / r) to Color.Black, 1f to Color.Transparent,
                center = cam.anchor, radius = r,
            ),
            blendMode = BlendMode.DstIn,
        )
        drawIntoCanvas { it.restore() }
    } else {
        world(drawContours)
    }

    // Route ahead dashed, ridden part solid.
    trail.reset(); ahead.reset()
    measure.getSegment(0f, cam.riderS, trail, true)
    measure.getSegment(cam.riderS, measure.length, ahead, true)
    world {
        drawPath(
            ahead, text.copy(alpha = 0.28f * a),
            style = Stroke(width = unit / k, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f * unit / k, 7f * unit / k))),
        )
        drawPath(trail, accent.copy(alpha = a), style = Stroke(width = 3f * unit / k, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    // The single glow on screen: it marks where the rider is, and goes first when the dot leaves the map.
    val glowR = 40f * unit
    drawCircle(
        Brush.radialGradient(listOf(accent.copy(alpha = 0.38f * a * a * a), Color.Transparent), center = cam.anchor, radius = glowR),
        radius = glowR, center = cam.anchor,
    )
}

private fun DrawScope.drawPing(t: Double, at: Double, center: Offset, accent: Color, maxR: Float, dur: Double, unit: Float) {
    val u = Phase(at, at + dur).progress(t).toFloat()
    if (u <= 0f || u >= 1f) return
    drawCircle(
        accent.copy(alpha = 0.7f * (1f - u)),
        radius = Ease.outCubic(u.toDouble()).toFloat() * maxR, center = center,
        style = Stroke(width = (1f + 2f * (1f - u)) * unit / 1.5f),
    )
}

/** Wordmark geometry, measured once per size: Strideway Sans for the name, Outfit for the tagline. */
private class Wordmark(name: String, val tagline: String, width: Float, height: Float, bold: Typeface?, regular: Typeface?) {
    // The dotless i lets the rider dot become the tittle.
    private val iIndex = name.indexOf('i')
    val text = if (iIndex >= 0) name.replaceRange(iIndex, iIndex + 1, "ı") else name
    val fs = width * 0.18f
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { typeface = bold; textSize = fs }
    val tagPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { typeface = regular; textSize = fs * 0.242f; textAlign = AndroidPaint.Align.CENTER }
    val left: Float
    val right: Float
    val baseline = height * 0.485f
    val lineY = baseline + 0.337f * fs
    val centerX = width / 2f
    val letterX: FloatArray
    val tittle: Offset
    val tittleR: Float
    val hopHeight = width * 0.3f

    init {
        val w = paint.measureText(text)
        left = (width - w) / 2f
        right = left + w
        letterX = FloatArray(text.length) { left + paint.measureText(text, 0, it) }
        val dot = measureTittle(paint)
        if (iIndex >= 0 && dot != null) {
            tittle = Offset(letterX[iIndex] + dot.first, baseline + dot.second)
            tittleR = dot.third
        } else {
            tittle = Offset(width / 2f, baseline - 0.735f * fs)
            tittleR = 0.078f * fs
        }
    }
}

/** Finds the dot of "i" in this face: (centre x from the glyph origin, centre y from the baseline, radius). */
private fun measureTittle(paint: AndroidPaint): Triple<Float, Float, Float>? {
    val b = android.graphics.Rect()
    paint.getTextBounds("i", 0, 1, b)
    if (b.width() <= 0 || b.height() <= 0) return null
    val pad = 2
    val bmp = Bitmap.createBitmap(b.width() + 2 * pad, b.height() + 2 * pad, Bitmap.Config.ALPHA_8)
    AndroidCanvas(bmp).drawText("i", (pad - b.left).toFloat(), (pad - b.top).toFloat(), paint)
    var top = -1
    var bottom = -1
    var minX = Int.MAX_VALUE
    var maxX = -1
    for (y in 0 until bmp.height) {
        var ink = false
        for (x in 0 until bmp.width) {
            if (Color(bmp.getPixel(x, y)).alpha > 0.5f) {
                ink = true; minX = minOf(minX, x); maxX = maxOf(maxX, x)
            }
        }
        if (ink && top < 0) top = y
        if (!ink && top >= 0) { bottom = y - 1; break }
    }
    bmp.recycle()
    if (top < 0 || bottom < top) return null
    val cx = (minX + maxX + 1) / 2f + b.left - pad
    val cy = (top + bottom + 1) / 2f + b.top - pad
    // Half the dot's height: in a slanted face the dot is wider than tall, and a circle of half its width
    // would overhang the stem.
    return Triple(cx, cy, (bottom - top + 1) / 2f)
}

private fun DrawScope.drawWordmark(t: Double, wm: Wordmark, text: Color, muted: Color, accent: Color, unit: Float) {
    val tl = SplashTimeline
    if (t < tl.BASELINE.start) return
    val reveal = tl.revealX(t, wm.left.toDouble(), wm.right.toDouble())
    val runX = if (t < tl.BASELINE.end) reveal.toFloat() else wm.right

    // The route line the dot draws under the word, pulled in toward the right once the dot hops off.
    val lx0 = wm.left + (wm.right - wm.left) * Ease.inOutCubic(tl.UNDERLINE_RETRACT.progress(t)).toFloat()
    if (runX > lx0) drawLine(accent, Offset(lx0, wm.lineY), Offset(runX, wm.lineY), strokeWidth = 3f * unit, cap = StrokeCap.Round)

    drawIntoCanvas { canvas ->
        val nc = canvas.nativeCanvas
        wm.text.forEachIndexed { i, ch ->
            val u = tl.letterReveal(reveal, wm.letterX[i].toDouble(), wm.fs.toDouble()).toFloat()
            if (u <= 0f) return@forEachIndexed
            wm.paint.color = text.copy(alpha = u).toArgb()
            nc.drawText(ch.toString(), wm.letterX[i], wm.baseline + (1f - u) * 0.24f * wm.fs, wm.paint)
        }
        val tg = Ease.outCubic(tl.TAGLINE.progress(t)).toFloat()
        if (tg > 0f) {
            wm.tagPaint.color = muted.copy(alpha = tg).toArgb()
            nc.drawText(wm.tagline, wm.centerX, wm.baseline + 0.84f * wm.fs + (1f - tg) * 0.1f * wm.fs, wm.tagPaint)
        }
    }
}

private class DotPose(val center: Offset, val r: Float, val sx: Float = 1f, val sy: Float = 1f)

private fun dotPose(t: Double, cam: Camera, wm: Wordmark, dotR: Float, rideEndAnchor: Offset): DotPose {
    val tl = SplashTimeline
    return when {
        t < tl.MAP_OUT.start -> DotPose(cam.anchor, dotR)
        t < tl.MAP_OUT.end -> {
            val u = Ease.inOutCubic(tl.MAP_OUT.progress(t)).toFloat()
            val p0 = rideEndAnchor
            val p1 = Offset(wm.left, wm.lineY)
            val c = Offset(p0.x, p0.y + (p1.y - p0.y) * 0.5f + wm.fs)
            val v = 1f - u
            DotPose(p0 * (v * v) + c * (2f * v * u) + p1 * (u * u), dotR)
        }
        t < tl.BASELINE.end -> DotPose(Offset(tl.revealX(t, wm.left.toDouble(), wm.right.toDouble()).toFloat(), wm.lineY), dotR)
        t < tl.HOP.end -> {
            val u = tl.HOP.progress(t)
            val e = Ease.inOutSine(u).toFloat()
            val x = wm.right + (wm.tittle.x - wm.right) * e
            val y = wm.lineY + (wm.tittle.y - wm.lineY) * e - tl.hopLift(u).toFloat() * wm.hopHeight
            DotPose(Offset(x, y), dotR + (wm.tittleR - dotR) * e)
        }
        else -> {
            // Lands with a small squash that settles before the fade.
            val k = tl.LANDING_SETTLE.progress(t)
            val sq = (exp(-k * 9) * cos(k * 18) * 0.28).toFloat() * (1f - k.toFloat())
            DotPose(wm.tittle, wm.tittleR, 1f + sq, 1f - sq)
        }
    }
}

private fun DrawScope.drawRider(t: Double, cam: Camera, wm: Wordmark, dotR: Float, bg: Color, accent: Color, unit: Float) {
    val tl = SplashTimeline
    val rideEndAnchor = camera(tl.RIDE.end, null).anchor
    val pose = dotPose(t, cam, wm, dotR, rideEndAnchor)

    if (t >= tl.HOP.start && t < tl.HOP.end) {
        for (k in 1..7) {
            val tp = t - k * 0.022
            if (tp < tl.HOP.start) break
            val p = dotPose(tp, cam, wm, dotR, rideEndAnchor)
            drawCircle(accent.copy(alpha = 0.5f * (1f - k / 8f)), radius = p.r * (1f - k / 10f), center = p.center)
        }
    }
    if (t >= tl.HOP.end) {
        drawPing(t, tl.HOP.end, wm.tittle, accent, size.width * 0.3f, 0.6, unit)
        drawPing(t, tl.HOP.end + 0.2, wm.tittle, accent, size.width * 0.19f, 0.45, unit)
    }
    val settled = t >= tl.LANDING_SETTLE.end
    withTransform({ scale(pose.sx, pose.sy, pivot = pose.center) }) {
        // A ring in the background colour keeps the dot readable over contours and the route line;
        // once it sits in the word it is just the tittle.
        if (!settled) drawCircle(bg, radius = pose.r + 2f * unit, center = pose.center)
        drawCircle(accent, radius = pose.r, center = pose.center)
    }
}
