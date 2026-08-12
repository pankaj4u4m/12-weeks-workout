package com.personal.twelveweek.web

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The same "Resistance Band Flow" design system as the Android app (see
 * DESIGN.md / app/src/main/java/com/personal/twelveweek/Theme.kt) — kept as
 * a standalone copy in this `web` package, rather than sharing Android's
 * Theme.kt directly, because that file is androidMain-only (custom Barlow
 * font resource) and lives in a package already fully owned by the Android
 * screens. Duplicating the color/shape/type tokens here keeps the web build
 * from ever touching MainActivity's package.
 */
val WebBandBlue = Color(0xFF315CFF)
val WebBandCoral = Color(0xFFFF5B38)
val WebBandMint = Color(0xFFB9E6D0)
private val TrainingInk = Color(0xFF17211D)
private val Daylight = Color(0xFFF4F5EF)
private val DarkGround = Color(0xFF111714)

private val LightColors = lightColorScheme(
    primary = WebBandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE4FF),
    onPrimaryContainer = Color(0xFF10205F),
    secondary = WebBandCoral,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDED5),
    onSecondaryContainer = Color(0xFF5B1607),
    tertiary = Color(0xFF247255),
    onTertiary = Color.White,
    tertiaryContainer = WebBandMint,
    onTertiaryContainer = Color(0xFF073824),
    background = Daylight,
    onBackground = TrainingInk,
    surface = Color.White,
    onSurface = TrainingInk,
    surfaceVariant = Color(0xFFE6ECE7),
    onSurfaceVariant = Color(0xFF4A5751),
    outline = Color(0xFF748079),
    outlineVariant = Color(0xFFC6CEC8),
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB6C4FF),
    onPrimary = Color(0xFF00258C),
    primaryContainer = Color(0xFF183FAF),
    onPrimaryContainer = Color(0xFFDCE4FF),
    secondary = Color(0xFFFFB5A2),
    onSecondary = Color(0xFF6B1B08),
    secondaryContainer = Color(0xFF8D2E17),
    onSecondaryContainer = Color(0xFFFFDBD1),
    tertiary = Color(0xFF9DD5BA),
    onTertiary = Color(0xFF003825),
    tertiaryContainer = Color(0xFF15523C),
    onTertiaryContainer = Color(0xFFB9F1D5),
    background = DarkGround,
    onBackground = Color(0xFFE5EDE7),
    surface = Color(0xFF18201C),
    onSurface = Color(0xFFE5EDE7),
    surfaceVariant = Color(0xFF27312C),
    onSurfaceVariant = Color(0xFFC1CBC4),
    outline = Color(0xFF89958D),
    outlineVariant = Color(0xFF3C4841),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val BaseTypography = Typography()

// lc-debt: headline/title faces use the platform default font family at
// SemiBold weight instead of the Android app's Barlow Semi Condensed —
// Compose Multiplatform needs a byte-array Font() load per target
// (androidMain: resources, wasmJs: fetch + Font(data)) to share one real
// custom face across both targets. Upgrade path: a commonMain
// expect/actual FontFamily loader backed by compose.components.resources.
private val WebTypography = BaseTypography.copy(
    displayLarge = BaseTypography.displayLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-1.2).sp),
    displayMedium = BaseTypography.displayMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp),
    headlineLarge = BaseTypography.headlineLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    headlineMedium = BaseTypography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = BaseTypography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = BaseTypography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = BaseTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = BaseTypography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = BaseTypography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
)

private val WebShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun TwelveWeekWebTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = WebTypography,
        shapes = WebShapes,
        content = content
    )
}
