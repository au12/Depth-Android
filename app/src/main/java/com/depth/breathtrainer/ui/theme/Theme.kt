package com.depth.breathtrainer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.depth.breathtrainer.R

val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold)
)

/** Tabular figures, so a ticking countdown doesn't shift sideways as digits change. */
const val TABULAR = "tnum"

private val DepthColors = darkColorScheme(
    background = Abyss,
    surface = Surface,
    surfaceVariant = SurfaceHigh,
    surfaceContainer = Surface,
    surfaceContainerHigh = SurfaceHigh,
    surfaceContainerLow = Deep,
    primary = Palettes.Breathe.accent,
    onPrimary = Palettes.Breathe.onAccent,
    secondary = Palettes.Prep.accent,
    error = Warn,
    onBackground = Ink,
    onSurface = Ink,
    onSurfaceVariant = InkMuted,
    outline = Hairline,
    outlineVariant = Hairline
)

private fun base(size: Int, weight: FontWeight, spacing: Float = 0f) = TextStyle(
    fontFamily = Manrope, fontWeight = weight, fontSize = size.sp, letterSpacing = spacing.sp
)

private val DepthType = Typography(
    displayLarge = base(57, FontWeight.ExtraBold),
    headlineLarge = base(32, FontWeight.ExtraBold),
    headlineMedium = base(28, FontWeight.Bold),
    titleLarge = base(22, FontWeight.ExtraBold),
    titleMedium = base(16, FontWeight.Bold),
    titleSmall = base(14, FontWeight.SemiBold),
    bodyLarge = base(16, FontWeight.Medium),
    bodyMedium = base(14, FontWeight.Medium),
    bodySmall = base(12, FontWeight.Medium),
    labelLarge = base(15, FontWeight.Bold, 0.2f),
    labelMedium = base(12, FontWeight.SemiBold, 0.4f),
    labelSmall = base(11, FontWeight.Bold, 1.2f)
)

@Composable
fun DepthTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DepthColors, typography = DepthType, content = content)
}
