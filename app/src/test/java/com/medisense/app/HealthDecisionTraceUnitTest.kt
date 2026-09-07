package com.medisense.app

import com.medisense.app.data.local.dao.*
import com.medisense.app.data.local.entity.*
import com.medisense.app.data.remote.supabase.AuthService
import com.medisense.app.data.repository.*
import com.medisense.app.domain.risk.ContextAwareRiskEngine
import com.medisense.app.domain.guidance.PersonalizedGuidanceEngine
import com.medisense.app.domain.model.*
import com.medisense.app.domain.quality.*
import com.medisense.app.domain.rchr.RchrProfileFeatures
import com.medisense.app.domain.rchr.RchrRepresentation
import com.medisense.app.domain.rchr.RchrSymptomFeatures
import com.medisense.app.domain.rchr.RchrPredictionFeatures
import com.medisense.app.domain.rchr.RchrMedicationFeatures
import com.medisense.app.domain.rchr.RchrAdherenceFeatures
import com.medisense.app.domain.rchr.RchrAppointmentFeatures
import com.medisense.app.domain.rchr.RchrContextFeatures
import com.medisense.app.domain.rchr.RchrTemporalFeatures
import com.medisense.app.domain.trace.HealthDecisionTraceEngine
import com.medisense.app.domain.trace.TraceSafetyFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class HealthDecisionTraceUnitTest {

    private lateinit var traceEngine: HealthDecisionTraceEngine
    private lateinit var fakeAuthService: FakeAuthService
    private lateinit var fakePredictionDao: FakePredictionHistoryDao
    private lateinit var fakeHealthProfileDao: FakeHealthProfileDao
    private lateinit var fakeMedicationDao: FakeMedicationDao
    private lateinit var fakeMedicationHistoryDao: FakeMedicationHistoryDao
    private lateinit var fakeAppointmentDao: FakeAppointmentDao
    private lateinit var fakeSyncMetadataDao: FakeSyncMetadataDao
    private lateinit var fakeSecurityAuditDao: FakeSecurityAuditEventDao

    private lateinit var personalHealthContextRepo: PersonalHealthContextRepository
    private lateinit var longitudinalRepo: LongitudinalHealthRepository
    private lateinit var rchrRepo: RchrRepository
    private lateinit var contextualRiskRepo: ContextualRiskRepository
    private lateinit var personalizedGuidanceRepo: PersonalizedGuidanceRepository
    private lateinit var healthDataQualityRepo: HealthDataQualityRepository
    private lateinit var securityAuditRepo: SecurityAuditRepository
    private lateinit var traceRepository: HealthDecisionTraceRepository

    private val testUserId = "user-trace-12345"
    private val otherUserId = "user-trace-99999"

    @Before
    fun setUp() {
        traceEngine = HealthDecisionTraceEngine()
        fakeAuthService = FakeAuthService(userId = testUserId, email = "trace_user@medisense.app")
        fakePredictionDao = FakePredictionHistoryDao()
        fakeHealthProfileDao = FakeHealthProfileDao()
        fakeMedicationDao = FakeMedicationDao()
        fakeMedicationHistoryDao = FakeMedicationHistoryDao()
        fakeAppointmentDao = FakeAppointmentDao()
        fakeSyncMetadataDao = FakeSyncMetadataDao()
        fakeSecurityAuditDao = FakeSecurityAuditEventDao()

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

        val riskEngine = ContextAwareRiskEngine()
        contextualRiskRepo = ContextualRiskRepository(
            personalHealthContextRepository = personalHealthContextRepo,
            longitudinalHealthRepository = longitudinalRepo,
            rchrRepository = rchrRepo,
            riskEngine = riskEngine,
            authService = fakeAuthService
        )

        val guidanceEngine = PersonalizedGuidanceEngine()
        personalizedGuidanceRepo = PersonalizedGuidanceRepository(
            personalHealthContextRepository = personalHealthContextRepo,
            longitudinalHealthRepository = longitudinalRepo,
            rchrRepository = rchrRepo,
            contextualRiskRepository = contextualRiskRepo,
            guidanceEngine = guidanceEngine,
            authService = fakeAuthService
        )

        val qualityEngine = HealthDataQualityEngine(
            profileValidator = ProfileDataValidator(),
            medicationValidator = MedicationDataValidator(),
            appointmentValidator = AppointmentDataValidator(),
            predictionValidator = PredictionDataValidator(),
            temporalValidator = TemporalConsistencyValidator()
        )
        securityAuditRepo = SecurityAuditRepository(
            auditDao = fakeSecurityAuditDao,
            authService = fakeAuthService
        )

        healthDataQualityRepo = HealthDataQualityRepository(
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

        traceRepository = HealthDecisionTraceRepository(
            authService = fakeAuthService,
            predictionHistoryDao = fakePredictionDao,
            personalHealthContextRepository = personalHealthContextRepo,
            longitudinalHealthRepository = longitudinalRepo,
            rchrRepository = rchrRepo,
            contextualRiskRepository = contextualRiskRepo,
            personalizedGuidanceRepository = personalizedGuidanceRepo,
            healthDataQualityRepository = healthDataQualityRepo,
            securityAuditRepository = securityAuditRepo,
            traceEngine = traceEngine
        )
    }

    // 1. Trace Generation for Disease Prediction
    @Test
    fun testBuildPredictionTrace_correctAttributionAndSteps() {
        val entity = PredictionHistoryEntity(
            id = 101L,
            userId = testUserId,
            predictedDisease = "Common Cold",
            confidence = 0.88f,
            symptoms = listOf("chills", "cough", "fatigue", "high_fever"),
            explanationSummary = "Key symptoms including high fever and cough significantly contributed to the model's confidence.",
            predictionTimestamp = 1788750000000L,
            modelVersion = "1.0"
        )

        val trace = traceEngine.buildPredictionTrace(entity)

        assertEquals("trace_pred_101", trace.traceId)
        assertEquals(HealthDecisionTraceType.DISEASE_PREDICTION, trace.decisionType)
        assertTrue(trace.outputResult.primaryOutput.contains("Common Cold", ignoreCase = true))
        assertTrue(trace.outputResult.confidenceOrStatus?.contains("88%") == true)
        assertEquals("LiteRT Disease Predictor & XAI Engine", trace.engineName)
        assertEquals(4, trace.inputFactors.size)
        assertEquals(5, trace.processingSteps.size)
        assertTrue(trace.limitationsText.isNotEmpty())

        val firstFactor = trace.inputFactors[0]
        assertEquals(TraceInfluenceDirection.POSITIVE, firstFactor.influenceDirection)
    }

    // 2. Trace Generation for Personalization Context
    @Test
    fun testBuildPersonalizationTrace_aggregatesContextFactors() {
        val context = PersonalHealthContext(
            userId = testUserId,
            demographics = DemographicContext(age = 35, gender = "Male"),
            chronicConditions = ChronicConditionContext(hasChronicConditions = true, conditions = listOf("Asthma")),
            allergies = AllergyContext(hasAllergies = false, allergiesList = emptyList()),
            medications = MedicationContext(activeCount = 1, activeNames = listOf("Albuterol"), adherencePercentage = 92f, adherenceSummary = "1 active med, 92% adherence"),
            predictions = PredictionContext(totalCount = 3, recentCount = 2, frequentSymptoms = listOf("Shortness of breath")),
            appointments = AppointmentContext(upcomingCount = 1),
            profileCompleteness = 85,
            personalizationScore = 80.0f,
            generatedSummary = "Patient with active asthma on Albuterol.",
            whyPersonalized = "Incorporating active asthma and prescribed Albuterol.",
            hasSufficientData = true
        )

        val trace = traceEngine.buildPersonalizationTrace(context, testUserId)

        assertEquals(HealthDecisionTraceType.PERSONALIZATION, trace.decisionType)
        assertEquals("Adaptive Personal Health Context Engine", trace.engineName)
        assertTrue(trace.inputFactors.any { it.name == "Profile Completeness" })
        assertEquals(3, trace.processingSteps.size)
    }

    // 3. Trace Generation for Longitudinal Dynamics
    @Test
    fun testBuildLongitudinalTrace_capturesTemporalPatterns() {
        val longitudinal = LongitudinalHealthSummary(
            userId = testUserId,
            period = AnalysisPeriod.DAYS_30,
            predictionActivity = PredictionActivityTrend(
                currentPeriodCount = 5,
                previousPeriodCount = 3,
                direction = TrendDirection.STABLE,
                changePercentage = 0f,
                topConditions = listOf("Common Cold"),
                dailyPoints = emptyList()
            ),
            recurringSymptoms = listOf(
                RecurringSymptom(
                    symptomName = "Cough",
                    occurrenceCount = 3,
                    firstObservedDate = 1788600000000L,
                    lastObservedDate = 1788740000000L,
                    isRecurring = true
                )
            ),
            confidenceTrend = ConfidenceTrend(
                currentAvgConfidence = 0.82f,
                previousAvgConfidence = 0.80f,
                direction = TrendDirection.STABLE,
                changePercentage = 2.5f,
                dailyPoints = emptyList()
            ),
            adherenceTrend = AdherenceTrend(
                currentAdherencePercentage = 90f,
                previousAdherencePercentage = 85f,
                direction = TrendDirection.IMPROVING,
                changePercentage = 5.0f,
                totalRecordedEvents = 25,
                takenCount = 23,
                missedCount = 2,
                skippedCount = 0,
                dailyPoints = emptyList()
            ),
            appointmentActivity = AppointmentActivityTrend(
                currentPeriodCount = 1,
                previousPeriodCount = 1,
                upcomingCount = 1,
                direction = TrendDirection.STABLE
            ),
            detectedPatterns = listOf(
                TemporalPattern(
                    id = "tp_1",
                    title = "Frequent Cough Recurrence",
                    description = "Cough logged 3 times in 30 days.",
                    category = PatternCategory.SYMPTOMS,
                    severity = PatternSeverity.ATTENTION
                )
            ),
            generatedSummary = "Cough frequency increased over past 2 weeks.",
            hasSufficientData = true
        )

        val trace = traceEngine.buildLongitudinalTrace(longitudinal, testUserId)

        assertEquals(HealthDecisionTraceType.LONGITUDINAL_PATTERN, trace.decisionType)
        assertEquals("Temporal Pattern Analyzer & Longitudinal Engine", trace.engineName)
        assertTrue(trace.inputFactors.any { it.name.contains("Frequent Cough Recurrence") })
        assertEquals(3, trace.processingSteps.size)
    }

    // 4. Trace Generation for RCHR Matrix
    @Test
    fun testBuildRchrTrace_verifiesHealthMatrixAndReconstruction() {
        val rchr = RchrRepresentation(
            userId = testUserId,
            representationVersion = "1.0",
            generatedAt = 1788750000000L,
            totalEncodedFeatures = 24,
            completenessPercentage = 80,
            profileFeatures = RchrProfileFeatures(
                age = 35,
                ageGroup = "ADULT",
                gender = "Male",
                bloodGroup = "O+",
                heightCm = 175.0,
                weightKg = 70.0,
                bmi = 22.86,
                bmiCategory = "NORMAL",
                allergyCount = 0,
                allergyList = emptyList(),
                chronicConditionCount = 1,
                chronicConditionList = listOf("Asthma"),
                profileCompletenessPercent = 85
            ),
            symptomFeatures = RchrSymptomFeatures(
                distinctSymptomCount = 3,
                allRecordedSymptoms = listOf("cough", "fatigue", "fever"),
                frequentSymptoms = listOf("cough"),
                recurringSymptoms = listOf("cough"),
                recentSymptomCount = 2
            ),
            predictionFeatures = RchrPredictionFeatures(
                totalPredictionCount = 3,
                recentPredictionCount = 2,
                dominantPredictedDisease = "Common Cold",
                topPredictedDiseases = listOf("Common Cold"),
                averageConfidence = 0.88f,
                confidenceRangeMin = 0.85f,
                confidenceRangeMax = 0.90f,
                confidenceTrendDirection = TrendDirection.STABLE
            ),
            medicationFeatures = RchrMedicationFeatures(
                activeMedicationCount = 1,
                activeMedicationNames = listOf("Albuterol"),
                totalPrescribedMedicationCount = 1,
                hasActiveMedications = true
            ),
            adherenceFeatures = RchrAdherenceFeatures(
                recordedDoseCount = 25,
                takenCount = 23,
                missedCount = 2,
                skippedCount = 0,
                adherencePercentage = 92f,
                adherenceCategory = "OPTIMAL",
                adherenceTrendDirection = TrendDirection.IMPROVING
            ),
            appointmentFeatures = RchrAppointmentFeatures(
                upcomingAppointmentCount = 1,
                nextAppointmentDoctor = "Dr. Smith",
                nextAppointmentDate = "2026-09-15",
                nextAppointmentType = "In-Person",
                pastAppointmentCount = 1,
                appointmentTrendDirection = TrendDirection.STABLE
            ),
            temporalFeatures = RchrTemporalFeatures(
                analysisWindowDays = 30,
                predictionActivityTrend = TrendDirection.STABLE,
                predictionChangePercent = 0f,
                confidenceTrend = TrendDirection.STABLE,
                adherenceTrend = TrendDirection.IMPROVING,
                appointmentTrend = TrendDirection.STABLE,
                detectedPatternsCount = 1,
                detectedPatternTitles = listOf("Frequent Cough Recurrence")
            ),
            contextFeatures = RchrContextFeatures(
                personalizationScore = 80f,
                contextCompleteness = 85,
                contextSummary = "Patient with active asthma on Albuterol.",
                whyPersonalized = "Incorporating active asthma."
            ),
            hasSufficientData = true
        )

        val trace = traceEngine.buildRchrTrace(rchr, null, testUserId)

        assertEquals(HealthDecisionTraceType.RCHR_REPRESENTATION, trace.decisionType)
        assertEquals("Reversible Composite Health Representation (RCHR) Engine", trace.engineName)
        assertTrue(trace.inputFactors.any { it.name == "Profile Features" })
    }

    // 5. Trace Generation for Contextual Risk Assessment
    @Test
    fun testBuildContextualRiskTrace_evaluatesClinicalPriority() {
        val risk = ContextualRiskAssessment(
            userId = testUserId,
            overallScore = 45,
            riskLevel = ContextualRiskLevel.MODERATE,
            contributingFactors = listOf(
                ContextualRiskFactor(
                    factorId = "f_1",
                    category = ContextualRiskCategory.SYMPTOM_RECURRENCE,
                    title = "Frequent Cough Episodes",
                    description = "Logged 3 times in 30 days.",
                    rawContributionScore = 50f,
                    weightedContribution = 10f,
                    weight = 0.20f,
                    source = "Longitudinal Trends",
                    isAvailable = true,
                    effectDirection = FactorEffectDirection.INCREASES_SCORE,
                    explanation = "Recurring respiratory symptoms detected."
                )
            ),
            positiveContributors = emptyList(),
            neutralOrMitigatingFactors = emptyList(),
            unavailableFactors = emptyList(),
            generatedSummary = "Moderate risk due to recurring cough.",
            dataAvailabilitySummary = mapOf("Profile" to true),
            hasSufficientData = true,
            calculatedAt = 1788750000000L
        )

        val trace = traceEngine.buildContextualRiskTrace(risk, testUserId)

        assertEquals(HealthDecisionTraceType.CONTEXTUAL_RISK, trace.decisionType)
        assertEquals("Context-Aware Health Risk Engine", trace.engineName)
        assertTrue(trace.inputFactors.any { it.name == "Frequent Cough Episodes" })
    }

    // 6. Trace Generation for Personalized Guidance
    @Test
    fun testBuildPersonalizedGuidanceTrace_evaluatesActionRecommendations() {
        val guidance = GuidanceEngineResult(
            userId = testUserId,
            guidanceList = listOf(
                PersonalizedGuidance(
                    id = "g_1",
                    category = GuidanceCategory.APPOINTMENT_FOLLOW_UP,
                    title = "Schedule Pulmonologist Review",
                    message = "Assess asthma management plan.",
                    explanation = "Recurring respiratory symptoms.",
                    priority = GuidancePriority.HIGH,
                    sources = listOf("Personal Health Context", "Longitudinal Trends"),
                    actionType = GuidanceActionType.NAVIGATE_APPOINTMENTS,
                    actionLabel = "Schedule Visit",
                    safetyPassed = true
                )
            ),
            totalEvaluatedRules = 10,
            generatedTimestamp = 1788750000000L,
            hasSufficientData = true
        )

        val trace = traceEngine.buildPersonalizedGuidanceTrace(guidance, testUserId)

        assertEquals(HealthDecisionTraceType.PERSONALIZED_GUIDANCE, trace.decisionType)
        assertEquals("Personalized Health Guidance Engine", trace.engineName)
        assertTrue(trace.inputFactors.any { it.name == "Schedule Pulmonologist Review" })
        assertEquals(3, trace.processingSteps.size)
    }

    // 7. Trace Generation for Data Quality Assessment
    @Test
    fun testBuildDataQualityTrace_evaluatesQualityScoreAndIssues() {
        val quality = HealthDataQualitySummary(
            status = HealthDataQualityStatus.GOOD,
            qualityScore = 88,
            totalChecks = 10,
            passedChecks = 8,
            errorCount = 0,
            warningCount = 2,
            infoCount = 0,
            issues = listOf(
                HealthDataQualityIssue(
                    id = "q_1",
                    category = HealthDataQualityCategory.MEDICATION_DATA,
                    severity = HealthDataQualitySeverity.WARNING,
                    title = "Missing Medication Dosage",
                    explanation = "Medication 'Aspirin' does not specify dosage.",
                    affectedRecordType = "MedicationEntity",
                    recordId = "101",
                    suggestedCorrection = "Update dosage in medication manager",
                    navigationDestinationId = null
                )
            ),
            categoryBreakdown = mapOf(HealthDataQualityCategory.MEDICATION_DATA to 75),
            evaluatedTimestamp = 1788750000000L
        )

        val trace = traceEngine.buildDataQualityTrace(quality, testUserId)

        assertEquals(HealthDecisionTraceType.DATA_QUALITY_ASSESSMENT, trace.decisionType)
        assertEquals("Health Data Quality & Integrity Engine", trace.engineName)
        assertTrue(trace.inputFactors.any { it.name == "Missing Medication Dosage" })
    }

    // 8. Trace Safety Filter & Non-Diagnostic Compliance
    @Test
    fun testTraceSafetyFilter_appliesNonDiagnosticDisclaimers() {
        val rawText = "The model proves that you have Influenza and confirms diagnosis of flu."
        val sanitized = TraceSafetyFilter.sanitize(rawText)

        assertTrue(sanitized.contains("indicates a potential correlation with"))
        assertTrue(sanitized.contains("estimates likelihood of"))
    }

    // 9. Plain Text Export Formatting
    @Test
    fun testFormatTraceAsPlainText_containsAllSectionsAndDisclaimers() {
        val entity = PredictionHistoryEntity(
            id = 202L,
            userId = testUserId,
            predictedDisease = "Migraine",
            confidence = 0.91f,
            symptoms = listOf("headache", "nausea", "visual_disturbances"),
            explanationSummary = "Headache and visual disturbances strongly contributed.",
            predictionTimestamp = 1788750000000L
        )

        val trace = traceEngine.buildPredictionTrace(entity)
        val plainText = traceEngine.formatTraceAsPlainText(trace)

        assertTrue(plainText.contains("MEDISENSE HEALTH DECISION TRACE"))
        assertTrue(plainText.contains("Trace ID: trace_pred_202"))
        assertTrue(plainText.contains("Disease Prediction & XAI"))
        assertTrue(plainText.contains("3. PROCESSING PIPELINE STEPS"))
        assertTrue(plainText.contains("2. IMPORTANT FACTORS & ATTRIBUTION"))
        assertTrue(plainText.contains("LIMITATIONS & MEDICAL DISCLAIMER"))
    }

    // 10. Repository Integration & Reactive Combine
    @Test
    fun testObserveDecisionTraces_aggregatesAndFilters() = runBlocking {
        fakePredictionDao.insertPredictionHistory(
            PredictionHistoryEntity(
                id = 301L,
                userId = testUserId,
                predictedDisease = "Allergic Rhinitis",
                confidence = 0.85f,
                symptoms = listOf("sneezing", "itchy_eyes"),
                predictionTimestamp = 1788750000000L
            )
        )

        fakePredictionDao.insertPredictionHistory(
            PredictionHistoryEntity(
                id = 999L,
                userId = otherUserId,
                predictedDisease = "Other Disease",
                confidence = 0.99f,
                symptoms = listOf("fever"),
                predictionTimestamp = 1788755000000L
            )
        )

        val allTraces = traceRepository.observeDecisionTraces().first()
        assertTrue(allTraces.isNotEmpty())
        assertTrue(allTraces.all { it.traceId != "trace_pred_999" })
        assertTrue(allTraces.any { it.traceId == "trace_pred_301" })

        val predictionTraces = traceRepository.observeDecisionTraces(HealthDecisionTraceType.DISEASE_PREDICTION).first()
        assertTrue(predictionTraces.isNotEmpty())
        assertTrue(predictionTraces.all { it.decisionType == HealthDecisionTraceType.DISEASE_PREDICTION })
    }

    // 11. Security & AI Audit Trail Logging
    @Test
    fun testSecurityAuditLogging_recordsViewAndExportEvents() = runBlocking {
        val entity = PredictionHistoryEntity(
            id = 401L,
            userId = testUserId,
            predictedDisease = "Bronchitis",
            confidence = 0.87f,
            symptoms = listOf("cough", "fatigue"),
            predictionTimestamp = 1788750000000L
        )
        val trace = traceEngine.buildPredictionTrace(entity)

        traceRepository.recordTraceViewedAudit(trace)

        val eventsAfterView = fakeSecurityAuditDao.getAuditEventsForUser(testUserId)
        assertTrue(eventsAfterView.any { it.eventType == SecurityAuditEventType.DECISION_TRACE_VIEWED.name })

        traceRepository.recordTraceExportedAudit(trace, "PDF")

        val eventsAfterExport = fakeSecurityAuditDao.getAuditEventsForUser(testUserId)
        assertTrue(eventsAfterExport.any { it.eventType == SecurityAuditEventType.DECISION_TRACE_EXPORTED.name })
    }

    // 12. User Isolation
    @Test
    fun testUserIsolation_noCrossUserDecisionTraceLeakage() = runBlocking {
        fakePredictionDao.insertPredictionHistory(
            PredictionHistoryEntity(
                id = 501L,
                userId = otherUserId,
                predictedDisease = "Confidential Condition",
                confidence = 0.95f,
                symptoms = listOf("chest_pain"),
                predictionTimestamp = 1788750000000L
            )
        )

        val userTraces = traceRepository.observeDecisionTraces().first()
        assertFalse(userTraces.any { it.outputResult.primaryOutput.contains("Confidential Condition") })
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
