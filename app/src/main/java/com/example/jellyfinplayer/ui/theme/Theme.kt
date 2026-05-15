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
import com.example.jellyfinplayer.data.AppBackgroundColor
import com.example.jellyfinplayer.data.AppThemeColor

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

private fun accentFor(color: AppThemeColor): Color = when (color) {
    AppThemeColor.FJORA -> Accent
    AppThemeColor.MONOCHROME -> Color.White
    AppThemeColor.PURPLE -> Color(0xFF7A5AC8)
    AppThemeColor.TEAL -> Color(0xFF3D9A93)
    AppThemeColor.BLUE -> Color(0xFF587DAE)
    AppThemeColor.ROSE -> Color(0xFFA84F66)
    AppThemeColor.GREEN -> Color(0xFF7C8F56)
}

private fun primaryContainerFor(color: AppThemeColor): Color = when (color) {
    AppThemeColor.FJORA -> Color(0xFF301400)
    AppThemeColor.MONOCHROME -> Color(0xFF242424)
    AppThemeColor.PURPLE -> Color(0xFF20173A)
    AppThemeColor.TEAL -> Color(0xFF0B2B2A)
    AppThemeColor.BLUE -> Color(0xFF111F34)
    AppThemeColor.ROSE -> Color(0xFF32131C)
    AppThemeColor.GREEN -> Color(0xFF202812)
}

private data class BackgroundPalette(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val outline: Color
)

private fun backgroundPaletteFor(color: AppBackgroundColor): BackgroundPalette = when (color) {
    AppBackgroundColor.FJORA -> BackgroundPalette(
        background = Background,
        surface = Surface,
        surfaceElevated = SurfaceElevated,
        outline = Outline
    )
    AppBackgroundColor.TRUE_BLACK -> BackgroundPalette(
        background = Color.Black,
        surface = Color.Black,
        surfaceElevated = Color(0xFF101010),
        outline = Color(0xFF2A2A2A)
    )
    AppBackgroundColor.CHARCOAL -> BackgroundPalette(
        background = Color(0xFF070809),
        surface = Color(0xFF0D0F11),
        surfaceElevated = Color(0xFF14171A),
        outline = Color(0xFF2A2F35)
    )
    AppBackgroundColor.MIDNIGHT -> BackgroundPalette(
        background = Color(0xFF030812),
        surface = Color(0xFF07101D),
        surfaceElevated = Color(0xFF0D1A2B),
        outline = Color(0xFF20304A)
    )
    AppBackgroundColor.AUBERGINE -> BackgroundPalette(
        background = Color(0xFF0C0610),
        surface = Color(0xFF130A19),
        surfaceElevated = Color(0xFF1D1025),
        outline = Color(0xFF34213F)
    )
    AppBackgroundColor.FOREST -> BackgroundPalette(
        background = Color(0xFF050A07),
        surface = Color(0xFF09120D),
        surfaceElevated = Color(0xFF101B14),
        outline = Color(0xFF223226)
    )
    AppBackgroundColor.ESPRESSO -> BackgroundPalette(
        background = Color(0xFF0B0705),
        surface = Color(0xFF130D09),
        surfaceElevated = Color(0xFF1D140E),
        outline = Color(0xFF34271C)
    )
}

private fun darkColors(
    themeColor: AppThemeColor,
    backgroundColor: AppBackgroundColor
) = backgroundPaletteFor(backgroundColor).let { bg ->
    val monochrome = themeColor == AppThemeColor.MONOCHROME
    darkColorScheme(
        primary = accentFor(themeColor),
        onPrimary = if (monochrome) Color.Black else Color.White,
        primaryContainer = primaryContainerFor(themeColor),
        onPrimaryContainer = if (monochrome) Color.White else Color(0xFFFFD8C0),
        secondary = if (monochrome) Color.White else AccentVariant,
        onSecondary = if (monochrome) Color.Black else Color(0xFF18083A),
        secondaryContainer = if (monochrome) Color(0xFF1A1A1A) else Color(0xFF280E55),
        onSecondaryContainer = if (monochrome) Color.White else Color(0xFFD8CCFF),
        background = bg.background,
        onBackground = if (monochrome) Color.White else OnSurface,
        surface = bg.surface,
        onSurface = if (monochrome) Color.White else OnSurface,
        surfaceVariant = bg.surfaceElevated,
        onSurfaceVariant = if (monochrome) Color(0xFFB8B8B8) else OnSurfaceMuted,
        outline = bg.outline,
        outlineVariant = if (monochrome) Color(0xFF202020) else Color(0xFF1E2138),
        error = ErrorRed
    )
}

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
fun AppTheme(
    themeColor: AppThemeColor = AppThemeColor.FJORA,
    backgroundColor: AppBackgroundColor = AppBackgroundColor.FJORA,
    content: @Composable () -> Unit
) {
    // Always dark — matches Findroid's "always cinema mode" feel.
    MaterialTheme(
        colorScheme = darkColors(themeColor, backgroundColor),
        shapes = AppShapes,
        typography = AppTypography,
        content = content
    )
}
