package com.medisense.app.ai.groq

import com.medisense.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroqAiService @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(35, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(35, TimeUnit.SECONDS)
        .build()

    private val groqEndpoint = "https://api.groq.com/openai/v1/chat/completions"

    // Text candidate models supported on the Groq endpoint
    private val textModels = listOf(
        "openai/gpt-oss-120b",
        "openai/gpt-oss-20b",
        "qwen/qwen3.8-27b",
        "groq/compound",
        "groq/compound-mini"
    )

    // Vision-capable multimodal models
    private val visionModels = listOf(
        "qwen/qwen3.8-27b",
        "qwen/qwen3.6-27b",
        "groq/compound"
    )

    // Secondary Gemini models for fallback
    private val geminiModels = listOf(
        "gemini-1.5-flash",
        "gemini-2.0-flash",
        "gemini-1.5-pro"
    )

    suspend fun getChatCompletion(messagesPayload: JSONArray): Result<String> = withContext(Dispatchers.IO) {
        val groqApiKey = BuildConfig.GROQ_API_KEY.trim()
        val geminiApiKey = BuildConfig.GEMINI_API_KEY.trim()

        val isMultimodal = hasImageContent(messagesPayload)
        val candidateModels = if (isMultimodal) visionModels else textModels

        var lastError: Exception? = null

        // 1. Try Groq AI provider
        if (groqApiKey.isNotBlank()) {
            for (modelName in candidateModels) {
                try {
                    val requestBodyJson = JSONObject().apply {
                        put("model", modelName)
                        put("messages", messagesPayload)
                        put("temperature", 0.7)
                        put("max_tokens", 1024)
                        put("top_p", 0.95)
                    }

                    val request = Request.Builder()
                        .url(groqEndpoint)
                        .addHeader("Authorization", "Bearer $groqApiKey")
                        .addHeader("Content-Type", "application/json")
                        .post(requestBodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .build()

                    val response = client.newCall(request).execute()
                    val responseCode = response.code
                    val responseBody = response.body?.string() ?: ""

                    if (response.isSuccessful && responseBody.isNotBlank()) {
                        val json = JSONObject(responseBody)
                        val choices = json.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val messageObj = choices.getJSONObject(0).optJSONObject("message")
                            val content = messageObj?.optString("content") ?: ""
                            if (content.isNotBlank()) {
                                return@withContext Result.success(content.trim())
                            }
                        }
                    }

                    val extractedMsg = extractErrorMessage(responseBody)

                    // If model error, try next candidate model
                    lastError = IOException("Groq ($modelName, $responseCode): $extractedMsg")
                    continue
                } catch (e: Exception) {
                    lastError = e
                }
            }
        }

        // 2. Failover Provider: Google Gemini API
        val fallbackKey = if (geminiApiKey.isNotBlank()) geminiApiKey else groqApiKey
        if (fallbackKey.isNotBlank()) {
            val geminiResult = callGeminiFallback(messagesPayload, fallbackKey)
            if (geminiResult.isSuccess) {
                return@withContext geminiResult
            } else if (lastError == null) {
                lastError = geminiResult.exceptionOrNull() as? Exception
            }
        }

        Result.failure(lastError ?: IOException("Unable to get response from AI service. Please verify your internet connection."))
    }

    private fun hasImageContent(messagesPayload: JSONArray): Boolean {
        for (i in 0 until messagesPayload.length()) {
            val msg = messagesPayload.optJSONObject(i) ?: continue
            val content = msg.opt("content")
            if (content is JSONArray) {
                for (j in 0 until content.length()) {
                    val part = content.optJSONObject(j) ?: continue
                    if (part.optString("type") == "image_url") return true
                }
            }
        }
        return false
    }

    private fun callGeminiFallback(messagesPayload: JSONArray, apiKey: String): Result<String> {
        var systemInstruction = ""
        val contentsArray = JSONArray()

        for (i in 0 until messagesPayload.length()) {
            val msg = messagesPayload.optJSONObject(i) ?: continue
            val role = msg.optString("role")
            val content = msg.opt("content")

            if (role.equals("system", ignoreCase = true)) {
                systemInstruction = content as? String ?: ""
            } else {
                val geminiRole = if (role.equals("user", ignoreCase = true)) "user" else "model"
                val partsArray = JSONArray()

                if (content is JSONArray) {
                    for (j in 0 until content.length()) {
                        val part = content.optJSONObject(j) ?: continue
                        if (part.optString("type") == "text") {
                            partsArray.put(JSONObject().put("text", part.optString("text")))
                        } else if (part.optString("type") == "image_url") {
                            val url = part.optJSONObject("image_url")?.optString("url") ?: ""
                            if (url.startsWith("data:image/")) {
                                val base64Data = url.substringAfter("base64,")
                                val mimeType = url.substringBefore(";base64,").removePrefix("data:")
                                partsArray.put(JSONObject().apply {
                                    put("inline_data", JSONObject().apply {
                                        put("mime_type", mimeType)
                                        put("data", base64Data)
                                    })
                                })
                            }
                        }
                    }
                } else {
                    partsArray.put(JSONObject().put("text", content?.toString() ?: ""))
                }

                contentsArray.put(JSONObject().apply {
                    put("role", geminiRole)
                    put("parts", partsArray)
                })
            }
        }

        for (modelName in geminiModels) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
                val payload = JSONObject().apply {
                    put("contents", contentsArray)
                    if (systemInstruction.isNotBlank()) {
                        put("system_instruction", JSONObject().apply {
                            put("parts", JSONArray().put(JSONObject().put("text", systemInstruction)))
                        })
                    }
                }

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful && responseBody.isNotBlank()) {
                    val json = JSONObject(responseBody)
                    val candidates = json.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val contentObj = candidates.getJSONObject(0).optJSONObject("content")
                        val parts = contentObj?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            val text = parts.getJSONObject(0).optString("text")
                            if (text.isNotBlank()) {
                                return Result.success(text.trim())
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Try next Gemini model
            }
        }

        return Result.failure(IOException("Failed to receive response from secondary AI provider."))
    }

    private fun extractErrorMessage(responseBody: String): String {
        return try {
            val json = JSONObject(responseBody)
            json.optJSONObject("error")?.optString("message") ?: responseBody.take(120)
        } catch (e: Exception) {
            responseBody.take(120)
        }
    }
}
