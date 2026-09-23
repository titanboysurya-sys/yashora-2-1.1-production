package com.ritvyom.yashoraReelgenerator.engine.text

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.Serializable

/**
 * Evaluated text layout metrics. Shared identically between Compose preview calculations
 * and OpenGL Render Engine rasterization.
 */
data class TextLayoutResult(
    val widthPx: Int,
    val heightPx: Int,
    val lineCount: Int,
    val paddingPx: Int,
    val totalWidthPx: Int,
    val totalHeightPx: Int,
    val staticLayout: StaticLayout
) : Serializable

/**
 * Shared, deterministic text layout engine.
 * Ensures that line-breaking, glyph shaping, Hindi Unicode combining characters,
 * word wrapping, and bounds are evaluated with 100% parity across Preview and Export.
 */
object TextLayoutEngine {

    /**
     * Builds a deterministic [TextPaint] with anti-aliasing, color, size, and typeface.
     */
    fun createTextPaint(
        style: TextStyleSpec,
        density: Float,
        typeface: Typeface
    ): TextPaint {
        val fontSizePx = (style.fontSizeSp * density * 1.5f).coerceAtLeast(12f)

        return TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            textSize = fontSizePx
            color = parseColor(style.textColorHex, Color.WHITE)
            this.typeface = typeface
            letterSpacing = style.letterSpacingEm

            if (style.isUnderline) {
                isUnderlineText = true
            }
        }
    }

    /**
     * Calculates the deterministic [StaticLayout] and measured dimensions.
     */
    fun measureText(
        text: String,
        style: TextStyleSpec,
        viewportWidth: Int,
        density: Float,
        typeface: Typeface
    ): TextLayoutResult {
        val safeText = if (text.isEmpty()) " " else text
        val textPaint = createTextPaint(style, density, typeface)

        val maxWidthPx = (viewportWidth * style.maxWidthFraction).toInt().coerceAtLeast(100)

        val alignment = when (style.alignment) {
            TextAlignment.LEFT -> Layout.Alignment.ALIGN_NORMAL
            TextAlignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
            TextAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
        }

        val staticLayout = StaticLayout.Builder.obtain(safeText, 0, safeText.length, textPaint, maxWidthPx)
            .setAlignment(alignment)
            .setLineSpacing(0f, style.lineSpacingMultiplier)
            .setIncludePad(true)
            .build()

        val paddingPx = if (style.hasBackground || style.strokeWidthDp > 0f) {
            (style.backgroundPaddingDp * density).toInt().coerceAtLeast(4)
        } else {
            (4 * density).toInt()
        }

        val totalWidthPx = (staticLayout.width + paddingPx * 2).coerceAtLeast(32)
        val totalHeightPx = (staticLayout.height + paddingPx * 2).coerceAtLeast(32)

        return TextLayoutResult(
            widthPx = staticLayout.width,
            heightPx = staticLayout.height,
            lineCount = staticLayout.lineCount,
            paddingPx = paddingPx,
            totalWidthPx = totalWidthPx,
            totalHeightPx = totalHeightPx,
            staticLayout = staticLayout
        )
    }

    /**
     * Parses a hex color string into Android Int Color with safe fallback.
     */
    fun parseColor(hex: String?, fallback: Int): Int {
        if (hex.isNullOrBlank()) return fallback
        return try {
            Color.parseColor(hex.trim())
        } catch (_: Exception) {
            fallback
        }
    }
}
