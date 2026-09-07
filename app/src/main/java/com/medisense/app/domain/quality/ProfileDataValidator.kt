package com.medisense.app.domain.quality

import com.medisense.app.R
import com.medisense.app.data.local.entity.HealthProfileEntity
import com.medisense.app.domain.model.HealthDataQualityCategory
import com.medisense.app.domain.model.HealthDataQualityIssue
import com.medisense.app.domain.model.HealthDataQualitySeverity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Validates the user's local health profile for completeness, formatting, and physical validity.
 */
@Singleton
class ProfileDataValidator @Inject constructor() {

    fun validate(profile: HealthProfileEntity?): ValidationResult {
        val issues = mutableListOf<HealthDataQualityIssue>()
        var totalChecks = 0
        var passedChecks = 0

        if (profile == null) {
            totalChecks++
            issues.add(
                HealthDataQualityIssue(
                    id = "prof_missing_all",
                    category = HealthDataQualityCategory.PROFILE_COMPLETENESS,
                    severity = HealthDataQualitySeverity.WARNING,
                    title = "Profile Not Configured",
                    explanation = "Basic health profile has not been created yet.",
                    affectedRecordType = "HealthProfile",
                    suggestedCorrection = "Complete your health profile to enable accurate personalization and analytics.",
                    navigationDestinationId = R.id.healthRecordFragment
                )
            )
            return ValidationResult(totalChecks = totalChecks, passedChecks = passedChecks, issues = issues)
        }

        // 1. Full Name check
        totalChecks++
        if (profile.fullName.isNullOrBlank()) {
            issues.add(
                HealthDataQualityIssue(
                    id = "prof_missing_fullname",
                    category = HealthDataQualityCategory.PROFILE_COMPLETENESS,
                    severity = HealthDataQualitySeverity.INFO,
                    title = "Full Name Missing",
                    explanation = "Profile is missing your name.",
                    affectedRecordType = "HealthProfile",
                    suggestedCorrection = "Add your full name in your health profile.",
                    navigationDestinationId = R.id.healthRecordFragment
                )
            )
        } else {
            passedChecks++
        }

        // 2. Date of Birth check
        totalChecks++
        if (profile.dateOfBirth.isNullOrBlank()) {
            issues.add(
                HealthDataQualityIssue(
                    id = "prof_missing_dob",
                    category = HealthDataQualityCategory.PROFILE_COMPLETENESS,
                    severity = HealthDataQualitySeverity.INFO,
                    title = "Date of Birth Missing",
                    explanation = "Date of birth is not provided in your profile.",
                    affectedRecordType = "HealthProfile",
                    suggestedCorrection = "Add your date of birth to calculate age-adjusted health metrics.",
                    navigationDestinationId = R.id.healthRecordFragment
                )
            )
        } else {
            // Check DOB validity and format
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
            try {
                val parsedDate = sdf.parse(profile.dateOfBirth.trim())
                if (parsedDate != null && parsedDate.after(Date())) {
                    issues.add(
                        HealthDataQualityIssue(
                            id = "prof_future_dob",
                            category = HealthDataQualityCategory.PROFILE_VALIDITY,
                            severity = HealthDataQualitySeverity.ERROR,
                            title = "Invalid Date of Birth",
                            explanation = "Date of birth (${profile.dateOfBirth}) is set in the future.",
                            affectedRecordType = "HealthProfile",
                            suggestedCorrection = "Update your date of birth to a valid historical date.",
                            navigationDestinationId = R.id.healthRecordFragment
                        )
                    )
                } else {
                    passedChecks++
                }
            } catch (e: Exception) {
                issues.add(
                    HealthDataQualityIssue(
                        id = "prof_malformed_dob",
                        category = HealthDataQualityCategory.PROFILE_VALIDITY,
                        severity = HealthDataQualitySeverity.ERROR,
                        title = "Malformed Date of Birth",
                        explanation = "Date of birth '${profile.dateOfBirth}' does not match standard YYYY-MM-DD format.",
                        affectedRecordType = "HealthProfile",
                        suggestedCorrection = "Correct the date of birth formatting in Profile.",
                        navigationDestinationId = R.id.healthRecordFragment
                    )
                )
            }
        }

        // 3. Gender check
        totalChecks++
        if (profile.gender.isNullOrBlank()) {
            issues.add(
                HealthDataQualityIssue(
                    id = "prof_missing_gender",
                    category = HealthDataQualityCategory.PROFILE_COMPLETENESS,
                    severity = HealthDataQualitySeverity.INFO,
                    title = "Gender Not Specified",
                    explanation = "Gender information is missing from your health profile.",
                    affectedRecordType = "HealthProfile",
                    suggestedCorrection = "Select your gender in Profile to support risk calculations.",
                    navigationDestinationId = R.id.healthRecordFragment
                )
            )
        } else {
            passedChecks++
        }

        // 4. Blood Group check
        totalChecks++
        if (profile.bloodGroup.isNullOrBlank()) {
            issues.add(
                HealthDataQualityIssue(
                    id = "prof_missing_blood_group",
                    category = HealthDataQualityCategory.PROFILE_COMPLETENESS,
                    severity = HealthDataQualitySeverity.INFO,
                    title = "Blood Group Missing",
                    explanation = "Blood group is not recorded in your profile.",
                    affectedRecordType = "HealthProfile",
                    suggestedCorrection = "Add your blood group to complete your clinical background.",
                    navigationDestinationId = R.id.healthRecordFragment
                )
            )
        } else {
            passedChecks++
        }

        // 5. Height physical validity
        if (profile.height != null) {
            totalChecks++
            if (profile.height <= 0.0) {
                issues.add(
                    HealthDataQualityIssue(
                        id = "prof_invalid_height",
                        category = HealthDataQualityCategory.PROFILE_VALIDITY,
                        severity = HealthDataQualitySeverity.ERROR,
                        title = "Invalid Height Value",
                        explanation = "Height is recorded as ${profile.height} cm, which is not physically valid.",
                        affectedRecordType = "HealthProfile",
                        suggestedCorrection = "Enter a valid positive height measurement.",
                        navigationDestinationId = R.id.healthRecordFragment
                    )
                )
            } else {
                passedChecks++
            }
        }

        // 6. Weight physical validity
        if (profile.weight != null) {
            totalChecks++
            if (profile.weight <= 0.0) {
                issues.add(
                    HealthDataQualityIssue(
                        id = "prof_invalid_weight",
                        category = HealthDataQualityCategory.PROFILE_VALIDITY,
                        severity = HealthDataQualitySeverity.ERROR,
                        title = "Invalid Weight Value",
                        explanation = "Weight is recorded as ${profile.weight} kg, which is not physically valid.",
                        affectedRecordType = "HealthProfile",
                        suggestedCorrection = "Enter a valid positive weight measurement.",
                        navigationDestinationId = R.id.healthRecordFragment
                    )
                )
            } else {
                passedChecks++
            }
        }

        // 7. Emergency Contact consistency
        val hasContactName = !profile.emergencyContactName.isNullOrBlank()
        val hasContactNumber = !profile.emergencyContactNumber.isNullOrBlank()

        if (hasContactName != hasContactNumber) {
            totalChecks++
            if (hasContactName && !hasContactNumber) {
                issues.add(
                    HealthDataQualityIssue(
                        id = "prof_incomplete_contact_phone",
                        category = HealthDataQualityCategory.PROFILE_COMPLETENESS,
                        severity = HealthDataQualitySeverity.WARNING,
                        title = "Emergency Contact Phone Missing",
                        explanation = "An emergency contact name was provided without a corresponding phone number.",
                        affectedRecordType = "HealthProfile",
                        suggestedCorrection = "Add the phone number for '${profile.emergencyContactName}'.",
                        navigationDestinationId = R.id.healthRecordFragment
                    )
                )
            } else {
                issues.add(
                    HealthDataQualityIssue(
                        id = "prof_incomplete_contact_name",
                        category = HealthDataQualityCategory.PROFILE_COMPLETENESS,
                        severity = HealthDataQualitySeverity.WARNING,
                        title = "Emergency Contact Name Missing",
                        explanation = "An emergency phone number was provided without a contact person name.",
                        affectedRecordType = "HealthProfile",
                        suggestedCorrection = "Add the name of the emergency contact person.",
                        navigationDestinationId = R.id.healthRecordFragment
                    )
                )
            }
        } else if (hasContactName && hasContactNumber) {
            totalChecks++
            passedChecks++
        }

        return ValidationResult(totalChecks = totalChecks, passedChecks = passedChecks, issues = issues)
    }
}
