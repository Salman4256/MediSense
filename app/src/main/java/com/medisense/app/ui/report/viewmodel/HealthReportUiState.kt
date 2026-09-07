package com.medisense.app.ui.report.viewmodel

import android.net.Uri
import com.medisense.app.domain.model.HealthReport
import com.medisense.app.domain.model.HealthReportExportResult
import java.io.File

/**
 * UI State container for the Comprehensive Personal Health Report screen.
 */
data class HealthReportUiState(
    val isLoading: Boolean = false,
    val report: HealthReport? = null,
    val errorMessage: String? = null,
    val isGeneratingPdf: Boolean = false,
    val exportResult: HealthReportExportResult? = null,
    val pdfGeneratedFile: File? = null,
    val pdfContentUri: Uri? = null,
    val showPrivacyShareConfirmation: Boolean = false
)
