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
    val Paper = Color(0xFFF8F7F3)
    val Ink = Color(0xFF252C2C)
    val InkSecondary = Color(0xFF515E61)
    val InkMuted = Color(0xFF5E6874)
    val Forest = Color(0xFF123D35)
    val Jade = Color(0xFF147A68)
    val JadeDark = Color(0xFF125D54)
    val JadeActive = Color(0xFF106859)
    val JadeSoft = Color(0xFFE8F2EC)
    val JadeMuted = Color(0xFF7EACA2)
    val HeaderSurface = Color(0xFFF1F4EE)
    val HeaderInk = Color(0xFF174E46)
    val HeaderSecondary = Color(0xFF46675F)
    val Divider = Color(0xFFE4E4DB)
    val InputOutline = Color(0xFFCDD5CE)
    val QuestionSurface = Color(0xFFF3F6F0)
    val FigureSurface = Color(0xFFFBFCF8)
    val Outline = Color(0xFF7F8994)
    val Track = Color(0xFFE8E5DE)
    val ErrorWarm = Color(0xFFC9543D)
    val ErrorText = Color(0xFFAC3D32)
    val ErrorSurface = Color(0xFFFBF0ED)
    val OnJade = Color.White
    val ContentSurface = Color(0xFFFFFEFB)
    val SubtleSurface = Color(0xFFF0F0E9)
    val ReadingSurface = Color(0xFFF6EFDF)
    val ReadingAccent = Color(0xFF80602E)
    val LearningSurface = Color(0xFFEEF2F6)
    val LearningAccent = Color(0xFF496B92)
    val AgentAccent = Color(0xFF56716C)
    val AgentSurface = Color(0xFFEDF3EF)
    val NotesAccent = Color(0xFF85644E)
    val NotesSurface = Color(0xFFF5EEE6)
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
    val CallToActionRadius = 12.dp
    val SurfaceRadius = 16.dp
    val ChipRadius = 10.dp
    val MaximumContentWidth = 600.dp
}

object SmartIconSizes {
    val Navigation = 22.dp
    val Small = 16.dp
    val Standard = 20.dp
    val Large = 24.dp
}

val SmartTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 34.sp,
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
        lineHeight = 26.sp,
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
    surfaceContainerLow = Color(0xFFFAF9F5),
    surfaceContainer = SubtleSurface,
    surfaceContainerHigh = Color(0xFFEDEAE3),
    surfaceContainerHighest = Color(0xFFE5E1D8),
    surfaceBright = ContentSurface,
    surfaceDim = Color(0xFFE2DED4),
    surfaceTint = Jade,
    inverseSurface = Color(0xFF26323A),
    inverseOnSurface = Color(0xFFF8F7F3),
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
