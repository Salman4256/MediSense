package com.medisense.app.domain.model

import java.io.File
import java.io.Serializable

/**
 * High-level data categories available for selective health data sharing.
 */
enum class SharingDataCategory(
    val categoryId: String,
    val displayName: String,
    val description: String,
    val iconResName: String,
    val defaultSelected: Boolean = true
) {
    PROFILE(
        categoryId = "PROFILE",
        displayName = "Personal Profile & Demographics",
        description = "Name, age, biological sex, blood group, and emergency contact details.",
        iconResName = "ic_person",
        defaultSelected = true
    ),
    CONDITIONS_ALLERGIES(
        categoryId = "CONDITIONS_ALLERGIES",
        displayName = "Medical Conditions & Allergies",
        description = "Recorded chronic health conditions, known allergies, and medical alerts.",
        iconResName = "ic_alert",
        defaultSelected = true
    ),
    MEDICATIONS(
        categoryId = "MEDICATIONS",
        displayName = "Medications & Adherence",
        description = "Active medication regimens, dosage, frequency, and historical adherence rates.",
        iconResName = "ic_medication",
        defaultSelected = true
    ),
    PREDICTIONS(
        categoryId = "PREDICTIONS",
        displayName = "Symptom & Assessment History",
        description = "Past symptom analyses, assistive predictions, confidence scores, and precautions.",
        iconResName = "ic_assessment",
        defaultSelected = false
    ),
    APPOINTMENTS(
        categoryId = "APPOINTMENTS",
        displayName = "Doctor Appointments",
        description = "Upcoming and past clinical appointments, doctor names, clinics, and notes.",
        iconResName = "ic_calendar",
        defaultSelected = false
    ),
    DECISION_TRACES(
        categoryId = "DECISION_TRACES",
        displayName = "Explainable Decision Traces",
        description = "AI decision traces, contributing factor breakdowns, and reasoning provenance.",
        iconResName = "ic_trace",
        defaultSelected = false
    ),
    LONGITUDINAL_SUMMARY(
        categoryId = "LONGITUDINAL_SUMMARY",
        displayName = "Longitudinal Trends & Patterns",
        description = "30-day health dynamics, recurring symptom patterns, and stability metrics.",
        iconResName = "ic_timeline",
        defaultSelected = false
    ),
    EMERGENCY_SUMMARY(
        categoryId = "EMERGENCY_SUMMARY",
        displayName = "Emergency Health Access Card",
        description = "Critical responder summary, primary contact, and vital emergency instructions.",
        iconResName = "ic_emergency",
        defaultSelected = true
    ),
    DATA_QUALITY_NOTE(
        categoryId = "DATA_QUALITY_NOTE",
        displayName = "Data Quality & Completeness Notice",
        description = "Local data validation status, completeness score, and recorded quality issues.",
        iconResName = "ic_verified",
        defaultSelected = true
    );

    companion object {
        fun fromId(id: String): SharingDataCategory? {
            return entries.find { it.categoryId.equals(id, ignoreCase = true) }
        }
    }
}

/**
 * Stated clinical or practical purpose for sharing health records.
 */
enum class SharingPurpose(val displayName: String, val description: String) {
    DOCTOR_CONSULTATION(
        displayName = "Doctor Consultation & Clinical Review",
        description = "For sharing with your physician, specialist, or healthcare provider prior to or during a consultation."
    ),
    CAREGIVER_SUPPORT(
        displayName = "Caregiver & Family Support",
        description = "For providing trusted family members or caregivers visibility into your care plan and active medications."
    ),
    EMERGENCY_PREPAREDNESS(
        displayName = "Emergency Readiness & Backup",
        description = "For keeping a critical offline emergency medical backup for first responders or travel."
    ),
    PERSONAL_ARCHIVE(
        displayName = "Personal Health Archive / Data Portability",
        description = "For your own personal health records, device transfer, or independent archive."
    );

    companion object {
        fun fromString(value: String): SharingPurpose {
            return runCatching { valueOf(value) }.getOrDefault(DOCTOR_CONSULTATION)
        }
    }
}

/**
 * Output export format selected for sharing.
 */
enum class SharingFormat(val displayName: String, val fileExtension: String, val mimeType: String) {
    JSON_PACKAGE(
        displayName = "Secure JSON Health Data Package (.json)",
        fileExtension = "json",
        mimeType = "application/json"
    ),
    PDF_DOCUMENT(
        displayName = "Caregiver / Doctor Summary Report (.pdf)",
        fileExtension = "pdf",
        mimeType = "application/pdf"
    ),
    BOTH(
        displayName = "Both JSON Package & PDF Report (.json + .pdf)",
        fileExtension = "zip",
        mimeType = "application/zip"
    );

    companion object {
        fun fromString(value: String): SharingFormat {
            return runCatching { valueOf(value) }.getOrDefault(PDF_DOCUMENT)
        }
    }
}

/**
 * Consent state for a shared health package record.
 */
enum class SharingConsentStatus(val displayName: String) {
    ACTIVE("Active / Exported"),
    REVOKED("Revoked by User"),
    EXPIRED("Expired");

    companion object {
        fun fromString(value: String): SharingConsentStatus {
            return runCatching { valueOf(value) }.getOrDefault(ACTIVE)
        }
    }
}

/**
 * Domain model representing a user's explicit consent grant for a health data export.
 */
data class HealthDataSharingConsent(
    val id: Long = 0,
    val userId: String,
    val purpose: SharingPurpose,
    val recipientLabel: String,
    val selectedCategories: List<SharingDataCategory>,
    val format: SharingFormat,
    val packageFingerprint: String,
    val status: SharingConsentStatus,
    val createdAt: Long,
    val revokedAt: Long? = null,
    val expiresAt: Long? = null,
    val disclosureAccepted: Boolean = true,
    val dataQualityAcknowledged: Boolean = true
) : Serializable

/**
 * User-configured scope for building an export package.
 */
data class HealthSharingScope(
    val purpose: SharingPurpose = SharingPurpose.DOCTOR_CONSULTATION,
    val recipientLabel: String = "",
    val selectedCategories: Set<SharingDataCategory> = setOf(
        SharingDataCategory.PROFILE,
        SharingDataCategory.CONDITIONS_ALLERGIES,
        SharingDataCategory.MEDICATIONS,
        SharingDataCategory.EMERGENCY_SUMMARY,
        SharingDataCategory.DATA_QUALITY_NOTE
    ),
    val format: SharingFormat = SharingFormat.PDF_DOCUMENT,
    val includeDecisionTraces: Boolean = false,
    val expirationDays: Int? = null
)

/**
 * Comprehensive, sanitized root JSON structure for exported health packages.
 */
data class HealthSharingPackage(
    val packageMetadata: PackageMetadata,
    val consentDeclaration: ConsentDeclaration,
    val personalProfile: SharedProfileSection? = null,
    val conditionsAndAllergies: SharedConditionsAllergiesSection? = null,
    val medicationsAndAdherence: SharedMedicationsSection? = null,
    val predictionHistory: SharedPredictionsSection? = null,
    val doctorAppointments: SharedAppointmentsSection? = null,
    val decisionTraces: SharedDecisionTracesSection? = null,
    val longitudinalSummary: SharedLongitudinalSection? = null,
    val emergencyAccessCard: SharedEmergencySection? = null,
    val dataQualityNotice: SharedDataQualitySection? = null,
    val safetyDisclaimer: String = "NON-DIAGNOSTIC NOTICE: This health data package is generated by MediSense for assistive and informative purposes only. It is not a certified medical diagnosis or clinical prescription. Always consult a qualified healthcare provider for clinical decisions."
) : Serializable

data class PackageMetadata(
    val packageId: String,
    val appName: String = "MediSense",
    val appVersion: String = "1.0",
    val schemaVersion: String = "2.0",
    val generatedAtTimestamp: Long,
    val generatedAtIso: String,
    val sha256Fingerprint: String,
    val totalCategoriesIncluded: Int
) : Serializable

data class ConsentDeclaration(
    val consentedByUserIdHash: String,
    val purpose: String,
    val recipientLabel: String,
    val consentedAtIso: String,
    val grantedCategories: List<String>,
    val exportFormat: String,
    val isRevocable: Boolean = true,
    val revocationNotice: String = "Consent records can be revoked locally in MediSense to stop future exports. Revocation does not delete copies already shared externally."
) : Serializable

data class SharedProfileSection(
    val fullName: String,
    val age: Int?,
    val dateOfBirth: String?,
    val gender: String?,
    val bloodGroup: String?,
    val emergencyContactName: String?,
    val emergencyContactPhone: String?
) : Serializable

data class SharedConditionsAllergiesSection(
    val conditions: List<String>,
    val allergies: List<String>,
    val notes: String?
) : Serializable

data class SharedMedicationsSection(
    val totalActiveMedications: Int,
    val overallAdherencePercentage: Int?,
    val activeMedications: List<SharedMedicationItem>,
    val recentDoseHistory: List<SharedAdherenceItem>
) : Serializable

data class SharedMedicationItem(
    val medicineName: String,
    val dosage: String,
    val frequency: String,
    val instructions: String,
    val startDate: String?,
    val endDate: String?
) : Serializable

data class SharedAdherenceItem(
    val medicineName: String,
    val scheduledDate: String,
    val scheduledTime: String,
    val status: String,
    val actionTime: String?
) : Serializable

data class SharedPredictionsSection(
    val totalAssessments: Int,
    val assessments: List<SharedPredictionItem>
) : Serializable

data class SharedPredictionItem(
    val dateIso: String,
    val reportedSymptoms: List<String>,
    val primaryAssessment: String,
    val confidencePercentage: Int,
    val riskLevel: String,
    val recommendedPrecautions: List<String>
) : Serializable

data class SharedAppointmentsSection(
    val totalAppointments: Int,
    val appointments: List<SharedAppointmentItem>
) : Serializable

data class SharedAppointmentItem(
    val doctorName: String,
    val clinicName: String,
    val appointmentType: String,
    val appointmentDate: String,
    val appointmentTime: String,
    val status: String,
    val notes: String?
) : Serializable

data class SharedDecisionTracesSection(
    val totalTraces: Int,
    val traces: List<SharedDecisionTraceItem>
) : Serializable

data class SharedDecisionTraceItem(
    val traceId: String,
    val decisionType: String,
    val title: String,
    val summary: String,
    val generatedAtIso: String,
    val confidenceScore: Float?,
    val contributingFactors: List<SharedTraceFactor>,
    val transparencyNotice: String
) : Serializable

data class SharedTraceFactor(
    val factorName: String,
    val weightDescription: String,
    val observation: String
) : Serializable

data class SharedLongitudinalSection(
    val analysisPeriod: String,
    val recurringSymptoms: List<String>,
    val detectedPatterns: List<String>,
    val stabilitySummary: String
) : Serializable

data class SharedEmergencySection(
    val emergencyContactName: String,
    val emergencyContactPhone: String,
    val bloodGroup: String,
    val criticalAllergies: List<String>,
    val emergencyInstructions: String
) : Serializable

data class SharedDataQualitySection(
    val overallQualityStatus: String,
    val completenessScore: Int?,
    val checksPassed: Int,
    val totalChecks: Int,
    val qualityIssues: List<String>,
    val noticeText: String
) : Serializable

/**
 * Result of a package export operation.
 */
sealed class HealthSharingExportResult {
    data class Success(
        val consent: HealthDataSharingConsent,
        val jsonFile: File? = null,
        val pdfFile: File? = null,
        val exportedFiles: List<File>,
        val previewSummary: String
    ) : HealthSharingExportResult()

    data class Error(val message: String, val throwable: Throwable? = null) : HealthSharingExportResult()
}
