package com.medisense.app.ui.trace.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medisense.app.data.remote.supabase.AuthService
import com.medisense.app.data.repository.HealthDecisionTraceRepository
import com.medisense.app.domain.model.HealthDecisionTrace
import com.medisense.app.domain.model.HealthDecisionTraceType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HealthDecisionTraceViewModel @Inject constructor(
    private val repository: HealthDecisionTraceRepository,
    private val authService: AuthService
) : ViewModel() {

    private val _activeFilter = MutableStateFlow<HealthDecisionTraceType?>(null)
    val activeFilter: StateFlow<HealthDecisionTraceType?> = _activeFilter.asStateFlow()

    private val _uiState = MutableStateFlow<HealthDecisionTraceUiState>(HealthDecisionTraceUiState.Loading)
    val uiState: StateFlow<HealthDecisionTraceUiState> = _uiState.asStateFlow()

    private val _eventChannel = Channel<HealthDecisionTraceEvent>(Channel.BUFFERED)
    val events: Flow<HealthDecisionTraceEvent> = _eventChannel.receiveAsFlow()

    init {
        loadTraces()
    }

    fun setFilter(filterType: HealthDecisionTraceType?) {
        _activeFilter.value = filterType
        loadTraces()
    }

    fun loadTraces() {
        if (!authService.isUserLoggedIn()) {
            _uiState.value = HealthDecisionTraceUiState.Error("Please log in to view explainable AI decision traces.")
            return
        }

        _uiState.value = HealthDecisionTraceUiState.Loading
        viewModelScope.launch {
            repository.observeDecisionTraces(_activeFilter.value)
                .catch { e ->
                    _uiState.value = HealthDecisionTraceUiState.Error(e.message ?: "Failed to load decision traces.")
                }
                .collect { traces ->
                    if (traces.isEmpty()) {
                        _uiState.value = HealthDecisionTraceUiState.Empty("No health decision traces recorded yet for this selection.")
                    } else {
                        _uiState.value = HealthDecisionTraceUiState.Success(
                            traces = traces,
                            activeFilter = _activeFilter.value,
                            totalCount = traces.size
                        )
                    }
                }
        }
    }

    fun onInspectTrace(trace: HealthDecisionTrace) {
        viewModelScope.launch {
            repository.recordTraceViewedAudit(trace)
            _eventChannel.send(HealthDecisionTraceEvent.ShowDetailSheet(trace))
        }
    }

    fun onExportPdf(context: Context, trace: HealthDecisionTrace) {
        viewModelScope.launch {
            val result = repository.generateTracePdf(context, trace)
            result.fold(
                onSuccess = { file ->
                    repository.recordTraceExportedAudit(trace, "PDF")
                    _eventChannel.send(HealthDecisionTraceEvent.SharePdf(file, "Decision Trace - ${trace.outputResult.primaryOutput}"))
                },
                onFailure = { error ->
                    _eventChannel.send(HealthDecisionTraceEvent.ShowToast("Failed to generate PDF: ${error.localizedMessage}"))
                }
            )
        }
    }

    fun getFormattedPlainText(trace: HealthDecisionTrace): String {
        return repository.formatTraceAsPlainText(trace)
    }

    fun onCopyPlainText(trace: HealthDecisionTrace) {
        viewModelScope.launch {
            val formattedText = repository.formatTraceAsPlainText(trace)
            repository.recordTraceExportedAudit(trace, "TEXT")
            _eventChannel.send(HealthDecisionTraceEvent.CopyToClipboard(formattedText, "MediSense Decision Trace"))
        }
    }
}
