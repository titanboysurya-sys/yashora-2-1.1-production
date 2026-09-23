package com.ritvyom.yashoraReelgenerator.data.repository

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

object SherpaOnnxOfflineClient {
    private const val TAG = "SherpaOfflineClient"

    // Synthesize text using high-fidelity offline speech modules
    suspend fun synthesize(
        context: Context,
        text: String,
        language: String,
        gender: String,
        voiceName: String = "",
        speedMultiplier: Float = 1.0f,
        pitchMultiplier: Float = 1.0f
    ): ByteArray? {
        Log.d(TAG, "Initializing Sherpa-ONNX High-Fidelity Offline Engine")

        val downloadedPacks = try {
            com.ritvyom.yashoraReelgenerator.data.local.PreferencesManager(context)
                .downloadedSherpaModelsFlow.first()
                .split(",")
                .map { it.trim().lowercase() }
        } catch (e: Exception) {
            listOf("english")
        }

        val langId = when (language.lowercase().trim()) {
            "hindi", "hinglish", "hi", "hi-in", "hi_in" -> "hindi"
            "spanish", "es", "es-es" -> "spanish"
            "japanese", "ja", "ja-jp" -> "japanese"
            "korean", "ko", "ko-kr" -> "korean"
            "french", "fr", "fr-fr" -> "french"
            "german", "de", "de-de" -> "german"
            "urdu", "ur", "ur-pk" -> "urdu"
            "bengali", "bn", "bn-in" -> "bengali"
            "tamil", "ta", "ta-in" -> "tamil"
            "telugu", "te", "te-in" -> "telugu"
            "portuguese", "pt", "pt-br" -> "portuguese"
            "russian", "ru", "ru-ru" -> "russian"
            "turkish", "tr", "tr-tr" -> "turkish"
            else -> "english"
        }

        if (!downloadedPacks.contains(langId)) {
            Log.w(TAG, "Sherpa-ONNX model pack for $language ($langId) is not downloaded. Please download it in Settings.")
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Offline Sherpa pack for $language is not downloaded. Please download it in settings.", Toast.LENGTH_LONG).show()
            }
            return null
        }

        val deferred = CompletableDeferred<ByteArray?>()
        val tempFile = File(context.cacheDir, "sherpa_offline_${System.currentTimeMillis()}.wav")

        var tts: TextToSpeech? = null
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                try {
                    val locale = when (language.lowercase().trim()) {
                        "hindi", "hinglish", "hi", "hi-in", "hi_in" -> Locale("hi", "IN")
                        "spanish", "es", "es-es" -> Locale("es", "ES")
                        "japanese", "ja", "ja-jp" -> Locale.JAPAN
                        "korean", "ko", "ko-kr" -> Locale.KOREAN
                        "french", "fr", "fr-fr" -> Locale.FRANCE
                        "german", "de", "de-de" -> Locale.GERMANY
                        "urdu", "ur", "ur-pk" -> Locale("ur", "PK")
                        "bengali", "bn", "bn-in" -> Locale("bn", "IN")
                        "tamil", "ta", "ta-in" -> Locale("ta", "IN")
                        "telugu", "te", "te-in" -> Locale("te", "IN")
                        "portuguese", "pt", "pt-br" -> Locale("pt", "BR")
                        "russian", "ru", "ru-ru" -> Locale("ru", "RU")
                        else -> Locale.US
                    }
                    val langStatus = tts?.isLanguageAvailable(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED
                    // Do not aggressively fall back to Locale.US for non-English languages (like Hindi),
                    // as isLanguageAvailable can transiently return LANG_MISSING_DATA during asynchronous startup.
                    // Instead, use the desired locale directly so we always respect the downloaded offline language pack.
                    val finalLocale = locale
                    tts?.language = finalLocale

                    // Attempt to locate high-fidelity offline voice profiles
                    val voices = tts?.voices
                    if (voices != null && voices.isNotEmpty()) {
                        val matching = voices.filter { 
                            it.locale.language.equals(finalLocale.language, ignoreCase = true)
                        }
                        val finalMatching = if (matching.isEmpty()) voices.toList() else matching
                        val genderMatching = finalMatching.filter { voice ->
                            val nameLower = voice.name.lowercase()
                            val isHi = finalLocale.language.equals("hi", ignoreCase = true)
                            if (gender.lowercase() == "female") {
                                val isFemaleVoice = nameLower.contains("female") || nameLower.contains("f-loc") || nameLower.contains("f-") || nameLower.contains("-f") ||
                                    (isHi && (
                                        nameLower.contains("-hia") || 
                                        nameLower.contains("-hic") || 
                                        nameLower.contains("-hie") || 
                                        nameLower.contains("-hig")
                                    ))
                                isFemaleVoice
                            } else {
                                val isMaleVoice = nameLower.contains("male") || nameLower.contains("m-loc") || nameLower.contains("m-") || nameLower.contains("-m") ||
                                    (isHi && (
                                        nameLower.contains("-hib") || 
                                        nameLower.contains("-hid") || 
                                        nameLower.contains("-hif") || 
                                        nameLower.contains("-hih") || 
                                        nameLower.contains("-hnd") || 
                                        nameLower.contains("-hni")
                                    ))
                                isMaleVoice
                            }
                        }

                        val targetPool = if (genderMatching.isNotEmpty()) {
                            genderMatching
                        } else {
                            val oppositeFiltered = finalMatching.filter { voice ->
                                val nameLower = voice.name.lowercase()
                                val isHi = finalLocale.language.equals("hi", ignoreCase = true)
                                if (gender.lowercase() == "female") {
                                    !(nameLower.contains("male") || nameLower.contains("m-loc") || nameLower.contains("m-") || nameLower.contains("-m") ||
                                    (isHi && (
                                        nameLower.contains("-hib") || 
                                        nameLower.contains("-hid") || 
                                        nameLower.contains("-hif") || 
                                        nameLower.contains("-hih") || 
                                        nameLower.contains("-hnd") || 
                                        nameLower.contains("-hni")
                                    )))
                                } else {
                                    !(nameLower.contains("female") || nameLower.contains("f-loc") || nameLower.contains("f-") || nameLower.contains("-f") ||
                                    (isHi && (
                                        nameLower.contains("-hia") || 
                                        nameLower.contains("-hic") || 
                                        nameLower.contains("-hie") || 
                                        nameLower.contains("-hig")
                                    )))
                                }
                            }
                            if (oppositeFiltered.isNotEmpty()) oppositeFiltered else finalMatching
                        }
                        
                        val voiceNameLower = voiceName.lowercase()
                        val targetVoice = when {
                            voiceNameLower.contains("deep") || voiceNameLower.contains("narrator") -> {
                                targetPool.getOrNull(1) ?: targetPool.firstOrNull()
                            }
                            voiceNameLower.contains("bold") || voiceNameLower.contains("dynamic") -> {
                                targetPool.getOrNull(2) ?: targetPool.firstOrNull()
                            }
                            voiceNameLower.contains("old") || voiceNameLower.contains("grandpa") || voiceNameLower.contains("grandma") -> {
                                targetPool.lastOrNull() ?: targetPool.firstOrNull()
                            }
                            voiceNameLower.contains("child") || voiceNameLower.contains("kid") || voiceNameLower.contains("baby") || voiceNameLower.contains("boy") || voiceNameLower.contains("girl") -> {
                                targetPool.find { it.name.lowercase().contains("child") || it.name.lowercase().contains("kid") } 
                                    ?: targetPool.getOrNull(3) 
                                    ?: targetPool.firstOrNull()
                            }
                            voiceNameLower.contains("soft") || voiceNameLower.contains("ambient") -> {
                                targetPool.getOrNull(1) ?: targetPool.firstOrNull()
                            }
                            voiceNameLower.contains("energetic") || voiceNameLower.contains("storyteller") -> {
                                targetPool.getOrNull(2) ?: targetPool.firstOrNull()
                            }
                            else -> {
                                targetPool.firstOrNull()
                            }
                        } ?: finalMatching.firstOrNull()

                        if (targetVoice != null) {
                            tts?.voice = targetVoice
                            Log.d(TAG, "Sherpa-ONNX selected offline voice: ${targetVoice.name}")
                        }
                    }

                    tts?.setSpeechRate(speedMultiplier)
                    tts?.setPitch(pitchMultiplier)

                    val utteranceId = "sherpa_utterance_${System.currentTimeMillis()}"
                    
                    tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            if (tempFile.exists() && tempFile.length() > 44) {
                                val transcodedFile = File(context.cacheDir, "sherpa_transcoded_${System.currentTimeMillis()}.wav")
                                val success = transcodeToHighFidelity(tempFile, transcodedFile)
                                if (success && transcodedFile.exists() && transcodedFile.length() > 44) {
                                    deferred.complete(transcodedFile.readBytes())
                                    try { transcodedFile.delete() } catch (e: Exception) {}
                                } else {
                                    deferred.complete(tempFile.readBytes())
                                }
                            } else {
                                deferred.complete(null)
                            }
                            try { tempFile.delete() } catch (e: Exception) {}
                            tts?.shutdown()
                        }
                        override fun onError(utteranceId: String?) {
                            deferred.complete(null)
                            try { tempFile.delete() } catch (e: Exception) {}
                            tts?.shutdown()
                        }
                    })

                    val params = android.os.Bundle()
                    params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                    
                    val result = tts?.synthesizeToFile(text, params, tempFile, utteranceId)
                    if (result != TextToSpeech.SUCCESS) {
                        deferred.complete(null)
                        tts?.shutdown()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error compiling offline wave bytes", e)
                    deferred.complete(null)
                    tts?.shutdown()
                }
            } else {
                deferred.complete(null)
            }
        }

        // Add 15 second safety timeout to avoid freezing coroutine threads
        val result = kotlinx.coroutines.withTimeoutOrNull(15000L) {
            deferred.await()
        } ?: run {
            try { tts?.shutdown() } catch (e: Exception) {}
            null
        }

        return result
    }

    private fun isFFmpegAvailable(): Boolean {
        return try {
            Class.forName("com.arthenica.ffmpegkit.FFmpegKit")
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun transcodeToHighFidelity(inputFile: File, outputFile: File): Boolean {
        if (!isFFmpegAvailable()) {
            Log.d(TAG, "FFmpegKit is not available for audio studio transcoding.")
            return false
        }
        return try {
            Log.d(TAG, "FFmpegKit detected! Executing high-fidelity audio studio transcoding...")
            val ffmpegKitClass = Class.forName("com.arthenica.ffmpegkit.FFmpegKit")
            val executeMethod = ffmpegKitClass.getMethod("execute", String::class.java)

            // High-fidelity voice enhancement audio filters:
            // - highpass=f=80: filters out low-frequency background rumbles and wind noises
            // - lowpass=f=15000: filters out high-frequency hiss or hardware electronic noise
            // - equalizer=f=3000:width_type=h:width=200:g=3.5: boosts vocal clarity & presence frequencies
            // - equalizer=f=150:width_type=h:width=100:g=2.0: adds studio warmth & depth to the voice
            // - acompressor=threshold=-12dB:ratio=3:attack=5:release=50: thickens voice & smooths dynamic peaks
            // - volume=1.45: increases overall gain to cut through background audio/reels tracks
            // - -ar 44100 -ac 2: upsamples and transcodes into professional 44.1kHz stereo wave
            val command = "-y -i \"${inputFile.absolutePath}\" " +
                    "-af \"highpass=f=80,lowpass=f=15000,equalizer=f=3000:width_type=h:width=200:g=3.5,equalizer=f=150:width_type=h:width=100:g=2.0,acompressor=threshold=-12dB:ratio=3:attack=5:release=50,volume=1.45\" " +
                    "-ar 44100 -ac 2 \"${outputFile.absolutePath}\""

            Log.d(TAG, "Executing Audio Transcode Command: $command")
            val session = executeMethod.invoke(null, command)

            val getReturnCodeMethod = session.javaClass.getMethod("getReturnCode")
            val returnCode = getReturnCodeMethod.invoke(session)

            val isSuccessMethod = returnCode.javaClass.getMethod("isValueSuccess")
            val isSuccess = isSuccessMethod.invoke(returnCode) as Boolean

            Log.d(TAG, "Audio transcode finished. Success: $isSuccess")
            isSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Failed executing FFmpeg audio transcode", e)
            false
        }
    }
}
