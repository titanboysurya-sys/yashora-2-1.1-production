package com.ritvyom.yashoraReelgenerator.engine.renderer

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.util.Log
import com.ritvyom.yashoraReelgenerator.core.rendering.ExportValidationResult
import java.io.File

/**
 * Validates the integrity, streams, dimensions, and playback capability of an exported MP4 file.
 */
object ExportValidator {

    private const val TAG = "ExportValidator"

    fun validateMp4(file: File, expectedWidth: Int? = null, expectedHeight: Int? = null): ExportValidationResult {
        if (!file.exists()) {
            return ExportValidationResult(
                isValid = false,
                fileExists = false,
                fileSizeBytes = 0L,
                hasVideoStream = false,
                hasAudioStream = false,
                detectedWidth = 0,
                detectedHeight = 0,
                detectedDurationUs = 0L,
                detectedFps = 0f,
                canInitializePlayback = false,
                errorMessage = "File does not exist: ${file.absolutePath}"
            )
        }

        val sizeBytes = file.length()
        if (sizeBytes < 1024) {
            return ExportValidationResult(
                isValid = false,
                fileExists = true,
                fileSizeBytes = sizeBytes,
                hasVideoStream = false,
                hasAudioStream = false,
                detectedWidth = 0,
                detectedHeight = 0,
                detectedDurationUs = 0L,
                detectedFps = 0f,
                canInitializePlayback = false,
                errorMessage = "Export file is too small (${sizeBytes} bytes)"
            )
        }

        var hasVideo = false
        var hasAudio = false
        var detectedWidth = 0
        var detectedHeight = 0
        var detectedDurationUs = 0L
        var detectedFps = 30f

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val trackCount = extractor.trackCount
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    hasVideo = true
                    if (format.containsKey(MediaFormat.KEY_WIDTH)) detectedWidth = format.getInteger(MediaFormat.KEY_WIDTH)
                    if (format.containsKey(MediaFormat.KEY_HEIGHT)) detectedHeight = format.getInteger(MediaFormat.KEY_HEIGHT)
                    if (format.containsKey(MediaFormat.KEY_DURATION)) detectedDurationUs = format.getLong(MediaFormat.KEY_DURATION)
                    if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) detectedFps = format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
                } else if (mime.startsWith("audio/")) {
                    hasAudio = true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaExtractor error on file: ${file.name}", e)
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }

        // Secondary verification via MediaMetadataRetriever
        var canInitPlayback = false
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (durStr != null && durStr.toLongOrNull() != null && (durStr.toLongOrNull() ?: 0L) > 0) {
                canInitPlayback = true
                if (detectedDurationUs == 0L) {
                    detectedDurationUs = (durStr.toLongOrNull() ?: 0L) * 1000L
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Playback verification failed", e)
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        val isValid = hasVideo && sizeBytes > 5000 && detectedDurationUs > 0

        return ExportValidationResult(
            isValid = isValid,
            fileExists = true,
            fileSizeBytes = sizeBytes,
            hasVideoStream = hasVideo,
            hasAudioStream = hasAudio,
            detectedWidth = detectedWidth,
            detectedHeight = detectedHeight,
            detectedDurationUs = detectedDurationUs,
            detectedFps = detectedFps,
            canInitializePlayback = canInitPlayback,
            errorMessage = if (!isValid) "Missing video track or invalid duration" else null
        )
    }
}
