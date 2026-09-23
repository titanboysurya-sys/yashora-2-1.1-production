package com.ritvyom.yashoraReelgenerator.presentation.utils

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.exp

object SoundSynth {
    val TRANSIENT_SFX = mapOf(
        "Whoosh" to "Heavy sliding wind passage",
        "Boom" to "Deep rich sub-bass explosive drop",
        "Pop" to "Retro bubble/plastic pop sound",
        "Sci-Fi Laser" to "Cosmic downward high frequency pitch sweep",
        "Digital Hit" to "Metallic FM pluck hit",
        "Notification Swipe" to "Warm ascending double tone",
        "Cyber Pulse" to "Low-pass filter square wave trigger"
    )

    suspend fun playSfx(name: String) = withContext(Dispatchers.Default) {
        val sampleRate = 44100
        val durationSec = when (name) {
            "Boom" -> 1.5f
            "Whoosh" -> 0.8f
            "Pop" -> 0.2f
            "Sci-Fi Laser" -> 0.4f
            "Digital Hit" -> 0.3f
            "Notification Swipe" -> 0.4f
            "Cyber Pulse" -> 0.3f
            else -> return@withContext
        }
        val numSamples = (durationSec * sampleRate).toInt()
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples
            val time = i.toFloat() / sampleRate

            val wave = when (name) {
                "Whoosh" -> {
                    // White noise swept with a narrow bandpass filter / window
                    val noise = (Math.random() * 2.0 - 1.0).toFloat()
                    val envelope = sin(progress * PI.toFloat())
                    noise * envelope * 0.4f
                }
                "Boom" -> {
                    // Deep exponential sub-drop: starts at 120Hz, drops to 25Hz
                    val fStart = 120f
                    val fEnd = 25f
                    val currentFreq = fStart + (fEnd - fStart) * progress
                    val phase = 2f * PI.toFloat() * currentFreq * time
                    val baseWave = sin(phase).toFloat()
                    val envelope = exp(-3f * progress) // rapid decay
                    baseWave * envelope * 0.8f
                }
                "Pop" -> {
                    // Rapid bubble pop: extremely fast pitch sweep (400 to 1200 Hz back to 300)
                    val currentFreq = 400f + 800f * sin(progress * PI.toFloat())
                    val phase = 2f * PI.toFloat() * currentFreq * time
                    val baseWave = sin(phase).toFloat()
                    val envelope = sin(progress * PI.toFloat())
                    baseWave * envelope * 0.5f
                }
                "Sci-Fi Laser" -> {
                    // Fast frequency sweep down with triangle-ish wave
                    val fStart = 2200f
                    val fEnd = 150f
                    val currentFreq = fStart + (fEnd - fStart) * progress * progress
                    val phase = 2f * PI.toFloat() * currentFreq * time
                    val baseWave = (phase % (2f * PI.toFloat()) / PI.toFloat() - 1f).toFloat()
                    val envelope = (1.0f - progress)
                    baseWave * envelope * 0.3f
                }
                "Digital Hit" -> {
                    // Metallic pluck FM synth: Modulator frequency is 3x Carrier
                    val carrierFreq = 220f
                    val modulatorFreq = 660f
                    val modIndex = 4.0f * exp(-6f * progress)
                    val modPhase = 2f * PI.toFloat() * modulatorFreq * time
                    val carrierPhase = 2f * PI.toFloat() * carrierFreq * time + modIndex * sin(modPhase)
                    val baseWave = sin(carrierPhase).toFloat()
                    val envelope = exp(-5f * progress)
                    baseWave * envelope * 0.6f
                }
                "Notification Swipe" -> {
                    // Dual tone chime ascending
                    val f1 = 440f
                    val f2 = 880f
                    val envelope = sin(progress * PI.toFloat())
                    val freq = if (progress < 0.4f) f1 else f2
                    val phase = 2f * PI.toFloat() * freq * time
                    sin(phase).toFloat() * envelope * 0.4f
                }
                "Cyber Pulse" -> {
                    // Thick square wave: modulated
                    val freq = 80f
                    val phase = 2f * PI.toFloat() * freq * time
                    val baseWave = if (sin(phase) > 0) 1f else -1f
                    val envelope = exp(-4f * progress)
                    baseWave * envelope * 0.4f
                }
                else -> 0f
            }

            buffer[i] = (wave * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        try {
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(numSamples * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(buffer, 0, buffer.size)
            audioTrack.play()
            // Wait for duration + safety padding
            delay((durationSec * 1000).toLong() + 200)
            audioTrack.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
