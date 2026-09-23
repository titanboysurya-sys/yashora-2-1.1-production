package com.ritvyom.yashoraReelgenerator.engine.renderer

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import com.ritvyom.yashoraReelgenerator.core.model.CanonicalTimeline
import com.ritvyom.yashoraReelgenerator.core.rendering.ExportConfig
import com.ritvyom.yashoraReelgenerator.core.rendering.ExportProgress
import com.ritvyom.yashoraReelgenerator.core.rendering.ExportResult
import com.ritvyom.yashoraReelgenerator.core.rendering.FrameTime
import com.ritvyom.yashoraReelgenerator.core.rendering.IExportRenderer
import com.ritvyom.yashoraReelgenerator.core.rendering.RenderContext
import com.ritvyom.yashoraReelgenerator.engine.TimelineEvaluator
import com.ritvyom.yashoraReelgenerator.engine.egl.EglCore
import com.ritvyom.yashoraReelgenerator.engine.egl.WindowSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Deterministic Offline Export Renderer.
 * Encodes the shared Render Graph into an MP4 container using MediaCodec + EGL + UnifiedRenderEngine.
 */
class OpenGlExportRenderer(
    private val context: Context
) : IExportRenderer {

    companion object {
        private const val TAG = "OpenGlExportRenderer"
        private const val TIMEOUT_USEC = 10_000L
    }

    private val _progress = MutableStateFlow(ExportProgress(0f, "Idle", 0, 0))
    override val progress: StateFlow<ExportProgress> = _progress.asStateFlow()

    @Volatile
    private var isCancelled = false

    @Volatile
    private var running = false
    override val isRunning: Boolean get() = running

    override suspend fun exportTimeline(
        timeline: CanonicalTimeline,
        config: ExportConfig,
        onProgress: (ExportProgress) -> Unit
    ): ExportResult = withContext(Dispatchers.Default) {
        running = true
        isCancelled = false

        val evaluator = TimelineEvaluator(timeline)
        val durationUs = timeline.totalDurationUs.coerceAtLeast(1_000_000L)
        val fps = config.fps.coerceIn(15, 60)
        val frameIntervalUs = 1_000_000L / fps
        val totalFrames = ((durationUs + frameIntervalUs - 1) / frameIntervalUs).toInt().coerceAtLeast(1)

        val outputFile = config.outputFile
        if (outputFile.exists()) outputFile.delete()
        outputFile.parentFile?.mkdirs()

        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var eglCore: EglCore? = null
        var encoderSurface: WindowSurface? = null
        var renderEngine: UnifiedRenderEngine? = null

        try {
            updateProgress(0f, "Configuring hardware encoder", 0, totalFrames, onProgress)

            // 1. Configure MediaCodec video encoder
            val format = MediaFormat.createVideoFormat(config.videoMimeType, config.width, config.height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, config.videoBitrateBps)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            encoder = MediaCodec.createEncoderByType(config.videoMimeType)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

            val inputSurface = encoder.createInputSurface()
            encoder.start()

            // 2. Setup EGL on the encoder input surface
            eglCore = EglCore(flags = EglCore.FLAG_RECORDABLE)
            encoderSurface = WindowSurface(eglCore, inputSurface, releaseSurface = true)
            encoderSurface.makeCurrent()

            // 3. Initialize Unified Render Engine
            renderEngine = UnifiedRenderEngine(context)
            renderEngine.initialize()

            // 4. Initialize MediaMuxer
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var videoTrackIndex = -1
            var muxerStarted = false
            val bufferInfo = MediaCodec.BufferInfo()

            val renderContext = RenderContext(
                canvasConfig = timeline.canvasConfig,
                viewportWidth = config.width,
                viewportHeight = config.height,
                isOfflineExport = true
            )

            val startTimeMs = System.currentTimeMillis()

            // 5. Deterministic Frame-by-Frame Rendering Loop
            for (frameIndex in 0 until totalFrames) {
                if (isCancelled) {
                    return@withContext ExportResult.Cancelled
                }

                val timeUs = frameIndex * frameIntervalUs
                val frameTime = FrameTime(
                    presentationTimeUs = timeUs,
                    frameIndex = frameIndex,
                    fps = fps,
                    totalDurationUs = durationUs
                )

                // Evaluate the EXACT same Render Graph
                val nodes = evaluator.evaluateNodesForTime(frameTime, renderContext)

                // Render through the EXACT same Unified Shader
                renderEngine.renderFrame(nodes, renderContext)

                // Tag presentation timestamp and swap buffer into encoder
                encoderSurface.setPresentationTime(timeUs * 1000L) // Nanoseconds
                encoderSurface.swapBuffers()

                // Drain encoder outputs
                var encoderOutputAvailable = true
                while (encoderOutputAvailable) {
                    val outIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
                    when {
                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (muxerStarted) throw RuntimeException("Format changed after muxer start")
                            val newFormat = encoder.outputFormat
                            videoTrackIndex = muxer.addTrack(newFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                        outIndex >= 0 -> {
                            val encodedData = encoder.getOutputBuffer(outIndex)
                            if (encodedData != null && bufferInfo.size > 0 && muxerStarted) {
                                encodedData.position(bufferInfo.offset)
                                encodedData.limit(bufferInfo.offset + bufferInfo.size)
                                muxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                            }
                            encoder.releaseOutputBuffer(outIndex, false)
                        }
                        else -> encoderOutputAvailable = false
                    }
                }

                // Progress update
                if (frameIndex % 5 == 0 || frameIndex == totalFrames - 1) {
                    val progressFraction = (frameIndex + 1).toFloat() / totalFrames
                    val elapsedMs = System.currentTimeMillis() - startTimeMs
                    val currentFps = if (elapsedMs > 0) ((frameIndex + 1) * 1000f / elapsedMs) else 0f
                    val remainingFrames = totalFrames - (frameIndex + 1)
                    val estRemainingSec = if (currentFps > 0) (remainingFrames / currentFps).toInt() else null

                    updateProgress(
                        progressFraction = progressFraction,
                        stage = "Rendering frames (${frameIndex + 1}/$totalFrames)",
                        rendered = frameIndex + 1,
                        total = totalFrames,
                        fps = currentFps,
                        etaSec = estRemainingSec,
                        callback = onProgress
                    )
                }
            }

            // 6. Signal End of Stream
            encoder.signalEndOfInputStream()

            // 7. Drain remaining frames until EOS
            var eos = false
            while (!eos) {
                val outIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            videoTrackIndex = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                    outIndex >= 0 -> {
                        val encodedData = encoder.getOutputBuffer(outIndex)
                        if (encodedData != null && bufferInfo.size > 0 && muxerStarted) {
                            encodedData.position(bufferInfo.offset)
                            encodedData.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                        }
                        encoder.releaseOutputBuffer(outIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            eos = true
                        }
                    }
                    else -> eos = true
                }
            }

            // 8. Stop & Clean up encoder and muxer
            try { encoder.stop() } catch (_: Exception) {}
            try {
                if (muxerStarted) muxer.stop()
            } catch (e: Exception) {
                Log.w(TAG, "Muxer stop warning", e)
            }

            // 9. Validate Output
            updateProgress(1.0f, "Validating MP4 output", totalFrames, totalFrames, onProgress)
            val validation = ExportValidator.validateMp4(outputFile, config.width, config.height)

            if (validation.isValid) {
                Log.i(TAG, "Export succeeded: ${outputFile.name}, size=${validation.fileSizeBytes}, dur=${validation.detectedDurationUs}us")
                ExportResult.Success(outputFile, validation)
            } else {
                Log.e(TAG, "Export validation failed: ${validation.errorMessage}")
                ExportResult.Failure("Export validation failed: ${validation.errorMessage}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Export failed with exception", e)
            ExportResult.Failure(e.message ?: "Unknown export error", e)
        } finally {
            try { renderEngine?.release() } catch (_: Exception) {}
            try { encoderSurface?.release() } catch (_: Exception) {}
            try { eglCore?.release() } catch (_: Exception) {}
            try { encoder?.release() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            running = false
        }
    }

    override fun cancel() {
        isCancelled = true
    }

    private fun updateProgress(
        progressFraction: Float,
        stage: String,
        rendered: Int,
        total: Int,
        callback: (ExportProgress) -> Unit,
        fps: Float = 0f,
        etaSec: Int? = null
    ) {
        val p = ExportProgress(
            progressFraction = progressFraction.coerceIn(0f, 1f),
            currentStage = stage,
            renderedFrames = rendered,
            totalFrames = total,
            currentFps = fps,
            estimatedRemainingSeconds = etaSec
        )
        _progress.value = p
        callback(p)
    }
}
