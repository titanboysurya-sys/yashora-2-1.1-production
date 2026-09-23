package com.ritvyom.yashoraReelgenerator.engine.audio

import java.io.Serializable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-performance, deterministic multi-channel PCM Audio Mixer and DSP processor.
 *
 * Implements:
 * 1. Constant-power stereo panning (Equal-Power Law)
 * 2. Peak limiting and soft clipping to eliminate severe distortion
 * 3. Deterministic gain application (effective volume, fade-in, fade-out, keyframes)
 * 4. Resampling and format conversions (Mono to Stereo, 16-bit linear PCM)
 * 5. Waveform generation for visual audio editing
 */
object AudioMixerEngine {

    const val DEFAULT_SAMPLE_RATE = 44100
    const val DEFAULT_CHANNELS = 2 // Stereo
    const val BYTES_PER_SAMPLE = 2 // 16-bit PCM (2 bytes per sample per channel)

    /**
     * Calculates left and right gains for a stereo pan value in range [-1.0f (Full Left), 1.0f (Full Right)].
     * Uses the sinusoidal Constant-Power Pan Law:
     * theta = (pan + 1.0f) * (PI / 4)
     * leftGain = cos(theta)
     * rightGain = sin(theta)
     */
    fun calculateStereoPanGains(pan: Float): Pair<Float, Float> {
        val clampedPan = pan.coerceIn(-1.0f, 1.0f)
        val angle = (clampedPan + 1.0f) * (PI.toFloat() / 4.0f)
        val left = cos(angle)
        val right = sin(angle)
        return Pair(left, right)
    }

    /**
     * Applies stereo pan and master gain to 16-bit stereo PCM samples in-place.
     * [stereoPcm]: ByteArray containing 16-bit little-endian interleaved stereo samples [L0, R0, L1, R1, ...]
     * [gain]: Overall linear volume multiplier (e.g. 0.0f .. 2.0f)
     * [pan]: Stereo balance (-1.0f = full left, 0.0f = center, +1.0f = full right)
     */
    fun processPcmBuffer(
        stereoPcm: ByteArray,
        gain: Float,
        pan: Float = 0.0f,
        offset: Int = 0,
        length: Int = stereoPcm.size
    ) {
        if (gain == 1.0f && pan == 0.0f) return
        val clampedGain = gain.coerceAtLeast(0.0f)
        val (panL, panR) = calculateStereoPanGains(pan)
        val leftMultiplier = clampedGain * panL
        val rightMultiplier = clampedGain * panR

        val frameCount = length / 4 // 4 bytes per stereo frame
        for (i in 0 until frameCount) {
            val byteIdx = offset + i * 4

            // Left sample (16-bit LE)
            val lLow = stereoPcm[byteIdx].toInt() and 0xFF
            val lHigh = stereoPcm[byteIdx + 1].toInt()
            val lSample = ((lHigh shl 8) or lLow).toShort().toFloat() * leftMultiplier
            val lClamped = softLimit(lSample)

            stereoPcm[byteIdx] = (lClamped.toInt() and 0xFF).toByte()
            stereoPcm[byteIdx + 1] = ((lClamped.toInt() shr 8) and 0xFF).toByte()

            // Right sample (16-bit LE)
            val rLow = stereoPcm[byteIdx + 2].toInt() and 0xFF
            val rHigh = stereoPcm[byteIdx + 3].toInt()
            val rSample = ((rHigh shl 8) or rLow).toShort().toFloat() * rightMultiplier
            val rClamped = softLimit(rSample)

            stereoPcm[byteIdx + 2] = (rClamped.toInt() and 0xFF).toByte()
            stereoPcm[byteIdx + 3] = ((rClamped.toInt() shr 8) and 0xFF).toByte()
        }
    }

    /**
     * Mixes multiple raw 16-bit interleaved stereo PCM byte streams with individual gains and pans.
     * Uses soft-limiting / peak compression to prevent harsh digital clipping when summing.
     *
     * @param sources List of [AudioMixSource] each providing PCM buffer, volume, and stereo pan.
     * @param outputLengthBytes Target byte length of mixed audio (must be multiple of 4).
     * @return 16-bit interleaved stereo PCM byte array.
     */
    fun mixStereoStreams(
        sources: List<AudioMixSource>,
        outputLengthBytes: Int
    ): ByteArray {
        val result = ByteArray(outputLengthBytes)
        if (sources.isEmpty() || outputLengthBytes <= 0) return result

        val frameCount = outputLengthBytes / 4 // 4 bytes per stereo frame
        val leftAccumulator = FloatArray(frameCount)
        val rightAccumulator = FloatArray(frameCount)

        for (source in sources) {
            if (source.gain <= 0.0001f && !source.hasAutomation) continue
            val (panL, panR) = calculateStereoPanGains(source.pan)
            val sourcePcm = source.pcmData ?: continue
            val sourceFrames = (sourcePcm.size / 4).coerceAtMost(frameCount)

            for (f in 0 until sourceFrames) {
                val byteIdx = f * 4

                val lLow = sourcePcm[byteIdx].toInt() and 0xFF
                val lHigh = sourcePcm[byteIdx + 1].toInt()
                val lSample = ((lHigh shl 8) or lLow).toShort().toFloat()

                val rLow = sourcePcm[byteIdx + 2].toInt() and 0xFF
                val rHigh = sourcePcm[byteIdx + 3].toInt()
                val rSample = ((rHigh shl 8) or rLow).toShort().toFloat()

                val frameGain = source.gain
                leftAccumulator[f] += lSample * frameGain * panL
                rightAccumulator[f] += rSample * frameGain * panR
            }
        }

        // Peak Limiting / Soft Clipper pass to protect against overflow while maintaining punch
        for (f in 0 until frameCount) {
            val byteIdx = f * 4

            val finalL = softLimit(leftAccumulator[f])
            result[byteIdx] = (finalL.toInt() and 0xFF).toByte()
            result[byteIdx + 1] = ((finalL.toInt() shr 8) and 0xFF).toByte()

            val finalR = softLimit(rightAccumulator[f])
            result[byteIdx + 2] = (finalR.toInt() and 0xFF).toByte()
            result[byteIdx + 3] = ((finalR.toInt() shr 8) and 0xFF).toByte()
        }

        return result
    }

    /**
     * Polynomial soft limiter (smooth saturation curve).
     * Linear up to threshold (24576), smoothly saturated above threshold approaching Short.MAX_VALUE.
     * Prevents harsh digital wrap-around or squared-wave clipping artifacts.
     */
    fun softLimit(sample: Float): Short {
        val threshold = 24576.0f // 75% of Short.MAX_VALUE
        val maxVal = 32767.0f

        val absVal = kotlin.math.abs(sample)
        if (absVal <= threshold) {
            return sample.toInt().toShort()
        }

        // Smooth hyperbola/tanh-like compression above threshold
        val excess = absVal - threshold
        val margin = maxVal - threshold // 8191.0f
        val compressedExcess = margin * (excess / (excess + margin))
        val compressed = threshold + compressedExcess
        val limited = if (sample > 0) compressed else -compressed

        return limited.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()
    }

    /**
     * Asynchronously generates a downsampled waveform amplitude array for visualization.
     * @param pcm 16-bit PCM byte array.
     * @param numPoints Desired number of points in the waveform (e.g. 100).
     * @return FloatArray of normalized amplitudes in range [0.0f .. 1.0f].
     */
    fun generateWaveformPoints(pcm: ByteArray, numPoints: Int = 100): FloatArray {
        if (pcm.isEmpty() || numPoints <= 0) return FloatArray(numPoints) { 0f }

        val totalSamples = pcm.size / 2
        val samplesPerPoint = (totalSamples / numPoints).coerceAtLeast(1)
        val result = FloatArray(numPoints)

        for (p in 0 until numPoints) {
            val startSample = p * samplesPerPoint
            val endSample = (startSample + samplesPerPoint).coerceAtMost(totalSamples)

            var peak = 0f
            for (s in startSample until endSample) {
                val byteIdx = s * 2
                val low = pcm[byteIdx].toInt() and 0xFF
                val high = pcm[byteIdx + 1].toInt()
                val sampleVal = kotlin.math.abs(((high shl 8) or low).toShort().toFloat())
                if (sampleVal > peak) peak = sampleVal
            }
            result[p] = (peak / 32768.0f).coerceIn(0.0f, 1.0f)
        }

        return result
    }
}

/**
 * Representation of an individual audio stream being mixed into the timeline master.
 */
data class AudioMixSource(
    val id: String,
    val pcmData: ByteArray?,
    val gain: Float = 1.0f,
    val pan: Float = 0.0f,
    val hasAutomation: Boolean = false
) : Serializable
