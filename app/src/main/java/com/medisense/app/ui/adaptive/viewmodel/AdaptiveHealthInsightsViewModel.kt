package com.medisense.app.ui.adaptive.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medisense.app.data.local.dao.PredictionHistoryDao
import com.medisense.app.data.repository.AdaptiveFeedbackRepository
import com.medisense.app.domain.adaptive.AdaptiveCounterfactualReRankingEngine
import com.medisense.app.domain.model.AdaptiveCounterfactualCandidate
import com.medisense.app.domain.model.InterventionCategory
import com.medisense.app.domain.model.ObservationInput
import com.medisense.app.domain.model.ObservedResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel managing the Adaptive Health Insights and Prediction-Observation Feedback Loop (Module 25).
 */
@HiltViewModel
class AdaptiveHealthInsightsViewModel @Inject constructor(
    private val feedbackRepository: AdaptiveFeedbackRepository,
    private val predictionHistoryDao: PredictionHistoryDao,
    private val reRankingEngine: AdaptiveCounterfactualReRankingEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdaptiveHealthInsightsUiState(isLoading = true))
    val uiState: StateFlow<AdaptiveHealthInsightsUiState> = _uiState.asStateFlow()

    private val defaultRawCandidates = listOf(
        AdaptiveCounterfactualReRankingEngine.RawScenarioCandidate(
            scenarioId = "cf_symptom_relief_01",
            title = "Ablation: Alleviate Primary Fatigue & Headache",
            description = "Simulating targeted relief of reported tension headache and fatigue through rest intervals.",
            category = InterventionCategory.SYMPTOM_MANAGEMENT,
            baselineScore = 0.85f,
            expectedShiftDescription = "Expected model shift: -24% predicted acute discomfort score"
        ),
        AdaptiveCounterfactualReRankingEngine.RawScenarioCandidate(
            scenarioId = "cf_lifestyle_hydration_02",
            title = "Lifestyle: Hydration & Sleep Regularity",
            description = "Increasing consistent fluid intake to 2.5L and maintaining 8-hour sleep schedule.",
            category = InterventionCategory.LIFESTYLE_MODIFICATION,
            baselineScore = 0.78f,
            expectedShiftDescription = "Expected model shift: -18% simulated fatigue & metabolic strain"
        ),
        AdaptiveCounterfactualReRankingEngine.RawScenarioCandidate(
            scenarioId = "cf_medication_adherence_03",
            title = "Medication: Scheduled Adherence Routine",
            description = "Maintaining on-time daily prescription adherence and tracking missed dose logs.",
            category = InterventionCategory.MEDICATION_ADHERENCE,
            baselineScore = 0.72f,
            expectedShiftDescription = "Expected model shift: Stabilizes therapeutic adherence index"
        ),
        AdaptiveCounterfactualReRankingEngine.RawScenarioCandidate(
            scenarioId = "cf_routine_monitoring_04",
            title = "Monitoring: Longitudinal Vital Sign Log",
            description = "Tracking morning resting heart rate and blood pressure thrice weekly.",
            category = InterventionCategory.ROUTINE_MONITORING,
            baselineScore = 0.65f,
            expectedShiftDescription = "Expected model shift: Increases data completeness for predictive precision"
        ),
        AdaptiveCounterfactualReRankingEngine.RawScenarioCandidate(
            scenarioId = "cf_clinical_followup_05",
            title = "Clinical: Primary Care Consultation Review",
            description = "Preparing structured consultation summary for scheduled physician appointment.",
            category = InterventionCategory.CLINICAL_FOLLOWUP,
            baselineScore = 0.60f,
            expectedShiftDescription = "Expected model shift: Validates diagnostic hypotheses with clinician"
        )
    )

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            try {
                combine(
                    feedbackRepository.observeRecords(),
                    feedbackRepository.observeProfile()
                ) { records, profile ->
                    val rankedCandidates = reRankingEngine.reRankCandidates(defaultRawCandidates, profile)
                    Pair(records, profile) to rankedCandidates
                }.collect { (pair, rankedCandidates) ->
                    val (records, profile) = pair
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            history = records,
                            profile = profile,
                            candidates = rankedCandidates,
                            errorMessage = null
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.localizedMessage ?: "Failed to load adaptive insights") }
            }
        }
    }

    fun selectCandidateForObservation(candidate: AdaptiveCounterfactualCandidate?) {
        _uiState.update { it.copy(selectedCandidateForObservation = candidate) }
    }

    fun saveObservation(
        observedResponse: ObservedResponse,
        userNotes: String?
    ) {
        val candidate = _uiState.value.selectedCandidateForObservation ?: return

        viewModelScope.launch {
            try {
                val input = ObservationInput(
                    sourcePredictionId = null,
                    counterfactualId = candidate.scenarioId,
                    category = candidate.category,
                    interventionDescription = candidate.title,
                    baselineStateSummary = candidate.description,
                    expectedResponse = candidate.expectedShiftDescription,
                    observedResponse = observedResponse,
                    expectedValue = candidate.adaptiveScore,
                    observedValue = null,
                    userNotes = userNotes?.trim()?.ifBlank { null }
                )

                feedbackRepository.recordObservation(input)
                _uiState.update {
                    it.copy(
                        selectedCandidateForObservation = null,
                        observationSavedMessage = "Observation recorded successfully!"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to record observation: ${e.message}") }
            }
        }
    }

    fun deleteObservation(id: Long) {
        viewModelScope.launch {
            try {
                feedbackRepository.deleteObservation(id)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to delete record: ${e.message}") }
            }
        }
    }

    fun clearSavedMessage() {
        _uiState.update { it.copy(observationSavedMessage = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
