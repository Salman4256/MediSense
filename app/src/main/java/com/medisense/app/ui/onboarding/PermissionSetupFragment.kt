package com.medisense.app.ui.onboarding

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.medisense.app.R
import com.medisense.app.databinding.FragmentPermissionSetupBinding
import com.medisense.app.domain.security.SecureLogger
import com.medisense.app.utils.PermissionHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.LinkedList
import java.util.Queue

@AndroidEntryPoint
class PermissionSetupFragment : Fragment() {

    private var _binding: FragmentPermissionSetupBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PermissionSetupViewModel by viewModels()

    private enum class PermissionStep {
        NOTIFICATION,
        CAMERA,
        MICROPHONE,
        EXACT_ALARM
    }

    private val sequentialQueue: Queue<PermissionStep> = LinkedList()
    private var isSequentialRunning = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        SecureLogger.d("PermissionSetup", "Notification permission granted: $isGranted")
        context?.let { viewModel.refreshPermissionState(it) }
        advanceSequentialRequest()
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        SecureLogger.d("PermissionSetup", "Camera permission granted: $isGranted")
        context?.let { viewModel.refreshPermissionState(it) }
        advanceSequentialRequest()
    }

    private val microphonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        SecureLogger.d("PermissionSetup", "Microphone permission granted: $isGranted")
        context?.let { viewModel.refreshPermissionState(it) }
        advanceSequentialRequest()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPermissionSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
        observeUiState()
    }

    override fun onResume() {
        super.onResume()
        context?.let { viewModel.refreshPermissionState(it) }
    }

    private fun setupListeners() {
        // Individual Card Clicks
        binding.cardNotificationPermission.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !PermissionHelper.hasNotificationPermission(requireContext())
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else if (!PermissionHelper.hasNotificationPermission(requireContext())) {
                PermissionHelper.openNotificationSettings(requireContext())
            }
        }

        binding.cardCameraPermission.setOnClickListener {
            if (!PermissionHelper.hasCameraPermission(requireContext())) {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        binding.cardMicrophonePermission.setOnClickListener {
            if (!PermissionHelper.hasRecordAudioPermission(requireContext())) {
                microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        binding.cardExactAlarmsPermission.setOnClickListener {
            if (!PermissionHelper.canScheduleExactAlarms(requireContext())) {
                PermissionHelper.openExactAlarmSettings(requireContext())
            }
        }

        // Primary Action Button
        binding.btnPrimaryAction.setOnClickListener {
            val state = viewModel.uiState.value
            if (state.areAllApplicablePermissionsGranted || !hasAnyUngrantedPermissions()) {
                finishOnboardingAndProceed()
            } else {
                startSequentialPermissionRequest()
            }
        }

        // Open Settings Button (Visible when any permission is denied)
        binding.btnOpenSettings.setOnClickListener {
            PermissionHelper.openAppSettings(requireContext())
        }

        // Skip / Continue Button
        binding.btnSkip.setOnClickListener {
            finishOnboardingAndProceed()
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderUi(state)
                }
            }
        }
    }

    private fun renderUi(state: PermissionUiState) {
        // 1. Notifications Card
        if (state.isNotificationsApplicable) {
            binding.cardNotificationPermission.visibility = View.VISIBLE
            if (state.hasNotificationPermission) {
                binding.tvNotificationStatus.text = "Granted"
                binding.tvNotificationStatus.setBackgroundResource(R.drawable.bg_badge_positive)
                binding.tvNotificationStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
            } else {
                binding.tvNotificationStatus.text = "Grant"
                binding.tvNotificationStatus.setBackgroundResource(R.drawable.bg_badge_info)
                binding.tvNotificationStatus.setTextColor(resources.getColor(android.R.color.holo_blue_dark, null))
            }
        } else {
            // Android 11/12: granted by system
            binding.cardNotificationPermission.visibility = View.VISIBLE
            binding.tvNotificationStatus.text = "Active"
            binding.tvNotificationStatus.setBackgroundResource(R.drawable.bg_badge_positive)
            binding.tvNotificationStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
        }

        // 2. Camera Card
        if (state.hasCameraPermission) {
            binding.tvCameraStatus.text = "Granted"
            binding.tvCameraStatus.setBackgroundResource(R.drawable.bg_badge_positive)
            binding.tvCameraStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
        } else {
            binding.tvCameraStatus.text = "Grant"
            binding.tvCameraStatus.setBackgroundResource(R.drawable.bg_badge_info)
            binding.tvCameraStatus.setTextColor(resources.getColor(android.R.color.holo_blue_dark, null))
        }

        // 3. Microphone Card
        if (state.hasMicrophonePermission) {
            binding.tvMicrophoneStatus.text = "Granted"
            binding.tvMicrophoneStatus.setBackgroundResource(R.drawable.bg_badge_positive)
            binding.tvMicrophoneStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
        } else {
            binding.tvMicrophoneStatus.text = "Grant"
            binding.tvMicrophoneStatus.setBackgroundResource(R.drawable.bg_badge_info)
            binding.tvMicrophoneStatus.setTextColor(resources.getColor(android.R.color.holo_blue_dark, null))
        }

        // 4. Exact Alarms Card
        if (state.isExactAlarmApplicable) {
            binding.cardExactAlarmsPermission.visibility = View.VISIBLE
            if (state.hasExactAlarmPermission) {
                binding.tvExactAlarmsStatus.text = "Active"
                binding.tvExactAlarmsStatus.setBackgroundResource(R.drawable.bg_badge_positive)
                binding.tvExactAlarmsStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
            } else {
                binding.tvExactAlarmsStatus.text = "Settings"
                binding.tvExactAlarmsStatus.setBackgroundResource(R.drawable.bg_badge_attention)
                binding.tvExactAlarmsStatus.setTextColor(resources.getColor(android.R.color.holo_orange_dark, null))
            }
        } else {
            binding.cardExactAlarmsPermission.visibility = View.VISIBLE
            binding.tvExactAlarmsStatus.text = "Active"
            binding.tvExactAlarmsStatus.setBackgroundResource(R.drawable.bg_badge_positive)
            binding.tvExactAlarmsStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
        }

        // 5. Button and Limitation Banners
        if (state.areAllApplicablePermissionsGranted) {
            binding.cardLimitationNotice.visibility = View.GONE
            binding.btnOpenSettings.visibility = View.GONE
            binding.btnPrimaryAction.text = "Continue to MediSense"
            binding.btnSkip.visibility = View.GONE
        } else {
            if (state.anyPermissionDenied && !isSequentialRunning) {
                binding.cardLimitationNotice.visibility = View.VISIBLE
                binding.btnOpenSettings.visibility = View.VISIBLE
                binding.btnPrimaryAction.text = "Continue to MediSense"
                binding.btnSkip.text = "Grant Permissions Again"
                binding.btnSkip.visibility = View.VISIBLE
            } else {
                binding.cardLimitationNotice.visibility = View.GONE
                binding.btnOpenSettings.visibility = View.GONE
                binding.btnPrimaryAction.text = "Grant Permissions"
                binding.btnSkip.visibility = View.VISIBLE
            }
        }
    }

    private fun hasAnyUngrantedPermissions(): Boolean {
        val ctx = context ?: return false
        val needNotif = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !PermissionHelper.hasNotificationPermission(ctx)
        val needCam = !PermissionHelper.hasCameraPermission(ctx)
        val needMic = !PermissionHelper.hasRecordAudioPermission(ctx)
        val needAlarm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !PermissionHelper.canScheduleExactAlarms(ctx)
        return needNotif || needCam || needMic || needAlarm
    }

    private fun startSequentialPermissionRequest() {
        val ctx = context ?: return
        sequentialQueue.clear()

        // 1. Notifications (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !PermissionHelper.hasNotificationPermission(ctx)
        ) {
            sequentialQueue.add(PermissionStep.NOTIFICATION)
        }

        // 2. Camera
        if (!PermissionHelper.hasCameraPermission(ctx)) {
            sequentialQueue.add(PermissionStep.CAMERA)
        }

        // 3. Microphone
        if (!PermissionHelper.hasRecordAudioPermission(ctx)) {
            sequentialQueue.add(PermissionStep.MICROPHONE)
        }

        // 4. Exact Alarm (Special Access)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !PermissionHelper.canScheduleExactAlarms(ctx)
        ) {
            sequentialQueue.add(PermissionStep.EXACT_ALARM)
        }

        if (sequentialQueue.isEmpty()) {
            finishOnboardingAndProceed()
            return
        }

        isSequentialRunning = true
        advanceSequentialRequest()
    }

    private fun advanceSequentialRequest() {
        if (sequentialQueue.isEmpty()) {
            isSequentialRunning = false
            context?.let { viewModel.refreshPermissionState(it) }
            return
        }

        when (sequentialQueue.poll()) {
            PermissionStep.NOTIFICATION -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    advanceSequentialRequest()
                }
            }
            PermissionStep.CAMERA -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            PermissionStep.MICROPHONE -> {
                microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            PermissionStep.EXACT_ALARM -> {
                showExactAlarmExplanationDialog()
            }
            null -> {
                isSequentialRunning = false
                context?.let { viewModel.refreshPermissionState(it) }
            }
        }
    }

    private fun showExactAlarmExplanationDialog() {
        val ctx = context ?: return
        MaterialAlertDialogBuilder(ctx)
            .setTitle("Exact Alarms for Reminders")
            .setMessage("MediSense uses exact alarms to alert you precisely when your scheduled medications are due. You can enable this permission in system settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                PermissionHelper.openExactAlarmSettings(ctx)
                advanceSequentialRequest()
            }
            .setNegativeButton("Continue") { _, _ ->
                advanceSequentialRequest()
            }
            .setOnDismissListener {
                isSequentialRunning = false
                context?.let { viewModel.refreshPermissionState(it) }
            }
            .show()
    }

    private fun finishOnboardingAndProceed() {
        viewModel.completeOnboarding()
        if (viewModel.isUserLoggedIn()) {
            findNavController().navigate(R.id.action_permissionSetupFragment_to_dashboardFragment)
        } else {
            findNavController().navigate(R.id.action_permissionSetupFragment_to_loginFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
