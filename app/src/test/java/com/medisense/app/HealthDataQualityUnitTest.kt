package com.medisense.app

import com.medisense.app.data.local.dao.*
import com.medisense.app.data.local.entity.*
import com.medisense.app.data.remote.supabase.AuthService
import com.medisense.app.data.repository.HealthDataQualityRepository
import com.medisense.app.data.repository.SecurityAuditRepository
import com.medisense.app.domain.model.HealthDataQualityCategory
import com.medisense.app.domain.model.HealthDataQualitySeverity
import com.medisense.app.domain.model.HealthDataQualityStatus
import com.medisense.app.domain.model.SecurityAuditEventType
import com.medisense.app.domain.quality.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class HealthDataQualityUnitTest {

    private lateinit var profileValidator: ProfileDataValidator
    private lateinit var medicationValidator: MedicationDataValidator
    private lateinit var appointmentValidator: AppointmentDataValidator
    private lateinit var predictionValidator: PredictionDataValidator
    private lateinit var temporalValidator: TemporalConsistencyValidator
    private lateinit var qualityEngine: HealthDataQualityEngine

    private lateinit var fakeHealthProfileDao: FakeHealthProfileDao
    private lateinit var fakeMedicationDao: FakeMedicationDao
    private lateinit var fakeMedicationHistoryDao: FakeMedicationHistoryDao
    private lateinit var fakeAppointmentDao: FakeAppointmentDao
    private lateinit var fakePredictionHistoryDao: FakePredictionHistoryDao
    private lateinit var fakeSyncMetadataDao: FakeSyncMetadataDao
    private lateinit var fakeSecurityAuditDao: FakeSecurityAuditEventDao
    private lateinit var fakeAuthService: FakeAuthService
    private lateinit var securityAuditRepository: SecurityAuditRepository
    private lateinit var qualityRepository: HealthDataQualityRepository

    private val testUserId = "user-quality-12345"
    private val otherUserId = "user-other-99999"

    @Before
    fun setUp() {
        profileValidator = ProfileDataValidator()
        medicationValidator = MedicationDataValidator()
        appointmentValidator = AppointmentDataValidator()
        predictionValidator = PredictionDataValidator()
        temporalValidator = TemporalConsistencyValidator()

        qualityEngine = HealthDataQualityEngine(
            profileValidator = profileValidator,
            medicationValidator = medicationValidator,
            appointmentValidator = appointmentValidator,
            predictionValidator = predictionValidator,
            temporalValidator = temporalValidator
        )

        fakeHealthProfileDao = FakeHealthProfileDao()
        fakeMedicationDao = FakeMedicationDao()
        fakeMedicationHistoryDao = FakeMedicationHistoryDao()
        fakeAppointmentDao = FakeAppointmentDao()
        fakePredictionHistoryDao = FakePredictionHistoryDao()
        fakeSyncMetadataDao = FakeSyncMetadataDao()
        fakeSecurityAuditDao = FakeSecurityAuditEventDao()

        fakeAuthService = FakeAuthService(userId = testUserId, email = "quality@medisense.app")
        securityAuditRepository = SecurityAuditRepository(fakeSecurityAuditDao, fakeAuthService)

        qualityRepository = HealthDataQualityRepository(
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
    }

    @Test
    fun `complete valid profile produces zero profile issues`() = runBlocking {
        val validProfile = HealthProfileEntity(
            id = "p-1",
            userId = testUserId,
            fullName = "Jane Doe",
            dateOfBirth = "1990-05-15",
            gender = "Female",
            bloodGroup = "O+",
            height = 168.0,
            weight = 62.0,
            allergies = "None",
            existingDiseases = "None",
            currentMedications = "None",
            familyHistory = "None",
            emergencyContactName = "John Doe",
            emergencyContactNumber = "1234567890",
            notes = "Healthy"
        )

        val result = profileValidator.validate(validProfile)
        assertEquals(0, result.issues.size)
        assertTrue(result.passedChecks > 0)
        assertEquals(result.totalChecks, result.passedChecks)
    }

    @Test
    fun `incomplete profile flags missing required fields with PROFILE_COMPLETENESS`() = runBlocking {
        val incompleteProfile = HealthProfileEntity(
            id = "p-2",
            userId = testUserId,
            fullName = null,
            dateOfBirth = null,
            gender = null,
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

        val result = profileValidator.validate(incompleteProfile)
        assertTrue(result.issues.any { it.category == HealthDataQualityCategory.PROFILE_COMPLETENESS && it.id == "prof_missing_fullname" })
        assertTrue(result.issues.any { it.category == HealthDataQualityCategory.PROFILE_COMPLETENESS && it.id == "prof_missing_dob" })
        assertTrue(result.issues.any { it.category == HealthDataQualityCategory.PROFILE_COMPLETENESS && it.id == "prof_missing_gender" })
        assertTrue(result.issues.any { it.category == HealthDataQualityCategory.PROFILE_COMPLETENESS && it.id == "prof_missing_blood_group" })
    }

    @Test
    fun `non-positive height and weight values flag PROFILE_VALIDITY error`() = runBlocking {
        val invalidMeasurementsProfile = HealthProfileEntity(
            id = "p-3",
            userId = testUserId,
            fullName = "John Smith",
            dateOfBirth = "1985-02-10",
            gender = "Male",
            bloodGroup = "A+",
            height = -150.0, // Invalid
            weight = 0.0,    // Invalid
            allergies = null,
            existingDiseases = null,
            currentMedications = null,
            familyHistory = null,
            emergencyContactName = null,
            emergencyContactNumber = null,
            notes = null
        )

        val result = profileValidator.validate(invalidMeasurementsProfile)
        val heightError = result.issues.find { it.id == "prof_invalid_height" }
        val weightError = result.issues.find { it.id == "prof_invalid_weight" }

        assertNotNull(heightError)
        assertEquals(HealthDataQualitySeverity.ERROR, heightError?.severity)
        assertNotNull(weightError)
        assertEquals(HealthDataQualitySeverity.ERROR, weightError?.severity)
    }

    @Test
    fun `future date of birth flags PROFILE_VALIDITY error`() = runBlocking {
        val futureDobProfile = HealthProfileEntity(
            id = "p-4",
            userId = testUserId,
            fullName = "Future User",
            dateOfBirth = "2099-01-01",
            gender = "Male",
            bloodGroup = "B+",
            height = 175.0,
            weight = 70.0,
            allergies = null,
            existingDiseases = null,
            currentMedications = null,
            familyHistory = null,
            emergencyContactName = null,
            emergencyContactNumber = null,
            notes = null
        )

        val result = profileValidator.validate(futureDobProfile)
        val futureDobError = result.issues.find { it.id == "prof_future_dob" }
        assertNotNull(futureDobError)
        assertEquals(HealthDataQualitySeverity.ERROR, futureDobError?.severity)
    }

    @Test
    fun `medication with end date earlier than start date flags MEDICATION_DATA error`() = runBlocking {
        val invalidMed = MedicationEntity(
            id = 1L,
            userId = testUserId,
            medicineName = "Amoxicillin",
            dosage = "500",
            dosageUnit = "mg",
            frequency = "THRICE_DAILY",
            scheduledTimes = listOf("08:00", "14:00", "20:00"),
            startDate = 500000L,
            endDate = 100000L, // End date < Start date
            active = true
        )

        val result = medicationValidator.validate(listOf(invalidMed), emptyList())
        val dateError = result.issues.find { it.id == "med_invalid_dates_1" }
        assertNotNull(dateError)
        assertEquals(HealthDataQualitySeverity.ERROR, dateError?.severity)
    }

    @Test
    fun `duplicate active medication schedule detection flags MEDICATION_DATA warning`() = runBlocking {
        val med1 = MedicationEntity(
            id = 10L,
            userId = testUserId,
            medicineName = "Metformin",
            dosage = "500",
            dosageUnit = "mg",
            frequency = "TWICE_DAILY",
            scheduledTimes = listOf("08:00", "20:00"),
            active = true
        )
        val med2 = MedicationEntity(
            id = 11L,
            userId = testUserId,
            medicineName = "metformin ", // Case-insensitive duplicate
            dosage = "500",
            dosageUnit = "mg",
            frequency = "TWICE_DAILY",
            scheduledTimes = listOf("08:00", "20:00"),
            active = true
        )

        val result = medicationValidator.validate(listOf(med1, med2), emptyList())
        val dupWarning = result.issues.find { it.category == HealthDataQualityCategory.MEDICATION_DATA && it.title.contains("Duplicate") }
        assertNotNull(dupWarning)
        assertEquals(HealthDataQualitySeverity.WARNING, dupWarning?.severity)
    }

    @Test
    fun `orphaned medication history referencing non-existent medication ID flags CROSS_RECORD_CONSISTENCY warning`() = runBlocking {
        val historyItem = MedicationHistoryEntity(
            id = 50L,
            medicationId = 9999L, // Does not exist in medication list
            userId = testUserId,
            medicineName = "Old Med",
            scheduledDate = 100000L,
            scheduledTime = "08:00"
        )

        val result = medicationValidator.validate(emptyList(), listOf(historyItem))
        val orphanedWarning = result.issues.find { it.id == "med_orphaned_history" }
        assertNotNull(orphanedWarning)
        assertEquals(HealthDataQualityCategory.CROSS_RECORD_CONSISTENCY, orphanedWarning?.category)
    }

    @Test
    fun `appointment with invalid timestamp flags APPOINTMENT_DATA error`() = runBlocking {
        val invalidApt = AppointmentEntity(
            id = 20L,
            userId = testUserId,
            doctorName = "Dr. Adams",
            clinicName = "Central Clinic",
            appointmentType = "Checkup",
            appointmentDate = "2026-09-01",
            appointmentTime = "10:00",
            appointmentTimestamp = 0L // Invalid
        )

        val result = appointmentValidator.validate(listOf(invalidApt))
        val timeError = result.issues.find { it.id == "apt_invalid_time_20" }
        assertNotNull(timeError)
        assertEquals(HealthDataQualitySeverity.ERROR, timeError?.severity)
    }

    @Test
    fun `duplicate appointment detection flags APPOINTMENT_DATA warning`() = runBlocking {
        val aptTime = System.currentTimeMillis() + 86400000L
        val apt1 = AppointmentEntity(
            id = 1L,
            userId = testUserId,
            doctorName = "Dr. Emily Stone",
            clinicName = "City Health",
            appointmentType = "Checkup",
            appointmentDate = "2026-09-10",
            appointmentTime = "11:00",
            appointmentTimestamp = aptTime,
            status = "SCHEDULED"
        )
        val apt2 = AppointmentEntity(
            id = 2L,
            userId = testUserId,
            doctorName = "dr. emily stone",
            clinicName = "City Health",
            appointmentType = "Followup",
            appointmentDate = "2026-09-10",
            appointmentTime = "11:00",
            appointmentTimestamp = aptTime,
            status = "SCHEDULED"
        )

        val result = appointmentValidator.validate(listOf(apt1, apt2))
        val dupWarning = result.issues.find { it.title.contains("Duplicate Scheduled Appointment") }
        assertNotNull(dupWarning)
        assertEquals(HealthDataQualitySeverity.WARNING, dupWarning?.severity)
    }

    @Test
    fun `prediction history with invalid confidence or empty symptoms flags PREDICTION_HISTORY error`() = runBlocking {
        val invalidPred = PredictionHistoryEntity(
            id = 1L,
            userId = testUserId,
            predictedDisease = "Hypertension",
            confidence = 1.85f, // Invalid (> 1.0)
            symptoms = emptyList(), // Invalid (empty)
            predictionTimestamp = System.currentTimeMillis()
        )

        val result = predictionValidator.validate(listOf(invalidPred))
        val confError = result.issues.find { it.id == "pred_invalid_conf_1" }
        val symptomsError = result.issues.find { it.id == "pred_empty_symptoms_1" }

        assertNotNull(confError)
        assertEquals(HealthDataQualitySeverity.ERROR, confError?.severity)
        assertNotNull(symptomsError)
        assertEquals(HealthDataQualitySeverity.ERROR, symptomsError?.severity)
    }

    @Test
    fun `record with updatedAt earlier than createdAt flags TEMPORAL_CONSISTENCY error`() = runBlocking {
        val invertedMed = MedicationEntity(
            id = 5L,
            userId = testUserId,
            medicineName = "Lisinopril",
            dosage = "10",
            createdAt = 500000L,
            updatedAt = 200000L // updatedAt < createdAt
        )

        val result = temporalValidator.validate(
            medications = listOf(invertedMed),
            appointments = emptyList(),
            predictions = emptyList()
        )

        val tempError = result.issues.find { it.id == "temp_med_updated_before_created_5" }
        assertNotNull(tempError)
        assertEquals(HealthDataQualitySeverity.ERROR, tempError?.severity)
    }

    @Test
    fun `future prediction timestamp flags TEMPORAL_CONSISTENCY error`() = runBlocking {
        val now = System.currentTimeMillis()
        val futurePred = PredictionHistoryEntity(
            id = 10L,
            userId = testUserId,
            predictedDisease = "Asthma",
            confidence = 0.9f,
            symptoms = listOf("cough", "wheezing"),
            predictionTimestamp = now + 86400000L // 1 day in the future
        )

        val result = temporalValidator.validate(
            medications = emptyList(),
            appointments = emptyList(),
            predictions = listOf(futurePred),
            currentTimeMs = now
        )

        val futureError = result.issues.find { it.id == "temp_pred_future_10" }
        assertNotNull(futureError)
        assertEquals(HealthDataQualitySeverity.ERROR, futureError?.severity)
    }

    @Test
    fun `zero data returns INSUFFICIENT_DATA with null score`() = runBlocking {
        val summary = qualityEngine.evaluate(
            profile = null,
            medications = emptyList(),
            medicationHistory = emptyList(),
            appointments = emptyList(),
            predictions = emptyList(),
            syncMetadata = null
        )

        assertEquals(HealthDataQualityStatus.INSUFFICIENT_DATA, summary.status)
        assertNull(summary.qualityScore)
        assertEquals(0, summary.totalChecks)
        assertEquals(0, summary.issues.size)
    }

    @Test
    fun `unauthenticated user returns INSUFFICIENT_DATA and does not query database`() = runBlocking {
        fakeAuthService.setSession(null, null)

        val summary = qualityRepository.evaluateDataQuality()

        assertEquals(HealthDataQualityStatus.INSUFFICIENT_DATA, summary.status)
        assertNull(summary.qualityScore)
    }

    @Test
    fun `user isolation ensures evaluation queries only target user records`() = runBlocking {
        // Insert records for other user
        fakeHealthProfileDao.insertHealthProfile(
            HealthProfileEntity(
                id = "other-p",
                userId = otherUserId,
                fullName = "Other User",
                dateOfBirth = "1970-01-01",
                gender = "Male",
                bloodGroup = "B+",
                height = 180.0,
                weight = 75.0,
                allergies = null,
                existingDiseases = null,
                currentMedications = null,
                familyHistory = null,
                emergencyContactName = null,
                emergencyContactNumber = null,
                notes = null
            )
        )

        // Target user has no records
        val summary = qualityRepository.evaluateDataQuality()

        assertEquals(HealthDataQualityStatus.INSUFFICIENT_DATA, summary.status)
    }

    @Test
    fun `evaluation is deterministic and sorts issues by severity`() = runBlocking {
        val profileWithWarning = HealthProfileEntity(
            id = "p-det",
            userId = testUserId,
            fullName = "Determinism Test",
            dateOfBirth = "1990-01-01",
            gender = null, // INFO
            bloodGroup = "O+",
            height = -10.0, // ERROR
            weight = 70.0,
            allergies = null,
            existingDiseases = null,
            currentMedications = null,
            familyHistory = null,
            emergencyContactName = "Contact",
            emergencyContactNumber = null, // WARNING
            notes = null
        )

        val summary = qualityEngine.evaluate(
            profile = profileWithWarning,
            medications = emptyList(),
            medicationHistory = emptyList(),
            appointments = emptyList(),
            predictions = emptyList(),
            syncMetadata = null
        )

        assertEquals(HealthDataQualityStatus.NEEDS_ATTENTION, summary.status)
        assertTrue(summary.issues.size >= 3)
        // First issue should be ERROR
        assertEquals(HealthDataQualitySeverity.ERROR, summary.issues.first().severity)
    }

    @Test
    fun `data quality evaluation does not mutate local Room records`() = runBlocking {
        val med = MedicationEntity(
            id = 1L,
            userId = testUserId,
            medicineName = "Aspirin",
            dosage = "81",
            dosageUnit = "mg",
            frequency = "ONCE_DAILY",
            active = true
        )
        fakeMedicationDao.insertMedication(med)

        qualityRepository.evaluateDataQuality()

        val retrievedMed = fakeMedicationDao.getMedicationById(1L, testUserId)
        assertNotNull(retrievedMed)
        assertEquals("Aspirin", retrievedMed?.medicineName)
        assertEquals("81", retrievedMed?.dosage)
    }

    @Test
    fun `data quality evaluation records DATA_QUALITY_CHECKED audit event`() = runBlocking {
        fakeHealthProfileDao.insertHealthProfile(
            HealthProfileEntity(
                id = "p-audit",
                userId = testUserId,
                fullName = "Audit User",
                dateOfBirth = "1992-04-12",
                gender = "Female",
                bloodGroup = "A+",
                height = 165.0,
                weight = 58.0,
                allergies = null,
                existingDiseases = null,
                currentMedications = null,
                familyHistory = null,
                emergencyContactName = null,
                emergencyContactNumber = null,
                notes = null
            )
        )

        val summary = qualityRepository.evaluateDataQuality()

        assertEquals(HealthDataQualityStatus.GOOD, summary.status)
        val auditEvents = fakeSecurityAuditDao.events
        val qualityEvent = auditEvents.find { it.eventType == SecurityAuditEventType.DATA_QUALITY_CHECKED.name }
        assertNotNull(qualityEvent)
        assertEquals(testUserId, qualityEvent?.userId)
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
