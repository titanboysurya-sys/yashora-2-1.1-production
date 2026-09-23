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
    primary = Color(0xFF0F52BA), // Sapphire Blue / Deep Indigo
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEF2FF), // Soft clean blue tint
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Color(0xFF334155), // Professional slate gray
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1F5F9),
    onSecondaryContainer = Color(0xFF0F172A),
    background = Color(0xFFFFFFFF), // Pure white background
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF), // Pure white surfaces
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF8FAFC), // Crisp clean gray
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFE2E8F0),
    outlineVariant = Color(0xFFF1F5F9)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF38BDF8), // Sky Blue accent
    onPrimary = Color(0xFF0F172A),
    primaryContainer = Color(0xFF1E293B),
    onPrimaryContainer = Color(0xFFF8FAFC),
    secondary = Color(0xFF94A3B8),
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = Color(0xFF1E293B),
    onSecondaryContainer = Color(0xFFF1F5F9),
    background = Color(0xFF121212), // Sleek neutral charcoal dark (NOT Blue)
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF1E1E1E), // Sleek neutral dark card surface
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF3F3F46),
    outlineVariant = Color(0xFF27272A)
)

private val AmoledColorScheme = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF18181B),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFA1A1AA),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF27272A),
    onSecondaryContainer = Color.White,
    background = Color(0xFF000000), // Pure OLED Pitch Black
    onBackground = Color(0xFFF4F4F5),
    surface = Color(0xFF09090B), // Deepest Dark OLED Surface
    onSurface = Color(0xFFF4F4F5),
    surfaceVariant = Color(0xFF18181B),
    onSurfaceVariant = Color(0xFFA1A1AA),
    outline = Color(0xFF27272A),
    outlineVariant = Color(0xFF18181B)
)

private val CyberpunkColorScheme = darkColorScheme(
    primary = Color(0xFFFF007F), // Hot Pink
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4A0033),
    onPrimaryContainer = Color(0xFFFFE0F0),
    secondary = Color(0xFF00F0FF), // Neon Cyan
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF00383C),
    onSecondaryContainer = Color(0xFFE0FFFF),
    background = Color(0xFF120824), // Midnight Cyber Purple
    onBackground = Color(0xFF00F0FF),
    surface = Color(0xFF1E1035), // Dark Magenta Surface
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2B1848),
    onSurfaceVariant = Color(0xFFFF007F),
    outline = Color(0xFF00F0FF),
    outlineVariant = Color(0xFFFF007F)
)

private val NeonColorScheme = darkColorScheme(
    primary = Color(0xFF39FF14), // Radioactive Neon Green
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF0E3A05),
    onPrimaryContainer = Color(0xFFE0FFE0),
    secondary = Color(0xFF00E5FF),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF003B42),
    onSecondaryContainer = Color(0xFFE0FFFF),
    background = Color(0xFF0A0F0A), // Pitch Green-Black
    onBackground = Color(0xFF39FF14),
    surface = Color(0xFF141F14), // Dark Matrix Surface
    onSurface = Color(0xFFE8FFE8),
    surfaceVariant = Color(0xFF1C2C1C),
    onSurfaceVariant = Color(0xFF98FF98),
    outline = Color(0xFF2A4D2A),
    outlineVariant = Color(0xFF141F14)
)

private val PurpleColorScheme = darkColorScheme(
    primary = Color(0xFFE040FB), // Electric Purple
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF3B0764),
    onPrimaryContainer = Color(0xFFF3E8FF),
    secondary = Color(0xFFD8B4FE),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF581C87),
    onSecondaryContainer = Color(0xFFFAF5FF),
    background = Color(0xFF120024), // Royal Velvet Purple
    onBackground = Color(0xFFFAF5FF),
    surface = Color(0xFF210936), // Deep Plum Surface
    onSurface = Color(0xFFFAF5FF),
    surfaceVariant = Color(0xFF32134E),
    onSurfaceVariant = Color(0xFFE9D5FF),
    outline = Color(0xFF7E22CE),
    outlineVariant = Color(0xFF32134E)
)

private val BlueColorScheme = darkColorScheme(
    primary = Color(0xFF60A5FA), // Vibrant Cobalt/Electric Blue
    onPrimary = Color(0xFF030712),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFEFF6FF),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = Color(0xFF1E293B),
    onSecondaryContainer = Color(0xFFE0F2FE),
    background = Color(0xFF0A192F), // Deep Oceanic Navy Blue
    onBackground = Color(0xFFF0F9FF),
    surface = Color(0xFF112240), // Deep Navy Surface
    onSurface = Color(0xFFF0F9FF),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF93C5FD),
    outline = Color(0xFF2563EB),
    outlineVariant = Color(0xFF1E293B)
)

private val GreenColorScheme = darkColorScheme(
    primary = Color(0xFF4ADE80), // Bright Mint Green
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF064E3B),
    onPrimaryContainer = Color(0xFFECFDF5),
    secondary = Color(0xFF34D399),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF022C22),
    onSecondaryContainer = Color(0xFFD1FAE5),
    background = Color(0xFF062319), // Deep Emerald Forest
    onBackground = Color(0xFFECFDF5),
    surface = Color(0xFF0F382A), // Forest Surface
    onSurface = Color(0xFFECFDF5),
    surfaceVariant = Color(0xFF1A4A38),
    onSurfaceVariant = Color(0xFFA7F3D0),
    outline = Color(0xFF059669),
    outlineVariant = Color(0xFF1A4A38)
)

private val OrangeColorScheme = darkColorScheme(
    primary = Color(0xFFFB923C), // Sunset Amber
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF7C2D12),
    onPrimaryContainer = Color(0xFFFFF7ED),
    secondary = Color(0xFFF97316),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF431407),
    onSecondaryContainer = Color(0xFFFFEDD5),
    background = Color(0xFF1E0F0A), // Deep Ember Charcoal
    onBackground = Color(0xFFFFF7ED),
    surface = Color(0xFF2E1A12), // Terracotta Surface
    onSurface = Color(0xFFFFF7ED),
    surfaceVariant = Color(0xFF42281D),
    onSurfaceVariant = Color(0xFFFDBA74),
    outline = Color(0xFFEA580C),
    outlineVariant = Color(0xFF42281D)
)

private val RedColorScheme = darkColorScheme(
    primary = Color(0xFFF87171), // Coral Crimson Ruby
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF7F1D1D),
    onPrimaryContainer = Color(0xFFFEF2F2),
    secondary = Color(0xFFEF4444),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF450A0A),
    onSecondaryContainer = Color(0xFFFEE2E2),
    background = Color(0xFF1C0A0A), // Crimson Velvet
    onBackground = Color(0xFFFEF2F2),
    surface = Color(0xFF2D1212), // Dark Rose Surface
    onSurface = Color(0xFFFEF2F2),
    surfaceVariant = Color(0xFF421A1A),
    onSurfaceVariant = Color(0xFFFCA5A5),
    outline = Color(0xFFDC2626),
    outlineVariant = Color(0xFF421A1A)
)

@Composable
fun YashoraTheme(
    themeName: String,
    content: @Composable () -> Unit
) {
    val systemIsDark = isSystemInDarkTheme()
    val scheme = when (themeName) {
        "Light", "Bento Grid" -> LightColorScheme
        "Dark" -> DarkColorScheme
        "System", "Device", "Device Default" -> if (systemIsDark) DarkColorScheme else LightColorScheme
        "AMOLED Black" -> AmoledColorScheme
        "Cyberpunk" -> CyberpunkColorScheme
        "Neon" -> NeonColorScheme
        "Purple" -> PurpleColorScheme
        "Blue" -> BlueColorScheme
        "Green" -> GreenColorScheme
        "Orange" -> OrangeColorScheme
        "Red" -> RedColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = Typography,
        shapes = BentoShapes,
        content = content
    )
}
