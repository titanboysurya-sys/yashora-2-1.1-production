package com.ritvyom.yashoraReelgenerator.engine.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.ritvyom.yashoraReelgenerator.core.model.CanonicalTimeline
import com.ritvyom.yashoraReelgenerator.engine.TimelineEvaluator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Real-time Multi-Track Audio Preview Player.
 *
 * Synchronizes with [CanonicalTimeline] and [TimelineEvaluator].
 * Plays mixed audio preview via low-latency Android [AudioTrack] without cumulative drift.
 * Ensures small-screen performance: avoids allocating duplicate players, caches active PCM buffers,
 * and maintains continuous clock synchronization with video preview.
 */
class MultiTrackAudioPreviewPlayer(
    private val context: Context
) {
    companion object {
        private const val TAG = "AudioPreviewPlayer"
        private const val SAMPLE_RATE = AudioMixerEngine.DEFAULT_SAMPLE_RATE
        private const val CHANNELS = AudioMixerEngine.DEFAULT_CHANNELS
        private const val BUFFER_CHUNK_SAMPLES = 2048 // ~46ms chunk size
        private const val BUFFER_CHUNK_BYTES = BUFFER_CHUNK_SAMPLES * 4
    }

    private val audioExecutor = Executors.newSingleThreadExecutor()
    private val audioDispatcher = audioExecutor.asCoroutineDispatcher()
    private val audioScope = CoroutineScope(audioDispatcher)

    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var currentPositionUs = 0L
    private var timeline: CanonicalTimeline? = null
    private var timelineEvaluator: TimelineEvaluator? = null
    private var playbackJob: Job? = null

    // Lightweight in-memory PCM cache by URI to avoid re-decoding audio during repeated scrub/loop
    private val pcmCache = android.util.LruCache<String, ByteArray>(10)

    init {
        initAudioTrack()
    }

    private fun initAudioTrack() {
        try {
            val minBufSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = (minBufSize * 2).coerceAtLeast(BUFFER_CHUNK_BYTES * 4)

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Failed initializing AudioTrack for preview playback", e)
        }
    }

    fun setTimeline(newTimeline: CanonicalTimeline) {
        timeline = newTimeline
        timelineEvaluator = TimelineEvaluator(newTimeline)
    }

    fun play() {
        if (isPlaying) return
        isPlaying = true
        try {
            audioTrack?.play()
        } catch (e: Exception) {
            Log.w(TAG, "AudioTrack play error", e)
        }

        playbackJob?.cancel()
        playbackJob = audioScope.launch {
            streamAudioLoop()
        }
    }

    fun pause() {
        isPlaying = false
        playbackJob?.cancel()
        playbackJob = null
        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (e: Exception) {
            Log.w(TAG, "AudioTrack pause error", e)
        }
    }

    fun seekTo(timeUs: Long) {
        currentPositionUs = timeUs
        try {
            audioTrack?.flush()
        } catch (_: Exception) {}
    }

    private suspend fun streamAudioLoop() {
        val chunkDurationUs = (BUFFER_CHUNK_SAMPLES * 1_000_000L) / SAMPLE_RATE
        val tempChunk = ByteArray(BUFFER_CHUNK_BYTES)

        while (audioScope.isActive && isPlaying) {
            val evaluator = timelineEvaluator
            val tl = timeline

            if (evaluator == null || tl == null || tl.totalDurationUs <= 0L) {
                delay(20)
                continue
            }

            val activeNodes = evaluator.evaluateAudioForTime(currentPositionUs)

            if (activeNodes.isEmpty()) {
                tempChunk.fill(0)
                audioTrack?.write(tempChunk, 0, tempChunk.size)
            } else {
                // Synthesize chunk from evaluated nodes
                tempChunk.fill(0)
                for (node in activeNodes) {
                    val pcm = getPcmForUri(node.audioUri) ?: continue
                    val (panL, panR) = AudioMixerEngine.calculateStereoPanGains(node.pan)
                    val effectiveGain = node.effectiveVolume
                    val sourceSampleOffset = (node.sourceTimeUs * SAMPLE_RATE / 1_000_000L).toInt() * 2 // stereo

                    val frameCount = BUFFER_CHUNK_SAMPLES
                    for (f in 0 until frameCount) {
                        val srcIdx = (sourceSampleOffset + f * 2) * 2 // 4 bytes per frame
                        if (srcIdx + 3 < pcm.size && srcIdx >= 0) {
                            val lLow = pcm[srcIdx].toInt() and 0xFF
                            val lHigh = pcm[srcIdx + 1].toInt()
                            val lVal = ((lHigh shl 8) or lLow).toShort().toFloat() * effectiveGain * panL

                            val rLow = pcm[srcIdx + 2].toInt() and 0xFF
                            val rHigh = pcm[srcIdx + 3].toInt()
                            val rVal = ((rHigh shl 8) or rLow).toShort().toFloat() * effectiveGain * panR

                            val dstIdx = f * 4
                            val curLLow = tempChunk[dstIdx].toInt() and 0xFF
                            val curLHigh = tempChunk[dstIdx + 1].toInt()
                            val curL = ((curLHigh shl 8) or curLLow).toShort().toFloat() + lVal

                            val curRLow = tempChunk[dstIdx + 2].toInt() and 0xFF
                            val curRHigh = tempChunk[dstIdx + 3].toInt()
                            val curR = ((curRHigh shl 8) or curRLow).toShort().toFloat() + rVal

                            val finalL = AudioMixerEngine.softLimit(curL)
                            val finalR = AudioMixerEngine.softLimit(curR)

                            tempChunk[dstIdx] = (finalL.toInt() and 0xFF).toByte()
                            tempChunk[dstIdx + 1] = ((finalL.toInt() shr 8) and 0xFF).toByte()
                            tempChunk[dstIdx + 2] = (finalR.toInt() and 0xFF).toByte()
                            tempChunk[dstIdx + 3] = ((finalR.toInt() shr 8) and 0xFF).toByte()
                        }
                    }
                }
                audioTrack?.write(tempChunk, 0, tempChunk.size)
            }

            currentPositionUs += chunkDurationUs
            if (currentPositionUs >= tl.totalDurationUs) {
                currentPositionUs = 0L // Loop
            }
        }
    }

    private fun getPcmForUri(uri: String): ByteArray? {
        if (uri.isBlank()) return null
        val cached = pcmCache.get(uri)
        if (cached != null) return cached

        // Decode audio via ReelVideoCompiler's robust decoder
        val decoded = com.ritvyom.yashoraReelgenerator.presentation.utils.ReelVideoCompiler.decodeAudioTrackToPcm(
            context = context,
            mediaPath = uri,
            targetSampleRate = SAMPLE_RATE,
            targetChannels = CHANNELS
        )
        if (decoded != null) {
            pcmCache.put(uri, decoded)
        }
        return decoded
    }

    fun release() {
        isPlaying = false
        playbackJob?.cancel()
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
        pcmCache.evictAll()
        audioScope.cancel()
        audioExecutor.shutdown()
    }
}
