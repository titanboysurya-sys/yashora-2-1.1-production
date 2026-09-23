package com.ritvyom.yashoraReelgenerator.domain.models

import androidx.annotation.Keep

@Keep
data class WatermarkConfig(
    val isEnabled: Boolean = false,
    val text: String = "",
    val position: WatermarkPosition = WatermarkPosition.BOTTOM_RIGHT,
    val fontSizeSp: Int = 14,
    val fontFamily: WatermarkFontFamily = WatermarkFontFamily.SANS_SERIF,
    val colorHex: String = "#FFFFFF",
    val opacity: Float = 0.75f,
    val style: WatermarkStyle = WatermarkStyle.SHADOW
)

@Keep
enum class WatermarkPosition(val displayName: String) {
    BOTTOM_RIGHT("Bottom Right"),
    BOTTOM_LEFT("Bottom Left"),
    BOTTOM_CENTER("Bottom Center"),
    TOP_RIGHT("Top Right"),
    TOP_LEFT("Top Left"),
    TOP_CENTER("Top Center"),
    CENTER("Center")
}

@Keep
enum class WatermarkFontFamily(val displayName: String) {
    SANS_SERIF("Modern Sans"),
    BOLD_MODERN("Bold Impact"),
    SERIF("Classic Serif"),
    MONOSPACE("Code Mono"),
    CURSIVE("Script Cursive")
}

@Keep
enum class WatermarkStyle(val displayName: String) {
    NONE("Clean Flat"),
    SHADOW("Drop Shadow"),
    GLOW("Neon Glow"),
    PILL_BADGE("Pill Badge Box")
}
