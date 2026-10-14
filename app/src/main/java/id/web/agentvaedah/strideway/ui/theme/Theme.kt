package id.web.agentvaedah.strideway.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import id.web.agentvaedah.strideway.data.prefs.ThemeMode

private fun scheme(c: StridewayColors): ColorScheme {
    val base = if (c.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c.accent,
        onPrimary = c.onAccent,
        primaryContainer = c.surfaceHigh,
        onPrimaryContainer = c.text,
        secondary = c.info,
        onSecondary = c.surface,
        tertiary = c.success,
        background = c.bg,
        onBackground = c.text,
        surface = c.surface,
        onSurface = c.text,
        surfaceVariant = c.surfaceHigh,
        onSurfaceVariant = c.textMuted,
        surfaceContainer = c.surface,
        surfaceContainerHigh = c.surfaceHigh,
        surfaceContainerHighest = c.surfaceHigh,
        surfaceContainerLow = c.surface,
        surfaceContainerLowest = c.bg,
        outline = c.outline,
        outlineVariant = c.outline,
        error = c.error,
        onError = c.surface,
    )
}

private val DarkScheme = scheme(DarkColors)
private val LightScheme = scheme(LightColors)

// Small radius for data surfaces, a larger one only for sheets: cards read as panels, not pills.
private val StridewayShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun StridewayTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = isDarkTheme(mode)
    CompositionLocalProvider(LocalStridewayColors provides if (dark) DarkColors else LightColors) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = StridewayTypography,
            shapes = StridewayShapes,
            content = content,
        )
    }
}
