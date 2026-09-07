package com.medisense.app.ui.report.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medisense.app.data.repository.HealthReportRepository
import com.medisense.app.domain.model.HealthReportExportResult
import com.medisense.app.domain.security.SecureLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HealthReportViewModel @Inject constructor(
    private val reportRepository: HealthReportRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(HealthReportUiState())
    val uiState: StateFlow<HealthReportUiState> = _uiState.asStateFlow()

    init {
        loadReport()
    }

    /**
     * Loads and generates the complete health report from Room database tables.
     */
    fun loadReport() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val report = reportRepository.generateHealthReport()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        report = report,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Failed to load health report", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to generate health report: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Generates a local PDF report using Android native PdfDocument engine.
     * Always uses applicationContext to prevent context leaks and crashes.
     */
    fun generatePdf(onComplete: ((Boolean, String?) -> Unit)? = null) {
        val report = _uiState.value.report ?: run {
            onComplete?.invoke(false, "Report data not ready")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingPdf = true) }
            try {
                val result = reportRepository.exportPdf(appContext, report)
                when (result) {
                    is HealthReportExportResult.Success -> {
                        _uiState.update {
                            it.copy(
                                isGeneratingPdf = false,
                                exportResult = result,
                                pdfGeneratedFile = result.file,
                                pdfContentUri = result.contentUri
                            )
                        }
                        onComplete?.invoke(true, null)
                    }
                    is HealthReportExportResult.Error -> {
                        _uiState.update {
                            it.copy(
                                isGeneratingPdf = false,
                                exportResult = result
                            )
                        }
                        onComplete?.invoke(false, result.message)
                    }
                }
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Error generating PDF report", e)
                _uiState.update {
                    it.copy(
                        isGeneratingPdf = false,
                        exportResult = HealthReportExportResult.Error("PDF generation error: ${e.message}", e)
                    )
                }
                onComplete?.invoke(false, e.message)
            }
        }
    }

    /**
     * Triggers the privacy confirmation dialog before sharing the report.
     */
    fun requestShareReport() {
        _uiState.update { it.copy(showPrivacyShareConfirmation = true) }
    }

    /**
     * Dismisses the privacy confirmation dialog.
     */
    fun dismissPrivacyConfirmation() {
        _uiState.update { it.copy(showPrivacyShareConfirmation = false) }
    }

    /**
     * Called when user approves sharing in the privacy confirmation dialog.
     * Ensures PDF exists, exports if needed, and delivers the secure content URI.
     * Uses applicationContext exclusively to prevent context leaks.
     */
    fun confirmShareReport(onReadyToShare: (Uri) -> Unit, onError: (String) -> Unit) {
        _uiState.update { it.copy(showPrivacyShareConfirmation = false) }

        val existingUri = _uiState.value.pdfContentUri
        if (existingUri != null) {
            recordShareCompleted()
            onReadyToShare(existingUri)
            return
        }

        // Generate PDF first if not already generated
        generatePdf { success, errorMsg ->
            if (success) {
                val uri = _uiState.value.pdfContentUri
                if (uri != null) {
                    recordShareCompleted()
                    onReadyToShare(uri)
                } else {
                    onError("Failed to obtain secure share URI")
                }
            } else {
                onError(errorMsg ?: "Failed to generate report PDF for sharing")
            }
        }
    }

    /**
     * Records a privacy-safe audit event for sharing the health report.
     */
    fun recordShareCompleted() {
        viewModelScope.launch {
            reportRepository.recordReportSharedAudit()
        }
    }

    companion object {
        private const val TAG = "HealthReportViewModel"
    }
}
