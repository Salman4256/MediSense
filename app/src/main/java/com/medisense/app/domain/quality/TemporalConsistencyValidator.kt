package com.medisense.app.domain.quality

import com.medisense.app.R
import com.medisense.app.data.local.entity.AppointmentEntity
import com.medisense.app.data.local.entity.MedicationEntity
import com.medisense.app.data.local.entity.PredictionHistoryEntity
import com.medisense.app.domain.model.HealthDataQualityCategory
import com.medisense.app.domain.model.HealthDataQualityIssue
import com.medisense.app.domain.model.HealthDataQualitySeverity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Validates temporal consistency across multiple entity lifecycles, auditing timestamps,
 * and future-dated historical records.
 */
@Singleton
class TemporalConsistencyValidator @Inject constructor() {

    private val CLOCK_SKEW_TOLERANCE_MS = 300_000L // 5 minutes

    fun validate(
        medications: List<MedicationEntity>,
        appointments: List<AppointmentEntity>,
        predictions: List<PredictionHistoryEntity>,
        currentTimeMs: Long = System.currentTimeMillis()
    ): ValidationResult {
        val issues = mutableListOf<HealthDataQualityIssue>()
        var totalChecks = 0
        var passedChecks = 0

        // 1. Medication timestamps: updatedAt < createdAt
        for (med in medications) {
            if (med.createdAt > 0L && med.updatedAt > 0L) {
                totalChecks++
                if (med.updatedAt < med.createdAt) {
                    issues.add(
                        HealthDataQualityIssue(
                            id = "temp_med_updated_before_created_${med.id}",
                            category = HealthDataQualityCategory.TEMPORAL_CONSISTENCY,
                            severity = HealthDataQualitySeverity.ERROR,
                            title = "Temporal Inconsistency in Medication",
                            explanation = "Medication '${med.medicineName}' update timestamp precedes its creation timestamp.",
                            affectedRecordType = "Medication",
                            recordId = med.id.toString(),
                            suggestedCorrection = "Record lifecycle timestamps indicate clock reversal or sync mismatch.",
                            navigationDestinationId = R.id.medicationListFragment
                        )
                    )
                } else {
                    passedChecks++
                }
            }
        }

        // 2. Appointment timestamps: updatedAt < createdAt
        for (apt in appointments) {
            if (apt.createdAt > 0L && apt.updatedAt > 0L) {
                totalChecks++
                if (apt.updatedAt < apt.createdAt) {
                    issues.add(
                        HealthDataQualityIssue(
                            id = "temp_apt_updated_before_created_${apt.id}",
                            category = HealthDataQualityCategory.TEMPORAL_CONSISTENCY,
                            severity = HealthDataQualitySeverity.ERROR,
                            title = "Temporal Inconsistency in Appointment",
                            explanation = "Appointment with '${apt.doctorName}' update timestamp precedes its creation timestamp.",
                            affectedRecordType = "Appointment",
                            recordId = apt.id.toString(),
                            suggestedCorrection = "Check device clock and synchronization timestamps.",
                            navigationDestinationId = R.id.appointmentFragment
                        )
                    )
                } else {
                    passedChecks++
                }
            }
        }

        // 3. Prediction history timestamps cannot be in the future
        for (pred in predictions) {
            totalChecks++
            if (pred.predictionTimestamp > currentTimeMs + CLOCK_SKEW_TOLERANCE_MS) {
                issues.add(
                    HealthDataQualityIssue(
                        id = "temp_pred_future_${pred.id}",
                        category = HealthDataQualityCategory.TEMPORAL_CONSISTENCY,
                        severity = HealthDataQualitySeverity.ERROR,
                        title = "Future Prediction Timestamp",
                        explanation = "Prediction history entry '${pred.predictedDisease}' has a future timestamp.",
                        affectedRecordType = "PredictionHistory",
                        recordId = pred.id.toString(),
                        suggestedCorrection = "Historical analysis records cannot occur in the future.",
                        navigationDestinationId = R.id.predictionHistoryFragment
                    )
                )
            } else {
                passedChecks++
            }
        }

        return ValidationResult(totalChecks = totalChecks, passedChecks = passedChecks, issues = issues)
    }
}
