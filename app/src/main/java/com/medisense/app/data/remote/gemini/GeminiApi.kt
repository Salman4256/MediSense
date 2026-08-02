package com.medisense.app.data.remote.gemini

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface GeminiApi {
    @GET("v1beta/models")
    suspend fun getModels(@Query("key") apiKey: String): Response<GeminiModelListResponse>

    @POST("v1beta/models/{model}:generateContent")
    @Headers("Content-Type: application/json")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): Response<GeminiResponse>
}
