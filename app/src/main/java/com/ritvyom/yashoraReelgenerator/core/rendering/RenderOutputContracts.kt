package com.ritvyom.yashoraReelgenerator.core.rendering

import android.view.Surface
import com.ritvyom.yashoraReelgenerator.core.model.CanonicalTimeline
import java.io.File
import java.io.Serializable
import kotlinx.coroutines.flow.StateFlow

/**
 * Preview Renderer Contract.
 * Renders the deterministic Render Graph to a live display Surface / TextureView.
 */
interface IPreviewRenderer {
    val isPlaying: StateFlow<Boolean>
    val currentPositionUs: StateFlow<Long>
    val durationUs: StateFlow<Long>

    fun attachSurface(surface: Surface, width: Int, height: Int)
    fun detachSurface()
    fun setTimeline(timeline: CanonicalTimeline)
    fun play()
    fun pause()
    fun seekTo(timeUs: Long)
    fun release()
}

/**
 * Configuration parameters for offline video export.
 */
data class ExportConfig(
    val outputFile: File,
    val width: Int = 1080,
    val height: Int = 1920,
    val fps: Int = 30,
    val videoBitrateBps: Int = 8_000_000,
    val audioBitrateBps: Int = 192_000,
    val audioSampleRate: Int = 44_100,
    val audioChannelCount: Int = 2,
    val videoMimeType: String = "video/avc", // video/avc or video/hevc
    val audioMimeType: String = "audio/mp4a-latm"
) : Serializable

/**
 * Real-time progress status during export.
 */
data class ExportProgress(
    val progressFraction: Float, // 0.0f .. 1.0f
    val currentStage: String,
    val renderedFrames: Int,
    val totalFrames: Int,
    val currentFps: Float = 0f,
    val estimatedRemainingSeconds: Int? = null
) : Serializable

/**
 * Comprehensive Validation Result ensuring the exported MP4 meets all quality criteria.
 */
data class ExportValidationResult(
    val isValid: Boolean,
    val fileExists: Boolean,
    val fileSizeBytes: Long,
    val hasVideoStream: Boolean,
    val hasAudioStream: Boolean,
    val detectedWidth: Int,
    val detectedHeight: Int,
    val detectedDurationUs: Long,
    val detectedFps: Float,
    val canInitializePlayback: Boolean,
    val errorMessage: String? = null
) : Serializable

/**
 * Outcome of an export operation.
 */
sealed class ExportResult {
    data class Success(
        val outputFile: File,
        val validationResult: ExportValidationResult
    ) : ExportResult()

    data class Failure(
        val reason: String,
        val exception: Throwable? = null
    ) : ExportResult()

    object Cancelled : ExportResult()
}

/**
 * Export Renderer Contract.
 * Encodes the deterministic Render Graph to an MP4 container.
 */
interface IExportRenderer {
    val progress: StateFlow<ExportProgress>
    val isRunning: Boolean

    suspend fun exportTimeline(
        timeline: CanonicalTimeline,
        config: ExportConfig,
        onProgress: (ExportProgress) -> Unit
    ): ExportResult

    fun cancel()
}
