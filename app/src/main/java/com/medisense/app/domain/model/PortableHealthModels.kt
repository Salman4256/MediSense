package com.medisense.app.domain.model

import java.io.File
import java.io.Serializable

/**
 * Enumeration of all selectable data categories available for portable health data export.
 */
enum class PortableDataCategory(
    val categoryId: String,
    val displayName: String,
    val resourceTypeHint: String,
    val description: String,
    val defaultSelected: Boolean = true
) {
    BASIC_PROFILE(
        categoryId = "BASIC_PROFILE",
        displayName = "Patient Profile & Demographics",
        resourceTypeHint = "Patient",
        description = "Name, age, date of birth, biological sex, blood group, height, and weight.",
        defaultSelected = true
    ),
    EMERGENCY_INFORMATION(
        categoryId = "EMERGENCY_INFORMATION",
        displayName = "Emergency Health Information",
        resourceTypeHint = "MediSenseEmergencyCard",
        description = "Primary emergency contact name, emergency phone, critical alerts, and notes.",
        defaultSelected = true
    ),
    ALLERGIES(
        categoryId = "ALLERGIES",
        displayName = "Allergies & Intolerances",
        resourceTypeHint = "AllergyIntolerance",
        description = "Recorded medication, environmental, and food allergies.",
        defaultSelected = true
    ),
    HEALTH_CONDITIONS(
        categoryId = "HEALTH_CONDITIONS",
        displayName = "Medical & Chronic Conditions",
        resourceTypeHint = "Condition",
        description = "Recorded chronic diseases and baseline medical conditions.",
        defaultSelected = true
    ),
    MEDICATIONS(
        categoryId = "MEDICATIONS",
        displayName = "Prescriptions & Medications",
        resourceTypeHint = "MedicationStatement",
        description = "Active medication schedules, dosages, frequencies, and instructions.",
        defaultSelected = true
    ),
    MEDICATION_ADHERENCE(
        categoryId = "MEDICATION_ADHERENCE",
        displayName = "Medication Adherence History",
        resourceTypeHint = "MediSenseAdherenceHistory",
        description = "Historical dose log records (taken, skipped, missed) and adherence percentage.",
        defaultSelected = true
    ),
    APPOINTMENTS(
        categoryId = "APPOINTMENTS",
        displayName = "Clinical Appointments & Encounters",
        resourceTypeHint = "Appointment",
        description = "Upcoming and completed doctor appointments, clinics, and encounter notes.",
        defaultSelected = false
    ),
    PREDICTION_HISTORY(
        categoryId = "PREDICTION_HISTORY",
        displayName = "Symptom Assessment History",
        resourceTypeHint = "MediSensePrediction",
        description = "Past AI symptom assessments, assistive model inferences, confidence, and XAI summaries.",
        defaultSelected = false
    ),
    HEALTH_TRENDS(
        categoryId = "HEALTH_TRENDS",
        displayName = "Longitudinal Health Dynamics",
        resourceTypeHint = "MediSenseLongitudinalTrend",
        description = "30-day temporal trends, recurring symptom trajectories, and stability metrics.",
        defaultSelected = false
    ),
    PERSONAL_CONTEXT(
        categoryId = "PERSONAL_CONTEXT",
        displayName = "Personal Health Context",
        resourceTypeHint = "MediSenseContext",
        description = "Adaptive personalization synthesis and profile completeness metrics.",
        defaultSelected = false
    ),
    DATA_QUALITY(
        categoryId = "DATA_QUALITY",
        displayName = "Data Quality & Integrity Assessment",
        resourceTypeHint = "MediSenseDataQuality",
        description = "Completeness scores, multi-domain integrity check results, and data notices.",
        defaultSelected = true
    ),
    PERSONALIZED_GUIDANCE(
        categoryId = "PERSONALIZED_GUIDANCE",
        displayName = "Personalized Health Guidance",
        resourceTypeHint = "MediSenseGuidance",
        description = "Application-generated health management suggestions and follow-up prompts.",
        defaultSelected = false
    ),
    CONSULTATION_SUMMARY(
        categoryId = "CONSULTATION_SUMMARY",
        displayName = "Doctor Consultation Prep Summaries",
        resourceTypeHint = "MediSenseConsultationSummary",
        description = "Structured appointment preparation summaries and physician discussion topics.",
        defaultSelected = false
    ),
    DECISION_TRACES(
        categoryId = "DECISION_TRACES",
        displayName = "Explainable Decision Traces",
        resourceTypeHint = "MediSenseDecisionTrace",
        description = "AI decision traces, contributing factor breakdowns, and reasoning provenance.",
        defaultSelected = false
    ),
    HEALTH_TIMELINE(
        categoryId = "HEALTH_TIMELINE",
        displayName = "Unified Health Journey Timeline",
        resourceTypeHint = "MediSenseTimelineEvent",
        description = "Normalized chronological health journey milestones and logging events.",
        defaultSelected = false
    ),
    RCHR_STATE(
        categoryId = "RCHR_STATE",
        displayName = "Composite Health Representation Matrix",
        resourceTypeHint = "MediSenseRchr",
        description = "Reversible Composite Health Representation (RCHR) 8-domain vector metrics.",
        defaultSelected = false
    );

    companion object {
        fun fromId(id: String): PortableDataCategory? {
            return entries.find { it.categoryId.equals(id, ignoreCase = true) }
        }
    }
}

/**
 * Top-level interoperability container formatted as a FHIR-inspired JSON Collection Bundle.
 */
data class PortableHealthRecordBundle(
    val resourceType: String = "Bundle",
    val type: String = "collection",
    val meta: PortablePackageMetadata,
    val interoperabilityStatement: String = "MediSense Portable Health Record — interoperability-oriented export. This export is not a certified clinical EHR/FHIR implementation.",
    val safetyDisclaimer: String = "This portable record contains information recorded or generated by MediSense. It is intended to support personal health management and communication. It is not a certified medical record and does not replace professional medical advice.",
    val entry: List<PortableBundleEntry>
) : Serializable

data class PortablePackageMetadata(
    val packageId: String,
    val appName: String = "MediSense",
    val appVersion: String = "1.0",
    val schemaVersion: String = "1.0-interop",
    val exportVersion: String = "1.0",
    val generatedAtTimestamp: Long,
    val generatedAtIso: String,
    val sha256Fingerprint: String,
    val totalResourceCount: Int,
    val selectedCategories: List<String>
) : Serializable

data class PortableBundleEntry(
    val fullUrl: String,
    val resource: PortableResource
) : Serializable

/**
 * Base polymorphic envelope for standardized interoperability resources.
 */
sealed class PortableResource(
    val resourceType: String,
    val id: String,
    val status: String = "final",
    val sourceProvenance: String = "MediSense Application"
) : Serializable

// ==========================================
// Standard FHIR-Inspired Resource DTOs
// ==========================================

data class PortablePatientResource(
    val resourceId: String,
    val name: String?,
    val birthDate: String?, // yyyy-MM-dd
    val age: Int?,
    val gender: String?,
    val bloodGroup: String?,
    val emergencyContact: PortableEmergencyContact?
) : PortableResource(resourceType = "Patient", id = resourceId)

data class PortableEmergencyContact(
    val name: String?,
    val telecom: String?,
    val relationship: String?
) : Serializable

data class PortableObservationResource(
    val resourceId: String,
    val code: String,
    val display: String,
    val value: String,
    val unit: String?,
    val effectiveDateTime: String?
) : PortableResource(resourceType = "Observation", id = resourceId)

data class PortableAllergyIntoleranceResource(
    val resourceId: String,
    val substance: String,
    val category: String,
    val clinicalStatus: String = "active",
    val recordedDate: String?
) : PortableResource(resourceType = "AllergyIntolerance", id = resourceId)

data class PortableConditionResource(
    val resourceId: String,
    val conditionName: String,
    val clinicalStatus: String = "active",
    val verificationStatus: String = "unconfirmed",
    val note: String?
) : PortableResource(resourceType = "Condition", id = resourceId)

data class PortableMedicationStatementResource(
    val resourceId: String,
    val medicationName: String,
    val dosageText: String?,
    val frequency: String?,
    val instructions: String?,
    val effectivePeriodStart: String?,
    val effectivePeriodEnd: String?,
    val isActive: Boolean,
    val provenanceNotice: String = "Recorded by user in MediSense; not a confirmed prescription."
) : PortableResource(resourceType = "MedicationStatement", id = resourceId)

data class PortableMedicationAdherenceResource(
    val resourceId: String,
    val totalDosesLogged: Int,
    val takenCount: Int,
    val missedCount: Int,
    val skippedCount: Int,
    val adherencePercentage: Int?,
    val adherenceCategory: String,
    val recentLogEntries: List<PortableAdherenceLogItem>
) : PortableResource(resourceType = "MediSenseAdherenceHistory", id = resourceId)

data class PortableAdherenceLogItem(
    val medicineName: String,
    val scheduledDate: String,
    val scheduledTime: String,
    val status: String,
    val actionTimeIso: String?
) : Serializable

data class PortableAppointmentResource(
    val resourceId: String,
    val practitionerName: String,
    val serviceProvider: String,
    val appointmentType: String,
    val startDateTime: String,
    val appointmentStatus: String,
    val comment: String?
) : PortableResource(resourceType = "Appointment", id = resourceId)

// ==========================================
// MediSense Application-Specific Extensions
// (Explicitly Non-Diagnostic)
// ==========================================

data class MediSensePredictionResource(
    val resourceId: String,
    val predictedDisease: String,
    val modelConfidence: Float,
    val modelVersion: String,
    val assessedAtIso: String,
    val reportedSymptoms: List<String>,
    val explanationSummary: String?,
    val nonDiagnosticNotice: String = "MediSense model-generated prediction; not a confirmed diagnosis."
) : PortableResource(resourceType = "MediSensePrediction", id = resourceId)

data class MediSenseTrendResource(
    val resourceId: String,
    val analysisPeriod: String,
    val recurringSymptoms: List<String>,
    val detectedPatterns: List<String>,
    val trendSummary: String,
    val nonDiagnosticNotice: String = "Historical pattern detected from available MediSense records."
) : PortableResource(resourceType = "MediSenseLongitudinalTrend", id = resourceId)

data class MediSenseContextResource(
    val resourceId: String,
    val profileCompletenessScore: Int,
    val activeMedicationsCount: Int,
    val recentAssessmentsCount: Int,
    val upcomingAppointmentsCount: Int,
    val contextSummary: String,
    val personalizationRationale: String,
    val nonDiagnosticNotice: String = "Personal health context synthesized from available records."
) : PortableResource(resourceType = "MediSenseContext", id = resourceId)

data class MediSenseRiskResource(
    val resourceId: String,
    val priorityLevel: String,
    val priorityScore: Int?,
    val primaryContributingFactors: List<String>,
    val riskSummary: String,
    val nonDiagnosticNotice: String = "Application-defined contextual indicator; not clinically validated."
) : PortableResource(resourceType = "MediSenseRiskPriority", id = resourceId)

data class MediSenseGuidanceResource(
    val resourceId: String,
    val guidanceTitle: String,
    val guidanceMessage: String,
    val category: String,
    val priority: String,
    val rationale: String,
    val nonDiagnosticNotice: String = "Health-management support only; not a treatment plan."
) : PortableResource(resourceType = "MediSenseGuidance", id = resourceId)

data class MediSenseRchrResource(
    val resourceId: String,
    val rchrVersion: String,
    val encodedFeaturesCount: Int,
    val completenessPercentage: Int,
    val stabilityIndex: String,
    val nonDiagnosticNotice: String = "Composite health vector summary for state preservation."
) : PortableResource(resourceType = "MediSenseRchr", id = resourceId)

data class MediSenseDecisionTraceResource(
    val resourceId: String,
    val decisionType: String,
    val engineName: String,
    val primaryOutput: String,
    val factorAttributions: List<PortableTraceFactorItem>,
    val executionPipelineSteps: List<String>,
    val explanationSummary: String,
    val transparencyNotice: String
) : PortableResource(resourceType = "MediSenseDecisionTrace", id = resourceId)

data class PortableTraceFactorItem(
    val factorName: String,
    val weightDescription: String,
    val interpretation: String
) : Serializable

data class MediSenseTimelineResource(
    val resourceId: String,
    val eventType: String,
    val title: String,
    val description: String,
    val timestampIso: String
) : PortableResource(resourceType = "MediSenseTimelineEvent", id = resourceId)

data class MediSenseDataQualityResource(
    val resourceId: String,
    val overallStatus: String,
    val qualityScore: Int?,
    val totalChecks: Int,
    val passedChecks: Int,
    val qualityNotices: List<String>
) : PortableResource(resourceType = "MediSenseDataQuality", id = resourceId)

data class MediSenseEmergencyCardResource(
    val resourceId: String,
    val patientName: String,
    val bloodGroup: String,
    val emergencyContactName: String,
    val emergencyContactPhone: String,
    val criticalAllergies: List<String>,
    val specialInstructions: String
) : PortableResource(resourceType = "MediSenseEmergencyCard", id = resourceId)

// ==========================================
// Operational / Export Flow Models
// ==========================================

data class PortableExportScope(
    val selectedCategories: Set<PortableDataCategory> = setOf(
        PortableDataCategory.BASIC_PROFILE,
        PortableDataCategory.EMERGENCY_INFORMATION,
        PortableDataCategory.ALLERGIES,
        PortableDataCategory.HEALTH_CONDITIONS,
        PortableDataCategory.MEDICATIONS,
        PortableDataCategory.MEDICATION_ADHERENCE,
        PortableDataCategory.DATA_QUALITY
    ),
    val userConfirmationAccepted: Boolean = false
)

data class PortableExportPreview(
    val packageId: String,
    val totalCategoriesSelected: Int,
    val estimatedResourceCount: Int,
    val categoryBreakdown: Map<PortableDataCategory, Int>,
    val qualitySummary: HealthDataQualitySummary?,
    val previewJsonSample: String
)

sealed class PortableExportResult {
    data class Success(
        val file: File,
        val resourceCount: Int,
        val sha256Fingerprint: String,
        val packageId: String,
        val previewSummary: String
    ) : PortableExportResult()

    data class Error(val message: String, val throwable: Throwable? = null) : PortableExportResult()
}
