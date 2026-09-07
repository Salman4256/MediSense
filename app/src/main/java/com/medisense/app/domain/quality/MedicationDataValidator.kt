package com.medisense.app.domain.quality

import com.medisense.app.R
import com.medisense.app.data.local.entity.MedicationEntity
import com.medisense.app.data.local.entity.MedicationHistoryEntity
import com.medisense.app.domain.model.HealthDataQualityCategory
import com.medisense.app.domain.model.HealthDataQualityIssue
import com.medisense.app.domain.model.HealthDataQualitySeverity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Validates medication records and adherence history for structural correctness and referential integrity.
 */
@Singleton
class MedicationDataValidator @Inject constructor() {

    fun validate(
        medications: List<MedicationEntity>,
        historyList: List<MedicationHistoryEntity>
    ): ValidationResult {
        val issues = mutableListOf<HealthDataQualityIssue>()
        var totalChecks = 0
        var passedChecks = 0

        val knownMedicationIds = medications.map { it.id }.toSet()

        // 1. Validate individual medication entities
        for (med in medications) {
            // Check medicine name
            totalChecks++
            if (med.medicineName.isBlank()) {
                issues.add(
                    HealthDataQualityIssue(
                        id = "med_empty_name_${med.id}",
                        category = HealthDataQualityCategory.MEDICATION_DATA,
                        severity = HealthDataQualitySeverity.ERROR,
                        title = "Missing Medicine Name",
                        explanation = "A medication record (ID: ${med.id}) exists with an empty medicine name.",
                        affectedRecordType = "Medication",
                        recordId = med.id.toString(),
                        suggestedCorrection = "Provide a valid medicine name or delete the invalid record.",
                        navigationDestinationId = R.id.medicationListFragment
                    )
                )
            } else {
                passedChecks++
            }

            // Check dosage
            totalChecks++
            if (med.dosage.isBlank()) {
                issues.add(
                    HealthDataQualityIssue(
                        id = "med_empty_dosage_${med.id}",
                        category = HealthDataQualityCategory.MEDICATION_DATA,
                        severity = HealthDataQualitySeverity.WARNING,
                        title = "Missing Dosage Information",
                        explanation = "Medication '${med.medicineName}' has no dosage value specified.",
                        affectedRecordType = "Medication",
                        recordId = med.id.toString(),
                        suggestedCorrection = "Add the prescribed dosage amount for ${med.medicineName}.",
                        navigationDestinationId = R.id.medicationListFragment
                    )
                )
            } else {
                passedChecks++
            }

            // Check start vs end date chronological relationship
            if (med.endDate != null) {
                totalChecks++
                if (med.endDate < med.startDate) {
                    issues.add(
                        HealthDataQualityIssue(
                            id = "med_invalid_dates_${med.id}",
                            category = HealthDataQualityCategory.MEDICATION_DATA,
                            severity = HealthDataQualitySeverity.ERROR,
                            title = "Invalid Medication Date Range",
                            explanation = "Medication '${med.medicineName}' has an end date earlier than its start date.",
                            affectedRecordType = "Medication",
                            recordId = med.id.toString(),
                            suggestedCorrection = "Correct the start or end date in the medication editor.",
                            navigationDestinationId = R.id.medicationListFragment
                        )
                    )
                } else {
                    passedChecks++
                }
            }

            // Check scheduled times
            totalChecks++
            if (med.active && med.scheduledTimes.isEmpty()) {
                issues.add(
                    HealthDataQualityIssue(
                        id = "med_empty_times_${med.id}",
                        category = HealthDataQualityCategory.MEDICATION_DATA,
                        severity = HealthDataQualitySeverity.WARNING,
                        title = "No Reminder Times Configured",
                        explanation = "Active medication '${med.medicineName}' has no daily reminder times set.",
                        affectedRecordType = "Medication",
                        recordId = med.id.toString(),
                        suggestedCorrection = "Add reminder times to receive automated medication alarms.",
                        navigationDestinationId = R.id.medicationListFragment
                    )
                )
            } else {
                passedChecks++
            }
        }

        // 2. Check for duplicate active medications
        val activeMeds = medications.filter { it.active }
        val duplicates = activeMeds
            .groupBy { it.medicineName.trim().lowercase() to it.frequency.uppercase() }
            .filter { it.value.size > 1 }

        for ((key, dupList) in duplicates) {
            totalChecks++
            val medName = dupList.first().medicineName
            issues.add(
                HealthDataQualityIssue(
                    id = "med_duplicate_${key.first}_${key.second}",
                    category = HealthDataQualityCategory.MEDICATION_DATA,
                    severity = HealthDataQualitySeverity.WARNING,
                    title = "Duplicate Active Medication",
                    explanation = "Multiple active schedules found for '$medName' with frequency '${key.second}'.",
                    affectedRecordType = "Medication",
                    recordId = dupList.map { it.id }.joinToString(","),
                    suggestedCorrection = "Review medication schedules and archive duplicate entries.",
                    navigationDestinationId = R.id.medicationListFragment
                )
            )
        }

        // 3. Check cross-record consistency between MedicationHistory and Medications
        val orphanedHistory = historyList.filter { it.medicationId !in knownMedicationIds && it.medicationId != 0L }
        if (orphanedHistory.isNotEmpty()) {
            totalChecks++
            issues.add(
                HealthDataQualityIssue(
                    id = "med_orphaned_history",
                    category = HealthDataQualityCategory.CROSS_RECORD_CONSISTENCY,
                    severity = HealthDataQualitySeverity.WARNING,
                    title = "Orphaned Medication Adherence Logs",
                    explanation = "${orphanedHistory.size} medication log entries reference deleted or non-existent medication schedules.",
                    affectedRecordType = "MedicationHistory",
                    suggestedCorrection = "Adherence history remains preserved for longitudinal analysis.",
                    navigationDestinationId = R.id.medicationHistoryFragment
                )
            )
        } else if (historyList.isNotEmpty()) {
            totalChecks++
            passedChecks++
        }

        return ValidationResult(totalChecks = totalChecks, passedChecks = passedChecks, issues = issues)
    }
}
