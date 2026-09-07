package com.medisense.app.ui.quality.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medisense.app.data.repository.HealthDataQualityRepository
import com.medisense.app.domain.model.HealthDataQualityCategory
import com.medisense.app.domain.model.HealthDataQualitySeverity
import com.medisense.app.domain.model.HealthDataQualitySummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HealthDataQualityViewModel @Inject constructor(
    private val qualityRepository: HealthDataQualityRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HealthDataQualityUiState())
    val uiState: StateFlow<HealthDataQualityUiState> = _uiState.asStateFlow()

    init {
        loadDataQuality()
    }

    fun loadDataQuality() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val summary = qualityRepository.evaluateDataQuality()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        summary = summary,
                        filteredIssues = filterIssues(summary, it.selectedCategory, it.selectedSeverity)
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to evaluate health data quality: ${e.message}"
                    )
                }
            }
        }
    }

    fun filterByCategory(category: HealthDataQualityCategory?) {
        _uiState.update {
            val updatedCategory = if (it.selectedCategory == category) null else category
            it.copy(
                selectedCategory = updatedCategory,
                filteredIssues = filterIssues(it.summary, updatedCategory, it.selectedSeverity)
            )
        }
    }

    fun filterBySeverity(severity: HealthDataQualitySeverity?) {
        _uiState.update {
            val updatedSeverity = if (it.selectedSeverity == severity) null else severity
            it.copy(
                selectedSeverity = updatedSeverity,
                filteredIssues = filterIssues(it.summary, it.selectedCategory, updatedSeverity)
            )
        }
    }

    private fun filterIssues(
        summary: HealthDataQualitySummary?,
        category: HealthDataQualityCategory?,
        severity: HealthDataQualitySeverity?
    ) = summary?.issues?.filter { issue ->
        (category == null || issue.category == category) &&
                (severity == null || issue.severity == severity)
    } ?: emptyList()
}
