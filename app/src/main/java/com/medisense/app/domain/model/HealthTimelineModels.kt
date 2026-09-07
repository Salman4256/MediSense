package com.medisense.app.domain.model

/**
 * Enumeration of all supported health timeline event types across Modules 1–17.
 * Strictly derives from existing persisted records and computed domain representations.
 */
enum class HealthTimelineEventType(val displayName: String, val category: HealthTimelineCategoryFilter) {
    PROFILE_UPDATED("Profile Updated", HealthTimelineCategoryFilter.CONTEXT_RISK),
    PREDICTION("Disease Prediction", HealthTimelineCategoryFilter.PREDICTIONS),
    SYMPTOM_OBSERVATION("Symptom Observation", HealthTimelineCategoryFilter.PREDICTIONS),
    MEDICATION_STARTED("Medication Started", HealthTimelineCategoryFilter.MEDICATIONS),
    MEDICATION_UPDATED("Medication Updated", HealthTimelineCategoryFilter.MEDICATIONS),
    MEDICATION_TAKEN("Medication Taken", HealthTimelineCategoryFilter.MEDICATIONS),
    MEDICATION_SKIPPED("Medication Skipped", HealthTimelineCategoryFilter.MEDICATIONS),
    MEDICATION_MISSED("Medication Missed", HealthTimelineCategoryFilter.MEDICATIONS),
    MEDICATION_SNOOZED("Medication Snoozed", HealthTimelineCategoryFilter.MEDICATIONS),
    APPOINTMENT_SCHEDULED("Appointment Scheduled", HealthTimelineCategoryFilter.APPOINTMENTS),
    APPOINTMENT_COMPLETED("Appointment Completed", HealthTimelineCategoryFilter.APPOINTMENTS),
    APPOINTMENT_CANCELLED("Appointment Cancelled", HealthTimelineCategoryFilter.APPOINTMENTS),
    HEALTH_TREND("Health Trend", HealthTimelineCategoryFilter.TRENDS),
    TEMPORAL_PATTERN("Temporal Pattern", HealthTimelineCategoryFilter.TRENDS),
    CONTEXT_UPDATE("Health Context Milestone", HealthTimelineCategoryFilter.CONTEXT_RISK),
    RISK_ASSESSMENT("Contextual Risk Assessment", HealthTimelineCategoryFilter.CONTEXT_RISK),
    PERSONALIZED_GUIDANCE("Personalized Guidance", HealthTimelineCategoryFilter.CONTEXT_RISK),
    DATA_QUALITY_ISSUE("Data Quality Issue", HealthTimelineCategoryFilter.DATA_QUALITY),
    SYNC_EVENT("Data Sync Update", HealthTimelineCategoryFilter.REPORTS_SYNC),
    HEALTH_REPORT_GENERATED("Health Report Generated", HealthTimelineCategoryFilter.REPORTS_SYNC)
}

/**
 * Identifies the exact origin module of each timeline entry for transparent attribution.
 */
enum class HealthTimelineSource(val displayName: String) {
    DISEASE_PREDICTION("Disease Prediction (Module 3)"),
    PREDICTION_HISTORY("Prediction History (Module 8)"),
    MEDICATION_MANAGEMENT("Medication Management (Module 6)"),
    APPOINTMENTS("Appointments (Module 7)"),
    HEALTH_TRENDS("Longitudinal Health Trends (Module 9B)"),
    PERSONALIZATION("Personal Health Context (Module 9A)"),
    RCHR("Composite Health Representation (Module 10)"),
    CONTEXTUAL_RISK("Contextual Health Assessment (Module 11)"),
    PERSONALIZED_GUIDANCE("Personalized Guidance (Module 12)"),
    DATA_QUALITY("Health Data Quality (Module 16)"),
    SYNCHRONIZATION("Data Synchronization (Module 15)"),
    HEALTH_REPORT("Health Report (Module 17)"),
    PROFILE("Health Profile (Module 1)")
}

/**
 * Filter time period presets.
 */
enum class HealthTimelinePeriod(val displayName: String, val dayCount: Int?) {
    ALL("All Time", null),
    LAST_7_DAYS("Last 7 Days", 7),
    LAST_30_DAYS("Last 30 Days", 30),
    LAST_90_DAYS("Last 90 Days", 90),
    THIS_YEAR("This Year", 365)
}

/**
 * High-level category grouping for filtering timeline entries.
 */
enum class HealthTimelineCategoryFilter(val displayName: String) {
    ALL("All Categories"),
    PREDICTIONS("Predictions"),
    MEDICATIONS("Medications"),
    APPOINTMENTS("Appointments"),
    TRENDS("Trends & Analytics"),
    CONTEXT_RISK("Context & Risk"),
    DATA_QUALITY("Data Quality"),
    REPORTS_SYNC("Reports & Sync")
}

/**
 * Chronological sorting direction for the timeline.
 */
enum class HealthTimelineSortOrder(val displayName: String) {
    NEWEST_FIRST("Newest First"),
    OLDEST_FIRST("Oldest First")
}

/**
 * Visual display priority / badge styling for timeline cards.
 */
enum class HealthTimelinePriority {
    NORMAL,
    IMPORTANT,
    ATTENTION
}

/**
 * Navigational intent to transition directly to the relevant originating module screen.
 */
enum class TimelineNavigationTarget {
    PREDICTION_DETAIL,
    PREDICTION_HISTORY,
    MEDICATION,
    MEDICATION_HISTORY,
    APPOINTMENT,
    HEALTH_TRENDS,
    CONTEXTUAL_RISK,
    PERSONALIZED_GUIDANCE,
    HEALTH_DATA_QUALITY,
    HEALTH_REPORT,
    PROFILE,
    NONE
}

/**
 * Explainability capsule addressing WHAT, WHEN, WHY, and WHERE for every event.
 */
data class HealthTimelineExplanation(
    val whatHappened: String,
    val whenOccurred: String,
    val whyShown: String,
    val sourceAttribution: String
)

/**
 * Normalized in-memory domain representation of an explainable health event.
 */
data class HealthTimelineEvent(
    val eventId: String,
    val userId: String,
    val timestamp: Long,
    val eventType: HealthTimelineEventType,
    val source: HealthTimelineSource,
    val title: String,
    val shortDescription: String,
    val detailedDescription: String,
    val priority: HealthTimelinePriority = HealthTimelinePriority.NORMAL,
    val relatedEntityId: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val isDerived: Boolean = false,
    val explanation: HealthTimelineExplanation,
    val navigationTarget: TimelineNavigationTarget = TimelineNavigationTarget.NONE
)

/**
 * User-selected filter state for the timeline view.
 */
data class HealthTimelineFilter(
    val period: HealthTimelinePeriod = HealthTimelinePeriod.ALL,
    val category: HealthTimelineCategoryFilter = HealthTimelineCategoryFilter.ALL,
    val sortOrder: HealthTimelineSortOrder = HealthTimelineSortOrder.NEWEST_FIRST,
    val searchQuery: String = ""
)

/**
 * Compact, deterministic summary of the user's longitudinal health journey.
 */
data class HealthTimelineSummary(
    val totalEventsCount: Int,
    val recentPredictionsCount: Int,
    val activeMedicationsCount: Int,
    val upcomingAppointmentsCount: Int,
    val detectedPatternsCount: Int,
    val dataQualityStatus: HealthDataQualityStatus,
    val latestEventTimestamp: Long?,
    val safetyDisclaimer: String = DEFAULT_SAFETY_DISCLAIMER
) {
    companion object {
        const val DEFAULT_SAFETY_DISCLAIMER =
            "Medical Safety Notice: This timeline summarizes information recorded or generated by MediSense for health-management support. Predictions and system-generated insights are not medical diagnoses or treatment recommendations. Consult a qualified healthcare professional for medical decisions."
    }
}
