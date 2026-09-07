package com.medisense.app.ui.sharing.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medisense.app.data.remote.supabase.AuthService
import com.medisense.app.data.repository.HealthDataQualityRepository
import com.medisense.app.data.repository.HealthSharingRepository
import com.medisense.app.data.repository.SecurityAuditRepository
import com.medisense.app.domain.model.*
import com.medisense.app.domain.security.SecureLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class HealthSharingViewModel @Inject constructor(
    private val sharingRepository: HealthSharingRepository,
    private val dataQualityRepository: HealthDataQualityRepository,
    private val securityAuditRepository: SecurityAuditRepository,
    private val authService: AuthService
) : ViewModel() {

    private val TAG = "HealthSharingVM"

    private val _uiState = MutableStateFlow(HealthSharingUiState())
    val uiState: StateFlow<HealthSharingUiState> = _uiState.asStateFlow()

    init {
        observeConsentHistory()
        loadDataQualityStatus()
    }

    private fun observeConsentHistory() {
        viewModelScope.launch {
            sharingRepository.observeConsents()
                .catch { e ->
                    SecureLogger.e(TAG, "Error observing consent history", e)
                }
                .collectLatest { list ->
                    _uiState.value = _uiState.value.copy(consentHistory = list)
                }
        }
    }

    private fun loadDataQualityStatus() {
        viewModelScope.launch {
            try {
                val quality = dataQualityRepository.evaluateDataQuality()
                _uiState.value = _uiState.value.copy(qualitySummary = quality)
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Failed to evaluate health data quality", e)
            }
        }
    }

    fun updatePurpose(purpose: SharingPurpose) {
        val currentScope = _uiState.value.scope
        _uiState.value = _uiState.value.copy(scope = currentScope.copy(purpose = purpose))
    }

    fun updateRecipientLabel(label: String) {
        val currentScope = _uiState.value.scope
        _uiState.value = _uiState.value.copy(scope = currentScope.copy(recipientLabel = label))
    }

    fun toggleCategory(category: SharingDataCategory, isSelected: Boolean) {
        val currentScope = _uiState.value.scope
        val currentSet = currentScope.selectedCategories.toMutableSet()
        if (isSelected) {
            currentSet.add(category)
        } else {
            currentSet.remove(category)
        }
        _uiState.value = _uiState.value.copy(scope = currentScope.copy(selectedCategories = currentSet))
    }

    fun selectAllCategories(selectAll: Boolean) {
        val currentScope = _uiState.value.scope
        val newSet = if (selectAll) {
            SharingDataCategory.entries.toSet()
        } else {
            emptySet()
        }
        _uiState.value = _uiState.value.copy(scope = currentScope.copy(selectedCategories = newSet))
    }

    fun updateFormat(format: SharingFormat) {
        val currentScope = _uiState.value.scope
        _uiState.value = _uiState.value.copy(scope = currentScope.copy(format = format))
    }

    fun preparePackageForPreview(onPreviewReady: (HealthSharingPackage) -> Unit) {
        val scope = _uiState.value.scope
        if (scope.selectedCategories.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please select at least one health category to share.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                loadingMessage = "Compiling secure health package preview..."
            )
            try {
                val pkg = sharingRepository.prepareSharingPackage(scope)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    previewPackage = pkg,
                    errorMessage = null
                )
                onPreviewReady(pkg)
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Failed to prepare preview package", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to compile package: ${e.localizedMessage}"
                )
            }
        }
    }

    fun grantConsentAndExport(context: Context, onComplete: (HealthSharingExportResult.Success?) -> Unit) {
        val scope = _uiState.value.scope
        val pkg = _uiState.value.previewPackage
        if (pkg == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "Package preview not ready.")
            onComplete(null)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                loadingMessage = "Writing files and recording local consent..."
            )
            try {
                val result = sharingRepository.grantConsentAndExport(context, scope, pkg)
                if (result.isSuccess) {
                    val exportSuccess = result.getOrThrow()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        exportSuccess = exportSuccess,
                        userMessage = "Health package successfully prepared for sharing!"
                    )
                    onComplete(exportSuccess)
                } else {
                    val error = result.exceptionOrNull()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Export failed: ${error?.localizedMessage}"
                    )
                    onComplete(null)
                }
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Error granting consent and exporting", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Export failed: ${e.localizedMessage}"
                )
                onComplete(null)
            }
        }
    }

    fun revokeConsent(consentId: Long) {
        viewModelScope.launch {
            try {
                sharingRepository.revokeConsent(consentId)
                _uiState.value = _uiState.value.copy(userMessage = "Consent revoked locally.")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to revoke consent: ${e.localizedMessage}")
            }
        }
    }

    fun deleteConsent(consentId: Long) {
        viewModelScope.launch {
            try {
                sharingRepository.deleteConsentRecord(consentId)
                _uiState.value = _uiState.value.copy(userMessage = "Consent record deleted.")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to delete record: ${e.localizedMessage}")
            }
        }
    }

    fun cancelSharingFlow(reason: String) {
        viewModelScope.launch {
            sharingRepository.recordSharingCancelled(reason)
        }
    }

    fun clearErrorMessage() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun clearUserMessage() {
        _uiState.value = _uiState.value.copy(userMessage = null)
    }
}
