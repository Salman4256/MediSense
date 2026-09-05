package com.medisense.app

import com.medisense.app.data.local.dao.*
import com.medisense.app.data.local.entity.*
import com.medisense.app.data.remote.supabase.AuthService
import com.medisense.app.data.repository.SecurityAuditRepository
import com.medisense.app.domain.model.PrivacyDataCategory
import com.medisense.app.domain.model.SecurityAuditEvent
import com.medisense.app.domain.model.SecurityAuditEventType
import com.medisense.app.domain.security.PrivacyDataManager
import com.medisense.app.domain.security.SecureLogger
import com.medisense.app.notification.AppointmentScheduler
import com.medisense.app.notification.MedicationScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SecurityAuditUnitTest {

    // In-memory fake DAO for audit events
    private class FakeSecurityAuditEventDao : SecurityAuditEventDao {
        val events = mutableListOf<SecurityAuditEventEntity>()
        private var nextId = 1L

        override fun observeRecentAuditEvents(userId: String, limit: Int): Flow<List<SecurityAuditEventEntity>> {
            val userEvents = events.filter { it.userId == userId }.sortedByDescending { it.timestamp }.take(limit)
            return flowOf(userEvents)
        }

        override suspend fun getAuditEventsForUser(userId: String): List<SecurityAuditEventEntity> {
            return events.filter { it.userId == userId }.sortedByDescending { it.timestamp }
        }

        override suspend fun insertAuditEvent(event: SecurityAuditEventEntity): Long {
            val id = nextId++
            val saved = event.copy(id = id)
            events.add(saved)
            return id
        }

        override suspend fun deleteAllAuditEventsForUser(userId: String) {
            events.removeAll { it.userId == userId }
        }
    }

    // In-memory fake DAOs for local data clearing test
    private class FakeHealthProfileDao : HealthProfileDao {
        val profiles = mutableListOf<HealthProfileEntity>()
        override fun observeHealthProfile(userId: String): Flow<HealthProfileEntity?> = flowOf(profiles.find { it.userId == userId })
        override suspend fun getHealthProfile(userId: String): HealthProfileEntity? = profiles.find { it.userId == userId }
        override suspend fun insertHealthProfile(profile: HealthProfileEntity) { profiles.add(profile) }
        override suspend fun updateHealthProfile(profile: HealthProfileEntity) {}
        override suspend fun deleteHealthProfile(profile: HealthProfileEntity) { profiles.remove(profile) }
        override suspend fun deleteHealthProfileByUserId(userId: String) { profiles.removeAll { it.userId == userId } }
        override suspend fun getPendingSyncProfiles(): List<HealthProfileEntity> = emptyList()
    }

    private class FakeMedicationDao : MedicationDao {
        val medications = mutableListOf<MedicationEntity>()
        override fun getMedicationsForUser(userId: String): Flow<List<MedicationEntity>> = flowOf(medications.filter { it.userId == userId })
        override fun getActiveMedicationsForUser(userId: String): Flow<List<MedicationEntity>> = flowOf(medications.filter { it.userId == userId && it.active })
        override suspend fun getActiveMedicationsForUserSync(userId: String): List<MedicationEntity> = medications.filter { it.userId == userId && it.active }
        override suspend fun getAllActiveMedicationsSync(): List<MedicationEntity> = medications.filter { it.active }
        override suspend fun getMedicationById(id: Long, userId: String): MedicationEntity? = medications.find { it.id == id && it.userId == userId }
        override suspend fun getMedicationByIdSync(id: Long): MedicationEntity? = medications.find { it.id == id }
        override suspend fun insertMedication(medication: MedicationEntity): Long { medications.add(medication); return medication.id }
        override suspend fun updateMedication(medication: MedicationEntity) {}
        override suspend fun deleteMedicationById(id: Long, userId: String) { medications.removeAll { it.id == id && it.userId == userId } }
        override suspend fun deleteAllMedicationsForUser(userId: String) { medications.removeAll { it.userId == userId } }
    }

    private class FakeMedicationHistoryDao : MedicationHistoryDao {
        val history = mutableListOf<MedicationHistoryEntity>()
        override fun getHistoryForUser(userId: String): Flow<List<MedicationHistoryEntity>> = flowOf(history.filter { it.userId == userId })
        override fun getHistoryForMedication(medicationId: Long, userId: String): Flow<List<MedicationHistoryEntity>> = flowOf(history.filter { it.medicationId == medicationId && it.userId == userId })
        override suspend fun getOccurrenceHistory(medicationId: Long, date: Long, time: String, userId: String): MedicationHistoryEntity? = history.find { it.medicationId == medicationId && it.userId == userId }
        override suspend fun findExistingRecord(medicationId: Long, date: Long, time: String): MedicationHistoryEntity? = history.find { it.medicationId == medicationId }
        override fun getHistoryForDateFlow(userId: String, date: Long): Flow<List<MedicationHistoryEntity>> = flowOf(emptyList())
        override fun getHistoryForDateRangeFlow(userId: String, startDate: Long, endDate: Long): Flow<List<MedicationHistoryEntity>> = flowOf(emptyList())
        override suspend fun insertHistory(history: MedicationHistoryEntity): Long { this.history.add(history); return history.id }
        override suspend fun updateHistory(history: MedicationHistoryEntity) {}
        override suspend fun deleteAllMedicationHistoryForUser(userId: String) { history.removeAll { it.userId == userId } }
    }

    private class FakeAppointmentDao : AppointmentDao {
        val appointments = mutableListOf<AppointmentEntity>()
        override fun observeAppointments(userId: String): Flow<List<AppointmentEntity>> = flowOf(appointments.filter { it.userId == userId })
        override fun observeUpcomingAppointments(userId: String, currentTime: Long): Flow<List<AppointmentEntity>> = flowOf(appointments.filter { it.userId == userId })
        override suspend fun getAllUpcomingScheduledAppointmentsSync(currentTime: Long): List<AppointmentEntity> = appointments.filter { it.status == "SCHEDULED" }
        override fun observeCompletedAppointments(userId: String): Flow<List<AppointmentEntity>> = flowOf(emptyList())
        override fun observeCancelledAppointments(userId: String): Flow<List<AppointmentEntity>> = flowOf(emptyList())
        override suspend fun getAppointment(id: Long, userId: String): AppointmentEntity? = appointments.find { it.id == id && it.userId == userId }
        override suspend fun insertAppointment(appointment: AppointmentEntity): Long { appointments.add(appointment); return appointment.id }
        override suspend fun updateAppointment(appointment: AppointmentEntity) {}
        override suspend fun deleteAppointment(appointment: AppointmentEntity) { appointments.remove(appointment) }
        override suspend fun deleteAppointmentById(id: Long, userId: String) { appointments.removeAll { it.id == id && it.userId == userId } }
        override suspend fun deleteAllAppointmentsForUser(userId: String) { appointments.removeAll { it.userId == userId } }
        override suspend fun updateAppointmentStatus(id: Long, userId: String, status: String, updatedAt: Long) {}
    }

    private class FakePredictionHistoryDao : PredictionHistoryDao {
        val list = mutableListOf<PredictionHistoryEntity>()
        override fun observePredictionHistory(userId: String): Flow<List<PredictionHistoryEntity>> = flowOf(list.filter { it.userId == userId })
        override suspend fun getPredictionHistoryById(id: Long, userId: String): PredictionHistoryEntity? = list.find { it.id == id && it.userId == userId }
        override suspend fun insertPredictionHistory(entity: PredictionHistoryEntity): Long { list.add(entity); return entity.id }
        override suspend fun deletePredictionHistory(id: Long, userId: String) { list.removeAll { it.id == id && it.userId == userId } }
        override suspend fun deleteAllPredictionHistory(userId: String) { list.removeAll { it.userId == userId } }
        override suspend fun getPendingSyncPredictionHistory(userId: String): List<PredictionHistoryEntity> = emptyList()
    }

    private class FakeConversationDao : ConversationDao {
        val conversations = mutableListOf<ConversationEntity>()
        override fun getConversationsForUser(userId: String): Flow<List<ConversationEntity>> = flowOf(conversations.filter { it.userId == userId })
        override suspend fun getConversationById(id: Long, userId: String): ConversationEntity? = conversations.find { it.id == id && it.userId == userId }
        override suspend fun insertConversation(conversation: ConversationEntity): Long { conversations.add(conversation); return conversation.id }
        override suspend fun updateConversation(conversation: ConversationEntity) {}
        override suspend fun deleteConversationById(id: Long, userId: String) { conversations.removeAll { it.id == id && it.userId == userId } }
        override suspend fun deleteAllConversationsForUser(userId: String) { conversations.removeAll { it.userId == userId } }
    }

    private class FakeChatMessageDao : ChatMessageDao {
        val messages = mutableListOf<ChatMessageEntity>()
        override fun getMessagesForConversation(conversationId: Long, userId: String): Flow<List<ChatMessageEntity>> = flowOf(messages.filter { it.conversationId == conversationId && it.userId == userId })
        override suspend fun getRecentMessagesSync(conversationId: Long, userId: String, limit: Int): List<ChatMessageEntity> = messages.filter { it.conversationId == conversationId && it.userId == userId }
        override suspend fun insertMessage(message: ChatMessageEntity): Long { messages.add(message); return message.id }
        override suspend fun deleteMessagesForConversation(conversationId: Long, userId: String) { messages.removeAll { it.conversationId == conversationId && it.userId == userId } }
        override suspend fun deleteAllMessagesForUser(userId: String) { messages.removeAll { it.userId == userId } }
    }

    private lateinit var auditDao: FakeSecurityAuditEventDao
    private lateinit var healthProfileDao: FakeHealthProfileDao
    private lateinit var medicationDao: FakeMedicationDao
    private lateinit var medicationHistoryDao: FakeMedicationHistoryDao
    private lateinit var appointmentDao: FakeAppointmentDao
    private lateinit var predictionHistoryDao: FakePredictionHistoryDao
    private lateinit var conversationDao: FakeConversationDao
    private lateinit var chatMessageDao: FakeChatMessageDao

    private var activeUserId: String = "user-uuid-1234"

    @Before
    fun setUp() {
        auditDao = FakeSecurityAuditEventDao()
        healthProfileDao = FakeHealthProfileDao()
        medicationDao = FakeMedicationDao()
        medicationHistoryDao = FakeMedicationHistoryDao()
        appointmentDao = FakeAppointmentDao()
        predictionHistoryDao = FakePredictionHistoryDao()
        conversationDao = FakeConversationDao()
        chatMessageDao = FakeChatMessageDao()
    }

    @Test
    fun `test audit event creation and user isolation`() = runBlocking {
        // Insert events for User A
        auditDao.insertAuditEvent(
            SecurityAuditEventEntity(
                userId = "user-A",
                eventType = SecurityAuditEventType.LOGIN.name,
                description = "User signed in",
                timestamp = 1000L
            )
        )
        auditDao.insertAuditEvent(
            SecurityAuditEventEntity(
                userId = "user-A",
                eventType = SecurityAuditEventType.PREDICTION_CREATED.name,
                description = "Disease prediction analysis performed",
                timestamp = 2000L
            )
        )

        // Insert event for User B
        auditDao.insertAuditEvent(
            SecurityAuditEventEntity(
                userId = "user-B",
                eventType = SecurityAuditEventType.PROFILE_UPDATED.name,
                description = "Health profile record updated",
                timestamp = 3000L
            )
        )

        val userAEvents = auditDao.observeRecentAuditEvents("user-A", 10).first()
        val userBEvents = auditDao.observeRecentAuditEvents("user-B", 10).first()

        assertEquals(2, userAEvents.size)
        assertEquals(1, userBEvents.size)

        // Verify User A cannot see User B events
        assertFalse(userAEvents.any { it.userId == "user-B" })
        assertTrue(userAEvents.any { it.eventType == "PREDICTION_CREATED" })

        // Verify User B cannot see User A events
        assertFalse(userBEvents.any { it.userId == "user-A" })
        assertEquals("PROFILE_UPDATED", userBEvents.first().eventType)
    }

    @Test
    fun `test local data clearing removes only target user records`() = runBlocking {
        val userA = "user-A"
        val userB = "user-B"

        // Populate records for both User A and User B
        healthProfileDao.insertHealthProfile(
            HealthProfileEntity(
                id = "p-1",
                userId = userA,
                fullName = "User A",
                dateOfBirth = "1995-05-15",
                gender = "Male",
                bloodGroup = "O+",
                height = 175.0,
                weight = 70.0,
                allergies = "None",
                existingDiseases = "None",
                currentMedications = "None",
                familyHistory = "None",
                emergencyContactName = "Contact A",
                emergencyContactNumber = "1234567890",
                notes = "Notes A"
            )
        )
        healthProfileDao.insertHealthProfile(
            HealthProfileEntity(
                id = "p-2",
                userId = userB,
                fullName = "User B",
                dateOfBirth = "1980-08-20",
                gender = "Female",
                bloodGroup = "A+",
                height = 160.0,
                weight = 60.0,
                allergies = "None",
                existingDiseases = "None",
                currentMedications = "None",
                familyHistory = "None",
                emergencyContactName = "Contact B",
                emergencyContactNumber = "0987654321",
                notes = "Notes B"
            )
        )

        medicationDao.insertMedication(
            MedicationEntity(
                id = 1L,
                userId = userA,
                medicineName = "Aspirin",
                dosage = "100mg",
                scheduledTimes = listOf("08:00"),
                active = true
            )
        )
        medicationDao.insertMedication(
            MedicationEntity(
                id = 2L,
                userId = userB,
                medicineName = "Metformin",
                dosage = "500mg",
                scheduledTimes = listOf("08:00", "20:00"),
                active = true
            )
        )

        predictionHistoryDao.insertPredictionHistory(
            PredictionHistoryEntity(
                id = 1L,
                userId = userA,
                predictedDisease = "Allergy",
                confidence = 0.8f,
                symptoms = listOf("itching"),
                predictionTimestamp = 1000L
            )
        )
        predictionHistoryDao.insertPredictionHistory(
            PredictionHistoryEntity(
                id = 2L,
                userId = userB,
                predictedDisease = "Common Cold",
                confidence = 0.9f,
                symptoms = listOf("cough"),
                predictionTimestamp = 2000L
            )
        )

        // Execute local clearing for User A
        healthProfileDao.deleteHealthProfileByUserId(userA)
        medicationDao.deleteAllMedicationsForUser(userA)
        predictionHistoryDao.deleteAllPredictionHistory(userA)

        // Verify User A records are gone
        assertEquals(null, healthProfileDao.getHealthProfile(userA))
        assertEquals(0, medicationDao.getActiveMedicationsForUserSync(userA).size)
        assertEquals(0, predictionHistoryDao.observePredictionHistory(userA).first().size)

        // Verify User B records are completely preserved
        assertEquals("Female", healthProfileDao.getHealthProfile(userB)?.gender)
        assertEquals(1, medicationDao.getActiveMedicationsForUserSync(userB).size)
        assertEquals("Metformin", medicationDao.getActiveMedicationsForUserSync(userB).first().medicineName)
        assertEquals(1, predictionHistoryDao.observePredictionHistory(userB).first().size)
    }

    @Test
    fun `test secure logger redacts sensitive credentials`() {
        val sampleWithPassword = "User login attempt: password=SuperSecretPassword123"
        val sampleWithBearer = "API Header: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.xyz"
        val sampleWithApiKey = "Configuring service with api_key=sbp_9392847294829"

        val sanitizedPassword = SecureLogger.sanitize(sampleWithPassword)
        val sanitizedBearer = SecureLogger.sanitize(sampleWithBearer)
        val sanitizedApiKey = SecureLogger.sanitize(sampleWithApiKey)

        assertFalse(sanitizedPassword.contains("SuperSecretPassword123"))
        assertTrue(sanitizedPassword.contains("[REDACTED_SECRET]"))

        assertFalse(sanitizedBearer.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"))
        assertTrue(sanitizedBearer.contains("[REDACTED_SECRET]"))

        assertFalse(sanitizedApiKey.contains("sbp_9392847294829"))
        assertTrue(sanitizedApiKey.contains("[REDACTED_SECRET]"))
    }

    @Test
    fun `test privacy data classifications are complete and accurate`() {
        val categories = PrivacyDataCategory.entries

        assertEquals(4, categories.size)
        assertTrue(categories.contains(PrivacyDataCategory.IDENTITY_DATA))
        assertTrue(categories.contains(PrivacyDataCategory.HEALTH_DATA))
        assertTrue(categories.contains(PrivacyDataCategory.DERIVED_HEALTH_INTELLIGENCE))
        assertTrue(categories.contains(PrivacyDataCategory.TECHNICAL_DATA))

        for (cat in categories) {
            assertFalse(cat.title.isBlank())
            assertFalse(cat.description.isBlank())
            assertFalse(cat.storageLocation.isBlank())
            assertFalse(cat.retentionPolicy.isBlank())
        }
    }
}
