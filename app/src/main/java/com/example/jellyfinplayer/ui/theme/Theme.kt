package com.example.jellyfinplayer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Fjora brand palette: deep blue-purple base with muted orange and purple
// accents — the logo gradient made subtle and cinematic.
private val Background = Color(0xFF05060D)
private val Surface = Color(0xFF090B18)
private val SurfaceElevated = Color(0xFF0F1228)
private val OnSurface = Color(0xFFDDDAF2)       // soft lavender-white
private val OnSurfaceMuted = Color(0xFF8886A6)
private val Outline = Color(0xFF262944)

private val Accent = Color(0xFFBF5820)          // muted burnt orange
private val AccentVariant = Color(0xFF6B44B8)   // deep muted purple
private val ErrorRed = Color(0xFFD05050)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF301400),
    onPrimaryContainer = Color(0xFFFFD8C0),
    secondary = AccentVariant,
    onSecondary = Color(0xFF18083A),
    secondaryContainer = Color(0xFF280E55),
    onSecondaryContainer = Color(0xFFD8CCFF),
    background = Background,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = OnSurfaceMuted,
    outline = Outline,
    outlineVariant = Color(0xFF1E2138),
    error = ErrorRed
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    displayMedium = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    headlineLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    // Always dark — matches Findroid's "always cinema mode" feel.
    MaterialTheme(
        colorScheme = DarkColors,
        shapes = AppShapes,
        typography = AppTypography,
        content = content
    )
}
