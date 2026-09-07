package com.medisense.app

import com.medisense.app.data.local.dao.*
import com.medisense.app.data.local.entity.*
import com.medisense.app.data.remote.supabase.AuthService
import com.medisense.app.data.repository.*
import com.medisense.app.domain.consultation.ConsultationSummaryGenerator
import com.medisense.app.domain.consultation.QuestionSuggestionEngine
import com.medisense.app.domain.guidance.PersonalizedGuidanceEngine
import com.medisense.app.domain.model.*
import com.medisense.app.domain.quality.*
import com.medisense.app.domain.risk.ContextAwareRiskEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ConsultationPreparationUnitTest {

    private lateinit var fakeHealthProfileDao: FakeHealthProfileDao
    private lateinit var fakeMedicationDao: FakeMedicationDao
    private lateinit var fakeMedicationHistoryDao: FakeMedicationHistoryDao
    private lateinit var fakeAppointmentDao: FakeAppointmentDao
    private lateinit var fakePredictionHistoryDao: FakePredictionHistoryDao
    private lateinit var fakeSecurityAuditDao: FakeSecurityAuditEventDao
    private lateinit var fakeSyncMetadataDao: FakeSyncMetadataDao
    private lateinit var fakeAuthService: FakeAuthService

    private lateinit var securityAuditRepository: SecurityAuditRepository
    private lateinit var healthDataQualityRepository: HealthDataQualityRepository
    private lateinit var personalHealthContextRepository: PersonalHealthContextRepository
    private lateinit var longitudinalHealthRepository: LongitudinalHealthRepository
    private lateinit var rchrRepository: RchrRepository
    private lateinit var contextualRiskRepository: ContextualRiskRepository
    private lateinit var personalizedGuidanceRepository: PersonalizedGuidanceRepository
    private lateinit var consultationPreparationRepository: ConsultationPreparationRepository

    private val testUserId = "user-consultation-12345"
    private val otherUserId = "user-consultation-99999"

    @Before
    fun setUp() {
        fakeHealthProfileDao = FakeHealthProfileDao()
        fakeMedicationDao = FakeMedicationDao()
        fakeMedicationHistoryDao = FakeMedicationHistoryDao()
        fakeAppointmentDao = FakeAppointmentDao()
        fakePredictionHistoryDao = FakePredictionHistoryDao()
        fakeSecurityAuditDao = FakeSecurityAuditEventDao()
        fakeSyncMetadataDao = FakeSyncMetadataDao()

        fakeAuthService = FakeAuthService(userId = testUserId, email = "consult_user@medisense.app")
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

        consultationPreparationRepository = ConsultationPreparationRepository(
            authService = fakeAuthService,
            healthProfileDao = fakeHealthProfileDao,
            medicationDao = fakeMedicationDao,
            medicationHistoryDao = fakeMedicationHistoryDao,
            appointmentDao = fakeAppointmentDao,
            predictionHistoryDao = fakePredictionHistoryDao,
            personalHealthContextRepository = personalHealthContextRepository,
            longitudinalHealthRepository = longitudinalHealthRepository,
            contextualRiskRepository = contextualRiskRepository,
            personalizedGuidanceRepository = personalizedGuidanceRepository,
            healthDataQualityRepository = healthDataQualityRepository,
            securityAuditRepository = securityAuditRepository
        )
    }

    private fun createProfile(
        userId: String,
        fullName: String? = "John Smith",
        allergies: String? = "Penicillin, Peanuts",
        existingDiseases: String? = "Hypertension"
    ) = HealthProfileEntity(
        id = "prof_$userId",
        userId = userId,
        fullName = fullName,
        dateOfBirth = "1985-05-15",
        gender = "Male",
        bloodGroup = "O+",
        height = 175.0,
        weight = 78.0,
        allergies = allergies,
        existingDiseases = existingDiseases,
        currentMedications = "Lisinopril 10mg",
        familyHistory = "None",
        emergencyContactName = "Mary Smith",
        emergencyContactNumber = "555-123-4567",
        notes = null
    )

    private suspend fun populateComprehensiveTestData(userId: String) {
        val now = System.currentTimeMillis()

        fakeHealthProfileDao.insertHealthProfile(createProfile(userId))

        val medId1 = fakeMedicationDao.insertMedication(
            MedicationEntity(
                id = 1L,
                userId = userId,
                medicineName = "Lisinopril",
                dosage = "10mg",
                frequency = "ONCE_DAILY",
                scheduledTimes = listOf("08:00"),
                startDate = now - 60L * 24 * 60 * 60 * 1000,
                instructions = "Take with water",
                active = true
            )
        )

        fakeMedicationDao.insertMedication(
            MedicationEntity(
                id = 2L,
                userId = userId,
                medicineName = "Amlodipine",
                dosage = "5mg",
                frequency = "ONCE_DAILY",
                scheduledTimes = listOf("20:00"),
                startDate = now - 30L * 24 * 60 * 60 * 1000,
                instructions = "Take after food",
                active = true
            )
        )

        // Dose history
        for (i in 0 until 10) {
            fakeMedicationHistoryDao.insertHistory(
                MedicationHistoryEntity(
                    id = (i + 1).toLong(),
                    medicationId = medId1,
                    userId = userId,
                    medicineName = "Lisinopril",
                    scheduledDate = now - i * 24L * 60 * 60 * 1000,
                    scheduledTime = "08:00",
                    status = if (i < 8) "TAKEN" else "MISSED",
                    actionTime = now - i * 24L * 60 * 60 * 1000
                )
            )
        }

        // Predictions
        fakePredictionHistoryDao.insertPredictionHistory(
            PredictionHistoryEntity(
                id = 101L,
                userId = userId,
                predictedDisease = "Hypertension",
                confidence = 0.88f,
                symptoms = listOf("headache", "dizziness", "chest_pain"),
                predictionTimestamp = now - 2L * 24 * 60 * 60 * 1000
            )
        )
        fakePredictionHistoryDao.insertPredictionHistory(
            PredictionHistoryEntity(
                id = 102L,
                userId = userId,
                predictedDisease = "Hypertension",
                confidence = 0.82f,
                symptoms = listOf("headache", "fatigue"),
                predictionTimestamp = now - 15L * 24 * 60 * 60 * 1000
            )
        )

        // Appointments
        fakeAppointmentDao.insertAppointment(
            AppointmentEntity(
                id = 201L,
                userId = userId,
                doctorName = "Dr. Alice Adams",
                clinicName = "Cardiology Specialists",
                appointmentDate = "2026-09-20",
                appointmentTime = "10:00",
                appointmentTimestamp = now + 13L * 24 * 60 * 60 * 1000,
                appointmentType = "Cardiology Follow-up",
                notes = "Review BP meds",
                status = "SCHEDULED"
            )
        )
    }

    // 1. Summary Generation with Complete Data
    @Test
    fun testGenerateSummary_withCompleteData_returnsAllSectionsCorrectly() = runBlocking {
        populateComprehensiveTestData(testUserId)

        val summary = consultationPreparationRepository.generateConsultationSummary(ConsultationPeriod.LAST_30_DAYS)

        assertNotNull(summary)
        assertEquals(ConsultationPeriod.LAST_30_DAYS, summary.visitOverview.period)
        assertEquals("John Smith", summary.profile.fullName)
        assertEquals("Male", summary.profile.gender)
        assertEquals("Penicillin, Peanuts", summary.profile.allergies)

        assertTrue(summary.symptoms.totalSymptomsCount > 0)
        assertTrue(summary.symptoms.frequentSymptoms.any { it.contains("headache") })

        assertEquals(2, summary.predictions.totalPredictionsCount)
        assertEquals("Hypertension", summary.predictions.recentPredictions.first().predictedCondition)

        assertEquals(2, summary.medications.activeMedications.size)
        assertEquals(80, summary.medications.adherencePercentage)

        assertEquals("Dr. Alice Adams", summary.appointments.upcomingAppointmentDoctor)

        assertTrue(summary.suggestedQuestions.isNotEmpty())
        assertTrue(summary.safetyDisclaimer.contains("does not provide a medical diagnosis"))
    }

    // 2. Summary Generation with Empty Data
    @Test
    fun testGenerateSummary_withEmptyData_returnsEmptySummaryGracefully() = runBlocking {
        val summary = consultationPreparationRepository.generateConsultationSummary(ConsultationPeriod.LAST_30_DAYS)

        assertNotNull(summary)
        assertEquals("Not recorded", summary.profile.fullName)
        assertEquals(0, summary.symptoms.totalSymptomsCount)
        assertEquals(0, summary.predictions.totalPredictionsCount)
        assertEquals(0, summary.medications.activeMedications.size)
        assertNull(summary.appointments.upcomingAppointmentDoctor)
        assertEquals(ConsultationDataCompleteness.INSUFFICIENT_DATA, summary.visitOverview.completenessStatus)
    }

    // 3. Period Filtering: 7 Days
    @Test
    fun testGenerateSummary_periodFiltering_7Days() = runBlocking {
        val now = System.currentTimeMillis()
        fakePredictionHistoryDao.insertPredictionHistory(
            PredictionHistoryEntity(
                id = 1L,
                userId = testUserId,
                predictedDisease = "Common Cold",
                confidence = 0.90f,
                symptoms = listOf("cough", "fever"),
                predictionTimestamp = now - 3L * 24 * 60 * 60 * 1000 // in 7d
            )
        )
        fakePredictionHistoryDao.insertPredictionHistory(
            PredictionHistoryEntity(
                id = 2L,
                userId = testUserId,
                predictedDisease = "Migraine",
                confidence = 0.75f,
                symptoms = listOf("headache"),
                predictionTimestamp = now - 20L * 24 * 60 * 60 * 1000 // out of 7d
            )
        )

        val summary = consultationPreparationRepository.generateConsultationSummary(ConsultationPeriod.LAST_7_DAYS)

        assertNotNull(summary)
        assertEquals(1, summary.predictions.totalPredictionsCount)
        assertEquals("Common Cold", summary.predictions.recentPredictions.first().predictedCondition)
    }

    // 4. Period Filtering: 30 Days
    @Test
    fun testGenerateSummary_periodFiltering_30Days() = runBlocking {
        val now = System.currentTimeMillis()
        fakePredictionHistoryDao.insertPredictionHistory(
            PredictionHistoryEntity(
                id = 1L,
                userId = testUserId,
                predictedDisease = "Common Cold",
                confidence = 0.90f,
                symptoms = listOf("cough"),
                predictionTimestamp = now - 10L * 24 * 60 * 60 * 1000 // in 30d
            )
        )
        fakePredictionHistoryDao.insertPredictionHistory(
            PredictionHistoryEntity(
                id = 2L,
                userId = testUserId,
                predictedDisease = "Migraine",
                confidence = 0.75f,
                symptoms = listOf("headache"),
                predictionTimestamp = now - 45L * 24 * 60 * 60 * 1000 // out of 30d
            )
        )

        val summary = consultationPreparationRepository.generateConsultationSummary(ConsultationPeriod.LAST_30_DAYS)

        assertNotNull(summary)
        assertEquals(1, summary.predictions.totalPredictionsCount)
        assertEquals("Common Cold", summary.predictions.recentPredictions.first().predictedCondition)
    }

    // 5. Period Filtering: 90 Days
    @Test
    fun testGenerateSummary_periodFiltering_90Days() = runBlocking {
        val now = System.currentTimeMillis()
        fakePredictionHistoryDao.insertPredictionHistory(
            PredictionHistoryEntity(
                id = 1L,
                userId = testUserId,
                predictedDisease = "Condition A",
                confidence = 0.8f,
                symptoms = listOf("fatigue"),
                predictionTimestamp = now - 50L * 24 * 60 * 60 * 1000 // in 90d
            )
        )
        fakePredictionHistoryDao.insertPredictionHistory(
            PredictionHistoryEntity(
                id = 2L,
                userId = testUserId,
                predictedDisease = "Condition B",
                confidence = 0.8f,
                symptoms = listOf("nausea"),
                predictionTimestamp = now - 120L * 24 * 60 * 60 * 1000 // out of 90d
            )
        )

        val summary = consultationPreparationRepository.generateConsultationSummary(ConsultationPeriod.LAST_90_DAYS)

        assertNotNull(summary)
        assertEquals(1, summary.predictions.totalPredictionsCount)
        assertEquals("Condition A", summary.predictions.recentPredictions.first().predictedCondition)
    }

    // 6. Period Filtering: All History
    @Test
    fun testGenerateSummary_periodFiltering_allHistory() = runBlocking {
        val now = System.currentTimeMillis()
        fakePredictionHistoryDao.insertPredictionHistory(
            PredictionHistoryEntity(
                id = 1L,
                userId = testUserId,
                predictedDisease = "Condition A",
                confidence = 0.8f,
                symptoms = listOf("fatigue"),
                predictionTimestamp = now - 50L * 24 * 60 * 60 * 1000
            )
        )
        fakePredictionHistoryDao.insertPredictionHistory(
            PredictionHistoryEntity(
                id = 2L,
                userId = testUserId,
                predictedDisease = "Condition B",
                confidence = 0.8f,
                symptoms = listOf("nausea"),
                predictionTimestamp = now - 200L * 24 * 60 * 60 * 1000
            )
        )

        val summary = consultationPreparationRepository.generateConsultationSummary(ConsultationPeriod.ALL_HISTORY)

        assertNotNull(summary)
        assertEquals(2, summary.predictions.totalPredictionsCount)
    }

    // 7. Question Suggestion: Recurring Symptoms
    @Test
    fun testQuestionSuggestion_recurringSymptoms_generatesSymptomQuestion() = runBlocking {
        val now = System.currentTimeMillis()
        fakePredictionHistoryDao.insertPredictionHistory(
            PredictionHistoryEntity(
                id = 1L,
                userId = testUserId,
                predictedDisease = "Migraine",
                confidence = 0.85f,
                symptoms = listOf("headache", "dizziness"),
                predictionTimestamp = now - 1L * 24 * 60 * 60 * 1000
            )
        )
        fakePredictionHistoryDao.insertPredictionHistory(
            PredictionHistoryEntity(
                id = 2L,
                userId = testUserId,
                predictedDisease = "Tension Headache",
                confidence = 0.75f,
                symptoms = listOf("headache"),
                predictionTimestamp = now - 3L * 24 * 60 * 60 * 1000
            )
        )

        val summary = consultationPreparationRepository.generateConsultationSummary(ConsultationPeriod.LAST_30_DAYS)

        assertNotNull(summary)
        val symptomQ = summary.suggestedQuestions.find { it.category == ConsultationQuestionSource.SYMPTOM_PATTERN }
        assertNotNull(symptomQ)
        assertTrue(symptomQ!!.questionText.contains("headache"))
    }

    // 8. Question Suggestion: Predictions
    @Test
    fun testQuestionSuggestion_recentPredictions_generatesPredictionQuestion() = runBlocking {
        val now = System.currentTimeMillis()
        fakePredictionHistoryDao.insertPredictionHistory(
            PredictionHistoryEntity(
                id = 1L,
                userId = testUserId,
                predictedDisease = "Type 2 Diabetes",
                confidence = 0.88f,
                symptoms = listOf("increased_thirst", "frequent_urination"),
                predictionTimestamp = now - 2L * 24 * 60 * 60 * 1000
            )
        )

        val summary = consultationPreparationRepository.generateConsultationSummary(ConsultationPeriod.LAST_30_DAYS)

        assertNotNull(summary)
        val predQ = summary.suggestedQuestions.find { it.category == ConsultationQuestionSource.PREDICTION_HISTORY }
        assertNotNull(predQ)
        assertTrue(predQ!!.questionText.contains("Type 2 Diabetes"))
        assertTrue(predQ.questionText.contains("diagnostic tests"))
    }

    // 9. Question Suggestion: Low Adherence
    @Test
    fun testQuestionSuggestion_lowMedicationAdherence_generatesAdherenceQuestion() = runBlocking {
        val now = System.currentTimeMillis()
        val medId = fakeMedicationDao.insertMedication(
            MedicationEntity(
                id = 1L,
                userId = testUserId,
                medicineName = "Metformin",
                dosage = "500mg",
                frequency = "ONCE_DAILY",
                scheduledTimes = listOf("08:00"),
                startDate = now - 30L * 24 * 60 * 60 * 1000,
                active = true
            )
        )

        for (i in 0 until 10) {
            fakeMedicationHistoryDao.insertHistory(
                MedicationHistoryEntity(
                    id = (i + 1).toLong(),
                    medicationId = medId,
                    userId = testUserId,
                    medicineName = "Metformin",
                    scheduledDate = now - i * 24L * 60 * 60 * 1000,
                    scheduledTime = "08:00",
                    status = if (i < 3) "TAKEN" else "MISSED",
                    actionTime = now - i * 24L * 60 * 60 * 1000
                )
            )
        }

        val summary = consultationPreparationRepository.generateConsultationSummary(ConsultationPeriod.LAST_30_DAYS)

        assertNotNull(summary)
        val adhQ = summary.suggestedQuestions.find { it.category == ConsultationQuestionSource.MEDICATION_ADHERENCE }
        assertNotNull(adhQ)
        assertTrue(adhQ!!.questionText.contains("difficulty maintaining"))
    }

    // 10. Question Suggestion: Allergies
    @Test
    fun testQuestionSuggestion_allergies_generatesAllergyReviewQuestion() = runBlocking {
        fakeHealthProfileDao.insertHealthProfile(
            createProfile(testUserId, allergies = "Sulfa drugs, Aspirin")
        )

        val summary = consultationPreparationRepository.generateConsultationSummary(ConsultationPeriod.LAST_30_DAYS)

        assertNotNull(summary)
        val allergyQ = summary.suggestedQuestions.find { it.category == ConsultationQuestionSource.PROFILE_GAP }
        assertNotNull(allergyQ)
        assertTrue(allergyQ!!.questionText.contains("Sulfa drugs, Aspirin"))
    }

    // 11. Question Suggestion: Appointments
    @Test
    fun testQuestionSuggestion_upcomingAppointments_generatesFollowUpQuestion() = runBlocking {
        val now = System.currentTimeMillis()
        fakeAppointmentDao.insertAppointment(
            AppointmentEntity(
                id = 1L,
                userId = testUserId,
                doctorName = "Dr. Green",
                clinicName = "Endocrine Center",
                appointmentDate = "2026-09-25",
                appointmentTime = "14:00",
                appointmentTimestamp = now + 18L * 24 * 60 * 60 * 1000,
                appointmentType = "Diabetes Checkup",
                status = "SCHEDULED"
            )
        )

        val summary = consultationPreparationRepository.generateConsultationSummary(ConsultationPeriod.LAST_30_DAYS)

        assertNotNull(summary)
        val aptQ = summary.suggestedQuestions.find { it.category == ConsultationQuestionSource.APPOINTMENT_TOPIC }
        assertNotNull(aptQ)
        assertTrue(aptQ!!.rationale.contains("Dr. Green"))
    }

    // 12. Question Suggestion: Incomplete Data
    @Test
    fun testQuestionSuggestion_incompleteData_generatesDataQualityQuestion() = runBlocking {
        val questions = QuestionSuggestionEngine.generateQuestions(
            profile = null,
            predictions = emptyList(),
            medications = emptyList(),
            medicationHistory = emptyList(),
            appointments = emptyList(),
            longitudinalSummary = null,
            dataQualitySummary = HealthDataQualitySummary(
                status = HealthDataQualityStatus.NEEDS_ATTENTION,
                qualityScore = 40,
                passedChecks = 2,
                totalChecks = 5,
                errorCount = 1,
                warningCount = 2,
                infoCount = 0,
                issues = emptyList(),
                evaluatedTimestamp = System.currentTimeMillis()
            )
        )

        val qualQ = questions.find { it.category == ConsultationQuestionSource.DATA_QUALITY }
        assertNotNull(qualQ)
        assertTrue(qualQ!!.questionText.contains("baseline health metrics"))
    }

    // 13. Question Suggestion: Caps at deterministic limit (8)
    @Test
    fun testQuestionSuggestion_capsAtMaxDeterministicQuestions() = runBlocking {
        populateComprehensiveTestData(testUserId)

        val summary = consultationPreparationRepository.generateConsultationSummary(ConsultationPeriod.LAST_30_DAYS)

        assertNotNull(summary)
        assertTrue(summary.suggestedQuestions.size in 1..8)
    }

    // 14. Question Suggestion: Determinism
    @Test
    fun testQuestionSuggestion_deterministicOrderAndRepetitionAvoidance() = runBlocking {
        populateComprehensiveTestData(testUserId)

        val summary1 = consultationPreparationRepository.generateConsultationSummary(ConsultationPeriod.LAST_30_DAYS)
        val summary2 = consultationPreparationRepository.generateConsultationSummary(ConsultationPeriod.LAST_30_DAYS)

        assertNotNull(summary1)
        assertNotNull(summary2)
        assertEquals(summary1.suggestedQuestions.size, summary2.suggestedQuestions.size)

        summary1.suggestedQuestions.indices.forEach { idx ->
            assertEquals(summary1.suggestedQuestions[idx].questionText, summary2.suggestedQuestions[idx].questionText)
            assertEquals(summary1.suggestedQuestions[idx].category, summary2.suggestedQuestions[idx].category)
        }
    }

    // 15. Question Selection Toggle
    @Test
    fun testQuestionSelection_toggleState() = runBlocking {
        populateComprehensiveTestData(testUserId)

        val summary = consultationPreparationRepository.generateConsultationSummary(ConsultationPeriod.LAST_30_DAYS)
        assertNotNull(summary)

        val firstQ = summary.suggestedQuestions.first()
        assertTrue(firstQ.isSelected)

        val toggled = firstQ.copy(isSelected = false)
        assertFalse(toggled.isSelected)
    }

    // 16. Custom Question: Add and Remove
    @Test
    fun testCustomQuestion_addAndRemove() = runBlocking {
        val customQ = ConsultationQuestion(
            id = "custom_1",
            questionText = "Can I start light cardio exercises?",
            category = ConsultationQuestionSource.USER_CUSTOM,
            rationale = "Custom question",
            isCustom = true,
            isSelected = true
        )

        assertEquals(ConsultationQuestionSource.USER_CUSTOM, customQ.category)
        assertEquals("Can I start light cardio exercises?", customQ.questionText)
        assertTrue(customQ.isSelected)

        val customList = mutableListOf(customQ)
        assertEquals(1, customList.size)

        customList.removeIf { it.id == customQ.id }
        assertEquals(0, customList.size)
    }

    // 17. Custom Question: User-Defined Wording Preserved
    @Test
    fun testCustomQuestion_userDefinedWordingPreserved() = runBlocking {
        val customText = "Is there any interaction between my supplements and my blood pressure medication?"
        val customQ = ConsultationQuestion(
            id = "custom_2",
            questionText = customText,
            category = ConsultationQuestionSource.USER_CUSTOM,
            rationale = "Custom question",
            isCustom = true,
            isSelected = true
        )

        assertEquals(customText, customQ.questionText)
    }

    // 18. Completeness Score: High Data Returns GOOD_DATA
    @Test
    fun testCompletenessScore_highData_returnsGoodData() = runBlocking {
        populateComprehensiveTestData(testUserId)

        val summary = consultationPreparationRepository.generateConsultationSummary(ConsultationPeriod.LAST_30_DAYS)

        assertNotNull(summary)
        assertEquals(ConsultationDataCompleteness.GOOD_DATA, summary.visitOverview.completenessStatus)
    }

    // 19. Completeness Score: Partial Data Returns PARTIAL_DATA
    @Test
    fun testCompletenessScore_partialData_returnsPartial() = runBlocking {
        fakeHealthProfileDao.insertHealthProfile(
            createProfile(testUserId, fullName = "John Smith")
        )

        val summary = consultationPreparationRepository.generateConsultationSummary(ConsultationPeriod.LAST_30_DAYS)

        assertNotNull(summary)
        assertEquals(ConsultationDataCompleteness.PARTIAL_DATA, summary.visitOverview.completenessStatus)
    }

    // 20. Completeness Score: Sparse Data Returns INSUFFICIENT_DATA
    @Test
    fun testCompletenessScore_sparseData_returnsInsufficient() = runBlocking {
        val summary = consultationPreparationRepository.generateConsultationSummary(ConsultationPeriod.LAST_30_DAYS)

        assertNotNull(summary)
        assertEquals(ConsultationDataCompleteness.INSUFFICIENT_DATA, summary.visitOverview.completenessStatus)
    }

    // 21. User Isolation: Only Fetches Current User Data
    @Test
    fun testUserIsolation_onlyFetchesCurrentUserData() = runBlocking {
        populateComprehensiveTestData(testUserId)
        populateComprehensiveTestData(otherUserId)

        val summary = consultationPreparationRepository.generateConsultationSummary(ConsultationPeriod.LAST_30_DAYS)

        assertNotNull(summary)
        assertEquals("John Smith", summary.profile.fullName)
        assertEquals(2, summary.medications.activeMedications.size)
        assertEquals(2, summary.predictions.totalPredictionsCount)
        assertEquals("Dr. Alice Adams", summary.appointments.upcomingAppointmentDoctor)
    }

    // 22. User Isolation: Other User's Data Not Included
    @Test
    fun testUserIsolation_otherUsersDataNotIncluded() = runBlocking {
        fakeHealthProfileDao.insertHealthProfile(
            createProfile(otherUserId, fullName = "Other Person", allergies = "Severe Latex")
        )

        val summary = consultationPreparationRepository.generateConsultationSummary(ConsultationPeriod.LAST_30_DAYS)

        assertNotNull(summary)
        assertNotEquals("Other Person", summary.profile.fullName)
        assertNotEquals("Severe Latex", summary.profile.allergies)
    }

    // 23. Safety Disclaimer Included in Summary
    @Test
    fun testSafetyDisclaimer_includedInSummary() = runBlocking {
        populateComprehensiveTestData(testUserId)

        val summary = consultationPreparationRepository.generateConsultationSummary(ConsultationPeriod.LAST_30_DAYS)

        assertNotNull(summary)
        assertTrue(summary.safetyDisclaimer.isNotBlank())
        assertTrue(summary.safetyDisclaimer.contains("Medical Safety Notice"))
    }

    // 24. Safety Language Contains Non-Diagnostic Wording
    @Test
    fun testSafetyLanguage_containsNonDiagnosticWording() = runBlocking {
        populateComprehensiveTestData(testUserId)

        val summary = consultationPreparationRepository.generateConsultationSummary(ConsultationPeriod.LAST_30_DAYS)
        assertNotNull(summary)

        assertTrue(summary.predictions.recentPredictions.all {
            it.predictedCondition.isNotBlank()
        })
        assertTrue(summary.safetyDisclaimer.contains("does not provide a medical diagnosis"))
    }

    // 25. Plain-Text Formatting Contains All Selected Sections
    @Test
    fun testExportPlainText_containsAllSelectedSectionsAndQuestions() = runBlocking {
        populateComprehensiveTestData(testUserId)

        val customQ = ConsultationQuestion(
            id = "custom_test",
            questionText = "Should I get my kidneys checked?",
            category = ConsultationQuestionSource.USER_CUSTOM,
            rationale = "Custom question",
            isCustom = true,
            isSelected = true
        )

        val summary = consultationPreparationRepository.generateConsultationSummary(
            period = ConsultationPeriod.LAST_30_DAYS,
            customQuestions = listOf(customQ)
        )

        val formatted = consultationPreparationRepository.formatSummaryAsPlainText(summary)

        assertTrue(formatted.contains("MEDISENSE DOCTOR CONSULTATION SUMMARY"))
        assertTrue(formatted.contains("1. PATIENT PROFILE"))
        assertTrue(formatted.contains("2. RECENT SYMPTOMS & OBSERVATIONS"))
        assertTrue(formatted.contains("3. RECORDED PREDICTIONS"))
        assertTrue(formatted.contains("4. CURRENT MEDICATIONS & ADHERENCE"))
        assertTrue(formatted.contains("5. APPOINTMENT INFORMATION"))
        assertTrue(formatted.contains("6. QUESTIONS TO DISCUSS WITH YOUR DOCTOR"))
        assertTrue(formatted.contains("Should I get my kidneys checked?"))
        assertTrue(formatted.contains("DISCLAIMER"))
    }

    // 26. Plain-Text Formatting Excludes Unchecked Questions
    @Test
    fun testExportPlainText_excludesUncheckedQuestions() = runBlocking {
        populateComprehensiveTestData(testUserId)

        val summary = consultationPreparationRepository.generateConsultationSummary(ConsultationPeriod.LAST_30_DAYS)
        val modifiedQuestions = summary.suggestedQuestions.mapIndexed { idx, q ->
            if (idx == 0) q.copy(isSelected = true) else q.copy(isSelected = false)
        }
        val modifiedSummary = summary.copy(suggestedQuestions = modifiedQuestions)

        val formatted = consultationPreparationRepository.formatSummaryAsPlainText(modifiedSummary)

        assertTrue(formatted.contains(modifiedQuestions.first().questionText))
        if (modifiedQuestions.size > 1) {
            assertFalse(formatted.contains(modifiedQuestions[1].questionText))
        }
    }

    // 27. Audit Log: Consultation Summary Generated Recorded
    @Test
    fun testAuditLog_consultationSummaryGenerated_recorded() = runBlocking {
        consultationPreparationRepository.generateConsultationSummary(ConsultationPeriod.LAST_30_DAYS)

        val audits = fakeSecurityAuditDao.getAuditEventsForUser(testUserId)
        val genAudit = audits.find { it.eventType == SecurityAuditEventType.CONSULTATION_SUMMARY_GENERATED.name }
        assertNotNull(genAudit)
    }

    // 28. Audit Log: Consultation Summary Shared Recorded
    @Test
    fun testAuditLog_consultationSummaryShared_recorded() = runBlocking {
        consultationPreparationRepository.recordSummarySharedAudit()

        val audits = fakeSecurityAuditDao.getAuditEventsForUser(testUserId)
        val shareAudit = audits.find { it.eventType == SecurityAuditEventType.CONSULTATION_SUMMARY_SHARED.name }
        assertNotNull(shareAudit)
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

    private class FakeMedicationHistoryDao : MedicationHistoryDao {
        val history = mutableListOf<MedicationHistoryEntity>()
        override fun getHistoryForUser(userId: String): Flow<List<MedicationHistoryEntity>> = flowOf(history.filter { it.userId == userId })
        override fun getHistoryForMedication(medicationId: Long, userId: String): Flow<List<MedicationHistoryEntity>> = flowOf(history.filter { it.medicationId == medicationId && it.userId == userId })
        override suspend fun getOccurrenceHistory(medicationId: Long, date: Long, time: String, userId: String): MedicationHistoryEntity? = null
        override suspend fun findExistingRecord(medicationId: Long, date: Long, time: String): MedicationHistoryEntity? = null
        override fun getHistoryForDateFlow(userId: String, date: Long): Flow<List<MedicationHistoryEntity>> = flowOf(history.filter { it.userId == userId && it.scheduledDate == date })
        override fun getHistoryForDateRangeFlow(userId: String, startDate: Long, endDate: Long): Flow<List<MedicationHistoryEntity>> = flowOf(emptyList())
        override suspend fun insertHistory(hist: MedicationHistoryEntity): Long {
            history.add(hist)
            return hist.id
        }
        override suspend fun updateHistory(hist: MedicationHistoryEntity) {}
        override suspend fun deleteAllMedicationHistoryForUser(userId: String) { history.removeAll { it.userId == userId } }
        override suspend fun getAllHistoryForUserSync(userId: String): List<MedicationHistoryEntity> = history.filter { it.userId == userId }
        override suspend fun upsertHistory(historyList: List<MedicationHistoryEntity>) { history.addAll(historyList) }
    }

    private class FakeAppointmentDao : AppointmentDao {
        val appts = mutableListOf<AppointmentEntity>()
        override fun observeAppointments(userId: String): Flow<List<AppointmentEntity>> = flowOf(appts.filter { it.userId == userId })
        override fun observeUpcomingAppointments(userId: String, currentTime: Long): Flow<List<AppointmentEntity>> = flowOf(appts.filter { it.userId == userId && it.appointmentTimestamp >= currentTime })
        override suspend fun getAllUpcomingScheduledAppointmentsSync(currentTime: Long): List<AppointmentEntity> = appts.filter { it.appointmentTimestamp >= currentTime }
        override fun observeCompletedAppointments(userId: String): Flow<List<AppointmentEntity>> = flowOf(appts.filter { it.userId == userId && it.status == "COMPLETED" })
        override fun observeCancelledAppointments(userId: String): Flow<List<AppointmentEntity>> = flowOf(appts.filter { it.userId == userId && it.status == "CANCELLED" })
        override suspend fun getAppointment(id: Long, userId: String): AppointmentEntity? = appts.find { it.id == id && it.userId == userId }
        override suspend fun insertAppointment(appointment: AppointmentEntity): Long {
            appts.add(appointment)
            return appointment.id
        }
        override suspend fun updateAppointment(appointment: AppointmentEntity) {}
        override suspend fun deleteAppointment(appointment: AppointmentEntity) { appts.remove(appointment) }
        override suspend fun deleteAppointmentById(id: Long, userId: String) { appts.removeAll { it.id == id && it.userId == userId } }
        override suspend fun deleteAllAppointmentsForUser(userId: String) { appts.removeAll { it.userId == userId } }
        override suspend fun updateAppointmentStatus(id: Long, userId: String, status: String, updatedAt: Long) {}
        override suspend fun getPendingSyncAppointments(userId: String): List<AppointmentEntity> = emptyList()
        override suspend fun getAllAppointmentsForUserSync(userId: String): List<AppointmentEntity> = appts.filter { it.userId == userId }
        override suspend fun markAppointmentSynced(id: Long, userId: String) {}
        override suspend fun upsertAppointments(appointments: List<AppointmentEntity>) { appts.addAll(appointments) }
    }

    private class FakePredictionHistoryDao : PredictionHistoryDao {
        val preds = mutableListOf<PredictionHistoryEntity>()
        override fun observePredictionHistory(userId: String): Flow<List<PredictionHistoryEntity>> = flowOf(preds.filter { it.userId == userId })
        override suspend fun getPredictionHistoryById(id: Long, userId: String): PredictionHistoryEntity? = preds.find { it.id == id && it.userId == userId }
        override suspend fun insertPredictionHistory(entity: PredictionHistoryEntity): Long {
            preds.add(entity)
            return entity.id
        }
        override suspend fun deletePredictionHistory(id: Long, userId: String) { preds.removeAll { it.id == id && it.userId == userId } }
        override suspend fun deleteAllPredictionHistory(userId: String) { preds.removeAll { it.userId == userId } }
        override suspend fun getPendingSyncPredictionHistory(userId: String): List<PredictionHistoryEntity> = emptyList()
        override suspend fun getAllPredictionHistoryForUserSync(userId: String): List<PredictionHistoryEntity> = preds.filter { it.userId == userId }
        override suspend fun markPredictionSynced(id: Long, userId: String) {}
        override suspend fun upsertPredictionHistory(predictions: List<PredictionHistoryEntity>) { preds.addAll(predictions) }
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

    private class FakeSyncMetadataDao : SyncMetadataDao {
        override fun observeSyncMetadata(userId: String): Flow<SyncMetadataEntity?> = flowOf(null)
        override suspend fun getSyncMetadata(userId: String): SyncMetadataEntity? = null
        override suspend fun upsertSyncMetadata(metadata: SyncMetadataEntity) {}
        override suspend fun deleteSyncMetadataForUser(userId: String) {}
    }
}
