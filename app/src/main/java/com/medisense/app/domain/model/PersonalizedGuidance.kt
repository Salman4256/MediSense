package com.medisense.app.domain.model

/**
 * Represents an individual, deterministic, and safety-verified personalized guidance item.
 *
 * @param id Unique stable rule/guidance identifier
 * @param category Guidance category
 * @param title Concise, user-friendly card title
 * @param message Actionable, conservative health-management message
 * @param explanation Explains why this specific recommendation appears based on user data
 * @param priority Application-defined trigger priority (LOW, MEDIUM, HIGH)
 * @param sources List of originating MediSense modules (e.g. "Personal Health Context", "Longitudinal Trends")
 * @param actionType Optional target destination screen
 * @param actionLabel Button label text for navigation (e.g. "View Trends", "Update Profile")
 * @param safetyPassed Centralized safety gate verification status
 */
data class PersonalizedGuidance(
    val id: String,
    val category: GuidanceCategory,
    val title: String,
    val message: String,
    val explanation: String,
    val priority: GuidancePriority,
    val sources: List<String>,
    val actionType: GuidanceActionType = GuidanceActionType.NONE,
    val actionLabel: String? = null,
    val safetyPassed: Boolean = true
)
