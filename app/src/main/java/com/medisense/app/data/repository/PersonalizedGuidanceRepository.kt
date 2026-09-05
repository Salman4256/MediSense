package com.medisense.app.data.repository

import com.medisense.app.data.remote.supabase.AuthService
import com.medisense.app.domain.guidance.PersonalizedGuidanceEngine
import com.medisense.app.domain.model.AnalysisPeriod
import com.medisense.app.domain.model.GuidanceEngineResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository orchestrating personalized health-management guidance.
 * Reactively aggregates health context from Modules 9A, 9B, 10, and 11 offline.
 */
@Singleton
class PersonalizedGuidanceRepository @Inject constructor(
    private val personalHealthContextRepository: PersonalHealthContextRepository,
    private val longitudinalHealthRepository: LongitudinalHealthRepository,
    private val rchrRepository: RchrRepository,
    private val contextualRiskRepository: ContextualRiskRepository,
    private val guidanceEngine: PersonalizedGuidanceEngine,
    private val authService: AuthService
) {

    fun getCurrentUserId(): String {
        return authService.getCurrentUserId() ?: "offline-user"
    }

    /**
     * Reactively observes and calculates personalized guidance recommendations.
     * Re-evaluates deterministically whenever underlying Room entities change.
     */
    fun observePersonalizedGuidance(): Flow<GuidanceEngineResult> {
        val userId = getCurrentUserId()

        return combine(
            personalHealthContextRepository.observePersonalHealthContext(),
            longitudinalHealthRepository.observeLongitudinalSummary(AnalysisPeriod.DAYS_30),
            rchrRepository.observeRchrRepresentation(),
            contextualRiskRepository.observeContextualRiskAssessment()
        ) { personalContext, longitudinalSummary, rchr, riskAssessment ->
            guidanceEngine.evaluate(
                userId = userId,
                personalContext = personalContext,
                longitudinalSummary = longitudinalSummary,
                rchr = rchr,
                riskAssessment = riskAssessment
            )
        }.flowOn(Dispatchers.Default)
    }
}
