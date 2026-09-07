package com.medisense.app.ui.portability.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medisense.app.data.remote.supabase.AuthService
import com.medisense.app.data.repository.PortableHealthDataRepository
import com.medisense.app.data.repository.SecurityAuditRepository
import com.medisense.app.domain.model.PortableDataCategory
import com.medisense.app.domain.model.PortableExportPreview
import com.medisense.app.domain.model.PortableExportResult
import com.medisense.app.domain.model.PortableExportScope
import com.medisense.app.domain.security.SecureLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class ExportPreset {
    FULL,
    CLINICAL,
    EMERGENCY,
    AI_INSIGHTS
}

@HiltViewModel
class HealthDataPortabilityViewModel @Inject constructor(
    private val portabilityRepository: PortableHealthDataRepository,
    private val authService: AuthService,
    private val securityAuditRepository: SecurityAuditRepository
) : ViewModel() {

    private val TAG = "HealthDataPortabilityVM"

    private val _uiState = MutableStateFlow(HealthDataPortabilityUiState())
    val uiState: StateFlow<HealthDataPortabilityUiState> = _uiState.asStateFlow()

    init {
        loadInitialState()
    }

    private fun loadInitialState() {
        viewModelScope.launch {
            try {
                val userId = authService.getCurrentUserId() ?: "offline_user"
                val initialCategories = PortableDataCategory.entries.map { cat ->
                    CategoryItemUiState(
                        category = cat,
                        isSelected = cat.defaultSelected,
                        recordCount = 0
                    )
                }

                _uiState.value = _uiState.value.copy(
                    userId = userId,
                    categories = initialCategories
                )
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Error initializing portability state", e)
            }
        }
    }

    fun toggleCategory(category: PortableDataCategory) {
        val updatedCategories = _uiState.value.categories.map { item ->
            if (item.category == category) {
                item.copy(isSelected = !item.isSelected)
            } else {
                item
            }
        }
        _uiState.value = _uiState.value.copy(categories = updatedCategories)
    }

    fun selectAllCategories() {
        val updatedCategories = _uiState.value.categories.map { it.copy(isSelected = true) }
        _uiState.value = _uiState.value.copy(categories = updatedCategories)
    }

    fun clearAllCategories() {
        val updatedCategories = _uiState.value.categories.map { it.copy(isSelected = false) }
        _uiState.value = _uiState.value.copy(categories = updatedCategories)
    }

    fun applyPreset(preset: ExportPreset) {
        val selectedSet: Set<PortableDataCategory> = when (preset) {
            ExportPreset.FULL -> PortableDataCategory.entries.toSet()
            ExportPreset.CLINICAL -> setOf(
                PortableDataCategory.BASIC_PROFILE,
                PortableDataCategory.ALLERGIES,
                PortableDataCategory.HEALTH_CONDITIONS,
                PortableDataCategory.MEDICATIONS,
                PortableDataCategory.MEDICATION_ADHERENCE,
                PortableDataCategory.APPOINTMENTS,
                PortableDataCategory.DATA_QUALITY
            )
            ExportPreset.EMERGENCY -> setOf(
                PortableDataCategory.BASIC_PROFILE,
                PortableDataCategory.EMERGENCY_INFORMATION,
                PortableDataCategory.ALLERGIES,
                PortableDataCategory.HEALTH_CONDITIONS,
                PortableDataCategory.MEDICATIONS,
                PortableDataCategory.DATA_QUALITY
            )
            ExportPreset.AI_INSIGHTS -> setOf(
                PortableDataCategory.PREDICTION_HISTORY,
                PortableDataCategory.HEALTH_TRENDS,
                PortableDataCategory.PERSONAL_CONTEXT,
                PortableDataCategory.PERSONALIZED_GUIDANCE,
                PortableDataCategory.DECISION_TRACES,
                PortableDataCategory.HEALTH_TIMELINE,
                PortableDataCategory.RCHR_STATE,
                PortableDataCategory.CONSULTATION_SUMMARY
            )
        }

        val updatedCategories = _uiState.value.categories.map { item ->
            item.copy(isSelected = selectedSet.contains(item.category))
        }
        _uiState.value = _uiState.value.copy(categories = updatedCategories)
    }

    fun setPrettyPrint(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(prettyPrint = enabled)
    }

    fun setIncludeDisclaimers(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(includeDisclaimers = enabled)
    }

    fun setIncludeFingerprint(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(includeFingerprint = enabled)
    }

    fun generatePreview() {
        val selectedCategories = _uiState.value.categories
            .filter { it.isSelected }
            .map { it.category }
            .toSet()

        if (selectedCategories.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please select at least one data category to inspect.")
            return
        }

        val scope = PortableExportScope(
            selectedCategories = selectedCategories,
            userConfirmationAccepted = true
        )

        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            try {
                val preview = withContext(Dispatchers.IO) {
                    portabilityRepository.generatePreview(scope)
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    preview = preview
                )
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Failed to generate export preview", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to preview export: ${e.message}"
                )
            }
        }
    }

    fun dismissPreview() {
        viewModelScope.launch {
            portabilityRepository.recordExportCancelled("User closed preview dialog")
        }
        _uiState.value = _uiState.value.copy(preview = null)
    }

    fun generateExport(context: Context) {
        val selectedCategories = _uiState.value.categories
            .filter { it.isSelected }
            .map { it.category }
            .toSet()

        if (selectedCategories.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please select at least one data category to export.")
            return
        }

        val scope = PortableExportScope(
            selectedCategories = selectedCategories,
            userConfirmationAccepted = true
        )

        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    portabilityRepository.exportPortableBundle(context, scope)
                }

                result.fold(
                    onSuccess = { successResult ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            exportResult = successResult,
                            preview = null,
                            infoMessage = "Portable health record generated successfully."
                        )
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Export generation failed: ${error.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Failed to generate health record export", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Export generation failed: ${e.message}"
                )
            }
        }
    }

    fun recordShareAuditEvent(packageId: String) {
        viewModelScope.launch {
            portabilityRepository.recordExportShared(packageId)
        }
    }

    fun dismissExportResult() {
        _uiState.value = _uiState.value.copy(exportResult = null)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun dismissInfo() {
        _uiState.value = _uiState.value.copy(infoMessage = null)
    }
}
