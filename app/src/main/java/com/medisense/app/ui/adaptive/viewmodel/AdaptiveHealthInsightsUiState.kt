package com.medisense.app.ui.adaptive.viewmodel

import com.medisense.app.domain.model.AdaptiveCounterfactualCandidate
import com.medisense.app.domain.model.InterventionResponseRecord
import com.medisense.app.domain.model.PersonalInterventionResponseProfile

/**
 * UI State for Adaptive Health Insights screen (Module 25).
 */
data class AdaptiveHealthInsightsUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val profile: PersonalInterventionResponseProfile? = null,
    val candidates: List<AdaptiveCounterfactualCandidate> = emptyList(),
    val history: List<InterventionResponseRecord> = emptyList(),
    val selectedCandidateForObservation: AdaptiveCounterfactualCandidate? = null,
    val observationSavedMessage: String? = null
)
