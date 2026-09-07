package com.medisense.app.domain.consultation

import com.medisense.app.data.local.entity.*
import com.medisense.app.domain.model.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Deterministic generator that aggregates outputs from Modules 1–18 into a structured,
 * explainable, medically safe [ConsultationSummary].
 *
 * NOTE: Operates strictly offline without external LLMs, cloud AI, or network calls.
 */
object ConsultationSummaryGenerator {

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    private val dateOnlyFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun generate(
        userId: String,
        profile: HealthProfileEntity?,
        predictions: List<PredictionHistoryEntity>,
        medications: List<MedicationEntity>,
        medicationHistory: List<MedicationHistoryEntity>,
        appointments: List<AppointmentEntity>,
        longitudinalSummary: LongitudinalHealthSummary?,
        personalContext: PersonalHealthContext?,
        riskAssessment: ContextualRiskAssessment?,
        guidanceResult: GuidanceEngineResult?,
        dataQualitySummary: HealthDataQualitySummary?,
        period: ConsultationPeriod = ConsultationPeriod.LAST_30_DAYS,
        customQuestions: List<ConsultationQuestion> = emptyList(),
        currentTime: Long = System.currentTimeMillis()
    ): ConsultationSummary {

        // Period Cutoff Calculation
        val cutoffTimestamp = if (period.dayCount != null) {
            currentTime - (period.dayCount.toLong() * 24 * 60 * 60 * 1000L)
        } else {
            0L
        }

        // Filter user-isolated records by period
        val userPredictions = predictions.filter { it.userId == userId && it.predictionTimestamp >= cutoffTimestamp }
        val userMeds = medications.filter { it.userId == userId }
        val userMedHistory = medicationHistory.filter { it.userId == userId && (it.actionTime ?: it.scheduledDate) >= cutoffTimestamp }
        val userAppointments = appointments.filter { it.userId == userId }

        // Determine Data Completeness Status
        val totalRecords = userPredictions.size + userMeds.size + userMedHistory.size + userAppointments.size + (if (profile != null) 1 else 0)
        val completeness = when {
            totalRecords >= 4 && profile != null && profile.fullName != null -> ConsultationDataCompleteness.GOOD_DATA
            totalRecords >= 1 -> ConsultationDataCompleteness.PARTIAL_DATA
            else -> ConsultationDataCompleteness.INSUFFICIENT_DATA
        }

        // 1. Visit Overview Section
        val dateLabel = if (period.dayCount != null) {
            val fromDate = formatDateOnly(cutoffTimestamp)
            val toDate = formatDateOnly(currentTime)
            "$fromDate to $toDate (${period.displayName})"
        } else {
            "All Available Records (${period.displayName})"
        }

        val statusMsg = when (completeness) {
            ConsultationDataCompleteness.GOOD_DATA -> "Comprehensive consultation summary compiled from your recorded health history."
            ConsultationDataCompleteness.PARTIAL_DATA -> "Consultation summary compiled. Some health categories contain partial data."
            ConsultationDataCompleteness.INSUFFICIENT_DATA -> "Limited recorded history available for this period. General consultation baseline prepared."
        }

        val visitOverview = ConsultationVisitOverview(
            generationTimestamp = currentTime,
            period = period,
            dataPeriodLabel = dateLabel,
            totalRecordsCount = totalRecords,
            completenessStatus = completeness,
            statusMessage = statusMsg
        )

        // 2. Profile Section
        val profileSummary = ConsultationProfileSummary(
            fullName = profile?.fullName ?: "Not recorded",
            ageOrDob = profile?.dateOfBirth ?: "Not recorded",
            gender = profile?.gender ?: "Not recorded",
            bloodGroup = profile?.bloodGroup ?: "Not recorded",
            allergies = if (!profile?.allergies.isNullOrBlank()) profile?.allergies else "None recorded",
            existingConditions = if (!profile?.existingDiseases.isNullOrBlank()) profile?.existingDiseases else "None recorded",
            currentMedications = if (!profile?.currentMedications.isNullOrBlank()) profile?.currentMedications else "None recorded",
            notes = profile?.notes,
            isProfileComplete = profile != null && !profile.fullName.isNullOrBlank() && !profile.dateOfBirth.isNullOrBlank()
        )

        // 3. Symptoms Section
        val allSymptoms = userPredictions.flatMap { it.symptoms }.filter { it.isNotBlank() }
        val symptomCounts = allSymptoms.groupingBy { it.trim().lowercase() }.eachCount()
        val frequentSymptomsList = symptomCounts.entries.sortedByDescending { it.value }.map { "${it.key} (${it.value}x)" }

        val recentObservationsList = userPredictions.take(5).flatMap { pred ->
            pred.symptoms.filter { it.isNotBlank() }.map { symptom ->
                Pair(symptom, formatDateOnly(pred.predictionTimestamp))
            }
        }.take(8)

        val symptomsSummary = ConsultationSymptomsSummary(
            totalSymptomsCount = allSymptoms.size,
            frequentSymptoms = frequentSymptomsList,
            recentObservations = recentObservationsList
        )

        // 4. Predictions Section (Strict Non-Diagnostic Wording)
        val predictionItems = userPredictions.map { pred ->
            ConsultationPredictionItem(
                predictedCondition = pred.predictedDisease,
                confidencePercentage = (pred.confidence * 100).toInt(),
                predictionDate = formatDateOnly(pred.predictionTimestamp),
                reportedSymptoms = pred.symptoms.filter { it.isNotBlank() },
                modelVersion = pred.modelVersion
            )
        }
        val topCondition = userPredictions.groupBy { it.predictedDisease }.maxByOrNull { it.value.size }?.key

        val predictionsSummary = ConsultationPredictionsSummary(
            totalPredictionsCount = userPredictions.size,
            recentPredictions = predictionItems,
            mostFrequentCondition = topCondition
        )

        // 5. Medication & Adherence Section
        val medicationItems = userMeds.map { med ->
            ConsultationMedicationItem(
                name = med.medicineName,
                dosage = "${med.dosage} ${med.dosageUnit}".trim(),
                frequency = med.frequency,
                instructions = med.instructions.ifBlank { "As prescribed" },
                isActive = med.active
            )
        }

        val totalDoses = userMedHistory.size
        val takenDoses = userMedHistory.count { it.status.equals("TAKEN", ignoreCase = true) }
        val skippedOrMissed = userMedHistory.count {
            it.status.equals("SKIPPED", ignoreCase = true) || it.status.equals("MISSED", ignoreCase = true)
        }
        val adherencePct = if (totalDoses > 0) (takenDoses * 100 / totalDoses) else null
        val adherenceSummaryStr = when {
            totalDoses == 0 && userMeds.isEmpty() -> "No active medications recorded."
            totalDoses == 0 -> "${userMeds.size} active medication(s) scheduled. No dose logs recorded in this period."
            adherencePct != null -> "$takenDoses of $totalDoses logged doses taken ($adherencePct% adherence, $skippedOrMissed missed/skipped)."
            else -> "Medication schedule active."
        }

        val medicationsSummary = ConsultationMedicationSummary(
            activeMedications = medicationItems,
            adherenceSummary = adherenceSummaryStr,
            adherencePercentage = adherencePct,
            missedOrSkippedCount = skippedOrMissed
        )

        // 6. Appointments Section
        val upcoming = userAppointments.firstOrNull { it.status.equals("SCHEDULED", ignoreCase = true) && it.appointmentTimestamp >= currentTime }
        val pastCount = userAppointments.count { it.status.equals("COMPLETED", ignoreCase = true) || it.appointmentTimestamp < currentTime }

        val appointmentsSummary = ConsultationAppointmentSummary(
            upcomingAppointmentDoctor = upcoming?.doctorName,
            upcomingAppointmentDate = if (upcoming != null) "${upcoming.appointmentDate} at ${upcoming.appointmentTime}" else null,
            upcomingAppointmentClinic = upcoming?.clinicName,
            upcomingAppointmentType = upcoming?.appointmentType,
            recentPastAppointmentsCount = pastCount
        )

        // 7. Health Trends Section
        val detectedPatternsList = longitudinalSummary?.detectedPatterns?.map { it.description } ?: emptyList()
        val trendSymptomStr = if (frequentSymptomsList.isNotEmpty()) {
            "Top recorded symptoms: ${frequentSymptomsList.take(3).joinToString(", ")}."
        } else {
            "No recurring symptom trends detected in this period."
        }
        val trendAdherenceStr = if (adherencePct != null) {
            "Medication adherence is at $adherencePct% over the selected period."
        } else {
            "Adherence trend not available for this period."
        }

        val trendsSummary = ConsultationTrendsSummary(
            detectedPatterns = detectedPatternsList,
            symptomTrend = trendSymptomStr,
            adherenceTrend = trendAdherenceStr
        )

        // 8. Personal Context Section
        val contextSummaryStr = personalContext?.generatedSummary
            ?: "Personal health context compiled from your stored health records."
        val priorityNotice = if (riskAssessment != null && riskAssessment.hasSufficientData) {
            "Context-aware priority level: ${riskAssessment.riskLevel.label} (Score: ${riskAssessment.overallScore ?: 0}/100). This is an application indicator and not a medical diagnosis."
        } else {
            null
        }

        val contextSummary = ConsultationContextSummary(
            personalContextSummary = contextSummaryStr,
            contextualPriorityNotice = priorityNotice
        )

        // 9. Data Quality Section
        val dqNotices = mutableListOf<String>()
        if (profileSummary.fullName == "Not recorded") dqNotices.add("Profile name not recorded.")
        if (profileSummary.ageOrDob == "Not recorded") dqNotices.add("Date of birth not recorded.")
        if (profileSummary.bloodGroup == "Not recorded") dqNotices.add("Blood group not specified.")
        if (userPredictions.isEmpty()) dqNotices.add("No disease predictions or symptom logs in this period.")
        if (dataQualitySummary?.issues?.isNotEmpty() == true) {
            dqNotices.addAll(dataQualitySummary.issues.take(3).map { it.title })
        }

        val dataQuality = ConsultationDataQualitySummary(
            dataQualityStatus = dataQualitySummary?.status ?: HealthDataQualityStatus.GOOD,
            notices = dqNotices,
            hasMissingData = dqNotices.isNotEmpty()
        )

        // 10. Smart Question Suggestions
        val suggestedQuestions = QuestionSuggestionEngine.generateQuestions(
            profile = profile,
            predictions = userPredictions,
            medications = userMeds,
            medicationHistory = userMedHistory,
            appointments = userAppointments,
            longitudinalSummary = longitudinalSummary,
            dataQualitySummary = dataQualitySummary
        )

        return ConsultationSummary(
            visitOverview = visitOverview,
            profile = profileSummary,
            symptoms = symptomsSummary,
            predictions = predictionsSummary,
            medications = medicationsSummary,
            appointments = appointmentsSummary,
            trends = trendsSummary,
            context = contextSummary,
            dataQuality = dataQuality,
            suggestedQuestions = suggestedQuestions,
            customQuestions = customQuestions
        )
    }

    private fun formatDateOnly(timestamp: Long): String {
        return try {
            dateOnlyFormatter.format(Date(timestamp))
        } catch (e: Exception) {
            "Date: $timestamp"
        }
    }
}
