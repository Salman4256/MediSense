package com.medisense.app.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.medisense.app.data.local.dao.AppointmentDao
import com.medisense.app.data.local.dao.HealthProfileDao
import com.medisense.app.data.local.dao.MedicationDao
import com.medisense.app.data.local.dao.PredictionHistoryDao
import com.medisense.app.data.remote.supabase.AuthService
import com.medisense.app.domain.model.*
import com.medisense.app.domain.portability.PortableHealthDataValidator
import com.medisense.app.domain.security.SecureLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository orchestrating Portable Health Data Import, Validation & Safe Preview (Module 24).
 * Enforces strict 100% offline-first processing, local identity isolation, and zero premature
 * Room database mutation.
 */
@Singleton
class PortableImportRepository @Inject constructor(
    private val authService: AuthService,
    private val healthProfileDao: HealthProfileDao,
    private val medicationDao: MedicationDao,
    private val appointmentDao: AppointmentDao,
    private val predictionHistoryDao: PredictionHistoryDao,
    private val securityAuditRepository: SecurityAuditRepository
) {

    fun getCurrentUserId(): String {
        return authService.getCurrentUserId() ?: "offline_user"
    }

    /**
     * Reads a selected file via [ContentResolver], evaluates its schema and data validity,
     * checks for local database conflicts, and generates an in-memory [PortableImportPreview].
     */
    suspend fun validateFileUri(
        context: Context,
        uri: Uri
    ): Result<PortableImportPreview> = withContext(Dispatchers.IO) {
        try {
            val userId = getCurrentUserId()
            val fileName = queryFileName(context, uri) ?: "imported_health_data.json"

            // 1. Record import started audit event
            securityAuditRepository.recordEvent(
                eventType = SecurityAuditEventType.PORTABLE_IMPORT_STARTED,
                customDescription = "Portable health data import selected: $fileName"
            )

            // 2. Read stream safely with 10MB memory safety guard
            val rawJson = readTextFromUri(context, uri)

            // 3. Query existing local data for non-destructive conflict analysis
            val localProfile = healthProfileDao.getHealthProfile(userId)
            val localMedications = medicationDao.getAllMedicationsForUserSync(userId)
            val localAppointments = appointmentDao.getAllAppointmentsForUserSync(userId)
            val localPredictions = predictionHistoryDao.getAllPredictionHistoryForUserSync(userId)

            // 4. Execute multi-stage validation
            val preview = PortableHealthDataValidator.validateAndParse(
                rawJson = rawJson,
                localProfile = localProfile,
                localMedications = localMedications,
                localAppointments = localAppointments,
                localPredictions = localPredictions,
                fileName = fileName
            )

            // 5. Record validation completed audit telemetry
            securityAuditRepository.recordEvent(
                eventType = SecurityAuditEventType.PORTABLE_IMPORT_VALIDATED,
                customDescription = "Portable file validated (${preview.summary.totalResources} resources, ${preview.summary.errorCount} errors, ${preview.summary.warningCount} warnings)"
            )

            Result.success(preview)
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to validate imported file uri", e)
            Result.failure(e)
        }
    }

    /**
     * Prepares a validated in-memory import package ready for future staging upon explicit user confirmation.
     * Guaranteed NOT to write or overwrite Room database records in Module 24.
     */
    suspend fun prepareValidatedPackage(
        preview: PortableImportPreview,
        userConfirmed: Boolean
    ): Result<ValidatedImportPackage> = withContext(Dispatchers.IO) {
        try {
            if (!userConfirmed) {
                return@withContext Result.failure(IllegalStateException("User confirmation is mandatory before preparing the validated package."))
            }

            if (!preview.summary.isImportable) {
                return@withContext Result.failure(IllegalStateException("Cannot prepare package containing ${preview.summary.errorCount} blocking validation errors."))
            }

            val userId = getCurrentUserId()
            val packageId = preview.summary.packageId ?: "PKG-${System.currentTimeMillis()}"

            val validatedPackage = ValidatedImportPackage(
                packageId = packageId,
                validatedAtTimestamp = System.currentTimeMillis(),
                targetUserId = userId,
                readyCategories = preview.categories,
                totalValidRecords = preview.summary.validResources,
                sha256Fingerprint = preview.summary.sha256Fingerprint ?: "SHA256-UNAVAILABLE"
            )

            // Record security audit telemetry
            securityAuditRepository.recordEvent(
                eventType = SecurityAuditEventType.PORTABLE_IMPORT_PREPARED,
                customDescription = "Validated import package prepared in memory (${validatedPackage.totalValidRecords} records)"
            )

            Result.success(validatedPackage)
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to prepare validated import package", e)
            Result.failure(e)
        }
    }

    /**
     * Records local audit telemetry when the import inspection dialog is viewed.
     */
    suspend fun recordImportPreviewed(packageId: String?) = withContext(Dispatchers.IO) {
        try {
            securityAuditRepository.recordEvent(
                eventType = SecurityAuditEventType.PORTABLE_IMPORT_PREVIEWED,
                customDescription = "Portable health package preview viewed: ${packageId ?: "Unknown ID"}"
            )
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to log import previewed audit event", e)
        }
    }

    /**
     * Records local audit telemetry when the user dismisses or cancels the import workflow.
     */
    suspend fun recordImportCancelled(reason: String) = withContext(Dispatchers.IO) {
        try {
            securityAuditRepository.recordEvent(
                eventType = SecurityAuditEventType.PORTABLE_IMPORT_CANCELLED,
                customDescription = "Portable health data import cancelled: $reason"
            )
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to log import cancelled audit event", e)
        }
    }

    private fun readTextFromUri(context: Context, uri: Uri): String {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                val sb = StringBuilder()
                val buffer = CharArray(4096)
                var bytesRead: Int
                var totalRead = 0
                val maxChars = 10 * 1024 * 1024 // 10MB safety cap

                while (reader.read(buffer).also { bytesRead = it } != -1) {
                    sb.append(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (totalRead > maxChars) {
                        throw IllegalArgumentException("Import file size exceeds 10MB safety threshold.")
                    }
                }
                return sb.toString()
            }
        } ?: throw IllegalArgumentException("Unable to open input stream for the selected file.")
    }

    private fun queryFileName(context: Context, uri: Uri): String? {
        return try {
            var fileName: String? = null
            if (uri.scheme == "content") {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) {
                            fileName = cursor.getString(nameIndex)
                        }
                    }
                }
            }
            if (fileName.isNullOrBlank()) {
                fileName = uri.lastPathSegment
            }
            fileName
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val TAG = "PortableImportRepo"
    }
}
