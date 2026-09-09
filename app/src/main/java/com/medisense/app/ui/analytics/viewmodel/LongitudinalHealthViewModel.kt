package com.medisense.app.ui.analytics.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medisense.app.data.repository.LongitudinalHealthRepository
import com.medisense.app.domain.model.AnalysisPeriod
import com.medisense.app.domain.model.LongitudinalHealthSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface LongitudinalHealthUiState {
    data object Loading : LongitudinalHealthUiState
    data class Success(val summary: LongitudinalHealthSummary) : LongitudinalHealthUiState
    data class InsufficientData(val period: AnalysisPeriod) : LongitudinalHealthUiState
    data class Error(val message: String) : LongitudinalHealthUiState
}

@HiltViewModel
class LongitudinalHealthViewModel @Inject constructor(
    private val repository: LongitudinalHealthRepository
) : ViewModel() {

    private val _selectedPeriod = MutableStateFlow(AnalysisPeriod.DAYS_30)
    val selectedPeriod: StateFlow<AnalysisPeriod> = _selectedPeriod.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<LongitudinalHealthUiState> = _selectedPeriod
        .flatMapLatest { period ->
            repository.observeLongitudinalSummary(period)
                .map { summary ->
                    if (summary.hasSufficientData) {
                        LongitudinalHealthUiState.Success(summary)
                    } else {
                        LongitudinalHealthUiState.InsufficientData(period)
                    }
                }
                .catch { e ->
                    emit(LongitudinalHealthUiState.Error(e.message ?: "Failed to load health analytics"))
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LongitudinalHealthUiState.Loading
        )

    fun selectPeriod(period: AnalysisPeriod) {
        _selectedPeriod.value = period
    }
}
