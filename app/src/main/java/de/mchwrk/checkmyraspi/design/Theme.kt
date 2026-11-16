package de.mchwrk.checkmyraspi.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * MCHWRK-Designsystem, abgeleitet aus mchwrk.de: warmes Creme/Anthrazit,
 * eine orange Akzentfarbe, dünne Ränder statt Schatten, kräftige Headlines.
 */
object MchwrkPalette {
    val LightBg = Color(0xFFFAF8F5)
    val LightSurface = Color(0xFFFFFFFF)
    val LightText = Color(0xFF1C1B19)
    val LightMuted = Color(0xFF6B6760)
    val LightLine = Color(0xFFE7E2DA)
    val LightAccent = Color(0xFFE8590C)
    val LightAccentSoft = Color(0xFFFDEEE4)
    val LightGood = Color(0xFF1A7F45)
    val LightWarn = Color(0xFFB45309)

    val DarkBg = Color(0xFF141312)
    val DarkSurface = Color(0xFF1D1C1A)
    val DarkText = Color(0xFFF2EFEA)
    val DarkMuted = Color(0xFFA29D94)
    val DarkLine = Color(0xFF2F2D2A)
    val DarkAccent = Color(0xFFFF7A33)
    val DarkAccentSoft = Color(0xFF2E1E14)
    val DarkGood = Color(0xFF6EE3A0)
    val DarkWarn = Color(0xFFFBBF24)
}

/** Zusätzliche Markenfarben, die Material 3 nicht abbildet. */
@Immutable
data class MchwrkColors(
    val line: Color,
    val muted: Color,
    val good: Color,
    val warn: Color,
    val accentSoft: Color,
)

val LocalMchwrkColors = staticCompositionLocalOf {
    MchwrkColors(MchwrkPalette.LightLine, MchwrkPalette.LightMuted, MchwrkPalette.LightGood, MchwrkPalette.LightWarn, MchwrkPalette.LightAccentSoft)
}

private val LightScheme: ColorScheme = with(MchwrkPalette) {
    lightColorScheme(
        primary = LightAccent, onPrimary = Color.White,
        primaryContainer = LightAccentSoft, onPrimaryContainer = Color(0xFF7A2E04),
        secondary = LightText, onSecondary = LightBg,
        secondaryContainer = Color(0xFFF1ECE5), onSecondaryContainer = LightText,
        background = LightBg, onBackground = LightText,
        surface = LightSurface, onSurface = LightText,
        surfaceVariant = Color(0xFFF3EFE9), onSurfaceVariant = LightMuted,
        surfaceContainer = LightBg, surfaceContainerLow = LightSurface, surfaceContainerHigh = LightSurface,
        surfaceContainerLowest = LightSurface, surfaceContainerHighest = Color(0xFFF3EFE9),
        // Kein Elevation-Tint: Flächen bleiben neutral wie auf mchwrk.de.
        surfaceTint = LightSurface,
        outline = LightLine, outlineVariant = LightLine,
        error = Color(0xFFB42318), onError = Color.White,
        inverseSurface = LightText, inverseOnSurface = LightBg, inversePrimary = DarkAccent,
        scrim = Color.Black,
    )
}

private val DarkScheme: ColorScheme = with(MchwrkPalette) {
    darkColorScheme(
        primary = DarkAccent, onPrimary = Color(0xFF1C0E05),
        primaryContainer = DarkAccentSoft, onPrimaryContainer = Color(0xFFFFC9A8),
        secondary = DarkText, onSecondary = DarkBg,
        secondaryContainer = Color(0xFF2A2826), onSecondaryContainer = DarkText,
        background = DarkBg, onBackground = DarkText,
        surface = DarkSurface, onSurface = DarkText,
        surfaceVariant = Color(0xFF26241F), onSurfaceVariant = DarkMuted,
        surfaceContainer = DarkBg, surfaceContainerLow = DarkSurface, surfaceContainerHigh = DarkSurface,
        surfaceContainerLowest = DarkBg, surfaceContainerHighest = Color(0xFF26241F),
        surfaceTint = DarkSurface,
        outline = DarkLine, outlineVariant = DarkLine,
        error = Color(0xFFFF8A80), onError = Color.Black,
        inverseSurface = DarkText, inverseOnSurface = DarkBg, inversePrimary = LightAccent,
        scrim = Color.Black,
    )
}

/** Monospace wie die URLs auf mchwrk.de. */
val MonoFamily: FontFamily = FontFamily.Monospace

private val base = Typography()

private val MchwrkTypography = Typography(
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.04).em),
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.04).em),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.03).em),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.03).em),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.01).em),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge,
    bodyMedium = base.bodyMedium,
    bodySmall = base.bodySmall,
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.SemiBold),
    // Abschnittsköpfe: Uppercase mit weitem Tracking (mchwrk .section-head h2).
    labelSmall = base.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.12.em, fontSize = 11.sp),
)

private val MchwrkShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun MchwrkTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val extra = if (dark) {
        with(MchwrkPalette) { MchwrkColors(DarkLine, DarkMuted, DarkGood, DarkWarn, DarkAccentSoft) }
    } else {
        with(MchwrkPalette) { MchwrkColors(LightLine, LightMuted, LightGood, LightWarn, LightAccentSoft) }
    }
    CompositionLocalProvider(LocalMchwrkColors provides extra) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = MchwrkTypography,
            shapes = MchwrkShapes,
            content = content,
        )
    }
}

object Mchwrk {
    val colors: MchwrkColors
        @Composable get() = LocalMchwrkColors.current
}
