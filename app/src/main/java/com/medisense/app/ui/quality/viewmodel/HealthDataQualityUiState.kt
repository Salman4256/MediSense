package com.medisense.app.ui.quality.viewmodel

import com.medisense.app.domain.model.HealthDataQualityCategory
import com.medisense.app.domain.model.HealthDataQualityIssue
import com.medisense.app.domain.model.HealthDataQualitySeverity
import com.medisense.app.domain.model.HealthDataQualitySummary

data class HealthDataQualityUiState(
    val isLoading: Boolean = true,
    val summary: HealthDataQualitySummary? = null,
    val selectedCategory: HealthDataQualityCategory? = null, // null = ALL
    val selectedSeverity: HealthDataQualitySeverity? = null, // null = ALL
    val filteredIssues: List<HealthDataQualityIssue> = emptyList(),
    val errorMessage: String? = null
)
