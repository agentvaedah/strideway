package id.web.agentvaedah.strideway.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import id.web.agentvaedah.strideway.R

/**
 * Outfit (SIL OFL 1.1), bundled so it works offline and on phones without Google Play Services.
 * It stands in for Metara until a licence is bought; swapping means replacing the four files in res/font.
 * Metric numerals use tabular figures so values do not jitter while they tick.
 */
val Outfit = FontFamily(
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
    Font(R.font.outfit_bold, FontWeight.Bold),
)

/**
 * Strideway Sans Bold Italic (tools/font/build_font.py): the brand face, cut at 45 degrees and slanted 12 degrees
 * like the logo. It has one weight and a basic character set, so it carries headings and big numbers only;
 * Outfit keeps running text readable.
 */
val StridewaySans = FontFamily(Font(R.font.strideway_sans_bold_italic, FontWeight.Bold, FontStyle.Italic))

val StridewayTypography = Typography(
    headlineLarge = TextStyle(fontFamily = StridewaySans, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, fontSize = 30.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = StridewaySans, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Outfit, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Outfit, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Outfit, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

object MetricText {
    private const val TABULAR = "tnum"

    val Huge = TextStyle(fontFamily = StridewaySans, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, fontSize = 52.sp, lineHeight = 56.sp, fontFeatureSettings = TABULAR)
    val Large = TextStyle(fontFamily = StridewaySans, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, fontSize = 40.sp, lineHeight = 44.sp, fontFeatureSettings = TABULAR)
    val Medium = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 30.sp, fontFeatureSettings = TABULAR)
    val Small = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 22.sp, fontFeatureSettings = TABULAR)
    val Label = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 14.sp)
}
