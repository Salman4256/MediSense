package com.medisense.app.ui.sharing.viewmodel

import com.medisense.app.domain.model.*
import java.io.File

/**
 * UI state representation for Caregiver & Doctor Health Data Sharing screen (Module 22).
 */
data class HealthSharingUiState(
    val isLoading: Boolean = false,
    val loadingMessage: String = "Compiling health package...",
    val scope: HealthSharingScope = HealthSharingScope(),
    val previewPackage: HealthSharingPackage? = null,
    val qualitySummary: HealthDataQualitySummary? = null,
    val consentHistory: List<HealthDataSharingConsent> = emptyList(),
    val exportSuccess: HealthSharingExportResult.Success? = null,
    val errorMessage: String? = null,
    val userMessage: String? = null
)
