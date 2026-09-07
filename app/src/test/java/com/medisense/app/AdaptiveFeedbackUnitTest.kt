package com.medisense.app

import com.medisense.app.data.local.dao.InterventionResponseDao
import com.medisense.app.data.local.dao.SecurityAuditEventDao
import com.medisense.app.data.local.entity.InterventionResponseRecordEntity
import com.medisense.app.data.local.entity.SecurityAuditEventEntity
import com.medisense.app.data.remote.supabase.AuthService
import com.medisense.app.data.repository.AdaptiveFeedbackRepository
import com.medisense.app.data.repository.SecurityAuditRepository
import com.medisense.app.domain.adaptive.*
import com.medisense.app.domain.model.*
import io.github.jan.supabase.createSupabaseClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AdaptiveFeedbackUnitTest {

    private lateinit var fakeAuthService: FakeAuthService
    private lateinit var fakeResponseDao: FakeInterventionResponseDao
    private lateinit var fakeSecurityAuditDao: FakeSecurityAuditEventDao
    private lateinit var securityAuditRepository: SecurityAuditRepository

    private lateinit var discrepancyEngine: AdaptiveDiscrepancyEngine
    private lateinit var memoryEngine: PersonalInterventionResponseMemoryEngine
    private lateinit var reRankingEngine: AdaptiveCounterfactualReRankingEngine
    private lateinit var safetyFilter: AdaptiveFeedbackSafetyFilter
    private lateinit var repository: AdaptiveFeedbackRepository

    private val testUserId = "user-adaptive-test-11111"
    private val foreignUserId = "user-foreign-99999"

    @Before
    fun setUp() {
        fakeAuthService = FakeAuthService(userId = testUserId, email = "adaptive_user@medisense.app")
        fakeResponseDao = FakeInterventionResponseDao()
        fakeSecurityAuditDao = FakeSecurityAuditEventDao()

        securityAuditRepository = SecurityAuditRepository(
            auditDao = fakeSecurityAuditDao,
            authService = fakeAuthService
        )

        discrepancyEngine = AdaptiveDiscrepancyEngine()
        memoryEngine = PersonalInterventionResponseMemoryEngine()
        reRankingEngine = AdaptiveCounterfactualReRankingEngine()
        safetyFilter = AdaptiveFeedbackSafetyFilter()

        repository = AdaptiveFeedbackRepository(
            responseDao = fakeResponseDao,
            authService = fakeAuthService,
            discrepancyEngine = discrepancyEngine,
            memoryEngine = memoryEngine,
            reRankingEngine = reRankingEngine,
            safetyFilter = safetyFilter,
            securityAuditRepository = securityAuditRepository
        )
    }

    // -------------------------------------------------------------
    // Test 1: New User Baseline (0 Observations)
    // -------------------------------------------------------------
    @Test
    fun testNewUserBaseline_producesStableBaselineRankAndInsufficientDataConfidence() = runBlocking {
        val profile = memoryEngine.synthesizeProfile(testUserId, emptyList())
        assertEquals(0, profile.totalObservations)
        assertEquals(0, profile.validObservationsCount)
        assertEquals(ResponseConfidenceLevel.INSUFFICIENT_DATA, profile.overallConfidenceLevel)
        assertEquals(InteractionPatternStatus.INSUFFICIENT_DATA, profile.interactionPatternStatus)

        val rawCandidates = listOf(
            AdaptiveCounterfactualReRankingEngine.RawScenarioCandidate(
                scenarioId = "cf_1",
                title = "Scenario 1",
                description = "Desc 1",
                category = InterventionCategory.LIFESTYLE_MODIFICATION,
                baselineScore = 0.80f,
                expectedShiftDescription = "Shift 1"
            ),
            AdaptiveCounterfactualReRankingEngine.RawScenarioCandidate(
                scenarioId = "cf_2",
                title = "Scenario 2",
                description = "Desc 2",
                category = InterventionCategory.MEDICATION_ADHERENCE,
                baselineScore = 0.60f,
                expectedShiftDescription = "Shift 2"
            )
        )

        val ranked = reRankingEngine.reRankCandidates(rawCandidates, profile)
        assertEquals(2, ranked.size)
        assertEquals("cf_1", ranked[0].scenarioId)
        assertEquals(1, ranked[0].rank)
        assertEquals(0.80f, ranked[0].baselineScore, 0.001f)
        assertEquals(0.80f, ranked[0].adaptiveScore, 0.001f)
        assertTrue(ranked[0].rankingRationale.contains("No past observations recorded"))
    }

    // -------------------------------------------------------------
    // Test 2: Single Observation Discrepancy (Aligned)
    // -------------------------------------------------------------
    @Test
    fun testSingleObservationDiscrepancy_improvedCalculatesAligned() {
        val result = discrepancyEngine.calculateDiscrepancy(
            expectedResponse = "Expected symptom reduction and fatigue improvement",
            observedResponse = ObservedResponse.IMPROVED,
            expectedValue = null,
            observedValue = null
        )

        assertEquals(DiscrepancyCategory.ALIGNED, result.discrepancyCategory)
        assertTrue(result.discrepancyValue <= AdaptiveFeedbackConfiguration.DISCREPANCY_ALIGNED_THRESHOLD)
        assertEquals("VALID", result.dataQualityStatus)
    }

    // -------------------------------------------------------------
    // Test 3: Deviation Calculation (Worsened & Unchanged)
    // -------------------------------------------------------------
    @Test
    fun testDeviationCalculation_worsenedAndUnchangedComputeDeviations() {
        // Worsened scenario
        val worsenedResult = discrepancyEngine.calculateDiscrepancy(
            expectedResponse = "Expected improvement",
            observedResponse = ObservedResponse.WORSENED,
            expectedValue = 0.85f,
            observedValue = null
        )
        assertEquals(DiscrepancyCategory.LARGE_DEVIATION, worsenedResult.discrepancyCategory)
        assertTrue(worsenedResult.discrepancyValue > AdaptiveFeedbackConfiguration.DISCREPANCY_MODERATE_THRESHOLD)

        // Unchanged scenario
        val unchangedResult = discrepancyEngine.calculateDiscrepancy(
            expectedResponse = "Expected strong positive improvement",
            observedResponse = ObservedResponse.UNCHANGED,
            expectedValue = 0.85f,
            observedValue = null
        )
        assertTrue(
            unchangedResult.discrepancyCategory == DiscrepancyCategory.SLIGHT_DEVIATION ||
            unchangedResult.discrepancyCategory == DiscrepancyCategory.MODERATE_DEVIATION
        )
    }

    // -------------------------------------------------------------
    // Test 4: Confidence Evolution with Multiple Observations
    // -------------------------------------------------------------
    @Test
    fun testConfidenceEvolution_consistentObservationsElevateConfidence() {
        val records = (1..6).map { i ->
            InterventionResponseRecord(
                id = i.toLong(),
                userId = testUserId,
                sourcePredictionId = null,
                counterfactualId = "cf_lifestyle",
                interventionCategory = InterventionCategory.LIFESTYLE_MODIFICATION,
                interventionDescription = "Hydration Scenario",
                baselineStateSummary = "Baseline",
                expectedResponse = "Improvement",
                observedResponse = ObservedResponse.IMPROVED,
                expectedValue = 0.8f,
                observedValue = 1.0f,
                discrepancyValue = 0.2f,
                discrepancyCategory = DiscrepancyCategory.ALIGNED,
                userNotes = null,
                observationTimestamp = System.currentTimeMillis() - i * 1000,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                confidenceLevel = ResponseConfidenceLevel.HIGH,
                dataQualityStatus = "VALID",
                modelVersion = "1.0",
                algorithmVersion = "1.0"
            )
        }

        val profile = memoryEngine.synthesizeProfile(testUserId, records)
        val lifestyleSummary = profile.categorySummaries[InterventionCategory.LIFESTYLE_MODIFICATION]

        assertNotNull(lifestyleSummary)
        assertEquals(6, lifestyleSummary!!.totalObservations)
        assertEquals(6, lifestyleSummary.alignedCount)
        assertEquals(1.0f, lifestyleSummary.alignmentRatio, 0.001f)
        assertEquals(ResponseConfidenceLevel.HIGH, lifestyleSummary.confidenceLevel)
    }

    // -------------------------------------------------------------
    // Test 5: Adaptive Candidate Re-ranking
    // -------------------------------------------------------------
    @Test
    fun testAdaptiveCandidateReRanking_boostsAlignedCategoryAndPenalizesDeviations() {
        val records = listOf(
            // 3 Aligned medication observations
            InterventionResponseRecord(
                id = 1,
                userId = testUserId,
                sourcePredictionId = null,
                counterfactualId = "cf_med",
                interventionCategory = InterventionCategory.MEDICATION_ADHERENCE,
                interventionDescription = "Med routine",
                baselineStateSummary = "Baseline",
                expectedResponse = "Improvement",
                observedResponse = ObservedResponse.IMPROVED,
                expectedValue = 0.8f,
                observedValue = 1.0f,
                discrepancyValue = 0.1f,
                discrepancyCategory = DiscrepancyCategory.ALIGNED,
                userNotes = null,
                observationTimestamp = System.currentTimeMillis(),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                confidenceLevel = ResponseConfidenceLevel.MODERATE,
                dataQualityStatus = "VALID",
                modelVersion = "1.0",
                algorithmVersion = "1.0"
            ),
            InterventionResponseRecord(
                id = 2,
                userId = testUserId,
                sourcePredictionId = null,
                counterfactualId = "cf_med",
                interventionCategory = InterventionCategory.MEDICATION_ADHERENCE,
                interventionDescription = "Med routine",
                baselineStateSummary = "Baseline",
                expectedResponse = "Improvement",
                observedResponse = ObservedResponse.IMPROVED,
                expectedValue = 0.8f,
                observedValue = 1.0f,
                discrepancyValue = 0.1f,
                discrepancyCategory = DiscrepancyCategory.ALIGNED,
                userNotes = null,
                observationTimestamp = System.currentTimeMillis(),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                confidenceLevel = ResponseConfidenceLevel.MODERATE,
                dataQualityStatus = "VALID",
                modelVersion = "1.0",
                algorithmVersion = "1.0"
            ),
            InterventionResponseRecord(
                id = 3,
                userId = testUserId,
                sourcePredictionId = null,
                counterfactualId = "cf_med",
                interventionCategory = InterventionCategory.MEDICATION_ADHERENCE,
                interventionDescription = "Med routine",
                baselineStateSummary = "Baseline",
                expectedResponse = "Improvement",
                observedResponse = ObservedResponse.IMPROVED,
                expectedValue = 0.8f,
                observedValue = 1.0f,
                discrepancyValue = 0.1f,
                discrepancyCategory = DiscrepancyCategory.ALIGNED,
                userNotes = null,
                observationTimestamp = System.currentTimeMillis(),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                confidenceLevel = ResponseConfidenceLevel.MODERATE,
                dataQualityStatus = "VALID",
                modelVersion = "1.0",
                algorithmVersion = "1.0"
            ),
            // 3 Large deviations in Lifestyle
            InterventionResponseRecord(
                id = 4,
                userId = testUserId,
                sourcePredictionId = null,
                counterfactualId = "cf_life",
                interventionCategory = InterventionCategory.LIFESTYLE_MODIFICATION,
                interventionDescription = "Diet change",
                baselineStateSummary = "Baseline",
                expectedResponse = "Improvement",
                observedResponse = ObservedResponse.WORSENED,
                expectedValue = 0.8f,
                observedValue = -0.5f,
                discrepancyValue = 1.3f,
                discrepancyCategory = DiscrepancyCategory.LARGE_DEVIATION,
                userNotes = null,
                observationTimestamp = System.currentTimeMillis(),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                confidenceLevel = ResponseConfidenceLevel.MODERATE,
                dataQualityStatus = "VALID",
                modelVersion = "1.0",
                algorithmVersion = "1.0"
            ),
            InterventionResponseRecord(
                id = 5,
                userId = testUserId,
                sourcePredictionId = null,
                counterfactualId = "cf_life",
                interventionCategory = InterventionCategory.LIFESTYLE_MODIFICATION,
                interventionDescription = "Diet change",
                baselineStateSummary = "Baseline",
                expectedResponse = "Improvement",
                observedResponse = ObservedResponse.WORSENED,
                expectedValue = 0.8f,
                observedValue = -0.5f,
                discrepancyValue = 1.3f,
                discrepancyCategory = DiscrepancyCategory.LARGE_DEVIATION,
                userNotes = null,
                observationTimestamp = System.currentTimeMillis(),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                confidenceLevel = ResponseConfidenceLevel.MODERATE,
                dataQualityStatus = "VALID",
                modelVersion = "1.0",
                algorithmVersion = "1.0"
            ),
            InterventionResponseRecord(
                id = 6,
                userId = testUserId,
                sourcePredictionId = null,
                counterfactualId = "cf_life",
                interventionCategory = InterventionCategory.LIFESTYLE_MODIFICATION,
                interventionDescription = "Diet change",
                baselineStateSummary = "Baseline",
                expectedResponse = "Improvement",
                observedResponse = ObservedResponse.WORSENED,
                expectedValue = 0.8f,
                observedValue = -0.5f,
                discrepancyValue = 1.3f,
                discrepancyCategory = DiscrepancyCategory.LARGE_DEVIATION,
                userNotes = null,
                observationTimestamp = System.currentTimeMillis(),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                confidenceLevel = ResponseConfidenceLevel.MODERATE,
                dataQualityStatus = "VALID",
                modelVersion = "1.0",
                algorithmVersion = "1.0"
            )
        )

        val profile = memoryEngine.synthesizeProfile(testUserId, records)

        // Raw candidates with equal baseline scores
        val rawCandidates = listOf(
            AdaptiveCounterfactualReRankingEngine.RawScenarioCandidate(
                scenarioId = "cand_life",
                title = "Lifestyle Diet",
                description = "Diet adjustments",
                category = InterventionCategory.LIFESTYLE_MODIFICATION,
                baselineScore = 0.70f,
                expectedShiftDescription = "Shift 1"
            ),
            AdaptiveCounterfactualReRankingEngine.RawScenarioCandidate(
                scenarioId = "cand_med",
                title = "Med Adherence",
                description = "Prescription adherence",
                category = InterventionCategory.MEDICATION_ADHERENCE,
                baselineScore = 0.70f,
                expectedShiftDescription = "Shift 2"
            )
        )

        val ranked = reRankingEngine.reRankCandidates(rawCandidates, profile)

        // Medication candidate should be rank #1 with boost
        assertEquals("cand_med", ranked[0].scenarioId)
        assertTrue(ranked[0].adaptiveScore > 0.70f)
        assertTrue(ranked[0].rankingRationale.contains("Rank boosted"))

        // Lifestyle candidate should be rank #2 with penalty
        assertEquals("cand_life", ranked[1].scenarioId)
        assertTrue(ranked[1].adaptiveScore < 0.70f)
        assertTrue(ranked[1].rankingRationale.contains("Rank adjusted"))
    }

    // -------------------------------------------------------------
    // Test 6: Data Quality Gate (Invalid/Not-observed Excluded)
    // -------------------------------------------------------------
    @Test
    fun testDataQualityGate_invalidRecordsExcludedFromProfileCalculations() {
        val records = listOf(
            InterventionResponseRecord(
                id = 1,
                userId = testUserId,
                sourcePredictionId = null,
                counterfactualId = "cf_1",
                interventionCategory = InterventionCategory.SYMPTOM_MANAGEMENT,
                interventionDescription = "Valid observation",
                baselineStateSummary = "Baseline",
                expectedResponse = "Improvement",
                observedResponse = ObservedResponse.IMPROVED,
                expectedValue = 0.8f,
                observedValue = 1.0f,
                discrepancyValue = 0.1f,
                discrepancyCategory = DiscrepancyCategory.ALIGNED,
                userNotes = null,
                observationTimestamp = System.currentTimeMillis(),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                confidenceLevel = ResponseConfidenceLevel.LOW,
                dataQualityStatus = "VALID",
                modelVersion = "1.0",
                algorithmVersion = "1.0"
            ),
            InterventionResponseRecord(
                id = 2,
                userId = testUserId,
                sourcePredictionId = null,
                counterfactualId = "cf_2",
                interventionCategory = InterventionCategory.SYMPTOM_MANAGEMENT,
                interventionDescription = "Invalid corrupted observation",
                baselineStateSummary = "Baseline",
                expectedResponse = "Improvement",
                observedResponse = ObservedResponse.IMPROVED,
                expectedValue = 0.8f,
                observedValue = 1.0f,
                discrepancyValue = 0.1f,
                discrepancyCategory = DiscrepancyCategory.ALIGNED,
                userNotes = null,
                observationTimestamp = System.currentTimeMillis(),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                confidenceLevel = ResponseConfidenceLevel.LOW,
                dataQualityStatus = "INVALID",
                modelVersion = "1.0",
                algorithmVersion = "1.0"
            ),
            InterventionResponseRecord(
                id = 3,
                userId = testUserId,
                sourcePredictionId = null,
                counterfactualId = "cf_3",
                interventionCategory = InterventionCategory.SYMPTOM_MANAGEMENT,
                interventionDescription = "Skipped observation",
                baselineStateSummary = "Baseline",
                expectedResponse = "Improvement",
                observedResponse = ObservedResponse.NOT_OBSERVED,
                expectedValue = 0.8f,
                observedValue = 0.0f,
                discrepancyValue = 0.0f,
                discrepancyCategory = DiscrepancyCategory.INSUFFICIENT_OBSERVATION,
                userNotes = null,
                observationTimestamp = System.currentTimeMillis(),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                confidenceLevel = ResponseConfidenceLevel.INSUFFICIENT_DATA,
                dataQualityStatus = "INSUFFICIENT_DATA",
                modelVersion = "1.0",
                algorithmVersion = "1.0"
            )
        )

        val profile = memoryEngine.synthesizeProfile(testUserId, records)
        assertEquals(3, profile.totalObservations)
        assertEquals(1, profile.validObservationsCount)
        assertEquals(1, profile.categorySummaries[InterventionCategory.SYMPTOM_MANAGEMENT]!!.totalObservations)
    }

    // -------------------------------------------------------------
    // Test 7: User Identity Isolation
    // -------------------------------------------------------------
    @Test
    fun testUserIdentityIsolation_foreignRecordsDoNotAffectCurrentProfile() = runBlocking {
        fakeResponseDao.insertRecord(
            InterventionResponseRecordEntity(
                userId = foreignUserId,
                interventionCategory = InterventionCategory.MEDICATION_ADHERENCE.name,
                interventionDescription = "Foreign user adherence",
                baselineStateSummary = "Foreign baseline",
                expectedResponse = "Improvement",
                observedResponse = ObservedResponse.IMPROVED.name,
                discrepancyCategory = DiscrepancyCategory.ALIGNED.name
            )
        )

        val profile = repository.observeProfile().first()
        assertEquals(0, profile.totalObservations)
        assertEquals(0, profile.validObservationsCount)
    }

    // -------------------------------------------------------------
    // Test 8: Deterministic Ranking Guarantee
    // -------------------------------------------------------------
    @Test
    fun testDeterministicRankingGuarantee_identicalInputsProduceExactOutputs() {
        val records = listOf(
            InterventionResponseRecord(
                id = 1,
                userId = testUserId,
                sourcePredictionId = null,
                counterfactualId = "cf_1",
                interventionCategory = InterventionCategory.ROUTINE_MONITORING,
                interventionDescription = "Routine vitals",
                baselineStateSummary = "Baseline",
                expectedResponse = "Improvement",
                observedResponse = ObservedResponse.IMPROVED,
                expectedValue = 0.8f,
                observedValue = 1.0f,
                discrepancyValue = 0.1f,
                discrepancyCategory = DiscrepancyCategory.ALIGNED,
                userNotes = null,
                observationTimestamp = 1000L,
                createdAt = 1000L,
                updatedAt = 1000L,
                confidenceLevel = ResponseConfidenceLevel.LOW,
                dataQualityStatus = "VALID",
                modelVersion = "1.0",
                algorithmVersion = "1.0"
            )
        )

        val rawCandidates = listOf(
            AdaptiveCounterfactualReRankingEngine.RawScenarioCandidate(
                scenarioId = "cf_b",
                title = "Scenario B",
                description = "Desc B",
                category = InterventionCategory.ROUTINE_MONITORING,
                baselineScore = 0.60f,
                expectedShiftDescription = "Shift B"
            ),
            AdaptiveCounterfactualReRankingEngine.RawScenarioCandidate(
                scenarioId = "cf_a",
                title = "Scenario A",
                description = "Desc A",
                category = InterventionCategory.ROUTINE_MONITORING,
                baselineScore = 0.60f,
                expectedShiftDescription = "Shift A"
            )
        )

        val profile1 = memoryEngine.synthesizeProfile(testUserId, records)
        val ranked1 = reRankingEngine.reRankCandidates(rawCandidates, profile1)

        val profile2 = memoryEngine.synthesizeProfile(testUserId, records)
        val ranked2 = reRankingEngine.reRankCandidates(rawCandidates, profile2)

        assertEquals(ranked1.size, ranked2.size)
        for (i in ranked1.indices) {
            assertEquals(ranked1[i].scenarioId, ranked2[i].scenarioId)
            assertEquals(ranked1[i].adaptiveScore, ranked2[i].adaptiveScore, 0.0001f)
            assertEquals(ranked1[i].rank, ranked2[i].rank)
            assertEquals(ranked1[i].rankingRationale, ranked2[i].rankingRationale)
        }
    }

    // -------------------------------------------------------------
    // Test 9: Safety Filter
    // -------------------------------------------------------------
    @Test
    fun testSafetyFilter_sanitizesProhibitedClinicalClaims() {
        val unsafeText = "This scenario is guaranteed to heal and cure your tension headache. Stop taking medication now."
        assertTrue(safetyFilter.containsProhibitedClaims(unsafeText))

        val sanitized = safetyFilter.sanitizeExplanation(unsafeText)
        assertFalse(sanitized.contains("cure", ignoreCase = true))
        assertFalse(sanitized.contains("guaranteed to heal", ignoreCase = true))
        assertFalse(sanitized.contains("stop taking medication", ignoreCase = true))

        val safeText = "Regular sleep intervals may support daily comfort and energy."
        assertFalse(safetyFilter.containsProhibitedClaims(safeText))
    }

    // -------------------------------------------------------------
    // Test 10: Full End-to-End Repository Lifecycle
    // -------------------------------------------------------------
    @Test
    fun testRepositoryLifecycle_recordsObservesUpdatesDeletesAndEvaluates() = runBlocking {
        // 1. Record an observation
        val input = ObservationInput(
            sourcePredictionId = 101L,
            counterfactualId = "cf_hydration_test",
            category = InterventionCategory.LIFESTYLE_MODIFICATION,
            interventionDescription = "Test Hydration",
            baselineStateSummary = "Headache baseline",
            expectedResponse = "Improvement",
            observedResponse = ObservedResponse.IMPROVED,
            expectedValue = 0.8f,
            observedValue = null,
            userNotes = "Private test note"
        )

        val rowId = repository.recordObservation(input)
        assertTrue(rowId > 0)

        // 2. Observe records
        val records = repository.observeRecords().first()
        assertEquals(1, records.size)
        assertEquals(InterventionCategory.LIFESTYLE_MODIFICATION, records[0].interventionCategory)
        assertEquals(DiscrepancyCategory.ALIGNED, records[0].discrepancyCategory)
        assertEquals("Private test note", records[0].userNotes)

        // 3. Update observation
        val updateInput = input.copy(
            observedResponse = ObservedResponse.PARTIALLY_IMPROVED,
            userNotes = "Updated note"
        )
        repository.updateObservation(rowId, updateInput)

        val updatedRecords = repository.observeRecords().first()
        assertEquals(1, updatedRecords.size)
        assertEquals(ObservedResponse.PARTIALLY_IMPROVED, updatedRecords[0].observedResponse)
        assertEquals("Updated note", updatedRecords[0].userNotes)

        // 4. Verify consultation & health report summaries
        val consultationSummary = repository.getFeedbackSummaryForConsultation().first()
        assertTrue(consultationSummary.contains("Total Observations Logged: 1"))

        val reportSummary = repository.getFeedbackSummaryForHealthReport().first()
        assertTrue(reportSummary.contains("Lifestyle Modification"))

        // 5. Delete observation
        repository.deleteObservation(rowId)
        val remaining = repository.observeRecords().first()
        assertTrue(remaining.isEmpty())
    }

    // -----------------------------------------------------------------
    // Fake DAO & Auth implementations for clean, fast unit testing
    // -----------------------------------------------------------------

    private class FakeAuthService(
        private var userId: String?,
        private var email: String?
    ) : AuthService(
        createSupabaseClient(
            supabaseUrl = "https://placeholder.supabase.co",
            supabaseKey = "dummy-anon-key"
        ) {},
        com.medisense.app.data.local.session.SharedPreferencesSessionManager(null)
    ) {
        override fun getCurrentUserId(): String? = userId
        override fun getCurrentUserEmail(): String? = email
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

class FakeInterventionResponseDao : InterventionResponseDao {
    private val records = mutableListOf<InterventionResponseRecordEntity>()
    private var nextId = 1L

    override fun observeRecordsForUser(userId: String): Flow<List<InterventionResponseRecordEntity>> {
        return flowOf(records.filter { it.userId == userId }.sortedByDescending { it.observationTimestamp })
    }

    override suspend fun getRecordsForUserSync(userId: String): List<InterventionResponseRecordEntity> {
        return records.filter { it.userId == userId }.sortedByDescending { it.observationTimestamp }
    }

    override suspend fun getRecordsByPrediction(
        predictionId: Long,
        userId: String
    ): List<InterventionResponseRecordEntity> {
        return records.filter { it.userId == userId && it.sourcePredictionId == predictionId }
    }

    override suspend fun getRecordsByCategory(
        category: String,
        userId: String
    ): List<InterventionResponseRecordEntity> {
        return records.filter { it.userId == userId && it.interventionCategory == category }
    }

    override suspend fun getRecordById(id: Long, userId: String): InterventionResponseRecordEntity? {
        return records.firstOrNull { it.id == id && it.userId == userId }
    }

    override suspend fun getPendingSyncRecords(userId: String): List<InterventionResponseRecordEntity> {
        return records.filter { it.userId == userId && it.pendingSync }
    }

    override suspend fun insertRecord(entity: InterventionResponseRecordEntity): Long {
        val id = if (entity.id == 0L) nextId++ else entity.id
        val newEntity = entity.copy(id = id)
        records.removeAll { it.id == id }
        records.add(newEntity)
        return id
    }

    override suspend fun insertRecords(entities: List<InterventionResponseRecordEntity>): List<Long> {
        return entities.map { insertRecord(it) }
    }

    override suspend fun updateRecord(entity: InterventionResponseRecordEntity) {
        records.removeAll { it.id == entity.id }
        records.add(entity)
    }

    override suspend fun deleteRecordById(id: Long, userId: String) {
        records.removeAll { it.id == id && it.userId == userId }
    }

    override suspend fun deleteAllRecordsForUser(userId: String) {
        records.removeAll { it.userId == userId }
    }
}
