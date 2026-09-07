package com.medisense.app

import com.medisense.app.data.local.dao.*
import com.medisense.app.data.local.entity.*
import com.medisense.app.data.remote.supabase.AuthService
import com.medisense.app.data.repository.*
import com.medisense.app.domain.guidance.PersonalizedGuidanceEngine
import com.medisense.app.domain.model.*
import com.medisense.app.domain.quality.*
import com.medisense.app.domain.report.HealthReportGenerator
import com.medisense.app.domain.risk.ContextAwareRiskEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class HealthReportUnitTest {

    private lateinit var fakeHealthProfileDao: FakeHealthProfileDao
    private lateinit var fakeMedicationDao: FakeMedicationDao
    private lateinit var fakeMedicationHistoryDao: FakeMedicationHistoryDao
    private lateinit var fakeAppointmentDao: FakeAppointmentDao
    private lateinit var fakePredictionHistoryDao: FakePredictionHistoryDao
    private lateinit var fakeSyncMetadataDao: FakeSyncMetadataDao
    private lateinit var fakeSecurityAuditDao: FakeSecurityAuditEventDao
    private lateinit var fakeAuthService: FakeAuthService

    private lateinit var securityAuditRepository: SecurityAuditRepository
    private lateinit var healthDataQualityRepository: HealthDataQualityRepository
    private lateinit var personalHealthContextRepository: PersonalHealthContextRepository
    private lateinit var longitudinalHealthRepository: LongitudinalHealthRepository
    private lateinit var rchrRepository: RchrRepository
    private lateinit var contextualRiskRepository: ContextualRiskRepository
    private lateinit var personalizedGuidanceRepository: PersonalizedGuidanceRepository
    private lateinit var healthReportRepository: HealthReportRepository

    private val testUserId = "user-report-12345"
    private val otherUserId = "user-report-99999"

    @Before
    fun setUp() {
        fakeHealthProfileDao = FakeHealthProfileDao()
        fakeMedicationDao = FakeMedicationDao()
        fakeMedicationHistoryDao = FakeMedicationHistoryDao()
        fakeAppointmentDao = FakeAppointmentDao()
        fakePredictionHistoryDao = FakePredictionHistoryDao()
        fakeSyncMetadataDao = FakeSyncMetadataDao()
        fakeSecurityAuditDao = FakeSecurityAuditEventDao()

        fakeAuthService = FakeAuthService(userId = testUserId, email = "report_user@medisense.app")
        securityAuditRepository = SecurityAuditRepository(fakeSecurityAuditDao, fakeAuthService)

        val qualityEngine = HealthDataQualityEngine(
            profileValidator = ProfileDataValidator(),
            medicationValidator = MedicationDataValidator(),
            appointmentValidator = AppointmentDataValidator(),
            predictionValidator = PredictionDataValidator(),
            temporalValidator = TemporalConsistencyValidator()
        )

        healthDataQualityRepository = HealthDataQualityRepository(
            authService = fakeAuthService,
            qualityEngine = qualityEngine,
            healthProfileDao = fakeHealthProfileDao,
            medicationDao = fakeMedicationDao,
            medicationHistoryDao = fakeMedicationHistoryDao,
            appointmentDao = fakeAppointmentDao,
            predictionHistoryDao = fakePredictionHistoryDao,
            syncMetadataDao = fakeSyncMetadataDao,
            securityAuditRepository = securityAuditRepository
        )

        personalHealthContextRepository = PersonalHealthContextRepository(
            healthProfileDao = fakeHealthProfileDao,
            predictionHistoryDao = fakePredictionHistoryDao,
            medicationDao = fakeMedicationDao,
            medicationHistoryDao = fakeMedicationHistoryDao,
            appointmentDao = fakeAppointmentDao,
            authService = fakeAuthService
        )

        longitudinalHealthRepository = LongitudinalHealthRepository(
            predictionHistoryDao = fakePredictionHistoryDao,
            medicationDao = fakeMedicationDao,
            medicationHistoryDao = fakeMedicationHistoryDao,
            appointmentDao = fakeAppointmentDao,
            authService = fakeAuthService
        )

        rchrRepository = RchrRepository(
            healthProfileDao = fakeHealthProfileDao,
            predictionHistoryDao = fakePredictionHistoryDao,
            medicationDao = fakeMedicationDao,
            medicationHistoryDao = fakeMedicationHistoryDao,
            appointmentDao = fakeAppointmentDao,
            authService = fakeAuthService
        )

        contextualRiskRepository = ContextualRiskRepository(
            personalHealthContextRepository = personalHealthContextRepository,
            longitudinalHealthRepository = longitudinalHealthRepository,
            rchrRepository = rchrRepository,
            riskEngine = ContextAwareRiskEngine(),
            authService = fakeAuthService
        )

        personalizedGuidanceRepository = PersonalizedGuidanceRepository(
            personalHealthContextRepository = personalHealthContextRepository,
            longitudinalHealthRepository = longitudinalHealthRepository,
            rchrRepository = rchrRepository,
            contextualRiskRepository = contextualRiskRepository,
            guidanceEngine = PersonalizedGuidanceEngine(),
            authService = fakeAuthService
        )

        healthReportRepository = HealthReportRepository(
            authService = fakeAuthService,
            healthProfileDao = fakeHealthProfileDao,
            medicationDao = fakeMedicationDao,
            medicationHistoryDao = fakeMedicationHistoryDao,
            appointmentDao = fakeAppointmentDao,
            predictionHistoryDao = fakePredictionHistoryDao,
            syncMetadataDao = fakeSyncMetadataDao,
            personalHealthContextRepository = personalHealthContextRepository,
            longitudinalHealthRepository = longitudinalHealthRepository,
            rchrRepository = rchrRepository,
            contextualRiskRepository = contextualRiskRepository,
            personalizedGuidanceRepository = personalizedGuidanceRepository,
            healthDataQualityRepository = healthDataQualityRepository,
            securityAuditRepository = securityAuditRepository
        )
    }

    // ==========================================
    // 1. Profile Generation Tests
    // ==========================================

    @Test
    fun `complete profile produces 100 percent completeness and accurate BMI`() = runBlocking {
        val profile = HealthProfileEntity(
            id = "p-1",
            userId = testUserId,
            fullName = "Sarah Connor",
            dateOfBirth = "1985-06-15",
            gender = "Female",
            bloodGroup = "A+",
            height = 170.0,
            weight = 65.0,
            allergies = "Penicillin",
            existingDiseases = "Hypertension",
            currentMedications = "Amlodipine",
            familyHistory = "Cardiovascular",
            emergencyContactName = "John Connor",
            emergencyContactNumber = "555-0199",
            notes = "Annual checkup pending"
        )
        fakeHealthProfileDao.insertHealthProfile(profile)

        val report = healthReportRepository.generateHealthReport()

        assertEquals("Sarah Connor", report.profile.fullName)
        assertEquals("1985-06-15", report.profile.dateOfBirth)
        assertEquals("Female", report.profile.gender)
        assertEquals("A+", report.profile.bloodGroup)
        assertEquals(170.0, report.profile.heightCm ?: 0.0, 0.01)
        assertEquals(65.0, report.profile.weightKg ?: 0.0, 0.01)
        assertEquals(22.5, report.profile.bmi ?: 0.0, 0.1) // 65 / (1.7^2) = 22.49 -> 22.5
        assertEquals(100, report.profile.completenessPercentage)
        assertTrue(report.profile.incompleteFields.isEmpty())
    }

    @Test
    fun `incomplete profile flags missing fields and reduces completeness percentage`() = runBlocking {
        val incompleteProfile = HealthProfileEntity(
            id = "p-2",
            userId = testUserId,
            fullName = "John Doe",
            dateOfBirth = null,
            gender = "Male",
            bloodGroup = null,
            height = null,
            weight = null,
            allergies = null,
            existingDiseases = null,
            currentMedications = null,
            familyHistory = null,
            emergencyContactName = null,
            emergencyContactNumber = null,
            notes = null
        )
        fakeHealthProfileDao.insertHealthProfile(incompleteProfile)

        val report = healthReportRepository.generateHealthReport()

        assertTrue(report.profile.completenessPercentage < 50)
        assertTrue(report.profile.incompleteFields.contains("Date of Birth"))
        assertTrue(report.profile.incompleteFields.contains("Blood Group"))
        assertTrue(report.profile.incompleteFields.contains("Height"))
        assertTrue(report.profile.incompleteFields.contains("Weight"))
        assertTrue(report.profile.incompleteFields.contains("Emergency Contact"))
        assertNull(report.profile.bmi)
    }

    @Test
    fun `new user with zero records generates safe default report without crash`() = runBlocking {
        val report = healthReportRepository.generateHealthReport()

        assertNotNull(report)
        assertEquals("MediSense Comprehensive Personal Health Report", report.metadata.title)
        assertEquals(0, report.profile.completenessPercentage)
        assertEquals(0, report.predictions.totalPredictions)
        assertEquals(0, report.medications.activeMedicationsCount)
        assertEquals(0, report.appointments.totalAppointments)
        assertNotNull(report.safetyDisclaimer)
        assertNotNull(report.rchr.explanationNotice)
    }

    // ==========================================
    // 2. Predictions & Uncertainty Tests
    // ==========================================

    @Test
    fun `prediction history aggregates confidence, symptoms and model uncertainty`() = runBlocking {
        val pred1 = PredictionHistoryEntity(
            id = 1L,
            userId = testUserId,
            predictedDisease = "Common Cold",
            confidence = 0.88f,
            symptoms = listOf("cough", "sneezing", "sore_throat"),
            explanationSummary = "Classic upper respiratory viral presentation",
            predictionTimestamp = System.currentTimeMillis() - 100000L,
            modelVersion = "1.0"
        )
        val pred2 = PredictionHistoryEntity(
            id = 2L,
            userId = testUserId,
            predictedDisease = "Allergic Rhinitis",
            confidence = 0.62f,
            symptoms = listOf("sneezing", "runny_nose"),
            explanationSummary = "Seasonal histamine response pattern",
            predictionTimestamp = System.currentTimeMillis() - 50000L,
            modelVersion = "1.0"
        )
        fakePredictionHistoryDao.insertPredictionHistory(pred1)
        fakePredictionHistoryDao.insertPredictionHistory(pred2)

        val report = healthReportRepository.generateHealthReport()

        assertEquals(2, report.predictions.totalPredictions)
        assertEquals(75, report.predictions.averageConfidencePercentage) // (88 + 62) / 2 = 75
        assertEquals(2, report.predictions.recentPredictions.size)

        val firstItem = report.predictions.recentPredictions[0] // Sorted newest first (pred2)
        assertEquals("Allergic Rhinitis", firstItem.predictedDisease)
        assertEquals(62, firstItem.confidencePercentage)
        assertEquals("Moderate Confidence", firstItem.confidenceLevel)
        assertNotNull(firstItem.uncertaintyInterpretation)

        val secondItem = report.predictions.recentPredictions[1] // pred1
        assertEquals("Common Cold", secondItem.predictedDisease)
        assertEquals(88, secondItem.confidencePercentage)
        assertEquals("High Confidence", secondItem.confidenceLevel)
    }

    // ==========================================
    // 3. Medication & Adherence Tests
    // ==========================================

    @Test
    fun `medications and adherence statistics calculate correctly`() = runBlocking {
        val med1 = MedicationEntity(
            id = 10L,
            userId = testUserId,
            medicineName = "Lisinopril",
            dosage = "10",
            dosageUnit = "mg",
            frequency = "Daily",
            scheduledTimes = listOf("08:00 AM"),
            startDate = System.currentTimeMillis() - 86400000L * 10,
            active = true
        )
        val med2 = MedicationEntity(
            id = 11L,
            userId = testUserId,
            medicineName = "Metformin",
            dosage = "500",
            dosageUnit = "mg",
            frequency = "Twice Daily",
            scheduledTimes = listOf("08:00 AM", "08:00 PM"),
            startDate = System.currentTimeMillis() - 86400000L * 10,
            active = false
        )
        fakeMedicationDao.insertMedication(med1)
        fakeMedicationDao.insertMedication(med2)

        // 8 Taken, 1 Skipped, 1 Missed = 80% adherence
        for (i in 1..8) {
            fakeMedicationHistoryDao.insertHistory(
                MedicationHistoryEntity(
                    id = i.toLong(),
                    medicationId = 10L,
                    userId = testUserId,
                    medicineName = "Lisinopril",
                    dosage = "10 mg",
                    scheduledDate = System.currentTimeMillis() - i * 86400000L,
                    scheduledTime = "08:00 AM",
                    status = "TAKEN"
                )
            )
        }
        fakeMedicationHistoryDao.insertHistory(
            MedicationHistoryEntity(
                id = 9L,
                medicationId = 10L,
                userId = testUserId,
                medicineName = "Lisinopril",
                dosage = "10 mg",
                scheduledDate = System.currentTimeMillis() - 9 * 86400000L,
                scheduledTime = "08:00 AM",
                status = "SKIPPED"
            )
        )
        fakeMedicationHistoryDao.insertHistory(
            MedicationHistoryEntity(
                id = 10L,
                medicationId = 10L,
                userId = testUserId,
                medicineName = "Lisinopril",
                dosage = "10 mg",
                scheduledDate = System.currentTimeMillis() - 10 * 86400000L,
                scheduledTime = "08:00 AM",
                status = "MISSED"
            )
        )

        val report = healthReportRepository.generateHealthReport()

        assertEquals(1, report.medications.activeMedicationsCount)
        assertEquals(2, report.medications.totalMedicationsCount)
        assertEquals(10, report.medications.totalDosesLogged)
        assertEquals(8, report.medications.takenDosesCount)
        assertEquals(1, report.medications.skippedDosesCount)
        assertEquals(1, report.medications.missedDosesCount)
        assertEquals(80, report.medications.adherencePercentage)
        assertEquals("Good (75–89%)", report.medications.adherenceRating)
    }

    // ==========================================
    // 4. Appointments Tests
    // ==========================================

    @Test
    fun `appointments correctly segment into upcoming and past categories`() = runBlocking {
        val now = System.currentTimeMillis()
        val upcomingApt = AppointmentEntity(
            id = 101L,
            userId = testUserId,
            doctorName = "Dr. Robert Smith",
            clinicName = "City Cardiology Clinic",
            appointmentType = "Checkup",
            appointmentDate = "2026-09-15",
            appointmentTime = "10:30 AM",
            appointmentTimestamp = now + 86400000L * 8,
            reminderMinutesBefore = 60,
            status = "CONFIRMED"
        )
        val pastApt = AppointmentEntity(
            id = 102L,
            userId = testUserId,
            doctorName = "Dr. Alice Wong",
            clinicName = "Central Dermatology Center",
            appointmentType = "Consultation",
            appointmentDate = "2026-08-01",
            appointmentTime = "02:00 PM",
            appointmentTimestamp = now - 86400000L * 30,
            reminderMinutesBefore = 30,
            status = "COMPLETED"
        )
        fakeAppointmentDao.insertAppointment(upcomingApt)
        fakeAppointmentDao.insertAppointment(pastApt)

        val report = healthReportRepository.generateHealthReport()

        assertEquals(2, report.appointments.totalAppointments)
        assertEquals(1, report.appointments.upcomingAppointments.size)
        assertEquals("Dr. Robert Smith", report.appointments.upcomingAppointments[0].doctorName)
        assertEquals(1, report.appointments.pastAppointments.size)
        assertEquals("Dr. Alice Wong", report.appointments.pastAppointments[0].doctorName)
    }

    // ==========================================
    // 5. RCHR, Risk, Guidance & Disclaimers
    // ==========================================

    @Test
    fun `report includes mandatory medical safety notice and RCHR explanation verbatim`() = runBlocking {
        val report = healthReportRepository.generateHealthReport()

        val expectedSafetyNotice =
            "Medical Safety Notice: MediSense provides educational and health-management information based on the data available in the application. Model predictions, trends, contextual indicators, and personalized guidance are not medical diagnoses or treatment plans and should not replace advice from a qualified healthcare professional."

        val expectedRchrNotice =
            "The reconstruction consistency score describes how consistently stored health-management information can be reconstructed from the structured representation. It is not a measure of medical accuracy."

        assertEquals(expectedSafetyNotice, report.safetyDisclaimer)
        assertEquals(expectedRchrNotice, report.rchr.explanationNotice)
    }

    @Test
    fun `data quality metrics from module 16 integrate smoothly into report`() = runBlocking {
        val validProfile = HealthProfileEntity(
            id = "p-1",
            userId = testUserId,
            fullName = "Alice Green",
            dateOfBirth = "1992-03-20",
            gender = "Female",
            bloodGroup = "B+",
            height = 165.0,
            weight = 58.0,
            allergies = "None",
            existingDiseases = "None",
            currentMedications = "None",
            familyHistory = "None",
            emergencyContactName = "Bob Green",
            emergencyContactNumber = "555-1234",
            notes = "Good health"
        )
        fakeHealthProfileDao.insertHealthProfile(validProfile)

        val report = healthReportRepository.generateHealthReport()

        assertEquals(HealthDataQualityStatus.GOOD, report.dataQuality.status)
        assertEquals(100, report.dataQuality.qualityScore)
        assertEquals(0, report.dataQuality.errorCount)
        assertEquals(0, report.dataQuality.warningCount)
    }

    // ==========================================
    // 6. User Isolation Tests
    // ==========================================

    @Test
    fun `strict user isolation prevents access to other user health records`() = runBlocking {
        // Records for another user
        fakeHealthProfileDao.insertHealthProfile(
            HealthProfileEntity(
                id = "p-other",
                userId = otherUserId,
                fullName = "Victim Profile",
                dateOfBirth = "1970-01-01",
                gender = "Male",
                bloodGroup = "AB+",
                height = 180.0,
                weight = 80.0,
                allergies = "Severe Peanut Allergy",
                existingDiseases = "Diabetes",
                currentMedications = "Insulin",
                familyHistory = "Diabetes",
                emergencyContactName = "Other Contact",
                emergencyContactNumber = "999",
                notes = "Confidential"
            )
        )
        fakeMedicationDao.insertMedication(
            MedicationEntity(
                id = 999L,
                userId = otherUserId,
                medicineName = "Insulin Glargine",
                dosage = "20",
                dosageUnit = "units",
                frequency = "Nightly",
                scheduledTimes = listOf("10:00 PM"),
                startDate = System.currentTimeMillis(),
                active = true
            )
        )
        fakePredictionHistoryDao.insertPredictionHistory(
            PredictionHistoryEntity(
                id = 888L,
                userId = otherUserId,
                predictedDisease = "Diabetes Type 2",
                confidence = 0.95f,
                symptoms = listOf("polyuria", "polydipsia"),
                predictionTimestamp = System.currentTimeMillis()
            )
        )

        // Generate report for testUserId (who has 0 records)
        val report = healthReportRepository.generateHealthReport()

        assertNull(report.profile.fullName)
        assertEquals(0, report.medications.activeMedicationsCount)
        assertEquals(0, report.predictions.totalPredictions)
    }

    // ==========================================
    // 7. Security Audit Telemetry Tests
    // ==========================================

    @Test
    fun `report generation and sharing record privacy-safe audit telemetry`() = runBlocking {
        healthReportRepository.generateHealthReport()
        healthReportRepository.recordReportSharedAudit()

        val auditList = fakeSecurityAuditDao.getAuditEventsForUser(testUserId)
        assertTrue(auditList.size >= 2)

        val genEvent = auditList.find { it.eventType == SecurityAuditEventType.HEALTH_REPORT_GENERATED.name }
        assertNotNull(genEvent)
        assertEquals(testUserId, genEvent?.userId)
        assertFalse(genEvent?.description?.contains("Insulin") ?: false)
        assertFalse(genEvent?.description?.contains("Diabetes") ?: false)

        val shareEvent = auditList.find { it.eventType == SecurityAuditEventType.HEALTH_REPORT_SHARED.name }
        assertNotNull(shareEvent)
        assertEquals(testUserId, shareEvent?.userId)
    }

    // ==========================================
    // 8. Deterministic & Non-Destructive Test
    // ==========================================

    @Test
    fun `report generation is deterministic and does not mutate underlying records`() = runBlocking {
        val profile = HealthProfileEntity(
            id = "p-det",
            userId = testUserId,
            fullName = "Deterministic Test",
            dateOfBirth = "1995-10-10",
            gender = "Other",
            bloodGroup = "O-",
            height = 175.0,
            weight = 70.0,
            allergies = "None",
            existingDiseases = "None",
            currentMedications = "None",
            familyHistory = "None",
            emergencyContactName = "Contact",
            emergencyContactNumber = "123",
            notes = "Test"
        )
        fakeHealthProfileDao.insertHealthProfile(profile)

        val report1 = healthReportRepository.generateHealthReport()
        val report2 = healthReportRepository.generateHealthReport()

        assertEquals(report1.profile.fullName, report2.profile.fullName)
        assertEquals(report1.profile.completenessPercentage, report2.profile.completenessPercentage)
        assertEquals(report1.profile.bmi, report2.profile.bmi)
        assertEquals(report1.dataQuality.status, report2.dataQuality.status)

        // Verify underlying Room entity is unmodified
        val stored = fakeHealthProfileDao.getHealthProfile(testUserId)
        assertEquals("Deterministic Test", stored?.fullName)
        assertEquals("1995-10-10", stored?.dateOfBirth)
    }

    // ==========================================
    // Test Doubles / In-memory Fakes
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
        fun setSession(newUserId: String?, newEmail: String?) {
            userId = newUserId
            email = newEmail
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
