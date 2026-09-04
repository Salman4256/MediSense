package com.medisense.app.ui.medication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medisense.app.data.local.dao.MedicationDao
import com.medisense.app.data.local.entity.MedicationEntity
import com.medisense.app.data.local.entity.MedicationHistoryEntity
import com.medisense.app.data.repository.MedicationRepository
import com.medisense.app.utils.AdherenceStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AdherencePeriod {
    TODAY,
    LAST_7_DAYS,
    OVERALL
}

@HiltViewModel
class MedicationViewModel @Inject constructor(
    private val repository: MedicationRepository,
    private val medicationDao: MedicationDao
) : ViewModel() {

    private val _medications = MutableStateFlow<List<MedicationEntity>>(emptyList())
    val medications: StateFlow<List<MedicationEntity>> = _medications.asStateFlow()

    private val _history = MutableStateFlow<List<MedicationHistoryEntity>>(emptyList())
    val history: StateFlow<List<MedicationHistoryEntity>> = _history.asStateFlow()

    private val _adherenceStats = MutableStateFlow(AdherenceStats())
    val adherenceStats: StateFlow<AdherenceStats> = _adherenceStats.asStateFlow()

    private val _currentPeriod = MutableStateFlow(AdherencePeriod.TODAY)
    val currentPeriod: StateFlow<AdherencePeriod> = _currentPeriod.asStateFlow()

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    init {
        loadMedications()
        setAdherencePeriod(AdherencePeriod.TODAY)
        loadHistory()
    }

    fun loadMedications() {
        viewModelScope.launch {
            val userId = repository.getCurrentUserId()
            medicationDao.getMedicationsForUser(userId)
                .catch { e -> _userMessage.emit(e.message ?: "Failed to load medications") }
                .collect { list ->
                    _medications.value = list
                }
        }
    }

    fun loadHistory() {
        viewModelScope.launch {
            repository.getHistory()
                .catch { e -> _userMessage.emit(e.message ?: "Failed to load history") }
                .collect { list ->
                    _history.value = list
                }
        }
    }

    fun setAdherencePeriod(period: AdherencePeriod) {
        _currentPeriod.value = period
        viewModelScope.launch {
            val flow = when (period) {
                AdherencePeriod.TODAY -> repository.getTodayAdherence()
                AdherencePeriod.LAST_7_DAYS -> repository.getWeeklyAdherence()
                AdherencePeriod.OVERALL -> repository.getOverallAdherence()
            }
            flow.catch { }.collect { stats ->
                _adherenceStats.value = stats
            }
        }
    }

    fun toggleActive(medicationId: Long, active: Boolean) {
        viewModelScope.launch {
            repository.toggleActive(medicationId, active)
        }
    }

    fun deleteMedication(medicationId: Long) {
        viewModelScope.launch {
            val result = repository.deleteMedication(medicationId)
            if (result.isSuccess) {
                _userMessage.emit("Medication deleted")
            } else {
                _userMessage.emit("Failed to delete medication")
            }
        }
    }

    fun markTaken(medication: MedicationEntity) {
        viewModelScope.launch {
            val time = medication.scheduledTimes.firstOrNull() ?: "12:00 PM"
            val result = repository.recordTaken(medication.id, time)
            if (result.isSuccess) {
                _userMessage.emit("${medication.medicineName} marked as taken")
            }
        }
    }

    fun markSkipped(medication: MedicationEntity) {
        viewModelScope.launch {
            val time = medication.scheduledTimes.firstOrNull() ?: "12:00 PM"
            val result = repository.recordSkipped(medication.id, time)
            if (result.isSuccess) {
                _userMessage.emit("${medication.medicineName} marked as skipped")
            }
        }
    }

    fun addMedication(
        name: String,
        dosage: String,
        unit: String,
        frequency: String,
        scheduledTimes: List<String>,
        startDate: Long,
        endDate: Long?,
        instructions: String,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            val entity = MedicationEntity(
                userId = repository.getCurrentUserId(),
                medicineName = name,
                dosage = dosage,
                dosageUnit = unit,
                frequency = frequency,
                scheduledTimes = scheduledTimes,
                startDate = startDate,
                endDate = endDate,
                instructions = instructions,
                active = true
            )
            val result = repository.addMedication(entity)
            if (result.isSuccess) {
                _userMessage.emit("Medication added successfully")
                onComplete()
            } else {
                _userMessage.emit(result.exceptionOrNull()?.message ?: "Failed to add medication")
            }
        }
    }
}
