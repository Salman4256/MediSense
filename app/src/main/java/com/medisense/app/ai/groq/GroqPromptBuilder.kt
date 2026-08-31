package com.medisense.app.ai.groq

import com.medisense.app.data.local.entity.ChatMessageEntity
import com.medisense.app.data.local.entity.HealthProfileEntity
import org.json.JSONArray
import org.json.JSONObject

object GroqPromptBuilder {

    const val SYSTEM_INSTRUCTION = """
You are MediSense AI, an intelligent, empathetic, and educational AI Health Assistant.

Your Mission:
Provide helpful, beginner-friendly, and scientifically accurate general health education, wellness guidance, lifestyle advice, medical term explanations, and informational image analysis.

Strict Safety Rules:
1. NEVER diagnose a medical condition or disease from text or images.
2. NEVER prescribe medication, recommend prescription dosages, or tell a user to stop prescribed medication.
3. For medical or health-related images (skin, prescription bottles, lab reports), clearly state that image analysis is informational and cannot replace an in-person physical clinical examination.
4. Clearly communicate uncertainty; if a medical query requires in-person clinical evaluation, explicitly advise the user to consult a doctor or healthcare professional.
5. If a user describes or shows potentially urgent or life-threatening symptoms (e.g. severe chest pain, sudden numbness, difficulty breathing, acute trauma), immediately advise seeking emergency medical care.
6. Protect user privacy and avoid alarming language.

Formatting Rules for Mobile Screens:
1. DO NOT use markdown table format with pipes (| column |). Tables look unreadable and broken on mobile phones.
2. Use clean bullet points (- or •), bold topic titles (**Title**), and clear section headers (### Section).
3. Use emojis for visual structure (e.g., 🩺 Overview, 💡 Common Causes, 💧 Self-Care Tips, ⚠️ When to See a Doctor).
4. Keep bullet points short, structured, and easy to read.
    """

    fun buildMessagesPayload(
        userPrompt: String,
        recentHistory: List<ChatMessageEntity>,
        healthProfile: HealthProfileEntity?,
        imageBase64: String? = null
    ): JSONArray {
        val messages = JSONArray()

        // 1. Build System Instruction with selective context
        val systemPrompt = buildString {
            append(SYSTEM_INSTRUCTION.trim())

            if (healthProfile != null) {
                val relevantContext = extractRelevantContext(userPrompt, healthProfile)
                if (relevantContext.isNotBlank()) {
                    append("\n\nRelevant User Profile Context (Use only if directly helpful):\n")
                    append(relevantContext)
                }
            }
        }

        messages.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })

        // 2. Add Recent Conversation History Window (text only in history for efficiency)
        for (msg in recentHistory) {
            val role = if (msg.role.equals("USER", ignoreCase = true)) "user" else "assistant"
            messages.put(JSONObject().apply {
                put("role", role)
                put("content", msg.content)
            })
        }

        // 3. Add Current User Message (Multimodal or Text)
        val cleanPrompt = userPrompt.ifBlank { "Please describe and analyze this health-related image." }
        if (!imageBase64.isNullOrBlank()) {
            val contentArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", cleanPrompt)
                })
                put(JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().apply {
                        put("url", "data:image/jpeg;base64,$imageBase64")
                    })
                })
            }
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", contentArray)
            })
        } else {
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", cleanPrompt)
            })
        }

        return messages
    }

    private fun extractRelevantContext(prompt: String, profile: HealthProfileEntity): String {
        val lower = prompt.lowercase()
        val contextParts = mutableListOf<String>()

        // Check if query is diet or nutrition related
        val isDietRelated = lower.contains("food") || lower.contains("diet") || lower.contains("eat") ||
                lower.contains("nutrition") || lower.contains("meal") || lower.contains("allergy") || lower.contains("allergic")

        // Check if query is medication related
        val isMedRelated = lower.contains("medicine") || lower.contains("medication") || lower.contains("drug") ||
                lower.contains("pill") || lower.contains("dose") || lower.contains("treatment")

        // Check if physical attributes are relevant
        val isFitnessRelated = lower.contains("weight") || lower.contains("bmi") || lower.contains("calorie") ||
                lower.contains("exercise") || lower.contains("workout") || lower.contains("fat")

        if (isDietRelated && !profile.allergies.isNullOrBlank()) {
            contextParts.add("Known Allergies: ${profile.allergies}")
        }

        if ((isDietRelated || isMedRelated) && !profile.existingDiseases.isNullOrBlank()) {
            contextParts.add("Existing Health Conditions: ${profile.existingDiseases}")
        }

        if (isMedRelated && !profile.currentMedications.isNullOrBlank()) {
            contextParts.add("Current Medications: ${profile.currentMedications}")
        }

        if (isFitnessRelated) {
            if (profile.height != null && profile.height > 0) contextParts.add("Height: ${profile.height} cm")
            if (profile.weight != null && profile.weight > 0) contextParts.add("Weight: ${profile.weight} kg")
        }

        if (!profile.gender.isNullOrBlank() && (lower.contains("woman") || lower.contains("man") || lower.contains("gender") || lower.contains("hormone") || lower.contains("pregnancy"))) {
            contextParts.add("Gender: ${profile.gender}")
        }

        return contextParts.joinToString("\n")
    }
}
