package com.medisense.app.ui.onboarding

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import com.medisense.app.data.local.session.SharedPreferencesSessionManager
import com.medisense.app.data.remote.supabase.AuthService
import com.medisense.app.domain.security.SecureLogger
import com.medisense.app.utils.PermissionHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class PermissionUiState(
    val hasNotificationPermission: Boolean = false,
    val hasCameraPermission: Boolean = false,
    val hasMicrophonePermission: Boolean = false,
    val hasExactAlarmPermission: Boolean = true,
    val isNotificationsApplicable: Boolean = true,
    val isExactAlarmApplicable: Boolean = true,
    val anyPermissionDenied: Boolean = false
) {
    val areAllApplicablePermissionsGranted: Boolean
        get() {
            val notifOk = !isNotificationsApplicable || hasNotificationPermission
            val alarmOk = !isExactAlarmApplicable || hasExactAlarmPermission
            return notifOk && hasCameraPermission && hasMicrophonePermission && alarmOk
        }
}

@HiltViewModel
class PermissionSetupViewModel @Inject constructor(
    private val sessionManager: SharedPreferencesSessionManager,
    private val authService: AuthService
) : ViewModel() {

    private val _uiState = MutableStateFlow(PermissionUiState())
    val uiState: StateFlow<PermissionUiState> = _uiState.asStateFlow()

    fun isUserLoggedIn(): Boolean {
        return authService.isUserLoggedIn() || sessionManager.isUserLoggedIn()
    }

    fun hasCompletedPermissionSetup(): Boolean {
        return sessionManager.hasCompletedPermissionSetup()
    }

    fun completeOnboarding() {
        sessionManager.setCompletedPermissionSetup(true)
        sessionManager.setCompletedOnboarding(true)
        SecureLogger.d("PermissionSetup", "Permission onboarding marked completed")
    }

    fun refreshPermissionState(context: Context) {
        val isNotifApplicable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val isExactAlarmApplicable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

        val hasNotif = PermissionHelper.hasNotificationPermission(context)
        val hasCam = PermissionHelper.hasCameraPermission(context)
        val hasMic = PermissionHelper.hasRecordAudioPermission(context)
        val hasAlarm = PermissionHelper.canScheduleExactAlarms(context)

        val anyDenied = (!hasNotif && isNotifApplicable) || !hasCam || !hasMic || (!hasAlarm && isExactAlarmApplicable)

        _uiState.value = PermissionUiState(
            hasNotificationPermission = hasNotif,
            hasCameraPermission = hasCam,
            hasMicrophonePermission = hasMic,
            hasExactAlarmPermission = hasAlarm,
            isNotificationsApplicable = isNotifApplicable,
            isExactAlarmApplicable = isExactAlarmApplicable,
            anyPermissionDenied = anyDenied
        )
    }
}
