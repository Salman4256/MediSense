package com.medisense.app.domain.quality

import com.medisense.app.R
import com.medisense.app.data.local.entity.AppointmentEntity
import com.medisense.app.domain.model.HealthDataQualityCategory
import com.medisense.app.domain.model.HealthDataQualityIssue
import com.medisense.app.domain.model.HealthDataQualitySeverity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Validates doctor appointment records for completeness, timing sanity, and duplicate prevention.
 */
@Singleton
class AppointmentDataValidator @Inject constructor() {

    fun validate(appointments: List<AppointmentEntity>): ValidationResult {
        val issues = mutableListOf<HealthDataQualityIssue>()
        var totalChecks = 0
        var passedChecks = 0

        for (apt in appointments) {
            // Check Doctor Name
            totalChecks++
            if (apt.doctorName.isBlank()) {
                issues.add(
                    HealthDataQualityIssue(
                        id = "apt_missing_doctor_${apt.id}",
                        category = HealthDataQualityCategory.APPOINTMENT_DATA,
                        severity = HealthDataQualitySeverity.ERROR,
                        title = "Missing Doctor Name",
                        explanation = "Appointment (ID: ${apt.id}) does not specify the doctor's name.",
                        affectedRecordType = "Appointment",
                        recordId = apt.id.toString(),
                        suggestedCorrection = "Add doctor or provider name to the appointment record.",
                        navigationDestinationId = R.id.appointmentFragment
                    )
                )
            } else {
                passedChecks++
            }

            // Check Clinic/Hospital Name
            totalChecks++
            if (apt.clinicName.isBlank()) {
                issues.add(
                    HealthDataQualityIssue(
                        id = "apt_missing_clinic_${apt.id}",
                        category = HealthDataQualityCategory.APPOINTMENT_DATA,
                        severity = HealthDataQualitySeverity.WARNING,
                        title = "Missing Clinic Location",
                        explanation = "Appointment with '${apt.doctorName}' has no clinic/hospital name specified.",
                        affectedRecordType = "Appointment",
                        recordId = apt.id.toString(),
                        suggestedCorrection = "Add clinic or hospital location.",
                        navigationDestinationId = R.id.appointmentFragment
                    )
                )
            } else {
                passedChecks++
            }

            // Check Appointment Timestamp
            totalChecks++
            if (apt.appointmentTimestamp <= 0L) {
                issues.add(
                    HealthDataQualityIssue(
                        id = "apt_invalid_time_${apt.id}",
                        category = HealthDataQualityCategory.APPOINTMENT_DATA,
                        severity = HealthDataQualitySeverity.ERROR,
                        title = "Invalid Appointment Time",
                        explanation = "Appointment with '${apt.doctorName}' contains an invalid date/time timestamp.",
                        affectedRecordType = "Appointment",
                        recordId = apt.id.toString(),
                        suggestedCorrection = "Edit the appointment date and time.",
                        navigationDestinationId = R.id.appointmentFragment
                    )
                )
            } else {
                passedChecks++
            }

            // Check Reminder Configuration
            totalChecks++
            if (apt.reminderMinutesBefore < 0) {
                issues.add(
                    HealthDataQualityIssue(
                        id = "apt_invalid_reminder_${apt.id}",
                        category = HealthDataQualityCategory.APPOINTMENT_DATA,
                        severity = HealthDataQualitySeverity.WARNING,
                        title = "Invalid Reminder Interval",
                        explanation = "Reminder time for appointment with '${apt.doctorName}' is negative.",
                        affectedRecordType = "Appointment",
                        recordId = apt.id.toString(),
                        suggestedCorrection = "Set reminder to a valid non-negative number of minutes.",
                        navigationDestinationId = R.id.appointmentFragment
                    )
                )
            } else {
                passedChecks++
            }
        }

        // Duplicate appointments detection
        val scheduledAppointments = appointments.filter { it.status == "SCHEDULED" }
        val duplicates = scheduledAppointments
            .groupBy { it.doctorName.trim().lowercase() to it.appointmentTimestamp }
            .filter { it.value.size > 1 }

        for ((key, dupList) in duplicates) {
            totalChecks++
            val doctor = dupList.first().doctorName
            issues.add(
                HealthDataQualityIssue(
                    id = "apt_duplicate_${key.first}_${key.second}",
                    category = HealthDataQualityCategory.APPOINTMENT_DATA,
                    severity = HealthDataQualitySeverity.WARNING,
                    title = "Duplicate Scheduled Appointment",
                    explanation = "Multiple appointments found for '$doctor' at the exact same scheduled time.",
                    affectedRecordType = "Appointment",
                    recordId = dupList.map { it.id }.joinToString(","),
                    suggestedCorrection = "Review upcoming appointments and remove duplicate entries.",
                    navigationDestinationId = R.id.appointmentFragment
                )
            )
        }

        return ValidationResult(totalChecks = totalChecks, passedChecks = passedChecks, issues = issues)
    }
}
