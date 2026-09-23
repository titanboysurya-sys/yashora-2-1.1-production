package com.ritvyom.yashoraReelgenerator.engine.renderer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.opengl.GLES20
import android.opengl.GLUtils
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import android.util.LruCache
import com.ritvyom.yashoraReelgenerator.core.model.CanvasBackgroundStyle
import com.ritvyom.yashoraReelgenerator.core.model.NormalizedCropRect
import com.ritvyom.yashoraReelgenerator.core.rendering.RenderContext
import com.ritvyom.yashoraReelgenerator.core.rendering.RenderNode
import com.ritvyom.yashoraReelgenerator.core.rendering.RenderPassType
import com.ritvyom.yashoraReelgenerator.engine.effects.GpuEffectPipeline
import com.ritvyom.yashoraReelgenerator.engine.egl.GlFramebufferPool
import com.ritvyom.yashoraReelgenerator.engine.egl.GlShaderUtil
import com.ritvyom.yashoraReelgenerator.engine.text.TextRasterizer
import com.ritvyom.yashoraReelgenerator.engine.text.TextStyleSpec
import java.io.File

/**
 * Unified Render Engine executing the Render Graph passes in strict deterministic order.
 * Consumed identically by Preview Renderer and Export Renderer.
 */
class UnifiedRenderEngine(private val context: Context) {

    companion object {
        private const val TAG = "UnifiedRenderEngine"
        private const val MAX_TEXTURE_CACHE_SIZE = 30
    }

    private var videoShader: UnifiedVideoShader? = null
    private var fboPool: GlFramebufferPool? = null
    private var gpuPipeline: GpuEffectPipeline? = null
    private var textRasterizer: TextRasterizer? = null
    private val textureCache = LruCache<String, Int>(MAX_TEXTURE_CACHE_SIZE)
    private val textBitmapCache = LruCache<String, Int>(MAX_TEXTURE_CACHE_SIZE)

    // Solid 1x1 fallback texture
    private var defaultWhiteTexture = 0

    fun initialize() {
        videoShader = UnifiedVideoShader()
        val pool = GlFramebufferPool()
        fboPool = pool
        gpuPipeline = GpuEffectPipeline(context, pool)
        textRasterizer = TextRasterizer(context)
        defaultWhiteTexture = createSolidTexture(Color.WHITE)
        Log.i(TAG, "UnifiedRenderEngine initialized with GPU effect pipeline and TextRasterizer")
    }

    /**
     * Executes the topological Render Graph passes for a single frame.
     */
    fun renderFrame(nodes: List<RenderNode>, renderContext: RenderContext) {
        val shader = videoShader ?: return
        val vpWidth = renderContext.viewportWidth
        val vpHeight = renderContext.viewportHeight

        GLES20.glViewport(0, 0, vpWidth, vpHeight)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // Strict z-index topological sorting for layered compositing
        val sortedNodes = nodes.sortedBy { it.zIndex }

        for (node in sortedNodes) {
            if (!node.isVisible) continue

            when (node.passType) {
                RenderPassType.CANVAS_COMPOSITE -> {
                    // Render canvas background
                    val canvasConfig = renderContext.canvasConfig
                    if (canvasConfig.backgroundStyle == CanvasBackgroundStyle.BLURRED_MEDIA && node.sourceUri != null) {
                        val bgTex = getOrLoadMediaTexture(node.sourceUri, node.isVideoSource)
                        if (bgTex != 0 && bgTex != defaultWhiteTexture) {
                            shader.drawBlurredBackground(bgTex, vpWidth, vpHeight, (canvasConfig.blurRadiusDp / 6f).coerceIn(1f, 10f))
                            continue
                        }
                    }

                    val (topRgba, bottomRgba) = when (canvasConfig.backgroundStyle) {
                        CanvasBackgroundStyle.SOLID_COLOR -> {
                            val c = parseColorOr(canvasConfig.backgroundColorHex, 0xFF0C0914.toInt())
                            val rgba = colorToRgba(c)
                            rgba to rgba
                        }
                        CanvasBackgroundStyle.GRADIENT, CanvasBackgroundStyle.BLURRED_MEDIA -> {
                            val cTop = parseColorOr(canvasConfig.gradientColorsHex.getOrNull(0) ?: "#0C0914", 0xFF0C0914.toInt())
                            val cBot = parseColorOr(canvasConfig.gradientColorsHex.getOrNull(1) ?: "#1E1830", 0xFF1E1830.toInt())
                            colorToRgba(cTop) to colorToRgba(cBot)
                        }
                    }
                    shader.drawBackground(vpWidth, vpHeight, topRgba, bottomRgba)
                }

                RenderPassType.SOURCE_DECODE, RenderPassType.GEOMETRIC_TRANSFORM -> {
                    // Video or Image layer pass
                    val textureId = getOrLoadMediaTexture(node.sourceUri, node.isVideoSource)
                    var effectiveTexture = if (textureId != 0) textureId else defaultWhiteTexture

                    // Apply multi-pass GPU Blur if specified in effects
                    val blurEffect = node.effects.firstOrNull { it.effectId.contains("blur", ignoreCase = true) }
                    if (blurEffect != null && blurEffect.intensity > 0.01f) {
                        val blurRadius = blurEffect.intensity * 8.0f
                        effectiveTexture = gpuPipeline?.applySeparableBlur(effectiveTexture, vpWidth, vpHeight, blurRadius) ?: effectiveTexture
                    }

                    val lutTexId = gpuPipeline?.getOrLoadLutTexture(node.colorGrading.lutAssetPath) ?: 0
                    val creativeEffect = node.effects.firstOrNull { !it.effectId.contains("blur", ignoreCase = true) }

                    shader.drawLayer(
                        textureId = effectiveTexture,
                        transform = node.transform,
                        crop = node.transform.crop,
                        colorGrading = node.colorGrading,
                        viewportWidth = vpWidth,
                        viewportHeight = vpHeight,
                        lutTextureId = lutTexId,
                        effectSpec = creativeEffect,
                        chromaKey = node.chromaKeySpec,
                        maskSpec = node.maskSpec
                    )
                }

                RenderPassType.OVERLAY_TEXT_AND_STICKER -> {
                    val lutTexId = gpuPipeline?.getOrLoadLutTexture(node.colorGrading.lutAssetPath) ?: 0
                    val creativeEffect = node.effects.firstOrNull { !it.effectId.contains("blur", ignoreCase = true) }

                    if (node.textPayload != null) {
                        // Render Text overlay
                        var textTexId = getOrRenderTextTexture(node.textPayload, node.textStyleSpec, node.textStylePayload, vpWidth)
                        if (textTexId != 0) {
                            val blurEffect = node.effects.firstOrNull { it.effectId.contains("blur", ignoreCase = true) }
                            if (blurEffect != null && blurEffect.intensity > 0.01f) {
                                textTexId = gpuPipeline?.applySeparableBlur(textTexId, vpWidth, vpHeight, blurEffect.intensity * 8f) ?: textTexId
                            }

                            shader.drawLayer(
                                textureId = textTexId,
                                transform = node.transform,
                                crop = NormalizedCropRect(),
                                colorGrading = node.colorGrading,
                                viewportWidth = vpWidth,
                                viewportHeight = vpHeight,
                                lutTextureId = lutTexId,
                                effectSpec = creativeEffect
                            )
                        }
                    } else if (node.sourceUri != null) {
                        // Sticker overlay
                        var stickerTexId = getOrLoadMediaTexture(node.sourceUri, false)
                        if (stickerTexId != 0) {
                            val blurEffect = node.effects.firstOrNull { it.effectId.contains("blur", ignoreCase = true) }
                            if (blurEffect != null && blurEffect.intensity > 0.01f) {
                                stickerTexId = gpuPipeline?.applySeparableBlur(stickerTexId, vpWidth, vpHeight, blurEffect.intensity * 8f) ?: stickerTexId
                            }

                            shader.drawLayer(
                                textureId = stickerTexId,
                                transform = node.transform,
                                crop = NormalizedCropRect(),
                                colorGrading = node.colorGrading,
                                viewportWidth = vpWidth,
                                viewportHeight = vpHeight,
                                lutTextureId = lutTexId,
                                effectSpec = creativeEffect
                            )
                        }
                    }
                }

                else -> {
                    // Other passes handled inline or by shader
                }
            }
        }
    }

    private fun getOrLoadMediaTexture(uriStr: String?, isVideo: Boolean): Int {
        if (uriStr.isNullOrBlank()) return defaultWhiteTexture
        textureCache.get(uriStr)?.let { return it }

        try {
            val bitmap: Bitmap? = if (isVideo) {
                // Extract video keyframe or poster
                val retriever = MediaMetadataRetriever()
                try {
                    if (uriStr.startsWith("http://") || uriStr.startsWith("https://")) {
                        retriever.setDataSource(uriStr, HashMap())
                    } else if (uriStr.startsWith("content://") || uriStr.startsWith("file://")) {
                        retriever.setDataSource(context, Uri.parse(uriStr))
                    } else {
                        retriever.setDataSource(File(uriStr).absolutePath)
                    }
                    retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } finally {
                    retriever.release()
                }
            } else {
                // Decode image from file / content uri
                val uri = Uri.parse(uriStr)
                if (uriStr.startsWith("http")) {
                    null // Streamed via Coil / network cache
                } else if (uriStr.startsWith("content://")) {
                    context.contentResolver.openInputStream(uri)?.use {
                        android.graphics.BitmapFactory.decodeStream(it)
                    }
                } else {
                    android.graphics.BitmapFactory.decodeFile(uriStr)
                }
            }

            if (bitmap != null) {
                val texId = uploadBitmapToTexture(bitmap)
                bitmap.recycle()
                textureCache.put(uriStr, texId)
                return texId
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load media texture: $uriStr", e)
        }

        return defaultWhiteTexture
    }

    private fun getOrRenderTextTexture(text: String, styleSpec: TextStyleSpec?, styleMap: Map<String, Any>?, viewportWidth: Int): Int {
        if (text.isBlank()) return defaultWhiteTexture

        // If high-fidelity TextStyleSpec is provided, use dedicated TextRasterizer
        if (styleSpec != null && textRasterizer != null) {
            val texId = textRasterizer!!.rasterizeText(
                text = text,
                style = styleSpec,
                viewportWidth = viewportWidth,
                density = context.resources.displayMetrics.density
            )
            if (texId > 0) return texId
        }

        val cacheKey = "$text|${styleMap.hashCode()}"
        textBitmapCache.get(cacheKey)?.let { return it }

        val fontName = (styleMap?.get("font") as? String) ?: "Default"
        val fontSizeSp = ((styleMap?.get("fontSizeSp") as? Number)?.toFloat() ?: 24f).coerceAtLeast(12f)
        val textColorHex = (styleMap?.get("textColorHex") as? String) ?: "#FFFFFF"
        val bgColorHex = (styleMap?.get("backgroundColorHex") as? String) ?: "#00000000"

        val density = context.resources.displayMetrics.density
        val fontSizePx = fontSizeSp * density * 1.5f // High DPI scale

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = fontSizePx
            color = parseColorOr(textColorHex, Color.WHITE)
            typeface = when (fontName) {
                "Bold", "Impact", "TikTok Style" -> Typeface.DEFAULT_BOLD
                "Serif" -> Typeface.SERIF
                "Monospace" -> Typeface.MONOSPACE
                else -> Typeface.DEFAULT
            }
        }

        val maxWidth = (viewportWidth * 0.85f).toInt().coerceAtLeast(200)
        val staticLayout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, maxWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.2f)
            .setIncludePad(true)
            .build()

        val padding = (16 * density).toInt()
        val bmpWidth = (staticLayout.width + padding * 2).coerceAtLeast(64)
        val bmpHeight = (staticLayout.height + padding * 2).coerceAtLeast(64)

        val bmp = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Draw background pill if configured
        val bgColor = parseColorOr(bgColorHex, Color.TRANSPARENT)
        if (Color.alpha(bgColor) > 5) {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = bgColor
            }
            val rect = android.graphics.RectF(0f, 0f, bmpWidth.toFloat(), bmpHeight.toFloat())
            canvas.drawRoundRect(rect, 16f * density, 16f * density, bgPaint)
        }

        canvas.save()
        canvas.translate(padding.toFloat(), padding.toFloat())
        staticLayout.draw(canvas)
        canvas.restore()

        val texId = uploadBitmapToTexture(bmp)
        bmp.recycle()
        textBitmapCache.put(cacheKey, texId)
        return texId
    }

    private fun uploadBitmapToTexture(bitmap: Bitmap): Int {
        val texId = GlShaderUtil.create2DTexture()
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        return texId
    }

    private fun createSolidTexture(color: Int): Int {
        val bmp = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(color)
        val texId = uploadBitmapToTexture(bmp)
        bmp.recycle()
        return texId
    }

    private fun parseColorOr(hex: String?, fallback: Int): Int {
        if (hex.isNullOrBlank()) return fallback
        return try {
            Color.parseColor(hex)
        } catch (_: Exception) {
            fallback
        }
    }

    private fun colorToRgba(color: Int): FloatArray {
        return floatArrayOf(
            Color.red(color) / 255f,
            Color.green(color) / 255f,
            Color.blue(color) / 255f,
            Color.alpha(color) / 255f
        )
    }

    fun release() {
        videoShader?.release()
        videoShader = null

        gpuPipeline?.release()
        gpuPipeline = null

        textRasterizer?.release()
        textRasterizer = null

        fboPool?.releaseAll()
        fboPool = null

        if (defaultWhiteTexture != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(defaultWhiteTexture), 0)
            defaultWhiteTexture = 0
        }

        // Clean up cached textures
        val texSnapshot = textureCache.snapshot()
        for ((_, texId) in texSnapshot) {
            GLES20.glDeleteTextures(1, intArrayOf(texId), 0)
        }
        textureCache.evictAll()

        val textSnapshot = textBitmapCache.snapshot()
        for ((_, texId) in textSnapshot) {
            GLES20.glDeleteTextures(1, intArrayOf(texId), 0)
        }
        textBitmapCache.evictAll()
    }
}
