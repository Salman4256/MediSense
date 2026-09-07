package com.medisense.app.ui.consultation.viewmodel

import com.medisense.app.domain.model.ConsultationPeriod
import com.medisense.app.domain.model.ConsultationSummary
import com.medisense.app.domain.model.HealthReportExportResult

sealed class ConsultationPreparationUiState {
    object Loading : ConsultationPreparationUiState()

    data class Content(
        val summary: ConsultationSummary,
        val selectedPeriod: ConsultationPeriod,
        val exportResult: HealthReportExportResult? = null
    ) : ConsultationPreparationUiState()

    data class Empty(
        val period: ConsultationPeriod,
        val message: String = "No recorded health information available for this time period."
    ) : ConsultationPreparationUiState()

    data class Error(
        val message: String
    ) : ConsultationPreparationUiState()
}
