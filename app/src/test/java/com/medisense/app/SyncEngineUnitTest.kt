package com.medisense.app

import com.medisense.app.data.local.dao.*
import com.medisense.app.data.local.entity.*
import com.medisense.app.data.remote.supabase.AuthService
import com.medisense.app.data.repository.SecurityAuditRepository
import com.medisense.app.data.sync.NetworkConnectivityChecker
import com.medisense.app.data.sync.SyncEngine
import com.medisense.app.data.sync.dto.*
import com.medisense.app.data.sync.model.SyncResult
import com.medisense.app.data.sync.model.SyncStatus
import com.medisense.app.data.sync.remote.ISupabaseSyncDataSource
import com.medisense.app.domain.model.SecurityAuditEventType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncEngineUnitTest {

    // Fakes
    private lateinit var fakeAuthService: FakeAuthService
    private lateinit var fakeConnectivityChecker: FakeConnectivityChecker
    private lateinit var fakeRemoteDataSource: FakeSupabaseSyncDataSource
    private lateinit var fakeHealthProfileDao: FakeHealthProfileDao
    private lateinit var fakeMedicationDao: FakeMedicationDao
    private lateinit var fakeMedicationHistoryDao: FakeMedicationHistoryDao
    private lateinit var fakeAppointmentDao: FakeAppointmentDao
    private lateinit var fakePredictionHistoryDao: FakePredictionHistoryDao
    private lateinit var fakeSyncMetadataDao: FakeSyncMetadataDao
    private lateinit var fakeSecurityAuditDao: FakeSecurityAuditEventDao
    private lateinit var securityAuditRepository: SecurityAuditRepository

    private lateinit var syncEngine: SyncEngine

    private val testUserId = "user-uuid-12345"
    private val testEmail = "patient@medisense.app"

    @Before
    fun setUp() {
        fakeAuthService = FakeAuthService(userId = testUserId, email = testEmail)
        fakeConnectivityChecker = FakeConnectivityChecker(online = true)
        fakeRemoteDataSource = FakeSupabaseSyncDataSource()
        fakeHealthProfileDao = FakeHealthProfileDao()
        fakeMedicationDao = FakeMedicationDao()
        fakeMedicationHistoryDao = FakeMedicationHistoryDao()
        fakeAppointmentDao = FakeAppointmentDao()
        fakePredictionHistoryDao = FakePredictionHistoryDao()
        fakeSyncMetadataDao = FakeSyncMetadataDao()
        fakeSecurityAuditDao = FakeSecurityAuditEventDao()

        securityAuditRepository = SecurityAuditRepository(fakeSecurityAuditDao, fakeAuthService)

        syncEngine = SyncEngine(
            authService = fakeAuthService,
            connectivityChecker = fakeConnectivityChecker,
            remoteDataSource = fakeRemoteDataSource,
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
    fun `sync without authenticated user returns AUTH_REQUIRED`() = runBlocking {
        fakeAuthService.setSession(null, null)

        val result = syncEngine.synchronize()

        assertEquals(SyncStatus.AUTH_REQUIRED, result.status)
        assertEquals(0, result.recordsUploaded)
        assertEquals(0, result.recordsDownloaded)
        assertEquals(0, fakeRemoteDataSource.upsertedProfiles.size)
    }

    @Test
    fun `sync when offline returns OFFLINE status and does not crash`() = runBlocking {
        fakeConnectivityChecker.setOnline(false)

        // Add a pending record locally
        fakeMedicationDao.insertMedication(
            MedicationEntity(
                id = 1,
                userId = testUserId,
                medicineName = "Metformin",
                dosage = "500",
                dosageUnit = "mg",
                frequency = "TWICE_DAILY",
                scheduledTimes = listOf("08:00", "20:00"),
                startDate = 1000L,
                endDate = 2000L,
                instructions = "With food",
                active = true,
                pendingSync = true
            )
        )

        val result = syncEngine.synchronize()

        assertEquals(SyncStatus.OFFLINE, result.status)
        val meta = fakeSyncMetadataDao.getSyncMetadata(testUserId)
        assertNotNull(meta)
        assertEquals(SyncStatus.OFFLINE.name, meta?.lastSyncStatus)
        assertEquals(1, meta?.pendingUploadCount)
    }

    @Test
    fun `sync uploads pending local health profile and marks as synced`() = runBlocking {
        fakeHealthProfileDao.insertHealthProfile(
            HealthProfileEntity(
                id = "hp-10",
                userId = testUserId,
                fullName = "John Smith",
                dateOfBirth = "1980-05-15",
                gender = "Male",
                bloodGroup = "O+",
                height = 175.0,
                weight = 80.0,
                allergies = "Penicillin",
                existingDiseases = "Hypertension",
                currentMedications = "None",
                familyHistory = "None",
                emergencyContactName = "Jane Smith",
                emergencyContactNumber = "1234567890",
                notes = "Routine notes",
                createdAt = "2026-01-01T00:00:00Z",
                updatedAt = "2026-01-01T00:00:00Z",
                pendingSync = true
            )
        )

        val result = syncEngine.synchronize()

        assertEquals(SyncStatus.SUCCESS, result.status)
        assertTrue(result.recordsUploaded >= 1)

        // Verify remote received DTO
        assertEquals(1, fakeRemoteDataSource.upsertedProfiles.size)
        val remoteProfile = fakeRemoteDataSource.upsertedProfiles.first()
        assertEquals(testUserId, remoteProfile.userId)
        assertEquals("Hypertension", remoteProfile.existingDiseases)

        // Verify local Room record is marked synced
        val localProfile = fakeHealthProfileDao.getHealthProfile(testUserId)
        assertNotNull(localProfile)
        assertFalse(localProfile!!.pendingSync)
    }

    @Test
    fun `sync uploads pending medications and marks them as synced`() = runBlocking {
        val now = System.currentTimeMillis()
        fakeMedicationDao.insertMedication(
            MedicationEntity(
                id = 1,
                userId = testUserId,
                medicineName = "Amlodipine",
                dosage = "5",
                dosageUnit = "mg",
                frequency = "ONCE_DAILY",
                scheduledTimes = listOf("09:00"),
                startDate = now - 10000,
                endDate = now + 100000,
                instructions = "Morning",
                active = true,
                pendingSync = true,
                updatedAt = now
            )
        )

        val result = syncEngine.synchronize()

        assertEquals(SyncStatus.SUCCESS, result.status)
        assertEquals(1, fakeRemoteDataSource.upsertedMedications.size)
        val remoteMed = fakeRemoteDataSource.upsertedMedications.first()
        assertEquals("Amlodipine", remoteMed.medicineName)
        assertEquals("5", remoteMed.dosage)

        val localMeds = fakeMedicationDao.getAllMedicationsForUserSync(testUserId)
        assertEquals(1, localMeds.size)
        assertFalse(localMeds.first().pendingSync)
    }

    @Test
    fun `sync downloads remote appointments when local is empty`() = runBlocking {
        val remoteAppointment = AppointmentDto(
            id = 999L,
            userId = testUserId,
            doctorName = "Dr. John Smith",
            clinicName = "General Health Clinic",
            appointmentType = "Cardiology",
            appointmentDate = "2026-09-10",
            appointmentTime = "10:00 AM",
            appointmentTimestamp = System.currentTimeMillis() + 86400000,
            notes = "Routine cardiovascular checkup",
            status = "SCHEDULED",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        fakeRemoteDataSource.remoteAppointments.add(remoteAppointment)

        val result = syncEngine.synchronize()

        assertEquals(SyncStatus.SUCCESS, result.status)
        assertTrue(result.recordsDownloaded >= 1)

        val localAppointments = fakeAppointmentDao.getAllAppointmentsForUserSync(testUserId)
        assertEquals(1, localAppointments.size)
        val local = localAppointments.first()
        assertEquals("Dr. John Smith", local.doctorName)
        assertEquals("General Health Clinic", local.clinicName)
        assertEquals("Cardiology", local.appointmentType)
        assertFalse(local.pendingSync)
    }

    @Test
    fun `LWW conflict resolution - remote with newer timestamp overwrites older local record`() = runBlocking {
        val localTime = 100000L
        val remoteTime = 200000L // Remote is newer

        // Local record has older timestamp
        fakeMedicationDao.insertMedication(
            MedicationEntity(
                id = 5,
                userId = testUserId,
                medicineName = "Atorvastatin",
                dosage = "10",
                dosageUnit = "mg",
                frequency = "ONCE_DAILY",
                scheduledTimes = listOf("21:00"),
                startDate = 1000L,
                endDate = 50000L,
                instructions = "Old instructions",
                active = true,
                pendingSync = false,
                updatedAt = localTime
            )
        )

        // Remote record has updated dosage and newer timestamp
        fakeRemoteDataSource.remoteMedications.add(
            MedicationDto(
                id = 5L,
                userId = testUserId,
                medicineName = "Atorvastatin",
                dosage = "20",
                dosageUnit = "mg",
                frequency = "ONCE_DAILY",
                scheduledTimes = listOf("21:00"),
                startDate = 1000L,
                endDate = 50000L,
                instructions = "Updated dosage per physician",
                active = true,
                createdAt = 1000L,
                updatedAt = remoteTime
            )
        )

        val result = syncEngine.synchronize()

        assertEquals(SyncStatus.SUCCESS, result.status)
        assertEquals(1, result.conflictsResolved)

        // Verify local medication was updated to 20mg
        val localMeds = fakeMedicationDao.getAllMedicationsForUserSync(testUserId)
        assertEquals(1, localMeds.size)
        assertEquals("20", localMeds.first().dosage)
        assertEquals("Updated dosage per physician", localMeds.first().instructions)
        assertEquals(remoteTime, localMeds.first().updatedAt)
    }

    @Test
    fun `LWW conflict resolution - local with newer timestamp wins over older remote record`() = runBlocking {
        val localTime = 300000L // Local is newer
        val remoteTime = 100000L

        fakeMedicationDao.insertMedication(
            MedicationEntity(
                id = 8,
                userId = testUserId,
                medicineName = "Lisinopril",
                dosage = "20",
                dosageUnit = "mg",
                frequency = "ONCE_DAILY",
                scheduledTimes = listOf("08:00"),
                startDate = 1000L,
                endDate = 50000L,
                instructions = "Local update",
                active = true,
                pendingSync = true,
                updatedAt = localTime
            )
        )

        fakeRemoteDataSource.remoteMedications.add(
            MedicationDto(
                id = 8L,
                userId = testUserId,
                medicineName = "Lisinopril",
                dosage = "10",
                dosageUnit = "mg",
                frequency = "ONCE_DAILY",
                scheduledTimes = listOf("08:00"),
                startDate = 1000L,
                endDate = 50000L,
                instructions = "Old remote",
                active = true,
                createdAt = 1000L,
                updatedAt = remoteTime
            )
        )

        val result = syncEngine.synchronize()

        assertEquals(SyncStatus.SUCCESS, result.status)

        // Local 20mg was uploaded to remote because local was newer
        val uploaded = fakeRemoteDataSource.upsertedMedications.find { it.medicineName == "Lisinopril" }
        assertNotNull(uploaded)
        assertEquals("20", uploaded!!.dosage)

        // Local remains 20mg
        val localMeds = fakeMedicationDao.getAllMedicationsForUserSync(testUserId)
        assertEquals(1, localMeds.size)
        assertEquals("20", localMeds.first().dosage)
    }

    @Test
    fun `sync records audit telemetry on completion`() = runBlocking {
        val result = syncEngine.synchronize()

        assertEquals(SyncStatus.SUCCESS, result.status)
        val events = fakeSecurityAuditDao.events
        assertTrue(events.isNotEmpty())
        val syncEvent = events.find { it.eventType == SecurityAuditEventType.CLOUD_SYNC.name }
        assertNotNull(syncEvent)
        assertEquals(testUserId, syncEvent?.userId)
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

    private class FakeConnectivityChecker(private var online: Boolean) : NetworkConnectivityChecker(null) {
        override fun isOnline(): Boolean = online
        fun setOnline(value: Boolean) { online = value }
    }

    private class FakeSupabaseSyncDataSource : ISupabaseSyncDataSource {
        val remoteProfiles = mutableListOf<HealthProfileDto>()
        val remoteMedications = mutableListOf<MedicationDto>()
        val remoteMedicationHistory = mutableListOf<MedicationHistoryDto>()
        val remoteAppointments = mutableListOf<AppointmentDto>()
        val remotePredictions = mutableListOf<PredictionHistoryDto>()

        val upsertedProfiles = mutableListOf<HealthProfileDto>()
        val upsertedMedications = mutableListOf<MedicationDto>()
        val upsertedMedicationHistory = mutableListOf<MedicationHistoryDto>()
        val upsertedAppointments = mutableListOf<AppointmentDto>()
        val upsertedPredictions = mutableListOf<PredictionHistoryDto>()

        override suspend fun fetchHealthProfile(userId: String): HealthProfileDto? {
            return remoteProfiles.find { it.userId == userId }
        }

        override suspend fun upsertHealthProfile(profile: HealthProfileDto): Boolean {
            upsertedProfiles.add(profile)
            remoteProfiles.removeAll { it.userId == profile.userId }
            remoteProfiles.add(profile)
            return true
        }

        override suspend fun fetchMedications(userId: String, sinceTimestamp: Long): List<MedicationDto> {
            return remoteMedications.filter { it.userId == userId && it.updatedAt >= sinceTimestamp }
        }

        override suspend fun upsertMedications(medications: List<MedicationDto>): Boolean {
            upsertedMedications.addAll(medications)
            medications.forEach { med ->
                remoteMedications.removeAll { it.id == med.id && it.userId == med.userId }
                remoteMedications.add(med)
            }
            return true
        }

        override suspend fun fetchMedicationHistory(userId: String, sinceTimestamp: Long): List<MedicationHistoryDto> {
            return remoteMedicationHistory.filter { it.userId == userId && it.scheduledDate >= sinceTimestamp }
        }

        override suspend fun upsertMedicationHistory(history: List<MedicationHistoryDto>): Boolean {
            upsertedMedicationHistory.addAll(history)
            history.forEach { h ->
                remoteMedicationHistory.removeAll { it.id == h.id && it.userId == h.userId }
                remoteMedicationHistory.add(h)
            }
            return true
        }

        override suspend fun fetchAppointments(userId: String, sinceTimestamp: Long): List<AppointmentDto> {
            return remoteAppointments.filter { it.userId == userId && it.updatedAt >= sinceTimestamp }
        }

        override suspend fun upsertAppointments(appointments: List<AppointmentDto>): Boolean {
            upsertedAppointments.addAll(appointments)
            appointments.forEach { apt ->
                remoteAppointments.removeAll { it.id == apt.id && it.userId == apt.userId }
                remoteAppointments.add(apt)
            }
            return true
        }

        override suspend fun fetchPredictionHistory(userId: String, sinceTimestamp: Long): List<PredictionHistoryDto> {
            return remotePredictions.filter { it.userId == userId && it.updatedAt >= sinceTimestamp }
        }

        override suspend fun upsertPredictionHistory(predictions: List<PredictionHistoryDto>): Boolean {
            upsertedPredictions.addAll(predictions)
            predictions.forEach { p ->
                remotePredictions.removeAll { it.id == p.id && it.userId == p.userId }
                remotePredictions.add(p)
            }
            return true
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
        override suspend fun getPendingSyncProfiles(): List<HealthProfileEntity> = profiles.filter { it.pendingSync }
        override suspend fun getPendingSyncProfileForUser(userId: String): HealthProfileEntity? {
            return profiles.find { it.userId == userId && it.pendingSync }
        }
        override suspend fun markProfileSynced(userId: String) {
            val idx = profiles.indexOfFirst { it.userId == userId }
            if (idx != -1) {
                profiles[idx] = profiles[idx].copy(pendingSync = false)
            }
        }
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

        override suspend fun getPendingSyncMedications(userId: String): List<MedicationEntity> {
            return medications.filter { it.userId == userId && it.pendingSync }
        }

        override suspend fun getAllMedicationsForUserSync(userId: String): List<MedicationEntity> {
            return medications.filter { it.userId == userId }
        }

        override suspend fun markMedicationSynced(id: Long, userId: String) {
            val idx = medications.indexOfFirst { it.id == id && it.userId == userId }
            if (idx != -1) {
                medications[idx] = medications[idx].copy(pendingSync = false)
            }
        }

        override suspend fun upsertMedications(medications: List<MedicationEntity>) {
            medications.forEach { med ->
                this.medications.removeAll { it.id == med.id && it.userId == med.userId }
                this.medications.add(med)
            }
        }
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
        override fun getHistoryForDateFlow(userId: String, date: Long): Flow<List<MedicationHistoryEntity>> =
            flowOf(history.filter { it.userId == userId && it.scheduledDate == date })
        override fun getHistoryForDateRangeFlow(userId: String, startDate: Long, endDate: Long): Flow<List<MedicationHistoryEntity>> =
            flowOf(history.filter { it.userId == userId && it.scheduledDate in startDate..endDate })

        override suspend fun insertHistory(history: MedicationHistoryEntity): Long {
            val id = if (history.id != 0L) history.id else nextId++
            val saved = history.copy(id = id)
            this.history.removeAll { it.id == id }
            this.history.add(saved)
            return id
        }

        override suspend fun updateHistory(history: MedicationHistoryEntity) {
            this.history.removeAll { it.id == history.id }
            this.history.add(history)
        }

        override suspend fun deleteAllMedicationHistoryForUser(userId: String) {
            history.removeAll { it.userId == userId }
        }

        override suspend fun getAllHistoryForUserSync(userId: String): List<MedicationHistoryEntity> {
            return history.filter { it.userId == userId }
        }

        override suspend fun upsertHistory(historyList: List<MedicationHistoryEntity>) {
            historyList.forEach { h ->
                history.removeAll { it.id == h.id && it.userId == h.userId }
                history.add(h)
            }
        }
    }

    private class FakeAppointmentDao : AppointmentDao {
        val appointments = mutableListOf<AppointmentEntity>()
        private var nextId = 1L

        override fun observeAppointments(userId: String): Flow<List<AppointmentEntity>> = flowOf(appointments.filter { it.userId == userId })
        override fun observeUpcomingAppointments(userId: String, currentTime: Long): Flow<List<AppointmentEntity>> =
            flowOf(appointments.filter { it.userId == userId && it.appointmentTimestamp >= currentTime && it.status == "SCHEDULED" })
        override suspend fun getAllUpcomingScheduledAppointmentsSync(currentTime: Long): List<AppointmentEntity> =
            appointments.filter { it.appointmentTimestamp >= currentTime && it.status == "SCHEDULED" }
        override fun observeCompletedAppointments(userId: String): Flow<List<AppointmentEntity>> =
            flowOf(appointments.filter { it.userId == userId && it.status == "COMPLETED" })
        override fun observeCancelledAppointments(userId: String): Flow<List<AppointmentEntity>> =
            flowOf(appointments.filter { it.userId == userId && it.status == "CANCELLED" })

        override suspend fun getAppointment(id: Long, userId: String): AppointmentEntity? =
            appointments.find { it.id == id && it.userId == userId }

        override suspend fun insertAppointment(appointment: AppointmentEntity): Long {
            val id = if (appointment.id != 0L) appointment.id else nextId++
            val saved = appointment.copy(id = id)
            appointments.removeAll { it.id == id }
            appointments.add(saved)
            return id
        }

        override suspend fun updateAppointment(appointment: AppointmentEntity) {
            appointments.removeAll { it.id == appointment.id }
            appointments.add(appointment)
        }

        override suspend fun deleteAppointment(appointment: AppointmentEntity) {
            appointments.remove(appointment)
        }

        override suspend fun deleteAppointmentById(id: Long, userId: String) {
            appointments.removeAll { it.id == id && it.userId == userId }
        }

        override suspend fun deleteAllAppointmentsForUser(userId: String) {
            appointments.removeAll { it.userId == userId }
        }

        override suspend fun updateAppointmentStatus(id: Long, userId: String, status: String, updatedAt: Long) {
            val idx = appointments.indexOfFirst { it.id == id && it.userId == userId }
            if (idx != -1) {
                appointments[idx] = appointments[idx].copy(status = status, updatedAt = updatedAt, pendingSync = true)
            }
        }

        override suspend fun getPendingSyncAppointments(userId: String): List<AppointmentEntity> {
            return appointments.filter { it.userId == userId && it.pendingSync }
        }

        override suspend fun getAllAppointmentsForUserSync(userId: String): List<AppointmentEntity> {
            return appointments.filter { it.userId == userId }
        }

        override suspend fun markAppointmentSynced(id: Long, userId: String) {
            val idx = appointments.indexOfFirst { it.id == id && it.userId == userId }
            if (idx != -1) {
                appointments[idx] = appointments[idx].copy(pendingSync = false)
            }
        }

        override suspend fun upsertAppointments(appointments: List<AppointmentEntity>) {
            appointments.forEach { apt ->
                this.appointments.removeAll { it.id == apt.id && it.userId == apt.userId }
                this.appointments.add(apt)
            }
        }
    }

    private class FakePredictionHistoryDao : PredictionHistoryDao {
        val predictions = mutableListOf<PredictionHistoryEntity>()
        private var nextId = 1L

        override fun observePredictionHistory(userId: String): Flow<List<PredictionHistoryEntity>> =
            flowOf(predictions.filter { it.userId == userId })

        override suspend fun getPredictionHistoryById(id: Long, userId: String): PredictionHistoryEntity? =
            predictions.find { it.id == id && it.userId == userId }

        override suspend fun insertPredictionHistory(entity: PredictionHistoryEntity): Long {
            val id = if (entity.id != 0L) entity.id else nextId++
            val saved = entity.copy(id = id)
            predictions.removeAll { it.id == id }
            predictions.add(saved)
            return id
        }

        override suspend fun deletePredictionHistory(id: Long, userId: String) {
            predictions.removeAll { it.id == id && it.userId == userId }
        }

        override suspend fun deleteAllPredictionHistory(userId: String) {
            predictions.removeAll { it.userId == userId }
        }

        override suspend fun getPendingSyncPredictionHistory(userId: String): List<PredictionHistoryEntity> {
            return predictions.filter { it.userId == userId && it.pendingSync }
        }

        override suspend fun getAllPredictionHistoryForUserSync(userId: String): List<PredictionHistoryEntity> {
            return predictions.filter { it.userId == userId }
        }

        override suspend fun markPredictionSynced(id: Long, userId: String) {
            val idx = predictions.indexOfFirst { it.id == id && it.userId == userId }
            if (idx != -1) {
                predictions[idx] = predictions[idx].copy(pendingSync = false)
            }
        }

        override suspend fun upsertPredictionHistory(predictions: List<PredictionHistoryEntity>) {
            predictions.forEach { p ->
                this.predictions.removeAll { it.id == p.id && it.userId == p.userId }
                this.predictions.add(p)
            }
        }
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
