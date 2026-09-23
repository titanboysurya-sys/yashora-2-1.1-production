package com.ritvyom.yashoraReelgenerator.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val BentoShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0F52BA), // Sapphire Blue / Deep Indigo for high-end professional look
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEF2FF), // Soft clean blue tint
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Color(0xFF334155), // Professional slate gray
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1F5F9),
    onSecondaryContainer = Color(0xFF0F172A),
    background = Color(0xFFFFFFFF), // Pure white background for that clean professional look
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF), // Pure white surfaces
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF8FAFC), // Crisp clean gray for list items and card elements
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFE2E8F0), // Thin clean borders
    outlineVariant = Color(0xFFF1F5F9)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF38BDF8), // Sky Blue for clean tech accents
    onPrimary = Color(0xFF0B0F19),
    primaryContainer = Color(0xFF1E293B),
    onPrimaryContainer = Color(0xFFF8FAFC),
    secondary = Color(0xFF94A3B8),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF151D30),
    onSecondaryContainer = Color(0xFFF1F5F9),
    background = Color(0xFF0B0F19), // Deep slate-black background (YT/FB premium style)
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF151D30), // Elevated modern surface cards
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF1E293B)
)

private val BentoColorScheme = lightColorScheme(
    primary = Color(0xFF6750A4), // Modern purple accents
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF49454F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEADDFF),
    onSecondaryContainer = Color(0xFF1D1B20),
    background = Color(0xFFF0F1F5), // Light cool gray-blue background to outline white cards in Bento Grid style
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFFFFFF), // White cards that pop beautifully against the off-white background
    onSurface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFFECEFF3),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFFD1D5DB),
    outlineVariant = Color(0xFFECEFF3)
)

private val AmoledColorScheme = darkColorScheme(
    primary = Color.White,
    secondary = AmoledSecondary,
    tertiary = AmoledPrimary,
    background = AmoledBackground,
    surface = AmoledSurface,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = Color(0xFFEEEEEE),
    onSurface = Color(0xFFEEEEEE)
)

private val CyberpunkColorScheme = darkColorScheme(
    primary = CyberPrimary,
    secondary = CyberSecondary,
    tertiary = CyberTertiary,
    background = CyberBackground,
    surface = CyberSurface,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color(0xFF00F0FF),
    onSurface = Color(0xFFFFFFFF)
)

private val NeonColorScheme = darkColorScheme(
    primary = NeonPrimary,
    secondary = NeonSecondary,
    tertiary = Color.White,
    background = NeonBackground,
    surface = NeonSurface,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = NeonPrimary,
    onSurface = Color.White
)

private val PurpleColorScheme = darkColorScheme(
    primary = PurpleSecondary,
    secondary = PurplePrimary,
    background = PurpleBg,
    surface = Color(0xFF21103E),
    onPrimary = Color.Black,
    onBackground = Color(0xFFF3E5F5),
    onSurface = Color(0xFFF3E5F5)
)

private val BlueColorScheme = darkColorScheme(
    primary = BlueSecondary,
    secondary = BluePrimary,
    background = BlueBg,
    surface = Color(0xFF1B2A47),
    onPrimary = Color.Black,
    onBackground = Color(0xFFE3F2FD),
    onSurface = Color(0xFFE3F2FD)
)

private val GreenColorScheme = darkColorScheme(
    primary = GreenSecondary,
    secondary = GreenPrimary,
    background = GreenBg,
    surface = Color(0xFF152C1E),
    onPrimary = Color.Black,
    onBackground = Color(0xFFE8F5E9),
    onSurface = Color(0xFFE8F5E9)
)

private val OrangeColorScheme = darkColorScheme(
    primary = OrangeSecondary,
    secondary = OrangePrimary,
    background = OrangeBg,
    surface = Color(0xFF2E1C16),
    onPrimary = Color.Black,
    onBackground = Color(0xFFFFF3E0),
    onSurface = Color(0xFFFFF3E0)
)

private val RedColorScheme = darkColorScheme(
    primary = RedSecondary,
    secondary = RedPrimary,
    background = RedBg,
    surface = Color(0xFF2D1010),
    onPrimary = Color.Black,
    onBackground = Color(0xFFFFEBEE),
    onSurface = Color(0xFFFFEBEE)
)

@Composable
fun YashoraTheme(
    themeName: String,
    content: @Composable () -> Unit
) {
    val systemIsDark = isSystemInDarkTheme()
    val scheme = when (themeName) {
        "Bento Grid" -> BentoColorScheme
        "Light" -> LightColorScheme
        "Dark" -> DarkColorScheme
        "System" -> if (systemIsDark) DarkColorScheme else LightColorScheme
        "AMOLED Black" -> AmoledColorScheme
        "Cyberpunk" -> CyberpunkColorScheme
        "Neon" -> NeonColorScheme
        "Purple" -> PurpleColorScheme
        "Blue" -> BlueColorScheme
        "Green" -> GreenColorScheme
        "Orange" -> OrangeColorScheme
        "Red" -> RedColorScheme
        else -> BentoColorScheme
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = Typography,
        shapes = BentoShapes,
        content = content
    )
}
