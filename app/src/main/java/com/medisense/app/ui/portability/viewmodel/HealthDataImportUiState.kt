package com.medisense.app.ui.portability.viewmodel

import com.medisense.app.domain.model.PortableImportCategoryPreview
import com.medisense.app.domain.model.PortableImportPreview
import com.medisense.app.domain.model.PortableImportStatus
import com.medisense.app.domain.model.ValidatedImportPackage

data class HealthDataImportUiState(
    val status: PortableImportStatus = PortableImportStatus.IDLE,
    val activeUserId: String = "",
    val preview: PortableImportPreview? = null,
    val selectedCategoryDetail: PortableImportCategoryPreview? = null,
    val validatedPackage: ValidatedImportPackage? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null
) {
    val isLoading: Boolean
        get() = status == PortableImportStatus.PARSING ||
                status == PortableImportStatus.VALIDATING ||
                status == PortableImportStatus.PREPARING

    val hasFileLoaded: Boolean
        get() = preview != null

    val isImportable: Boolean
        get() = preview?.summary?.isImportable == true
}
