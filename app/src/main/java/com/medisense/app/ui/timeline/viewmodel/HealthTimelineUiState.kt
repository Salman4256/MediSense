package com.medisense.app.ui.timeline.viewmodel

import com.medisense.app.domain.model.HealthTimelineCategoryFilter
import com.medisense.app.domain.model.HealthTimelineEvent
import com.medisense.app.domain.model.HealthTimelineFilter
import com.medisense.app.domain.model.HealthTimelineSummary

sealed class HealthTimelineUiState {
    object Loading : HealthTimelineUiState()

    data class Content(
        val events: List<HealthTimelineEvent>,
        val summary: HealthTimelineSummary,
        val filter: HealthTimelineFilter,
        val availableCategories: List<HealthTimelineCategoryFilter>
    ) : HealthTimelineUiState()

    data class Empty(
        val filter: HealthTimelineFilter,
        val message: String = "No health events found for the selected filter criteria."
    ) : HealthTimelineUiState()

    data class Error(
        val message: String
    ) : HealthTimelineUiState()
}
