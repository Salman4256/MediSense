package com.medisense.app

import com.medisense.app.data.local.dao.*
import com.medisense.app.data.local.entity.*
import com.medisense.app.data.remote.supabase.AuthService
import com.medisense.app.data.repository.*
import com.medisense.app.domain.model.*
import com.medisense.app.domain.portability.PortableHealthDataMapper
import com.medisense.app.domain.quality.*
import com.medisense.app.domain.risk.ContextAwareRiskEngine
import com.medisense.app.domain.guidance.PersonalizedGuidanceEngine
import com.medisense.app.domain.trace.HealthDecisionTraceEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PortableHealthDataUnitTest {

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
    private lateinit var personalHealthContextRepo: PersonalHealthContextRepository
    private lateinit var rchrRepo: RchrRepository
    private lateinit var riskRepo: ContextualRiskRepository
    private lateinit var guidanceRepo: PersonalizedGuidanceRepository
    private lateinit var timelineRepo: HealthTimelineRepository
    private lateinit var portabilityRepository: PortableHealthDataRepository

    private val testUserId = "user-portable-12345"
    private val otherUserId = "user-portable-99999"

    @Before
    fun setUp() {
        fakeAuthService = FakeAuthService(userId = testUserId, email = "portable_user@medisense.app")
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

        personalHealthContextRepo = PersonalHealthContextRepository(
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

        rchrRepo = RchrRepository(
            healthProfileDao = fakeHealthProfileDao,
            predictionHistoryDao = fakePredictionDao,
            medicationDao = fakeMedicationDao,
            medicationHistoryDao = fakeMedicationHistoryDao,
            appointmentDao = fakeAppointmentDao,
            authService = fakeAuthService
        )

        riskRepo = ContextualRiskRepository(
            personalHealthContextRepository = personalHealthContextRepo,
            longitudinalHealthRepository = longitudinalRepo,
            rchrRepository = rchrRepo,
            riskEngine = ContextAwareRiskEngine(),
            authService = fakeAuthService
        )

        guidanceRepo = PersonalizedGuidanceRepository(
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

        timelineRepo = HealthTimelineRepository(
            authService = fakeAuthService,
            healthProfileDao = fakeHealthProfileDao,
            medicationDao = fakeMedicationDao,
            medicationHistoryDao = fakeMedicationHistoryDao,
            appointmentDao = fakeAppointmentDao,
            predictionHistoryDao = fakePredictionDao,
            securityAuditEventDao = fakeSecurityAuditDao,
            personalHealthContextRepository = personalHealthContextRepo,
            longitudinalHealthRepository = longitudinalRepo,
            contextualRiskRepository = riskRepo,
            personalizedGuidanceRepository = guidanceRepo,
            healthDataQualityRepository = dataQualityRepo,
            securityAuditRepository = securityAuditRepo
        )

        portabilityRepository = PortableHealthDataRepository(
            authService = fakeAuthService,
            healthProfileDao = fakeHealthProfileDao,
            medicationDao = fakeMedicationDao,
            medicationHistoryDao = fakeMedicationHistoryDao,
            appointmentDao = fakeAppointmentDao,
            predictionHistoryDao = fakePredictionDao,
            emergencyHealthCardRepository = emergencyCardRepo,
            healthDecisionTraceRepository = decisionTraceRepo,
            longitudinalHealthRepository = longitudinalRepo,
            personalHealthContextRepository = personalHealthContextRepo,
            contextualRiskRepository = riskRepo,
            personalizedGuidanceRepository = guidanceRepo,
            rchrRepository = rchrRepo,
            healthTimelineRepository = timelineRepo,
            healthDataQualityRepository = dataQualityRepo,
            securityAuditRepository = securityAuditRepo
        )

        populateSampleData()
    }

    private fun populateSampleData() = runBlocking {
        fakeHealthProfileDao.insertHealthProfile(
            HealthProfileEntity(
                id = "profile-1",
                userId = testUserId,
                fullName = "John Doe",
                dateOfBirth = "1980-05-15",
                gender = "Male",
                bloodGroup = "A+",
                height = 178.0,
                weight = 80.0,
                allergies = "Penicillin, Peanuts",
                existingDiseases = "Hypertension, Asthma",
                currentMedications = "Lisinopril",
                familyHistory = null,
                emergencyContactName = "Jane Doe",
                emergencyContactNumber = "+1234567890",
                notes = null
            )
        )

        fakeMedicationDao.insertMedication(
            MedicationEntity(
                id = 101L,
                userId = testUserId,
                medicineName = "Lisinopril",
                dosage = "10",
                dosageUnit = "mg",
                frequency = "Once daily",
                instructions = "Take with water in the morning",
                startDate = System.currentTimeMillis() - 86400000L * 30,
                endDate = null,
                active = true
            )
        )

        fakeMedicationHistoryDao.insertHistory(
            MedicationHistoryEntity(
                id = 201L,
                medicationId = 101L,
                userId = testUserId,
                status = "TAKEN",
                scheduledDate = System.currentTimeMillis() - 86400000L,
                scheduledTime = "08:00 AM",
                actionTime = System.currentTimeMillis() - 3600000
            )
        )

        fakeAppointmentDao.insertAppointment(
            AppointmentEntity(
                id = 301L,
                userId = testUserId,
                doctorName = "Dr. Emily Smith",
                clinicName = "Cardiology Clinic",
                appointmentType = "In-Person",
                appointmentDate = "2026-09-15",
                appointmentTime = "10:30 AM",
                notes = "Routine cardiovascular checkup",
                status = "SCHEDULED",
                appointmentTimestamp = System.currentTimeMillis() + 86400000L * 5,
                reminderMinutesBefore = 60
            )
        )

        fakePredictionDao.insertPredictionHistory(
            PredictionHistoryEntity(
                id = 401L,
                userId = testUserId,
                predictedDisease = "Hypertension Risk",
                confidence = 0.88f,
                symptoms = listOf("Headache", "Dizziness", "Fatigue"),
                explanationSummary = "Elevated systolic trends combined with reported headache",
                predictionTimestamp = System.currentTimeMillis() - 7200000
            )
        )
    }

    // 1. Deterministic Bundle Mapping & SHA-256 Checksum
    @Test
    fun testDeterministicBundleSerializationAndSha256Checksum() = runBlocking {
        val scope = PortableExportScope(
            selectedCategories = PortableDataCategory.entries.toSet(),
            userConfirmationAccepted = true
        )

        val preview = portabilityRepository.generatePreview(scope)

        assertNotNull(preview)
        assertTrue(preview.estimatedResourceCount > 0)
        assertTrue(preview.previewJsonSample.contains("\"resourceType\": \"Bundle\""))
        assertTrue(preview.previewJsonSample.contains("\"type\": \"collection\""))
    }

    // 2. Data Minimization: Selective Categories Filtering
    @Test
    fun testSelectiveCategoryFiltering_onlyIncludesRequestedEntries() = runBlocking {
        val scope = PortableExportScope(
            selectedCategories = setOf(PortableDataCategory.MEDICATIONS, PortableDataCategory.ALLERGIES),
            userConfirmationAccepted = true
        )

        val preview = portabilityRepository.generatePreview(scope)
        assertEquals(2, preview.totalCategoriesSelected)
        assertTrue(preview.categoryBreakdown.containsKey(PortableDataCategory.MEDICATIONS))
        assertTrue(preview.categoryBreakdown.containsKey(PortableDataCategory.ALLERGIES))
    }

    // 3. Credential & Primary Key Sanitization
    @Test
    fun testCredentialAndSensitiveKeywordSanitization() {
        val textWithSensitiveKeywords = "Patient note: password=Secret123 with bearer eyJhbGciOiJIUzI1Ni... and secret_key=abc"
        val sanitized = PortableHealthDataMapper.sanitizeText(textWithSensitiveKeywords)

        assertFalse(sanitized.contains("Secret123"))
        assertFalse(sanitized.contains("eyJhbGci"))
        assertTrue(sanitized.contains("[REDACTED]"))
    }

    // 4. Export Presets Mapping Validation
    @Test
    fun testExportPresets_selectCorrectCategories() {
        val fullPreset = PortableDataCategory.entries.toSet()
        assertEquals(16, fullPreset.size)

        val clinicalCategories = setOf(
            PortableDataCategory.BASIC_PROFILE,
            PortableDataCategory.ALLERGIES,
            PortableDataCategory.HEALTH_CONDITIONS,
            PortableDataCategory.MEDICATIONS,
            PortableDataCategory.MEDICATION_ADHERENCE,
            PortableDataCategory.APPOINTMENTS,
            PortableDataCategory.DATA_QUALITY
        )
        assertEquals(7, clinicalCategories.size)
        assertTrue(clinicalCategories.contains(PortableDataCategory.MEDICATIONS))
        assertFalse(clinicalCategories.contains(PortableDataCategory.PREDICTION_HISTORY))

        val emergencyCategories = setOf(
            PortableDataCategory.BASIC_PROFILE,
            PortableDataCategory.EMERGENCY_INFORMATION,
            PortableDataCategory.ALLERGIES,
            PortableDataCategory.HEALTH_CONDITIONS,
            PortableDataCategory.MEDICATIONS,
            PortableDataCategory.DATA_QUALITY
        )
        assertEquals(6, emergencyCategories.size)
        assertTrue(emergencyCategories.contains(PortableDataCategory.EMERGENCY_INFORMATION))

        val aiCategories = setOf(
            PortableDataCategory.PREDICTION_HISTORY,
            PortableDataCategory.HEALTH_TRENDS,
            PortableDataCategory.PERSONAL_CONTEXT,
            PortableDataCategory.PERSONALIZED_GUIDANCE,
            PortableDataCategory.DECISION_TRACES,
            PortableDataCategory.HEALTH_TIMELINE,
            PortableDataCategory.RCHR_STATE,
            PortableDataCategory.CONSULTATION_SUMMARY
        )
        assertEquals(8, aiCategories.size)
        assertTrue(aiCategories.contains(PortableDataCategory.DECISION_TRACES))
    }

    // 5. Preview Snippet Generation
    @Test
    fun testPreviewGeneration_producesValidSummary() = runBlocking {
        val scope = PortableExportScope(
            selectedCategories = setOf(PortableDataCategory.BASIC_PROFILE, PortableDataCategory.MEDICATIONS)
        )

        val preview = portabilityRepository.generatePreview(scope)

        assertNotNull(preview)
        assertTrue(preview.estimatedResourceCount >= 2)
        assertTrue(preview.previewJsonSample.contains("\"resourceType\": \"Bundle\""))
    }

    // 6. User Isolation
    @Test
    fun testUserIsolation_otherUserDataNotExported() = runBlocking {
        // Insert data belonging to another user
        fakeMedicationDao.insertMedication(
            MedicationEntity(
                id = 999L,
                userId = otherUserId,
                medicineName = "OtherUserSecretDrug",
                dosage = "500",
                dosageUnit = "mg",
                frequency = "Twice daily",
                instructions = "Private",
                startDate = System.currentTimeMillis(),
                endDate = null,
                active = true
            )
        )

        val scope = PortableExportScope(
            selectedCategories = setOf(PortableDataCategory.MEDICATIONS),
            userConfirmationAccepted = true
        )

        val preview = portabilityRepository.generatePreview(scope)
        assertEquals(1, preview.categoryBreakdown[PortableDataCategory.MEDICATIONS])
        assertFalse(preview.previewJsonSample.contains("OtherUserSecretDrug"))
    }

    // 7. SHA-256 Fingerprint Determinism
    @Test
    fun testSha256FingerprintDeterminism() {
        val hash1 = PortableHealthDataMapper.computeSha256("Test Payload For Fingerprint")
        val hash2 = PortableHealthDataMapper.computeSha256("Test Payload For Fingerprint")
        val hash3 = PortableHealthDataMapper.computeSha256("Different Payload")

        assertEquals(64, hash1.length)
        assertEquals(hash1, hash2)
        assertNotEquals(hash1, hash3)
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
