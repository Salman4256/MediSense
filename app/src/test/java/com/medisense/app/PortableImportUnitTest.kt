package com.medisense.app

import com.medisense.app.data.local.dao.*
import com.medisense.app.data.local.entity.*
import com.medisense.app.data.remote.supabase.AuthService
import com.medisense.app.data.repository.*
import com.medisense.app.domain.guidance.PersonalizedGuidanceEngine
import com.medisense.app.domain.model.*
import com.medisense.app.domain.portability.PortableHealthDataMapper
import com.medisense.app.domain.portability.PortableHealthDataValidator
import com.medisense.app.domain.quality.*
import com.medisense.app.domain.risk.ContextAwareRiskEngine
import com.medisense.app.domain.trace.HealthDecisionTraceEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PortableImportUnitTest {

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
    private lateinit var portabilityExportRepo: PortableHealthDataRepository
    private lateinit var importRepo: PortableImportRepository

    private val testUserId = "user-import-test-11111"
    private val foreignUserId = "user-foreign-99999"

    @Before
    fun setUp() {
        fakeAuthService = FakeAuthService(userId = testUserId, email = "import_user@medisense.app")
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

        portabilityExportRepo = PortableHealthDataRepository(
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

        importRepo = PortableImportRepository(
            authService = fakeAuthService,
            healthProfileDao = fakeHealthProfileDao,
            medicationDao = fakeMedicationDao,
            appointmentDao = fakeAppointmentDao,
            predictionHistoryDao = fakePredictionDao,
            securityAuditRepository = securityAuditRepo
        )
    }

    // ==========================================
    // 1. BLANK OR EMPTY FILE VALIDATION
    // ==========================================

    @Test
    fun `test empty JSON string returns critical validation error`() {
        val preview = PortableHealthDataValidator.validateAndParse(
            rawJson = "",
            fileName = "empty.json"
        )
        assertFalse(preview.summary.isImportable)
        assertTrue(preview.issues.any { it.type == PortableImportIssueType.EMPTY_IMPORT })
        assertEquals(1, preview.summary.errorCount)
    }

    @Test
    fun `test whitespace only JSON string returns critical validation error`() {
        val preview = PortableHealthDataValidator.validateAndParse(
            rawJson = "   \n\t   ",
            fileName = "whitespace.json"
        )
        assertFalse(preview.summary.isImportable)
        assertTrue(preview.issues.any { it.type == PortableImportIssueType.EMPTY_IMPORT })
    }

    // ==========================================
    // 2. MALFORMED JSON SYNTAX VALIDATION
    // ==========================================

    @Test
    fun `test malformed JSON syntax returns invalid syntax issue`() {
        val malformedJson = "{ \"resourceType\": \"Bundle\", \"type\": \"collection\", \"entry\": [ { "
        val preview = PortableHealthDataValidator.validateAndParse(
            rawJson = malformedJson,
            fileName = "broken.json"
        )
        assertFalse(preview.summary.isImportable)
        assertTrue(preview.issues.any { it.type == PortableImportIssueType.INVALID_JSON })
    }

    // ==========================================
    // 3. SCHEMA & ENVELOPE VALIDATION
    // ==========================================

    @Test
    fun `test missing Bundle resourceType returns critical schema violation`() {
        val invalidEnvelopeJson = """
            {
                "type": "collection",
                "meta": {
                    "packageId": "pkg-1",
                    "schemaVersion": "1.0-interop",
                    "exportVersion": "1.0",
                    "appName": "MediSense",
                    "generatedAtTimestamp": 1000,
                    "generatedAtIso": "2026-09-07T00:00:00Z",
                    "sha256Fingerprint": "abc",
                    "totalResourceCount": 0,
                    "selectedCategories": []
                },
                "entry": []
            }
        """.trimIndent()
        val preview = PortableHealthDataValidator.validateAndParse(
            rawJson = invalidEnvelopeJson,
            fileName = "invalid_envelope.json"
        )
        assertFalse(preview.summary.isImportable)
        assertTrue(preview.issues.any { it.type == PortableImportIssueType.INVALID_RESOURCE_TYPE })
    }

    @Test
    fun `test unsupported schema version generates validation issue`() {
        val unsupportedVersionJson = """
            {
                "resourceType": "Bundle",
                "type": "collection",
                "meta": {
                    "packageId": "pkg-v99",
                    "schemaVersion": "99.0-future",
                    "exportVersion": "99.0",
                    "appName": "MediSense",
                    "generatedAtTimestamp": 1000,
                    "generatedAtIso": "2026-09-07T00:00:00Z",
                    "sha256Fingerprint": "abc",
                    "totalResourceCount": 0,
                    "selectedCategories": []
                },
                "entry": []
            }
        """.trimIndent()
        val preview = PortableHealthDataValidator.validateAndParse(
            rawJson = unsupportedVersionJson,
            fileName = "unsupported_version.json"
        )
        assertTrue(preview.issues.any { it.type == PortableImportIssueType.UNSUPPORTED_SCHEMA_VERSION })
    }

    // ==========================================
    // 4. RESOURCE LEVEL VALIDATION & REQUIRED FIELDS
    // ==========================================

    @Test
    fun `test medication statement with blank medicationName returns warning issue`() {
        val missingMedNameJson = """
            {
                "resourceType": "Bundle",
                "type": "collection",
                "meta": {
                    "packageId": "pkg-med-err",
                    "schemaVersion": "1.0-interop",
                    "exportVersion": "1.0",
                    "appName": "MediSense",
                    "generatedAtTimestamp": 1000,
                    "generatedAtIso": "2026-09-07T00:00:00Z",
                    "sha256Fingerprint": "abc",
                    "totalResourceCount": 1,
                    "selectedCategories": ["MEDICATIONS"]
                },
                "entry": [
                    {
                        "fullUrl": "urn:uuid:med-1",
                        "resource": {
                            "resourceType": "MedicationStatement",
                            "resourceId": "med-1",
                            "medicationName": "",
                            "isActive": true
                        }
                    }
                ]
            }
        """.trimIndent()
        val preview = PortableHealthDataValidator.validateAndParse(
            rawJson = missingMedNameJson,
            fileName = "missing_med_name.json"
        )
        assertTrue(preview.issues.any { it.type == PortableImportIssueType.MISSING_REQUIRED_FIELD })
    }

    // ==========================================
    // 5. DUPLICATE RESOURCE ID DETECTION
    // ==========================================

    @Test
    fun `test duplicate resource ids within the same bundle triggers duplicate issue`() {
        val duplicateIdsJson = """
            {
                "resourceType": "Bundle",
                "type": "collection",
                "meta": {
                    "packageId": "pkg-dups",
                    "schemaVersion": "1.0-interop",
                    "exportVersion": "1.0",
                    "appName": "MediSense",
                    "generatedAtTimestamp": 1000,
                    "generatedAtIso": "2026-09-07T00:00:00Z",
                    "sha256Fingerprint": "abc",
                    "totalResourceCount": 2,
                    "selectedCategories": ["MEDICATIONS"]
                },
                "entry": [
                    {
                        "fullUrl": "urn:uuid:same-id-1",
                        "resource": {
                            "resourceType": "MedicationStatement",
                            "resourceId": "duplicate-id-1",
                            "medicationName": "Aspirin",
                            "isActive": true
                        }
                    },
                    {
                        "fullUrl": "urn:uuid:same-id-2",
                        "resource": {
                            "resourceType": "MedicationStatement",
                            "resourceId": "duplicate-id-1",
                            "medicationName": "Ibuprofen",
                            "isActive": true
                        }
                    }
                ]
            }
        """.trimIndent()
        val preview = PortableHealthDataValidator.validateAndParse(
            rawJson = duplicateIdsJson,
            fileName = "duplicates.json"
        )
        assertTrue(preview.issues.any { it.type == PortableImportIssueType.DUPLICATE_RESOURCE })
    }

    // ==========================================
    // 6. CONFLICT DETECTION AGAINST LOCAL ROOM RECORDS
    // ==========================================

    @Test
    fun `test value conflict detected when medication dosage differs from local record`() {
        val localMeds = listOf(
            MedicationEntity(
                id = 101L,
                userId = testUserId,
                medicineName = "Metformin",
                dosage = "500",
                dosageUnit = "mg",
                frequency = "Twice daily",
                instructions = "With food",
                startDate = 1000L,
                endDate = null,
                active = true
            )
        )

        val importJson = """
            {
                "resourceType": "Bundle",
                "type": "collection",
                "meta": {
                    "packageId": "pkg-conflict",
                    "schemaVersion": "1.0-interop",
                    "exportVersion": "1.0",
                    "appName": "MediSense",
                    "generatedAtTimestamp": 1000,
                    "generatedAtIso": "2026-09-07T00:00:00Z",
                    "sha256Fingerprint": "abc",
                    "totalResourceCount": 1,
                    "selectedCategories": ["MEDICATIONS"]
                },
                "entry": [
                    {
                        "fullUrl": "urn:uuid:med-metformin",
                        "resource": {
                            "resourceType": "MedicationStatement",
                            "resourceId": "med-metformin",
                            "medicationName": "Metformin",
                            "dosageText": "1000mg ER daily",
                            "isActive": true
                        }
                    }
                ]
            }
        """.trimIndent()

        val preview = PortableHealthDataValidator.validateAndParse(
            rawJson = importJson,
            localMedications = localMeds,
            fileName = "conflict_med.json"
        )

        assertTrue(preview.conflicts.isNotEmpty())
        val conflict = preview.conflicts.firstOrNull { it.category == PortableDataCategory.MEDICATIONS }
        assertNotNull(conflict)
        assertEquals(ImportConflictType.VALUE_CONFLICT, conflict?.conflictType)
        assertTrue(conflict?.description?.contains("Metformin") == true)
    }

    @Test
    fun `test possible duplicate detected when imported medication matches local record exactly`() {
        val localMeds = listOf(
            MedicationEntity(
                id = 101L,
                userId = testUserId,
                medicineName = "Atorvastatin",
                dosage = "20mg",
                dosageUnit = "",
                frequency = "Once daily",
                instructions = "At bedtime",
                startDate = 1000L,
                endDate = null,
                active = true
            )
        )

        val importJson = """
            {
                "resourceType": "Bundle",
                "type": "collection",
                "meta": {
                    "packageId": "pkg-dup",
                    "schemaVersion": "1.0-interop",
                    "exportVersion": "1.0",
                    "appName": "MediSense",
                    "generatedAtTimestamp": 1000,
                    "generatedAtIso": "2026-09-07T00:00:00Z",
                    "sha256Fingerprint": "abc",
                    "totalResourceCount": 1,
                    "selectedCategories": ["MEDICATIONS"]
                },
                "entry": [
                    {
                        "fullUrl": "urn:uuid:med-atorva",
                        "resource": {
                            "resourceType": "MedicationStatement",
                            "resourceId": "med-atorva",
                            "medicationName": "Atorvastatin",
                            "dosageText": "20mg",
                            "isActive": true
                        }
                    }
                ]
            }
        """.trimIndent()

        val preview = PortableHealthDataValidator.validateAndParse(
            rawJson = importJson,
            localMedications = localMeds,
            fileName = "duplicate_med.json"
        )

        val duplicateConflict = preview.conflicts.firstOrNull { it.category == PortableDataCategory.MEDICATIONS }
        assertNotNull(duplicateConflict)
        assertEquals(ImportConflictType.POSSIBLE_DUPLICATE, duplicateConflict?.conflictType)
    }

    // ==========================================
    // 7. USER IDENTITY ISOLATION & ZERO MUTATION GUARANTEE
    // ==========================================

    @Test
    fun `test prepareValidatedPackage generates staging object without modifying Room database`() = runBlocking {
        // Precondition: 0 local medications and appointments
        assertEquals(0, fakeMedicationDao.getAllMedicationsForUserSync(testUserId).size)
        assertEquals(0, fakeAppointmentDao.getAllAppointmentsForUserSync(testUserId).size)

        val validImportJson = """
            {
                "resourceType": "Bundle",
                "type": "collection",
                "meta": {
                    "packageId": "pkg-stage-1",
                    "schemaVersion": "1.0-interop",
                    "exportVersion": "1.0",
                    "appName": "MediSense",
                    "generatedAtTimestamp": 1000,
                    "generatedAtIso": "2026-09-07T00:00:00Z",
                    "sha256Fingerprint": "abc12345",
                    "totalResourceCount": 1,
                    "selectedCategories": ["MEDICATIONS"]
                },
                "entry": [
                    {
                        "fullUrl": "urn:uuid:med-sample",
                        "resource": {
                            "resourceType": "MedicationStatement",
                            "resourceId": "med-sample",
                            "medicationName": "Lisinopril",
                            "dosageText": "10mg",
                            "isActive": true
                        }
                    }
                ]
            }
        """.trimIndent()

        val preview = PortableHealthDataValidator.validateAndParse(
            rawJson = validImportJson,
            fileName = "stage_test.json"
        )
        val result = importRepo.prepareValidatedPackage(preview, userConfirmed = true)

        assertTrue(result.isSuccess)
        val validatedPackage = result.getOrNull()
        assertNotNull(validatedPackage)
        assertEquals("pkg-stage-1", validatedPackage?.packageId)
        assertEquals(testUserId, validatedPackage?.targetUserId)

        // Verify Room DAOs were NOT written to
        assertEquals(0, fakeMedicationDao.getAllMedicationsForUserSync(testUserId).size)
        assertEquals(0, fakeAppointmentDao.getAllAppointmentsForUserSync(testUserId).size)
    }

    @Test
    fun `test prepareValidatedPackage fails if user confirmation is false`() = runBlocking {
        val validImportJson = """
            {
                "resourceType": "Bundle",
                "type": "collection",
                "meta": {
                    "packageId": "pkg-unconfirmed",
                    "schemaVersion": "1.0-interop",
                    "exportVersion": "1.0",
                    "appName": "MediSense",
                    "generatedAtTimestamp": 1000,
                    "generatedAtIso": "2026-09-07T00:00:00Z",
                    "sha256Fingerprint": "abc",
                    "totalResourceCount": 0,
                    "selectedCategories": []
                },
                "entry": []
            }
        """.trimIndent()

        val preview = PortableHealthDataValidator.validateAndParse(validImportJson)
        val result = importRepo.prepareValidatedPackage(preview, userConfirmed = false)

        assertTrue(result.isFailure)
    }

    // ==========================================
    // 8. SECURITY AUDIT LOGGING TELEMETRY
    // ==========================================

    @Test
    fun `test security audit logs are recorded on preview and package staging`() = runBlocking {
        val initialAuditCount = fakeSecurityAuditDao.getAuditEventsForUser(testUserId).size

        importRepo.recordImportPreviewed("pkg-audit-test")
        val auditEventsAfterPreview = fakeSecurityAuditDao.getAuditEventsForUser(testUserId)
        assertTrue(auditEventsAfterPreview.any { it.eventType == SecurityAuditEventType.PORTABLE_IMPORT_PREVIEWED.name })

        importRepo.recordImportCancelled("User dismissed")
        val auditEventsAfterCancel = fakeSecurityAuditDao.getAuditEventsForUser(testUserId)
        assertTrue(auditEventsAfterCancel.any { it.eventType == SecurityAuditEventType.PORTABLE_IMPORT_CANCELLED.name })
    }

    // ==========================================
    // 9. END-TO-END ROUNDTRIP EXPORT -> IMPORT TEST
    // ==========================================

    @Test
    fun `test full export bundle generated by MediSense passes import validation cleanly`() = runBlocking {
        // Seed full test data
        fakeHealthProfileDao.insertHealthProfile(
            HealthProfileEntity(
                id = "prof-1",
                userId = testUserId,
                fullName = "Dr. Jane Smith",
                dateOfBirth = "1988-06-12",
                gender = "Female",
                bloodGroup = "O+",
                height = 168.0,
                weight = 62.0,
                allergies = "Penicillin",
                existingDiseases = "None",
                currentMedications = "Levothyroxine",
                familyHistory = null,
                emergencyContactName = "John Smith",
                emergencyContactNumber = "+15550199",
                notes = null
            )
        )

        fakeMedicationDao.insertMedication(
            MedicationEntity(
                id = 201L,
                userId = testUserId,
                medicineName = "Levothyroxine",
                dosage = "50",
                dosageUnit = "mcg",
                frequency = "Once daily in the morning",
                instructions = "Take before breakfast",
                startDate = 1000L,
                endDate = null,
                active = true
            )
        )

        fakeAppointmentDao.insertAppointment(
            AppointmentEntity(
                id = 301L,
                userId = testUserId,
                doctorName = "Dr. House",
                clinicName = "Diagnostic Clinic Rm 302",
                appointmentType = "Endocrinology",
                appointmentDate = "2026-09-10",
                appointmentTime = "10:30 AM",
                appointmentTimestamp = System.currentTimeMillis() + 86400000L,
                reminderMinutesBefore = 30,
                notes = "Routine thyroid level checkup",
                status = "SCHEDULED"
            )
        )

        fakePredictionDao.insertPredictionHistory(
            PredictionHistoryEntity(
                id = 401L,
                userId = testUserId,
                predictedDisease = "Diabetes Risk",
                confidence = 0.88f,
                symptoms = listOf("Increased thirst", "Fatigue"),
                explanationSummary = "Elevated blood glucose indicators",
                predictionTimestamp = System.currentTimeMillis()
            )
        )

        // Generate complete export JSON bundle
        val scope = PortableExportScope(
            selectedCategories = PortableDataCategory.entries.toSet(),
            userConfirmationAccepted = true
        )
        val profile = fakeHealthProfileDao.getHealthProfile(testUserId)
        val medications = fakeMedicationDao.getAllMedicationsForUserSync(testUserId)
        val appointments = fakeAppointmentDao.getAllAppointmentsForUserSync(testUserId)
        val predictions = fakePredictionDao.getAllPredictionHistoryForUserSync(testUserId)

        val bundle = PortableHealthDataMapper.mapToBundle(
            userId = testUserId,
            scope = scope,
            profile = profile,
            medications = medications,
            medicationHistory = emptyList(),
            adherenceStats = null,
            appointments = appointments,
            predictions = predictions,
            longitudinal = null,
            personalContext = null,
            riskAssessment = null,
            guidance = null,
            rchr = null,
            decisionTraces = emptyList(),
            timelineEvents = emptyList(),
            emergencyCard = null,
            qualitySummary = null
        )

        val fullJson = com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(bundle)
        assertNotNull(fullJson)
        assertTrue(fullJson.contains("\"resourceType\": \"Bundle\""))

        // Validate the exported JSON string
        val preview = PortableHealthDataValidator.validateAndParse(
            rawJson = fullJson,
            fileName = "full_export.json"
        )

        assertTrue("Generated bundle must be valid and importable", preview.summary.isImportable)
        assertTrue("Total resource count must be > 0", preview.summary.totalResources > 0)
        assertEquals(0, preview.summary.errorCount)
        assertNotNull(preview.summary.packageId)

        // Ensure category preview tallies are present
        assertTrue(preview.categories.isNotEmpty())
        val medCat = preview.categories.find { it.category == PortableDataCategory.MEDICATIONS }
        assertNotNull(medCat)
        assertEquals(1, medCat?.recordCount)
    }

    // ==========================================
    // FAKE DAOS & AUTH SERVICE
    // ==========================================

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
