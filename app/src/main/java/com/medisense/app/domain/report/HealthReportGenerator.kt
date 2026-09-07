package com.medisense.app.domain.report

import com.medisense.app.data.local.entity.AppointmentEntity
import com.medisense.app.data.local.entity.HealthProfileEntity
import com.medisense.app.data.local.entity.MedicationEntity
import com.medisense.app.data.local.entity.MedicationHistoryEntity
import com.medisense.app.data.local.entity.PredictionHistoryEntity
import com.medisense.app.data.local.entity.SyncMetadataEntity
import com.medisense.app.domain.model.*
import com.medisense.app.domain.rchr.RchrRepresentation
import com.medisense.app.utils.MedicationDateTimeUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Central deterministic generator that aggregates outputs from Modules 1–16
 * into a unified, medically safe, user-isolated [HealthReport].
 *
 * NOTE: Operates strictly offline without ML model re-inference or remote network requests.
 */
object HealthReportGenerator {

    private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    private val DATE_ONLY_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun generate(
        profile: HealthProfileEntity?,
        medications: List<MedicationEntity>,
        medicationHistory: List<MedicationHistoryEntity>,
        appointments: List<AppointmentEntity>,
        predictions: List<PredictionHistoryEntity>,
        syncMetadata: SyncMetadataEntity?,
        dataQuality: HealthDataQualitySummary,
        personalContext: PersonalHealthContext,
        longitudinalSummary: LongitudinalHealthSummary,
        rchr: RchrRepresentation,
        riskAssessment: ContextualRiskAssessment,
        guidanceResult: GuidanceEngineResult
    ): HealthReport {

        val metadata = HealthReportMetadata(
            title = "MediSense Comprehensive Personal Health Report",
            reportVersion = "1.0",
            generatedTimestamp = System.currentTimeMillis(),
            appVersion = "1.0",
            format = "In-App / PDF"
        )

        val reportProfile = buildProfileSection(profile)
        val reportDataQuality = buildDataQualitySection(dataQuality)
        val reportPredictions = buildPredictionSection(predictions)
        val reportMedications = buildMedicationSection(medications, medicationHistory)
        val reportAppointments = buildAppointmentSection(appointments)
        val reportTrends = buildTrendSection(longitudinalSummary)
        val reportContext = buildContextSection(personalContext)
        val reportRchr = buildRchrSection(rchr)
        val reportRisk = buildRiskSection(riskAssessment)
        val reportGuidance = buildGuidanceSection(guidanceResult)
        val reportSync = buildSyncSection(syncMetadata)

        return HealthReport(
            metadata = metadata,
            profile = reportProfile,
            dataQuality = reportDataQuality,
            predictions = reportPredictions,
            medications = reportMedications,
            appointments = reportAppointments,
            trends = reportTrends,
            context = reportContext,
            rchr = reportRchr,
            riskPriority = reportRisk,
            guidance = reportGuidance,
            syncStatus = reportSync
        )
    }

    private fun buildProfileSection(profile: HealthProfileEntity?): HealthReportProfile {
        if (profile == null) {
            return HealthReportProfile(
                fullName = null,
                dateOfBirth = null,
                gender = null,
                bloodGroup = null,
                heightCm = null,
                weightKg = null,
                bmi = null,
                allergies = null,
                existingDiseases = null,
                currentMedications = null,
                familyHistory = null,
                emergencyContactName = null,
                emergencyContactNumber = null,
                notes = null,
                completenessPercentage = 0,
                incompleteFields = listOf(
                    "Full Name", "Date of Birth", "Gender", "Blood Group",
                    "Height", "Weight", "Emergency Contact"
                )
            )
        }

        val incompleteList = mutableListOf<String>()
        var filledCount = 0
        val totalTracked = 10

        if (!profile.fullName.isNullOrBlank()) filledCount++ else incompleteList.add("Full Name")
        if (!profile.dateOfBirth.isNullOrBlank()) filledCount++ else incompleteList.add("Date of Birth")
        if (!profile.gender.isNullOrBlank()) filledCount++ else incompleteList.add("Gender")
        if (!profile.bloodGroup.isNullOrBlank()) filledCount++ else incompleteList.add("Blood Group")
        if (profile.height != null && profile.height > 0.0) filledCount++ else incompleteList.add("Height")
        if (profile.weight != null && profile.weight > 0.0) filledCount++ else incompleteList.add("Weight")
        if (!profile.allergies.isNullOrBlank()) filledCount++ else incompleteList.add("Allergies")
        if (!profile.existingDiseases.isNullOrBlank()) filledCount++ else incompleteList.add("Existing Diseases")
        if (!profile.currentMedications.isNullOrBlank()) filledCount++ else incompleteList.add("Current Medications")
        if (!profile.emergencyContactName.isNullOrBlank() || !profile.emergencyContactNumber.isNullOrBlank()) {
            filledCount++
        } else {
            incompleteList.add("Emergency Contact")
        }

        val completeness = (filledCount.toDouble() / totalTracked.toDouble() * 100.0).roundToInt()

        val bmi = if (profile.height != null && profile.height > 0.0 && profile.weight != null && profile.weight > 0.0) {
            val hM = profile.height / 100.0
            (profile.weight / (hM * hM) * 10.0).roundToInt() / 10.0
        } else null

        return HealthReportProfile(
            fullName = profile.fullName,
            dateOfBirth = profile.dateOfBirth,
            gender = profile.gender,
            bloodGroup = profile.bloodGroup,
            heightCm = profile.height,
            weightKg = profile.weight,
            bmi = bmi,
            allergies = profile.allergies,
            existingDiseases = profile.existingDiseases,
            currentMedications = profile.currentMedications,
            familyHistory = profile.familyHistory,
            emergencyContactName = profile.emergencyContactName,
            emergencyContactNumber = profile.emergencyContactNumber,
            notes = profile.notes,
            completenessPercentage = completeness,
            incompleteFields = incompleteList
        )
    }

    private fun buildDataQualitySection(dataQuality: HealthDataQualitySummary): HealthReportDataQuality {
        return HealthReportDataQuality(
            status = dataQuality.status,
            qualityScore = dataQuality.qualityScore,
            totalChecks = dataQuality.totalChecks,
            passedChecks = dataQuality.passedChecks,
            errorCount = dataQuality.errorCount,
            warningCount = dataQuality.warningCount,
            infoCount = dataQuality.infoCount,
            unresolvedIssues = dataQuality.issues
        )
    }

    private fun buildPredictionSection(predictions: List<PredictionHistoryEntity>): HealthReportPredictionSummary {
        val sorted = predictions.sortedByDescending { it.predictionTimestamp }
        val items = sorted.map { entity ->
            val confPct = (entity.confidence * 100).roundToInt().coerceIn(0, 100)
            val confLevel = when {
                confPct >= 80 -> "High Confidence"
                confPct >= 50 -> "Moderate Confidence"
                else -> "Low Confidence"
            }
            val dateStr = runCatching { DATE_FORMAT.format(Date(entity.predictionTimestamp)) }
                .getOrDefault("Unknown Date")

            val uncertainty = when {
                entity.symptoms.size < 2 -> "Few symptoms provided; multiple conditions share similar initial presentations."
                confPct >= 80 -> "Strong relative pattern alignment with known symptom profiles."
                confPct >= 50 -> "Moderate pattern alignment; consider discussing alternative possibilities with a clinician."
                else -> "Diffuse pattern overlap; model output is non-decisive."
            }

            HealthReportPredictionItem(
                id = entity.id,
                predictedDisease = entity.predictedDisease,
                confidencePercentage = confPct,
                confidenceLevel = confLevel,
                predictionDate = dateStr,
                modelVersion = entity.modelVersion,
                symptoms = entity.symptoms,
                explanationSummary = entity.explanationSummary,
                uncertaintyInterpretation = uncertainty
            )
        }

        val mostFrequent = if (predictions.isNotEmpty()) {
            predictions.groupBy { it.predictedDisease }
                .maxByOrNull { it.value.size }?.key
        } else null

        val avgConf = if (predictions.isNotEmpty()) {
            (predictions.map { it.confidence }.average() * 100).roundToInt().coerceIn(0, 100)
        } else null

        return HealthReportPredictionSummary(
            totalPredictions = predictions.size,
            recentPredictions = items,
            mostFrequentCondition = mostFrequent,
            averageConfidencePercentage = avgConf
        )
    }

    private fun buildMedicationSection(
        medications: List<MedicationEntity>,
        history: List<MedicationHistoryEntity>
    ): HealthReportMedicationSummary {
        val active = medications.filter { it.active }
        val activeItems = active.map { med ->
            val startStr = runCatching { DATE_ONLY_FORMAT.format(Date(med.startDate)) }.getOrDefault("N/A")
            val endStr = med.endDate?.let { runCatching { DATE_ONLY_FORMAT.format(Date(it)) }.getOrNull() }
            HealthReportMedicationItem(
                id = med.id,
                medicineName = med.medicineName,
                dosage = "${med.dosage} ${med.dosageUnit}".trim(),
                frequency = med.frequency,
                scheduledTimes = med.scheduledTimes,
                startDate = startStr,
                endDate = endStr,
                instructions = med.instructions,
                isActive = med.active
            )
        }

        val stats = MedicationDateTimeUtils.calculateAdherence(history)
        val adherenceRating = when {
            history.isEmpty() -> "No Adherence Logs Recorded"
            stats.percentage >= 90f -> "Excellent (≥90%)"
            stats.percentage >= 75f -> "Good (75–89%)"
            stats.percentage >= 50f -> "Moderate (50–74%)"
            else -> "Needs Attention (<50%)"
        }

        return HealthReportMedicationSummary(
            activeMedicationsCount = active.size,
            totalMedicationsCount = medications.size,
            activeMedications = activeItems,
            totalDosesLogged = stats.totalScheduled,
            takenDosesCount = stats.takenCount,
            skippedDosesCount = stats.skippedCount,
            missedDosesCount = stats.missedCount,
            adherencePercentage = if (history.isNotEmpty()) stats.percentage.roundToInt() else null,
            adherenceRating = adherenceRating
        )
    }

    private fun buildAppointmentSection(appointments: List<AppointmentEntity>): HealthReportAppointmentSummary {
        val now = System.currentTimeMillis()
        val sorted = appointments.sortedBy { it.appointmentTimestamp }

        val upcomingList = mutableListOf<HealthReportAppointmentItem>()
        val pastList = mutableListOf<HealthReportAppointmentItem>()

        for (apt in sorted) {
            val dateStr = apt.appointmentDate.ifBlank {
                runCatching { DATE_ONLY_FORMAT.format(Date(apt.appointmentTimestamp)) }.getOrDefault("N/A")
            }
            val item = HealthReportAppointmentItem(
                id = apt.id,
                doctorName = apt.doctorName,
                clinicName = apt.clinicName,
                appointmentType = apt.appointmentType,
                appointmentDate = dateStr,
                appointmentTime = apt.appointmentTime,
                reminderMinutesBefore = apt.reminderMinutesBefore,
                status = apt.status,
                notes = apt.notes
            )
            if (apt.appointmentTimestamp >= now && !apt.status.equals("CANCELLED", ignoreCase = true)) {
                upcomingList.add(item)
            } else {
                pastList.add(item)
            }
        }

        return HealthReportAppointmentSummary(
            totalAppointments = appointments.size,
            upcomingAppointments = upcomingList,
            pastAppointments = pastList.reversed() // Most recent past appointments first
        )
    }

    private fun buildTrendSection(longitudinalSummary: LongitudinalHealthSummary): HealthReportTrendSummary {
        val topSymptoms = longitudinalSummary.recurringSymptoms
            .sortedByDescending { it.occurrenceCount }
            .take(5)
            .map { "${it.symptomName} (${it.occurrenceCount} recorded)" }

        val patterns = longitudinalSummary.detectedPatterns.map { pattern ->
            "${pattern.title}: ${pattern.description}"
        }

        return HealthReportTrendSummary(
            periodLabel = longitudinalSummary.period.displayName,
            predictionFrequencyTrend = "${longitudinalSummary.predictionActivity.direction.name.replace('_', ' ')} (${longitudinalSummary.predictionActivity.currentPeriodCount} predictions in window)",
            topRecurringSymptoms = topSymptoms,
            confidenceTrend = longitudinalSummary.confidenceTrend.direction.name.replace('_', ' '),
            adherenceTrend = longitudinalSummary.adherenceTrend.direction.name.replace('_', ' '),
            appointmentActivity = "${longitudinalSummary.appointmentActivity.upcomingCount} upcoming, ${longitudinalSummary.appointmentActivity.currentPeriodCount} in window",
            detectedPatterns = patterns
        )
    }

    private fun buildContextSection(
        context: PersonalHealthContext
    ): HealthReportContextSummary {
        val demo = buildString {
            if (context.demographics.age != null) append("${context.demographics.age} years old, ")
            if (!context.demographics.gender.isNullOrBlank()) append("${context.demographics.gender}, ")
            if (!context.demographics.bloodGroup.isNullOrBlank()) append("Blood Group: ${context.demographics.bloodGroup}, ")
            if (context.demographics.bmi != null) append("BMI: ${context.demographics.bmi}")
        }.trimEnd(',', ' ')

        val chronicStr = if (context.chronicConditions.conditions.isNotEmpty()) {
            context.chronicConditions.conditions.joinToString(", ")
        } else {
            "No chronic conditions reported"
        }

        val allergyStr = if (context.allergies.allergiesList.isNotEmpty()) {
            context.allergies.allergiesList.joinToString(", ")
        } else {
            "No known allergies reported"
        }

        val medStr = "${context.medications.activeCount} active medication schedule(s)"
        val adhStr = if (context.medications.adherencePercentage != null) {
            "${context.medications.adherencePercentage.roundToInt()}% 30-day adherence rate"
        } else {
            "No adherence logs in recent period"
        }

        val aptStr = "${context.appointments.upcomingCount} scheduled appointment(s)"

        return HealthReportContextSummary(
            profileCompleteness = context.profileCompleteness,
            demographicSummary = if (demo.isNotBlank()) demo else "Demographic details not fully specified",
            chronicConditionsSummary = chronicStr,
            allergySummary = allergyStr,
            activeMedicationSummary = medStr,
            adherenceContext = adhStr,
            upcomingAppointmentContext = aptStr
        )
    }

    private fun buildRchrSection(rchr: RchrRepresentation): HealthReportRchrSummary {
        return HealthReportRchrSummary(
            rchrVersion = rchr.representationVersion,
            encodedCategories = listOf("Demographics", "Vitals/Physical", "Clinical Context", "Medication Adherence", "Temporal Dynamics"),
            activeFeatureGroupsCount = 5,
            reconstructionConsistencyScore = rchr.completenessPercentage
        )
    }

    private fun buildRiskSection(riskAssessment: ContextualRiskAssessment): HealthReportRiskSummary {
        val factors = riskAssessment.contributingFactors.map { factor ->
            "${factor.title}: ${factor.description}"
        }
        return HealthReportRiskSummary(
            priorityLevel = riskAssessment.riskLevel,
            priorityScore = riskAssessment.overallScore,
            hasSufficientData = riskAssessment.hasSufficientData,
            contributingFactors = factors,
            plainLanguageExplanation = riskAssessment.generatedSummary
        )
    }

    private fun buildGuidanceSection(guidanceResult: GuidanceEngineResult): HealthReportGuidanceSummary {
        val items = guidanceResult.guidanceList.map { item ->
            HealthReportGuidanceItem(
                category = item.category,
                priority = item.priority,
                title = item.title,
                actionableGuidance = item.message,
                clinicalReason = item.explanation,
                sourceModule = item.sources.joinToString(", ")
            )
        }
        return HealthReportGuidanceSummary(
            totalGuidanceCount = items.size,
            items = items
        )
    }

    private fun buildSyncSection(syncMetadata: SyncMetadataEntity?): HealthReportSyncSummary {
        return HealthReportSyncSummary(
            syncStatus = syncMetadata?.lastSyncStatus ?: "LOCAL_ONLY",
            lastSyncTimestamp = syncMetadata?.lastSyncTimestamp,
            pendingRecordsCount = syncMetadata?.pendingUploadCount ?: 0,
            isOfflineMode = syncMetadata == null || syncMetadata.lastSyncTimestamp == 0L
        )
    }
}
