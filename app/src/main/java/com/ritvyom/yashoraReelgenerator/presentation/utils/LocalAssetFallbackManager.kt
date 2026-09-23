package com.ritvyom.yashoraReelgenerator.presentation.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.core.content.ContextCompat
import com.ritvyom.yashoraReelgenerator.R
import java.io.File
import java.io.FileOutputStream
import java.util.Random

/**
 * Fallback mechanism for Media3 Transformer video construction pipeline and visual asset resolution.
 * Iterates through script sections and assigns a default random background asset from a curated local
 * drawable set or placeholder cache whenever AI-generated visual search returns empty.
 */
object LocalAssetFallbackManager {
    private const val TAG = "LocalAssetFallback"
    private const val CACHE_DIR_NAME = "YashoraPlaceholderCache"

    // Curated aesthetic color palette themes for random local background generation
    private val CURATED_COLOR_THEMES = listOf(
        Pair("#1C093A", "#05010E"), // Cinematic Violet
        Pair("#0D1B2A", "#1B263B"), // Deep Space Navy
        Pair("#1F1000", "#050200"), // Warm Golden Amber
        Pair("#001E17", "#000805"), // Emerald Forest Studio
        Pair("#23001E", "#080007"), // Cyberpunk Neon Magenta
        Pair("#1C1C1C", "#080808"), // Obsidian Charcoal
        Pair("#1A0006", "#040001"), // Crimson Luxury
        Pair("#0B132B", "#1C2541")  // Midnight Royal Blue
    )

    /**
     * Assigns a default background asset from a curated local drawable set or placeholder cache
     * for a given script section index.
     */
    fun getCuratedFallbackAsset(
        context: Context,
        sceneIndex: Int,
        style: String = "",
        aspectRatio: String = "9:16"
    ): String {
        return try {
            val cacheDir = File(context.filesDir, CACHE_DIR_NAME)
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val seed = Math.abs((sceneIndex + style.hashCode()).toLong())
            val themeIndex = (seed % CURATED_COLOR_THEMES.size).toInt()
            val themeName = "curated_bg_scene_${sceneIndex}_theme_$themeIndex"
            val targetFile = File(cacheDir, "$themeName.png")

            if (targetFile.exists() && targetFile.length() > 1000L) {
                return targetFile.absolutePath
            }

            // 1. Try resolving from local drawable resource set first
            val drawableBmp = loadFromLocalDrawableSet(context, themeIndex)
            if (drawableBmp != null) {
                saveBitmapToCache(drawableBmp, targetFile)
                try { drawableBmp.recycle() } catch (e: Exception) {}
                if (targetFile.exists() && targetFile.length() > 0L) {
                    return targetFile.absolutePath
                }
            }

            // 2. Generate curated aesthetic gradient background bitmap if drawable set is unavailable
            val width = if (aspectRatio == "16:9") 1280 else if (aspectRatio == "1:1") 1080 else 720
            val height = if (aspectRatio == "16:9") 720 else if (aspectRatio == "1:1") 1080 else 1280

            val generatedBmp = createCuratedAestheticBitmap(width, height, themeIndex, sceneIndex)
            saveBitmapToCache(generatedBmp, targetFile)
            try { generatedBmp.recycle() } catch (e: Exception) {}

            Log.d(TAG, "Assigned curated local placeholder asset for script section ${sceneIndex + 1}: ${targetFile.absolutePath}")
            targetFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve curated fallback asset for scene $sceneIndex", e)
            ""
        }
    }

    /**
     * Loads a bitmap from the curated local drawable resource set.
     */
    private fun loadFromLocalDrawableSet(context: Context, index: Int): Bitmap? {
        return try {
            val drawableId = R.drawable.ic_launcher_background
            val drawable: Drawable? = ContextCompat.getDrawable(context, drawableId) ?: return null
            val bmp = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable?.setBounds(0, 0, canvas.width, canvas.height)
            drawable?.draw(canvas)

            // Overlay subtle gradient to transform launcher background into a sleek canvas
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val theme = CURATED_COLOR_THEMES[index % CURATED_COLOR_THEMES.size]
            val grad = LinearGradient(
                0f, 0f, 0f, 1280f,
                Color.parseColor(theme.first),
                Color.parseColor(theme.second),
                Shader.TileMode.CLAMP
            )
            paint.shader = grad
            paint.alpha = 200
            canvas.drawRect(0f, 0f, 720f, 1280f, paint)
            bmp
        } catch (e: Exception) {
            Log.w(TAG, "Error rendering from local drawable set", e)
            null
        }
    }

    /**
     * Renders a high-quality curated aesthetic canvas for placeholder cache.
     */
    private fun createCuratedAestheticBitmap(
        width: Int,
        height: Int,
        themeIndex: Int,
        sceneIndex: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val theme = CURATED_COLOR_THEMES[themeIndex % CURATED_COLOR_THEMES.size]
        val startColor = Color.parseColor(theme.first)
        val endColor = Color.parseColor(theme.second)

        // Base linear gradient
        val linearGrad = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            startColor, endColor,
            Shader.TileMode.CLAMP
        )
        paint.shader = linearGrad
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // Radial ambient glow overlay
        val ambientPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val radialGrad = RadialGradient(
            width * 0.5f, height * 0.4f, Math.max(width, height) * 0.6f,
            Color.argb(80, 255, 255, 255),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        ambientPaint.shader = radialGrad
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), ambientPaint)

        // Subtle geometric decorative curves
        val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(20, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        val path = Path()
        path.moveTo(0f, height * 0.7f)
        path.cubicTo(
            width * 0.3f, height * 0.6f,
            width * 0.7f, height * 0.8f,
            width.toFloat(), height * 0.65f
        )
        canvas.drawPath(path, pathPaint)

        return bitmap
    }

    private fun saveBitmapToCache(bitmap: Bitmap, outputFile: File) {
        try {
            FileOutputStream(outputFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
                out.flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing bitmap to placeholder cache", e)
        }
    }
}
