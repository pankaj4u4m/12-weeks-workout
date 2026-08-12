package com.personal.twelveweek

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val BandBlue = Color(0xFF315CFF)
val BandCoral = Color(0xFFFF5B38)
val BandMint = Color(0xFFB9E6D0)
val TrainingInk = Color(0xFF17211D)
val Daylight = Color(0xFFF4F5EF)
val DarkGround = Color(0xFF111714)

private val LightColors = lightColorScheme(
    primary = BandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE4FF),
    onPrimaryContainer = Color(0xFF10205F),
    secondary = BandCoral,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDED5),
    onSecondaryContainer = Color(0xFF5B1607),
    tertiary = Color(0xFF247255),
    onTertiary = Color.White,
    tertiaryContainer = BandMint,
    onTertiaryContainer = Color(0xFF073824),
    background = Daylight,
    onBackground = TrainingInk,
    surface = Color(0xFFFFFFFF),
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

private val BarlowSemiCondensed = FontFamily(
    Font(
        resId = R.font.barlow_semicondensed_semibold,
        weight = FontWeight.SemiBold
    )
)

private val BaseTypography = Typography()

private val TwelveWeekTypography = BaseTypography.copy(
    displayLarge = TextStyle(
        fontFamily = BarlowSemiCondensed,
        fontWeight = FontWeight.SemiBold,
        fontSize = 64.sp,
        lineHeight = 64.sp,
        letterSpacing = (-1.2).sp
    ),
    displayMedium = TextStyle(
        fontFamily = BarlowSemiCondensed,
        fontWeight = FontWeight.SemiBold,
        fontSize = 48.sp,
        lineHeight = 50.sp,
        letterSpacing = (-0.8).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = BarlowSemiCondensed,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = BarlowSemiCondensed,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 34.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = BarlowSemiCondensed,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 30.sp
    ),
    titleLarge = TextStyle(
        fontFamily = BarlowSemiCondensed,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 28.sp
    ),
    titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = BaseTypography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = BaseTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = BaseTypography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = BaseTypography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
)

private val TwelveWeekShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = TwelveWeekTypography,
        shapes = TwelveWeekShapes,
        content = content
    )
}
