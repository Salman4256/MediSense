package com.medisense.app.data.remote.gemini

import com.medisense.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

interface GeminiApiService {
    suspend fun sendMessage(
        history: List<Pair<String, String>>, // Pair<Role, Message> (Role = "user" or "model")
        newMessage: String
    ): String
}

class GeminiApiServiceImpl(
    private val api: GeminiApi = GeminiClient.api
) : GeminiApiService {

    private val gson = Gson()

    override suspend fun sendMessage(
        history: List<Pair<String, String>>,
        newMessage: String
    ): String = withContext(Dispatchers.IO) {
        
        val chatHistory = history.map { (role, message) ->
            GeminiContent(
                role = role,
                parts = listOf(GeminiPart(text = message))
            )
        }

        val request = GeminiRequest(
            contents = chatHistory + GeminiContent("user", listOf(GeminiPart(newMessage))),
            systemInstruction = GeminiSystemInstruction(
                parts = listOf(GeminiPart(GeminiPromptBuilder.getSystemPrompt()))
            )
        )

        val preferredModels = listOf("gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.0-flash")
        var selectedModel = "gemini-2.5-flash" // default fallback

        try {
            val modelsResponse = api.getModels(BuildConfig.GEMINI_API_KEY)
            if (modelsResponse.isSuccessful) {
                val availableModels = modelsResponse.body()?.models?.map { it.name.replace("models/", "") } ?: emptyList()
                Log.d("GeminiApiService", "Available models: $availableModels")
                for (pref in preferredModels) {
                    if (availableModels.contains(pref)) {
                        selectedModel = pref
                        break
                    }
                }
            } else {
                Log.e("GeminiApiService", "Failed to get models: Code ${modelsResponse.code()}")
            }
        } catch (e: Exception) {
            Log.e("GeminiApiService", "Network error fetching models", e)
        }

        Log.d("GeminiApiService", "Selected model: $selectedModel")

        try {
            val response = api.generateContent(selectedModel, BuildConfig.GEMINI_API_KEY, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                val text = body?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (text != null) return@withContext text
                throw Exception("Received empty response from AI.")
            } else {
                val code = response.code()
                val errorJson = response.errorBody()?.string()
                
                var serverMessage = "Unknown error"
                try {
                    if (errorJson != null) {
                        val errorResponse = gson.fromJson(errorJson, GeminiErrorResponse::class.java)
                        serverMessage = errorResponse.error?.message ?: serverMessage
                    }
                } catch (e: JsonSyntaxException) {
                    serverMessage = "Unparseable error JSON"
                }

                Log.e("GeminiApiService", "Model $selectedModel failed: Code $code - $serverMessage")

                val friendlyMessage = when (code) {
                    404 -> "AI model $selectedModel is temporarily unavailable."
                    401 -> "AI service authentication failed."
                    429 -> "AI service is currently busy. Please try again later."
                    in 500..599 -> "AI server is down. Please try again later."
                    else -> "Failed to connect to AI Assistant."
                }
                
                throw Exception(friendlyMessage)
            }
        } catch (e: Exception) {
            if (e.message?.contains("AI service") == true || e.message?.contains("AI server") == true || e.message?.contains("Failed to connect") == true || e.message?.contains("AI model") == true) {
                throw e
            }
            Log.e("GeminiApiService", "Network exception on model $selectedModel", e)
            throw Exception("Network error while connecting to AI.")
        }
    }
}
