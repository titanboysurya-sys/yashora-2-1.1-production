package com.ritvyom.yashoraReelgenerator.presentation.utils

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.ritvyom.yashoraReelgenerator.domain.models.Scene
import java.io.File
import java.nio.ByteBuffer

object Media3VideoTrimmer {
    private const val TAG = "Media3VideoTrimmer"

    /**
     * Retrieves video duration in milliseconds.
     */
    fun getVideoDurationMs(context: Context, videoPath: String): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            if (videoPath.startsWith("content://") || videoPath.startsWith("file://")) {
                retriever.setDataSource(context, Uri.parse(videoPath))
            } else {
                retriever.setDataSource(videoPath)
            }
            val timeStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            timeStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "Error reading video duration", e)
            0L
        } finally {
            try { retriever.release() } catch (e: Exception) {}
        }
    }

    /**
     * Trims video file using Media3 Transformer with automatic fallback to MediaExtractor/MediaMuxer.
     */
    fun trimVideo(
        context: Context,
        inputPath: String,
        outputPath: String,
        startMs: Long,
        endMs: Long,
        onProgress: (Float) -> Unit,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val inputFile = File(inputPath)
        if (!inputFile.exists() || inputFile.length() == 0L) {
            onComplete(false, "Input video file does not exist")
            return
        }

        val durationMs = getVideoDurationMs(context, inputPath)
        val validStartMs = startMs.coerceIn(0L, (durationMs - 500L).coerceAtLeast(0L))
        val validEndMs = endMs.coerceIn(validStartMs + 500L, durationMs.coerceAtLeast(validStartMs + 500L))

        Log.d(TAG, "Trimming video from $validStartMs ms to $validEndMs ms (Total duration: $durationMs ms)")

        val mainHandler = Handler(Looper.getMainLooper())

        val clippingConfig = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(validStartMs)
            .setEndPositionMs(validEndMs)
            .setStartsAtKeyFrame(false)
            .build()

        val uri = if (inputPath.startsWith("content://") || inputPath.startsWith("file://")) {
            Uri.parse(inputPath)
        } else {
            Uri.fromFile(inputFile)
        }

        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setClippingConfiguration(clippingConfig)
            .build()

        val editedMediaItem = EditedMediaItem.Builder(mediaItem).build()
        val outputFile = File(outputPath)
        if (outputFile.exists()) {
            try { outputFile.delete() } catch (e: Exception) {}
        }

        var isFinished = false

        try {
            val progressHolder = ProgressHolder()
            var progressRunnable: Runnable? = null

            val transformer = Transformer.Builder(context)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (isFinished) return
                        isFinished = true
                        Log.d(TAG, "Media3 Transformer trim succeeded: $outputPath")
                        mainHandler.post {
                            onProgress(1.0f)
                            onComplete(true, null)
                        }
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        if (isFinished) return
                        Log.w(TAG, "Media3 Transformer failed: ${exportException.message}. Running fallback extractor trim...", exportException)
                        fallbackTrimExtractor(inputPath, outputPath, validStartMs, validEndMs, onProgress, onComplete)
                    }
                })
                .build()

            progressRunnable = object : Runnable {
                override fun run() {
                    if (isFinished) return
                    try {
                        val progressState = transformer.getProgress(progressHolder)
                        if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
                            val pr = (progressHolder.progress / 100f).coerceIn(0f, 0.99f)
                            mainHandler.post { onProgress(pr) }
                        }
                    } catch (e: Exception) {}
                    mainHandler.postDelayed(this, 100)
                }
            }

            transformer.start(editedMediaItem, outputPath)
            mainHandler.post(progressRunnable)

        } catch (e: Exception) {
            Log.w(TAG, "Media3 Transformer initialization error: ${e.message}. Executing fallback trim...", e)
            fallbackTrimExtractor(inputPath, outputPath, validStartMs, validEndMs, onProgress, onComplete)
        }
    }

    /**
     * Fallback video clipping using native MediaExtractor and MediaMuxer.
     * Guaranteed compatibility across all Android devices/emulators.
     */
    private fun fallbackTrimExtractor(
        inputPath: String,
        outputPath: String,
        startMs: Long,
        endMs: Long,
        onProgress: (Float) -> Unit,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val mainHandler = Handler(Looper.getMainLooper())
        Thread {
            try {
                val extractor = MediaExtractor()
                extractor.setDataSource(inputPath)

                val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val trackCount = extractor.trackCount
                val indexMap = HashMap<Int, Int>()

                val startUs = startMs * 1000L
                val endUs = endMs * 1000L

                for (i in 0 until trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                        extractor.selectTrack(i)
                        val dstIndex = muxer.addTrack(format)
                        indexMap[i] = dstIndex
                    }
                }

                muxer.start()

                // Seek to start position
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

                val bufferSize = 1024 * 1024
                val buffer = ByteBuffer.allocate(bufferSize)
                val bufferInfo = MediaCodec.BufferInfo()

                val totalDurationUs = (endUs - startUs).coerceAtLeast(1L)

                while (true) {
                    bufferInfo.offset = 0
                    bufferInfo.size = extractor.readSampleData(buffer, 0)

                    if (bufferInfo.size < 0) {
                        break
                    }

                    val sampleTimeUs = extractor.sampleTime
                    if (sampleTimeUs > endUs) {
                        break
                    }

                    val trackIndex = extractor.sampleTrackIndex
                    val dstIndex = indexMap[trackIndex]

                    if (dstIndex != null && sampleTimeUs >= startUs) {
                        bufferInfo.presentationTimeUs = sampleTimeUs - startUs
                        bufferInfo.flags = extractor.sampleFlags
                        muxer.writeSampleData(dstIndex, buffer, bufferInfo)

                        val pr = ((sampleTimeUs - startUs).toFloat() / totalDurationUs.toFloat()).coerceIn(0f, 0.99f)
                        mainHandler.post { onProgress(pr) }
                    }

                    extractor.advance()
                }

                muxer.stop()
                muxer.release()
                extractor.release()

                mainHandler.post {
                    onProgress(1.0f)
                    onComplete(true, null)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Fallback MediaMuxer trim failed", e)
                mainHandler.post {
                    onComplete(false, "Trim error: ${e.localizedMessage}")
                }
            }
        }.start()
    }

    /**
     * Media3 Transformer video construction pipeline. Iterates through script sections
     * and assigns a default random background asset from a curated local drawable set or placeholder cache
     * whenever the AI-generated visual search result returns empty.
     */
    fun constructVideoFromScriptSections(
        context: Context,
        scenes: List<Scene>,
        outputPath: String,
        aspectRatio: String = "9:16",
        onProgress: (Float) -> Unit,
        onComplete: (Boolean, String?) -> Unit
    ) {
        if (scenes.isEmpty()) {
            onComplete(false, "No scenes provided")
            return
        }

        val mainHandler = Handler(Looper.getMainLooper())
        val editedMediaItems = mutableListOf<EditedMediaItem>()

        // Iterate through script sections and assign curated local fallback whenever visual search is empty
        scenes.forEachIndexed { idx, scene ->
            var mediaPath = scene.mediaPath ?: ""
            val isLocalAndValid = mediaPath.startsWith("/") && File(mediaPath).exists() && File(mediaPath).length() > 0L
            val isHttpUrl = mediaPath.startsWith("http://") || mediaPath.startsWith("https://")

            if (!isLocalAndValid && !isHttpUrl) {
                Log.w(TAG, "AI visual search returned empty for script section ${idx + 1}. Assigning default asset from curated local drawable set/placeholder cache...")
                mediaPath = LocalAssetFallbackManager.getCuratedFallbackAsset(
                    context = context,
                    sceneIndex = idx,
                    style = scene.visualPrompt,
                    aspectRatio = aspectRatio
                )
            }

            val fileUri = if (mediaPath.startsWith("content://") || mediaPath.startsWith("file://") || mediaPath.startsWith("http")) {
                Uri.parse(mediaPath)
            } else {
                Uri.fromFile(File(mediaPath))
            }

            val mediaItem = MediaItem.Builder()
                .setUri(fileUri)
                .build()

            val media3Effects = Media3FilterEngine.createMedia3Effects(
                filterName = scene.selectedFilterName,
                intensityPercent = scene.filterIntensity
            )
            val effects = androidx.media3.transformer.Effects(
                /* audioProcessors = */ emptyList(),
                /* videoEffects = */ media3Effects
            )
            val editedItem = EditedMediaItem.Builder(mediaItem)
                .setEffects(effects)
                .build()

            editedMediaItems.add(editedItem)
        }

        try {
            val sequence = EditedMediaItemSequence(editedMediaItems)
            val composition = Composition.Builder(listOf(sequence)).build()
            val progressHolder = ProgressHolder()

            var isFinished = false
            val transformer = Transformer.Builder(context)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(comp: Composition, exportResult: ExportResult) {
                        if (isFinished) return
                        isFinished = true
                        Log.d(TAG, "Media3 Transformer construction succeeded: $outputPath")
                        mainHandler.post {
                            onProgress(1.0f)
                            onComplete(true, null)
                        }
                    }

                    override fun onError(comp: Composition, exportResult: ExportResult, exportException: ExportException) {
                        if (isFinished) return
                        isFinished = true
                        Log.w(TAG, "Media3 Transformer error: ${exportException.message}")
                        mainHandler.post {
                            onComplete(false, exportException.localizedMessage)
                        }
                    }
                })
                .build()

            val progressRunnable = object : Runnable {
                override fun run() {
                    if (isFinished) return
                    try {
                        val progressState = transformer.getProgress(progressHolder)
                        if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
                            val pr = (progressHolder.progress / 100f).coerceIn(0f, 0.99f)
                            mainHandler.post { onProgress(pr) }
                        }
                    } catch (e: Exception) {}
                    mainHandler.postDelayed(this, 100)
                }
            }

            transformer.start(composition, outputPath)
            mainHandler.post(progressRunnable)

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Media3 Transformer script sequence composition", e)
            onComplete(false, "Composition initialization failed: ${e.localizedMessage}")
        }
    }
}
