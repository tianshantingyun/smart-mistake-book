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

/** Neutral reading surfaces, a teal brand, and distinct semantic accents. */
object SmartColors {
    val Paper = Color(0xFFF6F7F9)
    val Ink = Color(0xFF242A32)
    val InkSecondary = Color(0xFF56616F)
    val InkMuted = Color(0xFF5E6874)
    val Forest = Color(0xFF123D35)
    val Jade = Color(0xFF087F70)
    val JadeDark = Color(0xFF125D54)
    val JadeActive = Color(0xFF06695F)
    val JadeSoft = Color(0xFFE5F2EE)
    val JadeMuted = Color(0xFF7EACA2)
    val HeaderSurface = Color(0xFFEAF2EF)
    val HeaderInk = Color(0xFF174E46)
    val HeaderSecondary = Color(0xFF46675F)
    val Divider = Color(0xFFE2E6EB)
    val Outline = Color(0xFF7F8994)
    val Track = Color(0xFFE6EAEE)
    val ErrorWarm = Color(0xFFC9543D)
    val ErrorText = Color(0xFFAC3D32)
    val ErrorSurface = Color(0xFFFBF0ED)
    val OnJade = Color.White
    val ContentSurface = Color.White
    val SubtleSurface = Color(0xFFF0F2F5)
    val ReadingSurface = Color(0xFFF7F2E9)
    val ReadingAccent = Color(0xFF765C3B)
    val LearningSurface = Color(0xFFEDF2FA)
    val LearningAccent = Color(0xFF435F88)
    val PendingSurface = Color(0xFFFCF3E3)
    val PendingAccent = Color(0xFF805A18)
    val CodeSurface = Color(0xFF20262F)
    val CodeHeader = Color(0xFF2B333F)
    val OnCode = Color(0xFFE6EBF1)
    val StarMastered = Color(0xFF82DFBB)
    val StarLearning = Color(0xFFA9C5F0)
    val StarConflicted = Color(0xFFF4CD83)
    val StarStale = Color(0xFFDAB999)
    val StarUnknown = Color(0xFFB1BAC6)
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
    val CallToActionRadius = 16.dp
    val SurfaceRadius = 20.dp
    val ChipRadius = 12.dp
    val MaximumContentWidth = 600.dp
}

object SmartIconSizes {
    val Small = 16.dp
    val Standard = 20.dp
    val Large = 24.dp
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
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.2).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
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
        fontSize = 16.sp,
        lineHeight = 24.sp,
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
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(SmartDimens.SurfaceRadius),
    large = RoundedCornerShape(SmartDimens.CallToActionRadius),
    extraLarge = RoundedCornerShape(24.dp),
)

internal val SmartLightColorScheme = lightColorScheme(
    primary = Jade,
    onPrimary = OnJade,
    primaryContainer = JadeSoft,
    onPrimaryContainer = Ink,
    secondary = SmartColors.LearningAccent,
    onSecondary = OnJade,
    secondaryContainer = SmartColors.LearningSurface,
    onSecondaryContainer = SmartColors.LearningAccent,
    tertiary = SmartColors.ReadingAccent,
    onTertiary = OnJade,
    tertiaryContainer = SmartColors.ReadingSurface,
    onTertiaryContainer = SmartColors.ReadingAccent,
    background = Paper,
    onBackground = Ink,
    surface = ContentSurface,
    onSurface = Ink,
    surfaceVariant = SubtleSurface,
    onSurfaceVariant = InkSecondary,
    surfaceContainerLowest = ContentSurface,
    surfaceContainerLow = Color(0xFFFAFBFC),
    surfaceContainer = SubtleSurface,
    surfaceContainerHigh = Color(0xFFEBEEF2),
    surfaceContainerHighest = Color(0xFFE3E8EE),
    surfaceBright = ContentSurface,
    surfaceDim = Color(0xFFE1E6EC),
    surfaceTint = Jade,
    inverseSurface = Color(0xFF26323A),
    inverseOnSurface = Color(0xFFF5F7FA),
    inversePrimary = Color(0xFF91D8CA),
    error = ErrorText,
    onError = OnJade,
    errorContainer = SmartColors.ErrorSurface,
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
