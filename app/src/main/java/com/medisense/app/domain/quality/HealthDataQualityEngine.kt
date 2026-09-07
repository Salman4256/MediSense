package com.medisense.app.domain.quality

import com.medisense.app.R
import com.medisense.app.data.local.entity.*
import com.medisense.app.domain.model.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Centralized, deterministic Health Data Quality Engine.
 * Evaluates all local health data across profile, medications, adherence history, appointments,
 * prediction history, temporal ordering, and sync readiness.
 *
 * NOTE: This engine performs DATA QUALITY evaluation only. It never performs medical diagnosis,
 * clinical risk assessment, or medication dosage validation.
 */
@Singleton
class HealthDataQualityEngine @Inject constructor(
    private val profileValidator: ProfileDataValidator,
    private val medicationValidator: MedicationDataValidator,
    private val appointmentValidator: AppointmentDataValidator,
    private val predictionValidator: PredictionDataValidator,
    private val temporalValidator: TemporalConsistencyValidator
) {

    fun evaluate(
        profile: HealthProfileEntity?,
        medications: List<MedicationEntity>,
        medicationHistory: List<MedicationHistoryEntity>,
        appointments: List<AppointmentEntity>,
        predictions: List<PredictionHistoryEntity>,
        syncMetadata: SyncMetadataEntity?
    ): HealthDataQualitySummary {

        // Check if there is sufficient user data to perform evaluation
        val hasAnyData = profile != null ||
                medications.isNotEmpty() ||
                medicationHistory.isNotEmpty() ||
                appointments.isNotEmpty() ||
                predictions.isNotEmpty()

        if (!hasAnyData) {
            return HealthDataQualitySummary(
                status = HealthDataQualityStatus.INSUFFICIENT_DATA,
                qualityScore = null,
                totalChecks = 0,
                passedChecks = 0,
                errorCount = 0,
                warningCount = 0,
                infoCount = 0,
                issues = emptyList(),
                categoryBreakdown = emptyMap(),
                evaluatedTimestamp = System.currentTimeMillis()
            )
        }

        val allIssues = mutableListOf<HealthDataQualityIssue>()
        var totalChecks = 0
        var passedChecks = 0

        // 1. Profile Validation
        val profileResult = profileValidator.validate(profile)
        totalChecks += profileResult.totalChecks
        passedChecks += profileResult.passedChecks
        allIssues.addAll(profileResult.issues)

        // 2. Medication Data Validation
        val medResult = medicationValidator.validate(medications, medicationHistory)
        totalChecks += medResult.totalChecks
        passedChecks += medResult.passedChecks
        allIssues.addAll(medResult.issues)

        // 3. Appointment Data Validation
        val aptResult = appointmentValidator.validate(appointments)
        totalChecks += aptResult.totalChecks
        passedChecks += aptResult.passedChecks
        allIssues.addAll(aptResult.issues)

        // 4. Prediction History Validation
        val predResult = predictionValidator.validate(predictions)
        totalChecks += predResult.totalChecks
        passedChecks += predResult.passedChecks
        allIssues.addAll(predResult.issues)

        // 5. Temporal Consistency Validation
        val tempResult = temporalValidator.validate(medications, appointments, predictions)
        totalChecks += tempResult.totalChecks
        passedChecks += tempResult.passedChecks
        allIssues.addAll(tempResult.issues)

        // 6. Sync Readiness & Checkpoint Checks
        totalChecks++
        if (syncMetadata != null && syncMetadata.failedRecordCount > 0) {
            allIssues.add(
                HealthDataQualityIssue(
                    id = "sync_failed_records",
                    category = HealthDataQualityCategory.SYNC_READINESS,
                    severity = HealthDataQualitySeverity.WARNING,
                    title = "Cloud Synchronization Pending Retries",
                    explanation = "${syncMetadata.failedRecordCount} health records failed to upload in the last sync attempt.",
                    affectedRecordType = "SyncMetadata",
                    suggestedCorrection = "Trigger manual cloud sync in Privacy & Security settings when online.",
                    navigationDestinationId = R.id.privacySecurityFragment
                )
            )
        } else {
            passedChecks++
        }

        // Calculate deterministic score percentage
        val score = if (totalChecks > 0) {
            ((passedChecks.toDouble() / totalChecks.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)
        } else {
            null
        }

        val errorCount = allIssues.count { it.severity == HealthDataQualitySeverity.ERROR }
        val warningCount = allIssues.count { it.severity == HealthDataQualitySeverity.WARNING }
        val infoCount = allIssues.count { it.severity == HealthDataQualitySeverity.INFO }

        val status = when {
            errorCount > 0 || (score != null && score < 75) -> HealthDataQualityStatus.NEEDS_ATTENTION
            warningCount > 0 || (score != null && score < 95) -> HealthDataQualityStatus.NEEDS_ATTENTION
            else -> HealthDataQualityStatus.GOOD
        }

        // Sort issues deterministically: ERROR -> WARNING -> INFO, then Category, then Title
        val sortedIssues = allIssues.sortedWith(
            compareBy<HealthDataQualityIssue> { issue ->
                when (issue.severity) {
                    HealthDataQualitySeverity.ERROR -> 0
                    HealthDataQualitySeverity.WARNING -> 1
                    HealthDataQualitySeverity.INFO -> 2
                }
            }.thenBy { it.category.name }.thenBy { it.title }
        )

        val breakdown = sortedIssues.groupBy { it.category }.mapValues { it.value.size }

        return HealthDataQualitySummary(
            status = status,
            qualityScore = score,
            totalChecks = totalChecks,
            passedChecks = passedChecks,
            errorCount = errorCount,
            warningCount = warningCount,
            infoCount = infoCount,
            issues = sortedIssues,
            categoryBreakdown = breakdown,
            evaluatedTimestamp = System.currentTimeMillis()
        )
    }
}
