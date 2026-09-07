package com.medisense.app.ui.portability.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medisense.app.data.remote.supabase.AuthService
import com.medisense.app.data.repository.PortableImportRepository
import com.medisense.app.domain.model.PortableImportCategoryPreview
import com.medisense.app.domain.model.PortableImportStatus
import com.medisense.app.domain.security.SecureLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class HealthDataImportViewModel @Inject constructor(
    private val importRepository: PortableImportRepository,
    private val authService: AuthService
) : ViewModel() {

    private val TAG = "HealthDataImportVM"

    private val _uiState = MutableStateFlow(HealthDataImportUiState())
    val uiState: StateFlow<HealthDataImportUiState> = _uiState.asStateFlow()

    init {
        val currentUserId = authService.getCurrentUserId() ?: "offline_user"
        _uiState.value = _uiState.value.copy(activeUserId = currentUserId)
    }

    /**
     * Reads and validates the selected portable health data JSON file.
     */
    fun processFileUri(context: Context, uri: Uri) {
        _uiState.value = _uiState.value.copy(
            status = PortableImportStatus.PARSING,
            errorMessage = null,
            validatedPackage = null
        )

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(status = PortableImportStatus.VALIDATING)
                val result = withContext(Dispatchers.IO) {
                    importRepository.validateFileUri(context, uri)
                }

                result.fold(
                    onSuccess = { preview ->
                        val newStatus = when {
                            preview.summary.errorCount > 0 -> PortableImportStatus.BLOCKED
                            preview.summary.warningCount > 0 -> PortableImportStatus.READY_WITH_WARNINGS
                            else -> PortableImportStatus.READY
                        }
                        _uiState.value = _uiState.value.copy(
                            status = newStatus,
                            preview = preview,
                            infoMessage = if (preview.summary.isImportable) "Portable health file validated successfully." else null
                        )
                    },
                    onFailure = { error ->
                        SecureLogger.e(TAG, "File validation error", error)
                        _uiState.value = _uiState.value.copy(
                            status = PortableImportStatus.ERROR,
                            errorMessage = "Validation failed: ${error.localizedMessage ?: "Invalid file"}"
                        )
                    }
                )
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Unexpected error processing file", e)
                _uiState.value = _uiState.value.copy(
                    status = PortableImportStatus.ERROR,
                    errorMessage = "Failed to process file: ${e.message}"
                )
            }
        }
    }

    /**
     * Inspects detailed records and validation issues for a single category.
     */
    fun inspectCategory(categoryPreview: PortableImportCategoryPreview) {
        _uiState.value = _uiState.value.copy(selectedCategoryDetail = categoryPreview)
        viewModelScope.launch {
            importRepository.recordImportPreviewed(_uiState.value.preview?.summary?.packageId)
        }
    }

    fun dismissCategoryDetail() {
        _uiState.value = _uiState.value.copy(selectedCategoryDetail = null)
    }

    /**
     * Prepares the in-memory validated import package after explicit user confirmation.
     */
    fun prepareValidatedImportPackage(userConfirmed: Boolean) {
        val preview = _uiState.value.preview ?: return

        _uiState.value = _uiState.value.copy(status = PortableImportStatus.PREPARING, errorMessage = null)

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    importRepository.prepareValidatedPackage(preview, userConfirmed)
                }

                result.fold(
                    onSuccess = { validatedPkg ->
                        _uiState.value = _uiState.value.copy(
                            status = PortableImportStatus.COMPLETED,
                            validatedPackage = validatedPkg,
                            infoMessage = "Validated package (${validatedPkg.totalValidRecords} records) prepared in memory."
                        )
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            status = PortableImportStatus.READY,
                            errorMessage = "Failed to prepare package: ${error.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Error preparing validated import package", e)
                _uiState.value = _uiState.value.copy(
                    status = PortableImportStatus.READY,
                    errorMessage = "Error preparing package: ${e.message}"
                )
            }
        }
    }

    /**
     * Clears current selection and resets state.
     */
    fun clearFile() {
        viewModelScope.launch {
            importRepository.recordImportCancelled("User cleared file selection")
        }
        _uiState.value = _uiState.value.copy(
            status = PortableImportStatus.IDLE,
            preview = null,
            selectedCategoryDetail = null,
            validatedPackage = null,
            errorMessage = null
        )
    }

    fun dismissValidatedPackage() {
        _uiState.value = _uiState.value.copy(validatedPackage = null)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun dismissInfo() {
        _uiState.value = _uiState.value.copy(infoMessage = null)
    }
}
