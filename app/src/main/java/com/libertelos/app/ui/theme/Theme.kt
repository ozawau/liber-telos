package com.libertelos.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val InkTeal = Color(0xFF0D4F4F)
val SoftStone = Color(0xFFF2F4F7)
val PaperWhite = Color(0xFFFBFCFD)
val SlateInk = Color(0xFF1C2430)
val MutedSlate = Color(0xFF5B6B8C)
val AmberOut = Color(0xFFE07A3D)
val SeaIn = Color(0xFF0F766E)
val TransferBlue = Color(0xFF2F6FED)
val ReimburseAmber = Color(0xFFB45309)
val LineSoft = Color(0xFFD8DEE8)

private val LightColors = lightColorScheme(
    primary = InkTeal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7EBE8),
    onPrimaryContainer = InkTeal,
    secondary = AmberOut,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE6D6),
    onSecondaryContainer = Color(0xFF6B3410),
    tertiary = SeaIn,
    background = SoftStone,
    onBackground = SlateInk,
    surface = PaperWhite,
    onSurface = SlateInk,
    surfaceVariant = Color(0xFFE8EDF3),
    onSurfaceVariant = MutedSlate,
    outline = LineSoft,
    error = Color(0xFFB42318)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FCFC6),
    onPrimary = Color(0xFF003734),
    primaryContainer = Color(0xFF0D4F4F),
    onPrimaryContainer = Color(0xFFD7EBE8),
    secondary = Color(0xFFFFB485),
    onSecondary = Color(0xFF4A2300),
    background = Color(0xFF12181F),
    onBackground = Color(0xFFE8EDF3),
    surface = Color(0xFF1A222C),
    onSurface = Color(0xFFE8EDF3),
    surfaceVariant = Color(0xFF2A3441),
    onSurfaceVariant = Color(0xFFB7C2D0),
    outline = Color(0xFF3D4A5A)
)

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)

@Composable
fun LiberTelosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
