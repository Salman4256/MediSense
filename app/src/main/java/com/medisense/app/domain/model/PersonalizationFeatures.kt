package com.medisense.app.domain.model

import com.medisense.app.data.local.entity.AppointmentEntity
import com.medisense.app.data.local.entity.HealthProfileEntity
import com.medisense.app.data.local.entity.MedicationEntity
import com.medisense.app.data.local.entity.MedicationHistoryEntity
import com.medisense.app.data.local.entity.PredictionHistoryEntity
import com.medisense.app.utils.AdherenceStats
import com.medisense.app.utils.MedicationDateTimeUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Interpretable feature extractors and transparent rule-based scoring algorithms
 * for the Personal Health Context Engine.
 */
object PersonalizationFeatures {

    const val RECENT_PREDICTION_WINDOW_DAYS = 30
    const val RECENT_ADHERENCE_WINDOW_DAYS = 7
    const val UPCOMING_APPOINTMENT_WINDOW_DAYS = 30

    private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

    /**
     * Calculates personal health profile completeness as a percentage (0% to 100%).
     */
    fun calculateProfileCompleteness(profile: HealthProfileEntity?): Int {
        if (profile == null) return 0

        val fieldsToCheck = listOf(
            !profile.fullName.isNullOrBlank(),
            !profile.dateOfBirth.isNullOrBlank(),
            !profile.gender.isNullOrBlank(),
            !profile.bloodGroup.isNullOrBlank(),
            profile.height != null && profile.height > 0,
            profile.weight != null && profile.weight > 0,
            !profile.allergies.isNullOrBlank(),
            !profile.existingDiseases.isNullOrBlank(),
            !profile.currentMedications.isNullOrBlank(),
            !profile.familyHistory.isNullOrBlank()
        )

        val completedCount = fieldsToCheck.count { it }
        return ((completedCount.toFloat() / fieldsToCheck.size) * 100).toInt().coerceIn(0, 100)
    }

    /**
     * Calculates user age from date of birth string (yyyy-MM-dd).
     */
    fun calculateAge(dobString: String?): Int? {
        if (dobString.isNullOrBlank()) return null
        return try {
            val parts = dobString.split("-")
            if (parts.size == 3) {
                val birthYear = parts[0].toInt()
                val birthMonth = parts[1].toInt()
                val birthDay = parts[2].toInt()

                val today = Calendar.getInstance()
                var age = today.get(Calendar.YEAR) - birthYear
                val currentMonth = today.get(Calendar.MONTH) + 1
                val currentDay = today.get(Calendar.DAY_OF_MONTH)

                if (currentMonth < birthMonth || (currentMonth == birthMonth && currentDay < birthDay)) {
                    age--
                }
                if (age in 0..125) age else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Calculates BMI from height in cm and weight in kg.
     */
    fun calculateBmi(heightCm: Double?, weightKg: Double?): Double? {
        if (heightCm == null || weightKg == null || heightCm <= 0 || weightKg <= 0) return null
        val heightM = heightCm / 100.0
        val rawBmi = weightKg / (heightM * heightM)
        return Math.round(rawBmi * 10.0) / 10.0
    }

    /**
     * Splits comma or semicolon separated text lists (e.g. allergies, chronic conditions).
     */
    fun parseListString(input: String?): List<String> {
        if (input.isNullOrBlank()) return emptyList()
        return input.split(",", ";", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals("None", ignoreCase = true) && !it.equals("N/A", ignoreCase = true) }
    }

    /**
     * Extracts the most frequent symptoms from recent prediction history.
     */
    fun extractFrequentSymptoms(history: List<PredictionHistoryEntity>, topN: Int = 3): List<String> {
        if (history.isEmpty()) return emptyList()
        val symptomCounts = mutableMapOf<String, Int>()

        for (item in history) {
            for (symptom in item.symptoms) {
                val cleaned = symptom.replace("_", " ").trim()
                if (cleaned.isNotEmpty()) {
                    symptomCounts[cleaned] = (symptomCounts[cleaned] ?: 0) + 1
                }
            }
        }

        return symptomCounts.entries
            .sortedByDescending { it.value }
            .take(topN)
            .map { entry ->
                entry.key.split(" ").joinToString(" ") { word ->
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                }
            }
    }

    /**
     * Extracts the most frequent predicted conditions from recent history.
     */
    fun extractFrequentDiseases(history: List<PredictionHistoryEntity>, topN: Int = 2): List<String> {
        if (history.isEmpty()) return emptyList()
        val diseaseCounts = mutableMapOf<String, Int>()

        for (item in history) {
            val disease = item.predictedDisease.trim()
            if (disease.isNotEmpty()) {
                diseaseCounts[disease] = (diseaseCounts[disease] ?: 0) + 1
            }
        }

        return diseaseCounts.entries
            .sortedByDescending { it.value }
            .take(topN)
            .map { entry ->
                entry.key.split(" ").joinToString(" ") { word ->
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                }
            }
    }

    /**
     * Calculates the average confidence percentage across prediction history.
     */
    fun calculateAverageConfidence(history: List<PredictionHistoryEntity>): Float? {
        if (history.isEmpty()) return null
        val sum = history.map { it.confidence }.sum()
        return (sum / history.size).coerceIn(0.0f, 1.0f)
    }

    /**
     * Filters predictions recorded within the recent window (default 30 days).
     */
    fun filterRecentPredictions(history: List<PredictionHistoryEntity>, now: Long = System.currentTimeMillis()): List<PredictionHistoryEntity> {
        val cutoff = now - (RECENT_PREDICTION_WINDOW_DAYS * DAY_MILLIS)
        return history.filter { it.predictionTimestamp >= cutoff }
    }

    /**
     * Filters medication history entries within the recent adherence window (default 7 days).
     */
    fun filterRecentMedicationHistory(history: List<MedicationHistoryEntity>, now: Long = System.currentTimeMillis()): List<MedicationHistoryEntity> {
        val cutoff = now - (RECENT_ADHERENCE_WINDOW_DAYS * DAY_MILLIS)
        return history.filter { it.scheduledDate >= cutoff }
    }

    /**
     * Filters upcoming scheduled appointments within the next window (default 30 days).
     */
    fun filterUpcomingAppointments(appointments: List<AppointmentEntity>, now: Long = System.currentTimeMillis()): List<AppointmentEntity> {
        val cutoff = now + (UPCOMING_APPOINTMENT_WINDOW_DAYS * DAY_MILLIS)
        return appointments.filter { it.status == "SCHEDULED" && it.appointmentTimestamp in now..cutoff }
    }

    /**
     * Calculates a transparent, explainable Personalization Priority / Context Score (0.0 to 100.0).
     *
     * FORMULA BREAKDOWN:
     * 1. Profile Completeness (Weight: 20%) -> 0 to 20 pts
     * 2. Medication Management & Adherence (Weight: 25%) -> 0 to 25 pts
     *    - Has active medications: +10 pts
     *    - Adherence rate >= 80%: +15 pts (scaled linearly for lower adherence)
     * 3. Recent Prediction & Symptom Activity (Weight: 35%) -> 0 to 35 pts
     *    - Recent predictions logged: min(count * 10, 25 pts)
     *    - Recurring symptoms detected: +10 pts
     * 4. Scheduled Healthcare Engagements (Weight: 20%) -> 0 to 20 pts
     *    - Upcoming appointments: min(count * 10, 20 pts)
     */
    fun calculatePersonalizationScore(
        completeness: Int,
        activeMedCount: Int,
        adherenceStats: AdherenceStats?,
        recentPredictionCount: Int,
        hasRecurringSymptoms: Boolean,
        upcomingAppointmentCount: Int
    ): Float {
        // 1. Profile component (0 - 20)
        val profilePts = (completeness.toFloat() / 100f) * 20f

        // 2. Medication component (0 - 25)
        var medPts = 0f
        if (activeMedCount > 0) {
            medPts += 10f
            if (adherenceStats != null && adherenceStats.totalScheduled > 0) {
                medPts += (adherenceStats.percentage / 100f) * 15f
            } else {
                medPts += 5f // Default baseline adherence when newly scheduled
            }
        }

        // 3. Prediction & Symptom component (0 - 35)
        var predPts = 0f
        if (recentPredictionCount > 0) {
            predPts += (recentPredictionCount * 10f).coerceAtMost(25f)
            if (hasRecurringSymptoms) {
                predPts += 10f
            }
        }

        // 4. Appointment component (0 - 20)
        val apptPts = (upcomingAppointmentCount * 10f).coerceAtMost(20f)

        val total = profilePts + medPts + predPts + apptPts
        return Math.round(total.coerceIn(0f, 100f) * 10f) / 10f
    }

    /**
     * Generates a concise, factual, and strictly non-diagnostic human-readable summary.
     */
    fun generateFactualSummary(
        profileCompleteness: Int,
        recentPredictions: List<PredictionHistoryEntity>,
        frequentSymptoms: List<String>,
        activeMeds: List<MedicationEntity>,
        adherenceStats: AdherenceStats?,
        upcomingAppointments: List<AppointmentEntity>
    ): String {
        val statements = mutableListOf<String>()

        // 1. Symptom & Prediction statements
        if (recentPredictions.isNotEmpty()) {
            if (frequentSymptoms.isNotEmpty()) {
                val symptomStr = frequentSymptoms.joinToString(", ")
                statements.add("Frequently observed symptoms: $symptomStr (${recentPredictions.size} recent prediction records)")
            } else {
                statements.add("${recentPredictions.size} recent disease prediction records archived")
            }
        }

        // 2. Medication & Adherence statements
        if (activeMeds.isNotEmpty()) {
            if (adherenceStats != null && adherenceStats.totalScheduled > 0) {
                val adherencePercent = adherenceStats.percentage.toInt()
                statements.add("${activeMeds.size} active medication${if (activeMeds.size > 1) "s" else ""} ($adherencePercent% adherence this week)")
            } else {
                statements.add("${activeMeds.size} active medication${if (activeMeds.size > 1) "s" else ""} scheduled")
            }
        }

        // 3. Appointment statements
        if (upcomingAppointments.isNotEmpty()) {
            val next = upcomingAppointments.first()
            val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
            val dateStr = dateFormat.format(Date(next.appointmentTimestamp))
            statements.add("Upcoming appointment with ${next.doctorName} on $dateStr")
        }

        return when {
            statements.isNotEmpty() -> {
                "Your recent health activity: " + statements.joinToString(" • ") + "."
            }
            profileCompleteness > 0 -> {
                "Your health profile is $profileCompleteness% complete. Record your symptoms or schedule medications to build your adaptive context."
            }
            else -> {
                "Not enough health information is available yet. Complete your profile or log symptoms to see your personalized health context."
            }
        }
    }
}
