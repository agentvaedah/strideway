package id.web.agentvaedah.strideway.ui.motion

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Shared timings, so screens move with one rhythm. */
object Motion {
    const val SHORT = 180
    const val MEDIUM = 320
    const val LONG = 520
    const val STAGGER = 60

    /** Settles quickly with a hint of overshoot; used for presses and cards arriving. */
    fun <T> soft() = spring<T>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
    fun <T> enter() = tween<T>(LONG, easing = FastOutSlowInEasing)
}

/** True when the phone's animator scale is zero; every effect here then jumps straight to its end. */
@Composable
fun reduceMotion(): Boolean {
    val context = LocalContext.current
    return remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/** Shrinks slightly while pressed, so a tap feels physical. Works alongside clickable, does not consume the touch. */
fun Modifier.pressScale(pressed: Float = 0.97f): Modifier = composed {
    val reduce = reduceMotion()
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    this
        .pointerInput(reduce) {
            if (reduce) return@pointerInput
            awaitPointerEventScope {
                while (true) {
                    val down = awaitPointerEvent()
                    if (down.changes.any { it.pressed }) {
                        scope.launch { scale.animateTo(pressed, Motion.soft()) }
                        do {
                            val e = awaitPointerEvent()
                        } while (e.changes.any { it.pressed })
                        scope.launch { scale.animateTo(1f, Motion.soft()) }
                    }
                }
            }
        }
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
}

/** Fades and rises into place, each [index] a beat after the one before; lists feel like they arrive in order. */
fun Modifier.staggeredEnter(index: Int): Modifier = composed {
    val reduce = reduceMotion()
    val progress = remember { Animatable(if (reduce) 1f else 0f) }
    val rise = with(LocalDensity.current) { 16.dp.toPx() }
    LaunchedEffect(Unit) {
        if (!reduce) progress.animateTo(1f, tween(Motion.LONG, delayMillis = (index.coerceAtMost(8)) * Motion.STAGGER, easing = FastOutSlowInEasing))
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * rise
    }
}

/** A number that counts to its new value instead of jumping; [format] turns the animated value into text. */
@Composable
fun AnimatedNumber(value: Double, format: (Double) -> String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val reduce = reduceMotion()
    val shown by animateFloatAsState(value.toFloat(), if (reduce) tween(0) else tween(Motion.LONG + 300, easing = FastOutSlowInEasing), label = "number")
    Row(modifier) { Text(format(shown.toDouble()), style = style, color = color, maxLines = 1) }
}
