package com.ritvyom.yashoraReelgenerator.data.voxeleven

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

object ElevenLabsClient {
    private const val TAG = "ElevenLabsClient"
    private const val BASE_URL = "https://api.elevenlabs.io/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.NONE
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val apiService: ElevenLabsApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(ElevenLabsApi::class.java)
    }

    val defaultVoices = listOf(
        Voice(voiceId = "21m00Tcm4TlvDq8ikWAM", name = "Rachel", category = "premade", description = "Calm & Soft, Young Narration", labels = mapOf("gender" to "female", "accent" to "american", "age" to "young", "use_case" to "narration"), previewUrl = "https://storage.googleapis.com/eleven-public-prod/premade/voices/21m00Tcm4TlvDq8ikWAM/6b03eb2a-d242-4f05-894c-8abebecb8720.mp3"),
        Voice(voiceId = "pNInz6obpg7j8YtMUIa9", name = "Adam", category = "premade", description = "Deep & Authoritative Narration", labels = mapOf("gender" to "male", "accent" to "american", "age" to "middle aged", "use_case" to "narration"), previewUrl = "https://storage.googleapis.com/eleven-public-prod/premade/voices/pNInz6obpg7j8YtMUIa9/30aa70fa-db02-4015-846f-c1f93f958a0e.mp3"),
        Voice(voiceId = "ErXwobaYiN019PkySvjV", name = "Antoni", category = "premade", description = "Well-rounded & Conversational", labels = mapOf("gender" to "male", "accent" to "american", "age" to "young", "use_case" to "narration"), previewUrl = "https://storage.googleapis.com/eleven-public-prod/premade/voices/ErXwobaYiN019PkySvjV/38d2139b-73a0-4a87-b93e-2b15801efcda.mp3"),
        Voice(voiceId = "VR6AewLTigWG4xSOukaG", name = "Arnold", category = "premade", description = "Crisp & Authoritative News", labels = mapOf("gender" to "male", "accent" to "american", "age" to "middle aged", "use_case" to "news"), previewUrl = "https://storage.googleapis.com/eleven-public-prod/premade/voices/VR6AewLTigWG4xSOukaG/7d4e5f22-2630-4e3a-96e0-e14b2d4999f8.mp3"),
        Voice(voiceId = "EXAVITQu4vr4xnSDOCMa", name = "Bella", category = "premade", description = "Soft & Expressive Narrative", labels = mapOf("gender" to "female", "accent" to "american", "age" to "young", "use_case" to "audiobook"), previewUrl = "https://storage.googleapis.com/eleven-public-prod/premade/voices/EXAVITQu4vr4xnSDOCMa/a50b44b9-8e40-4ef1-80a2-2d1b7024847e.mp3"),
        Voice(voiceId = "N2lVS1w92zCOyYr99Gg4", name = "Callum", category = "premade", description = "Intense & Video Game Character", labels = mapOf("gender" to "male", "accent" to "transatlantic", "age" to "middle aged", "use_case" to "characters"), previewUrl = "https://storage.googleapis.com/eleven-public-prod/premade/voices/N2lVS1w92zCOyYr99Gg4/21d51a02-5c02-4f8e-a22c-a2b160b73059.mp3"),
        Voice(voiceId = "IKne3meq5aSn9XLyUdCD", name = "Charlie", category = "premade", description = "Casual & Conversational", labels = mapOf("gender" to "male", "accent" to "australian", "age" to "middle aged", "use_case" to "conversational"), previewUrl = "https://storage.googleapis.com/eleven-public-prod/premade/voices/IKne3meq5aSn9XLyUdCD/102de6f2-22ed-410e-a17d-94c0a5369a47.mp3"),
        Voice(voiceId = "XB0fDUnXU5powomDhCwa", name = "Charlotte", category = "premade", description = "Seductive & Expressive", labels = mapOf("gender" to "female", "accent" to "english", "age" to "young", "use_case" to "characters"), previewUrl = "https://storage.googleapis.com/eleven-public-prod/premade/voices/XB0fDUnXU5powomDhCwa/94ed221c-9392-4632-8418-2d8ebcf449d0.mp3"),
        Voice(voiceId = "onwK4e9ZLuTAKqWW03F9", name = "Daniel", category = "premade", description = "Deep British News Presenter", labels = mapOf("gender" to "male", "accent" to "british", "age" to "middle aged", "use_case" to "news"), previewUrl = "https://storage.googleapis.com/eleven-public-prod/premade/voices/onwK4e9ZLuTAKqWW03F9/28169e06-030a-4293-86f3-33bc8834914a.mp3"),
        Voice(voiceId = "AZnzlk1XvdvUeBnXmlld", name = "Domi", category = "premade", description = "Strong & Confident Narration", labels = mapOf("gender" to "female", "accent" to "american", "age" to "young", "use_case" to "narration"), previewUrl = "https://storage.googleapis.com/eleven-public-prod/premade/voices/AZnzlk1XvdvUeBnXmlld/503e7c8d-327f-4c12-843c-6232d3d9eddd.mp3"),
        Voice(voiceId = "ThT5KcBeYPX3keUQqHPh", name = "Dorothy", category = "premade", description = "Pleasant Children's Stories", labels = mapOf("gender" to "female", "accent" to "british", "age" to "young", "use_case" to "children"), previewUrl = "https://storage.googleapis.com/eleven-public-prod/premade/voices/ThT5KcBeYPX3keUQqHPh/9e3a6a90-3945-48b2-a4f6-8c4d1685e8d2.mp3"),
        Voice(voiceId = "MF3mGyEYCl7XYWbV9V6O", name = "Elli", category = "premade", description = "Emotional & Young Voice", labels = mapOf("gender" to "female", "accent" to "american", "age" to "young", "use_case" to "narration"), previewUrl = "https://storage.googleapis.com/eleven-public-prod/premade/voices/MF3mGyEYCl7XYWbV9V6O/d9b05a20-410a-41d3-a4c3-37ebfe6f5e32.mp3"),
        Voice(voiceId = "LcfcDJNUP1GQjkzn1xUU", name = "Emily", category = "premade", description = "Calm & Meditative Tone", labels = mapOf("gender" to "female", "accent" to "american", "age" to "young", "use_case" to "meditation"), previewUrl = "https://storage.googleapis.com/eleven-public-prod/premade/voices/LcfcDJNUP1GQjkzn1xUU/c03565e3-47a3-4889-a292-6f2963d3e8e2.mp3"),
        Voice(voiceId = "g5CIjZEefAph4nQFvHAz", name = "Ethan", category = "premade", description = "Soft Whispering ASMR Voice", labels = mapOf("gender" to "male", "accent" to "american", "age" to "young", "use_case" to "asmr"), previewUrl = "https://storage.googleapis.com/eleven-public-prod/premade/voices/g5CIjZEefAph4nQFvHAz/8271e54c-83b6-45ef-8f19-d98c2faef31c.mp3"),
        Voice(voiceId = "cgSgspJ2msm6clMC924e", name = "Glinda", category = "premade", description = "Warm Accent & Expressive", labels = mapOf("gender" to "female", "accent" to "american", "age" to "middle aged", "use_case" to "narration"), previewUrl = "https://storage.googleapis.com/eleven-public-prod/premade/voices/cgSgspJ2msm6clMC924e/2e90e66c-5f4b-4876-b9ed-8a211e4f45bd.mp3"),
        Voice(voiceId = "SOYuuA21A43A2kIq169L", name = "Harry", category = "premade", description = "Anxious & Dramatic Character", labels = mapOf("gender" to "male", "accent" to "american", "age" to "young", "use_case" to "characters"), previewUrl = "https://storage.googleapis.com/eleven-public-prod/premade/voices/SOYuuA21A43A2kIq169L/8c69d803-176c-482a-a9e9-7c8a666e1d3e.mp3"),
        Voice(voiceId = "ZQe5CZAouabDqUTRelid", name = "James", category = "premade", description = "Calm Australian News Voice", labels = mapOf("gender" to "male", "accent" to "australian", "age" to "old", "use_case" to "news"), previewUrl = "https://storage.googleapis.com/eleven-public-prod/premade/voices/ZQe5CZAouabDqUTRelid/f3e39c4a-89a3-4b68-b7c1-1e90d20d7718.mp3"),
        Voice(voiceId = "bV1W4M314DZnB21G0fD3", name = "Jeremy", category = "premade", description = "Excited & Energetic Voice", labels = mapOf("gender" to "male", "accent" to "american-irish", "age" to "young", "use_case" to "conversational"), previewUrl = "https://storage.googleapis.com/eleven-public-prod/premade/voices/bV1W4M314DZnB21G0fD3/e5b4b1a2-5813-41bb-a801-b2c4e09e1e9a.mp3"),
        Voice(voiceId = "Zlb1dXrM653N07WRd21s", name = "Joseph", category = "premade", description = "Formal British News", labels = mapOf("gender" to "male", "accent" to "british", "age" to "middle aged", "use_case" to "news"), previewUrl = "https://storage.googleapis.com/eleven-public-prod/premade/voices/Zlb1dXrM653N07WRd21s/9a589f2a-742f-48e0-a9d2-315efb51e505.mp3"),
        Voice(voiceId = "TxGEqnHWrfWFTfGW9XjX", name = "Josh", category = "premade", description = "Deep & Young American Voice", labels = mapOf("gender" to "male", "accent" to "american", "age" to "young", "use_case" to "narration"), previewUrl = "https://storage.googleapis.com/eleven-public-prod/premade/voices/TxGEqnHWrfWFTfGW9XjX/3a2b7f7e-d009-4560-b6c2-0746e01a8ef1.mp3"),
        Voice(voiceId = "TX3LPaxmHKxFdv7VOQHJ", name = "Liam", category = "premade", description = "Young & Friendly Tone", labels = mapOf("gender" to "male", "accent" to "american", "age" to "young", "use_case" to "conversational"), previewUrl = "https://storage.googleapis.com/eleven-public-prod/premade/voices/TX3LPaxmHKxFdv7VOQHJ/21d27993-4903-45f8-a15d-83ec0f8a851e.mp3"),
        Voice(voiceId = "XrExE9yKIg1WjnnlVkGX", name = "Matilda", category = "premade", description = "Warm & Natural Narration", labels = mapOf("gender" to "female", "accent" to "american", "age" to "middle aged", "use_case" to "audiobook"), previewUrl = "https://storage.googleapis.com/eleven-public-prod/premade/voices/XrExE9yKIg1WjnnlVkGX/102c7760-49e0-47b7-95f0-6c9a2d3345f1.mp3"),
        Voice(voiceId = "flq6f7yk4E4fJM5XTY5a", name = "Michael", category = "premade", description = "Old Wise Audiobook Narrator", labels = mapOf("gender" to "male", "accent" to "american", "age" to "old", "use_case" to "audiobook"), previewUrl = "https://storage.googleapis.com/eleven-public-prod/premade/voices/flq6f7yk4E4fJM5XTY5a/091a13e5-8292-4919-b695-a22676f2df3e.mp3"),
        Voice(voiceId = "piTKgcLEGmPE4e6mEKli", name = "Nicole", category = "premade", description = "Soft Whispering Voice", labels = mapOf("gender" to "female", "accent" to "american", "age" to "young", "use_case" to "audiobook"), previewUrl = "https://storage.googleapis.com/eleven-public-prod/premade/voices/piTKgcLEGmPE4e6mEKli/4255d614-7f15-46f3-a3a8-422204e33d2c.mp3"),
        Voice(voiceId = "5Q0t7uMcjG2i2xY1pX2V", name = "Paul", category = "premade", description = "Grounded News Anchor", labels = mapOf("gender" to "male", "accent" to "american", "age" to "middle aged", "use_case" to "news"), previewUrl = "https://storage.googleapis.com/eleven-public-prod/premade/voices/5Q0t7uMcjG2i2xY1pX2V/75f3a09a-4f47-49e5-b1a1-125d7c81d83f.mp3"),
        Voice(voiceId = "yoZ06a6428U0A8O252k0", name = "Sam", category = "premade", description = "Raspy & Young Dynamic Voice", labels = mapOf("gender" to "male", "accent" to "american", "age" to "young", "use_case" to "narration"), previewUrl = "https://storage.googleapis.com/eleven-public-prod/premade/voices/yoZ06a6428U0A8O252k0/e2e5c8e3-9828-42f0-936e-57df1258d92a.mp3"),
        Voice(voiceId = "pMsC212v2381e3y1f4e1", name = "Serena", category = "premade", description = "Pleasant Conversational", labels = mapOf("gender" to "female", "accent" to "american", "age" to "middle aged", "use_case" to "conversational"), previewUrl = "https://storage.googleapis.com/eleven-public-prod/premade/voices/pMsC212v2381e3y1f4e1/02129e9d-128a-4d22-834c-1f81d112b23a.mp3"),
        Voice(voiceId = "GBv7mTt0atIp3Br8iCJU", name = "Thomas", category = "premade", description = "Calm Narration", labels = mapOf("gender" to "male", "accent" to "american", "age" to "young", "use_case" to "narration"), previewUrl = "https://storage.googleapis.com/eleven-public-prod/premade/voices/GBv7mTt0atIp3Br8iCJU/61d102e3-27c1-41d6-a24e-4fdfa8241d33.mp3")
    )

    suspend fun rawGetUserSubscription(apiKey: String): Subscription = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        try {
            apiService.getUserSubscription(cleanKey)
        } catch (e: Exception) {
            val userResp = apiService.getUserInfo(cleanKey)
            userResp.subscription ?: Subscription()
        }
    }

    suspend fun getUserSubscription(apiKey: String): Subscription {
        return com.ritvyom.yashoraReelgenerator.data.remote.UnifiedApiClient.getUserSubscription(apiKey)
    }

    suspend fun validateApiKey(apiKey: String): UserResponse = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        val sub = getUserSubscription(cleanKey)
        UserResponse(subscription = sub)
    }

    suspend fun rawFetchVoices(apiKey: String): List<Voice> = withContext(Dispatchers.IO) {
        val response = apiService.getVoices(apiKey.trim())
        response.voices
    }

    suspend fun fetchVoices(apiKey: String): List<Voice> {
        return com.ritvyom.yashoraReelgenerator.data.remote.UnifiedApiClient.fetchVoices(apiKey)
    }

    /**
     * Compute a deterministic MD5 hash for caching based on voice ID, model, audio parameters, and script text.
     */
    fun computeCacheKey(
        voiceId: String,
        text: String,
        modelId: String = "eleven_multilingual_v2",
        stability: Double = 0.5,
        similarityBoost: Double = 0.75,
        style: Double = 0.0,
        useSpeakerBoost: Boolean = true
    ): String {
        val normalizedText = text.trim()
        val input = "${voiceId.trim()}_${modelId.trim()}_${"%.2f".format(Locale.US, stability)}_${"%.2f".format(Locale.US, similarityBoost)}_${"%.2f".format(Locale.US, style)}_${useSpeakerBoost}_$normalizedText"
        return try {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(input.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            input.hashCode().toString().replace("-", "n")
        }
    }

    /**
     * Retrieves the persistent cached audio file for the requested voice and text if it already exists.
     * Checks both the Room database index and local disk storage.
     * Returns null if not cached yet.
     */
    fun getCachedAudioFile(
        context: Context,
        voiceId: String,
        text: String,
        modelId: String = "eleven_multilingual_v2",
        stability: Double = 0.5,
        similarityBoost: Double = 0.75,
        style: Double = 0.0,
        useSpeakerBoost: Boolean = true
    ): File? {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return null
        val cacheDir = File(context.filesDir, "elevenlabs_persistent_cache")
        val key = computeCacheKey(voiceId, cleanText, modelId, stability, similarityBoost, style, useSpeakerBoost)
        val file = File(cacheDir, "vox_$key.mp3")
        if (file.exists() && file.length() > 200) {
            return file
        }

        // Check Room AudioCache database record
        try {
            val audioCacheDao = com.ritvyom.yashoraReelgenerator.data.local.AppDatabase.getDatabase(context).audioCacheDao()
            val entry = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                audioCacheDao.getAudioCache(voiceId.trim(), key) ?: audioCacheDao.getAudioCacheByHash(key)
            }
            if (entry != null && entry.fileUri.isNotBlank()) {
                val dbFile = File(entry.fileUri)
                if (dbFile.exists() && dbFile.length() > 200) {
                    return dbFile
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "AudioCache Room database query skipped/failed", e)
        }

        return null
    }

    /**
     * Checks if this audio was already generated and saved to persistent disk cache.
     */
    fun isAudioCached(
        context: Context,
        voiceId: String,
        text: String,
        modelId: String = "eleven_multilingual_v2",
        stability: Double = 0.5,
        similarityBoost: Double = 0.75,
        style: Double = 0.0,
        useSpeakerBoost: Boolean = true
    ): Boolean {
        return getCachedAudioFile(context, voiceId, text, modelId, stability, similarityBoost, style, useSpeakerBoost) != null
    }

    /**
     * Generates speech for text using ElevenLabs API with persistent disk-caching.
     * If the audio was already synthesized previously, it is immediately returned from disk
     * without making any network call or consuming any ElevenLabs API tokens.
     */
    suspend fun rawGenerateSpeech(
        context: Context,
        apiKey: String,
        voiceId: String,
        text: String,
        modelId: String = "eleven_multilingual_v2",
        stability: Double = 0.5,
        similarityBoost: Double = 0.75,
        style: Double = 0.0,
        useSpeakerBoost: Boolean = true,
        targetFile: File? = null
    ): File = withContext(Dispatchers.IO) {
        val cleanText = text.trim()
        val cacheDir = File(context.filesDir, "elevenlabs_persistent_cache").apply {
            if (!exists()) mkdirs()
        }
        val cacheKey = computeCacheKey(voiceId, cleanText, modelId, stability, similarityBoost, style, useSpeakerBoost)
        val permanentCacheFile = File(cacheDir, "vox_$cacheKey.mp3")

        // 1. Check if audio for this exact text + voice settings already exists on local disk
        if (permanentCacheFile.exists() && permanentCacheFile.length() > 200) {
            Log.d(
                TAG,
                "Cache HIT: Reusing cached ElevenLabs audio for [${cleanText.take(30)}...] (${permanentCacheFile.length()} bytes). 0 API tokens spent."
            )
            if (targetFile != null && targetFile.absolutePath != permanentCacheFile.absolutePath) {
                try {
                    val parent = targetFile.parentFile
                    if (parent != null && !parent.exists()) parent.mkdirs()
                    permanentCacheFile.copyTo(targetFile, overwrite = true)
                    return@withContext targetFile
                } catch (e: Exception) {
                    Log.w(TAG, "Failed copying cached file to targetFile, using permanent cache file directly", e)
                }
            }
            return@withContext permanentCacheFile
        }

        // 2. Cache MISS: Generate speech via ElevenLabs API
        Log.d(
            TAG,
            "Cache MISS: Synthesizing voice via ElevenLabs API for voiceId=$voiceId, text length=${cleanText.length} chars (stability=$stability, similarity=$similarityBoost, style=$style, boost=$useSpeakerBoost)..."
        )
        val request = TtsRequest(
            text = cleanText,
            modelId = modelId,
            voiceSettings = VoiceSettings(
                stability = stability,
                similarityBoost = similarityBoost,
                style = style,
                useSpeakerBoost = useSpeakerBoost
            )
        )

        val responseBody = apiService.textToSpeech(
            voiceId = voiceId,
            apiKey = apiKey.trim(),
            request = request
        )

        val tempFile = File(cacheDir, "temp_${System.currentTimeMillis()}_$cacheKey.tmp")
        responseBody.byteStream().use { inputStream ->
            FileOutputStream(tempFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }

        if (tempFile.exists() && tempFile.length() > 0) {
            val renamed = tempFile.renameTo(permanentCacheFile)
            if (!renamed) {
                tempFile.copyTo(permanentCacheFile, overwrite = true)
                tempFile.delete()
            }
        }

        if (permanentCacheFile.exists() && permanentCacheFile.length() > 0) {
            try {
                val db = com.ritvyom.yashoraReelgenerator.data.local.AppDatabase.getDatabase(context)
                val audioCacheDao = db.audioCacheDao()
                val entry = com.ritvyom.yashoraReelgenerator.data.local.entities.AudioCache(
                    voiceId = voiceId.trim(),
                    textHash = cacheKey,
                    fileUri = permanentCacheFile.absolutePath,
                    textPrompt = text,
                    localFilePath = permanentCacheFile.absolutePath,
                    timestamp = System.currentTimeMillis()
                )
                audioCacheDao.insertAudioCache(entry)

                // Store in GeneratedAudio metadata table
                val generatedAudioDao = db.generatedAudioDao()
                val audioMetadata = com.ritvyom.yashoraReelgenerator.data.local.entities.GeneratedAudioEntity(
                    textPrompt = text,
                    selectedVoiceId = voiceId.trim(),
                    localFilePath = permanentCacheFile.absolutePath,
                    fileSizeBytes = permanentCacheFile.length(),
                    audioFormat = "audio/mpeg",
                    provider = "VoxEleven",
                    timestamp = System.currentTimeMillis()
                )
                generatedAudioDao.insertAudio(audioMetadata)
                Log.d(TAG, "Saved GeneratedAudio metadata and AudioCache into Room DB for voiceId=$voiceId")
            } catch (e: Exception) {
                Log.w(TAG, "Failed inserting GeneratedAudio/AudioCache into Room database", e)
            }
        }

        val resultFile = if (targetFile != null && targetFile.absolutePath != permanentCacheFile.absolutePath) {
            val parent = targetFile.parentFile
            if (parent != null && !parent.exists()) parent.mkdirs()
            permanentCacheFile.copyTo(targetFile, overwrite = true)
            targetFile
        } else {
            permanentCacheFile
        }

        Log.d(
            TAG,
            "Successfully cached ElevenLabs audio to disk (${resultFile.length()} bytes). Future playbacks will cost 0 API tokens."
        )
        resultFile
    }

    suspend fun generateSpeech(
        context: Context,
        apiKey: String,
        voiceId: String,
        text: String,
        modelId: String = "eleven_multilingual_v2",
        stability: Double = 0.5,
        similarityBoost: Double = 0.75,
        style: Double = 0.0,
        useSpeakerBoost: Boolean = true,
        targetFile: File? = null
    ): File {
        return com.ritvyom.yashoraReelgenerator.data.remote.UnifiedApiClient.generateSpeech(
            context = context,
            apiKey = apiKey,
            voiceId = voiceId,
            text = text,
            modelId = modelId,
            stability = stability,
            similarityBoost = similarityBoost,
            style = style,
            useSpeakerBoost = useSpeakerBoost,
            targetFile = targetFile
        )
    }

    fun getCacheCount(context: Context): Int {
        val cacheDir = File(context.filesDir, "elevenlabs_persistent_cache")
        return cacheDir.listFiles { _, name -> name.endsWith(".mp3") }?.size ?: 0
    }

    fun getCacheSizeBytes(context: Context): Long {
        val cacheDir = File(context.filesDir, "elevenlabs_persistent_cache")
        return cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    fun clearCache(context: Context) {
        val cacheDir = File(context.filesDir, "elevenlabs_persistent_cache")
        if (cacheDir.exists()) {
            cacheDir.listFiles()?.forEach { it.delete() }
        }
    }
}
