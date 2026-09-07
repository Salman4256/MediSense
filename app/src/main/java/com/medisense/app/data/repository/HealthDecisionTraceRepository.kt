package com.medisense.app.data.repository

import android.content.Context
import com.medisense.app.data.local.dao.PredictionHistoryDao
import com.medisense.app.data.local.entity.PredictionHistoryEntity
import com.medisense.app.data.remote.supabase.AuthService
import com.medisense.app.domain.model.*
import com.medisense.app.domain.rchr.RchrRepresentation
import com.medisense.app.domain.trace.HealthDecisionTraceEngine
import com.medisense.app.domain.trace.HealthDecisionTracePdfGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository orchestrating human-readable, deterministic Explainable Health Decision Traces (Module 21).
 * Integrates directly with existing ML, Personalization, Longitudinal, RCHR, Risk, Guidance,
 * and Data Quality engines.
 * Guaranteed 100% offline-first and strictly user-isolated.
 */
@Singleton
class HealthDecisionTraceRepository @Inject constructor(
    private val authService: AuthService,
    private val predictionHistoryDao: PredictionHistoryDao,
    private val personalHealthContextRepository: PersonalHealthContextRepository,
    private val longitudinalHealthRepository: LongitudinalHealthRepository,
    private val rchrRepository: RchrRepository,
    private val contextualRiskRepository: ContextualRiskRepository,
    private val personalizedGuidanceRepository: PersonalizedGuidanceRepository,
    private val healthDataQualityRepository: HealthDataQualityRepository,
    private val securityAuditRepository: SecurityAuditRepository,
    private val traceEngine: HealthDecisionTraceEngine
) {

    fun getCurrentUserId(): String {
        return authService.getCurrentUserId() ?: "offline-user"
    }

    /**
     * Reactively aggregates all explainable health decision traces across all MediSense engines.
     * Optionally filters by a specific [HealthDecisionTraceType].
     */
    fun observeDecisionTraces(filterType: HealthDecisionTraceType? = null): Flow<List<HealthDecisionTrace>> {
        val userId = getCurrentUserId()

        val f1 = predictionHistoryDao.observePredictionHistory(userId)
        val f2 = personalHealthContextRepository.observePersonalHealthContext()
        val f3 = longitudinalHealthRepository.observeLongitudinalSummary(AnalysisPeriod.DAYS_30)
        val f4 = rchrRepository.observeRchrRepresentation()
        val f5 = contextualRiskRepository.observeContextualRiskAssessment()
        val f6 = personalizedGuidanceRepository.observePersonalizedGuidance()

        return combine(listOf(f1, f2, f3, f4, f5, f6)) { array: Array<Any?> ->
            @Suppress("UNCHECKED_CAST")
            val predictions = (array[0] as? List<PredictionHistoryEntity>) ?: emptyList()
            val personalContext = array[1] as? PersonalHealthContext
            val longitudinal = array[2] as? LongitudinalHealthSummary
            val rchr = array[3] as? RchrRepresentation
            val risk = array[4] as? ContextualRiskAssessment
            val guidance = array[5] as? GuidanceEngineResult

            val traceList = mutableListOf<HealthDecisionTrace>()

            // 1. Prediction Traces (Module 3, 4, 8)
            predictions.forEach { entity ->
                traceList.add(traceEngine.buildPredictionTrace(entity))
            }

            // 2. Personal Context Trace (Module 9A)
            if (personalContext != null && (personalContext.profileCompleteness > 0 || personalContext.medications.activeCount > 0)) {
                traceList.add(traceEngine.buildPersonalizationTrace(personalContext, userId))
            }

            // 3. Longitudinal Dynamics Trace (Module 9B)
            if (longitudinal != null && (longitudinal.detectedPatterns.isNotEmpty() || longitudinal.recurringSymptoms.isNotEmpty() || longitudinal.hasSufficientData)) {
                traceList.add(traceEngine.buildLongitudinalTrace(longitudinal, userId))
            }

            // 4. RCHR Representation Trace (Module 10)
            if (rchr != null && rchr.totalEncodedFeatures > 0) {
                traceList.add(traceEngine.buildRchrTrace(rchr, null, userId))
            }

            // 5. Contextual Risk Assessment Trace (Module 11)
            if (risk != null && (risk.contributingFactors.isNotEmpty() || risk.overallScore != null)) {
                traceList.add(traceEngine.buildContextualRiskTrace(risk, userId))
            }

            // 6. Personalized Guidance Trace (Module 12)
            if (guidance != null && guidance.guidanceList.isNotEmpty()) {
                traceList.add(traceEngine.buildPersonalizedGuidanceTrace(guidance, userId))
            }

            // Sort deterministically: newest first
            val sortedList = traceList.sortedByDescending { it.generatedAt }

            // Filter if requested
            if (filterType != null) {
                sortedList.filter { it.decisionType == filterType }
            } else {
                sortedList
            }
        }.flowOn(Dispatchers.Default)
    }

    /**
     * Retrieves a single trace for a specific disease prediction entity.
     */
    fun observePredictionTrace(predictionId: Long): Flow<HealthDecisionTrace?> {
        val userId = getCurrentUserId()
        return predictionHistoryDao.observePredictionHistory(userId).map { list ->
            val entity = list.find { it.id == predictionId }
            entity?.let { traceEngine.buildPredictionTrace(it) }
        }.flowOn(Dispatchers.Default)
    }

    /**
     * Deterministically generates a trace for the latest Health Data Quality assessment (Module 18).
     */
    suspend fun getLatestDataQualityTrace(): HealthDecisionTrace? = withContext(Dispatchers.IO) {
        val qualitySummary = healthDataQualityRepository.evaluateDataQuality()
        if (qualitySummary.totalChecks > 0) {
            traceEngine.buildDataQualityTrace(qualitySummary, getCurrentUserId())
        } else {
            null
        }
    }

    /**
     * Formats the given trace into a structured plain-text audit record for clipboard sharing / export.
     */
    fun formatTraceAsPlainText(trace: HealthDecisionTrace): String {
        return traceEngine.formatTraceAsPlainText(trace)
    }

    /**
     * Generates a multi-page PDF audit report document for the trace.
     */
    suspend fun generateTracePdf(context: Context, trace: HealthDecisionTrace): Result<File> = withContext(Dispatchers.IO) {
        try {
            when (val exportResult = HealthDecisionTracePdfGenerator.generatePdf(context, trace)) {
                is HealthReportExportResult.Success -> Result.success(exportResult.file)
                is HealthReportExportResult.Error -> Result.failure(Exception(exportResult.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Records a local security and AI transparency audit log entry when a user inspects a decision trace.
     */
    suspend fun recordTraceViewedAudit(trace: HealthDecisionTrace) = withContext(Dispatchers.IO) {
        securityAuditRepository.recordEvent(
            eventType = SecurityAuditEventType.DECISION_TRACE_VIEWED,
            customDescription = "Viewed decision trace for ${trace.decisionType.displayName} (${trace.traceId})"
        )
    }

    /**
     * Records a local security and AI transparency audit log entry when a trace is exported or shared.
     */
    suspend fun recordTraceExportedAudit(trace: HealthDecisionTrace, exportFormat: String) = withContext(Dispatchers.IO) {
        securityAuditRepository.recordEvent(
            eventType = SecurityAuditEventType.DECISION_TRACE_EXPORTED,
            customDescription = "Exported decision trace ${trace.traceId} in $exportFormat format"
        )
    }
}
