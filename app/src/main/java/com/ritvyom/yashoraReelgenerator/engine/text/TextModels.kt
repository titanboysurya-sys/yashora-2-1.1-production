package com.ritvyom.yashoraReelgenerator.engine.text

import java.io.Serializable

/**
 * Functional role / type of text in the timeline.
 */
enum class TextType : Serializable {
    TEXT,
    CAPTION,
    SUBTITLE,
    TITLE,
    WATERMARK
}

/**
 * Text layout alignment.
 */
enum class TextAlignment : Serializable {
    LEFT,
    CENTER,
    RIGHT;

    companion object {
        fun fromString(str: String?): TextAlignment = when (str?.trim()?.uppercase()) {
            "LEFT" -> LEFT
            "RIGHT" -> RIGHT
            else -> CENTER
        }
    }
}

/**
 * Text entrance / exit animation categories and types.
 */
enum class TextAnimationType : Serializable {
    NONE,
    FADE,
    SLIDE_UP,
    SLIDE_DOWN,
    SLIDE_LEFT,
    SLIDE_RIGHT,
    SCALE_POP,
    POP,
    BOUNCE,
    TYPEWRITER,
    WORD_POP,
    WORD_FADE;

    companion object {
        fun fromString(str: String?): TextAnimationType = when (str?.trim()?.uppercase()) {
            "FADE", "FADE IN" -> FADE
            "SLIDE UP", "SLIDE_UP" -> SLIDE_UP
            "SLIDE DOWN", "SLIDE_DOWN" -> SLIDE_DOWN
            "SLIDE LEFT", "SLIDE_LEFT" -> SLIDE_LEFT
            "SLIDE RIGHT", "SLIDE_RIGHT" -> SLIDE_RIGHT
            "SCALE_POP", "SCALE POP" -> SCALE_POP
            "POP", "POP UP" -> POP
            "BOUNCE" -> BOUNCE
            "TYPEWRITER" -> TYPEWRITER
            "WORD_POP", "WORD POP" -> WORD_POP
            "WORD_FADE", "WORD FADE" -> WORD_FADE
            else -> NONE
        }
    }
}

/**
 * Timed word metadata for karaoke / caption word-by-word highlighting.
 */
data class TimedWord(
    val word: String,
    val startOffsetUs: Long,
    val durationUs: Long
) : Serializable {
    val endOffsetUs: Long get() = startOffsetUs + durationUs
}

/**
 * Complete styling specifications for Text, Caption, Subtitle, Title, and Watermark layers.
 */
data class TextStyleSpec(
    val fontName: String = "Default",
    val fontSizeSp: Float = 24f,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val letterSpacingEm: Float = 0f,
    val lineSpacingMultiplier: Float = 1.15f,
    val alignment: TextAlignment = TextAlignment.CENTER,
    val maxWidthFraction: Float = 0.85f,

    // Fill Color
    val textColorHex: String = "#FFFFFF",
    val textOpacity: Float = 1.0f,

    // Background Container
    val hasBackground: Boolean = false,
    val backgroundColorHex: String = "#80000000",
    val backgroundOpacity: Float = 0.6f,
    val backgroundPaddingDp: Float = 8f,
    val backgroundCornerRadiusDp: Float = 8f,

    // Stroke / Outline
    val strokeColorHex: String? = null,
    val strokeWidthDp: Float = 0f,

    // Drop Shadow
    val shadowColorHex: String? = "#80000000",
    val shadowOpacity: Float = 0.5f,
    val shadowRadiusDp: Float = 4f,
    val shadowOffsetX: Float = 2f,
    val shadowOffsetY: Float = 2f,

    // Timed Karaoke / Highlight
    val highlightColorHex: String = "#FFEB3B",
    val timedWords: List<TimedWord> = emptyList()
) : Serializable {

    companion object {
        // Built-in presets for quick aesthetic styling
        val CLASSIC = TextStyleSpec(
            fontName = "SansSerif",
            fontSizeSp = 22f,
            textColorHex = "#FFFFFF",
            hasBackground = true,
            backgroundColorHex = "#99000000",
            backgroundCornerRadiusDp = 6f
        )

        val BOLD_POP = TextStyleSpec(
            fontName = "Bold",
            fontSizeSp = 28f,
            isBold = true,
            textColorHex = "#FFEB3B",
            strokeColorHex = "#000000",
            strokeWidthDp = 2.5f,
            shadowColorHex = "#000000",
            shadowRadiusDp = 6f
        )

        val MINIMAL = TextStyleSpec(
            fontName = "SansSerif",
            fontSizeSp = 20f,
            textColorHex = "#FFFFFF",
            shadowColorHex = "#66000000",
            shadowRadiusDp = 3f
        )

        val BOX_HIGHLIGHT = TextStyleSpec(
            fontName = "Default",
            fontSizeSp = 24f,
            isBold = true,
            textColorHex = "#000000",
            hasBackground = true,
            backgroundColorHex = "#FFEB3B",
            backgroundOpacity = 1.0f,
            backgroundCornerRadiusDp = 8f
        )

        val CYBERPUNK = TextStyleSpec(
            fontName = "Monospace",
            fontSizeSp = 24f,
            isBold = true,
            textColorHex = "#00E5FF",
            strokeColorHex = "#FF007F",
            strokeWidthDp = 1.5f,
            shadowColorHex = "#00E5FF",
            shadowRadiusDp = 8f
        )

        val WATERMARK = TextStyleSpec(
            fontName = "SansSerif",
            fontSizeSp = 16f,
            textColorHex = "#FFFFFF",
            textOpacity = 0.45f,
            shadowColorHex = "#40000000",
            shadowRadiusDp = 2f
        )
    }
}
