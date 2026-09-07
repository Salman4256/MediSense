package com.medisense.app.domain.consultation

import com.medisense.app.data.local.entity.*
import com.medisense.app.domain.model.*

/**
 * Deterministic rule-based engine that generates neutral, explainable discussion questions
 * for the user to consider discussing with their healthcare professional.
 *
 * NOTE: Strictly non-diagnostic and non-prescriptive.
 */
object QuestionSuggestionEngine {

    private const val DEFAULT_MAX_QUESTIONS = 8

    fun generateQuestions(
        profile: HealthProfileEntity?,
        predictions: List<PredictionHistoryEntity>,
        medications: List<MedicationEntity>,
        medicationHistory: List<MedicationHistoryEntity>,
        appointments: List<AppointmentEntity>,
        longitudinalSummary: LongitudinalHealthSummary?,
        dataQualitySummary: HealthDataQualitySummary?,
        maxQuestions: Int = DEFAULT_MAX_QUESTIONS
    ): List<ConsultationQuestion> {
        val candidates = mutableListOf<ConsultationQuestion>()

        // 1. Recurring Symptoms Rule
        val allSymptoms = predictions.flatMap { it.symptoms }.filter { it.isNotBlank() }
        val symptomCounts = allSymptoms.groupingBy { it.trim().lowercase() }.eachCount()
        val recurringSymptoms = symptomCounts.filter { it.value >= 2 }.keys.toList()

        if (recurringSymptoms.isNotEmpty()) {
            val symptomsStr = recurringSymptoms.take(3).joinToString(", ")
            candidates.add(
                ConsultationQuestion(
                    id = "q_recurring_symptoms",
                    questionText = "Could we review the recurring symptoms ($symptomsStr) recorded in my recent health history?",
                    category = ConsultationQuestionSource.SYMPTOM_PATTERN,
                    rationale = "Repeated symptom occurrences were recorded across multiple prediction checks."
                )
            )
        } else if (allSymptoms.isNotEmpty()) {
            val symptomsStr = allSymptoms.distinct().take(3).joinToString(", ")
            candidates.add(
                ConsultationQuestion(
                    id = "q_recent_symptoms",
                    questionText = "I recently logged symptoms including $symptomsStr. What observations should I monitor going forward?",
                    category = ConsultationQuestionSource.SYMPTOM_PATTERN,
                    rationale = "Recent symptoms were recorded in your health history."
                )
            )
        }

        // 2. Prediction History Discussion Rule
        if (predictions.isNotEmpty()) {
            val topCondition = predictions.groupBy { it.predictedDisease }.maxByOrNull { it.value.size }?.key
            candidates.add(
                ConsultationQuestion(
                    id = "q_prediction_history",
                    questionText = "MediSense recorded AI model indications related to $topCondition. What clinical evaluation or standard diagnostic tests would you recommend?",
                    category = ConsultationQuestionSource.PREDICTION_HISTORY,
                    rationale = "Model estimations were recorded based on your reported symptoms."
                )
            )
        }

        // 3. Medication Adherence & Routine Rule
        val skippedOrMissed = medicationHistory.filter {
            it.status.equals("SKIPPED", ignoreCase = true) || it.status.equals("MISSED", ignoreCase = true)
        }
        if (skippedOrMissed.isNotEmpty()) {
            val medName = skippedOrMissed.first().medicineName
            candidates.add(
                ConsultationQuestion(
                    id = "q_adherence_gap",
                    questionText = "I have experienced some difficulty maintaining my scheduled dose timing for $medName. What strategies or timing adjustments do you recommend?",
                    category = ConsultationQuestionSource.MEDICATION_ADHERENCE,
                    rationale = "Recent medication logs show missed or skipped scheduled doses."
                )
            )
        }

        // 4. Active Medications Review Rule
        val activeMeds = medications.filter { it.active }
        if (activeMeds.isNotEmpty()) {
            val medNames = activeMeds.take(2).joinToString(", ") { it.medicineName }
            candidates.add(
                ConsultationQuestion(
                    id = "q_active_meds_review",
                    questionText = "Are there any interactions, side effects, or routine monitoring tests recommended for my currently recorded medications ($medNames)?",
                    category = ConsultationQuestionSource.MEDICATION_ADHERENCE,
                    rationale = "You have active scheduled medications recorded in your regimen."
                )
            )
        }

        // 5. Allergy & Contraindication Verification Rule
        if (!profile?.allergies.isNullOrBlank()) {
            candidates.add(
                ConsultationQuestion(
                    id = "q_allergies_check",
                    questionText = "Could we verify how my recorded allergies (${profile?.allergies}) might influence any future treatments or prescriptions?",
                    category = ConsultationQuestionSource.PROFILE_GAP,
                    rationale = "Known allergies are recorded in your baseline health profile."
                )
            )
        } else {
            candidates.add(
                ConsultationQuestion(
                    id = "q_allergies_unrecorded",
                    questionText = "Should I be tested or screened for any medication or environmental allergies based on my history?",
                    category = ConsultationQuestionSource.PROFILE_GAP,
                    rationale = "No allergy information is currently stored in your profile."
                )
            )
        }

        // 6. Upcoming Appointment Prioritization Rule
        val upcomingAppt = appointments.firstOrNull { it.status.equals("SCHEDULED", ignoreCase = true) }
        if (upcomingAppt != null) {
            candidates.add(
                ConsultationQuestion(
                    id = "q_appointment_priority",
                    questionText = "What specific health goals or preventive screenings should we prioritize during today's visit?",
                    category = ConsultationQuestionSource.APPOINTMENT_TOPIC,
                    rationale = "You have an appointment scheduled with ${upcomingAppt.doctorName}."
                )
            )
        }

        // 7. Longitudinal Trends Rule
        if (longitudinalSummary != null && longitudinalSummary.detectedPatterns.isNotEmpty()) {
            val pattern = longitudinalSummary.detectedPatterns.first()
            candidates.add(
                ConsultationQuestion(
                    id = "q_trend_pattern",
                    questionText = "My health trends show '${pattern.title}'. What lifestyle or clinical factors could be contributing to this pattern?",
                    category = ConsultationQuestionSource.SYMPTOM_PATTERN,
                    rationale = "Longitudinal analysis identified a recurring temporal health pattern."
                )
            )
        }

        // 8. Data Quality & Regular Tracking Rule
        if (dataQualitySummary?.status == HealthDataQualityStatus.NEEDS_ATTENTION) {
            candidates.add(
                ConsultationQuestion(
                    id = "q_data_completeness",
                    questionText = "Which baseline health metrics (such as blood pressure, blood glucose, or weight) would be most beneficial for me to track regularly at home?",
                    category = ConsultationQuestionSource.DATA_QUALITY,
                    rationale = "Completing your personal health records improves future consultation preparation."
                )
            )
        }

        // 9. Fallback Preventive Rule (if candidates are few)
        if (candidates.size < 3) {
            candidates.add(
                ConsultationQuestion(
                    id = "q_preventive_care",
                    questionText = "What routine preventive health screenings or lifestyle adjustments would you recommend for my age and health status?",
                    category = ConsultationQuestionSource.APPOINTMENT_TOPIC,
                    rationale = "Establishing a proactive preventive care baseline."
                )
            )
        }

        // Distinct by ID and cap at maxQuestions
        return candidates.distinctBy { it.id }.take(maxQuestions)
    }
}
