package com.medisense.app.ui.privacy.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.medisense.app.R
import com.medisense.app.databinding.FragmentPrivacySecurityBinding
import com.medisense.app.ui.privacy.adapter.SecurityAuditAdapter
import com.medisense.app.ui.privacy.viewmodel.PrivacySecurityViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PrivacySecurityFragment : Fragment() {

    private var _binding: FragmentPrivacySecurityBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PrivacySecurityViewModel by viewModels()
    private val auditAdapter = SecurityAuditAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPrivacySecurityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        setupActionListeners()
        observeUiState()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupRecyclerView() {
        binding.rvAuditEvents.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = auditAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupActionListeners() {
        binding.btnSignOut.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Sign Out")
                .setMessage("Are you sure you want to sign out of your MediSense session?")
                .setPositiveButton("Sign Out") { _, _ ->
                    viewModel.logout {
                        findNavController().navigate(R.id.loginFragment)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnClearLocalData.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Clear Local Health Data?")
                .setMessage("This will remove all locally stored health records, prediction history, medications, appointments, and conversation logs from this device. Are you sure?")
                .setPositiveButton("Clear Data") { _, _ ->
                    viewModel.clearLocalHealthData()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnClearAuditHistory.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Clear Audit Log")
                .setMessage("Are you sure you want to clear your local security and privacy audit trail?")
                .setPositiveButton("Clear") { _, _ ->
                    viewModel.clearAuditHistory()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // 1. Account details
                    binding.tvSignedInEmail.text = state.userEmail
                    binding.tvUserUuid.text = "Canonical Auth UUID: ${state.userId}"

                    // 2. Storage & AI transmission
                    binding.tvLocalStorageInfo.text = state.governanceInfo.LOCAL_STORAGE_EXPLANATION
                    binding.tvCloudStorageInfo.text = state.governanceInfo.CLOUD_STORAGE_EXPLANATION
                    binding.tvAiStorageInfo.text = state.governanceInfo.AI_DATA_EXPLANATION
                    binding.tvLocalClearInfo.text = state.governanceInfo.LOCAL_DATA_CLEARING_NOTICE
                    binding.tvDisclaimer.text = state.governanceInfo.HEALTHCARE_DISCLAIMER

                    // 3. Audit events
                    auditAdapter.submitList(state.auditEvents)
                    binding.tvEmptyAudit.isVisible = state.auditEvents.isEmpty()
                    binding.rvAuditEvents.isVisible = state.auditEvents.isNotEmpty()

                    // 4. Loading & Action messages
                    binding.btnClearLocalData.isEnabled = !state.isClearingData
                    state.actionSuccessMessage?.let { msg ->
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                        viewModel.clearMessages()
                    }
                    state.actionErrorMessage?.let { err ->
                        Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show()
                        viewModel.clearMessages()
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
