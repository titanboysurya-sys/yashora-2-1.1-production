package com.ritvyom.yashoraReelgenerator.data.repository

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.UUID

object EdgeTtsClient {
    private const val TAG = "EdgeTtsClient"
    private const val EDGE_URL = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1?TrustedClientToken=6A5AA1D4EAFF4E9B87E2353997568D3F"
    private const val VOICES_LIST_URL = "https://speech.platform.bing.com/consumer/speech/synthesize/readaloud/voices/list?trustedclienttoken=6A5AA1D4EAFF4E9B87E2353997568D3F"

    data class VoiceInfo(
        val name: String,
        val shortName: String,
        val gender: String,
        val locale: String
    )

    private var cachedVoices: List<VoiceInfo>? = null
    private val cacheMutex = Mutex()

    private suspend fun getVoices(): List<VoiceInfo> {
        cacheMutex.withLock {
            if (cachedVoices != null) {
                return cachedVoices!!
            }
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val request = Request.Builder()
                    .url(VOICES_LIST_URL)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            val jsonArray = JSONArray(body)
                            val list = mutableListOf<VoiceInfo>()
                            for (i in 0 until jsonArray.length()) {
                                val obj = jsonArray.getJSONObject(i)
                                val name = obj.optString("Name", "")
                                val shortName = obj.optString("ShortName", "")
                                val gender = obj.optString("Gender", "")
                                val locale = obj.optString("Locale", "")
                                if (name.isNotEmpty() && shortName.isNotEmpty()) {
                                    list.add(VoiceInfo(name, shortName, gender, locale))
                                }
                            }
                            cachedVoices = list
                            Log.d(TAG, "Successfully fetched and parsed ${list.size} Edge TTS voices online.")
                            return list
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch dynamic Edge TTS voice manifest, using offline fallback", e)
            }
            return emptyList()
        }
    }

    private suspend fun getVoiceForLocaleDynamic(language: String, gender: String, voiceName: String = ""): String {
        val langCode = getLangCode(language)
        val voices = getVoices()
        if (voices.isNotEmpty()) {
            val localeVoices = voices.filter { it.locale.equals(langCode, ignoreCase = true) }
            if (localeVoices.isNotEmpty()) {
                val voiceNameClean = voiceName.trim()
                val isFemale = gender.lowercase() == "female" || voiceNameClean.contains("Woman") || voiceNameClean.contains("Girl") || voiceNameClean.contains("Grandma") || voiceNameClean.contains("Storyteller") || voiceNameClean.contains("Sweet")
                val isChild = gender.lowercase() == "child" || voiceNameClean.contains("Kid") || voiceNameClean.contains("Child") || voiceNameClean.contains("Baby") || voiceNameClean.contains("Little")

                // Map abstract UI voice presets to standard Edge Online TTS neural voice keywords
                val mappedKeyword = when (language.lowercase().trim()) {
                    "hindi", "hinglish", "hi", "hi-in", "hi_in" -> {
                        when {
                            voiceNameClean.contains("Deep") || voiceNameClean.contains("Narrator") || voiceNameClean.contains("Grandpa") || voiceNameClean.contains("Manish") -> "ManishNeural"
                            voiceNameClean.contains("Bold") || voiceNameClean.contains("Dynamic") || voiceNameClean.contains("Aarav") -> "AaravNeural"
                            voiceNameClean.contains("Soft") || voiceNameClean.contains("Ambient") || voiceNameClean.contains("Storyteller") || voiceNameClean.contains("Kavya") -> "KavyaNeural"
                            voiceNameClean.contains("Energetic") || voiceNameClean.contains("Girl") || voiceNameClean.contains("Cute") || voiceNameClean.contains("Ananya") -> "AnanyaNeural"
                            voiceNameClean.contains("Playful") || voiceNameClean.contains("Child") -> "AaravNeural"
                            isFemale -> "SwaraNeural"
                            else -> "MadhurNeural"
                        }
                    }
                    "english" -> {
                        when {
                            voiceNameClean.contains("Deep") || voiceNameClean.contains("Narrator") || voiceNameClean.contains("Brian") -> "BrianNeural"
                            voiceNameClean.contains("Bold") || voiceNameClean.contains("Dynamic") || voiceNameClean.contains("Steffan") -> "SteffanNeural"
                            voiceNameClean.contains("Old") || voiceNameClean.contains("Grandpa") || voiceNameClean.contains("Roger") -> "RogerNeural"
                            voiceNameClean.contains("Playful") || voiceNameClean.contains("Boy") || voiceNameClean.contains("Eric") -> "EricNeural"
                            voiceNameClean.contains("Sweet") || voiceNameClean.contains("Child") || voiceNameClean.contains("Kid") || voiceNameClean.contains("Ana") -> "AnaNeural"
                            voiceNameClean.contains("Soft") || voiceNameClean.contains("Ambient") || voiceNameClean.contains("Emma") -> "EmmaNeural"
                            voiceNameClean.contains("Energetic") || voiceNameClean.contains("Michelle") -> "MichelleNeural"
                            voiceNameClean.contains("Old") || voiceNameClean.contains("Grandma") || voiceNameClean.contains("Aria") -> "AriaNeural"
                            voiceNameClean.contains("Storyteller") || voiceNameClean.contains("Ava") -> "AvaNeural"
                            isFemale -> "JennyNeural"
                            else -> "GuyNeural"
                        }
                    }
                    else -> ""
                }

                if (mappedKeyword.isNotEmpty()) {
                    val match = localeVoices.find { it.shortName.contains(mappedKeyword, ignoreCase = true) || it.name.contains(mappedKeyword, ignoreCase = true) }
                    if (match != null) {
                        Log.d(TAG, "Resolved mapped voice match online: ${match.shortName}")
                        return match.shortName
                    }
                }

                // 1. Try to find precise match by voiceName clean keyword if provided
                if (voiceNameClean.isNotEmpty()) {
                    val match = localeVoices.find { it.shortName.contains(voiceNameClean, ignoreCase = true) || it.name.contains(voiceNameClean, ignoreCase = true) }
                    if (match != null) {
                        Log.d(TAG, "Resolved precise voice match online: ${match.shortName}")
                        return match.shortName
                    }
                }

                // 2. Try matching by subcategory tags (child vs adult, then gender)
                val matchedVoice = when {
                    isChild -> {
                        localeVoices.find { it.shortName.contains("child", ignoreCase = true) || it.shortName.contains("kid", ignoreCase = true) || it.shortName.contains("baby", ignoreCase = true) || it.name.contains("child", ignoreCase = true) }
                    }
                    isFemale -> {
                        localeVoices.find { it.gender.equals("female", ignoreCase = true) }
                    }
                    else -> {
                        localeVoices.find { it.gender.equals("male", ignoreCase = true) }
                    }
                }
                if (matchedVoice != null) {
                    Log.d(TAG, "Resolved gender/child voice match online: ${matchedVoice.shortName}")
                    return matchedVoice.shortName
                }

                // 3. Last resort fallback in the resolved locale list
                Log.d(TAG, "Fallback voice selected from locale list: ${localeVoices.first().shortName}")
                return localeVoices.first().shortName
            }
        }

        // Offline fallback
        return getVoiceForLocaleOffline(language, gender, voiceName)
    }

    suspend fun synthesize(
        text: String,
        language: String,
        gender: String,
        voiceName: String = "",
        speedMultiplier: Float = 1.0f,
        pitchMultiplier: Float = 1.0f
    ): ByteArray? {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(EDGE_URL)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0")
            .addHeader("Origin", "chrome-extension://jdiccldimpdaibofeedgoolghaocofne")
            .build()

        val deferred = CompletableDeferred<ByteArray?>()
        val audioStream = ByteArrayOutputStream()

        val voiceToUse = getVoiceForLocaleDynamic(language, gender, voiceName)
        val langCode = getLangCode(language)

        val wsListener = object : WebSocketListener() {
            var isFinished = false

            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connection opened")
                
                // Generate secure correlation UUID request token
                val requestId = UUID.randomUUID().toString().replace("-", "")
                val timestamp = System.currentTimeMillis().toString()

                // Step 1: Send configuration frame
                val configMsg = "X-Timestamp:$timestamp\r\n" +
                        "Content-Type:application/json; charset=utf-8\r\n" +
                        "Path:speech.config\r\n\r\n" +
                        "{\"context\":{\"system\":{\"name\":\"SpeechSDK\",\"version\":\"1.30.0\",\"build\":\"JavaScript\",\"lang\":\"JavaScript\"},\"os\":{\"platform\":\"Windows\",\"name\":\"Chromium\",\"version\":\"120.0.0.0\"}}}"

                webSocket.send(configMsg)

                // Step 2: Format Voice parameters and speed/pitch percentage rates
                // Convert float multiplier to percentage format like "+10%", "-15%"
                val rateVal = ((speedMultiplier - 1.0f) * 100).toInt()
                val rateStr = if (rateVal >= 0) "+$rateVal%" else "$rateVal%"

                val pitchVal = ((pitchMultiplier - 1.0f) * 100).toInt()
                val pitchStr = if (pitchVal >= 0) "+$pitchVal%" else "$pitchVal%"

                // Construct raw Microsoft enterprise SSML markup payload
                val ssmlMsg = "X-RequestId:$requestId\r\n" +
                        "X-Timestamp:$timestamp\r\n" +
                        "Content-Type:application/ssml+xml\r\n" +
                        "Path:ssml\r\n\r\n" +
                        "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='$langCode'>" +
                        "<voice name='$voiceToUse'>" +
                        "<prosody rate='$rateStr' pitch='$pitchStr'>${escapeXml(text)}</prosody>" +
                        "</voice>" +
                        "</speak>"

                webSocket.send(ssmlMsg)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Received Text Frame: $text")
                if (text.contains("turn.end")) {
                    isFinished = true
                    webSocket.close(1000, "Finished successfully")
                    deferred.complete(audioStream.toByteArray())
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                try {
                    val buffer = bytes.asByteBuffer()
                    if (buffer.remaining() < 2) return

                    // Parse binary metadata frame with 2-byte header
                    val headerLength = buffer.short.toInt() and 0xFFFF
                    if (buffer.remaining() < headerLength) return

                    val headerBytes = ByteArray(headerLength)
                    buffer.get(headerBytes)
                    val headers = String(headerBytes, Charsets.UTF_8)

                    // If it contains the binary audio path, extract remaining bytes
                    if (headers.contains("Path:audio")) {
                        val audioBytes = ByteArray(buffer.remaining())
                        buffer.get(audioBytes)
                        audioStream.write(audioBytes)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed parsing binary audio frame payload", e)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket is closing: $code / $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code")
                if (!deferred.isCompleted) {
                    deferred.complete(audioStream.toByteArray())
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure", t)
                webSocket.close(1001, "Error occurred")
                if (!deferred.isCompleted) {
                    deferred.complete(null)
                }
            }
        }

        val webSocket = client.newWebSocket(request, wsListener)

        // Wait up to 30 seconds for complete speech compilation
        val result = withTimeoutOrNull(30000L) {
            deferred.await()
        }

        // Close the websocket cleanly if it wasn't closed
        try {
            webSocket.close(1000, "Timeout or cancelled")
        } catch (e: Exception) {}

        return result
    }

    private fun getVoiceForLocaleOffline(language: String, gender: String, voiceName: String = ""): String {
        val voiceNameClean = voiceName.trim()
        val isFemale = gender.lowercase() == "female" || voiceNameClean.contains("Woman") || voiceNameClean.contains("Girl") || voiceNameClean.contains("Grandma") || voiceNameClean.contains("Storyteller") || voiceNameClean.contains("Sweet")
        val isChild = gender.lowercase() == "child" || voiceNameClean.contains("Kid") || voiceNameClean.contains("Child") || voiceNameClean.contains("Baby") || voiceNameClean.contains("Little")

        return when (language.lowercase().trim()) {
            "hindi", "hinglish", "hi", "hi-in", "hi_in" -> {
                when {
                    isChild -> {
                        when {
                            voiceNameClean.contains("Cute") || voiceNameClean.contains("Girl") -> "hi-IN-AnanyaNeural"
                            voiceNameClean.contains("Playful") || voiceNameClean.contains("Child") -> "hi-IN-AaravNeural"
                            else -> "hi-IN-MadhurNeural"
                        }
                    }
                    isFemale -> {
                        when {
                            voiceNameClean.contains("Soft") || voiceNameClean.contains("Ambient") -> "hi-IN-KavyaNeural"
                            voiceNameClean.contains("Energetic") || voiceNameClean.contains("Girl") -> "hi-IN-AnanyaNeural"
                            voiceNameClean.contains("Storyteller") -> "hi-IN-KavyaNeural"
                            else -> "hi-IN-SwaraNeural"
                        }
                    }
                    else -> {
                        // Male
                        when {
                            voiceNameClean.contains("Deep") || voiceNameClean.contains("Narrator") -> "hi-IN-ManishNeural"
                            voiceNameClean.contains("Bold") || voiceNameClean.contains("Dynamic") -> "hi-IN-AaravNeural"
                            voiceNameClean.contains("Old") || voiceNameClean.contains("Grandpa") -> "hi-IN-ManishNeural"
                            else -> "hi-IN-MadhurNeural"
                        }
                    }
                }
            }
            "english" -> {
                when {
                    isChild -> {
                        when {
                            voiceNameClean.contains("Playful") -> "en-US-EricNeural"
                            voiceNameClean.contains("Sweet") -> "en-US-AnaNeural"
                            else -> "en-US-ChristopherNeural"
                        }
                    }
                    isFemale -> {
                        when {
                            voiceNameClean.contains("Soft") || voiceNameClean.contains("Ambient") -> "en-US-EmmaNeural"
                            voiceNameClean.contains("Energetic") -> "en-US-MichelleNeural"
                            voiceNameClean.contains("Old") || voiceNameClean.contains("Grandma") -> "en-US-AriaNeural"
                            voiceNameClean.contains("Storyteller") -> "en-US-AvaNeural"
                            else -> "en-US-JennyNeural"
                        }
                    }
                    else -> {
                        // Male
                        when {
                            voiceNameClean.contains("Deep") || voiceNameClean.contains("Narrator") -> "en-US-BrianNeural"
                            voiceNameClean.contains("Bold") || voiceNameClean.contains("Dynamic") -> "en-US-SteffanNeural"
                            voiceNameClean.contains("Old") || voiceNameClean.contains("Grandpa") -> "en-US-RogerNeural"
                            else -> "en-US-GuyNeural"
                        }
                    }
                }
            }
            "spanish" -> if (isFemale) "es-ES-ElviraNeural" else "es-ES-AlvaroNeural"
            "japanese" -> if (isFemale) "ja-JP-NanamiNeural" else "ja-JP-KeitaNeural"
            "chinese", "chinese (simplified)" -> if (isFemale) "zh-CN-XiaoxiaoNeural" else "zh-CN-YunxiNeural"
            "chinese (traditional)" -> if (isFemale) "zh-TW-HsiaoChenNeural" else "zh-TW-YunJheNeural"
            "korean" -> if (isFemale) "ko-KR-SunHiNeural" else "ko-KR-InJoonNeural"
            "german" -> if (isFemale) "de-DE-AmalaNeural" else "de-DE-ConradNeural"
            "french" -> if (isFemale) "fr-FR-DeniseNeural" else "fr-FR-HenriNeural"
            "arabic" -> if (isFemale) "ar-SA-ZariyahNeural" else "ar-SA-HamedNeural"
            "urdu" -> if (isFemale) "ur-PK-UzmaNeural" else "ur-PK-AsadNeural"
            "bengali" -> if (isFemale) "bn-IN-TanishaNeural" else "bn-IN-BashkarNeural"
            "marathi" -> if (isFemale) "mr-IN-AarohiNeural" else "mr-IN-ManoharNeural"
            "telugu" -> if (isFemale) "te-IN-ShrutiNeural" else "te-IN-MohanNeural"
            "tamil" -> if (isFemale) "ta-IN-PallaviNeural" else "ta-IN-ValluvarNeural"
            "gujarati" -> if (isFemale) "gu-IN-DhwaniNeural" else "gu-IN-NiranjanNeural"
            "kannada" -> if (isFemale) "kn-IN-SapnaNeural" else "kn-IN-GaganNeural"
            "odia" -> if (isFemale) "or-IN-SubhasiniNeural" else "or-IN-SukantNeural"
            "malayalam" -> if (isFemale) "ml-IN-SobhanaNeural" else "ml-IN-MidhunNeural"
            "punjabi" -> if (isFemale) "pa-IN-OjasNeural" else "pa-IN-OjasNeural"
            "assamese" -> if (isFemale) "as-IN-YashicaNeural" else "as-IN-YashicaNeural"
            "italian" -> if (isFemale) "it-IT-ElsaNeural" else "it-IT-DiegoNeural"
            "russian" -> if (isFemale) "ru-RU-SvetlanaNeural" else "ru-RU-DmitryNeural"
            "portuguese" -> if (isFemale) "pt-BR-FranciscaNeural" else "pt-BR-AntonioNeural"
            "turkish" -> if (isFemale) "tr-TR-EmelNeural" else "tr-TR-AhmetNeural"
            "dutch" -> if (isFemale) "nl-NL-ColetteNeural" else "nl-NL-MaartenNeural"
            "polish" -> if (isFemale) "pl-PL-AgnieszkaNeural" else "pl-PL-MarekNeural"
            "indonesian" -> if (isFemale) "id-ID-GadisNeural" else "id-ID-ArdiNeural"
            "vietnamese" -> if (isFemale) "vi-VN-HoaiMyNeural" else "vi-VN-NamMinhNeural"
            "thai" -> if (isFemale) "th-TH-AcharaNeural" else "th-TH-NiwatNeural"
            "filipino" -> if (isFemale) "fil-PH-BlessicaNeural" else "fil-PH-AngeloNeural"
            "malay" -> if (isFemale) "ms-MY-YasminNeural" else "ms-MY-OsmanNeural"
            "persian" -> if (isFemale) "fa-IR-DilaraNeural" else "fa-IR-FaridNeural"
            "hebrew" -> if (isFemale) "he-IL-HilaNeural" else "he-IL-AvriNeural"
            "swedish" -> if (isFemale) "sv-SE-SofieNeural" else "sv-SE-MattiasNeural"
            "norwegian" -> if (isFemale) "nb-NO-PernilleNeural" else "nb-NO-FinnNeural"
            "finnish" -> if (isFemale) "fi-FI-SelmaNeural" else "fi-FI-HarriNeural"
            "greek" -> if (isFemale) "el-GR-AthinaNeural" else "el-GR-NestorasNeural"
            "romanian" -> if (isFemale) "ro-RO-AlinaNeural" else "ro-RO-EmilNeural"
            "hungarian" -> if (isFemale) "hu-HU-NoemiNeural" else "hu-HU-TamasNeural"
            "czech" -> if (isFemale) "cs-CZ-VlastaNeural" else "cs-CZ-AntoninNeural"
            "ukrainian" -> if (isFemale) "uk-UA-PolinaNeural" else "uk-UA-OstapNeural"
            "nepali" -> if (isFemale) "ne-NP-HemkalaNeural" else "ne-NP-SagarNeural"
            "sinhala" -> if (isFemale) "si-LK-ThiliniNeural" else "si-LK-SameeraNeural"
            "swahili" -> if (isFemale) "sw-KE-ZuriNeural" else "sw-KE-RafikiNeural"
            "hausa" -> if (isFemale) "ha-NG-AminaNeural" else "ha-NG-MahmudNeural"
            "amharic" -> if (isFemale) "am-ET-MekdesNeural" else "am-ET-AmehaNeural"
            else -> if (isFemale) "en-US-JennyNeural" else "en-US-GuyNeural"
        }
    }

    private fun getLangCode(language: String): String {
        return when (language.lowercase().trim()) {
            "hindi", "hinglish", "hi", "hi-in", "hi_in" -> "hi-IN"
            "spanish" -> "es-ES"
            "japanese" -> "ja-JP"
            "chinese", "chinese (simplified)" -> "zh-CN"
            "chinese (traditional)" -> "zh-TW"
            "korean" -> "ko-KR"
            "german" -> "de-DE"
            "french" -> "fr-FR"
            "arabic" -> "ar-SA"
            "urdu" -> "ur-PK"
            "bengali" -> "bn-IN"
            "marathi" -> "mr-IN"
            "tamil" -> "ta-IN"
            "telugu" -> "te-IN"
            "gujarati" -> "gu-IN"
            "kannada" -> "kn-IN"
            "odia" -> "or-IN"
            "malayalam" -> "ml-IN"
            "punjabi" -> "pa-IN"
            "assamese" -> "as-IN"
            "italian" -> "it-IT"
            "russian" -> "ru-RU"
            "portuguese" -> "pt-BR"
            "turkish" -> "tr-TR"
            "dutch" -> "nl-NL"
            "polish" -> "pl-PL"
            "indonesian" -> "id-ID"
            "vietnamese" -> "vi-VN"
            "thai" -> "th-TH"
            "filipino" -> "fil-PH"
            "malay" -> "ms-MY"
            "persian" -> "fa-IR"
            "hebrew" -> "he-IL"
            "swedish" -> "sv-SE"
            "norwegian" -> "nb-NO"
            "finnish" -> "fi-FI"
            "greek" -> "el-GR"
            "romanian" -> "ro-RO"
            "hungarian" -> "hu-HU"
            "czech" -> "cs-CZ"
            "ukrainian" -> "uk-UA"
            "nepali" -> "ne-NP"
            "sinhala" -> "si-LK"
            "swahili" -> "sw-KE"
            "hausa" -> "ha-NG"
            "amharic" -> "am-ET"
            else -> "en-US"
        }
    }

    private fun escapeXml(s: String): String {
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
