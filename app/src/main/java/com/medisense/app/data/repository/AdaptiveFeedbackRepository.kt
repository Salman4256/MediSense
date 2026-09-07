package com.medisense.app.data.repository

import com.medisense.app.data.local.dao.InterventionResponseDao
import com.medisense.app.data.local.entity.InterventionResponseRecordEntity
import com.medisense.app.data.remote.supabase.AuthService
import com.medisense.app.domain.adaptive.*
import com.medisense.app.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository orchestrating adaptive counterfactual feedback, discrepancy analysis,
 * and user-isolated observation recording (Module 25).
 */
@Singleton
class AdaptiveFeedbackRepository @Inject constructor(
    private val responseDao: InterventionResponseDao,
    private val authService: AuthService,
    private val discrepancyEngine: AdaptiveDiscrepancyEngine,
    private val memoryEngine: PersonalInterventionResponseMemoryEngine,
    private val reRankingEngine: AdaptiveCounterfactualReRankingEngine,
    private val safetyFilter: AdaptiveFeedbackSafetyFilter,
    private val securityAuditRepository: SecurityAuditRepository
) {

    fun getCurrentUserId(): String {
        return authService.getCurrentUserId() ?: "offline-user"
    }

    /**
     * Reactively observes all intervention response records for the active user.
     */
    fun observeRecords(): Flow<List<InterventionResponseRecord>> {
        val userId = getCurrentUserId()
        return responseDao.observeRecordsForUser(userId)
            .map { list -> list.map { it.toDomainModel() } }
            .flowOn(Dispatchers.IO)
    }

    /**
     * Reactively observes synthesized Personal Intervention Response Profile (PIRP).
     */
    fun observeProfile(): Flow<PersonalInterventionResponseProfile> {
        val userId = getCurrentUserId()
        return responseDao.observeRecordsForUser(userId)
            .map { list ->
                val domainRecords = list.map { it.toDomainModel() }
                memoryEngine.synthesizeProfile(userId, domainRecords)
            }
            .flowOn(Dispatchers.IO)
    }

    /**
     * Records a new user observation on an intervention scenario.
     */
    suspend fun recordObservation(input: ObservationInput): Long = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()

        val sanitizedNotes = input.userNotes?.let { safetyFilter.sanitizeExplanation(it) }

        val discrepancy = discrepancyEngine.calculateDiscrepancy(
            expectedResponse = input.expectedResponse,
            observedResponse = input.observedResponse,
            expectedValue = input.expectedValue,
            observedValue = input.observedValue
        )

        val existingRecords = responseDao.getRecordsForUserSync(userId).map { it.toDomainModel() }
        val tempRecords = existingRecords + listOf(
            InterventionResponseRecord(
                id = 0,
                userId = userId,
                sourcePredictionId = input.sourcePredictionId,
                counterfactualId = input.counterfactualId,
                interventionCategory = input.category,
                interventionDescription = input.interventionDescription,
                baselineStateSummary = input.baselineStateSummary,
                expectedResponse = input.expectedResponse,
                observedResponse = input.observedResponse,
                expectedValue = discrepancy.calculatedExpectedValue,
                observedValue = discrepancy.calculatedObservedValue,
                discrepancyValue = discrepancy.discrepancyValue,
                discrepancyCategory = discrepancy.discrepancyCategory,
                userNotes = sanitizedNotes,
                observationTimestamp = input.observationTimestamp,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                confidenceLevel = ResponseConfidenceLevel.INSUFFICIENT_DATA,
                dataQualityStatus = discrepancy.dataQualityStatus,
                modelVersion = "1.0",
                algorithmVersion = "1.0"
            )
        )

        val updatedProfile = memoryEngine.synthesizeProfile(userId, tempRecords)
        val categoryConfidence = updatedProfile.categorySummaries[input.category]?.confidenceLevel
            ?: ResponseConfidenceLevel.INSUFFICIENT_DATA

        val entity = InterventionResponseRecordEntity(
            userId = userId,
            sourcePredictionId = input.sourcePredictionId,
            counterfactualId = input.counterfactualId,
            interventionCategory = input.category.name,
            interventionDescription = input.interventionDescription,
            baselineStateSummary = input.baselineStateSummary,
            expectedResponse = input.expectedResponse,
            observedResponse = input.observedResponse.name,
            expectedValue = discrepancy.calculatedExpectedValue,
            observedValue = discrepancy.calculatedObservedValue,
            discrepancyValue = discrepancy.discrepancyValue,
            discrepancyCategory = discrepancy.discrepancyCategory.name,
            userNotes = sanitizedNotes,
            observationTimestamp = input.observationTimestamp,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            confidenceLevel = categoryConfidence.name,
            dataQualityStatus = discrepancy.dataQualityStatus,
            modelVersion = "1.0",
            algorithmVersion = "1.0",
            pendingSync = true
        )

        val rowId = responseDao.insertRecord(entity)
        securityAuditRepository.recordEvent(
            SecurityAuditEventType.INTERVENTION_OBSERVATION_RECORDED,
            "Recorded user observation for category ${input.category.displayName}"
        )
        rowId
    }

    /**
     * Updates an existing observation record.
     */
    suspend fun updateObservation(id: Long, input: ObservationInput): Unit = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        val existing = responseDao.getRecordById(id, userId) ?: return@withContext

        val sanitizedNotes = input.userNotes?.let { safetyFilter.sanitizeExplanation(it) }

        val discrepancy = discrepancyEngine.calculateDiscrepancy(
            expectedResponse = input.expectedResponse,
            observedResponse = input.observedResponse,
            expectedValue = input.expectedValue,
            observedValue = input.observedValue
        )

        val updatedEntity = existing.copy(
            interventionCategory = input.category.name,
            interventionDescription = input.interventionDescription,
            baselineStateSummary = input.baselineStateSummary,
            expectedResponse = input.expectedResponse,
            observedResponse = input.observedResponse.name,
            expectedValue = discrepancy.calculatedExpectedValue,
            observedValue = discrepancy.calculatedObservedValue,
            discrepancyValue = discrepancy.discrepancyValue,
            discrepancyCategory = discrepancy.discrepancyCategory.name,
            userNotes = sanitizedNotes,
            observationTimestamp = input.observationTimestamp,
            updatedAt = System.currentTimeMillis(),
            dataQualityStatus = discrepancy.dataQualityStatus,
            pendingSync = true
        )

        responseDao.updateRecord(updatedEntity)
        securityAuditRepository.recordEvent(
            SecurityAuditEventType.INTERVENTION_OBSERVATION_UPDATED,
            "Updated observation record #$id"
        )
    }

    /**
     * Deletes a specific observation record.
     */
    suspend fun deleteObservation(id: Long): Unit = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        responseDao.deleteRecordById(id, userId)
        securityAuditRepository.recordEvent(
            SecurityAuditEventType.INTERVENTION_OBSERVATION_DELETED,
            "Deleted observation record #$id"
        )
    }

    /**
     * Evaluates raw scenario candidates against active user's response profile.
     */
    fun evaluateAdaptiveCandidates(
        rawScenarios: List<AdaptiveCounterfactualReRankingEngine.RawScenarioCandidate>
    ): Flow<AdaptiveFeedbackEngineResult> {
        val userId = getCurrentUserId()
        return responseDao.observeRecordsForUser(userId)
            .map { list ->
                val domainRecords = list.map { it.toDomainModel() }
                val profile = memoryEngine.synthesizeProfile(userId, domainRecords)
                val rankedCandidates = reRankingEngine.reRankCandidates(rawScenarios, profile)

                securityAuditRepository.recordEvent(
                    SecurityAuditEventType.ADAPTIVE_FEEDBACK_EVALUATED,
                    "Evaluated ${rankedCandidates.size} adaptive counterfactual scenarios"
                )

                AdaptiveFeedbackEngineResult(
                    candidates = rankedCandidates,
                    profile = profile,
                    recentRecords = domainRecords.take(10)
                )
            }
            .flowOn(Dispatchers.IO)
    }

    /**
     * Formats an executive summary of user feedback and adaptive response patterns for doctor consultation.
     */
    fun getFeedbackSummaryForConsultation(): Flow<String> {
        return observeProfile().map { profile ->
            if (profile.totalObservations == 0) {
                "No previous self-recorded intervention feedback logged by user."
            } else {
                val aligned = profile.categorySummaries.values.sumOf { it.alignedCount }
                val deviations = profile.categorySummaries.values.sumOf { it.largeDeviationCount + it.moderateDeviationCount }
                "Intervention Response Feedback Summary:\n" +
                "• Total Observations Logged: ${profile.validObservationsCount}\n" +
                "• Trend Alignment: $aligned aligned scenario(s), $deviations notable deviation(s)\n" +
                "• Interaction Status: ${profile.patternDescription}\n" +
                "• Overall Feedback Confidence: ${profile.overallConfidenceLevel.displayName}"
            }
        }.flowOn(Dispatchers.IO)
    }

    /**
     * Formats feedback observations for comprehensive health reports.
     */
    fun getFeedbackSummaryForHealthReport(): Flow<String> {
        return observeProfile().map { profile ->
            if (profile.totalObservations == 0) {
                "Adaptive Feedback Loop: No user-recorded scenario observations recorded to date."
            } else {
                val sb = StringBuilder()
                sb.append("Adaptive Scenario Feedback & Trend Tracking (Module 25):\n")
                sb.append("Total observations: ${profile.validObservationsCount} (Confidence: ${profile.overallConfidenceLevel.displayName})\n")
                profile.categorySummaries.filter { it.value.totalObservations > 0 }.forEach { (cat, sum) ->
                    sb.append("- ${cat.displayName}: ${sum.totalObservations} observation(s), ${(sum.alignmentRatio * 100).toInt()}% alignment\n")
                }
                sb.append("Pattern Note: ${profile.patternDescription}")
                sb.toString()
            }
        }.flowOn(Dispatchers.IO)
    }

    private fun InterventionResponseRecordEntity.toDomainModel(): InterventionResponseRecord {
        return InterventionResponseRecord(
            id = id,
            userId = userId,
            sourcePredictionId = sourcePredictionId,
            counterfactualId = counterfactualId,
            interventionCategory = InterventionCategory.fromString(interventionCategory),
            interventionDescription = interventionDescription,
            baselineStateSummary = baselineStateSummary,
            expectedResponse = expectedResponse,
            observedResponse = ObservedResponse.fromString(observedResponse),
            expectedValue = expectedValue,
            observedValue = observedValue,
            discrepancyValue = discrepancyValue,
            discrepancyCategory = DiscrepancyCategory.fromString(discrepancyCategory),
            userNotes = userNotes,
            observationTimestamp = observationTimestamp,
            createdAt = createdAt,
            updatedAt = updatedAt,
            confidenceLevel = ResponseConfidenceLevel.fromString(confidenceLevel),
            dataQualityStatus = dataQualityStatus,
            modelVersion = modelVersion,
            algorithmVersion = algorithmVersion
        )
    }
}
