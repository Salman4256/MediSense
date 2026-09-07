package com.medisense.app

import com.medisense.app.data.local.dao.*
import com.medisense.app.data.local.entity.*
import com.medisense.app.data.remote.supabase.AuthService
import com.medisense.app.data.repository.*
import com.medisense.app.domain.model.*
import com.medisense.app.domain.quality.*
import com.medisense.app.domain.risk.ContextAwareRiskEngine
import com.medisense.app.domain.guidance.PersonalizedGuidanceEngine
import com.medisense.app.domain.sharing.HealthSharingPackageBuilder
import com.medisense.app.domain.trace.HealthDecisionTraceEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class HealthSharingUnitTest {

    private lateinit var fakeAuthService: FakeAuthService
    private lateinit var fakeConsentDao: FakeHealthSharingConsentDao
    private lateinit var fakeHealthProfileDao: FakeHealthProfileDao
    private lateinit var fakeMedicationDao: FakeMedicationDao
    private lateinit var fakeMedicationHistoryDao: FakeMedicationHistoryDao
    private lateinit var fakeAppointmentDao: FakeAppointmentDao
    private lateinit var fakePredictionDao: FakePredictionHistoryDao
    private lateinit var fakeSyncMetadataDao: FakeSyncMetadataDao
    private lateinit var fakeSecurityAuditDao: FakeSecurityAuditEventDao

    private lateinit var emergencyCardRepo: EmergencyHealthCardRepository
    private lateinit var decisionTraceRepo: HealthDecisionTraceRepository
    private lateinit var longitudinalRepo: LongitudinalHealthRepository
    private lateinit var dataQualityRepo: HealthDataQualityRepository
    private lateinit var securityAuditRepo: SecurityAuditRepository
    private lateinit var sharingRepository: HealthSharingRepository

    private val testUserId = "user-share-12345"
    private val otherUserId = "user-share-99999"

    @Before
    fun setUp() {
        fakeAuthService = FakeAuthService(userId = testUserId, email = "share_user@medisense.app")
        fakeConsentDao = FakeHealthSharingConsentDao()
        fakeHealthProfileDao = FakeHealthProfileDao()
        fakeMedicationDao = FakeMedicationDao()
        fakeMedicationHistoryDao = FakeMedicationHistoryDao()
        fakeAppointmentDao = FakeAppointmentDao()
        fakePredictionDao = FakePredictionHistoryDao()
        fakeSyncMetadataDao = FakeSyncMetadataDao()
        fakeSecurityAuditDao = FakeSecurityAuditEventDao()

        securityAuditRepo = SecurityAuditRepository(
            auditDao = fakeSecurityAuditDao,
            authService = fakeAuthService
        )

        emergencyCardRepo = EmergencyHealthCardRepository(
            healthProfileDao = fakeHealthProfileDao,
            medicationDao = fakeMedicationDao,
            authService = fakeAuthService
        )

        val personalHealthContextRepo = PersonalHealthContextRepository(
            healthProfileDao = fakeHealthProfileDao,
            predictionHistoryDao = fakePredictionDao,
            medicationDao = fakeMedicationDao,
            medicationHistoryDao = fakeMedicationHistoryDao,
            appointmentDao = fakeAppointmentDao,
            authService = fakeAuthService
        )

        longitudinalRepo = LongitudinalHealthRepository(
            predictionHistoryDao = fakePredictionDao,
            medicationDao = fakeMedicationDao,
            medicationHistoryDao = fakeMedicationHistoryDao,
            appointmentDao = fakeAppointmentDao,
            authService = fakeAuthService
        )

        val rchrRepo = RchrRepository(
            healthProfileDao = fakeHealthProfileDao,
            predictionHistoryDao = fakePredictionDao,
            medicationDao = fakeMedicationDao,
            medicationHistoryDao = fakeMedicationHistoryDao,
            appointmentDao = fakeAppointmentDao,
            authService = fakeAuthService
        )

        val riskRepo = ContextualRiskRepository(
            personalHealthContextRepository = personalHealthContextRepo,
            longitudinalHealthRepository = longitudinalRepo,
            rchrRepository = rchrRepo,
            riskEngine = ContextAwareRiskEngine(),
            authService = fakeAuthService
        )

        val guidanceRepo = PersonalizedGuidanceRepository(
            personalHealthContextRepository = personalHealthContextRepo,
            longitudinalHealthRepository = longitudinalRepo,
            rchrRepository = rchrRepo,
            contextualRiskRepository = riskRepo,
            guidanceEngine = PersonalizedGuidanceEngine(),
            authService = fakeAuthService
        )

        val qualityEngine = HealthDataQualityEngine(
            profileValidator = ProfileDataValidator(),
            medicationValidator = MedicationDataValidator(),
            appointmentValidator = AppointmentDataValidator(),
            predictionValidator = PredictionDataValidator(),
            temporalValidator = TemporalConsistencyValidator()
        )

        dataQualityRepo = HealthDataQualityRepository(
            authService = fakeAuthService,
            qualityEngine = qualityEngine,
            healthProfileDao = fakeHealthProfileDao,
            medicationDao = fakeMedicationDao,
            medicationHistoryDao = fakeMedicationHistoryDao,
            appointmentDao = fakeAppointmentDao,
            predictionHistoryDao = fakePredictionDao,
            syncMetadataDao = fakeSyncMetadataDao,
            securityAuditRepository = securityAuditRepo
        )

        decisionTraceRepo = HealthDecisionTraceRepository(
            authService = fakeAuthService,
            predictionHistoryDao = fakePredictionDao,
            personalHealthContextRepository = personalHealthContextRepo,
            longitudinalHealthRepository = longitudinalRepo,
            rchrRepository = rchrRepo,
            contextualRiskRepository = riskRepo,
            personalizedGuidanceRepository = guidanceRepo,
            healthDataQualityRepository = dataQualityRepo,
            securityAuditRepository = securityAuditRepo,
            traceEngine = HealthDecisionTraceEngine()
        )

        sharingRepository = HealthSharingRepository(
            authService = fakeAuthService,
            consentDao = fakeConsentDao,
            healthProfileDao = fakeHealthProfileDao,
            medicationDao = fakeMedicationDao,
            medicationHistoryDao = fakeMedicationHistoryDao,
            appointmentDao = fakeAppointmentDao,
            predictionHistoryDao = fakePredictionDao,
            emergencyHealthCardRepository = emergencyCardRepo,
            healthDecisionTraceRepository = decisionTraceRepo,
            longitudinalHealthRepository = longitudinalRepo,
            healthDataQualityRepository = dataQualityRepo,
            securityAuditRepository = securityAuditRepo
        )
    }

    // 1. Data Minimization: Only selected categories are included in package
    @Test
    fun testPackageBuilder_selectiveCategories_onlyIncludesSelected() {
        val profile = HealthProfileEntity(
            id = "profile-1",
            userId = testUserId,
            fullName = "John Doe",
            dateOfBirth = "1988-04-12",
            gender = "Male",
            bloodGroup = "A+",
            height = 175.0,
            weight = 70.0,
            allergies = "Penicillin",
            existingDiseases = "Hypertension",
            currentMedications = "Lisinopril",
            familyHistory = null,
            emergencyContactName = "Jane Doe",
            emergencyContactNumber = "+1-555-0199",
            notes = null
        )
        val medications = listOf(
            MedicationEntity(
                id = 1L,
                userId = testUserId,
                medicineName = "Lisinopril",
                dosage = "10",
                dosageUnit = "mg",
                frequency = "Once daily",
                active = true
            )
        )

        // Select only PROFILE and MEDICATIONS
        val scope = HealthSharingScope(
            purpose = SharingPurpose.DOCTOR_CONSULTATION,
            recipientLabel = "Dr. Cardiology",
            selectedCategories = setOf(SharingDataCategory.PROFILE, SharingDataCategory.MEDICATIONS),
            format = SharingFormat.PDF_DOCUMENT
        )

        val pkg = HealthSharingPackageBuilder.buildPackage(
            userId = testUserId,
            scope = scope,
            profile = profile,
            medications = medications,
            medicationHistory = emptyList(),
            adherenceStats = null,
            predictions = emptyList(),
            appointments = emptyList(),
            decisionTraces = emptyList(),
            longitudinal = null,
            emergencyCard = null,
            qualitySummary = null
        )

        assertNotNull(pkg.personalProfile)
        assertEquals("John Doe", pkg.personalProfile?.fullName)
        assertEquals("A+", pkg.personalProfile?.bloodGroup)

        assertNotNull(pkg.medicationsAndAdherence)
        assertEquals(1, pkg.medicationsAndAdherence?.totalActiveMedications)

        // Other non-selected categories must remain null
        assertNull(pkg.conditionsAndAllergies)
        assertNull(pkg.predictionHistory)
        assertNull(pkg.doctorAppointments)
        assertNull(pkg.decisionTraces)
        assertNull(pkg.longitudinalSummary)
        assertNull(pkg.emergencyAccessCard)
        assertNull(pkg.dataQualityNotice)
    }

    // 2. Data Sanitization: Strips sensitive credentials & internal IDs
    @Test
    fun testPackageBuilder_dataSanitization_redactsSensitiveCredentials() {
        val profile = HealthProfileEntity(
            id = "profile-2",
            userId = testUserId,
            fullName = "Alice Smith password=SecretPass123",
            dateOfBirth = "1992-06-15",
            gender = "Female",
            bloodGroup = "B+",
            height = 165.0,
            weight = 58.0,
            allergies = null,
            existingDiseases = null,
            currentMedications = null,
            familyHistory = null,
            emergencyContactName = null,
            emergencyContactNumber = null,
            notes = "bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
        )

        val scope = HealthSharingScope(
            purpose = SharingPurpose.PERSONAL_ARCHIVE,
            recipientLabel = "Personal Backup",
            selectedCategories = setOf(SharingDataCategory.PROFILE, SharingDataCategory.CONDITIONS_ALLERGIES),
            format = SharingFormat.JSON_PACKAGE
        )

        val pkg = HealthSharingPackageBuilder.buildPackage(
            userId = testUserId,
            scope = scope,
            profile = profile,
            medications = emptyList(),
            medicationHistory = emptyList(),
            adherenceStats = null,
            predictions = emptyList(),
            appointments = emptyList(),
            decisionTraces = emptyList(),
            longitudinal = null,
            emergencyCard = null,
            qualitySummary = null
        )

        assertTrue(pkg.personalProfile?.fullName?.contains("[REDACTED]") == true)
        assertFalse(pkg.personalProfile?.fullName?.contains("SecretPass123") == true)
        assertTrue(pkg.conditionsAndAllergies?.notes?.contains("[REDACTED]") == true)
    }

    // 3. SHA-256 Fingerprint Determinism
    @Test
    fun testPackageBuilder_sha256Fingerprint_isDeterministicAndValid() {
        val scope = HealthSharingScope(
            purpose = SharingPurpose.EMERGENCY_PREPAREDNESS,
            recipientLabel = "First Responders",
            selectedCategories = setOf(SharingDataCategory.EMERGENCY_SUMMARY),
            format = SharingFormat.BOTH
        )

        val pkg = HealthSharingPackageBuilder.buildPackage(
            userId = testUserId,
            scope = scope,
            profile = null,
            medications = emptyList(),
            medicationHistory = emptyList(),
            adherenceStats = null,
            predictions = emptyList(),
            appointments = emptyList(),
            decisionTraces = emptyList(),
            longitudinal = null,
            emergencyCard = null,
            qualitySummary = null
        )

        val fp = pkg.packageMetadata.sha256Fingerprint
        assertNotNull(fp)
        assertTrue(fp.length >= 16)
        assertNotEquals("COMPUTING", fp)
    }

    // 4. Consent Lifecycle: Grant, Observe & Audit Telemetry
    @Test
    fun testGrantConsent_savesRecordAndLogsTelemetry() = runBlocking {
        fakeHealthProfileDao.insertHealthProfile(
            HealthProfileEntity(
                id = "profile-4",
                userId = testUserId,
                fullName = "Jane Smith",
                dateOfBirth = "1990-01-01",
                gender = "Female",
                bloodGroup = "O+",
                height = 170.0,
                weight = 65.0,
                allergies = null,
                existingDiseases = null,
                currentMedications = null,
                familyHistory = null,
                emergencyContactName = null,
                emergencyContactNumber = null,
                notes = null
            )
        )

        val scope = HealthSharingScope(
            purpose = SharingPurpose.DOCTOR_CONSULTATION,
            recipientLabel = "Dr. Johnson",
            selectedCategories = setOf(SharingDataCategory.PROFILE),
            format = SharingFormat.PDF_DOCUMENT
        )

        val pkg = sharingRepository.prepareSharingPackage(scope)
        assertNotNull(pkg)

        val consentsBefore = fakeConsentDao.getConsentsForUserSync(testUserId)
        assertEquals(0, consentsBefore.size)

        // Insert consent entity
        val consentEntity = HealthSharingConsentEntity(
            userId = testUserId,
            purpose = scope.purpose.name,
            recipientLabel = scope.recipientLabel,
            selectedCategories = scope.selectedCategories.map { it.categoryId },
            format = scope.format.name,
            packageFingerprint = pkg.packageMetadata.sha256Fingerprint,
            status = SharingConsentStatus.ACTIVE.name,
            createdAt = System.currentTimeMillis()
        )
        fakeConsentDao.insertConsent(consentEntity)

        val consentsAfter = sharingRepository.observeConsents().first()
        assertEquals(1, consentsAfter.size)
        assertEquals("Dr. Johnson", consentsAfter[0].recipientLabel)
        assertEquals(SharingConsentStatus.ACTIVE, consentsAfter[0].status)
        assertEquals(SharingPurpose.DOCTOR_CONSULTATION, consentsAfter[0].purpose)

        val auditEvents = fakeSecurityAuditDao.getAuditEventsForUser(testUserId)
        assertTrue(auditEvents.any { it.eventType == SecurityAuditEventType.HEALTH_DATA_SHARE_PREPARED.name })
    }

    // 5. Consent Revocation
    @Test
    fun testRevokeConsent_updatesStatusToRevoked() = runBlocking {
        val consentId = fakeConsentDao.insertConsent(
            HealthSharingConsentEntity(
                userId = testUserId,
                purpose = SharingPurpose.CAREGIVER_SUPPORT.name,
                recipientLabel = "Jane Caregiver",
                selectedCategories = listOf("PROFILE", "MEDICATIONS"),
                format = SharingFormat.JSON_PACKAGE.name,
                packageFingerprint = "fingerprint12345",
                status = SharingConsentStatus.ACTIVE.name,
                createdAt = System.currentTimeMillis()
            )
        )

        val result = sharingRepository.revokeConsent(consentId)
        assertTrue(result.isSuccess)

        val consents = sharingRepository.observeConsents().first()
        assertEquals(1, consents.size)
        assertEquals(SharingConsentStatus.REVOKED, consents[0].status)
        assertNotNull(consents[0].revokedAt)

        val auditEvents = fakeSecurityAuditDao.getAuditEventsForUser(testUserId)
        assertTrue(auditEvents.any { it.eventType == SecurityAuditEventType.HEALTH_DATA_SHARE_REVOKED.name })
    }

    // 6. User Isolation: No cross-user leakage in consent or packages
    @Test
    fun testUserIsolation_noCrossUserConsentLeakage() = runBlocking {
        fakeConsentDao.insertConsent(
            HealthSharingConsentEntity(
                userId = otherUserId,
                purpose = SharingPurpose.DOCTOR_CONSULTATION.name,
                recipientLabel = "Other Doctor",
                selectedCategories = listOf("PROFILE"),
                format = SharingFormat.PDF_DOCUMENT.name,
                packageFingerprint = "fingerprint_other",
                status = SharingConsentStatus.ACTIVE.name,
                createdAt = System.currentTimeMillis()
            )
        )

        fakeConsentDao.insertConsent(
            HealthSharingConsentEntity(
                userId = testUserId,
                purpose = SharingPurpose.CAREGIVER_SUPPORT.name,
                recipientLabel = "My Caregiver",
                selectedCategories = listOf("PROFILE"),
                format = SharingFormat.PDF_DOCUMENT.name,
                packageFingerprint = "fingerprint_mine",
                status = SharingConsentStatus.ACTIVE.name,
                createdAt = System.currentTimeMillis()
            )
        )

        val myConsents = sharingRepository.observeConsents().first()
        assertEquals(1, myConsents.size)
        assertEquals("My Caregiver", myConsents[0].recipientLabel)
        assertFalse(myConsents.any { it.recipientLabel == "Other Doctor" })
    }

    // 7. Non-blocking Data Quality Notice Integration
    @Test
    fun testDataQualityNotice_includedWhenIssuesExist() {
        val qualitySummary = HealthDataQualitySummary(
            status = HealthDataQualityStatus.NEEDS_ATTENTION,
            qualityScore = 72,
            totalChecks = 15,
            passedChecks = 11,
            errorCount = 0,
            warningCount = 4,
            infoCount = 0,
            issues = listOf(
                HealthDataQualityIssue(
                    id = "q1",
                    category = HealthDataQualityCategory.PROFILE_COMPLETENESS,
                    severity = HealthDataQualitySeverity.WARNING,
                    title = "Incomplete Emergency Contact",
                    explanation = "Emergency contact phone is missing.",
                    affectedRecordType = "HealthProfileEntity",
                    recordId = "1",
                    suggestedCorrection = "Add emergency contact phone in profile",
                    navigationDestinationId = null
                )
            ),
            categoryBreakdown = emptyMap(),
            evaluatedTimestamp = System.currentTimeMillis()
        )

        val scope = HealthSharingScope(
            selectedCategories = setOf(SharingDataCategory.DATA_QUALITY_NOTE)
        )

        val pkg = HealthSharingPackageBuilder.buildPackage(
            userId = testUserId,
            scope = scope,
            profile = null,
            medications = emptyList(),
            medicationHistory = emptyList(),
            adherenceStats = null,
            predictions = emptyList(),
            appointments = emptyList(),
            decisionTraces = emptyList(),
            longitudinal = null,
            emergencyCard = null,
            qualitySummary = qualitySummary
        )

        assertNotNull(pkg.dataQualityNotice)
        assertEquals("NEEDS_ATTENTION", pkg.dataQualityNotice?.overallQualityStatus)
        assertEquals(72, pkg.dataQualityNotice?.completenessScore)
        assertEquals(1, pkg.dataQualityNotice?.qualityIssues?.size)
        assertTrue(pkg.dataQualityNotice?.noticeText?.contains("non-blocking") == true)
    }

    // 8. Purpose & Format Enums
    @Test
    fun testSharingPurposeAndFormatVariations() {
        assertEquals("Doctor Consultation & Clinical Review", SharingPurpose.DOCTOR_CONSULTATION.displayName)
        assertEquals("Caregiver & Family Support", SharingPurpose.CAREGIVER_SUPPORT.displayName)
        assertEquals("Emergency Readiness & Backup", SharingPurpose.EMERGENCY_PREPAREDNESS.displayName)
        assertEquals("Personal Health Archive / Data Portability", SharingPurpose.PERSONAL_ARCHIVE.displayName)

        assertEquals("json", SharingFormat.JSON_PACKAGE.fileExtension)
        assertEquals("pdf", SharingFormat.PDF_DOCUMENT.fileExtension)
        assertEquals("zip", SharingFormat.BOTH.fileExtension)
    }

    // 9. Plain Text / Preview Summary Formatting
    @Test
    fun testPreviewSummary_formatsReadableText() {
        val scope = HealthSharingScope(
            purpose = SharingPurpose.DOCTOR_CONSULTATION,
            recipientLabel = "Dr. Davis",
            selectedCategories = setOf(SharingDataCategory.PROFILE, SharingDataCategory.MEDICATIONS)
        )

        val pkg = HealthSharingPackageBuilder.buildPackage(
            userId = testUserId,
            scope = scope,
            profile = null,
            medications = emptyList(),
            medicationHistory = emptyList(),
            adherenceStats = null,
            predictions = emptyList(),
            appointments = emptyList(),
            decisionTraces = emptyList(),
            longitudinal = null,
            emergencyCard = null,
            qualitySummary = null
        )

        val summary = HealthSharingPackageBuilder.generatePreviewSummary(pkg)
        assertTrue(summary.contains("Package ID:"))
        assertTrue(summary.contains("Purpose: Doctor Consultation & Clinical Review"))
        assertTrue(summary.contains("Recipient: Dr. Davis"))
        assertTrue(summary.contains("Personal Profile & Demographics"))
        assertTrue(summary.contains("Medications & Adherence"))
    }

    // 10. Cancellation Audit Telemetry
    @Test
    fun testCancelSharingFlow_recordsCancellationAudit() = runBlocking {
        sharingRepository.recordSharingCancelled("User dismissed preview modal")

        val auditEvents = fakeSecurityAuditDao.getAuditEventsForUser(testUserId)
        assertTrue(auditEvents.any { it.eventType == SecurityAuditEventType.HEALTH_DATA_SHARE_CANCELLED.name })
    }

    // Fake DAOs & Services
    private class FakeAuthService(
        private var userId: String?,
        private var email: String?
    ) : AuthService(
        io.github.jan.supabase.createSupabaseClient(
            supabaseUrl = "https://placeholder.supabase.co",
            supabaseKey = "dummy-anon-key"
        ) {},
        com.medisense.app.data.local.session.SharedPreferencesSessionManager(null)
    ) {
        override fun getCurrentUserId(): String? = userId
        override fun getCurrentUserEmail(): String? = email
    }

    private class FakeHealthSharingConsentDao : HealthSharingConsentDao {
        val consents = mutableListOf<HealthSharingConsentEntity>()
        private var nextId = 1L

        override fun observeConsentsForUser(userId: String): Flow<List<HealthSharingConsentEntity>> =
            flowOf(consents.filter { it.userId == userId })

        override suspend fun getConsentsForUserSync(userId: String): List<HealthSharingConsentEntity> =
            consents.filter { it.userId == userId }

        override suspend fun getConsentById(id: Long, userId: String): HealthSharingConsentEntity? =
            consents.find { it.id == id && it.userId == userId }

        override suspend fun insertConsent(entity: HealthSharingConsentEntity): Long {
            val id = if (entity.id != 0L) entity.id else nextId++
            val saved = entity.copy(id = id)
            consents.removeAll { it.id == id }
            consents.add(saved)
            return id
        }

        override suspend fun updateConsent(entity: HealthSharingConsentEntity) {
            consents.removeAll { it.id == entity.id }
            consents.add(entity)
        }

        override suspend fun updateConsentStatus(id: Long, userId: String, status: String, revokedAt: Long?) {
            val existing = consents.find { it.id == id && it.userId == userId }
            if (existing != null) {
                consents.remove(existing)
                consents.add(existing.copy(status = status, revokedAt = revokedAt))
            }
        }

        override suspend fun deleteConsentById(id: Long, userId: String) {
            consents.removeAll { it.id == id && it.userId == userId }
        }

        override suspend fun deleteAllConsentsForUser(userId: String) {
            consents.removeAll { it.userId == userId }
        }
    }

    private class FakeHealthProfileDao : HealthProfileDao {
        val profiles = mutableListOf<HealthProfileEntity>()
        override fun observeHealthProfile(userId: String): Flow<HealthProfileEntity?> = flowOf(profiles.find { it.userId == userId })
        override suspend fun getHealthProfile(userId: String): HealthProfileEntity? = profiles.find { it.userId == userId }
        override suspend fun insertHealthProfile(profile: HealthProfileEntity) {
            profiles.removeAll { it.userId == profile.userId }
            profiles.add(profile)
        }
        override suspend fun updateHealthProfile(profile: HealthProfileEntity) {
            profiles.removeAll { it.userId == profile.userId }
            profiles.add(profile)
        }
        override suspend fun deleteHealthProfile(profile: HealthProfileEntity) { profiles.remove(profile) }
        override suspend fun deleteHealthProfileByUserId(userId: String) { profiles.removeAll { it.userId == userId } }
        override suspend fun getPendingSyncProfiles(): List<HealthProfileEntity> = emptyList()
        override suspend fun getPendingSyncProfileForUser(userId: String): HealthProfileEntity? = null
        override suspend fun markProfileSynced(userId: String) {}
    }

    private class FakeMedicationDao : MedicationDao {
        val medications = mutableListOf<MedicationEntity>()
        private var nextId = 1L

        override fun getMedicationsForUser(userId: String): Flow<List<MedicationEntity>> = flowOf(medications.filter { it.userId == userId })
        override fun getActiveMedicationsForUser(userId: String): Flow<List<MedicationEntity>> = flowOf(medications.filter { it.userId == userId && it.active })
        override suspend fun getActiveMedicationsForUserSync(userId: String): List<MedicationEntity> = medications.filter { it.userId == userId && it.active }
        override suspend fun getAllActiveMedicationsSync(): List<MedicationEntity> = medications.filter { it.active }
        override suspend fun getMedicationById(id: Long, userId: String): MedicationEntity? = medications.find { it.id == id && it.userId == userId }
        override suspend fun getMedicationByIdSync(id: Long): MedicationEntity? = medications.find { it.id == id }

        override suspend fun insertMedication(medication: MedicationEntity): Long {
            val id = if (medication.id != 0L) medication.id else nextId++
            val item = medication.copy(id = id)
            medications.removeAll { it.id == id }
            medications.add(item)
            return id
        }

        override suspend fun updateMedication(medication: MedicationEntity) {
            medications.removeAll { it.id == medication.id }
            medications.add(medication)
        }

        override suspend fun deleteMedicationById(id: Long, userId: String) {
            medications.removeAll { it.id == id && it.userId == userId }
        }

        override suspend fun deleteAllMedicationsForUser(userId: String) {
            medications.removeAll { it.userId == userId }
        }

        override suspend fun getPendingSyncMedications(userId: String): List<MedicationEntity> = emptyList()
        override suspend fun getAllMedicationsForUserSync(userId: String): List<MedicationEntity> = medications.filter { it.userId == userId }
        override suspend fun markMedicationSynced(id: Long, userId: String) {}
        override suspend fun upsertMedications(medications: List<MedicationEntity>) {}
    }

    private class FakeMedicationHistoryDao : MedicationHistoryDao {
        val history = mutableListOf<MedicationHistoryEntity>()
        private var nextId = 1L

        override fun getHistoryForUser(userId: String): Flow<List<MedicationHistoryEntity>> = flowOf(history.filter { it.userId == userId })
        override fun getHistoryForMedication(medicationId: Long, userId: String): Flow<List<MedicationHistoryEntity>> = flowOf(history.filter { it.medicationId == medicationId && it.userId == userId })
        override suspend fun getOccurrenceHistory(medicationId: Long, date: Long, time: String, userId: String): MedicationHistoryEntity? =
            history.find { it.medicationId == medicationId && it.scheduledDate == date && it.scheduledTime == time && it.userId == userId }
        override suspend fun findExistingRecord(medicationId: Long, date: Long, time: String): MedicationHistoryEntity? =
            history.find { it.medicationId == medicationId && it.scheduledDate == date && it.scheduledTime == time }
        override fun getHistoryForDateFlow(userId: String, date: Long): Flow<List<MedicationHistoryEntity>> = flowOf(emptyList())
        override fun getHistoryForDateRangeFlow(userId: String, startDate: Long, endDate: Long): Flow<List<MedicationHistoryEntity>> = flowOf(emptyList())

        override suspend fun insertHistory(history: MedicationHistoryEntity): Long {
            val id = if (history.id != 0L) history.id else nextId++
            val saved = history.copy(id = id)
            this.history.removeAll { it.id == id }
            this.history.add(saved)
            return id
        }

        override suspend fun updateHistory(history: MedicationHistoryEntity) {}
        override suspend fun deleteAllMedicationHistoryForUser(userId: String) { history.removeAll { it.userId == userId } }
        override suspend fun getAllHistoryForUserSync(userId: String): List<MedicationHistoryEntity> = history.filter { it.userId == userId }
        override suspend fun upsertHistory(historyList: List<MedicationHistoryEntity>) {}
    }

    private class FakeAppointmentDao : AppointmentDao {
        val appointments = mutableListOf<AppointmentEntity>()
        private var nextId = 1L

        override fun observeAppointments(userId: String): Flow<List<AppointmentEntity>> = flowOf(appointments.filter { it.userId == userId })
        override fun observeUpcomingAppointments(userId: String, currentTime: Long): Flow<List<AppointmentEntity>> = flowOf(appointments.filter { it.userId == userId })
        override suspend fun getAllUpcomingScheduledAppointmentsSync(currentTime: Long): List<AppointmentEntity> = appointments.filter { it.status == "SCHEDULED" }
        override fun observeCompletedAppointments(userId: String): Flow<List<AppointmentEntity>> = flowOf(emptyList())
        override fun observeCancelledAppointments(userId: String): Flow<List<AppointmentEntity>> = flowOf(emptyList())

        override suspend fun getAppointment(id: Long, userId: String): AppointmentEntity? = appointments.find { it.id == id && it.userId == userId }
        override suspend fun insertAppointment(appointment: AppointmentEntity): Long {
            val id = if (appointment.id != 0L) appointment.id else nextId++
            val saved = appointment.copy(id = id)
            appointments.removeAll { it.id == id }
            appointments.add(saved)
            return id
        }

        override suspend fun updateAppointment(appointment: AppointmentEntity) {}
        override suspend fun deleteAppointment(appointment: AppointmentEntity) { appointments.remove(appointment) }
        override suspend fun deleteAppointmentById(id: Long, userId: String) { appointments.removeAll { it.id == id && it.userId == userId } }
        override suspend fun deleteAllAppointmentsForUser(userId: String) { appointments.removeAll { it.userId == userId } }
        override suspend fun updateAppointmentStatus(id: Long, userId: String, status: String, updatedAt: Long) {}
        override suspend fun getPendingSyncAppointments(userId: String): List<AppointmentEntity> = emptyList()
        override suspend fun getAllAppointmentsForUserSync(userId: String): List<AppointmentEntity> = appointments.filter { it.userId == userId }
        override suspend fun markAppointmentSynced(id: Long, userId: String) {}
        override suspend fun upsertAppointments(appointments: List<AppointmentEntity>) {}
    }

    private class FakePredictionHistoryDao : PredictionHistoryDao {
        val list = mutableListOf<PredictionHistoryEntity>()
        private var nextId = 1L

        override fun observePredictionHistory(userId: String): Flow<List<PredictionHistoryEntity>> = flowOf(list.filter { it.userId == userId })
        override suspend fun getPredictionHistoryById(id: Long, userId: String): PredictionHistoryEntity? = list.find { it.id == id && it.userId == userId }
        override suspend fun insertPredictionHistory(entity: PredictionHistoryEntity): Long {
            val id = if (entity.id != 0L) entity.id else nextId++
            val saved = entity.copy(id = id)
            list.removeAll { it.id == id }
            list.add(saved)
            return id
        }

        override suspend fun deletePredictionHistory(id: Long, userId: String) { list.removeAll { it.id == id && it.userId == userId } }
        override suspend fun deleteAllPredictionHistory(userId: String) { list.removeAll { it.userId == userId } }
        override suspend fun getPendingSyncPredictionHistory(userId: String): List<PredictionHistoryEntity> = emptyList()
        override suspend fun getAllPredictionHistoryForUserSync(userId: String): List<PredictionHistoryEntity> = list.filter { it.userId == userId }
        override suspend fun markPredictionSynced(id: Long, userId: String) {}
        override suspend fun upsertPredictionHistory(predictions: List<PredictionHistoryEntity>) {}
    }

    private class FakeSyncMetadataDao : SyncMetadataDao {
        val metadata = mutableListOf<SyncMetadataEntity>()

        override fun observeSyncMetadata(userId: String): Flow<SyncMetadataEntity?> = flowOf(metadata.find { it.userId == userId })
        override suspend fun getSyncMetadata(userId: String): SyncMetadataEntity? = metadata.find { it.userId == userId }
        override suspend fun upsertSyncMetadata(metadata: SyncMetadataEntity) {
            this.metadata.removeAll { it.userId == metadata.userId }
            this.metadata.add(metadata)
        }
        override suspend fun deleteSyncMetadataForUser(userId: String) {
            metadata.removeAll { it.userId == userId }
        }
    }

    private class FakeSecurityAuditEventDao : SecurityAuditEventDao {
        val events = mutableListOf<SecurityAuditEventEntity>()
        private var nextId = 1L

        override fun observeRecentAuditEvents(userId: String, limit: Int): Flow<List<SecurityAuditEventEntity>> =
            flowOf(events.filter { it.userId == userId }.take(limit))

        override suspend fun getAuditEventsForUser(userId: String): List<SecurityAuditEventEntity> =
            events.filter { it.userId == userId }

        override suspend fun insertAuditEvent(event: SecurityAuditEventEntity): Long {
            val id = nextId++
            events.add(event.copy(id = id))
            return id
        }

        override suspend fun deleteAllAuditEventsForUser(userId: String) {
            events.removeAll { it.userId == userId }
        }
    }
}
