package com.medisense.app.ui.consultation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medisense.app.data.repository.ConsultationPreparationRepository
import com.medisense.app.domain.model.*
import com.medisense.app.domain.security.SecureLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ConsultationPreparationViewModel @Inject constructor(
    private val repository: ConsultationPreparationRepository
) : ViewModel() {

    private val TAG = "ConsultationPrepVM"

    private val _selectedPeriod = MutableStateFlow(ConsultationPeriod.LAST_30_DAYS)
    val selectedPeriod: StateFlow<ConsultationPeriod> = _selectedPeriod.asStateFlow()

    private val _customQuestions = MutableStateFlow<List<ConsultationQuestion>>(emptyList())
    val customQuestions: StateFlow<List<ConsultationQuestion>> = _customQuestions.asStateFlow()

    private val _uiState = MutableStateFlow<ConsultationPreparationUiState>(ConsultationPreparationUiState.Loading)
    val uiState: StateFlow<ConsultationPreparationUiState> = _uiState.asStateFlow()

    private var currentSummary: ConsultationSummary? = null

    init {
        loadSummary()
    }

    fun loadSummary() {
        viewModelScope.launch {
            _uiState.value = ConsultationPreparationUiState.Loading
            try {
                val summary = repository.generateConsultationSummary(
                    period = _selectedPeriod.value,
                    customQuestions = _customQuestions.value
                )
                currentSummary = summary

                if (summary.visitOverview.totalRecordsCount == 0 && summary.profile.fullName == "Not recorded") {
                    _uiState.value = ConsultationPreparationUiState.Empty(
                        period = _selectedPeriod.value,
                        message = "No health entries found for ${_selectedPeriod.value.displayName}. As you record predictions, medications, and visits, your consultation summary will be prepared automatically."
                    )
                } else {
                    _uiState.value = ConsultationPreparationUiState.Content(
                        summary = summary,
                        selectedPeriod = _selectedPeriod.value
                    )
                }
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Error generating consultation summary", e)
                _uiState.value = ConsultationPreparationUiState.Error("Failed to prepare consultation summary: ${e.localizedMessage}")
            }
        }
    }

    fun setPeriod(period: ConsultationPeriod) {
        if (_selectedPeriod.value != period) {
            _selectedPeriod.value = period
            loadSummary()
        }
    }

    fun addCustomQuestion(questionText: String) {
        val trimmed = questionText.trim()
        if (trimmed.isBlank()) return

        val newQuestion = ConsultationQuestion(
            id = "custom_q_${UUID.randomUUID().toString().take(8)}",
            questionText = trimmed,
            category = ConsultationQuestionSource.USER_CUSTOM,
            rationale = "Custom question added by you for this visit.",
            isCustom = true,
            isSelected = true
        )

        _customQuestions.value = _customQuestions.value + newQuestion
        loadSummary()
    }

    fun removeCustomQuestion(questionId: String) {
        _customQuestions.value = _customQuestions.value.filterNot { it.id == questionId }
        loadSummary()
    }

    fun toggleQuestionSelection(questionId: String) {
        val summary = currentSummary ?: return

        val updatedSuggested = summary.suggestedQuestions.map {
            if (it.id == questionId) it.copy(isSelected = !it.isSelected) else it
        }

        val updatedCustom = summary.customQuestions.map {
            if (it.id == questionId) it.copy(isSelected = !it.isSelected) else it
        }

        val updatedSummary = summary.copy(
            suggestedQuestions = updatedSuggested,
            customQuestions = updatedCustom
        )
        currentSummary = updatedSummary

        _uiState.value = ConsultationPreparationUiState.Content(
            summary = updatedSummary,
            selectedPeriod = _selectedPeriod.value
        )
    }

    fun getFormattedSummaryPlainText(): String {
        val summary = currentSummary ?: return ""
        return repository.formatSummaryAsPlainText(summary)
    }

    fun exportSummaryPdf(context: Context, onResult: (HealthReportExportResult) -> Unit) {
        val summary = currentSummary ?: return
        viewModelScope.launch {
            val result = repository.exportSummaryPdf(context, summary)
            onResult(result)
        }
    }

    fun exportSummaryDocument(context: Context, onResult: (HealthReportExportResult) -> Unit) {
        val summary = currentSummary ?: return
        viewModelScope.launch {
            val result = repository.exportSummaryPdf(context, summary)
            onResult(result)
        }
    }

    fun recordShareCompleted() {
        viewModelScope.launch {
            repository.recordSummarySharedAudit()
        }
    }
}
