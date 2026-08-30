package com.medisense.app.data.remote.gemini

import com.medisense.app.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

interface GeminiApiService {
    suspend fun generateResponse(prompt: String, history: List<Pair<String, String>> = emptyList()): String
}

@Singleton
class GeminiApiServiceImpl @Inject constructor() : GeminiApiService {
    private val repository = GeminiRepository(BuildConfig.GROQ_API_KEY.ifBlank { BuildConfig.GEMINI_API_KEY })

    override suspend fun generateResponse(
        prompt: String,
        history: List<Pair<String, String>>
    ): String {
        return repository.generateContent(prompt, history)
    }
}
