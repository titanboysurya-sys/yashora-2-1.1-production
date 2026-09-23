package com.ritvyom.yashoraReelgenerator.engine.text

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Robust Font Management system for Yashora Video Editor.
 * Supports:
 * - System standard fonts (Sans-Serif, Serif, Monospace)
 * - Bundled / style presets (TikTok Style, Modern Bold, Cyberpunk, Handwritten, Display Heavy)
 * - Custom fonts loaded from files/assets
 * - Hindi Unicode + Devanagari script fallback
 * - Graceful fallback on missing fonts (never crashes)
 */
object FontManager {
    private const val TAG = "FontManager"

    // Thread-safe typeface cache
    private val typefaceCache = ConcurrentHashMap<String, Typeface>()

    val AVAILABLE_FONTS = listOf(
        "TikTok Style",
        "Modern Bold",
        "Cyberpunk",
        "Serif Elegant",
        "Handwritten",
        "Display Heavy",
        "SansSerif",
        "Serif",
        "Monospace",
        "Default"
    )

    /**
     * Resolves a [Typeface] by font name and requested styling (bold/italic).
     * Guaranteed to return a valid Typeface (never null) with graceful fallback to system default.
     */
    fun getTypeface(context: Context?, fontName: String?, isBold: Boolean = false, isItalic: Boolean = false): Typeface {
        val safeName = fontName?.trim() ?: "Default"
        val styleKey = "$safeName|b:$isBold|i:$isItalic"

        typefaceCache[styleKey]?.let { return it }

        val style = when {
            isBold && isItalic -> Typeface.BOLD_ITALIC
            isBold -> Typeface.BOLD
            isItalic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }

        // Try to load custom font file if safeName points to a file path
        if (safeName.startsWith("/") || safeName.startsWith("file://")) {
            try {
                val cleanPath = if (safeName.startsWith("file://")) safeName.removePrefix("file://") else safeName
                val fontFile = File(cleanPath)
                if (fontFile.exists() && fontFile.canRead()) {
                    val tf = Typeface.createFromFile(fontFile)
                    val styledTf = Typeface.create(tf, style)
                    typefaceCache[styleKey] = styledTf
                    return styledTf
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed loading custom font file from $safeName, falling back", e)
            }
        }

        // Try loading from assets if context is available
        if (context != null) {
            try {
                val assetCandidates = listOf("fonts/$safeName.ttf", "fonts/$safeName.otf")
                for (candidate in assetCandidates) {
                    try {
                        val tf = Typeface.createFromAsset(context.assets, candidate)
                        val styledTf = Typeface.create(tf, style)
                        typefaceCache[styleKey] = styledTf
                        return styledTf
                    } catch (_: Exception) {
                        // try next
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Asset font lookup failed for $safeName", e)
            }
        }

        // Map recognized font styles to system typography
        val baseTypeface = when (safeName) {
            "TikTok Style", "Modern Bold", "Display Heavy", "Impact", "Bold" -> Typeface.DEFAULT_BOLD
            "Serif Elegant", "Serif" -> Typeface.SERIF
            "Cyberpunk", "Monospace" -> Typeface.MONOSPACE
            "Handwritten", "Cursive" -> Typeface.SANS_SERIF
            "SansSerif" -> Typeface.SANS_SERIF
            else -> Typeface.DEFAULT
        }

        val finalTypeface = try {
            Typeface.create(baseTypeface, style)
        } catch (e: Exception) {
            Log.w(TAG, "Failed creating styled typeface for $safeName, falling back to DEFAULT", e)
            Typeface.DEFAULT
        }

        typefaceCache[styleKey] = finalTypeface
        return finalTypeface
    }

    /**
     * Clears cached typefaces to free memory.
     */
    fun clearCache() {
        typefaceCache.clear()
    }
}
