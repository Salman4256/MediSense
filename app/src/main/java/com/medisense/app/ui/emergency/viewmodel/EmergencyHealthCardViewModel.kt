package com.medisense.app.ui.emergency.viewmodel

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medisense.app.data.remote.supabase.AuthService
import com.medisense.app.data.repository.EmergencyHealthCardRepository
import com.medisense.app.data.repository.SecurityAuditRepository
import com.medisense.app.domain.model.EmergencyHealthCard
import com.medisense.app.domain.model.SecurityAuditEventType
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
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * ViewModel for Emergency & Critical Health Access Card.
 * Manages reactive observation of emergency health records and secure export telemetry.
 */
@HiltViewModel
class EmergencyHealthCardViewModel @Inject constructor(
    private val emergencyCardRepository: EmergencyHealthCardRepository,
    private val securityAuditRepository: SecurityAuditRepository,
    private val authService: AuthService
) : ViewModel() {

    private val TAG = "EmergencyCardVM"

    private val _uiState = MutableStateFlow<EmergencyHealthCardUiState>(EmergencyHealthCardUiState.Loading)
    val uiState: StateFlow<EmergencyHealthCardUiState> = _uiState.asStateFlow()

    private var currentCard: EmergencyHealthCard? = null
    private var hasLoggedViewEvent = false

    init {
        loadEmergencyCard()
    }

    fun loadEmergencyCard() {
        viewModelScope.launch {
            val userId = authService.getCurrentUserId() ?: "local-user"
            _uiState.value = EmergencyHealthCardUiState.Loading

            emergencyCardRepository.observeEmergencyHealthCard(userId)
                .catch { e ->
                    SecureLogger.e(TAG, "Error observing emergency health card", e)
                    _uiState.value = EmergencyHealthCardUiState.Error(
                        e.localizedMessage ?: "Failed to load Emergency Health Card"
                    )
                }
                .collectLatest { card ->
                    currentCard = card
                    _uiState.value = EmergencyHealthCardUiState.Content(card = card)
                    if (!hasLoggedViewEvent) {
                        hasLoggedViewEvent = true
                        logCardViewed()
                    }
                }
        }
    }

    fun logCardViewed() {
        viewModelScope.launch {
            try {
                securityAuditRepository.recordEvent(SecurityAuditEventType.EMERGENCY_CARD_VIEWED)
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Failed to record card viewed audit event", e)
            }
        }
    }

    fun logEmergencyContactDialInitiated() {
        viewModelScope.launch {
            try {
                securityAuditRepository.recordEvent(SecurityAuditEventType.EMERGENCY_CONTACT_DIAL_INITIATED)
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Failed to record dial initiated audit event", e)
            }
        }
    }

    fun logCardShared() {
        viewModelScope.launch {
            try {
                securityAuditRepository.recordEvent(SecurityAuditEventType.EMERGENCY_CARD_SHARED)
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Failed to record card shared audit event", e)
            }
        }
    }

    fun getPlainTextCard(): String {
        val card = currentCard ?: return ""
        return EmergencyHealthCard.formatAsPlainText(card)
    }

    fun exportEmergencyCardPdf(context: Context, onResult: (Uri?) -> Unit) {
        val card = currentCard
        if (card == null) {
            onResult(null)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val genResult = com.medisense.app.domain.emergency.EmergencyHealthCardPdfGenerator.generatePdf(context, card)
                if (genResult is com.medisense.app.domain.model.HealthReportExportResult.Success) {
                    val contentUri: Uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        genResult.file
                    )
                    withContext(Dispatchers.Main) {
                        onResult(contentUri)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onResult(null)
                    }
                }
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Failed to export emergency card PDF", e)
                withContext(Dispatchers.Main) {
                    onResult(null)
                }
            }
        }
    }

    fun exportEmergencyCardAsText(context: Context, onResult: (Uri?) -> Unit) {
        val card = currentCard
        if (card == null) {
            onResult(null)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val textContent = EmergencyHealthCard.formatAsPlainText(card)
                val emergencyDir = File(context.cacheDir, "emergency")
                if (!emergencyDir.exists()) {
                    emergencyDir.mkdirs()
                }

                val exportFile = File(emergencyDir, "medisense_emergency_card.txt")
                FileOutputStream(exportFile).use { fos ->
                    fos.write(textContent.toByteArray(Charsets.UTF_8))
                }

                val contentUri: Uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    exportFile
                )

                withContext(Dispatchers.Main) {
                    onResult(contentUri)
                }
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Failed to export emergency card text file", e)
                withContext(Dispatchers.Main) {
                    onResult(null)
                }
            }
        }
    }
}
