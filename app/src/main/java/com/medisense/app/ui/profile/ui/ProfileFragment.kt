package com.medisense.app.ui.profile.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.medisense.app.R
import com.medisense.app.databinding.FragmentProfileBinding
import com.medisense.app.ui.profile.viewmodel.ProfileViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupButtons()
        observeUiState()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupButtons() {
        binding.btnEditProfile.setOnClickListener {
            // Navigate to Personal Health Records for editing
            findNavController().navigate(R.id.action_profileFragment_to_adaptiveHealthInsightsFragment)
        }

        binding.btnHealthReport.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_healthReportFragment)
        }

        binding.btnHealthDataQuality.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_healthDataQualityFragment)
        }

        binding.btnPrivacySecurity.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_privacySecurityFragment)
        }

        binding.btnSupportInfo.setOnClickListener {
            showSupportOptionsDialog()
        }

        binding.btnLogout.setOnClickListener {
            showLogoutConfirmationDialog()
        }
    }

    private fun showSupportOptionsDialog() {
        val options = arrayOf(
            "Contact Support (${com.medisense.app.domain.support.MediSenseSupportConstants.MEDISENSE_SUPPORT_EMAIL})",
            "Official Website (${com.medisense.app.domain.support.MediSenseSupportConstants.MEDISENSE_WEBSITE_DOMAIN})",
            "View Privacy & Security Settings"
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("MediSense Support & Information")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> com.medisense.app.utils.SupportIntentHelper.openSupportEmail(requireContext())
                    1 -> com.medisense.app.utils.SupportIntentHelper.openOfficialWebsite(requireContext())
                    2 -> findNavController().navigate(R.id.action_profileFragment_to_privacySecurityFragment)
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showLogoutConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout from MediSense?")
            .setPositiveButton("Logout") { _, _ ->
                viewModel.logout()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // Avatar letter
                    binding.tvAvatarLetter.text = state.avatarLetter

                    // Name and email
                    binding.tvFullName.text = state.displayName.ifEmpty { "MediSense User" }
                    binding.tvEmail.text = state.email

                    // User ID (truncated for display)
                    val displayId = if (state.userId.length > 20) {
                        "ID: …${state.userId.takeLast(12)}"
                    } else if (state.userId.isNotEmpty()) {
                        "ID: ${state.userId}"
                    } else {
                        "Account Status: Active"
                    }
                    binding.tvUserId.text = displayId

                    // Logout success → navigate to login
                    if (state.logoutSuccess) {
                        findNavController().navigate(R.id.action_profileFragment_to_loginFragment)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
