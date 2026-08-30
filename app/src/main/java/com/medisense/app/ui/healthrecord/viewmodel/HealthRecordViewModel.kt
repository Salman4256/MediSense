package com.medisense.app.ui.healthrecord.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medisense.app.data.local.entity.HealthProfileEntity
import com.medisense.app.data.repository.HealthProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

sealed class HealthRecordUiState {
    object Idle : HealthRecordUiState()
    object Loading : HealthRecordUiState()
    data class Success(val message: String) : HealthRecordUiState()
    data class Error(val message: String) : HealthRecordUiState()
    data class ProfileLoaded(val profile: HealthProfileEntity) : HealthRecordUiState()
}

@HiltViewModel
class HealthRecordViewModel @Inject constructor(
    private val repository: HealthProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HealthRecordUiState>(HealthRecordUiState.Idle)
    val uiState: StateFlow<HealthRecordUiState> = _uiState.asStateFlow()

    private val _currentProfile = MutableStateFlow<HealthProfileEntity?>(null)
    val currentProfile: StateFlow<HealthProfileEntity?> = _currentProfile.asStateFlow()

    fun loadProfile() {
        val userId = repository.getCurrentUserId()
        if (userId == null) {
            _uiState.value = HealthRecordUiState.Error("User not logged in")
            return
        }

        _uiState.value = HealthRecordUiState.Loading
        viewModelScope.launch {
            repository.loadProfile(userId)
                .onSuccess { profile ->
                    _currentProfile.value = profile
                    if (profile != null) {
                        _uiState.value = HealthRecordUiState.ProfileLoaded(profile)
                    } else {
                        _uiState.value = HealthRecordUiState.Idle
                    }
                }
                .onFailure { error ->
                    _uiState.value = HealthRecordUiState.Error(error.message ?: "Failed to load profile")
                }
        }
    }

    fun saveOrUpdateProfile(
        fullName: String,
        dob: String,
        gender: String,
        bloodGroup: String,
        heightStr: String,
        weightStr: String,
        allergies: String,
        diseases: String,
        medications: String,
        familyHistory: String,
        emergencyName: String,
        emergencyPhone: String,
        notes: String
    ) {
        val userId = repository.getCurrentUserId()
        if (userId == null) {
            _uiState.value = HealthRecordUiState.Error("User not logged in")
            return
        }

        // 1. Validation
        val validationError = validateInput(fullName, dob, heightStr, weightStr, emergencyPhone)
        if (validationError != null) {
            _uiState.value = HealthRecordUiState.Error(validationError)
            return
        }

        _uiState.value = HealthRecordUiState.Loading
        viewModelScope.launch {
            val existing = _currentProfile.value
            val id = existing?.id ?: UUID.randomUUID().toString()
            val now = LocalDate.now().toString()
            val createdAt = existing?.createdAt ?: now

            val profile = HealthProfileEntity(
                id = id,
                userId = userId,
                fullName = fullName,
                dateOfBirth = dob,
                gender = gender,
                bloodGroup = bloodGroup,
                height = heightStr.toDoubleOrNull(),
                weight = weightStr.toDoubleOrNull(),
                allergies = allergies,
                existingDiseases = diseases,
                currentMedications = medications,
                familyHistory = familyHistory,
                emergencyContactName = emergencyName,
                emergencyContactNumber = emergencyPhone,
                notes = notes,
                createdAt = createdAt,
                updatedAt = now
            )

            repository.saveProfile(profile)
                .onSuccess {
                    _currentProfile.value = profile
                    _uiState.value = HealthRecordUiState.Success("Profile saved successfully!")
                }
                .onFailure { error ->
                    _uiState.value = HealthRecordUiState.Error(error.message ?: "Failed to save profile")
                }
        }
    }

    private fun validateInput(
        fullName: String,
        dob: String,
        heightStr: String,
        weightStr: String,
        emergencyPhone: String
    ): String? {
        if (fullName.isBlank()) return "Full name is required"
        if (dob.isBlank()) return "Date of birth is required"

        try {
            val parsedDob = LocalDate.parse(dob)
            if (parsedDob.isAfter(LocalDate.now())) {
                return "Date of birth cannot be in the future"
            }
        } catch (e: Exception) {
            return "Invalid date of birth format"
        }

        val height = heightStr.toDoubleOrNull()
        if (height == null || height <= 0) return "Height must be greater than 0"

        val weight = weightStr.toDoubleOrNull()
        if (weight == null || weight <= 0) return "Weight must be greater than 0"

        if (emergencyPhone.isBlank()) return "Emergency contact number is required"
        if (!android.util.Patterns.PHONE.matcher(emergencyPhone).matches()) {
            return "Invalid emergency contact phone number format"
        }

        return null
    }

    fun resetState() {
        _uiState.value = HealthRecordUiState.Idle
    }
}
