package com.ritvyom.yashoraReelgenerator.presentation.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import com.ritvyom.yashoraReelgenerator.domain.models.Scene
import com.ritvyom.yashoraReelgenerator.domain.models.getCanvasLayers
import com.ritvyom.yashoraReelgenerator.domain.models.WatermarkConfig
import com.ritvyom.yashoraReelgenerator.domain.models.WatermarkPosition
import com.ritvyom.yashoraReelgenerator.domain.models.WatermarkFontFamily
import com.ritvyom.yashoraReelgenerator.domain.models.WatermarkStyle
import android.graphics.Typeface
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URL
import java.nio.ByteBuffer
import kotlin.math.sin

object ReelVideoCompiler {
    private const val TAG = "ReelVideoCompiler"
    private const val FRAME_RATE = 30 // Smooth 30fps video
    private const val BIT_RATE = 6000000 // High quality 6.0 Mbps for crisp 1080p

    // High-performance precomputed Lookup Tables (LUTs) for instantaneous RGB to YUV420 conversions
    private val TABLE_Y_R = IntArray(256) { r -> 66 * r }
    private val TABLE_Y_G = IntArray(256) { g -> 129 * g }
    private val TABLE_Y_B = IntArray(256) { b -> 25 * b + 128 + 4096 } // includes +16 after shr 8

    private val TABLE_U_R = IntArray(256) { r -> -38 * r }
    private val TABLE_U_G = IntArray(256) { g -> -74 * g }
    private val TABLE_U_B = IntArray(256) { b -> 112 * b + 128 + 32768 } // includes +128 after shr 8

    private val TABLE_V_R = IntArray(256) { r -> 112 * r }
    private val TABLE_V_G = IntArray(256) { g -> -94 * g }
    private val TABLE_V_B = IntArray(256) { b -> -18 * b + 128 + 32768 } // includes +128 after shr 8

    /**
     * High-Performance Image & Video Frame Clarity Enhancer
     * Applies adaptive unsharp mask sharpening (Laplacian edge convolution),
     * dynamic micro-contrast tone curves, and rich color vibrance.
     */
    fun enhanceBitmapHighClarity(src: Bitmap, sharpnessStrength: Float = 0.45f): Bitmap {
        val width = src.width
        val height = src.height
        if (width <= 2 || height <= 2) return src

        val inPixels = IntArray(width * height)
        src.getPixels(inPixels, 0, width, 0, 0, width, height)
        val outPixels = IntArray(width * height)

        // Fast Micro-Contrast & Dynamic Range LUT
        val contrastLUT = IntArray(256) { i ->
            val normalized = i / 255.0f
            val enhanced = if (normalized < 0.5f) {
                (2.0f * normalized * normalized * 0.94f) + (0.06f * normalized)
            } else {
                (1.0f - 2.0f * (1.0f - normalized) * (1.0f - normalized) * 0.94f) + (0.06f * (normalized - 0.5f))
            }
            (enhanced * 255f).toInt().coerceIn(0, 255)
        }

        for (y in 0 until height) {
            val rowOffset = y * width
            val isEdgeY = (y == 0 || y == height - 1)
            val upOffset = if (y > 0) (y - 1) * width else rowOffset
            val downOffset = if (y < height - 1) (y + 1) * width else rowOffset

            for (x in 0 until width) {
                val idx = rowOffset + x
                if (isEdgeY || x == 0 || x == width - 1) {
                    outPixels[idx] = inPixels[idx]
                    continue
                }

                val c = inPixels[idx]
                val l = inPixels[idx - 1]
                val r = inPixels[idx + 1]
                val u = inPixels[upOffset + x]
                val d = inPixels[downOffset + x]

                val a = (c ushr 24) and 0xFF
                val cr = (c ushr 16) and 0xFF
                val cg = (c ushr 8) and 0xFF
                val cb = c and 0xFF

                // 2D Laplacian High-Frequency Unsharp Delta
                val deltaR = (4 * cr - ((l ushr 16) and 0xFF) - ((r ushr 16) and 0xFF) - ((u ushr 16) and 0xFF) - ((d ushr 16) and 0xFF)).coerceIn(-64, 64)
                val deltaG = (4 * cg - ((l ushr 8) and 0xFF) - ((r ushr 8) and 0xFF) - ((u ushr 8) and 0xFF) - ((d ushr 8) and 0xFF)).coerceIn(-64, 64)
                val deltaB = (4 * cb - (l and 0xFF) - (r and 0xFF) - (u and 0xFF) - (d and 0xFF)).coerceIn(-64, 64)

                val sr = (cr + (deltaR * sharpnessStrength)).toInt().coerceIn(0, 255)
                val sg = (cg + (deltaG * sharpnessStrength)).toInt().coerceIn(0, 255)
                val sb = (cb + (deltaB * sharpnessStrength)).toInt().coerceIn(0, 255)

                // Vibrancy enhancement (+12%) to remove haziness
                val avg = (sr + sg + sb) / 3
                val vr = (avg + (sr - avg) * 1.12f).toInt().coerceIn(0, 255)
                val vg = (avg + (sg - avg) * 1.12f).toInt().coerceIn(0, 255)
                val vb = (avg + (sb - avg) * 1.12f).toInt().coerceIn(0, 255)

                val finalR = contrastLUT[vr]
                val finalG = contrastLUT[vg]
                val finalB = contrastLUT[vb]

                outPixels[idx] = (a shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
            }
        }

        return Bitmap.createBitmap(outPixels, width, height, Bitmap.Config.ARGB_8888)
    }

    // Backward-compatible wrapper
    fun compileVideo(
        context: Context,
        scenes: List<Scene>,
        outputFile: File,
        onProgress: (Float) -> Unit
    ): Boolean {
        return compileVideoWithAudio(
            context = context,
            scenes = scenes,
            voiceFiles = emptyList(),
            aspectRatio = "9:16",
            outputFile = outputFile,
            onProgress = onProgress
        )
    }

    // Backward-compatible overload
    fun compileVideoWithAudio(
        context: Context,
        scenes: List<Scene>,
        voiceFiles: List<File?>,
        aspectRatio: String,
        outputFile: File,
        onProgress: (Float) -> Unit
    ): Boolean {
        return compileVideoWithAudio(
            context = context,
            scenes = scenes,
            voiceFiles = voiceFiles,
            aspectRatio = aspectRatio,
            resolution = "1080p",
            outputFile = outputFile,
            bgMusicCategory = "Cinematic",
            bgMusicVolume = 0.3f,
            bgMusicEnabled = true,
            onProgress = onProgress
        )
    }

    private fun renderWatermarkOnCanvas(
        canvas: Canvas,
        width: Int,
        height: Int,
        scaleFactor: Float,
        watermarkConfig: WatermarkConfig?
    ) {
        if (watermarkConfig == null || !watermarkConfig.isEnabled) return
        val rawText = watermarkConfig.text.trim()
        val text = if (rawText.isNotEmpty()) rawText else "Yashora AI Reel Engine"

        val wmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            val baseColor = try {
                Color.parseColor(watermarkConfig.colorHex)
            } catch (e: Exception) {
                Color.WHITE
            }
            color = baseColor
            alpha = (watermarkConfig.opacity.coerceIn(0.1f, 1.0f) * 255).toInt()
            textSize = (watermarkConfig.fontSizeSp.coerceIn(8, 36) * scaleFactor)
            typeface = when (watermarkConfig.fontFamily) {
                WatermarkFontFamily.SANS_SERIF -> Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                WatermarkFontFamily.BOLD_MODERN -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                WatermarkFontFamily.SERIF -> Typeface.create(Typeface.SERIF, Typeface.NORMAL)
                WatermarkFontFamily.MONOSPACE -> Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                WatermarkFontFamily.CURSIVE -> Typeface.create("casual", Typeface.ITALIC)
            }
        }

        val marginX = 28f * scaleFactor
        val marginY = 38f * scaleFactor
        val metrics = wmPaint.fontMetrics
        val textHeight = metrics.descent - metrics.ascent
        val textWidth = wmPaint.measureText(text)

        var x = marginX
        var y = height - marginY
        var align = Paint.Align.LEFT

        when (watermarkConfig.position) {
            WatermarkPosition.BOTTOM_LEFT -> {
                align = Paint.Align.LEFT
                x = marginX
                y = height - marginY
            }
            WatermarkPosition.BOTTOM_RIGHT -> {
                align = Paint.Align.RIGHT
                x = width - marginX
                y = height - marginY
            }
            WatermarkPosition.BOTTOM_CENTER -> {
                align = Paint.Align.CENTER
                x = width / 2f
                y = height - marginY
            }
            WatermarkPosition.TOP_LEFT -> {
                align = Paint.Align.LEFT
                x = marginX
                y = marginY + textHeight
            }
            WatermarkPosition.TOP_RIGHT -> {
                align = Paint.Align.RIGHT
                x = width - marginX
                y = marginY + textHeight
            }
            WatermarkPosition.TOP_CENTER -> {
                align = Paint.Align.CENTER
                x = width / 2f
                y = marginY + textHeight
            }
            WatermarkPosition.CENTER -> {
                align = Paint.Align.CENTER
                x = width / 2f
                y = (height / 2f) + (textHeight / 4f)
            }
        }
        wmPaint.textAlign = align

        when (watermarkConfig.style) {
            WatermarkStyle.PILL_BADGE -> {
                val boxPadX = 12f * scaleFactor
                val boxPadY = 6f * scaleFactor
                val left = when (align) {
                    Paint.Align.LEFT -> x - boxPadX
                    Paint.Align.RIGHT -> x - textWidth - boxPadX
                    Paint.Align.CENTER -> x - (textWidth / 2f) - boxPadX
                }
                val top = y + metrics.ascent - boxPadY
                val right = left + textWidth + (boxPadX * 2f)
                val bottom = y + metrics.descent + boxPadY

                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    alpha = (watermarkConfig.opacity * 0.65f * 255).toInt().coerceIn(40, 220)
                    style = Paint.Style.FILL
                }
                val pillRect = RectF(left, top, right, bottom)
                canvas.drawRoundRect(pillRect, 8f * scaleFactor, 8f * scaleFactor, bgPaint)
                wmPaint.clearShadowLayer()
            }
            WatermarkStyle.GLOW -> {
                val glowColor = try { Color.parseColor(watermarkConfig.colorHex) } catch (e: Exception) { Color.WHITE }
                wmPaint.setShadowLayer(10f * scaleFactor, 0f, 0f, glowColor)
            }
            WatermarkStyle.SHADOW -> {
                wmPaint.setShadowLayer(4f * scaleFactor, 2f * scaleFactor, 2f * scaleFactor, Color.BLACK)
            }
            WatermarkStyle.NONE -> {
                wmPaint.clearShadowLayer()
            }
        }

        canvas.drawText(text, x, y, wmPaint)
        wmPaint.clearShadowLayer()
    }

    // Major upgrade helper coordinating compile, synthesis, and audio-video mix merge with custom resolution overrides
    fun compileVideoWithAudio(
        context: Context,
        scenes: List<Scene>,
        voiceFiles: List<File?>,
        aspectRatio: String,
        resolution: String,
        outputFile: File,
        bgMusicCategory: String = "Cinematic",
        bgMusicVolume: Float = 0.3f,
        bgMusicEnabled: Boolean = true,
        preserveOriginalAudio: Boolean = true,
        isEnhanceVideo: Boolean = false,
        isEnhanceEnabled: Boolean = isEnhanceVideo,
        watermarkConfig: WatermarkConfig? = null,
        onProgress: (Float) -> Unit
    ): Boolean {
        if (scenes.isEmpty()) return false

        val effectiveEnhance = isEnhanceEnabled || isEnhanceVideo

        // Determine dynamic canvas size classes mapped safely to selected resolution limit
        val baseShortSide = when {
            resolution.contains("360") -> 360
            resolution.contains("480") -> 480
            resolution.contains("540") -> 540
            resolution.contains("720") -> 720
            resolution.contains("2K") || resolution.contains("1440") -> 1440
            resolution.contains("4K") || resolution.contains("2160") -> 2160
            resolution.contains("8K") || resolution.contains("4320") -> 4320
            else -> 1080
        }

        val (rawW, rawH) = when (aspectRatio) {
            "9:16" -> Pair(baseShortSide, (baseShortSide * 16.0 / 9.0).toInt())
            "16:9" -> Pair((baseShortSide * 16.0 / 9.0).toInt(), baseShortSide)
            "1:1" -> Pair(baseShortSide, baseShortSide)
            "4:5" -> Pair(baseShortSide, (baseShortSide * 5.0 / 4.0).toInt())
            "3:4" -> Pair(baseShortSide, (baseShortSide * 4.0 / 3.0).toInt())
            "21:9" -> Pair((baseShortSide * 21.0 / 9.0).toInt(), baseShortSide)
            else -> Pair(baseShortSide, (baseShortSide * 16.0 / 9.0).toInt())
        }

        val width = (Math.round(rawW.toDouble() / 16.0).toInt() * 16).coerceAtLeast(16)
        val height = (Math.round(rawH.toDouble() / 16.0).toInt() * 16).coerceAtLeast(16)
        Log.d(TAG, "Compiling video with dimensions: ${width}x${height} in resolution $resolution and aspect ratio: $aspectRatio (Enhance: $effectiveEnhance)")

        // Step 1: Compile silent video track
        val tempSilentVideo = File(context.cacheDir, "temp_silent_${System.currentTimeMillis()}.mp4")
        val videoSuccess = compileSilentVideo(context, scenes, voiceFiles, width, height, tempSilentVideo, effectiveEnhance, watermarkConfig) { pr ->
            onProgress(0.1f + 0.5f * pr) // spans from 10% to 60% of total export pipeline
        }

        if (!videoSuccess) {
            Log.e(TAG, "Compilation of raw video track failed.")
            return false
        }

        // Step 2: Combine voice waveforms into one synchronized raw pcm format
        val tempPcmAudio = File(context.cacheDir, "temp_audio_${System.currentTimeMillis()}.pcm")
        val (sampleRate, audioSuccess) = generateTimelineAudio(
            context = context,
            voiceFiles = voiceFiles,
            scenes = scenes,
            bgMusicCategory = bgMusicCategory,
            bgMusicVolume = bgMusicVolume,
            bgMusicEnabled = bgMusicEnabled,
            preserveOriginalAudio = preserveOriginalAudio,
            pcmOutputFile = tempPcmAudio
        )

        // Step 3: Encode raw audio PCM to compressed high compatibility AAC file (Stereo)
        val tempAacAudio = File(context.cacheDir, "temp_audio_${System.currentTimeMillis()}.m4a")
        var pcmEncodingSuccess = false
        if (audioSuccess && tempPcmAudio.exists() && tempPcmAudio.length() > 0) {
            Log.d(TAG, "Successfully prepared timeline audio PCM. Initiating AAC audio encoding (Stereo)...")
            pcmEncodingSuccess = encodePcmToAac(tempPcmAudio, tempAacAudio, sampleRate, 2)
        }

        // Step 4: Final multiplex/remux to join audio and video tracks into MP4
        var compileSuccess = false
        if (pcmEncodingSuccess && tempAacAudio.exists() && tempAacAudio.length() > 0) {
            Log.d(TAG, "Muxing video track & narration track into high-fidelity result file...")
            onProgress(0.9f)
            
            if (FFmpegVideoWrapper.isFFmpegAvailable()) {
                Log.d(TAG, "Attempting multiplexing via FFmpeg wrapper...")
                compileSuccess = FFmpegVideoWrapper.executeFFmpegMux(tempSilentVideo, tempAacAudio, outputFile)
                if (!compileSuccess) {
                    Log.w(TAG, "FFmpeg wrapper mux failed. Falling back to native MediaMuxer.")
                    compileSuccess = mergeVideoAndAudio(tempSilentVideo, tempAacAudio, outputFile)
                }
            } else {
                Log.d(TAG, "FFmpeg is not available. Using native MediaMuxer.")
                compileSuccess = mergeVideoAndAudio(tempSilentVideo, tempAacAudio, outputFile)
            }
        } else {
            // Safe fallback: copy silent video if speech process was empty or failed
            Log.w(TAG, "Audio compiling skipped/failed, fallback to storing silent video.")
            try {
                tempSilentVideo.copyTo(outputFile, overwrite = true)
                compileSuccess = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy clean silent fallback file", e)
            }
        }

        // Step 5: Clean up all temporary files safely
        try { tempSilentVideo.delete() } catch (e: Exception) {}
        try { tempPcmAudio.delete() } catch (e: Exception) {}
        try { tempAacAudio.delete() } catch (e: Exception) {}
        for (f in voiceFiles) {
            try { f?.delete() } catch (e: Exception) {}
        }

        return compileSuccess
    }

    private fun compileSilentVideo(
        context: Context,
        scenes: List<Scene>,
        voiceFiles: List<File?>,
        width: Int,
        height: Int,
        outputFile: File,
        isEnhanceVideo: Boolean = false,
        watermarkConfig: WatermarkConfig? = null,
        onProgress: (Float) -> Unit
    ): Boolean {
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        val tempVideoFiles = ArrayList<File?>()
        val bitmaps = ArrayList<Bitmap?>()
        var renderBitmap: Bitmap? = null

        try {
            // Step A: Pre-download scene assets (bitmaps or temporary moving video clips)
            
            for (i in scenes.indices) {
                val scene = scenes[i]
                var bmp: Bitmap? = null
                var path = scene.mediaPath
                var videoFile: File? = null
                
                if (!path.isNullOrEmpty()) {
                    if (path.contains("1554050857-c84a8abdb5e4")) {
                        path = path.replace("1554050857-c84a8abdb5e4", "1579783902614-a3fb3927b6a5")
                    }
                    
                    val isVideo = isVideoPath(context, path)
                    if (isVideo) {
                        val isLocal = path.startsWith("/") && File(path).exists()
                        if (isLocal) {
                            videoFile = File(path)
                            Log.d(TAG, "Using existing local moving video background for scene ${i + 1}: $path")
                        } else {
                            val localExistingFile = com.ritvyom.yashoraReelgenerator.data.repository.ProjectRepository.findExistingLocalMediaFile(context, path)
                            if (localExistingFile != null && localExistingFile.exists() && localExistingFile.length() > 0) {
                                videoFile = localExistingFile
                                Log.d(TAG, "Using existing local app storage moving video background for scene ${i + 1}, bypassing download: ${localExistingFile.absolutePath}")
                            } else {
                                val cachedVideoFile = getCacheFileForUrl(context, path, "mp4")
                                if (cachedVideoFile.exists() && cachedVideoFile.length() > 0) {
                                    videoFile = cachedVideoFile
                                    Log.d(TAG, "Using locally cached moving video background for scene ${i + 1}, bypassing download: ${cachedVideoFile.absolutePath}")
                                } else {
                                    val tempFile = File(context.cacheDir, "scene_video_${i}_${System.currentTimeMillis()}.mp4")
                                    val downloadSuccess = downloadFile(context, path, tempFile)
                                    if (downloadSuccess && tempFile.exists() && tempFile.length() > 0) {
                                        // Save copy to cache
                                        try {
                                            tempFile.copyTo(cachedVideoFile, overwrite = true)
                                            videoFile = tempFile
                                            Log.d(TAG, "Successfully downloaded moving video background for scene ${i + 1} and saved to cache: ${tempFile.length()} bytes")
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Failed caching moving video background file: $path", e)
                                            videoFile = tempFile
                                        }
                                    } else {
                                        try { tempFile.delete() } catch(e: Exception) {}
                                    }
                                }
                            }
                        }
                    }
                    
                    if (videoFile == null) {
                        bmp = downloadBitmap(context, path)
                        if (bmp == null) {
                            Log.w(TAG, "Primary download failed for scene ${i + 1}, attempting robust abstract fallback 1...")
                            bmp = downloadBitmap(context, "https://images.unsplash.com/photo-1579546929518-9e396f3cc809?auto=format&fit=crop&w=600&q=80") // Pastel gradient
                            if (bmp == null) {
                                Log.w(TAG, "Fallback 1 failed, trying robust backup scenic scenery fallback 2...")
                                bmp = downloadBitmap(context, "https://images.unsplash.com/photo-1508739773434-c26b3d09e071?auto=format&fit=crop&w=600&q=80") // Scenic forest
                            }
                        }
                    } else if (bmp == null) {
                        try {
                            val retriever = android.media.MediaMetadataRetriever()
                            retriever.setDataSource(videoFile.absolutePath)
                            bmp = retriever.getScaledFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST, width, height)
                                ?: retriever.getFrameAtTime(0)
                            retriever.release()
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed extracting preview frame from video file for scene ${i + 1}", e)
                        }
                        if (bmp == null) {
                            bmp = downloadBitmap(context, "https://images.unsplash.com/photo-1579546929518-9e396f3cc809?auto=format&fit=crop&w=600&q=80")
                        }
                    }
                } else if (bmp == null) {
                    bmp = downloadBitmap(context, "https://images.unsplash.com/photo-1579546929518-9e396f3cc809?auto=format&fit=crop&w=600&q=80")
                }

                // Smart Retry Failsafe: Guarantee bitmap is NEVER null to avoid empty frames in output
                if (bmp == null) {
                    Log.w(TAG, "Smart Retry Failsafe: Primary asset downloads failed for scene ${i + 1}. Attempting curated local drawable fallback asset...")
                    val targetAspectRatio = if (width > height) "16:9" else if (width == height) "1:1" else "9:16"
                    val fallbackPath = LocalAssetFallbackManager.getCuratedFallbackAsset(
                        context = context,
                        sceneIndex = i,
                        style = scene.visualPrompt,
                        aspectRatio = targetAspectRatio
                    )
                    if (fallbackPath.isNotEmpty() && java.io.File(fallbackPath).exists()) {
                        bmp = BitmapFactory.decodeFile(fallbackPath)
                    }
                }
                if (bmp == null) {
                    Log.w(TAG, "Smart Retry Failsafe: Attempting Picsum seed retry...")
                    val seed = kotlin.math.abs((scene.visualPrompt + i).hashCode())
                    bmp = downloadBitmap(context, "https://picsum.photos/seed/$seed/$width/$height")
                }
                if (bmp == null) {
                    Log.w(TAG, "Smart Retry Failsafe: Generating procedural fallback canvas bitmap for scene ${i + 1}...")
                    bmp = createProceduralFallbackBitmap(width, height, i)
                }

                // Performance Optimization: Pre-scale large bitmaps once during loading to prevent heavy downscaling on every single frame
                if (bmp != null) {
                    val maxWorkDim = (Math.max(width, height) * 1.15f).toInt()
                    if (bmp.width > maxWorkDim || bmp.height > maxWorkDim) {
                        val scale = maxWorkDim.toFloat() / Math.max(bmp.width, bmp.height)
                        val targetW = (bmp.width * scale).toInt().coerceAtLeast(1)
                        val targetH = (bmp.height * scale).toInt().coerceAtLeast(1)
                        val scaled = Bitmap.createScaledBitmap(bmp, targetW, targetH, true)
                        bmp.recycle()
                        bmp = scaled
                    }
                    if (isEnhanceVideo) {
                        val sharpBmp = enhanceBitmapHighClarity(bmp)
                        if (sharpBmp != bmp) {
                            bmp.recycle()
                            bmp = sharpBmp
                        }
                    }
                }

                bitmaps.add(bmp)
                tempVideoFiles.add(videoFile)
            }

            // Step B: Setup MediaCodec encoder
            val mimeType = MediaFormat.MIMETYPE_VIDEO_AVC
            val baseBitRate = when {
                width <= 480 -> 1800000
                width <= 720 -> 3500000
                width <= 1080 -> 8000000
                width <= 1440 -> 14000000
                else -> 22000000
            }
            val calculatedBitRate = if (isEnhanceVideo) {
                // Allocate high bitrate to preserve sharp micro-details, crisp edges and prevent macroblocking
                (baseBitRate * 1.75f).toInt()
            } else {
                baseBitRate
            }

            encoder = MediaCodec.createEncoderByType(mimeType)
            val chosenColorFormat = selectColorFormat(encoder, mimeType)
            Log.d(TAG, "Selected MediaCodec color format: $chosenColorFormat, width=$width, height=$height, bitRate=$calculatedBitRate (Enhanced: $isEnhanceVideo)")

            // Create format with dynamic profile and level matching resolution to prevent MediaCodec failures
            val format = MediaFormat.createVideoFormat(mimeType, width, height).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, calculatedBitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                setInteger(MediaFormat.KEY_COLOR_FORMAT, chosenColorFormat)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    setInteger(MediaFormat.KEY_BITRATE_MODE, android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
                    setInteger(MediaFormat.KEY_PRIORITY, 0)
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    val maxDim = Math.max(width, height)
                    val level = when {
                        maxDim <= 720 -> android.media.MediaCodecInfo.CodecProfileLevel.AVCLevel31
                        maxDim <= 1280 -> android.media.MediaCodecInfo.CodecProfileLevel.AVCLevel31
                        maxDim <= 1920 -> android.media.MediaCodecInfo.CodecProfileLevel.AVCLevel4
                        maxDim <= 2560 -> android.media.MediaCodecInfo.CodecProfileLevel.AVCLevel5
                        else -> android.media.MediaCodecInfo.CodecProfileLevel.AVCLevel51
                    }
                    val profile = if (isEnhanceVideo) {
                        android.media.MediaCodecInfo.CodecProfileLevel.AVCProfileHigh
                    } else {
                        android.media.MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline
                    }
                    setInteger(MediaFormat.KEY_PROFILE, profile)
                    setInteger(MediaFormat.KEY_LEVEL, level)
                }
            }

            try {
                encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            } catch (e: Exception) {
                Log.w(TAG, "Dynamic profile/level configuration failed. Retrying with basic standard compatibility format.", e)
                try {
                    val fallbackFormat = MediaFormat.createVideoFormat(mimeType, width, height).apply {
                        setInteger(MediaFormat.KEY_BIT_RATE, calculatedBitRate)
                        setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
                        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                        setInteger(MediaFormat.KEY_COLOR_FORMAT, chosenColorFormat)
                    }
                    encoder.configure(fallbackFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                } catch (ex: Exception) {
                    Log.e(TAG, "All MediaCodec configuration attempts failed.", ex)
                    throw ex
                }
            }
            encoder.start()

            // Setup MediaMuxer
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var trackIndex = -1
            var muxerStarted = false

            // Step C: Draw frames and send to codec
            val bufferInfo = MediaCodec.BufferInfo()
            var presentationTimeUs = 0L
            val frameDurationUs = 1000000L / FRAME_RATE

            val scaleFactor = width.toFloat() / 576f

            renderBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(renderBitmap!!)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            // High performance pre-allocated buffers to prevent GC stuttering during fast frames rendering
            val argbBuffer = IntArray(width * height)
            val yuvBuffer = ByteArray(width * height * 3 / 2)

            // Text paints dynamically scaled proportional to resolution
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 20f * scaleFactor
                textAlign = Paint.Align.CENTER
                style = Paint.Style.FILL
            }
            val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 24f * scaleFactor
                textAlign = Paint.Align.CENTER
                style = Paint.Style.FILL
            }
            val backgroundBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#99000000") // Black translucent
                style = Paint.Style.FILL
            }
            val brandingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                alpha = 110
                textSize = 14f * scaleFactor
                textAlign = Paint.Align.LEFT
                style = Paint.Style.FILL
            }

            var sampleRate = 16000
            var channels = 1
            var bitsPerSample = 16

            for (f in voiceFiles) {
                if (f != null && f.exists() && f.length() >= 44) {
                    val info = WavFileInfo.readWavFile(f)
                    if (info != null) {
                        sampleRate = info.sampleRate
                        channels = info.channels
                        bitsPerSample = info.bitsPerSample
                        break
                    }
                }
            }
            val bytesPerSecond = sampleRate * channels * (bitsPerSample / 8)

            // Pre-calculate total frames across entire reel for high-precision frame progress and ETA monitoring
            var totalDurationEstimate = 0.0
            for (idx in scenes.indices) {
                val f = voiceFiles.getOrNull(idx)
                val voiceDurationFloat = if (f != null && f.exists() && f.length() >= 44) {
                    val info = WavFileInfo.readWavFile(f)
                    if (info != null && info.pcmData != null && info.pcmData.size > 0) {
                        val wavBytesPerSec = info.sampleRate * info.channels * (info.bitsPerSample / 8)
                        info.pcmData.size.toDouble() / wavBytesPerSec
                    } else 0.0
                } else 0.0
                val effectiveDuration = if (voiceDurationFloat > 0.0) voiceDurationFloat else scenes[idx].durationSeconds.toDouble().coerceAtLeast(1.0)
                totalDurationEstimate += effectiveDuration
            }
            val totalFramesAcrossAllScenes = Math.round(totalDurationEstimate * FRAME_RATE).toInt().coerceAtLeast(1)

            var cumulativeAudioTime = 0.0
            var cumulativeFramesTarget = 0
            var totalFramesInput = 0

            for (i in scenes.indices) {
                if (Thread.currentThread().isInterrupted) {
                    Log.d(TAG, "Video compilation aborted by user interrupt request.")
                    return false
                }
                val scene = scenes[i]
                val bmp = bitmaps[i]
                val tempVid = tempVideoFiles.getOrNull(i)

                val f = voiceFiles.getOrNull(i)
                val voiceDurationFloat = if (f != null && f.exists() && f.length() >= 44) {
                    val info = WavFileInfo.readWavFile(f)
                    if (info != null && info.pcmData != null && info.pcmData.size > 0) {
                        val wavBytesPerSec = info.sampleRate * info.channels * (info.bitsPerSample / 8)
                        info.pcmData.size.toDouble() / wavBytesPerSec
                    } else {
                        0.0
                    }
                } else {
                    0.0
                }
                val effectiveDuration = if (voiceDurationFloat > 0.0) {
                    voiceDurationFloat
                } else {
                    scene.durationSeconds.toDouble().coerceAtLeast(1.0)
                }

                cumulativeAudioTime += effectiveDuration
                val nextCumulativeFramesTarget = Math.round(cumulativeAudioTime * FRAME_RATE).toInt()
                val sceneFramesCount = (nextCumulativeFramesTarget - cumulativeFramesTarget).coerceAtLeast(1)
                cumulativeFramesTarget = nextCumulativeFramesTarget

                var retriever: android.media.MediaMetadataRetriever? = null
                var sequentialDecoder: SequentialFrameDecoder? = null
                var sourceFrameCount = 0
                var videoDurationUs = 0L
                if (tempVid != null && tempVid.exists()) {
                    try {
                        sequentialDecoder = SequentialFrameDecoder(tempVid, width, height, isEnhanceVideo = isEnhanceVideo)
                        Log.d(TAG, "Successfully initialized high-performance SequentialFrameDecoder for scene ${i + 1} (Enhance: $isEnhanceVideo)")
                    } catch (e: Exception) {
                        Log.e(TAG, "SequentialFrameDecoder init failed for scene ${i + 1}, falling back to retriever.", e)
                        sequentialDecoder = null
                    }
                    
                    retriever = android.media.MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(tempVid.absolutePath)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            val countStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
                            if (!countStr.isNullOrEmpty()) {
                                sourceFrameCount = countStr.toIntOrNull() ?: 0
                            }
                        }
                        val durStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                        if (!durStr.isNullOrEmpty()) {
                            videoDurationUs = (durStr.toLongOrNull() ?: 0L) * 1000L
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed setting media data source for temp video", e)
                        retriever = null
                    }
                }

                var lastValidVideoFrame: Bitmap? = null

                // Performance Optimization: Pre-compute scene filters, paints, typeface, mask, text overlays & subtitle layout ONCE per scene
                val curTypeface = getTypefaceByName(scene.captionFont)
                textPaint.typeface = curTypeface
                overlayPaint.typeface = curTypeface

                val colorPaint = Paint(paint).apply {
                    isFilterBitmap = true
                    isAntiAlias = true
                    if (isEnhanceVideo) {
                        val cm = android.graphics.ColorMatrix()
                        // 1. Automatic conservative color vibrancy / richness boost (+10%)
                        val baseSat = (scene.saturationValue * 1.10f).coerceIn(0.5f, 2.0f)
                        cm.setSaturation(baseSat)

                        // 2. Automatic conservative contrast enhancement (+8%) with subtle mid-tone tone curve
                        val contrastScale = 1.08f
                        val contrastTranslate = (-0.5f * contrastScale + 0.5f) * 255f + 3f
                        val contrastMatrix = android.graphics.ColorMatrix(floatArrayOf(
                            contrastScale, 0f, 0f, 0f, contrastTranslate,
                            0f, contrastScale, 0f, 0f, contrastTranslate,
                            0f, 0f, contrastScale, 0f, contrastTranslate,
                            0f, 0f, 0f, 1f, 0f
                        ))
                        cm.postConcat(contrastMatrix)

                        // 3. User scene custom adjustments if configured
                        if (scene.brightnessValue != 0f || scene.warmthValue != 0f) {
                            val matrixArray = cm.array
                            if (scene.brightnessValue != 0f) {
                                val brightOffset = (scene.brightnessValue / 100f) * 255f
                                matrixArray[4] = matrixArray[4] + brightOffset
                                matrixArray[9] = matrixArray[9] + brightOffset
                                matrixArray[14] = matrixArray[14] + brightOffset
                            }
                            if (scene.warmthValue != 0f) {
                                val warmthOffset = (scene.warmthValue / 100f) * 40f
                                matrixArray[4] = matrixArray[4] + warmthOffset
                                matrixArray[14] = matrixArray[14] - warmthOffset
                            }
                            cm.set(matrixArray)
                        }
                        colorFilter = android.graphics.ColorMatrixColorFilter(cm)
                    } else if (scene.saturationValue != 1.0f || scene.brightnessValue != 0f || scene.warmthValue != 0f) {
                        val cm = android.graphics.ColorMatrix()
                        cm.setSaturation(scene.saturationValue)
                        if (scene.brightnessValue != 0f) {
                            val brightOffset = (scene.brightnessValue / 100f) * 255f
                            val matrixArray = cm.array
                            matrixArray[4] = matrixArray[4] + brightOffset
                            matrixArray[9] = matrixArray[9] + brightOffset
                            matrixArray[14] = matrixArray[14] + brightOffset
                            cm.set(matrixArray)
                        }
                        if (scene.warmthValue != 0f) {
                            val warmthOffset = (scene.warmthValue / 100f) * 40f
                            val matrixArray = cm.array
                            matrixArray[4] = matrixArray[4] + warmthOffset
                            matrixArray[14] = matrixArray[14] - warmthOffset
                            cm.set(matrixArray)
                        }
                        colorFilter = android.graphics.ColorMatrixColorFilter(cm)
                    }
                }

                val filter = EditorConstants.FILTERS.getOrNull(scene.selectedFilterIndex)
                val tintPaint: Paint? = if (filter != null && filter.tintColor.alpha > 0f) {
                    Paint().apply {
                        color = Color.argb(
                            (filter.tintColor.alpha * 255).toInt(),
                            (filter.tintColor.red * 255).toInt(),
                            (filter.tintColor.green * 255).toInt(),
                            (filter.tintColor.blue * 255).toInt()
                        )
                        style = Paint.Style.FILL
                    }
                } else null

                val maskPath: android.graphics.Path? = when (scene.maskShape) {
                    "Circle" -> android.graphics.Path().apply {
                        addCircle(width / 2f, height / 2f, Math.min(width, height) / 2f, android.graphics.Path.Direction.CW)
                    }
                    "Rectangle" -> android.graphics.Path().apply {
                        val margin = 20f * scaleFactor
                        addRoundRect(
                            margin, margin, width - margin, height - margin,
                            24f * scaleFactor, 24f * scaleFactor,
                            android.graphics.Path.Direction.CW
                        )
                    }
                    else -> null
                }

                val overlayText = scene.textOverlay?.takeIf { it.isNotEmpty() }
                val overlayBoxRect: RectF?
                val overlayTextY: Float
                val overlayTextX: Float
                if (overlayText != null) {
                    val parsedColor = try { Color.parseColor(scene.overlayColor) } catch (e: Exception) { Color.WHITE }
                    overlayPaint.color = parsedColor
                    val curFontSize = (scene.textOverlayFontSize.coerceIn(12f, 60f) * scaleFactor * scene.textOverlayScale.coerceIn(0.4f, 4.0f))
                    overlayPaint.textSize = curFontSize
                    val rect = Rect()
                    overlayPaint.getTextBounds(overlayText, 0, overlayText.length, rect)
                    val boxW = rect.width() + (24 * scaleFactor).toInt()
                    val boxH = rect.height() + (16 * scaleFactor).toInt()
                    val centerX = width * scene.textOverlayX.coerceIn(0.05f, 0.95f)
                    val centerY = height * scene.textOverlayY.coerceIn(0.05f, 0.95f)
                    overlayBoxRect = RectF(
                        centerX - boxW / 2f,
                        centerY - boxH / 2f,
                        centerX + boxW / 2f,
                        centerY + boxH / 2f
                    )
                    overlayTextX = centerX
                    overlayTextY = centerY + rect.height() / 2f - 2f * scaleFactor
                } else {
                    overlayBoxRect = null
                    overlayTextX = 0f
                    overlayTextY = 0f
                }

                val stickerText = scene.stickerName.takeIf { it.isNotEmpty() && it != "None" }
                val stickerSize = (46f * scaleFactor * scene.stickerScale.coerceIn(0.4f, 4.0f))
                val stickerX = width * scene.stickerX.coerceIn(0.05f, 0.95f)
                val stickerY = height * scene.stickerY.coerceIn(0.05f, 0.95f)
                val stickerPaint = if (stickerText != null) {
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        textAlign = Paint.Align.CENTER
                        textSize = stickerSize
                    }
                } else null

                val sceneLayers = scene.getCanvasLayers()

                val subtitle = scene.subtitle
                val subtitleLines: List<String>
                val subtitleBoxRect: RectF?
                val subtitleDrawBox: Boolean
                val subtitleTextHeight = 28f * scaleFactor
                val subtitlePadding = 16f * scaleFactor
                val subtitleBgColor: Int
                val subtitleUseGlow: Boolean

                if (subtitle.isNotEmpty()) {
                    val defaultColorStr = scene.subtitleColor.ifEmpty { "#FFFFFF" }
                    val defaultBgColorStr = scene.subtitleBgColor.ifEmpty { "#99000000" }

                    val resolvedTextColor: Int

                    when (scene.subtitleDesign) {
                        "TikTok Yellow" -> {
                            resolvedTextColor = Color.parseColor("#FFFF00")
                            subtitleBgColor = Color.parseColor("#CC111111")
                            subtitleDrawBox = true
                            subtitleUseGlow = false
                        }
                        "Cyber Neon" -> {
                            resolvedTextColor = Color.parseColor("#00F0FF")
                            subtitleBgColor = Color.TRANSPARENT
                            subtitleDrawBox = false
                            subtitleUseGlow = true
                        }
                        "Hot Pink Style" -> {
                            resolvedTextColor = Color.parseColor("#FFFFFF")
                            subtitleBgColor = Color.parseColor("#FF007F")
                            subtitleDrawBox = true
                            subtitleUseGlow = false
                        }
                        "Minimal Borderless" -> {
                            resolvedTextColor = try { Color.parseColor(defaultColorStr) } catch (e: Exception) { Color.WHITE }
                            subtitleBgColor = Color.TRANSPARENT
                            subtitleDrawBox = false
                            subtitleUseGlow = false
                        }
                        "Royal Gold" -> {
                            resolvedTextColor = Color.parseColor("#FFD700")
                            subtitleBgColor = Color.parseColor("#E61A1502")
                            subtitleDrawBox = true
                            subtitleUseGlow = false
                        }
                        else -> { // "Classic Box"
                            resolvedTextColor = try { Color.parseColor(defaultColorStr) } catch (e: Exception) { Color.WHITE }
                            subtitleBgColor = try { Color.parseColor(defaultBgColorStr) } catch (e: Exception) { Color.parseColor("#99000000") }
                            subtitleDrawBox = true
                            subtitleUseGlow = false
                        }
                    }

                    textPaint.color = resolvedTextColor
                    subtitleLines = splitTextToLines(subtitle, textPaint, (width - 60f * scaleFactor))
                    subtitleBoxRect = RectF(
                        20f * scaleFactor,
                        height - 130f * scaleFactor - (subtitleLines.size * subtitleTextHeight + subtitlePadding),
                        width - 20f * scaleFactor,
                        height - 130f * scaleFactor
                    )
                } else {
                    subtitleLines = emptyList()
                    subtitleBoxRect = null
                    subtitleDrawBox = false
                    subtitleBgColor = 0
                    subtitleUseGlow = false
                }

                val dimPaint = Paint().apply {
                    color = Color.argb(165, 12, 9, 20)
                    style = Paint.Style.FILL
                }

                val srcRect = Rect()
                val dstRect = RectF()
                val bgDstRect = RectF()

                for (frame in 0 until sceneFramesCount) {
                    if (Thread.currentThread().isInterrupted) {
                        Log.d(TAG, "Video compilation aborted by user interrupt request.")
                        try { lastValidVideoFrame?.recycle() } catch (e: Exception) {}
                        return false
                    }

                    // Clear canvas
                    canvas.drawColor(Color.parseColor("#0C0914"))
                    
                    var frameBmp: Bitmap? = null
                    
                    // Try high-performance sequential frame decoder first for 100% smooth, non-frozen cinematic motion
                    if (sequentialDecoder != null) {
                        try {
                            frameBmp = sequentialDecoder.getNextFrame()
                        } catch (e: Exception) {
                            Log.w(TAG, "SequentialFrameDecoder failed to retrieve next frame for scene ${i + 1}", e)
                        }
                    }
                    
                    // Fallback gracefully to retriever ONLY if sequential decoder failed and we don't have a valid frame yet
                    if (frameBmp == null && retriever != null && lastValidVideoFrame == null) {
                        val targetFrameIdx = if (sourceFrameCount > 0) (frame % sourceFrameCount) else -1
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P && targetFrameIdx >= 0) {
                            try {
                                frameBmp = retriever.getFrameAtIndex(targetFrameIdx)
                            } catch (e: Exception) {
                                Log.w(TAG, "getFrameAtIndex failed for index $targetFrameIdx", e)
                            }
                        }

                        if (frameBmp == null) {
                            val targetTimeUs = (frame * 1000000L) / FRAME_RATE
                            val timeUs = if (videoDurationUs > 0) targetTimeUs % videoDurationUs else targetTimeUs
                            try {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                                    try {
                                        frameBmp = retriever.getScaledFrameAtTime(
                                            timeUs,
                                            android.media.MediaMetadataRetriever.OPTION_CLOSEST,
                                            width,
                                            height
                                        )
                                    } catch (ex: Exception) {
                                        Log.w(TAG, "retriever.getScaledFrameAtTime failed, trying getFrameAtTime", ex)
                                    }
                                }
                                if (frameBmp == null) {
                                    frameBmp = retriever.getFrameAtTime(timeUs, android.media.MediaMetadataRetriever.OPTION_CLOSEST)
                                        ?: retriever.getFrameAtTime(timeUs)
                                }
                            } catch (e: java.lang.Exception) {
                                Log.e(TAG, "Failed getting video frame at $timeUs Us", e)
                            }
                        }
                    }
                    
                    if (frameBmp != null) {
                        if (isEnhanceVideo && sequentialDecoder == null) {
                            val sharpFrame = enhanceBitmapHighClarity(frameBmp)
                            if (sharpFrame != frameBmp) {
                                frameBmp.recycle()
                                frameBmp = sharpFrame
                            }
                        }
                        lastValidVideoFrame = frameBmp
                    }

                    // Guaranteed visual fallback cascade: New Frame -> Previous Video Frame -> Static Image -> Procedural Gradient
                    val activeBmpToDraw = lastValidVideoFrame ?: bmp ?: createProceduralFallbackBitmap(width, height, i)
                    
                    srcRect.set(0, 0, activeBmpToDraw.width, activeBmpToDraw.height)
                    val scaleX = width.toFloat() / activeBmpToDraw.width
                    val scaleY = height.toFloat() / activeBmpToDraw.height
                    val aspectDisparity = if (scaleX > scaleY) scaleX / scaleY else scaleY / scaleX
                    val isAspectMismatch = aspectDisparity > 1.25f
                    val baseScale = if (isAspectMismatch) Math.min(scaleX, scaleY) else Math.max(scaleX, scaleY)
                    
                    val progress = frame.toFloat() / sceneFramesCount.coerceAtLeast(1)
                    val effectType = i % 4
                    var currentScale = baseScale
                    var dX = 0f
                    var dY = 0f
                    
                    when (effectType) {
                        0 -> { // Ken Burns Zoom In
                            val scaleMul = 1.0f + 0.08f * progress
                            currentScale = baseScale * scaleMul
                        }
                        1 -> { // Ken Burns Zoom Out
                            val scaleMul = 1.08f - 0.08f * progress
                            currentScale = baseScale * scaleMul
                        }
                        2 -> { // Smooth Right-to-Left Pan
                            currentScale = baseScale * 1.08f
                            val maxPanOffset = (width * 0.04f)
                            dX = maxPanOffset - (progress * 2f * maxPanOffset)
                        }
                        3 -> { // Smooth Left-to-Right Pan + subtle Zoom In
                            val scaleMul = 1.02f + 0.06f * progress
                            currentScale = baseScale * scaleMul
                            val maxPanOffset = (width * 0.03f)
                            dX = -maxPanOffset + (progress * 2f * maxPanOffset)
                        }
                    }
                    
                    val dW = activeBmpToDraw.width * currentScale
                    val dH = activeBmpToDraw.height * currentScale
                    val left = (width - dW) / 2 + dX
                    val top = (height - dH) / 2 + dY
                    dstRect.set(left, top, left + dW, top + dH)
                    
                    canvas.save()
                    
                    if (scene.rotationDegrees != 0) {
                        canvas.rotate(scene.rotationDegrees.toFloat(), width / 2f, height / 2f)
                    }
                    
                    val sX = if (scene.isFlippedHorizontal) -1f else 1f
                    val sY = if (scene.isFlippedVertical) -1f else 1f
                    if (scene.isFlippedHorizontal || scene.isFlippedVertical) {
                        canvas.scale(sX, sY, width / 2f, height / 2f)
                    }
                    
                    if (maskPath != null) {
                        canvas.clipPath(maskPath)
                    }

                    // If aspect ratios differ significantly, fill background with dimmed media first
                    if (isAspectMismatch) {
                        val bgScale = Math.max(scaleX, scaleY)
                        val bgW = activeBmpToDraw.width * bgScale
                        val bgH = activeBmpToDraw.height * bgScale
                        val bgLeft = (width - bgW) / 2f
                        val bgTop = (height - bgH) / 2f
                        bgDstRect.set(bgLeft, bgTop, bgLeft + bgW, bgTop + bgH)
                        canvas.drawBitmap(activeBmpToDraw, srcRect, bgDstRect, colorPaint)
                        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
                    }

                    canvas.drawBitmap(activeBmpToDraw, srcRect, dstRect, colorPaint)
                    canvas.restore()
                        
                    // Apply selected color filter tint from EditorConstants
                    if (tintPaint != null) {
                        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), tintPaint)
                    }
                    
                    // Draw Canvas Layers in order of depth (Z-order)
                    if (sceneLayers.isNotEmpty()) {
                        for (layer in sceneLayers) {
                            if (!layer.isVisible) continue
                            if (layer.type == "TEXT" && layer.content.isNotEmpty()) {
                                val layerText = layer.content.uppercase()
                                val layerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    color = try { Color.parseColor(layer.color) } catch (e: Exception) { Color.WHITE }
                                    textSize = 24f * scaleFactor * layer.scale.coerceIn(0.3f, 4.0f)
                                    textAlign = Paint.Align.CENTER
                                    style = Paint.Style.FILL
                                    typeface = when (layer.font) {
                                        "Sans-Serif" -> Typeface.SANS_SERIF
                                        "Serif" -> Typeface.SERIF
                                        "Monospace" -> Typeface.MONOSPACE
                                        "Display Bold" -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                                        "TikTok Style" -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                                        "Insta Premium" -> Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
                                        else -> Typeface.DEFAULT_BOLD
                                    }
                                }
                                val textBounds = Rect()
                                layerPaint.getTextBounds(layerText, 0, layerText.length, textBounds)
                                val boxW = textBounds.width() + (24 * scaleFactor).toInt()
                                val boxH = textBounds.height() + (16 * scaleFactor).toInt()
                                val centerX = width * layer.x.coerceIn(0.05f, 0.95f)
                                val centerY = height * layer.y.coerceIn(0.05f, 0.95f)
                                val layerBox = RectF(
                                    centerX - boxW / 2f,
                                    centerY - boxH / 2f,
                                    centerX + boxW / 2f,
                                    centerY + boxH / 2f
                                )
                                val textRot = layer.rotation
                                if (textRot != 0f) {
                                    canvas.save()
                                    canvas.rotate(textRot, layerBox.centerX(), layerBox.centerY())
                                }
                                canvas.drawRoundRect(layerBox, 8f * scaleFactor, 8f * scaleFactor, backgroundBoxPaint)
                                canvas.drawText(
                                    layerText,
                                    centerX,
                                    centerY + textBounds.height() / 2f - 2f * scaleFactor,
                                    layerPaint
                                )
                                if (textRot != 0f) {
                                    canvas.restore()
                                }
                            } else if (layer.type == "STICKER" && layer.content.isNotEmpty() && layer.content != "None") {
                                val stSize = 46f * scaleFactor * layer.scale.coerceIn(0.3f, 4.0f)
                                val stPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    textAlign = Paint.Align.CENTER
                                    textSize = stSize
                                }
                                val stX = width * layer.x.coerceIn(0.05f, 0.95f)
                                val stY = height * layer.y.coerceIn(0.05f, 0.95f)
                                val stRot = layer.rotation
                                if (stRot != 0f) {
                                    canvas.save()
                                    canvas.rotate(stRot, stX, stY)
                                }
                                canvas.drawText(layer.content, stX, stY, stPaint)
                                if (stRot != 0f) {
                                    canvas.restore()
                                }
                            }
                        }
                    } else {
                        // Fallback legacy text overlay if present
                        if (overlayText != null && overlayBoxRect != null) {
                            val textRot = scene.textOverlayRotation
                            if (textRot != 0f) {
                                canvas.save()
                                canvas.rotate(textRot, overlayBoxRect.centerX(), overlayBoxRect.centerY())
                            }
                            canvas.drawRoundRect(overlayBoxRect, 8f * scaleFactor, 8f * scaleFactor, backgroundBoxPaint)
                            canvas.drawText(
                                overlayText.uppercase(),
                                overlayTextX,
                                overlayTextY,
                                overlayPaint
                            )
                            if (textRot != 0f) {
                                canvas.restore()
                            }
                        }

                        // Fallback legacy sticker/emoji if present
                        if (stickerText != null && stickerPaint != null) {
                            val stRot = scene.stickerRotation
                            if (stRot != 0f) {
                                canvas.save()
                                canvas.rotate(stRot, stickerX, stickerY)
                            }
                            canvas.drawText(
                                stickerText,
                                stickerX,
                                stickerY,
                                stickerPaint
                            )
                            if (stRot != 0f) {
                                canvas.restore()
                            }
                        }
                    }
                    
                    // Draw subtitles if present
                    if (subtitleLines.isNotEmpty() && subtitleBoxRect != null) {
                        if (subtitleDrawBox) {
                            backgroundBoxPaint.color = subtitleBgColor
                            canvas.drawRoundRect(subtitleBoxRect, 12f * scaleFactor, 12f * scaleFactor, backgroundBoxPaint)
                        }
                        
                        if (subtitleUseGlow) {
                            textPaint.setShadowLayer(10f * scaleFactor, 0f, 0f, Color.parseColor("#00F0FF"))
                        } else {
                            textPaint.setShadowLayer(4f * scaleFactor, 2f * scaleFactor, 2f * scaleFactor, Color.BLACK)
                        }

                        for (lineIdx in subtitleLines.indices) {
                            val lineText = subtitleLines[lineIdx]
                            canvas.drawText(
                                lineText,
                                width / 2f,
                                subtitleBoxRect.top + subtitlePadding + (lineIdx + 0.7f) * subtitleTextHeight,
                                textPaint
                            )
                        }
                        textPaint.clearShadowLayer()
                    }
                    
                    // CapCut / Filmora Pro Transition Rendering (Media3 Pipeline)
                    if (scene.transitionType.isNotBlank() && scene.transitionType != "None") {
                        Media3TransitionEngine.drawTransition(
                            canvas = canvas,
                            width = width,
                            height = height,
                            frameIndex = frame,
                            totalFrames = sceneFramesCount,
                            transitionType = scene.transitionType,
                            transitionDurationMs = scene.transitionDurationMs,
                            fps = FRAME_RATE
                        )
                    }

                    // Cinema Vignette Layer
                    if (scene.vignetteValue > 0f) {
                        val vigRadius = kotlin.math.hypot(width / 2f, height / 2f)
                        val vigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            shader = android.graphics.RadialGradient(
                                width / 2f,
                                height / 2f,
                                vigRadius,
                                intArrayOf(android.graphics.Color.TRANSPARENT, android.graphics.Color.argb((scene.vignetteValue * 220).toInt().coerceIn(0, 255), 0, 0, 0)),
                                floatArrayOf(0.35f, 1.0f),
                                android.graphics.Shader.TileMode.CLAMP
                            )
                        }
                        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), vigPaint)
                    }

                    renderWatermarkOnCanvas(canvas, width, height, scaleFactor, watermarkConfig)

                    var inputBufIndex = -1
                    while (inputBufIndex < 0) {
                        if (Thread.currentThread().isInterrupted) {
                            Log.d(TAG, "Video compilation aborted by user interrupt request.")
                            return false
                        }
                        inputBufIndex = encoder.dequeueInputBuffer(5000)
                        if (inputBufIndex >= 0) {
                            if (totalFramesInput == 0) {
                                val params = android.os.Bundle().apply {
                                    putInt(android.media.MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
                                }
                                try { encoder.setParameters(params) } catch (e: Exception) {}
                            }
                            // Convert ARGB to YUV420 semiplanar using preallocated native bounds
                            getNV21Optimized(width, height, renderBitmap!!, argbBuffer, yuvBuffer, chosenColorFormat)
                            val inputBuffer = encoder.getInputBuffer(inputBufIndex)!!
                            inputBuffer.clear()
                            
                            // Buffer capacity check safeguards to prevent crashes and ensure smooth 30fps rendering
                            if (yuvBuffer.size <= inputBuffer.remaining()) {
                                inputBuffer.put(yuvBuffer)
                                inputBuffer.position(0) // Reset position so the encoder reads correct byte buffer start point
                                encoder.queueInputBuffer(
                                    inputBufIndex,
                                    0,
                                    yuvBuffer.size,
                                    presentationTimeUs,
                                    0
                                )
                                presentationTimeUs += frameDurationUs
                                totalFramesInput++
                            } else {
                                Log.e(TAG, "YUV buffer size (${yuvBuffer.size}) exceeds input buffer capacity (${inputBuffer.remaining()})!")
                                val bytesToPut = inputBuffer.remaining()
                                inputBuffer.put(yuvBuffer, 0, bytesToPut)
                                inputBuffer.position(0)
                                encoder.queueInputBuffer(
                                    inputBufIndex,
                                    0,
                                    bytesToPut,
                                    presentationTimeUs,
                                    0
                                )
                                presentationTimeUs += frameDurationUs
                                totalFramesInput++
                            }
                        } else {
                            // In an offline compiler, we DO NOT drop frames. We drain all output to free inputs.
                            drainEncoder(
                                encoder = encoder,
                                muxer = muxer,
                                bufferInfo = bufferInfo,
                                isMuxerStarted = { muxerStarted },
                                getTrackIndex = { trackIndex }
                            ) { index ->
                                trackIndex = index
                                muxerStarted = true
                            }
                            try { Thread.sleep(5) } catch (e: Exception) {}
                        }
                    }

                    // Continuous background drain keeping pipeline clean
                    drainEncoder(
                        encoder = encoder,
                        muxer = muxer,
                        bufferInfo = bufferInfo,
                        isMuxerStarted = { muxerStarted },
                        getTrackIndex = { trackIndex }
                    ) { index ->
                        trackIndex = index
                        muxerStarted = true
                    }

                    // Fine-grained progress reporting for real-time ETA & FPS tracking (every 2 frames or scene transition)
                    val currentGlobalFrame = (cumulativeFramesTarget - sceneFramesCount + frame + 1).coerceAtLeast(1)
                    if (currentGlobalFrame % 2 == 0 || currentGlobalFrame >= totalFramesAcrossAllScenes) {
                        val currentFrameProgress = (currentGlobalFrame.toFloat() / totalFramesAcrossAllScenes).coerceIn(0f, 1f)
                        onProgress(currentFrameProgress)
                    }
                }
                
                try {
                    lastValidVideoFrame?.recycle()
                } catch (e: Exception) {}
                try {
                    sequentialDecoder?.release()
                } catch (e: Exception) {}
                try {
                    retriever?.release()
                } catch (e: Exception) {}

                val sceneDoneProgress = (cumulativeFramesTarget.toFloat() / totalFramesAcrossAllScenes).coerceIn(0f, 1f)
                onProgress(sceneDoneProgress)
            }

            // Signal End of Stream
            var eosQueued = false
            var dequeueAttempts = 0
            while (!eosQueued && dequeueAttempts < 30) {
                val inputBufIndex = encoder.dequeueInputBuffer(10000)
                if (inputBufIndex >= 0) {
                    encoder.queueInputBuffer(
                        inputBufIndex,
                        0,
                        0,
                        presentationTimeUs,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                    eosQueued = true
                    Log.d(TAG, "EOS marker successfully queued to MediaCodec")
                } else {
                    drainEncoder(
                        encoder = encoder,
                        muxer = muxer,
                        bufferInfo = bufferInfo,
                        isMuxerStarted = { muxerStarted },
                        getTrackIndex = { trackIndex }
                    ) { index ->
                        trackIndex = index
                        muxerStarted = true
                    }
                    dequeueAttempts++
                    try { Thread.sleep(10) } catch (e: Exception) {}
                }
            }

            // Final drain
            var eosReached = false
            var drainAttempts = 0
            while (!eosReached && drainAttempts < 50) {
                val status = drainEncoder(
                    encoder = encoder,
                    muxer = muxer,
                    bufferInfo = bufferInfo,
                    isMuxerStarted = { muxerStarted },
                    getTrackIndex = { trackIndex }
                ) { index ->
                    trackIndex = index
                    muxerStarted = true
                }
                if (status == 1) {
                    eosReached = true
                    Log.d(TAG, "Successfully reached End of Stream during final drain!")
                    break
                }
                drainAttempts++
                try { Thread.sleep(10) } catch (e: Exception) {}
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed compiling silent video file", e)
            return false
        } finally {
            for (tempVid in tempVideoFiles) {
                try {
                    if (tempVid != null && tempVid.exists()) {
                        tempVid.delete()
                    }
                } catch (e: Exception) {}
            }
            for (bmp in bitmaps) {
                try {
                    if (bmp != null && !bmp.isRecycled) {
                        bmp.recycle()
                    }
                } catch (e: Exception) {}
            }
            try {
                if (renderBitmap != null && !renderBitmap.isRecycled) {
                    renderBitmap.recycle()
                }
            } catch (e: Exception) {}
            try {
                encoder?.stop()
                encoder?.release()
            } catch (e: Exception) {}
            try {
                muxer?.stop()
                muxer?.release()
            } catch (e: Exception) {}
        }
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer?,
        bufferInfo: MediaCodec.BufferInfo,
        isMuxerStarted: () -> Boolean,
        getTrackIndex: () -> Int,
        onFormatChanged: (Int) -> Unit
    ): Int {
        var eosStatus = 0
        while (true) {
            val encoderStatus = encoder.dequeueOutputBuffer(bufferInfo, 2000)
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                break
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val newFormat = encoder.outputFormat
                if (muxer != null && !isMuxerStarted()) {
                    val videoTrack = muxer.addTrack(newFormat)
                    muxer.start()
                    onFormatChanged(videoTrack)
                }
            } else if (encoderStatus >= 0) {
                val encodedData = encoder.getOutputBuffer(encoderStatus)!!
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    bufferInfo.size = 0
                }

                if (bufferInfo.size != 0 && muxer != null && isMuxerStarted()) {
                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                    val trkIdx = getTrackIndex()
                    val targetTrack = if (trkIdx >= 0) trkIdx else 0
                    muxer.writeSampleData(targetTrack, encodedData, bufferInfo)
                }

                encoder.releaseOutputBuffer(encoderStatus, false)

                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    eosStatus = 1
                    break
                }
            }
        }
        return eosStatus
    }

    private fun splitTextToLines(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = ArrayList<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "${currentLine} $word"
            val width = paint.measureText(testLine)
            if (width > maxWidth) {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                    currentLine = StringBuilder(word)
                } else {
                    lines.add(word)
                }
            } else {
                currentLine.append(if (currentLine.isEmpty()) word else " $word")
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        return lines
    }

    private fun selectColorFormat(encoder: android.media.MediaCodec, mimeType: String): Int {
        try {
            val codecInfo = encoder.codecInfo
            val capabilities = codecInfo.getCapabilitiesForType(mimeType)
            for (colorFormat in capabilities.colorFormats) {
                if (colorFormat == android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
                    return colorFormat
                }
            }
            for (colorFormat in capabilities.colorFormats) {
                if (colorFormat == android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
                    return colorFormat
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error selecting color format", e)
        }
        return android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
    }

    private fun encodeYUV420Planar(yuv420p: ByteArray, argb: IntArray, width: Int, height: Int) {
        val frameSize = width * height
        var yIndex = 0
        var uIndex = frameSize
        var vIndex = frameSize + (frameSize / 4)
        var index = 0

        for (j in 0 until height) {
            val isEvenRow = (j and 1) == 0
            for (i in 0 until width) {
                val rgb = argb[index++]
                val r = (rgb shr 16) and 0xff
                val g = (rgb shr 8) and 0xff
                val b = rgb and 0xff

                val y = (TABLE_Y_R[r] + TABLE_Y_G[g] + TABLE_Y_B[b]) shr 8
                yuv420p[yIndex++] = (if (y < 0) 0 else if (y > 255) 255 else y).toByte()

                if (isEvenRow && (i and 1) == 0) {
                    val u = (TABLE_U_R[r] + TABLE_U_G[g] + TABLE_U_B[b]) shr 8
                    val v = (TABLE_V_R[r] + TABLE_V_G[g] + TABLE_V_B[b]) shr 8
                    yuv420p[uIndex++] = (if (u < 0) 0 else if (u > 255) 255 else u).toByte()
                    yuv420p[vIndex++] = (if (v < 0) 0 else if (v > 255) 255 else v).toByte()
                }
            }
        }
    }

    private fun getNV21Optimized(
        inputWidth: Int,
        inputHeight: Int,
        bitmap: Bitmap,
        argb: IntArray,
        yuv: ByteArray,
        colorFormat: Int
    ) {
        bitmap.getPixels(argb, 0, inputWidth, 0, 0, inputWidth, inputHeight)
        if (colorFormat == android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
            encodeYUV420Planar(yuv, argb, inputWidth, inputHeight)
        } else {
            encodeYUV420SP(yuv, argb, inputWidth, inputHeight)
        }
    }

    private fun getNV21(inputWidth: Int, inputHeight: Int, bitmap: Bitmap): ByteArray {
        val argb = IntArray(inputWidth * inputHeight)
        bitmap.getPixels(argb, 0, inputWidth, 0, 0, inputWidth, inputHeight)

        val yuv = ByteArray(inputWidth * inputHeight * 3 / 2)
        encodeYUV420SP(yuv, argb, inputWidth, inputHeight)
        return yuv
    }

    private fun encodeYUV420SP(yuv420sp: ByteArray, argb: IntArray, width: Int, height: Int) {
        val frameSize = width * height
        var yIndex = 0
        var uvIndex = frameSize
        var index = 0

        for (j in 0 until height) {
            val isEvenRow = (j and 1) == 0
            for (i in 0 until width) {
                val rgb = argb[index++]
                val r = (rgb shr 16) and 0xff
                val g = (rgb shr 8) and 0xff
                val b = rgb and 0xff

                val y = (TABLE_Y_R[r] + TABLE_Y_G[g] + TABLE_Y_B[b]) shr 8
                yuv420sp[yIndex++] = (if (y < 0) 0 else if (y > 255) 255 else y).toByte()

                if (isEvenRow && (i and 1) == 0) {
                    val u = (TABLE_U_R[r] + TABLE_U_G[g] + TABLE_U_B[b]) shr 8
                    val v = (TABLE_V_R[r] + TABLE_V_G[g] + TABLE_V_B[b]) shr 8
                    yuv420sp[uvIndex++] = (if (u < 0) 0 else if (u > 255) 255 else u).toByte()
                    yuv420sp[uvIndex++] = (if (v < 0) 0 else if (v > 255) 255 else v).toByte()
                }
            }
        }
    }

    private fun getCacheFileForUrl(context: Context, url: String, extension: String): File {
        val cacheDir = File(context.cacheDir, "YashoraMediaCache").apply {
            if (!exists()) mkdirs()
        }
        val hexString = try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(url.toByteArray(Charsets.UTF_8))
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            url.hashCode().toString()
        }
        return File(cacheDir, "media_$hexString.$extension")
    }

    private fun downloadFile(context: Context, urlStr: String, destFile: File): Boolean {
        if (urlStr.startsWith("content://") || urlStr.startsWith("file://") || urlStr.startsWith("/")) {
            try {
                if (urlStr.startsWith("/")) {
                    val file = File(urlStr)
                    if (file.exists()) {
                        file.inputStream().use { input ->
                            java.io.FileOutputStream(destFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        return true
                    }
                } else {
                    val uri = android.net.Uri.parse(urlStr)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        java.io.FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    return true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy local path to temp file: $urlStr", e)
                return false
            }
        }
        try {
            val url = java.net.URL(urlStr)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            val ua = when {
                urlStr.contains("nekos.best", ignoreCase = true) ->
                    "YashoraReelGenerator (Ritvyom@gmail.com)"
                urlStr.contains("wikimedia.org", ignoreCase = true) || urlStr.contains("wikipedia.org", ignoreCase = true) ->
                    "YashoraReelGenerator/2.0 (https://ai.studio; yashoratechnologies@gmail.com) Android/14 OkHttp/4.12"
                else ->
                    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
            }
            connection.setRequestProperty("User-Agent", ua)
            connection.inputStream.use { input ->
                java.io.FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download media file: $urlStr", e)
            return false
        }
    }

    private fun downloadBitmap(context: Context, urlStr: String): Bitmap? {
        if (urlStr.startsWith("content://") || urlStr.startsWith("file://") || urlStr.startsWith("/")) {
            try {
                if (urlStr.startsWith("/")) {
                    val file = File(urlStr)
                    if (file.exists()) {
                        return BitmapFactory.decodeFile(urlStr)
                    }
                } else {
                    val uri = android.net.Uri.parse(urlStr)
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        return BitmapFactory.decodeStream(stream)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode local path bitmap: $urlStr", e)
                return null
            }
        }
        
        // Check local storage and disk cache first
        try {
            val localMediaFile = com.ritvyom.yashoraReelgenerator.data.repository.ProjectRepository.findExistingLocalMediaFile(context, urlStr)
            if (localMediaFile != null && localMediaFile.exists() && localMediaFile.length() > 0) {
                val localBmp = BitmapFactory.decodeFile(localMediaFile.absolutePath)
                if (localBmp != null) {
                    Log.d(TAG, "Image found in local app storage, bypassing network download: $urlStr -> ${localMediaFile.absolutePath}")
                    return localBmp
                }
            }

            val cachedFile = getCacheFileForUrl(context, urlStr, "jpg")
            if (cachedFile.exists() && cachedFile.length() > 0) {
                val cachedBmp = BitmapFactory.decodeFile(cachedFile.absolutePath)
                if (cachedBmp != null) {
                    Log.d(TAG, "Image found in local disk cache, bypassing network: $urlStr")
                    return cachedBmp
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load cached bitmap: $urlStr", e)
        }

        // On cache miss, download safely
        val tempImgFile = File(context.cacheDir, "temp_img_${System.currentTimeMillis()}.jpg")
        val parsedUrlStr = if (urlStr.startsWith("//")) "https:$urlStr" else urlStr
        val downloadSuccess = downloadFile(context, parsedUrlStr, tempImgFile)
        if (downloadSuccess && tempImgFile.exists() && tempImgFile.length() > 0) {
            val bmp = BitmapFactory.decodeFile(tempImgFile.absolutePath)
            if (bmp != null) {
                // Save to cache
                try {
                    val cachedFile = getCacheFileForUrl(context, urlStr, "jpg")
                    tempImgFile.copyTo(cachedFile, overwrite = true)
                    Log.d(TAG, "Successfully cached newly downloaded image: $urlStr")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to write image to disk cache: $urlStr", e)
                }
                try { tempImgFile.delete() } catch (e: Exception) {}
                return bmp
            }
        }
        try { tempImgFile.delete() } catch (e: Exception) {}

        // Direct stream fallback download as last resort
        try {
            val url = java.net.URL(urlStr)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 25000
            connection.readTimeout = 25000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
            connection.inputStream.use { stream ->
                return BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed downloading bitmap from url: $urlStr", e)
            return null
        }
    }

    private fun createProceduralFallbackBitmap(width: Int, height: Int, sceneIndex: Int): Bitmap {
        val w = width.coerceAtLeast(720)
        val h = height.coerceAtLeast(1280)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val colors = when (sceneIndex % 5) {
            0 -> intArrayOf(Color.parseColor("#1e1b4b"), Color.parseColor("#312e81"), Color.parseColor("#4338ca"))
            1 -> intArrayOf(Color.parseColor("#0f172a"), Color.parseColor("#1e293b"), Color.parseColor("#334155"))
            2 -> intArrayOf(Color.parseColor("#14532d"), Color.parseColor("#15803d"), Color.parseColor("#22c55e"))
            3 -> intArrayOf(Color.parseColor("#7c2d12"), Color.parseColor("#c2410c"), Color.parseColor("#f97316"))
            else -> intArrayOf(Color.parseColor("#581c87"), Color.parseColor("#7e22ce"), Color.parseColor("#a855f7"))
        }

        val shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), colors, null, Shader.TileMode.CLAMP)
        paint.shader = shader
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

        paint.shader = null
        paint.color = Color.WHITE
        paint.alpha = 20
        canvas.drawCircle(w * 0.5f, h * 0.4f, w * 0.35f, paint)

        return bitmap
    }

    // Custom real-time linear interpolation audio resampler to standardize sample rates & channels
    private fun resamplePcm(
        srcBytes: ByteArray,
        srcSampleRate: Int,
        destSampleRate: Int,
        srcChannels: Int,
        destChannels: Int
    ): ByteArray {
        val safeSrcChannels = srcChannels.coerceAtLeast(1)
        val safeDestChannels = destChannels.coerceAtLeast(1)

        if (srcSampleRate == destSampleRate && safeSrcChannels == safeDestChannels) {
            return srcBytes
        }
        
        try {
            // Convert srcBytes to ShortArray (little-endian)
            val srcShortsSize = srcBytes.size / 2
            val srcShorts = ShortArray(srcShortsSize)
            for (i in 0 until srcShortsSize) {
                val low = srcBytes[i * 2].toInt() and 0xFF
                val high = srcBytes[i * 2 + 1].toInt()
                srcShorts[i] = ((high shl 8) or low).toShort()
            }
            
            // Total input frames
            val inputFrameCount = srcShortsSize / safeSrcChannels
            if (inputFrameCount <= 0) return srcBytes

            // 1. Separate input channels
            val inputChannelsData = Array(safeSrcChannels) { ShortArray(inputFrameCount) }
            for (frame in 0 until inputFrameCount) {
                for (ch in 0 until safeSrcChannels) {
                    val idx = frame * safeSrcChannels + ch
                    if (idx < srcShortsSize) {
                        inputChannelsData[ch][frame] = srcShorts[idx]
                    }
                }
            }

            // 2. Resample each channel
            val scale = srcSampleRate.toDouble() / destSampleRate.toDouble()
            val outputFrameCount = (inputFrameCount / scale).toInt().coerceAtLeast(1)
            val resampledChannelsData = Array(safeSrcChannels) { ShortArray(outputFrameCount) }

            if (srcSampleRate == destSampleRate) {
                for (ch in 0 until safeSrcChannels) {
                    System.arraycopy(inputChannelsData[ch], 0, resampledChannelsData[ch], 0, outputFrameCount.coerceAtMost(inputFrameCount))
                }
            } else {
                for (ch in 0 until safeSrcChannels) {
                    val srcChData = inputChannelsData[ch]
                    val destChData = resampledChannelsData[ch]
                    for (i in 0 until outputFrameCount) {
                        val srcIndexFloat = i * scale
                        val srcIndex = srcIndexFloat.toInt()
                        val nextIndex = (srcIndex + 1).coerceAtMost(inputFrameCount - 1)
                        val t = (srcIndexFloat - srcIndex).toFloat()
                        
                        val val1 = srcChData[srcIndex].toInt()
                        val val2 = srcChData[nextIndex].toInt()
                        destChData[i] = (val1 + t * (val2 - val1)).toInt().toShort()
                    }
                }
            }

            // 3. Map resampled channels to destination channels
            val finalShortsSize = outputFrameCount * safeDestChannels
            val finalShorts = ShortArray(finalShortsSize)

            for (frame in 0 until outputFrameCount) {
                for (ch in 0 until safeDestChannels) {
                    val outSample: Short = when {
                        // If source channel exists, use it
                        ch < safeSrcChannels -> resampledChannelsData[ch][frame]
                        // Otherwise, fallback: duplicate first channel
                        safeSrcChannels > 0 -> resampledChannelsData[0][frame]
                        else -> 0
                    }
                    val idx = frame * safeDestChannels + ch
                    if (idx < finalShortsSize) {
                        finalShorts[idx] = outSample
                    }
                }
            }

            // Convert ShortArray back to ByteArray (little-endian)
            val destBytes = ByteArray(finalShorts.size * 2)
            for (i in finalShorts.indices) {
                val value = finalShorts[i].toInt()
                destBytes[i * 2] = (value and 0xFF).toByte()
                destBytes[i * 2 + 1] = ((value shr 8) and 0xFF).toByte()
            }
            
            return destBytes
        } catch (e: Exception) {
            Log.e(TAG, "Resampling PCM failed, returning un-resampled source bytes as fallback", e)
            return srcBytes
        }
    }

    /**
     * Decodes the audio track from a video or audio file (MP4, MKV, MOV, M4A, etc.) into linear PCM bytes.
     * Resamples the decoded audio to standard 44.1kHz stereo PCM so it cleanly mixes with the project timeline.
     */
    fun decodeAudioTrackToPcm(
        context: Context,
        mediaPath: String,
        targetSampleRate: Int = 44100,
        targetChannels: Int = 2,
        maxDurationMs: Long = 60000L
    ): ByteArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            if (mediaPath.startsWith("content://")) {
                extractor.setDataSource(context, android.net.Uri.parse(mediaPath), null)
            } else if (mediaPath.startsWith("http://") || mediaPath.startsWith("https://")) {
                val cachedFile = getCacheFileForUrl(context, mediaPath, "mp4")
                if (cachedFile.exists() && cachedFile.length() > 0) {
                    extractor.setDataSource(cachedFile.absolutePath)
                } else {
                    extractor.setDataSource(mediaPath)
                }
            } else {
                val file = File(mediaPath)
                if (file.exists()) {
                    extractor.setDataSource(file.absolutePath)
                } else {
                    val found = com.ritvyom.yashoraReelgenerator.data.repository.ProjectRepository.findExistingLocalMediaFile(context, mediaPath)
                    if (found != null && found.exists()) {
                        extractor.setDataSource(found.absolutePath)
                    } else {
                        Log.w(TAG, "Audio extraction media file not found: $mediaPath")
                        return null
                    }
                }
            }

            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex < 0 || audioFormat == null) {
                Log.d(TAG, "No audio track found in media file: $mediaPath")
                return null
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = audioFormat.getString(MediaFormat.KEY_MIME)!!
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(audioFormat, null, null, 0)
            codec.start()

            val srcSampleRate = if (audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100
            val srcChannels = if (audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 2

            val bufferInfo = MediaCodec.BufferInfo()
            var isInputEOS = false
            var isOutputEOS = false
            val timeoutUs = 5000L
            val maxUs = if (maxDurationMs > 0) maxDurationMs * 1000L else Long.MAX_VALUE

            val rawDecodedStream = java.io.ByteArrayOutputStream()
            var loopSafety = 0

            while (!isOutputEOS && loopSafety < 10000) {
                loopSafety++
                if (!isInputEOS) {
                    val inIndex = codec.dequeueInputBuffer(timeoutUs)
                    if (inIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inIndex)
                        if (inputBuffer != null) {
                            inputBuffer.clear()
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0 || (extractor.sampleTime > maxUs)) {
                                codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isInputEOS = true
                            } else {
                                codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (outIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        val chunk = ByteArray(bufferInfo.size)
                        outputBuffer.get(chunk)
                        rawDecodedStream.write(chunk)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isOutputEOS = true
                    }
                }
            }

            val decodedRawPcm = rawDecodedStream.toByteArray()
            if (decodedRawPcm.isEmpty()) {
                return null
            }

            return resamplePcm(
                srcBytes = decodedRawPcm,
                srcSampleRate = srcSampleRate,
                destSampleRate = targetSampleRate,
                srcChannels = srcChannels,
                destChannels = targetChannels
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed extracting audio track from $mediaPath: ${e.message}")
            return null
        } finally {
            try { codec?.stop(); codec?.release() } catch (e: Exception) {}
            try { extractor.release() } catch (e: Exception) {}
        }
    }

    private fun applyVolumeToPcm(pcm: ByteArray, volume: Float): ByteArray {
        if (volume == 1.0f) return pcm
        val clampedVol = volume.coerceIn(0f, 2.0f)
        if (clampedVol == 0f) return ByteArray(pcm.size)
        val result = ByteArray(pcm.size)
        val shorts = pcm.size / 2
        for (i in 0 until shorts) {
            val low = pcm[i * 2].toInt() and 0xFF
            val high = pcm[i * 2 + 1].toInt()
            val sample = ((high shl 8) or low).toShort().toFloat() * clampedVol
            val clampedSample = com.ritvyom.yashoraReelgenerator.engine.audio.AudioMixerEngine.softLimit(sample)
            result[i * 2] = (clampedSample.toInt() and 0xFF).toByte()
            result[i * 2 + 1] = ((clampedSample.toInt() shr 8) and 0xFF).toByte()
        }
        return result
    }

    private fun getBgMusicPcm(context: Context, category: String, targetSampleRate: Int, targetChannels: Int, totalLengthBytes: Int): ByteArray {
        val bgBytes = ByteArray(totalLengthBytes)
        // 1. Try to load ambient_loop from assets
        var loopBytes: ByteArray? = null
        try {
            val assetName = "sfx/ambient_loop.wav.b64"
            val input = context.assets.open(assetName)
            val b64String = input.bufferedReader().use { it.readText() }.trim()
            val decodedWavBytes = android.util.Base64.decode(b64String, android.util.Base64.DEFAULT)
            val info = WavFileInfo.readWavFileFromBytes(decodedWavBytes)
            if (info != null) {
                loopBytes = resamplePcm(
                    srcBytes = info.pcmData,
                    srcSampleRate = info.sampleRate,
                    destSampleRate = targetSampleRate,
                    srcChannels = info.channels,
                    destChannels = targetChannels
                )
            }
        } catch (e: Exception) {
            Log.e("BgMusic", "Failed loading ambient loop from assets", e)
        }

        // 2. Fallback or procedural enhancement: generate cozy synthesizer chord progression based on selected genre!
        val sampleRateDouble = targetSampleRate.toDouble()
        val totalSamples = totalLengthBytes / (targetChannels * 2)
        val shortBuffer = ShortArray(totalSamples * targetChannels)

        // Decode loop if available
        var loopShorts: ShortArray? = null
        if (loopBytes != null) {
            val size = loopBytes.size / 2
            loopShorts = ShortArray(size)
            for (i in 0 until size) {
                val low = loopBytes[i * 2].toInt() and 0xFF
                val high = loopBytes[i * 2 + 1].toInt()
                loopShorts[i] = ((high shl 8) or low).toShort()
            }
        }

        // Category-based chord progressions (frequencies in Hz)
        val chords = when (category) {
            "Emotional" -> listOf(
                listOf(261.63f, 329.63f, 392.00f, 523.25f), // C Major
                listOf(220.00f, 261.63f, 329.63f, 440.00f), // A Minor
                listOf(349.23f, 440.00f, 523.25f, 698.46f), // F Major
                listOf(293.66f, 349.23f, 440.00f, 587.33f)  // D Minor
            )
            "Cinematic" -> listOf(
                listOf(196.00f, 246.94f, 293.66f, 392.00f), // G Major
                listOf(220.00f, 261.63f, 329.63f, 440.00f), // A Minor
                listOf(174.61f, 220.00f, 261.63f, 349.23f), // F Major
                listOf(164.81f, 196.00f, 246.94f, 329.63f)  // E Minor
            )
            "Motivational", "Happy" -> listOf(
                listOf(261.63f, 329.63f, 392.00f, 523.25f), // C Major
                listOf(293.66f, 349.23f, 440.00f, 587.33f), // G Major
                listOf(220.00f, 261.63f, 329.63f, 440.00f), // A Minor
                listOf(349.23f, 440.00f, 523.25f, 698.46f)  // F Major
            )
            "Technology" -> listOf(
                listOf(110.00f, 165.00f, 220.00f, 330.00f), // A5 open
                listOf(130.81f, 196.00f, 261.63f, 392.00f), // C5 open
                listOf(146.83f, 220.00f, 293.66f, 440.00f), // D5 open
                listOf(98.00f,  146.83f, 196.00f, 293.66f)  // G5 open
            )
            "Horror" -> listOf(
                listOf(220.00f, 233.08f, 311.13f, 440.00f), // A diminished
                listOf(220.00f, 261.63f, 277.18f, 415.30f)  // Dark chromatics
            )
            else -> listOf(
                listOf(261.63f, 329.63f, 392.00f, 523.25f), // C Major
                listOf(349.23f, 440.00f, 523.25f, 698.46f)  // F Major
            )
        }

        val chordDurationSeconds = 4.0
        val chordSamples = (chordDurationSeconds * targetSampleRate).toInt()

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRateDouble
            
            // Loop ambient track if available, as a beautiful acoustic backdrop!
            var ambientVal = 0.0
            if (loopShorts != null && loopShorts.isNotEmpty()) {
                val idx = if (targetChannels == 2) {
                    (i * 2) % loopShorts.size
                } else {
                    i % loopShorts.size
                }
                ambientVal = loopShorts[idx].toDouble() / Short.MAX_VALUE.toDouble()
            }

            // Procedural synth pad for warm chords
            val chordIndex = ((i / chordSamples) % chords.size)
            val activeChords = chords[chordIndex]
            
            var synthVal = 0.0
            for (freq in activeChords) {
                // Gentle sine wave with subtle LFO
                val lfo = 1.0 + 0.15 * sin(2.0 * Math.PI * 0.5 * t)
                val phase = 2.0 * Math.PI * freq * t
                synthVal += sin(phase) * lfo
            }
            synthVal = (synthVal / activeChords.size) * 0.25 // scale down to not clip

            // Multi-genre stylistic filters:
            if (category == "Horror") {
                val creepLfo = sin(2.0 * Math.PI * 6.0 * t) * 4.0
                val phase = 2.0 * Math.PI * (110.0 + creepLfo) * t
                synthVal += sin(phase) * 0.15
            }

            // Blend ambient track and synth pad
            val blendedVal = if (loopShorts != null) {
                (ambientVal * 0.5) + (synthVal * 0.5)
            } else {
                synthVal
            }

            val sampleShort = (blendedVal * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

            if (targetChannels == 2) {
                shortBuffer[i * 2] = sampleShort
                shortBuffer[i * 2 + 1] = sampleShort
            } else {
                shortBuffer[i] = sampleShort
            }
        }

        // Convert back to bytes
        for (i in shortBuffer.indices) {
            val value = shortBuffer[i].toInt()
            bgBytes[i * 2] = (value and 0xFF).toByte()
            bgBytes[i * 2 + 1] = ((value shr 8) and 0xFF).toByte()
        }

        return bgBytes
    }

    // Composes continuous timeline audio track synchronized to slide durations with Stereo Audio and Background Music support
    fun generateTimelineAudio(
        context: Context,
        voiceFiles: List<File?>,
        scenes: List<Scene>,
        bgMusicCategory: String,
        bgMusicVolume: Float,
        bgMusicEnabled: Boolean,
        preserveOriginalAudio: Boolean = true,
        pcmOutputFile: File
    ): Pair<Int, Boolean> {
        val sampleRate = 44100
        val channels = 2
        val bitsPerSample = 16
        val bytesPerSecond = sampleRate * channels * (bitsPerSample / 8)
 
        var fos: FileOutputStream? = null
        val tempVoicePcmFile = File(context.cacheDir, "temp_voice_raw_${System.currentTimeMillis()}.pcm")
        var tempVoiceFos: FileOutputStream? = null
        
        var cumulativeAudioTime = 0.0
        var cumulativeBytesTarget = 0L
  
        try {
            tempVoiceFos = FileOutputStream(tempVoicePcmFile)
            for (i in scenes.indices) {
                val scene = scenes[i]
                val f = voiceFiles.getOrNull(i)
                
                var pcmData: ByteArray? = null
                if (f != null && f.exists() && f.length() >= 44) {
                    val info = WavFileInfo.readWavFile(f)
                    if (info != null) {
                        // Dynamically resample to target timeline sample rate & channels to fix pitch/robotic glitch
                        pcmData = resamplePcm(
                            srcBytes = info.pcmData,
                            srcSampleRate = info.sampleRate,
                            destSampleRate = sampleRate,
                            srcChannels = info.channels,
                            destChannels = channels
                        )
                    }
                }

                // 2. If no TTS voice file, check for custom voice recording / imported audio track
                if (pcmData == null && !scene.customVoiceAudioPath.isNullOrEmpty()) {
                    try {
                        pcmData = decodeAudioTrackToPcm(
                            context = context,
                            mediaPath = scene.customVoiceAudioPath!!,
                            targetSampleRate = sampleRate,
                            targetChannels = channels,
                            maxDurationMs = (scene.durationSeconds * 1000L)
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed reading customVoiceAudioPath: ${scene.customVoiceAudioPath}", e)
                    }
                }

                // 3. User Original Video Audio Preservation:
                // If scene contains an imported or recorded video, extract its original voice & audio track when preserveOriginalAudio is true!
                if (pcmData == null && preserveOriginalAudio && !scene.mediaPath.isNullOrEmpty() && (scene.mediaType == "VIDEO" || isVideoPath(context, scene.mediaPath))) {
                    try {
                        Log.d(TAG, "Extracting original video audio track from scene ${i + 1}: ${scene.mediaPath}")
                        pcmData = decodeAudioTrackToPcm(
                            context = context,
                            mediaPath = scene.mediaPath!!,
                            targetSampleRate = sampleRate,
                            targetChannels = channels,
                            maxDurationMs = (scene.durationSeconds * 1000L)
                        )
                        if (pcmData != null) {
                            Log.d(TAG, "Successfully extracted ${pcmData.size} bytes of original video audio from scene ${i + 1}!")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed extracting video audio for scene ${i + 1}", e)
                    }
                }

                // 4. Apply scene volume adjustment if volume is customized
                if (pcmData != null && scene.volume != 1.0f) {
                    pcmData = applyVolumeToPcm(pcmData, scene.volume)
                }
 
                val voiceDurationSecondsFloat = if (pcmData != null) {
                    pcmData.size.toDouble() / bytesPerSecond
                } else {
                    0.0
                }
 
                val effectiveDuration = if (voiceDurationSecondsFloat > 0.0) {
                    voiceDurationSecondsFloat
                } else {
                    scene.durationSeconds.toDouble().coerceAtLeast(1.0)
                }
 
                cumulativeAudioTime += effectiveDuration
                val nextCumulativeBytesTarget = Math.round(cumulativeAudioTime * bytesPerSecond).toLong()
                
                var rawSegmentBytesCount = (nextCumulativeBytesTarget - cumulativeBytesTarget).toInt()
                // Align to stereo frame boundary (4 bytes per frame: 2 channels * 2 bytes/sample)
                if (rawSegmentBytesCount % 4 != 0) {
                    rawSegmentBytesCount += 4 - (rawSegmentBytesCount % 4)
                }
                
                val targetBytesCount = if (pcmData != null) {
                    pcmData.size.coerceAtLeast(rawSegmentBytesCount)
                } else {
                    rawSegmentBytesCount
                }
                
                cumulativeBytesTarget += targetBytesCount
 
                if (pcmData != null) {
                    tempVoiceFos.write(pcmData)
                    if (pcmData.size < targetBytesCount) {
                        val silenceNeeded = targetBytesCount - pcmData.size
                        tempVoiceFos.write(ByteArray(silenceNeeded))
                    }
                } else {
                    tempVoiceFos.write(ByteArray(targetBytesCount))
                }
            }
            tempVoiceFos.close()
            tempVoiceFos = null

            // Read the full voice PCM file
            val voiceBytes = tempVoicePcmFile.readBytes()
            
            // Check if project has user's own video(s)
            val hasUserVideoClips = scenes.any {
                !it.mediaPath.isNullOrEmpty() &&
                (it.mediaType == "VIDEO" || isVideoPath(context, it.mediaPath)) &&
                (it.visualPrompt.contains("Imported", ignoreCase = true) || it.narrationText.isBlank() || it.mediaPath?.contains("user_media") == true)
            }

            // User requirement: Background music is for AI-generated reels.
            // If user imported their own video and preserveOriginalAudio is true, original audio is preserved without default AI bg music unless explicitly selected!
            val shouldApplyBgMusic = bgMusicEnabled && (
                !hasUserVideoClips || 
                !preserveOriginalAudio || 
                (!bgMusicCategory.equals("None", ignoreCase = true) && !bgMusicCategory.equals("Cinematic", ignoreCase = true) && bgMusicVolume > 0f)
            )

            // Generate background music of the exact same length
            val bgBytes = if (shouldApplyBgMusic && voiceBytes.isNotEmpty()) {
                getBgMusicPcm(context, bgMusicCategory, sampleRate, channels, voiceBytes.size)
            } else {
                ByteArray(0)
            }

            // Blend voice and bg music sample by sample
            fos = FileOutputStream(pcmOutputFile)
            val mixedBytes = ByteArray(voiceBytes.size)
            val shortSize = voiceBytes.size / 2
            
            for (i in 0 until shortSize) {
                val vLow = voiceBytes[i * 2].toInt() and 0xFF
                val vHigh = voiceBytes[i * 2 + 1].toInt()
                val voiceSample = ((vHigh shl 8) or vLow).toShort().toFloat()

                var bgSample = 0f
                if (shouldApplyBgMusic && bgBytes.size > i * 2 + 1) {
                    val bgLow = bgBytes[i * 2].toInt() and 0xFF
                    val bgHigh = bgBytes[i * 2 + 1].toInt()
                    bgSample = ((bgHigh shl 8) or bgLow).toShort().toFloat()
                }

                // Voice/Original audio is at 100% volume, background music is mixed at target volume level
                val mixedSample = (voiceSample * 1.0f) + (bgSample * bgMusicVolume)
                // Use professional soft-limiting / cubic saturation from AudioMixerEngine to eliminate harsh digital clipping
                val mixedShort = com.ritvyom.yashoraReelgenerator.engine.audio.AudioMixerEngine.softLimit(mixedSample)

                mixedBytes[i * 2] = (mixedShort.toInt() and 0xFF).toByte()
                mixedBytes[i * 2 + 1] = ((mixedShort.toInt() shr 8) and 0xFF).toByte()
            }
            
            fos.write(mixedBytes)
            Log.d(TAG, "Successfully prepared timeline audio of size: ${mixedBytes.size} bytes (bgMusic: $shouldApplyBgMusic, userVideo: $hasUserVideoClips)")
            return Pair(sampleRate, true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed assembling timeline PCM file", e)
            return Pair(sampleRate, false)
        } finally {
            try { tempVoiceFos?.close() } catch (e: Exception) {}
            try { fos?.close() } catch (e: Exception) {}
            try { if (tempVoicePcmFile.exists()) tempVoicePcmFile.delete() } catch (e: Exception) {}
        }
    }

    // Encodes PCM linear bytes to audio compression AAC file format standard
    private fun encodePcmToAac(
        pcmFile: File,
        aacFile: File,
        sampleRate: Int,
        channels: Int
    ): Boolean {
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var fis: FileInputStream? = null
        try {
            fis = FileInputStream(pcmFile)
            val audioMimeType = MediaFormat.MIMETYPE_AUDIO_AAC
            codec = MediaCodec.createEncoderByType(audioMimeType)
            val format = MediaFormat.createAudioFormat(audioMimeType, sampleRate, channels)
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            format.setInteger(MediaFormat.KEY_BIT_RATE, 128000)
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1024 * 16)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            muxer = MediaMuxer(aacFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var audioTrackIndex = -1
            var muxerStarted = false

            val bufferInfo = MediaCodec.BufferInfo()
            val tempPcmBuffer = ByteArray(1024 * 4)
            var presentationTimeUs = 0L
            var totalBytesRead = 0L
            var inputEos = false
            var outputEos = false

            while (!outputEos) {
                // Queue Input Buffer
                if (!inputEos) {
                    val inputBufIndex = codec.dequeueInputBuffer(10000)
                    if (inputBufIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputBufIndex)!!
                        inputBuffer.clear()
                        val limit = inputBuffer.remaining().coerceAtMost(tempPcmBuffer.size)
                        val bytesRead = fis.read(tempPcmBuffer, 0, limit)
                        if (bytesRead <= 0) {
                            codec.queueInputBuffer(
                                inputBufIndex,
                                0,
                                0,
                                presentationTimeUs,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputEos = true
                            Log.d(TAG, "Audio Encoder input EOF reached. Queued BUFFER_FLAG_END_OF_STREAM.")
                        } else {
                            inputBuffer.put(tempPcmBuffer, 0, bytesRead)
                            inputBuffer.position(0)
                            codec.queueInputBuffer(
                                inputBufIndex,
                                0,
                                bytesRead,
                                presentationTimeUs,
                                0
                            )
                            totalBytesRead += bytesRead
                            val sampleCount = totalBytesRead / (channels * 2)
                            presentationTimeUs = (1000000L * sampleCount) / sampleRate
                        }
                    }
                }

                // Dequeue Output buffers
                while (true) {
                    val outputBufIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                    if (outputBufIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        break
                    } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val newFormat = codec.outputFormat
                        audioTrackIndex = muxer.addTrack(newFormat)
                        muxer.start()
                        muxerStarted = true
                    } else if (outputBufIndex >= 0) {
                        val encodedData = codec.getOutputBuffer(outputBufIndex)!!
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            bufferInfo.size = 0
                        }
                        if (bufferInfo.size != 0 && muxerStarted) {
                            encodedData.position(bufferInfo.offset)
                            encodedData.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(audioTrackIndex, encodedData, bufferInfo)
                        }
                        codec.releaseOutputBuffer(outputBufIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputEos = true
                            Log.d(TAG, "Audio Encoder output EOF successfully drained from codec.")
                            break
                        }
                    }
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed encoding PCM to AAC standard audio file", e)
            return false
        } finally {
            try { fis?.close() } catch (e: Exception) {}
            try { codec?.stop(); codec?.release() } catch (e: Exception) {}
            try { muxer?.stop(); muxer?.release() } catch (e: Exception) {}
        }
    }

    // Remix/multiplex standalone silent video stream and audio stream into a single MP4 container
    @android.annotation.SuppressLint("WrongConstant")
    fun mergeVideoAndAudio(
        videoFile: File,
        audioFile: File,
        outputFile: File
    ): Boolean {
        var videoExtractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        try {
            videoExtractor = MediaExtractor()
            videoExtractor.setDataSource(videoFile.absolutePath)

            audioExtractor = MediaExtractor()
            audioExtractor.setDataSource(audioFile.absolutePath)

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // Collect Video Track Metadata
            var videoTrackIdx = -1
            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoExtractor.selectTrack(i)
                    videoTrackIdx = muxer.addTrack(format)
                    break
                }
            }

            // Collect Audio Track Metadata
            var audioTrackIdx = -1
            for (i in 0 until audioExtractor.trackCount) {
                val format = audioExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioExtractor.selectTrack(i)
                    audioTrackIdx = muxer.addTrack(format)
                    break
                }
            }

            // Start Muxing
            muxer.start()

            val byteCapacity = 1024 * 1024
            val writeBuffer = ByteBuffer.allocate(byteCapacity)
            val info = MediaCodec.BufferInfo()

            var videoDone = (videoTrackIdx < 0)
            var audioDone = (audioTrackIdx < 0)

            while (!videoDone || !audioDone) {
                if (Thread.currentThread().isInterrupted) {
                    Log.d(TAG, "Muxing split aborted via task cancel/interrupt.")
                    return false
                }

                // Properly interleave frames clock-wise according to timestamp increments
                val writeVideo = !videoDone && (audioDone || videoExtractor.sampleTime <= audioExtractor.sampleTime)

                if (writeVideo) {
                    info.offset = 0
                    info.size = videoExtractor.readSampleData(writeBuffer, 0)
                    if (info.size < 0) {
                        videoDone = true
                    } else {
                        info.presentationTimeUs = videoExtractor.sampleTime
                        info.flags = videoExtractor.sampleFlags
                        muxer.writeSampleData(videoTrackIdx, writeBuffer, info)
                        videoExtractor.advance()
                    }
                } else if (!audioDone) {
                    info.offset = 0
                    info.size = audioExtractor.readSampleData(writeBuffer, 0)
                    if (info.size < 0) {
                        audioDone = true
                    } else {
                        info.presentationTimeUs = audioExtractor.sampleTime
                        info.flags = audioExtractor.sampleFlags
                        muxer.writeSampleData(audioTrackIdx, writeBuffer, info)
                        audioExtractor.advance()
                    }
                }
            }

            Log.d(TAG, "Audio and Video components joined successfully.")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed merging video track and audio audio tracks", e)
            return false
        } finally {
            try { videoExtractor?.release() } catch (e: Exception) {}
            try { audioExtractor?.release() } catch (e: Exception) {}
            try {
                muxer?.stop()
                muxer?.release()
            } catch (e: Exception) {}
        }
    }

    private fun getTypefaceByName(name: String?): android.graphics.Typeface {
        return when (name) {
            "Sans-Serif" -> android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.NORMAL)
            "Serif" -> android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.NORMAL)
            "Monospace" -> android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
            "Display Bold" -> android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
            "TikTok Style" -> android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
            "Insta Premium" -> android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD_ITALIC)
            else -> android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
        }
    }

    fun isVideoPath(context: Context, path: String?): Boolean {
        if (path.isNullOrEmpty()) return false
        val lower = path.lowercase()
        if (lower.contains(".mp4") || lower.contains(".3gp") || lower.contains(".3gpp") ||
            lower.contains(".mkv") || lower.contains(".webm") || lower.contains(".mov") ||
            lower.contains(".avi") || lower.contains("video") || lower.contains(".m4v")) {
            return true
        }
        if (path.startsWith("content://")) {
            try {
                val mime = context.contentResolver.getType(android.net.Uri.parse(path))
                if (mime != null && mime.startsWith("video/")) {
                    return true
                }
            } catch (e: Exception) {}
        }
        val retriever = android.media.MediaMetadataRetriever()
        try {
            if (path.startsWith("content://")) {
                retriever.setDataSource(context, android.net.Uri.parse(path))
            } else if (path.startsWith("/")) {
                retriever.setDataSource(path)
            } else {
                retriever.setDataSource(path, HashMap<String, String>())
            }
            val hasVideo = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
            retriever.release()
            return hasVideo == "yes"
        } catch (e: Exception) {
            try { retriever.release() } catch (ex: Exception) {}
        }
        return false
    }

    private fun drawSceneFrame(
        canvas: Canvas,
        width: Int,
        height: Int,
        bmp: Bitmap?,
        frameIndex: Int,
        totalFrames: Int,
        sceneIndex: Int,
        scene: Scene,
        scaleFactor: Float,
        textPaint: Paint,
        overlayPaint: Paint,
        backgroundBoxPaint: Paint,
        brandingPaint: Paint,
        paint: Paint,
        watermarkConfig: WatermarkConfig? = null
    ) {
        val progress = frameIndex.toFloat() / totalFrames.coerceAtLeast(1)
        
        canvas.drawColor(Color.parseColor("#0C0914"))
        
        if (bmp != null) {
            val srcRect = Rect(0, 0, bmp.width, bmp.height)
            val scaleX = width.toFloat() / bmp.width
            val scaleY = height.toFloat() / bmp.height
            val baseScale = Math.max(scaleX, scaleY)
            
            // Alternating cinematic Ken Burns zoom and pan modes
            val effectType = sceneIndex % 4
            var currentScale = baseScale
            var dX = 0f
            var dY = 0f
            
            when (effectType) {
                0 -> { // Ken Burns Zoom In
                    val scaleMul = 1.0f + 0.12f * progress
                    currentScale = baseScale * scaleMul
                }
                1 -> { // Ken Burns Zoom Out
                    val scaleMul = 1.12f - 0.12f * progress
                    currentScale = baseScale * scaleMul
                }
                2 -> { // Smooth Right-to-Left Pan with constant comfortable boundary scale
                    currentScale = baseScale * 1.12f
                    val maxPanOffset = (width * 0.06f)
                    dX = maxPanOffset - (progress * 2f * maxPanOffset)
                }
                3 -> { // Smooth Left-to-Right Pan + subtle Zoom In
                    val scaleMul = 1.02f + 0.08f * progress
                    currentScale = baseScale * scaleMul
                    val maxPanOffset = (width * 0.04f)
                    dX = -maxPanOffset + (progress * 2f * maxPanOffset)
                }
            }
            
            val dW = bmp.width * currentScale
            val dH = bmp.height * currentScale
            val left = (width - dW) / 2 + dX
            val top = (height - dH) / 2 + dY
            val dstRect = RectF(left, top, left + dW, top + dH)
            
            canvas.save()
            
            if (scene.rotationDegrees != 0) {
                canvas.rotate(scene.rotationDegrees.toFloat(), width / 2f, height / 2f)
            }
            
            val sX = if (scene.isFlippedHorizontal) -1f else 1f
            val sY = if (scene.isFlippedVertical) -1f else 1f
            if (scene.isFlippedHorizontal || scene.isFlippedVertical) {
                canvas.scale(sX, sY, width / 2f, height / 2f)
            }
            
            if (scene.maskShape == "Circle") {
                val path = android.graphics.Path().apply {
                    addCircle(width / 2f, height / 2f, Math.min(width, height) / 2f, android.graphics.Path.Direction.CW)
                }
                canvas.clipPath(path)
            } else if (scene.maskShape == "Rectangle") {
                val path = android.graphics.Path().apply {
                    val margin = 20f * scaleFactor
                    addRoundRect(
                        margin, margin, width - margin, height - margin,
                        24f * scaleFactor, 24f * scaleFactor,
                        android.graphics.Path.Direction.CW
                    )
                }
                canvas.clipPath(path)
            }
            
            val colorPaint = Paint(paint).apply {
                val cm = android.graphics.ColorMatrix()
                cm.setSaturation(scene.saturationValue)
                if (scene.brightnessValue != 0f) {
                    val brightOffset = (scene.brightnessValue / 100f) * 255f
                    val matrixArray = cm.array
                    matrixArray[4] = matrixArray[4] + brightOffset
                    matrixArray[9] = matrixArray[9] + brightOffset
                    matrixArray[14] = matrixArray[14] + brightOffset
                    cm.set(matrixArray)
                }
                if (scene.warmthValue != 0f) {
                    val warmthOffset = (scene.warmthValue / 100f) * 40f
                    val matrixArray = cm.array
                    matrixArray[4] = matrixArray[4] + warmthOffset
                    matrixArray[14] = matrixArray[14] - warmthOffset
                    cm.set(matrixArray)
                }
                colorFilter = android.graphics.ColorMatrixColorFilter(cm)
            }

            canvas.drawBitmap(bmp, srcRect, dstRect, colorPaint)
            canvas.restore()
        } else {
            // High-fidelity active dynamic wave background for text-only narration cards!
            val color1 = blendColors(Color.parseColor("#1C093A"), Color.parseColor("#3C096C"), progress)
            val color2 = Color.parseColor("#05010E")
            val grad = android.graphics.LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                color1, color2,
                android.graphics.Shader.TileMode.CLAMP
            )
            paint.shader = grad
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.shader = null
        }
        
        // Scene Transitions: Seamless Fade, Wipe, Slide, Dissolve via Media3 Transition Engine
        if (scene.transitionType.isNotBlank() && scene.transitionType != "None") {
            Media3TransitionEngine.drawTransition(
                canvas = canvas,
                width = width,
                height = height,
                frameIndex = frameIndex,
                totalFrames = totalFrames,
                transitionType = scene.transitionType,
                transitionDurationMs = scene.transitionDurationMs,
                fps = 30
            )
        }

        // Selected filter tint
        val filter = EditorConstants.FILTERS.getOrNull(scene.selectedFilterIndex)
        if (filter != null && filter.tintColor.alpha > 0f) {
            val tintPaint = Paint().apply {
                color = Color.argb(
                    (filter.tintColor.alpha * 255).toInt(),
                    (filter.tintColor.red * 255).toInt(),
                    (filter.tintColor.green * 255).toInt(),
                    (filter.tintColor.blue * 255).toInt()
                )
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), tintPaint)
        }
        
        val curTypeface = getTypefaceByName(scene.captionFont)
        textPaint.typeface = curTypeface
        overlayPaint.typeface = curTypeface
        
        // Centered header text overlay (e.g. key point, title, intro)
        scene.textOverlay?.let { over ->
            if (over.isNotEmpty()) {
                val parsedColor = try {
                    Color.parseColor(scene.overlayColor)
                } catch (e: Exception) {
                    Color.WHITE
                }
                overlayPaint.color = parsedColor
                
                val rect = Rect()
                overlayPaint.getTextBounds(over, 0, over.length, rect)
                val boxW = rect.width() + (24 * scaleFactor).toInt()
                val boxH = rect.height() + (16 * scaleFactor).toInt()
                val boxRect = RectF(
                    (width - boxW) / 2f,
                    80f * scaleFactor,
                    (width + boxW) / 2f,
                    80f * scaleFactor + boxH
                )
                canvas.drawRoundRect(boxRect, 8f * scaleFactor, 8f * scaleFactor, backgroundBoxPaint)
                
                canvas.drawText(
                    over.uppercase(),
                    width / 2f,
                    80f * scaleFactor + boxH / 2f + rect.height() / 2f - 2f * scaleFactor,
                    overlayPaint
                )
            }
        }
        
        // Wrap narration text/subtitles inside a bottom translucent modern pill box
        val subtitle = scene.subtitle
        if (subtitle.isNotEmpty()) {
            val defaultColorStr = scene.subtitleColor.ifEmpty { "#FFFFFF" }
            val defaultBgColorStr = scene.subtitleBgColor.ifEmpty { "#99000000" }
            
            val resolvedTextColor: Int
            val resolvedBgColor: Int
            val drawBackgroundBox: Boolean
            val useGlow: Boolean
            
            when (scene.subtitleDesign) {
                "TikTok Yellow" -> {
                    resolvedTextColor = Color.parseColor("#FFFF00")
                    resolvedBgColor = Color.parseColor("#CC111111")
                    drawBackgroundBox = true
                    useGlow = false
                }
                "Cyber Neon" -> {
                    resolvedTextColor = Color.parseColor("#00F0FF")
                    resolvedBgColor = Color.TRANSPARENT
                    drawBackgroundBox = false
                    useGlow = true
                }
                "Hot Pink Style" -> {
                    resolvedTextColor = Color.parseColor("#FFFFFF")
                    resolvedBgColor = Color.parseColor("#FF007F")
                    drawBackgroundBox = true
                    useGlow = false
                }
                "Minimal Borderless" -> {
                    resolvedTextColor = try { Color.parseColor(defaultColorStr) } catch (e: Exception) { Color.WHITE }
                    resolvedBgColor = Color.TRANSPARENT
                    drawBackgroundBox = false
                    useGlow = false
                }
                "Royal Gold" -> {
                    resolvedTextColor = Color.parseColor("#FFD700")
                    resolvedBgColor = Color.parseColor("#E61A1502")
                    drawBackgroundBox = true
                    useGlow = false
                }
                else -> { // "Classic Box"
                    resolvedTextColor = try { Color.parseColor(defaultColorStr) } catch (e: Exception) { Color.WHITE }
                    resolvedBgColor = try { Color.parseColor(defaultBgColorStr) } catch (e: Exception) { Color.parseColor("#99000000") }
                    drawBackgroundBox = true
                    useGlow = false
                }
            }
            
            textPaint.color = resolvedTextColor
            if (useGlow) {
                textPaint.setShadowLayer(10f * scaleFactor, 0f, 0f, Color.parseColor("#00F0FF"))
            } else {
                textPaint.setShadowLayer(4f * scaleFactor, 2f * scaleFactor, 2f * scaleFactor, Color.BLACK)
            }
            
            backgroundBoxPaint.color = resolvedBgColor
            
            val lines = splitTextToLines(subtitle, textPaint, (width - 60f * scaleFactor))
            val textHeight = 28f * scaleFactor
            val padding = 16f * scaleFactor
            val boxRect = RectF(
                20f * scaleFactor,
                height - 130f * scaleFactor - (lines.size * textHeight + padding),
                width - 20f * scaleFactor,
                height - 130f * scaleFactor
            )
            
            if (drawBackgroundBox) {
                canvas.drawRoundRect(boxRect, 12f * scaleFactor, 12f * scaleFactor, backgroundBoxPaint)
            }
            
            for (lineIdx in lines.indices) {
                val lineText = lines[lineIdx]
                canvas.drawText(
                    lineText,
                    width / 2f,
                    boxRect.top + padding + (lineIdx + 0.7f) * textHeight,
                    textPaint
                )
            }
            textPaint.clearShadowLayer()
        }
        
        // Soft watermark
        renderWatermarkOnCanvas(canvas, width, height, scaleFactor, watermarkConfig)
    }

    private fun blendColors(color1: Int, color2: Int, ratio: Float): Int {
        val r = (Color.red(color1) * (1 - ratio) + Color.red(color2) * ratio).toInt()
        val g = (Color.green(color1) * (1 - ratio) + Color.green(color2) * ratio).toInt()
        val b = (Color.blue(color1) * (1 - ratio) + Color.blue(color2) * ratio).toInt()
        return Color.rgb(r, g, b)
    }

    private fun fillImageFromBitmap(image: android.media.Image, bitmap: Bitmap) {
        val width = image.width
        val height = image.height
        val argb = IntArray(width * height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)
        
        val planes = image.planes
        val yPlane = planes[0].buffer
        val uPlane = planes[1].buffer
        val vPlane = planes[2].buffer
        
        val yRowStride = planes[0].rowStride
        val uRowStride = planes[1].rowStride
        val vRowStride = planes[2].rowStride
        
        val yPixelStride = planes[0].pixelStride
        val uPixelStride = planes[1].pixelStride
        val vPixelStride = planes[2].pixelStride
        
        yPlane.clear()
        uPlane.clear()
        vPlane.clear()
        
        // Stuff Y plane
        for (y in 0 until height) {
            yPlane.position(y * yRowStride)
            for (x in 0 until width) {
                val rgb = argb[y * width + x]
                val r = (rgb shr 16) and 0xff
                val g = (rgb shr 8) and 0xff
                val b = rgb and 0xff
                var yVal = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yVal = yVal.coerceIn(0, 255)
                yPlane.put(yVal.toByte())
            }
        }
        
        // Stuff UV planes (Interleaved or Planar depending on Pixel Stride)
        val uvHeight = height / 2
        val uvWidth = width / 2
        for (y in 0 until uvHeight) {
            val uPos = y * uRowStride
            val vPos = y * vRowStride
            
            for (x in 0 until uvWidth) {
                val x0 = x * 2
                val y0 = y * 2
                val c00 = argb[y0 * width + x0]
                val c01 = argb[y0 * width + (x0 + 1).coerceAtMost(width - 1)]
                val c10 = argb[(y0 + 1).coerceAtMost(height - 1) * width + x0]
                val c11 = argb[(y0 + 1).coerceAtMost(height - 1) * width + (x0 + 1).coerceAtMost(width - 1)]
                
                val r = (((c00 shr 16) and 0xff) + ((c01 shr 16) and 0xff) + ((c10 shr 16) and 0xff) + ((c11 shr 16) and 0xff)) / 4
                val g = (((c00 shr 8) and 0xff) + ((c01 shr 8) and 0xff) + ((c10 shr 8) and 0xff) + ((c11 shr 8) and 0xff)) / 4
                val b = ((c00 and 0xff) + (c01 and 0xff) + (c10 and 0xff) + (c11 and 0xff)) / 4
                
                var uVal = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                var vVal = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                
                uVal = uVal.coerceIn(0, 255)
                vVal = vVal.coerceIn(0, 255)
                
                uPlane.position(uPos + x * uPixelStride)
                uPlane.put(uVal.toByte())
                
                vPlane.position(vPos + x * vPixelStride)
                vPlane.put(vVal.toByte())
            }
        }
    }
}

// Inline Wave Binary File metadata reader & Native Decoder
class WavFileInfo(
    val sampleRate: Int,
    val channels: Int,
    val bitsPerSample: Int,
    val pcmData: ByteArray
) {
    companion object {
        fun readWavFile(file: File): WavFileInfo? {
            if (!file.exists() || file.length() < 12) return null
            
            // For .wav files, try direct RIFF chunk parsing first for instant and accurate PCM extraction
            if (file.name.endsWith(".wav", ignoreCase = true)) {
                try {
                    val bytes = file.readBytes()
                    val direct = readWavFileFromBytes(bytes)
                    if (direct != null && direct.sampleRate > 0 && direct.channels in 1..8 && direct.pcmData.isNotEmpty()) {
                        return direct
                    }
                } catch (e: Exception) {
                    Log.d("WavReader", "Direct WAV parsing failed for ${file.name}, trying native decoder fallback", e)
                }
            }

            // Fallback: try native decoding (for mp3, m4a, ogg, etc.)
            try {
                val decoded = decodeAudioFileToPcm(file)
                if (decoded != null && decoded.sampleRate > 0 && decoded.channels in 1..8 && decoded.pcmData.isNotEmpty()) {
                    return decoded
                }
            } catch (e: Exception) {
                Log.d("WavReader", "Native decoding failed for ${file.name}", e)
            }

            return null
        }

        fun readWavFileFromBytes(bytes: ByteArray): WavFileInfo? {
            if (bytes.size < 12) return null
            try {
                val riff = String(bytes, 0, 4, Charsets.US_ASCII)
                val wave = String(bytes, 8, 4, Charsets.US_ASCII)
                if (riff != "RIFF" || wave != "WAVE") {
                    return null
                }

                var channels = 1
                var sampleRate = 44100
                var bitsPerSample = 16
                var pcmData: ByteArray? = null

                var offset = 12
                while (offset + 8 <= bytes.size) {
                    val chunkId = String(bytes, offset, 4, Charsets.US_ASCII)
                    val chunkSize = (bytes[offset + 4].toInt() and 0xff) or
                            ((bytes[offset + 5].toInt() and 0xff) shl 8) or
                            ((bytes[offset + 6].toInt() and 0xff) shl 16) or
                            ((bytes[offset + 7].toInt() and 0xff) shl 24)

                    val chunkDataOffset = offset + 8
                    if (chunkSize < 0 || chunkDataOffset + chunkSize > bytes.size) {
                        if (chunkId == "data" && chunkDataOffset < bytes.size) {
                            pcmData = bytes.copyOfRange(chunkDataOffset, bytes.size)
                        }
                        break
                    }

                    if (chunkId == "fmt " && chunkSize >= 14) {
                        channels = (bytes[chunkDataOffset + 2].toInt() and 0xff) or
                                ((bytes[chunkDataOffset + 3].toInt() and 0xff) shl 8)
                        sampleRate = (bytes[chunkDataOffset + 4].toInt() and 0xff) or
                                ((bytes[chunkDataOffset + 5].toInt() and 0xff) shl 8) or
                                ((bytes[chunkDataOffset + 6].toInt() and 0xff) shl 16) or
                                ((bytes[chunkDataOffset + 7].toInt() and 0xff) shl 24)
                        if (chunkSize >= 16) {
                            bitsPerSample = (bytes[chunkDataOffset + 14].toInt() and 0xff) or
                                    ((bytes[chunkDataOffset + 15].toInt() and 0xff) shl 8)
                        }
                    } else if (chunkId == "data") {
                        pcmData = bytes.copyOfRange(chunkDataOffset, chunkDataOffset + chunkSize)
                        break
                    }

                    val padding = if (chunkSize % 2 != 0) 1 else 0
                    offset += 8 + chunkSize + padding
                }

                if (pcmData == null || pcmData.isEmpty()) {
                    if (bytes.size > 44) {
                        pcmData = bytes.copyOfRange(44, bytes.size)
                    } else {
                        return null
                    }
                }

                return WavFileInfo(sampleRate, channels, bitsPerSample, pcmData)
            } catch (e: Exception) {
                Log.e("WavReader", "Failed parsing wave stream details from bytes", e)
                return null
            }
        }

        private fun decodeAudioFileToPcm(file: File): WavFileInfo? {
            val extractor = android.media.MediaExtractor()
            var codec: android.media.MediaCodec? = null
            try {
                extractor.setDataSource(file.absolutePath)
                var trackIndex = -1
                var format: android.media.MediaFormat? = null
                for (i in 0 until extractor.trackCount) {
                    val fmt = extractor.getTrackFormat(i)
                    val mime = fmt.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("audio/")) {
                        trackIndex = i
                        format = fmt
                        break
                    }
                }
                if (trackIndex < 0 || format == null) {
                    extractor.release()
                    return null
                }
                
                extractor.selectTrack(trackIndex)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                codec = android.media.MediaCodec.createDecoderByType(mime)
                codec.configure(format, null, null, 0)
                codec.start()
                
                var sampleRate = if (format.containsKey(android.media.MediaFormat.KEY_SAMPLE_RATE)) {
                    try { format.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE) } catch (e: Exception) { 44100 }
                } else {
                    44100
                }
                var channels = if (format.containsKey(android.media.MediaFormat.KEY_CHANNEL_COUNT)) {
                    try { format.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT) } catch (e: Exception) { 1 }
                } else {
                    1
                }
                val bitsPerSample = 16 // MediaCodec default output for audio decoders is 16-bit PCM
                
                val outputStream = java.io.ByteArrayOutputStream()
                val info = android.media.MediaCodec.BufferInfo()
                var sawInputEOS = false
                var sawOutputEOS = false
                
                var noOutputCounter = 0
                while (!sawOutputEOS && noOutputCounter < 200) {
                    if (!sawInputEOS) {
                        val inputBufferIndex = codec.dequeueInputBuffer(10000)
                        if (inputBufferIndex >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputBufferIndex)!!
                            inputBuffer.clear()
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                sawInputEOS = true
                            } else {
                                codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    
                    val outputBufferIndex = codec.dequeueOutputBuffer(info, 10000)
                    if (outputBufferIndex >= 0) {
                        noOutputCounter = 0
                        val outputBuffer = codec.getOutputBuffer(outputBufferIndex)!!
                        outputBuffer.position(info.offset)
                        outputBuffer.limit(info.offset + info.size)
                        val chunk = ByteArray(info.size)
                        outputBuffer.get(chunk)
                        outputBuffer.clear()
                        outputStream.write(chunk)
                        codec.releaseOutputBuffer(outputBufferIndex, false)
                        if ((info.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            sawOutputEOS = true
                        }
                    } else if (outputBufferIndex == android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        noOutputCounter = 0
                        val newFormat = codec.outputFormat
                        if (newFormat.containsKey(android.media.MediaFormat.KEY_SAMPLE_RATE)) {
                            sampleRate = newFormat.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (newFormat.containsKey(android.media.MediaFormat.KEY_CHANNEL_COUNT)) {
                            channels = newFormat.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT)
                        }
                    } else {
                        if (sawInputEOS) {
                            noOutputCounter++
                        }
                    }
                }
                
                val pcmData = outputStream.toByteArray()
                Log.d("WavReader", "Natively decoded ${file.name} to raw PCM: size=${pcmData.size}, sampleRate=$sampleRate, channels=$channels")
                return WavFileInfo(sampleRate, channels, bitsPerSample, pcmData)
            } catch (e: Exception) {
                Log.e("WavReader", "Failed decoding audio file ${file.name} to PCM", e)
                return null
            } finally {
                try { codec?.stop(); codec?.release() } catch (e: Exception) {}
                try { extractor.release() } catch (e: Exception) {}
            }
        }
    }
}

// Ultra high-performance progressive video decoder providing 100% smooth, fluent real video frame extraction
class SequentialFrameDecoder(
    private val videoFile: File,
    private val targetWidth: Int,
    private val targetHeight: Int,
    private val isEnhanceVideo: Boolean = false
) {
    private var extractor: android.media.MediaExtractor? = null
    private var decoder: android.media.MediaCodec? = null
    private val bufferInfo = android.media.MediaCodec.BufferInfo()
    private var isEOS = false
    private var videoTrackIndex = -1
    private var outputFormat: android.media.MediaFormat? = null
    private var lastDecodedFrame: Bitmap? = null
    private var pixelArray: IntArray? = null
    private var yByteArray: ByteArray? = null
    private var uByteArray: ByteArray? = null
    private var vByteArray: ByteArray? = null

    init {
        try {
            extractor = android.media.MediaExtractor()
            extractor!!.setDataSource(videoFile.absolutePath)
            val trackCount = extractor!!.trackCount
            for (i in 0 until trackCount) {
                val format = extractor!!.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoTrackIndex = i
                    extractor!!.selectTrack(i)
                    decoder = android.media.MediaCodec.createDecoderByType(mime)
                    decoder!!.configure(format, null, null, 0)
                    decoder!!.start()
                    break
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SequentialDecoder", "Failed to initialize decoder: ${e.message}", e)
            release()
        }
    }

    fun getNextFrame(): Bitmap? {
        if (decoder == null || extractor == null || isEOS) {
            return lastDecodedFrame
        }
        var decodedBitmap: Bitmap? = null
        var attempts = 0
        val timeoutUs = 4000L
        while (decodedBitmap == null && attempts < 50) {
            attempts++
            if (!isEOS) {
                val inputIndex = try {
                    decoder!!.dequeueInputBuffer(timeoutUs)
                } catch (e: Exception) {
                    -1
                }
                if (inputIndex >= 0) {
                    try {
                        val inputBuffer = decoder!!.getInputBuffer(inputIndex)!!
                        var sampleSize = extractor!!.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            // Seamless loop: rewind extractor back to start position
                            extractor!!.seekTo(0, android.media.MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                            sampleSize = extractor!!.readSampleData(inputBuffer, 0)
                        }
                        if (sampleSize < 0) {
                            decoder!!.queueInputBuffer(inputIndex, 0, 0, 0, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEOS = true
                        } else {
                            decoder!!.queueInputBuffer(inputIndex, 0, sampleSize, extractor!!.sampleTime, 0)
                            extractor!!.advance()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("SequentialDecoder", "Error feeding input buffer", e)
                    }
                }
            }

            val outputIndex = try {
                decoder!!.dequeueOutputBuffer(bufferInfo, timeoutUs)
            } catch (e: Exception) {
                -1
            }
            if (outputIndex >= 0) {
                if ((bufferInfo.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    isEOS = false
                    try {
                        extractor!!.seekTo(0, android.media.MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                        decoder!!.flush()
                    } catch (e: Exception) {}
                }
                if (bufferInfo.size > 0) {
                    decodedBitmap = getDecodedBitmap(outputIndex)
                }
                try {
                    decoder!!.releaseOutputBuffer(outputIndex, false)
                } catch (e: Exception) {}
            } else if (outputIndex == android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                outputFormat = decoder!!.outputFormat
            }
        }

        if (decodedBitmap != null) {
            lastDecodedFrame = decodedBitmap
            return decodedBitmap
        }

        return lastDecodedFrame
    }

    private fun getDecodedBitmap(outputIndex: Int): Bitmap? {
        try {
            val image = decoder!!.getOutputImage(outputIndex) ?: return null
            val format = outputFormat ?: decoder!!.outputFormat
            val width = format.getInteger(android.media.MediaFormat.KEY_WIDTH)
            val height = format.getInteger(android.media.MediaFormat.KEY_HEIGHT)
            
            val bmp = imageToBitmap(image, width, height)
            image.close()
            if (bmp != null) {
                if (bmp.width != targetWidth || bmp.height != targetHeight) {
                    val scaled = Bitmap.createScaledBitmap(bmp, targetWidth, targetHeight, true)
                    bmp.recycle()
                    return scaled
                }
            }
            return bmp
        } catch (e: Exception) {
            android.util.Log.e("SequentialDecoder", "Error converting frame to bitmap: ${e.message}", e)
            return null
        }
    }

    private fun imageToBitmap(image: android.media.Image, width: Int, height: Int): Bitmap? {
        try {
            val planes = image.planes
            val yPlane = planes[0]
            val uPlane = planes[1]
            val vPlane = planes[2]

            val yBuffer = yPlane.buffer
            val uBuffer = uPlane.buffer
            val vBuffer = vPlane.buffer

            val yRowStride = yPlane.rowStride
            val yPixelStride = yPlane.pixelStride
            val uRowStride = uPlane.rowStride
            val uPixelStride = uPlane.pixelStride
            val vRowStride = vPlane.rowStride
            val vPixelStride = vPlane.pixelStride

            val yRemaining = yBuffer.remaining()
            if (yByteArray == null || yByteArray!!.size < yRemaining) {
                yByteArray = ByteArray(yRemaining)
            }
            val yBytes = yByteArray!!
            yBuffer.get(yBytes, 0, yRemaining)

            val uRemaining = uBuffer.remaining()
            if (uByteArray == null || uByteArray!!.size < uRemaining) {
                uByteArray = ByteArray(uRemaining)
            }
            val uBytes = uByteArray!!
            uBuffer.get(uBytes, 0, uRemaining)

            val vRemaining = vBuffer.remaining()
            if (vByteArray == null || vByteArray!!.size < vRemaining) {
                vByteArray = ByteArray(vRemaining)
            }
            val vBytes = vByteArray!!
            vBuffer.get(vBytes, 0, vRemaining)

            val totalPixels = width * height
            if (pixelArray == null || pixelArray!!.size < totalPixels) {
                pixelArray = IntArray(totalPixels)
            }
            val pixels = pixelArray!!

            for (y in 0 until height) {
                val yRowStart = y * yRowStride
                val uvRowStart = (y / 2) * uRowStride
                val vRowStart = (y / 2) * vRowStride
                val rowOffset = y * width
                val isEdgeY = (y == 0 || y == height - 1)
                val yUpStart = if (y > 0) (y - 1) * yRowStride else yRowStart
                val yDownStart = if (y < height - 1) (y + 1) * yRowStride else yRowStart

                for (x in 0 until width) {
                    val yIndex = yRowStart + x * yPixelStride
                    val uIndex = uvRowStart + (x / 2) * uPixelStride
                    val vIndex = vRowStart + (x / 2) * vPixelStride

                    val rawY = if (yIndex in 0 until yRemaining) (yBytes[yIndex].toInt() and 0xFF) else 0
                    val rawU = (if (uIndex in 0 until uRemaining) (uBytes[uIndex].toInt() and 0xFF) else 128) - 128
                    val rawV = (if (vIndex in 0 until vRemaining) (vBytes[vIndex].toInt() and 0xFF) else 128) - 128

                    val yVal: Int
                    val uVal: Int
                    val vVal: Int

                    if (isEnhanceVideo) {
                        // High-speed Luminance Unsharp Laplacian Kernel for fine edge details & clarity
                        val yValSharp = if (!isEdgeY && x > 0 && x < width - 1) {
                            val yL = yBytes[yRowStart + (x - 1) * yPixelStride].toInt() and 0xFF
                            val yR = yBytes[yRowStart + (x + 1) * yPixelStride].toInt() and 0xFF
                            val yU = yBytes[yUpStart + x * yPixelStride].toInt() and 0xFF
                            val yD = yBytes[yDownStart + x * yPixelStride].toInt() and 0xFF
                            val deltaY = (4 * rawY - yL - yR - yU - yD).coerceIn(-64, 64)
                            (rawY + (deltaY * 0.45f).toInt()).coerceIn(0, 255)
                        } else {
                            rawY
                        }
                        // Dynamic range tone stretch
                        yVal = if (yValSharp < 128) {
                            (yValSharp * yValSharp / 135).coerceIn(0, 255)
                        } else {
                            (255 - ((255 - yValSharp) * (255 - yValSharp) / 135)).coerceIn(0, 255)
                        }
                        // Color Vibrancy (+15%)
                        uVal = (rawU * 1.15f).toInt().coerceIn(-128, 127)
                        vVal = (rawV * 1.15f).toInt().coerceIn(-128, 127)
                    } else {
                        yVal = rawY
                        uVal = rawU
                        vVal = rawV
                    }

                    val r = (yVal + 1.370705f * vVal).toInt().coerceIn(0, 255)
                    val g = (yVal - 0.337633f * uVal - 0.698001f * vVal).toInt().coerceIn(0, 255)
                    val b = (yVal + 1.732446f * uVal).toInt().coerceIn(0, 255)

                    pixels[rowOffset + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            return Bitmap.createBitmap(pixels, 0, width, width, height, Bitmap.Config.ARGB_8888)
        } catch (e: Exception) {
            android.util.Log.e("SequentialDecoder", "Error converting YUV directly to Bitmap: ${e.message}", e)
            return null
        }
    }

    private fun vPlaneByteVal(vBuf: java.nio.ByteBuffer, index: Int): Byte {
        return if (index >= 0 && index < vBuf.limit()) vBuf.get(index) else 0.toByte()
    }

    private fun uPlaneByteVal(uBuf: java.nio.ByteBuffer, index: Int): Byte {
        return if (index >= 0 && index < uBuf.limit()) uBuf.get(index) else 0.toByte()
    }

    fun release() {
        try {
            lastDecodedFrame?.recycle()
            lastDecodedFrame = null
        } catch (e: Exception) {}
        try {
            decoder?.stop()
        } catch (e: Exception) {}
        try {
            decoder?.release()
        } catch (e: Exception) {}
        decoder = null

        try {
            extractor?.release()
        } catch (e: Exception) {}
        extractor = null
    }
}
