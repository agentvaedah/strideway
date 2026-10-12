package id.web.agentvaedah.strideway.ui.theme

import androidx.compose.ui.graphics.Color

// Dark is fixed: the phone lives on a top tube, often at dawn or night, where a bright UI glares.
object Palette {
    val Bg = Color(0xFF0E1012)
    val Surface = Color(0xFF171A1D)
    val SurfaceHigh = Color(0xFF22262A)
    val Outline = Color(0xFF353A3F)
    val Text = Color(0xFFF2F4F5)
    val TextMuted = Color(0xFFA9B0B6)
    val Accent = Color(0xFFFC4C02)
    val OnAccent = Color(0xFF0E1012)
    val Info = Color(0xFF4FA3E0)
    val Success = Color(0xFF5BC27A)
    val Warning = Color(0xFFF2B53A)
    val Error = Color(0xFFF0524F)

    val HrZones = listOf(
        Color(0xFF7D868E),
        Color(0xFF4FA3E0),
        Color(0xFF5BC27A),
        Color(0xFFF2B53A),
        Color(0xFFF0524F),
    )

    /** Zone 0 means below Z1 and uses the muted text colour. */
    fun hrZone(zone: Int): Color = if (zone in 1..5) HrZones[zone - 1] else TextMuted
}
