package com.medisense.app.ui.timeline.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medisense.app.data.repository.HealthTimelineRepository
import com.medisense.app.domain.model.*
import com.medisense.app.domain.security.SecureLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HealthTimelineViewModel @Inject constructor(
    private val timelineRepository: HealthTimelineRepository
) : ViewModel() {

    private val TAG = "HealthTimelineVM"

    private val _filterState = MutableStateFlow(HealthTimelineFilter())
    val filterState: StateFlow<HealthTimelineFilter> = _filterState.asStateFlow()

    private val _uiState = MutableStateFlow<HealthTimelineUiState>(HealthTimelineUiState.Loading)
    val uiState: StateFlow<HealthTimelineUiState> = _uiState.asStateFlow()

    private val _selectedEvent = MutableStateFlow<HealthTimelineEvent?>(null)
    val selectedEvent: StateFlow<HealthTimelineEvent?> = _selectedEvent.asStateFlow()

    init {
        observeTimelineData()
    }

    private fun observeTimelineData() {
        viewModelScope.launch {
            _filterState
                .flatMapLatest { filter ->
                    _uiState.value = HealthTimelineUiState.Loading
                    timelineRepository.observeTimeline(flowOf(filter))
                        .catch { e ->
                            SecureLogger.e(TAG, "Error observing health timeline", e)
                            emit(Pair(emptyList(), HealthTimelineSummary(
                                totalEventsCount = 0,
                                recentPredictionsCount = 0,
                                activeMedicationsCount = 0,
                                upcomingAppointmentsCount = 0,
                                detectedPatternsCount = 0,
                                dataQualityStatus = HealthDataQualityStatus.INSUFFICIENT_DATA,
                                latestEventTimestamp = null
                            )))
                        }
                }
                .collect { (events, summary) ->
                    val currentFilter = _filterState.value
                    if (events.isEmpty() && summary.totalEventsCount == 0) {
                        _uiState.value = HealthTimelineUiState.Empty(
                            filter = currentFilter,
                            message = "No health journey records found. As you record disease predictions, manage medications, and schedule appointments, they will appear here chronologically."
                        )
                    } else if (events.isEmpty()) {
                        _uiState.value = HealthTimelineUiState.Empty(
                            filter = currentFilter,
                            message = "No events match the selected filter (${currentFilter.period.displayName}, ${currentFilter.category.displayName}). Try choosing 'All Time' or 'All Categories'."
                        )
                    } else {
                        // Calculate available categories that actually contain events
                        val categoriesWithEvents = events.map { it.eventType.category }.distinct()
                        val availableCategories = mutableListOf(HealthTimelineCategoryFilter.ALL).apply {
                            addAll(categoriesWithEvents)
                        }

                        _uiState.value = HealthTimelineUiState.Content(
                            events = events,
                            summary = summary,
                            filter = currentFilter,
                            availableCategories = availableCategories
                        )
                    }
                }
        }
    }

    fun setPeriod(period: HealthTimelinePeriod) {
        _filterState.update { it.copy(period = period) }
    }

    fun setCategory(category: HealthTimelineCategoryFilter) {
        _filterState.update { it.copy(category = category) }
    }

    fun setSortOrder(sortOrder: HealthTimelineSortOrder) {
        _filterState.update { it.copy(sortOrder = sortOrder) }
    }

    fun toggleSortOrder() {
        val nextOrder = if (_filterState.value.sortOrder == HealthTimelineSortOrder.NEWEST_FIRST) {
            HealthTimelineSortOrder.OLDEST_FIRST
        } else {
            HealthTimelineSortOrder.NEWEST_FIRST
        }
        setSortOrder(nextOrder)
    }

    fun setSearchQuery(query: String) {
        _filterState.update { it.copy(searchQuery = query) }
    }

    fun selectEvent(event: HealthTimelineEvent) {
        _selectedEvent.value = event
    }

    fun clearSelectedEvent() {
        _selectedEvent.value = null
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                _uiState.value = HealthTimelineUiState.Loading
                val (events, summary) = timelineRepository.getTimelineSnapshot(_filterState.value)
                val currentFilter = _filterState.value

                if (events.isEmpty() && summary.totalEventsCount == 0) {
                    _uiState.value = HealthTimelineUiState.Empty(
                        filter = currentFilter,
                        message = "No health journey records found yet."
                    )
                } else if (events.isEmpty()) {
                    _uiState.value = HealthTimelineUiState.Empty(
                        filter = currentFilter,
                        message = "No events match the active filter criteria."
                    )
                } else {
                    val availableCategories = mutableListOf(HealthTimelineCategoryFilter.ALL).apply {
                        addAll(events.map { it.eventType.category }.distinct())
                    }
                    _uiState.value = HealthTimelineUiState.Content(
                        events = events,
                        summary = summary,
                        filter = currentFilter,
                        availableCategories = availableCategories
                    )
                }
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Failed to refresh timeline", e)
                _uiState.value = HealthTimelineUiState.Error("Failed to refresh health timeline: ${e.localizedMessage}")
            }
        }
    }
}
