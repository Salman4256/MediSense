package com.medisense.app.data.repository

import com.medisense.app.data.remote.supabase.AuthService
import com.medisense.app.domain.model.AnalysisPeriod
import com.medisense.app.domain.model.ContextualRiskAssessment
import com.medisense.app.domain.risk.ContextAwareRiskEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository providing reactive, offline-first Context-Aware Health Risk Assessments.
 * Aggregates information across Modules 1–10 using local Room tables.
 */
@Singleton
class ContextualRiskRepository @Inject constructor(
    private val personalHealthContextRepository: PersonalHealthContextRepository,
    private val longitudinalHealthRepository: LongitudinalHealthRepository,
    private val rchrRepository: RchrRepository,
    private val riskEngine: ContextAwareRiskEngine,
    private val authService: AuthService
) {

    fun getCurrentUserId(): String {
        return authService.getCurrentUserId() ?: "offline-user"
    }

    /**
     * Reactively observes and calculates the user's Contextual Health Risk Assessment.
     * Evaluates deterministically on local Room flow emissions without any network dependency.
     */
    fun observeContextualRiskAssessment(): Flow<ContextualRiskAssessment> {
        val userId = getCurrentUserId()

        return combine(
            personalHealthContextRepository.observePersonalHealthContext(),
            longitudinalHealthRepository.observeLongitudinalSummary(AnalysisPeriod.DAYS_30),
            rchrRepository.observeRchrRepresentation()
        ) { personalContext, longitudinalSummary, rchr ->
            riskEngine.evaluate(
                userId = userId,
                personalContext = personalContext,
                longitudinalSummary = longitudinalSummary,
                rchr = rchr
            )
        }.flowOn(Dispatchers.Default)
    }
}
