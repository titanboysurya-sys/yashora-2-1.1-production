package com.ritvyom.yashoraReelgenerator.data.api

import com.ritvyom.yashoraReelgenerator.data.model.TtsRequest
import com.ritvyom.yashoraReelgenerator.data.model.UserResponse
import com.ritvyom.yashoraReelgenerator.data.model.VoicesResponse
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Streaming

interface ElevenLabsApi {

    @GET("v1/voices")
    suspend fun getVoices(
        @Header("xi-api-key") apiKey: String
    ): VoicesResponse

    @GET("v1/user")
    suspend fun getUserInfo(
        @Header("xi-api-key") apiKey: String
    ): UserResponse

    @POST("v1/text-to-speech/{voice_id}")
    @Streaming
    suspend fun textToSpeech(
        @Path("voice_id") voiceId: String,
        @Header("xi-api-key") apiKey: String,
        @Body request: TtsRequest
    ): ResponseBody
}
