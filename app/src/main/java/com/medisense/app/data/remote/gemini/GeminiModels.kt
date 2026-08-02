package com.medisense.app.data.remote.gemini

import com.google.gson.annotations.SerializedName

// Request Models
data class GeminiRequest(
    @SerializedName("contents")
    val contents: List<GeminiContent>,
    @SerializedName("systemInstruction")
    val systemInstruction: GeminiSystemInstruction? = null
)

data class GeminiContent(
    @SerializedName("role")
    val role: String, // "user" or "model"
    @SerializedName("parts")
    val parts: List<GeminiPart>
)

data class GeminiSystemInstruction(
    @SerializedName("parts")
    val parts: List<GeminiPart>
)

data class GeminiPart(
    @SerializedName("text")
    val text: String
)

// Success Response Models
data class GeminiResponse(
    @SerializedName("candidates")
    val candidates: List<GeminiCandidate>?
)

data class GeminiCandidate(
    @SerializedName("content")
    val content: GeminiContent?
)

// Error Response Models
data class GeminiErrorResponse(
    @SerializedName("error")
    val error: GeminiErrorDetails?
)

data class GeminiErrorDetails(
    @SerializedName("code")
    val code: Int?,
    @SerializedName("message")
    val message: String?,
    @SerializedName("status")
    val status: String?
)

// Model Info Models
data class GeminiModelListResponse(
    @SerializedName("models")
    val models: List<GeminiModelInfo>?
)

data class GeminiModelInfo(
    @SerializedName("name")
    val name: String,
    @SerializedName("supportedGenerationMethods")
    val supportedGenerationMethods: List<String>?
)
