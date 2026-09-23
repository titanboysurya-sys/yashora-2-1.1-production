package com.ritvyom.yashoraReelgenerator.engine.text

import android.util.Log
import com.ritvyom.yashoraReelgenerator.core.model.TextClipItem
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Serializable
import java.util.UUID
import java.util.regex.Pattern

/**
 * Standard Subtitle Cue representing an individual timed caption block.
 */
data class SubtitleCue(
    val id: String = UUID.randomUUID().toString(),
    val startUs: Long,
    val endUs: Long,
    val text: String,
    val speaker: String? = null,
    val confidence: Float = 1.0f
) : Serializable {
    val durationUs: Long get() = (endUs - startUs).coerceAtLeast(100_000L)
}

/**
 * Robust, deterministic Subtitle Parser supporting:
 * - SubRip (.srt) format with HH:mm:ss,SSS and HH:mm:ss.SSS
 * - WebVTT (.vtt) format with WEBVTT headers and formatting tags
 * - Multiline captions
 * - Graceful handling of malformed lines or overlapping intervals
 * - Unicode (Hindi, English, Special characters) preservation
 * - Conversion directly into Canonical [TextClipItem]s
 */
object SubtitleParser {
    private const val TAG = "SubtitleParser"

    // Pattern for SRT timestamp: "00:01:20,000 --> 00:01:23,500"
    private val SRT_TIMESTAMP_PATTERN = Pattern.compile(
        "(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{3})\\s*-->\\s*(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{3})"
    )

    // Pattern for WebVTT timestamp: "00:01:20.000 --> 00:01:23.500" or "01:20.000 --> 01:23.500"
    private val VTT_TIMESTAMP_PATTERN = Pattern.compile(
        "(?:(\\d{1,2}):)?(\\d{2}):(\\d{2})\\.(\\d{3})\\s*-->\\s*(?:(\\d{1,2}):)?(\\d{2}):(\\d{2})\\.(\\d{3})"
    )

    /**
     * Parses an SRT or WebVTT string or stream into a list of [SubtitleCue]s.
     */
    fun parse(content: String): List<SubtitleCue> {
        val lines = content.lines()
        val isVtt = lines.firstOrNull { it.trim().startsWith("WEBVTT") } != null
        return if (isVtt) parseWebVtt(lines) else parseSrt(lines)
    }

    /**
     * Parses from a file safely.
     */
    fun parseFile(file: File): List<SubtitleCue> {
        if (!file.exists() || !file.canRead()) return emptyList()
        return try {
            parse(file.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing subtitle file: ${file.absolutePath}", e)
            emptyList()
        }
    }

    /**
     * Parses from an InputStream safely.
     */
    fun parseStream(inputStream: InputStream): List<SubtitleCue> {
        return try {
            val content = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { it.readText() }
            parse(content)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing subtitle input stream", e)
            emptyList()
        }
    }

    private fun parseSrt(lines: List<String>): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        var currentStartUs: Long? = null
        var currentEndUs: Long? = null
        val currentTextLines = mutableListOf<String>()

        fun flushCurrent() {
            if (currentStartUs != null && currentEndUs != null && currentTextLines.isNotEmpty()) {
                val fullText = currentTextLines.joinToString("\n").trim()
                if (fullText.isNotBlank()) {
                    cues.add(
                        SubtitleCue(
                            startUs = currentStartUs!!,
                            endUs = currentEndUs!!.coerceAtLeast(currentStartUs!! + 100_000L),
                            text = stripFormattingTags(fullText)
                        )
                    )
                }
            }
            currentStartUs = null
            currentEndUs = null
            currentTextLines.clear()
        }

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                flushCurrent()
                continue
            }

            val matcher = SRT_TIMESTAMP_PATTERN.matcher(trimmed)
            if (matcher.find()) {
                // If previous cue wasn't flushed, flush it
                flushCurrent()

                val h1 = matcher.group(1)?.toLongOrNull() ?: 0L
                val m1 = matcher.group(2)?.toLongOrNull() ?: 0L
                val s1 = matcher.group(3)?.toLongOrNull() ?: 0L
                val ms1 = matcher.group(4)?.toLongOrNull() ?: 0L
                currentStartUs = ((h1 * 3600 + m1 * 60 + s1) * 1000 + ms1) * 1000L

                val h2 = matcher.group(5)?.toLongOrNull() ?: 0L
                val m2 = matcher.group(6)?.toLongOrNull() ?: 0L
                val s2 = matcher.group(7)?.toLongOrNull() ?: 0L
                val ms2 = matcher.group(8)?.toLongOrNull() ?: 0L
                currentEndUs = ((h2 * 3600 + m2 * 60 + s2) * 1000 + ms2) * 1000L
            } else if (currentStartUs != null) {
                // Text line belonging to active timestamp
                currentTextLines.add(trimmed)
            }
        }

        flushCurrent()
        return cues
    }

    private fun parseWebVtt(lines: List<String>): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        var currentStartUs: Long? = null
        var currentEndUs: Long? = null
        val currentTextLines = mutableListOf<String>()

        fun flushCurrent() {
            if (currentStartUs != null && currentEndUs != null && currentTextLines.isNotEmpty()) {
                val fullText = currentTextLines.joinToString("\n").trim()
                if (fullText.isNotBlank()) {
                    cues.add(
                        SubtitleCue(
                            startUs = currentStartUs!!,
                            endUs = currentEndUs!!.coerceAtLeast(currentStartUs!! + 100_000L),
                            text = stripFormattingTags(fullText)
                        )
                    )
                }
            }
            currentStartUs = null
            currentEndUs = null
            currentTextLines.clear()
        }

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("WEBVTT") || trimmed.startsWith("NOTE") || trimmed.isEmpty()) {
                if (trimmed.isEmpty()) flushCurrent()
                continue
            }

            val matcher = VTT_TIMESTAMP_PATTERN.matcher(trimmed)
            if (matcher.find()) {
                flushCurrent()

                val h1 = matcher.group(1)?.toLongOrNull() ?: 0L
                val m1 = matcher.group(2)?.toLongOrNull() ?: 0L
                val s1 = matcher.group(3)?.toLongOrNull() ?: 0L
                val ms1 = matcher.group(4)?.toLongOrNull() ?: 0L
                currentStartUs = ((h1 * 3600 + m1 * 60 + s1) * 1000 + ms1) * 1000L

                val h2 = matcher.group(5)?.toLongOrNull() ?: 0L
                val m2 = matcher.group(6)?.toLongOrNull() ?: 0L
                val s2 = matcher.group(7)?.toLongOrNull() ?: 0L
                val ms2 = matcher.group(8)?.toLongOrNull() ?: 0L
                currentEndUs = ((h2 * 3600 + m2 * 60 + s2) * 1000 + ms2) * 1000L
            } else if (currentStartUs != null) {
                currentTextLines.add(trimmed)
            }
        }

        flushCurrent()
        return cues
    }

    /**
     * Removes HTML/VTT style tags like <b>, <i>, <c.color>, etc.
     */
    private fun stripFormattingTags(text: String): String {
        return text.replace(Regex("<[^>]*>"), "")
    }

    /**
     * Converts a list of [SubtitleCue]s directly into canonical [TextClipItem]s for the timeline.
     */
    fun cuesToCanonicalClips(
        cues: List<SubtitleCue>,
        trackId: String = "track_subtitles",
        styleSpec: TextStyleSpec = TextStyleSpec.CLASSIC
    ): List<TextClipItem> {
        return cues.mapIndexed { index, cue ->
            TextClipItem(
                id = cue.id,
                trackId = trackId,
                name = "Caption ${index + 1}",
                startTimeUs = cue.startUs,
                durationUs = cue.durationUs,
                sourceStartUs = 0L,
                sourceDurationUs = cue.durationUs,
                text = cue.text,
                textType = TextType.CAPTION,
                fontName = styleSpec.fontName,
                fontSizeSp = styleSpec.fontSizeSp,
                textColorHex = styleSpec.textColorHex,
                backgroundColorHex = styleSpec.backgroundColorHex,
                strokeColorHex = styleSpec.strokeColorHex,
                strokeWidth = styleSpec.strokeWidthDp,
                shadowColorHex = styleSpec.shadowColorHex,
                shadowRadius = styleSpec.shadowRadiusDp,
                styleSpec = styleSpec,
                positionX = 0.5f,
                positionY = 0.82f,
                zIndex = 45
            )
        }
    }
}
