package com.medisense.app.ui.portability.viewmodel

import com.medisense.app.domain.model.PortableDataCategory
import com.medisense.app.domain.model.PortableExportPreview
import com.medisense.app.domain.model.PortableExportResult

data class CategoryItemUiState(
    val category: PortableDataCategory,
    val isSelected: Boolean,
    val recordCount: Int = 0
)

data class HealthDataPortabilityUiState(
    val isLoading: Boolean = false,
    val userId: String = "",
    val categories: List<CategoryItemUiState> = emptyList(),
    val prettyPrint: Boolean = true,
    val includeDisclaimers: Boolean = true,
    val includeFingerprint: Boolean = true,
    val preview: PortableExportPreview? = null,
    val exportResult: PortableExportResult? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null
) {
    val selectedCount: Int
        get() = categories.count { it.isSelected }

    val totalRecordsSelected: Int
        get() = categories.filter { it.isSelected }.sumOf { it.recordCount }
}
