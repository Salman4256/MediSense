package com.medisense.app.ui.privacy.viewmodel

import com.medisense.app.domain.model.PrivacyDataCategory
import com.medisense.app.domain.model.PrivacyGovernanceInformation
import com.medisense.app.domain.model.SecurityAuditEvent

data class PrivacySecurityUiState(
    val userEmail: String = "",
    val userId: String = "",
    val dataCategories: List<PrivacyDataCategory> = emptyList(),
    val auditEvents: List<SecurityAuditEvent> = emptyList(),
    val governanceInfo: PrivacyGovernanceInformation = PrivacyGovernanceInformation,
    val isClearingData: Boolean = false,
    val actionSuccessMessage: String? = null,
    val actionErrorMessage: String? = null
)
