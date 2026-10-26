package com.tingyun.smartmistakebook.core.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The single, paper-like palette used by the visual references. */
object SmartColors {
    val Paper = Color(0xFFF7F6F2)
    val Ink = Color(0xFF151515)
    val InkSecondary = Color(0xFF525252)
    val InkMuted = Color(0xFF87919B)
    val Jade = Color(0xFF23704A)
    val JadeDark = Color(0xFF286647)
    val JadeActive = Color(0xFF087046)
    val JadeSoft = Color(0xFFDDE7E0)
    val JadeMuted = Color(0xFF83B39A)
    val Divider = Color(0xFFD8DAD8)
    val Outline = Color(0xFFC8CED1)
    val Track = Color(0xFFE2E0DE)
    val ErrorWarm = Color(0xFFE36F27)
    val ErrorText = Color(0xFFA23E24)
    val OnJade = Color(0xFFFFFDFC)
    val ContentSurface = Color.White
    val SubtleSurface = Color(0xFFF0EFEC)
}

val Paper = SmartColors.Paper
val Ink = SmartColors.Ink
val InkSecondary = SmartColors.InkSecondary
val InkMuted = SmartColors.InkMuted
val Jade = SmartColors.Jade
val JadeDark = SmartColors.JadeDark
val JadeActive = SmartColors.JadeActive
val JadeSoft = SmartColors.JadeSoft
val JadeMuted = SmartColors.JadeMuted
val Divider = SmartColors.Divider
val Outline = SmartColors.Outline
val Track = SmartColors.Track
val ErrorWarm = SmartColors.ErrorWarm
val ErrorText = SmartColors.ErrorText
val OnJade = SmartColors.OnJade
val ContentSurface = SmartColors.ContentSurface
val SubtleSurface = SmartColors.SubtleSurface

object SmartDimens {
    val ContentHorizontalPadding = 16.dp
    val MinimumTouchTarget = 48.dp
    val CallToActionRadius = 12.dp
    val SurfaceRadius = 12.dp
    val ChipRadius = 8.dp
    val MaximumContentWidth = 600.dp
}

val SmartTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.2).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 24.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
)

val SmartShapes = Shapes(
    extraSmall = RoundedCornerShape(SmartDimens.ChipRadius),
    small = RoundedCornerShape(SmartDimens.SurfaceRadius),
    medium = RoundedCornerShape(SmartDimens.SurfaceRadius),
    large = RoundedCornerShape(SmartDimens.CallToActionRadius),
    extraLarge = RoundedCornerShape(16.dp),
)

private val SmartLightColorScheme = lightColorScheme(
    primary = Jade,
    onPrimary = OnJade,
    primaryContainer = JadeSoft,
    onPrimaryContainer = Ink,
    secondary = JadeDark,
    onSecondary = OnJade,
    secondaryContainer = JadeSoft,
    onSecondaryContainer = Ink,
    tertiary = JadeMuted,
    onTertiary = Ink,
    background = Paper,
    onBackground = Ink,
    surface = ContentSurface,
    onSurface = Ink,
    surfaceVariant = SubtleSurface,
    onSurfaceVariant = InkSecondary,
    surfaceContainerLowest = ContentSurface,
    surfaceContainerLow = Paper,
    surfaceContainer = SubtleSurface,
    surfaceContainerHigh = ContentSurface,
    surfaceContainerHighest = SubtleSurface,
    error = ErrorText,
    onError = OnJade,
    errorContainer = Color(0xFFFBEAE4),
    onErrorContainer = ErrorText,
    outline = Outline,
    outlineVariant = Divider,
    scrim = Ink,
)

@Composable
fun SmartMistakeBookTheme(content: @Composable () -> Unit) {
    ClaudeMotionTheme {
    MaterialTheme(
        colorScheme = SmartLightColorScheme,
        typography = SmartTypography,
        shapes = SmartShapes,
        content = content,
    )
    }
}
