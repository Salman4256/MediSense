package com.medisense.app.domain.model

import java.io.Serializable

/**
 * Status phases for the portable health data import and validation flow.
 */
enum class PortableImportStatus {
    IDLE,
    SELECTING_FILE,
    PARSING,
    VALIDATING,
    READY,
    READY_WITH_WARNINGS,
    BLOCKED,
    PREPARING,
    COMPLETED,
    ERROR
}

/**
 * Severity level of an issue detected during portable health data import validation.
 */
enum class PortableImportIssueSeverity {
    INFO,
    WARNING,
    ERROR
}

/**
 * Categorization of validation issues detected during portable file parsing and validation.
 */
enum class PortableImportIssueType {
    INVALID_JSON,
    UNSUPPORTED_SCHEMA_VERSION,
    MISSING_METADATA,
    INVALID_RESOURCE,
    MISSING_REQUIRED_FIELD,
    INVALID_DATE,
    INVALID_RESOURCE_TYPE,
    DUPLICATE_RESOURCE,
    INCONSISTENT_USER_CONTEXT,
    UNSUPPORTED_RESOURCE,
    MALFORMED_VALUE,
    EMPTY_IMPORT,
    SECURITY_VALIDATION_FAILURE,
    CHECKSUM_MISMATCH,
    FILE_SIZE_LIMIT_EXCEEDED
}

/**
 * Specific validation finding detailing the affected component and suggested resolution.
 */
data class PortableImportIssue(
    val issueId: String,
    val type: PortableImportIssueType,
    val severity: PortableImportIssueSeverity,
    val message: String,
    val affectedResource: String? = null,
    val suggestedAction: String? = null
) : Serializable

/**
 * Type of conflict detected when comparing imported data against local storage.
 */
enum class ImportConflictType {
    NO_CONFLICT,
    POSSIBLE_DUPLICATE,
    VALUE_CONFLICT,
    UNSUPPORTED_MERGE
}

/**
 * Individual data discrepancy item detected between imported resources and local database records.
 */
data class ImportConflictItem(
    val category: PortableDataCategory,
    val description: String,
    val conflictType: ImportConflictType,
    val localValue: String? = null,
    val importedValue: String? = null
) : Serializable

/**
 * Individual category inspection preview summarizing records, status, and parsed fields.
 */
data class PortableImportCategoryPreview(
    val category: PortableDataCategory,
    val displayName: String,
    val resourceType: String,
    val recordCount: Int,
    val statusText: String,
    val hasWarnings: Boolean,
    val hasErrors: Boolean,
    val summaryLines: List<String>,
    val parsedResources: List<PortableResource> = emptyList()
) : Serializable

/**
 * Top-level package metrics and metadata extracted from the portable JSON file.
 */
data class PortableImportSummary(
    val fileName: String,
    val schemaVersion: String,
    val exportVersion: String,
    val appName: String,
    val generatedAtIso: String?,
    val packageId: String?,
    val sha256Fingerprint: String?,
    val totalResources: Int,
    val validResources: Int,
    val warningCount: Int,
    val errorCount: Int,
    val isImportable: Boolean
) : Serializable

/**
 * Comprehensive in-memory preview passed to the presentation layer.
 */
data class PortableImportPreview(
    val summary: PortableImportSummary,
    val categories: List<PortableImportCategoryPreview>,
    val issues: List<PortableImportIssue>,
    val conflicts: List<ImportConflictItem>,
    val rawBundle: PortableHealthRecordBundle?
) : Serializable

/**
 * Staged, validated import package prepared after explicit user confirmation.
 * Does NOT write to Room database directly in Module 24.
 */
data class ValidatedImportPackage(
    val packageId: String,
    val validatedAtTimestamp: Long,
    val targetUserId: String,
    val readyCategories: List<PortableImportCategoryPreview>,
    val totalValidRecords: Int,
    val sha256Fingerprint: String,
    val safetyNotice: String = "Validated in-memory package prepared for safe staging. Local database unchanged."
) : Serializable

/**
 * Result envelope of a portable import preparation action.
 */
sealed class PortableImportResult {
    data class Success(
        val packageId: String,
        val validatedPackage: ValidatedImportPackage
    ) : PortableImportResult()

    data class Error(
        val message: String,
        val issues: List<PortableImportIssue> = emptyList(),
        val throwable: Throwable? = null
    ) : PortableImportResult()
}
