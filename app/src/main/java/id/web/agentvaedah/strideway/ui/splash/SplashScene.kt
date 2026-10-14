package id.web.agentvaedah.strideway.ui.splash

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import id.web.agentvaedah.strideway.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class SceneFile(
    val routeStep: Float,
    val route: List<Float>,
    val heading: List<Float>,
    val minor: List<List<Int>>,
    val major: List<List<Int>>,
)

/**
 * Contours and the first climb of the splash video, exported by tools/motion-splash/export-scene.mjs.
 * Coordinates are world units: at zoom 1 the screen is [WORLD_WIDTH] units wide.
 */
class SplashScene private constructor(
    val minor: Path,
    val major: Path,
    val route: Path,
    private val routeX: FloatArray,
    private val routeY: FloatArray,
    private val heading: FloatArray,
    private val step: Float,
) {
    val routeLength: Float get() = step * (routeX.size - 1)

    fun pointAt(s: Float): Offset {
        val (i, u) = index(s)
        return Offset(routeX[i] + (routeX[i + 1] - routeX[i]) * u, routeY[i] + (routeY[i + 1] - routeY[i]) * u)
    }

    /** Smoothed direction of travel in radians, so the camera banks instead of snapping. */
    fun headingAt(s: Float): Float {
        val (i, u) = index(s)
        return heading[i] + (heading[i + 1] - heading[i]) * u
    }

    private fun index(s: Float): Pair<Int, Float> {
        val f = (s / step).coerceIn(0f, routeX.size - 1.001f)
        val i = f.toInt()
        return i to (f - i)
    }

    companion object {
        const val WORLD_WIDTH = 1080f
        private val json = Json { ignoreUnknownKeys = true }

        fun load(context: Context): SplashScene {
            val text = context.resources.openRawResource(R.raw.splash_scene).bufferedReader().use { it.readText() }
            val file = json.decodeFromString(SceneFile.serializer(), text)
            val xs = FloatArray(file.route.size / 2) { file.route[2 * it] }
            val ys = FloatArray(file.route.size / 2) { file.route[2 * it + 1] }
            val route = Path().apply {
                moveTo(xs[0], ys[0])
                for (i in 1 until xs.size) lineTo(xs[i], ys[i])
            }
            return SplashScene(
                minor = smoothed(file.minor),
                major = smoothed(file.major),
                route = route,
                routeX = xs,
                routeY = ys,
                heading = file.heading.toFloatArray(),
                step = file.routeStep,
            )
        }

        // The export is simplified to keep it small; curving through segment midpoints hides the corners.
        private fun smoothed(lines: List<List<Int>>): Path = Path().apply {
            for (l in lines) {
                val n = l.size / 2
                if (n < 2) continue
                moveTo(l[0].toFloat(), l[1].toFloat())
                for (i in 1 until n - 1) {
                    val x = l[2 * i].toFloat()
                    val y = l[2 * i + 1].toFloat()
                    quadraticBezierTo(x, y, (x + l[2 * i + 2]) / 2f, (y + l[2 * i + 3]) / 2f)
                }
                lineTo(l[2 * n - 2].toFloat(), l[2 * n - 1].toFloat())
            }
        }
    }
}
