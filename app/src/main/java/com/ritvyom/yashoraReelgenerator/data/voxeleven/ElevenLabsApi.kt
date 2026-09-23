package com.ritvyom.yashoraReelgenerator.data.voxeleven

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ElevenLabsApi {
    @GET("v1/voices")
    suspend fun getVoices(
        @Header("xi-api-key") apiKey: String
    ): VoicesResponse

    @POST("v1/text-to-speech/{voice_id}")
    @Headers("Content-Type: application/json")
    suspend fun textToSpeech(
        @Path("voice_id") voiceId: String,
        @Header("xi-api-key") apiKey: String,
        @Body request: TtsRequest
    ): ResponseBody

    @GET("v1/user/subscription")
    suspend fun getUserSubscription(
        @Header("xi-api-key") apiKey: String
    ): Subscription

    @GET("v1/user")
    suspend fun getUserInfo(
        @Header("xi-api-key") apiKey: String
    ): UserResponse
}
