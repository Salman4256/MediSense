package com.medisense.app.domain.guidance

/**
 * Centralized, application-defined configuration parameters for Module 12 Personalized Guidance.
 *
 * NOTE: All thresholds and weights are application heuristics for health management and
 * are NOT clinically validated diagnostic criteria.
 */
object GuidanceConfiguration {

    // Maximum recommendations to display on screen to prevent user cognitive overload
    const val MAX_DISPLAYED_RECOMMENDATIONS = 5

    // Profile completeness threshold (below this triggers profile completion guidance)
    const val PROFILE_COMPLETENESS_THRESHOLD = 80 // Percentage

    // Medication adherence thresholds
    const val ADHERENCE_OPTIMAL_THRESHOLD = 80.0f // Percentage
    const val ADHERENCE_SUBOPTIMAL_THRESHOLD = 60.0f // Percentage

    // Rule Identifiers
    const val RULE_PROFILE_COMPLETENESS = "rule_profile_completeness"
    const val RULE_SYMPTOM_MONITORING = "rule_symptom_monitoring"
    const val RULE_MEDICATION_ADHERENCE = "rule_medication_adherence"
    const val RULE_APPOINTMENT_FOLLOW_UP = "rule_appointment_follow_up"
    const val RULE_HEALTH_TRENDS = "rule_health_trends"
    const val RULE_PREDICTION_TRACKING = "rule_prediction_tracking"
    const val RULE_PROFESSIONAL_REVIEW = "rule_professional_review"
    const val RULE_RECORD_MAINTENANCE = "rule_record_maintenance"
    const val RULE_BASELINE_SETUP = "rule_baseline_setup"

    // Source Names
    const val SOURCE_MODULE_2 = "Personal Health Profile"
    const val SOURCE_MODULE_6 = "Medication Reminders"
    const val SOURCE_MODULE_7 = "Doctor Appointments"
    const val SOURCE_MODULE_8 = "Prediction History"
    const val SOURCE_MODULE_9A = "Personal Health Context"
    const val SOURCE_MODULE_9B = "Longitudinal Health Trends"
    const val SOURCE_MODULE_10 = "Composite Health Representation (RCHR)"
    const val SOURCE_MODULE_11 = "Health Context Risk Engine"

    // Disclaimers
    const val MEDICAL_SAFETY_DISCLAIMER =
        "Personalized guidance is generated from information available in MediSense and is intended for health-management support only. It is not a medical diagnosis, treatment plan, or substitute for professional medical advice."
}
