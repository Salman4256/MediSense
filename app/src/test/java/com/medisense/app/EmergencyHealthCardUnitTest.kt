package com.medisense.app

import com.medisense.app.data.local.dao.HealthProfileDao
import com.medisense.app.data.local.dao.MedicationDao
import com.medisense.app.data.local.dao.SecurityAuditEventDao
import com.medisense.app.data.local.entity.HealthProfileEntity
import com.medisense.app.data.local.entity.MedicationEntity
import com.medisense.app.data.local.entity.SecurityAuditEventEntity
import com.medisense.app.data.remote.supabase.AuthService
import com.medisense.app.data.repository.EmergencyHealthCardRepository
import com.medisense.app.data.repository.SecurityAuditRepository
import com.medisense.app.domain.model.EmergencyHealthCard
import com.medisense.app.domain.model.SecurityAuditEventType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Calendar

class EmergencyHealthCardUnitTest {

    private lateinit var fakeHealthProfileDao: FakeHealthProfileDao
    private lateinit var fakeMedicationDao: FakeMedicationDao
    private lateinit var fakeSecurityAuditDao: FakeSecurityAuditEventDao
    private lateinit var fakeAuthService: FakeAuthService

    private lateinit var emergencyRepository: EmergencyHealthCardRepository
    private lateinit var securityAuditRepository: SecurityAuditRepository

    private val testUserId = "user-emergency-12345"
    private val otherUserId = "user-emergency-99999"

    @Before
    fun setUp() {
        fakeHealthProfileDao = FakeHealthProfileDao()
        fakeMedicationDao = FakeMedicationDao()
        fakeSecurityAuditDao = FakeSecurityAuditEventDao()
        fakeAuthService = FakeAuthService(userId = testUserId, email = "emergency_user@medisense.app")

        emergencyRepository = EmergencyHealthCardRepository(
            healthProfileDao = fakeHealthProfileDao,
            medicationDao = fakeMedicationDao,
            authService = fakeAuthService
        )

        securityAuditRepository = SecurityAuditRepository(
            auditDao = fakeSecurityAuditDao,
            authService = fakeAuthService
        )
    }

    // 1. Full Profile & Active Medications Data Aggregation
    @Test
    fun testGetEmergencyHealthCard_fullProfileAndMedications() = runBlocking {
        fakeHealthProfileDao.insertHealthProfile(
            HealthProfileEntity(
                id = "prof_1",
                userId = testUserId,
                fullName = "Johnathan Doe",
                dateOfBirth = "1988-04-12",
                gender = "Male",
                bloodGroup = "O+",
                height = 178.0,
                weight = 75.0,
                allergies = "Penicillin, Peanuts",
                existingDiseases = "Hypertension, Asthma",
                currentMedications = "Amlodipine 5mg",
                familyHistory = "Heart Disease",
                emergencyContactName = "Jane Doe",
                emergencyContactNumber = "+1-555-0199",
                notes = "Pacemaker implanted in 2021. Avoid strong magnetic fields."
            )
        )

        fakeMedicationDao.insertMedication(
            MedicationEntity(
                id = 101,
                userId = testUserId,
                medicineName = "Amlodipine",
                dosage = "5",
                dosageUnit = "mg",
                frequency = "ONCE_DAILY",
                instructions = "Take in the morning",
                active = true
            )
        )
        fakeMedicationDao.insertMedication(
            MedicationEntity(
                id = 102,
                userId = testUserId,
                medicineName = "Salbutamol Inhaler",
                dosage = "100",
                dosageUnit = "mcg",
                frequency = "AS_NEEDED",
                instructions = "Inhale during acute shortness of breath",
                active = true
            )
        )

        val card = emergencyRepository.getEmergencyHealthCard(testUserId)

        assertEquals(testUserId, card.userId)
        assertEquals("Johnathan Doe", card.personalInfo.fullName)
        assertEquals("1988-04-12", card.personalInfo.dateOfBirth)
        assertEquals("Male", card.personalInfo.gender)
        assertNotNull(card.personalInfo.age)
        assertTrue(card.personalInfo.age!! > 30)

        // Allergies
        assertEquals(2, card.allergies.allergies.size)
        assertTrue(card.allergies.allergies.contains("Penicillin"))
        assertTrue(card.allergies.allergies.contains("Peanuts"))

        // Blood Group
        assertEquals("O+", card.bloodGroup)

        // Conditions
        assertEquals(2, card.conditions.conditions.size)
        assertTrue(card.conditions.conditions.contains("Hypertension"))
        assertTrue(card.conditions.conditions.contains("Asthma"))

        // Active Medications from Room table
        assertEquals(2, card.medications.medications.size)
        assertEquals("Amlodipine", card.medications.medications[0].name)
        assertEquals("5 mg", card.medications.medications[0].dosage)
        assertEquals("ONCE_DAILY", card.medications.medications[0].frequency)
        assertEquals("Take in the morning", card.medications.medications[0].instructions)

        // Emergency Contact
        assertEquals("Jane Doe", card.emergencyContact.name)
        assertEquals("+1-555-0199", card.emergencyContact.phone)

        // Notes
        assertEquals("Pacemaker implanted in 2021. Avoid strong magnetic fields.", card.notes.notes)

        // Completeness
        assertEquals(100, card.completeness.scorePercentage)
        assertTrue(card.completeness.missingFields.isEmpty())
    }

    // 2. Fallback on Empty / Missing Profile
    @Test
    fun testGetEmergencyHealthCard_emptyProfileFallback() = runBlocking {
        val card = emergencyRepository.getEmergencyHealthCard(testUserId)

        assertEquals(testUserId, card.userId)
        assertEquals("", card.personalInfo.fullName)
        assertEquals("", card.personalInfo.dateOfBirth)
        assertNull(card.personalInfo.age)
        assertEquals("", card.personalInfo.gender)
        assertTrue(card.allergies.allergies.isEmpty())
        assertEquals("", card.bloodGroup)
        assertTrue(card.conditions.conditions.isEmpty())
        assertTrue(card.medications.medications.isEmpty())
        assertEquals("", card.emergencyContact.name)
        assertEquals("", card.emergencyContact.phone)
        assertEquals("", card.notes.notes)

        assertEquals(0, card.completeness.scorePercentage)
        assertEquals(6, card.completeness.missingFields.size)
    }

    // 3. Partial Completeness Calculation
    @Test
    fun testCompleteness_partialScore() {
        val completeness = EmergencyHealthCard.calculateCompleteness(
            fullName = "Alice Smith",
            bloodGroup = "B+",
            contactName = "Bob Smith",
            contactPhone = "+1234567890",
            allergiesText = null,
            conditionsText = null,
            hasMedications = false
        )

        // 3 completed out of 6 (Full Name, Blood Group, Emergency Contact)
        assertEquals(50, completeness.scorePercentage)
        assertEquals(3, completeness.completedFields.size)
        assertTrue(completeness.completedFields.contains("Full Name"))
        assertTrue(completeness.completedFields.contains("Blood Group"))
        assertTrue(completeness.completedFields.contains("Emergency Contact"))

        assertEquals(3, completeness.missingFields.size)
        assertTrue(completeness.missingFields.contains("Known Allergies"))
        assertTrue(completeness.missingFields.contains("Medical Conditions"))
        assertTrue(completeness.missingFields.contains("Active Medications"))
    }

    // 4. Blood Group Edge Cases in Completeness
    @Test
    fun testCompleteness_bloodGroupUnknownTreatedAsMissing() {
        val completeness1 = EmergencyHealthCard.calculateCompleteness(
            fullName = "Alice",
            bloodGroup = "Unknown",
            contactName = "Bob",
            contactPhone = "123",
            allergiesText = "Pollen",
            conditionsText = "None",
            hasMedications = true
        )
        assertTrue(completeness1.missingFields.contains("Blood Group"))

        val completeness2 = EmergencyHealthCard.calculateCompleteness(
            fullName = "Alice",
            bloodGroup = "Not recorded",
            contactName = "Bob",
            contactPhone = "123",
            allergiesText = "Pollen",
            conditionsText = "None",
            hasMedications = true
        )
        assertTrue(completeness2.missingFields.contains("Blood Group"))
    }

    // 5. Age Calculation from Different Valid Formats
    @Test
    fun testAgeCalculation_validFormats() {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val expectedAge = currentYear - 1990

        val ageIso = EmergencyHealthCard.calculateAge("1990-01-01")
        assertNotNull(ageIso)
        assertTrue(ageIso == expectedAge || ageIso == expectedAge - 1)

        val ageSlash = EmergencyHealthCard.calculateAge("1990/01/01")
        assertNotNull(ageSlash)

        val ageDdmmyyyy = EmergencyHealthCard.calculateAge("01-01-1990")
        assertNotNull(ageDdmmyyyy)
    }

    // 6. Age Calculation from Invalid / Blank Input
    @Test
    fun testAgeCalculation_invalidDates() {
        assertNull(EmergencyHealthCard.calculateAge(null))
        assertNull(EmergencyHealthCard.calculateAge(""))
        assertNull(EmergencyHealthCard.calculateAge("   "))
        assertNull(EmergencyHealthCard.calculateAge("invalid-date-string"))
        assertNull(EmergencyHealthCard.calculateAge("9999-99-99"))
        assertNull(EmergencyHealthCard.calculateAge("1800-01-01")) // Outside valid human age threshold
    }

    // 7. Medication Fallback from HealthProfileEntity when MedicationEntity is empty
    @Test
    fun testMedications_fallbackFromProfile() = runBlocking {
        fakeHealthProfileDao.insertHealthProfile(
            HealthProfileEntity(
                id = "prof_med_fallback",
                userId = testUserId,
                fullName = "Bob Jones",
                dateOfBirth = "1975-06-15",
                gender = "Male",
                bloodGroup = "A-",
                height = 175.0,
                weight = 80.0,
                allergies = "None",
                existingDiseases = "Diabetes",
                currentMedications = "Metformin 500mg, Lisinopril 10mg",
                familyHistory = null,
                emergencyContactName = "Mary Jones",
                emergencyContactNumber = "+1-555-8888",
                notes = null
            )
        )

        val card = emergencyRepository.getEmergencyHealthCard(testUserId)

        assertEquals(2, card.medications.medications.size)
        assertEquals("Metformin 500mg", card.medications.medications[0].name)
        assertEquals("Lisinopril 10mg", card.medications.medications[1].name)
        assertEquals("Metformin 500mg, Lisinopril 10mg", card.medications.rawFallback)
    }

    // 8. Delimited String Splitting (Semicolon, Newline, Bullet Points)
    @Test
    fun testStringListSplitting() = runBlocking {
        fakeHealthProfileDao.insertHealthProfile(
            HealthProfileEntity(
                id = "prof_delim",
                userId = testUserId,
                fullName = "Delim Test",
                dateOfBirth = "1980-01-01",
                gender = "Other",
                bloodGroup = "AB+",
                height = null,
                weight = null,
                allergies = "• Aspirin\n- Latex\n* Shellfish",
                existingDiseases = "Asthma; Chronic Bronchitis; Sinusitis",
                currentMedications = null,
                familyHistory = null,
                emergencyContactName = null,
                emergencyContactNumber = null,
                notes = null
            )
        )

        val card = emergencyRepository.getEmergencyHealthCard(testUserId)

        assertEquals(3, card.allergies.allergies.size)
        assertEquals("Aspirin", card.allergies.allergies[0])
        assertEquals("Latex", card.allergies.allergies[1])
        assertEquals("Shellfish", card.allergies.allergies[2])

        assertEquals(3, card.conditions.conditions.size)
        assertEquals("Asthma", card.conditions.conditions[0])
        assertEquals("Chronic Bronchitis", card.conditions.conditions[1])
        assertEquals("Sinusitis", card.conditions.conditions[2])
    }

    // 9. Plain Text Formatting Contains All Critical Sections & Disclaimer
    @Test
    fun testFormatAsPlainText_containsAllSectionsAndDisclaimer() = runBlocking {
        fakeHealthProfileDao.insertHealthProfile(
            HealthProfileEntity(
                id = "prof_text",
                userId = testUserId,
                fullName = "Sarah Connor",
                dateOfBirth = "1985-02-28",
                gender = "Female",
                bloodGroup = "AB-",
                height = 168.0,
                weight = 62.0,
                allergies = "Sulfa drugs",
                existingDiseases = "Migraine",
                currentMedications = "Sumatriptan 50mg",
                familyHistory = null,
                emergencyContactName = "John Connor",
                emergencyContactNumber = "+1-555-0999",
                notes = "Carry EpiPen at all times."
            )
        )

        val card = emergencyRepository.getEmergencyHealthCard(testUserId)
        val text = EmergencyHealthCard.formatAsPlainText(card)

        assertTrue(text.contains("MEDISENSE EMERGENCY HEALTH CARD"))
        assertTrue(text.contains("--- EMERGENCY CONTACT ---"))
        assertTrue(text.contains("John Connor"))
        assertTrue(text.contains("+1-555-0999"))
        assertTrue(text.contains("--- KNOWN ALLERGIES ---"))
        assertTrue(text.contains("Sulfa drugs"))
        assertTrue(text.contains("--- BLOOD GROUP ---"))
        assertTrue(text.contains("AB-"))
        assertTrue(text.contains("--- ACTIVE MEDICATIONS ---"))
        assertTrue(text.contains("Sumatriptan 50mg"))
        assertTrue(text.contains("--- MEDICAL CONDITIONS ---"))
        assertTrue(text.contains("Migraine"))
        assertTrue(text.contains("--- PERSONAL INFORMATION ---"))
        assertTrue(text.contains("Sarah Connor"))
        assertTrue(text.contains("DOB: 1985-02-28"))
        assertTrue(text.contains("--- IMPORTANT HEALTH NOTES ---"))
        assertTrue(text.contains("Carry EpiPen at all times."))
        assertTrue(text.contains("CRITICAL NOTICE & MEDICAL DISCLAIMER"))
        assertTrue(text.contains("In a life-threatening medical emergency, immediately contact emergency response personnel"))
    }

    // 10. Reactive Flow Observation
    @Test
    fun testObserveEmergencyHealthCard_emitsUpdates() = runBlocking {
        val initialCard = emergencyRepository.observeEmergencyHealthCard(testUserId).first()
        assertEquals("", initialCard.personalInfo.fullName)

        fakeHealthProfileDao.insertHealthProfile(
            HealthProfileEntity(
                id = "prof_reactive",
                userId = testUserId,
                fullName = "Reactive User",
                dateOfBirth = "1992-10-10",
                gender = "Non-binary",
                bloodGroup = "O-",
                height = null,
                weight = null,
                allergies = "None",
                existingDiseases = "None",
                currentMedications = null,
                familyHistory = null,
                emergencyContactName = "Contact Person",
                emergencyContactNumber = "999-888-777",
                notes = null
            )
        )

        val updatedCard = emergencyRepository.observeEmergencyHealthCard(testUserId).first()
        assertEquals("Reactive User", updatedCard.personalInfo.fullName)
        assertEquals("O-", updatedCard.bloodGroup)
        assertEquals("Contact Person", updatedCard.emergencyContact.name)
    }

    // 11. Security Audit Logging: Card Viewed
    @Test
    fun testAuditLog_cardViewed() = runBlocking {
        securityAuditRepository.recordEvent(SecurityAuditEventType.EMERGENCY_CARD_VIEWED)

        val audits = fakeSecurityAuditDao.getAuditEventsForUser(testUserId)
        val viewEvent = audits.find { it.eventType == SecurityAuditEventType.EMERGENCY_CARD_VIEWED.name }
        assertNotNull(viewEvent)
        assertEquals("Emergency Health Access Card viewed", viewEvent!!.description)
    }

    // 12. Security Audit Logging: Card Shared
    @Test
    fun testAuditLog_cardShared() = runBlocking {
        securityAuditRepository.recordEvent(SecurityAuditEventType.EMERGENCY_CARD_SHARED)

        val audits = fakeSecurityAuditDao.getAuditEventsForUser(testUserId)
        val shareEvent = audits.find { it.eventType == SecurityAuditEventType.EMERGENCY_CARD_SHARED.name }
        assertNotNull(shareEvent)
        assertEquals("Emergency Health Access Card exported/shared", shareEvent!!.description)
    }

    // 13. Security Audit Logging: Dial Initiated
    @Test
    fun testAuditLog_dialInitiated() = runBlocking {
        securityAuditRepository.recordEvent(SecurityAuditEventType.EMERGENCY_CONTACT_DIAL_INITIATED)

        val audits = fakeSecurityAuditDao.getAuditEventsForUser(testUserId)
        val dialEvent = audits.find { it.eventType == SecurityAuditEventType.EMERGENCY_CONTACT_DIAL_INITIATED.name }
        assertNotNull(dialEvent)
        assertEquals("Emergency contact phone dialer opened", dialEvent!!.description)
    }

    // 14. Strict User-Scoped Isolation
    @Test
    fun testUserIsolation_noDataLeakBetweenUsers() = runBlocking {
        fakeHealthProfileDao.insertHealthProfile(
            HealthProfileEntity(
                id = "prof_other",
                userId = otherUserId,
                fullName = "Secret Agent",
                dateOfBirth = "1970-01-01",
                gender = "Male",
                bloodGroup = "AB+",
                height = null,
                weight = null,
                allergies = "Classified",
                existingDiseases = "Classified",
                currentMedications = "Classified Med",
                familyHistory = null,
                emergencyContactName = "Handler",
                emergencyContactNumber = "007",
                notes = "Classified Note"
            )
        )

        val myCard = emergencyRepository.getEmergencyHealthCard(testUserId)
        assertEquals("", myCard.personalInfo.fullName)
        assertEquals("", myCard.bloodGroup)
        assertTrue(myCard.allergies.allergies.isEmpty())
        assertTrue(myCard.medications.medications.isEmpty())
        assertEquals("", myCard.emergencyContact.name)
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
        val meds = mutableListOf<MedicationEntity>()
        override fun getMedicationsForUser(userId: String): Flow<List<MedicationEntity>> = flowOf(meds.filter { it.userId == userId })
        override fun getActiveMedicationsForUser(userId: String): Flow<List<MedicationEntity>> = flowOf(meds.filter { it.userId == userId && it.active })
        override suspend fun getActiveMedicationsForUserSync(userId: String): List<MedicationEntity> = meds.filter { it.userId == userId && it.active }
        override suspend fun getAllActiveMedicationsSync(): List<MedicationEntity> = meds.filter { it.active }
        override suspend fun getMedicationById(id: Long, userId: String): MedicationEntity? = meds.find { it.id == id && it.userId == userId }
        override suspend fun getMedicationByIdSync(id: Long): MedicationEntity? = meds.find { it.id == id }
        override suspend fun insertMedication(medication: MedicationEntity): Long {
            meds.add(medication)
            return medication.id
        }
        override suspend fun updateMedication(medication: MedicationEntity) {
            meds.removeAll { it.id == medication.id }
            meds.add(medication)
        }
        override suspend fun deleteMedicationById(id: Long, userId: String) { meds.removeAll { it.id == id && it.userId == userId } }
        override suspend fun deleteAllMedicationsForUser(userId: String) { meds.removeAll { it.userId == userId } }
        override suspend fun getPendingSyncMedications(userId: String): List<MedicationEntity> = emptyList()
        override suspend fun getAllMedicationsForUserSync(userId: String): List<MedicationEntity> = meds.filter { it.userId == userId }
        override suspend fun markMedicationSynced(id: Long, userId: String) {}
        override suspend fun upsertMedications(medications: List<MedicationEntity>) { meds.addAll(medications) }
    }

    private class FakeSecurityAuditEventDao : SecurityAuditEventDao {
        val events = mutableListOf<SecurityAuditEventEntity>()
        override fun observeRecentAuditEvents(userId: String, limit: Int): Flow<List<SecurityAuditEventEntity>> = flowOf(events.filter { it.userId == userId }.take(limit))
        override suspend fun getAuditEventsForUser(userId: String): List<SecurityAuditEventEntity> = events.filter { it.userId == userId }
        override suspend fun insertAuditEvent(event: SecurityAuditEventEntity): Long {
            events.add(event)
            return event.id
        }
        override suspend fun deleteAllAuditEventsForUser(userId: String) { events.removeAll { it.userId == userId } }
    }
}
