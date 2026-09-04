package com.medisense.app.ui.appointment.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medisense.app.data.local.entity.AppointmentEntity
import com.medisense.app.data.repository.AppointmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AppointmentFilter {
    ALL,
    UPCOMING,
    COMPLETED,
    CANCELLED
}

@HiltViewModel
class AppointmentViewModel @Inject constructor(
    private val repository: AppointmentRepository
) : ViewModel() {

    private val _appointments = MutableStateFlow<List<AppointmentEntity>>(emptyList())
    val appointments: StateFlow<List<AppointmentEntity>> = _appointments.asStateFlow()

    private val _currentFilter = MutableStateFlow(AppointmentFilter.ALL)
    val currentFilter: StateFlow<AppointmentFilter> = _currentFilter.asStateFlow()

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    private var filterJob: Job? = null

    init {
        setFilter(AppointmentFilter.ALL)
    }

    fun setFilter(filter: AppointmentFilter) {
        _currentFilter.value = filter
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            val flow = when (filter) {
                AppointmentFilter.ALL -> repository.observeAppointments()
                AppointmentFilter.UPCOMING -> repository.observeUpcomingAppointments()
                AppointmentFilter.COMPLETED -> repository.observeCompletedAppointments()
                AppointmentFilter.CANCELLED -> repository.observeCancelledAppointments()
            }
            flow.catch { e -> _userMessage.emit(e.message ?: "Failed to load appointments") }
                .collect { list ->
                    _appointments.value = list
                }
        }
    }

    fun markCompleted(appointmentId: Long) {
        viewModelScope.launch {
            val result = repository.markAppointmentCompleted(appointmentId)
            if (result.isSuccess) {
                _userMessage.emit("Appointment marked as completed")
            } else {
                _userMessage.emit(result.exceptionOrNull()?.message ?: "Failed to update appointment")
            }
        }
    }

    fun cancelAppointment(appointmentId: Long) {
        viewModelScope.launch {
            val result = repository.cancelAppointment(appointmentId)
            if (result.isSuccess) {
                _userMessage.emit("Appointment cancelled")
            } else {
                _userMessage.emit(result.exceptionOrNull()?.message ?: "Failed to cancel appointment")
            }
        }
    }

    fun deleteAppointment(appointmentId: Long) {
        viewModelScope.launch {
            val result = repository.deleteAppointmentById(appointmentId)
            if (result.isSuccess) {
                _userMessage.emit("Appointment deleted")
            } else {
                _userMessage.emit(result.exceptionOrNull()?.message ?: "Failed to delete appointment")
            }
        }
    }

    fun addAppointment(
        doctorName: String,
        clinicName: String,
        appointmentType: String,
        appointmentDate: String,
        appointmentTime: String,
        appointmentTimestamp: Long,
        reminderMinutesBefore: Int,
        notes: String?,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.addAppointment(
                doctorName = doctorName,
                clinicName = clinicName,
                appointmentType = appointmentType,
                appointmentDate = appointmentDate,
                appointmentTime = appointmentTime,
                appointmentTimestamp = appointmentTimestamp,
                reminderMinutesBefore = reminderMinutesBefore,
                notes = notes
            )
            if (result.isSuccess) {
                _userMessage.emit("Appointment scheduled successfully")
                onSuccess()
            } else {
                _userMessage.emit(result.exceptionOrNull()?.message ?: "Failed to schedule appointment")
            }
        }
    }
}
