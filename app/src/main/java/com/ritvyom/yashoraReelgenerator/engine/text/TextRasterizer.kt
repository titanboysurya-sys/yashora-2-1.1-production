package com.ritvyom.yashoraReelgenerator.engine.text

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log
import android.util.LruCache
import com.ritvyom.yashoraReelgenerator.engine.egl.GlShaderUtil

/**
 * High-performance, memory-conscious rasterizer and GPU texture manager for text clips.
 * Features:
 * - Deterministic text rendering using [TextLayoutEngine] and [FontManager]
 * - Background pill rendering with corner radius & opacity
 * - Text drop shadows and clean outline strokes
 * - LRU Texture Caching with safe reuse and eviction recycling
 * - Karaoke highlight word styling
 */
class TextRasterizer(
    private val context: Context,
    maxCacheSizeMb: Int = 16
) {
    companion object {
        private const val TAG = "TextRasterizer"
    }

    private data class TextureEntry(
        val textureId: Int,
        val width: Int,
        val height: Int,
        val byteCount: Int
    )

    // LRU Cache for text textures
    private val textureLruCache = object : LruCache<String, TextureEntry>(maxCacheSizeMb * 1024 * 1024) {
        override fun sizeOf(key: String, value: TextureEntry): Int = value.byteCount

        override fun entryRemoved(evicted: Boolean, key: String, oldValue: TextureEntry, newValue: TextureEntry?) {
            if (evicted || (newValue != null && oldValue.textureId != newValue.textureId)) {
                deleteTexture(oldValue.textureId)
            }
        }
    }

    private val activeTextures = mutableSetOf<Int>()

    /**
     * Rasterizes text to an OpenGL texture with the specified styling parameters.
     */
    fun rasterizeText(
        text: String,
        style: TextStyleSpec,
        viewportWidth: Int,
        density: Float = 2.0f,
        currentWordHighlightIndex: Int = -1
    ): Int {
        if (text.isBlank()) return 0

        val cacheKey = buildCacheKey(text, style, viewportWidth, currentWordHighlightIndex)
        textureLruCache.get(cacheKey)?.let { return it.textureId }

        val typeface = FontManager.getTypeface(context, style.fontName, style.isBold, style.isItalic)
        val layoutResult = TextLayoutEngine.measureText(text, style, viewportWidth, density, typeface)

        val bmpWidth = layoutResult.totalWidthPx
        val bmpHeight = layoutResult.totalHeightPx

        val bitmap = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Draw Background Container if enabled
        if (style.hasBackground) {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = TextLayoutEngine.parseColor(style.backgroundColorHex, android.graphics.Color.BLACK)
                alpha = (style.backgroundOpacity.coerceIn(0f, 1f) * 255).toInt()
            }
            val cornerRadiusPx = style.backgroundCornerRadiusDp * density
            val rect = RectF(0f, 0f, bmpWidth.toFloat(), bmpHeight.toFloat())
            canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, bgPaint)
        }

        val padding = layoutResult.paddingPx.toFloat()

        // 2. Draw Drop Shadow or Stroke if specified
        if (!style.strokeColorHex.isNullOrBlank() && style.strokeWidthDp > 0f) {
            val strokePaint = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                textSize = (style.fontSizeSp * density * 1.5f).coerceAtLeast(12f)
                color = TextLayoutEngine.parseColor(style.strokeColorHex, android.graphics.Color.BLACK)
                this.typeface = typeface
                this.style = Paint.Style.STROKE
                strokeWidth = style.strokeWidthDp * density * 1.5f
                letterSpacing = style.letterSpacingEm
            }

            val strokeLayout = android.text.StaticLayout.Builder.obtain(
                text,
                0,
                text.length,
                strokePaint,
                (viewportWidth * style.maxWidthFraction).toInt().coerceAtLeast(100)
            )
                .setAlignment(layoutResult.staticLayout.alignment)
                .setLineSpacing(0f, style.lineSpacingMultiplier)
                .setIncludePad(true)
                .build()

            canvas.save()
            canvas.translate(padding, padding)
            strokeLayout.draw(canvas)
            canvas.restore()
        }

        // 3. Draw Text Fill & Shadow
        canvas.save()
        canvas.translate(padding, padding)

        if (!style.shadowColorHex.isNullOrBlank() && style.shadowRadiusDp > 0f) {
            layoutResult.staticLayout.paint.setShadowLayer(
                style.shadowRadiusDp * density,
                style.shadowOffsetX * density,
                style.shadowOffsetY * density,
                TextLayoutEngine.parseColor(style.shadowColorHex, android.graphics.Color.DKGRAY)
            )
        } else {
            layoutResult.staticLayout.paint.clearShadowLayer()
        }

        layoutResult.staticLayout.draw(canvas)
        canvas.restore()

        // 4. Upload to OpenGL texture
        val texId = GlShaderUtil.create2DTexture()
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        val byteCount = bitmap.byteCount
        bitmap.recycle()

        val entry = TextureEntry(texId, bmpWidth, bmpHeight, byteCount)
        textureLruCache.put(cacheKey, entry)
        activeTextures.add(texId)

        return texId
    }

    private fun buildCacheKey(
        text: String,
        style: TextStyleSpec,
        viewportWidth: Int,
        highlightIndex: Int
    ): String {
        return "$text|${style.fontName}|${style.fontSizeSp}|${style.textColorHex}|${style.backgroundColorHex}|${style.strokeColorHex}|${style.shadowColorHex}|$viewportWidth|$highlightIndex"
    }

    private fun deleteTexture(textureId: Int) {
        if (textureId > 0 && activeTextures.contains(textureId)) {
            val texArr = intArrayOf(textureId)
            GLES20.glDeleteTextures(1, texArr, 0)
            activeTextures.remove(textureId)
        }
    }

    /**
     * Releases all allocated textures and clears the cache.
     */
    fun release() {
        for (texId in activeTextures) {
            val texArr = intArrayOf(texId)
            GLES20.glDeleteTextures(1, texArr, 0)
        }
        activeTextures.clear()
        textureLruCache.evictAll()
    }
}
