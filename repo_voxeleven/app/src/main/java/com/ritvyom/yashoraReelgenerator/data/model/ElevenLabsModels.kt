package com.ritvyom.yashoraReelgenerator.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TtsRequest(
    @Json(name = "text") val text: String,
    @Json(name = "model_id") val modelId: String = "eleven_multilingual_v2",
    @Json(name = "voice_settings") val voiceSettings: VoiceSettings? = null
)

@JsonClass(generateAdapter = true)
data class VoiceSettings(
    @Json(name = "stability") val stability: Double = 0.5,
    @Json(name = "similarity_boost") val similarityBoost: Double = 0.75,
    @Json(name = "style") val style: Double = 0.0,
    @Json(name = "use_speaker_boost") val useSpeakerBoost: Boolean = true
)

@JsonClass(generateAdapter = true)
data class VoicesResponse(
    @Json(name = "voices") val voices: List<Voice>
)

@JsonClass(generateAdapter = true)
data class Voice(
    @Json(name = "voice_id") val voiceId: String,
    @Json(name = "name") val name: String,
    @Json(name = "category") val category: String? = null,
    @Json(name = "labels") val labels: Map<String, String>? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "preview_url") val previewUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class UserResponse(
    @Json(name = "subscription") val subscription: Subscription
)

@JsonClass(generateAdapter = true)
data class Subscription(
    @Json(name = "character_count") val characterCount: Int,
    @Json(name = "character_limit") val characterLimit: Int,
    @Json(name = "tier") val tier: String
)
