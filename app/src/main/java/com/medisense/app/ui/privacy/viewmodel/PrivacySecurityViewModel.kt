package com.medisense.app.ui.privacy.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medisense.app.data.remote.supabase.AuthService
import com.medisense.app.data.repository.SecurityAuditRepository
import com.medisense.app.data.sync.SyncRepository
import com.medisense.app.data.sync.model.SyncStatus
import com.medisense.app.domain.model.SecurityAuditEventType
import com.medisense.app.domain.security.PrivacyDataManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PrivacySecurityViewModel @Inject constructor(
    private val authService: AuthService,
    private val securityAuditRepository: SecurityAuditRepository,
    private val privacyDataManager: PrivacyDataManager,
    private val syncRepository: SyncRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrivacySecurityUiState())
    val uiState: StateFlow<PrivacySecurityUiState> = _uiState.asStateFlow()

    init {
        loadSessionDetails()
        observeAuditTelemetry()
        observeSyncData()
    }

    private fun loadSessionDetails() {
        val email = authService.getCurrentUserEmail() ?: "user@medisense.app"
        val userId = authService.getCurrentUserId() ?: "local-user"
        val categories = privacyDataManager.getPrivacyDataCategories()
        val governance = privacyDataManager.getGovernanceInfo()

        _uiState.update {
            it.copy(
                userEmail = email,
                userId = userId,
                dataCategories = categories,
                governanceInfo = governance
            )
        }
    }

    private fun observeAuditTelemetry() {
        securityAuditRepository.observeRecentAuditEvents(limit = 25)
            .onEach { events ->
                _uiState.update { it.copy(auditEvents = events) }
            }
            .launchIn(viewModelScope)
    }

    private fun observeSyncData() {
        syncRepository.observeSyncMetadata()
            .onEach { meta ->
                _uiState.update { it.copy(syncMetadata = meta) }
            }
            .launchIn(viewModelScope)

        syncRepository.syncStatus
            .onEach { status ->
                _uiState.update {
                    it.copy(
                        syncStatus = status,
                        isSyncing = status == SyncStatus.SYNCING
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun triggerSync() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, actionSuccessMessage = null, actionErrorMessage = null) }
            val result = syncRepository.triggerSync()
            _uiState.update {
                it.copy(
                    isSyncing = false,
                    syncStatus = result.status,
                    actionSuccessMessage = if (result.status == SyncStatus.SUCCESS || result.status == SyncStatus.PARTIAL_SUCCESS) {
                        result.message
                    } else null,
                    actionErrorMessage = if (result.status == SyncStatus.FAILED || result.status == SyncStatus.AUTH_REQUIRED || result.status == SyncStatus.OFFLINE) {
                        result.message
                    } else null
                )
            }
        }
    }

    fun clearLocalHealthData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isClearingData = true, actionSuccessMessage = null, actionErrorMessage = null) }
            val success = privacyDataManager.clearLocalUserData(preserveAuditLog = true)
            _uiState.update {
                it.copy(
                    isClearingData = false,
                    actionSuccessMessage = if (success) "Local health records and caches were successfully cleared." else null,
                    actionErrorMessage = if (!success) "Failed to clear local records. Please try again." else null
                )
            }
        }
    }

    fun clearAuditHistory() {
        viewModelScope.launch {
            securityAuditRepository.clearAuditHistory()
            _uiState.update {
                it.copy(actionSuccessMessage = "Local security audit logs cleared.")
            }
        }
    }

    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                securityAuditRepository.recordEvent(SecurityAuditEventType.LOGOUT, "User logged out")
                authService.logout()
            } catch (ignored: Exception) {}
            onComplete()
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(actionSuccessMessage = null, actionErrorMessage = null) }
    }
}
