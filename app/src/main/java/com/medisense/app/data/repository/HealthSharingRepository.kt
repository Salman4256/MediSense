package com.medisense.app.data.repository

import android.content.Context
import com.medisense.app.data.local.dao.*
import com.medisense.app.data.local.entity.HealthSharingConsentEntity
import com.medisense.app.data.remote.supabase.AuthService
import com.medisense.app.domain.model.*
import com.medisense.app.domain.security.SecureLogger
import com.medisense.app.domain.sharing.HealthSharingPackageBuilder
import com.medisense.app.domain.sharing.HealthSharingPdfGenerator
import com.medisense.app.utils.MedicationDateTimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository orchestrating Consent-Controlled Caregiver / Doctor Health Data Sharing (Module 22).
 * Operates strictly 100% offline-first, local-only without remote cloud storage uploads,
 * and enforces strict user isolation by Supabase Auth UUID.
 */
@Singleton
class HealthSharingRepository @Inject constructor(
    private val authService: AuthService,
    private val consentDao: HealthSharingConsentDao,
    private val healthProfileDao: HealthProfileDao,
    private val medicationDao: MedicationDao,
    private val medicationHistoryDao: MedicationHistoryDao,
    private val appointmentDao: AppointmentDao,
    private val predictionHistoryDao: PredictionHistoryDao,
    private val emergencyHealthCardRepository: EmergencyHealthCardRepository,
    private val healthDecisionTraceRepository: HealthDecisionTraceRepository,
    private val longitudinalHealthRepository: LongitudinalHealthRepository,
    private val healthDataQualityRepository: HealthDataQualityRepository,
    private val securityAuditRepository: SecurityAuditRepository
) {

    fun getCurrentUserId(): String {
        return authService.getCurrentUserId() ?: "offline-user"
    }

    /**
     * Observes local consent records for the currently authenticated user.
     */
    fun observeConsents(): Flow<List<HealthDataSharingConsent>> {
        val userId = getCurrentUserId()
        return consentDao.observeConsentsForUser(userId)
            .map { list ->
                list.map { entity ->
                    HealthDataSharingConsent(
                        id = entity.id,
                        userId = entity.userId,
                        purpose = SharingPurpose.fromString(entity.purpose),
                        recipientLabel = entity.recipientLabel,
                        selectedCategories = entity.selectedCategories.mapNotNull { SharingDataCategory.fromId(it) },
                        format = SharingFormat.fromString(entity.format),
                        packageFingerprint = entity.packageFingerprint,
                        status = SharingConsentStatus.fromString(entity.status),
                        createdAt = entity.createdAt,
                        revokedAt = entity.revokedAt,
                        expiresAt = entity.expiresAt,
                        disclosureAccepted = entity.disclosureAccepted,
                        dataQualityAcknowledged = entity.dataQualityAcknowledged
                    )
                }
            }
            .flowOn(Dispatchers.IO)
    }

    /**
     * Builds a sanitized [HealthSharingPackage] in-memory for previewing and consent validation.
     */
    suspend fun prepareSharingPackage(scope: HealthSharingScope): HealthSharingPackage = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()

        val profile = healthProfileDao.getHealthProfile(userId)
        val medications = medicationDao.getAllMedicationsForUserSync(userId)
        val medicationHistory = medicationHistoryDao.getAllHistoryForUserSync(userId)
        val adherenceStats = MedicationDateTimeUtils.calculateAdherence(medicationHistory)
        val predictions = predictionHistoryDao.getAllPredictionHistoryForUserSync(userId)
        val appointments = appointmentDao.getAllAppointmentsForUserSync(userId)
        val decisionTraces = healthDecisionTraceRepository.observeDecisionTraces().firstOrNull().orEmpty()
        val longitudinal = longitudinalHealthRepository.observeLongitudinalSummary(AnalysisPeriod.DAYS_30).firstOrNull()
        val emergencyCard = emergencyHealthCardRepository.getEmergencyHealthCard(userId)
        val qualitySummary = healthDataQualityRepository.evaluateDataQuality()

        val pkg = HealthSharingPackageBuilder.buildPackage(
            userId = userId,
            scope = scope,
            profile = profile,
            medications = medications,
            medicationHistory = medicationHistory,
            adherenceStats = adherenceStats,
            predictions = predictions,
            appointments = appointments,
            decisionTraces = decisionTraces,
            longitudinal = longitudinal,
            emergencyCard = emergencyCard,
            qualitySummary = qualitySummary
        )

        // Log audit event for package preparation
        securityAuditRepository.recordEvent(
            eventType = SecurityAuditEventType.HEALTH_DATA_SHARE_PREPARED,
            customDescription = "Prepared health sharing package ${pkg.packageMetadata.packageId} for ${scope.purpose.name}"
        )

        pkg
    }

    /**
     * Commits explicit user consent, records the consent entity in Room,
     * renders the requested JSON/PDF files locally in cacheDir/shares/, and returns export result.
     */
    suspend fun grantConsentAndExport(
        context: Context,
        scope: HealthSharingScope,
        pkg: HealthSharingPackage
    ): Result<HealthSharingExportResult.Success> = withContext(Dispatchers.IO) {
        try {
            val userId = getCurrentUserId()
            val now = System.currentTimeMillis()
            val expiresAt = scope.expirationDays?.let { days ->
                now + (days * 24 * 60 * 60 * 1000L)
            }

            val consentEntity = HealthSharingConsentEntity(
                userId = userId,
                purpose = scope.purpose.name,
                recipientLabel = scope.recipientLabel.ifBlank { "Designated Recipient" },
                selectedCategories = scope.selectedCategories.map { it.categoryId },
                format = scope.format.name,
                packageFingerprint = pkg.packageMetadata.sha256Fingerprint,
                status = SharingConsentStatus.ACTIVE.name,
                createdAt = now,
                revokedAt = null,
                expiresAt = expiresAt,
                disclosureAccepted = true,
                dataQualityAcknowledged = true
            )

            val consentId = consentDao.insertConsent(consentEntity)
            val consentDomain = HealthDataSharingConsent(
                id = consentId,
                userId = userId,
                purpose = scope.purpose,
                recipientLabel = consentEntity.recipientLabel,
                selectedCategories = scope.selectedCategories.toList(),
                format = scope.format,
                packageFingerprint = consentEntity.packageFingerprint,
                status = SharingConsentStatus.ACTIVE,
                createdAt = now,
                revokedAt = null,
                expiresAt = expiresAt,
                disclosureAccepted = true,
                dataQualityAcknowledged = true
            )

            // Log consent granted audit event
            securityAuditRepository.recordEvent(
                eventType = SecurityAuditEventType.HEALTH_DATA_SHARE_CONSENTED,
                customDescription = "Consent granted for sharing package ${pkg.packageMetadata.packageId} to ${consentEntity.recipientLabel}"
            )

            val exportedFiles = mutableListOf<File>()
            var jsonFile: File? = null
            var pdfFile: File? = null

            if (scope.format == SharingFormat.JSON_PACKAGE || scope.format == SharingFormat.BOTH) {
                val jf = HealthSharingPackageBuilder.writeJsonPackageToFile(context, pkg)
                jsonFile = jf
                exportedFiles.add(jf)
            }

            if (scope.format == SharingFormat.PDF_DOCUMENT || scope.format == SharingFormat.BOTH) {
                when (val pdfResult = HealthSharingPdfGenerator.generatePdf(context, pkg)) {
                    is HealthReportExportResult.Success -> {
                        pdfFile = pdfResult.file
                        exportedFiles.add(pdfResult.file)
                    }
                    is HealthReportExportResult.Error -> {
                        throw IllegalStateException("Failed to render PDF report: ${pdfResult.message}")
                    }
                }
            }

            // Log export audit event
            securityAuditRepository.recordEvent(
                eventType = SecurityAuditEventType.HEALTH_DATA_SHARE_EXPORTED,
                customDescription = "Exported sharing package ${pkg.packageMetadata.packageId} (${scope.format.name})"
            )

            val previewSummary = HealthSharingPackageBuilder.generatePreviewSummary(pkg)

            Result.success(
                HealthSharingExportResult.Success(
                    consent = consentDomain,
                    jsonFile = jsonFile,
                    pdfFile = pdfFile,
                    exportedFiles = exportedFiles,
                    previewSummary = previewSummary
                )
            )
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to grant consent and export package", e)
            Result.failure(e)
        }
    }

    /**
     * Revokes a consent record locally in the database.
     */
    suspend fun revokeConsent(consentId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val userId = getCurrentUserId()
            val now = System.currentTimeMillis()
            consentDao.updateConsentStatus(consentId, userId, SharingConsentStatus.REVOKED.name, now)

            securityAuditRepository.recordEvent(
                eventType = SecurityAuditEventType.HEALTH_DATA_SHARE_REVOKED,
                customDescription = "Revoked sharing consent ID #$consentId"
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Deletes a consent record permanently from the local Room database.
     */
    suspend fun deleteConsentRecord(consentId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val userId = getCurrentUserId()
            consentDao.deleteConsentById(consentId, userId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Records an audit entry when a user cancels the sharing flow.
     */
    suspend fun recordSharingCancelled(reason: String) = withContext(Dispatchers.IO) {
        try {
            securityAuditRepository.recordEvent(
                eventType = SecurityAuditEventType.HEALTH_DATA_SHARE_CANCELLED,
                customDescription = "Health data sharing cancelled: $reason"
            )
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to record share cancellation audit event", e)
        }
    }

    companion object {
        private const val TAG = "HealthSharingRepo"
    }
}
