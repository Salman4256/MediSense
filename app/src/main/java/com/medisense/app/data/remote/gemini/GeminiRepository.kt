package com.medisense.app.data.remote.gemini

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiRepository(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Primary Groq models list in order of preference
    private val groqEndpoint = "https://api.groq.com/openai/v1/chat/completions"
    private val candidateGroqModels = listOf(
        "openai/gpt-oss-120b",
        "openai/gpt-oss-20b",
        "qwen/qwen3.8-27b",
        "groq/compound",
        "groq/compound-mini"
    )

    // Secondary Gemini models list
    private val candidateGeminiModels = listOf(
        "gemini-1.5-flash",
        "gemini-2.0-flash",
        "gemini-1.5-pro"
    )

    suspend fun generateContent(
        prompt: String,
        history: List<Pair<String, String>> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        val messages = JSONArray()

        // System message
        messages.put(JSONObject().apply {
            put("role", "system")
            put("content", GeminiPromptBuilder.SYSTEM_INSTRUCTION.trim())
        })

        // Add conversation history
        for (item in history) {
            val role = if (item.first.equals("USER", ignoreCase = true)) "user" else "assistant"
            messages.put(JSONObject().apply {
                put("role", role)
                put("content", item.second)
            })
        }

        // Current user message
        messages.put(JSONObject().apply {
            put("role", "user")
            put("content", prompt.trim())
        })

        var lastError: Exception? = null

        // 1. Try Groq candidate models
        for (modelName in candidateGroqModels) {
            try {
                val body = JSONObject().apply {
                    put("model", modelName)
                    put("messages", messages)
                    put("temperature", 0.7)
                    put("max_tokens", 1024)
                }

                val request = Request.Builder()
                    .url(groqEndpoint)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful && responseBody.isNotBlank()) {
                    val json = JSONObject(responseBody)
                    val choices = json.optJSONArray("choices")
                    if (choices != null && choices.length() > 0) {
                        val messageObj = choices.getJSONObject(0).optJSONObject("message")
                        val content = messageObj?.optString("content") ?: ""
                        if (content.isNotBlank()) {
                            return@withContext content.trim()
                        }
                    }
                }

                if (response.code == 404 || response.code == 400 || response.code == 429) {
                    lastError = Exception("Groq model $modelName returned ${response.code}")
                    continue
                }
            } catch (e: Exception) {
                lastError = e
            }
        }

        // 2. Fallback to Gemini REST API
        for (geminiModel in candidateGeminiModels) {
            try {
                val contents = JSONArray()
                for (item in history) {
                    val role = if (item.first.equals("USER", ignoreCase = true)) "user" else "model"
                    contents.put(JSONObject().apply {
                        put("role", role)
                        put("parts", JSONArray().put(JSONObject().put("text", item.second)))
                    })
                }
                contents.put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                })

                val geminiPayload = JSONObject().apply {
                    put("contents", contents)
                    put("system_instruction", JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().put("text", GeminiPromptBuilder.SYSTEM_INSTRUCTION.trim())))
                    })
                }

                val url = "https://generativelanguage.googleapis.com/v1beta/models/$geminiModel:generateContent?key=$apiKey"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .post(geminiPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful && responseBody.isNotBlank()) {
                    val json = JSONObject(responseBody)
                    val candidates = json.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
                        val text = parts?.optJSONObject(0)?.optString("text") ?: ""
                        if (text.isNotBlank()) {
                            return@withContext text.trim()
                        }
                    }
                }
            } catch (e: Exception) {
                lastError = e
            }
        }

        throw (lastError ?: Exception("Unable to get AI response. Please try again."))
    }
}
