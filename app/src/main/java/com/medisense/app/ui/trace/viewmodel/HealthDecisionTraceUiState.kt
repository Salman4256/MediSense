package com.medisense.app.ui.trace.viewmodel

import com.medisense.app.domain.model.HealthDecisionTrace
import com.medisense.app.domain.model.HealthDecisionTraceType
import java.io.File

/**
 * UI state for Module 21 Explainable Health Decision Traces & AI Audit Trail.
 */
sealed interface HealthDecisionTraceUiState {
    object Loading : HealthDecisionTraceUiState

    data class Success(
        val traces: List<HealthDecisionTrace>,
        val activeFilter: HealthDecisionTraceType? = null,
        val totalCount: Int = traces.size
    ) : HealthDecisionTraceUiState

    data class Empty(
        val message: String = "No decision traces recorded yet for this filter."
    ) : HealthDecisionTraceUiState

    data class Error(
        val message: String
    ) : HealthDecisionTraceUiState
}

/**
 * Single-event side effects (exporting PDF, copying text, displaying errors).
 */
sealed interface HealthDecisionTraceEvent {
    data class ShowToast(val message: String) : HealthDecisionTraceEvent
    data class SharePdf(val file: File, val title: String) : HealthDecisionTraceEvent
    data class CopyToClipboard(val text: String, val label: String) : HealthDecisionTraceEvent
    data class ShowDetailSheet(val trace: HealthDecisionTrace) : HealthDecisionTraceEvent
}
