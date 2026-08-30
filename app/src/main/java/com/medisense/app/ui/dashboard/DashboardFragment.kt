package com.medisense.app.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.medisense.app.R
import com.medisense.app.databinding.FragmentDashboardBinding
import com.medisense.app.ui.auth.viewmodel.AuthState
import com.medisense.app.ui.auth.viewmodel.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupToolbar()
        setupHealthRecordsButton()
        setupDiseasePredictionButton()
        setupAiAssistantButton()
        setupMedicationRemindersButton()
        setupProfileNavigation()
        observeAuthState()
        
        // Setup Placeholder Email
        binding.tvWelcome.text = "Welcome to MediSense"
        authViewModel.checkSession()
    }

    private fun observeAuthState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.authState.collect { state ->
                    when (state) {
                        is AuthState.Unauthenticated -> {
                            findNavController().navigate(R.id.action_dashboardFragment_to_loginFragment)
                        }
                        is AuthState.Error -> {
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                            authViewModel.resetState()
                        }
                        else -> {
                            // other states ignored for dashboard
                        }
                    }
                }
            }
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_profile -> {
                    findNavController().navigate(R.id.action_dashboardFragment_to_profileFragment)
                    true
                }
                R.id.action_logout -> {
                    authViewModel.logout()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupProfileNavigation() {
        binding.tvWelcome.setOnClickListener {
            findNavController().navigate(R.id.action_dashboardFragment_to_profileFragment)
        }
    }

    private fun setupHealthRecordsButton() {
        binding.btnHealthRecords.setOnClickListener {
            findNavController().navigate(R.id.action_dashboardFragment_to_healthRecordFragment)
        }
    }

    private fun setupDiseasePredictionButton() {
        binding.btnDiseasePrediction.setOnClickListener {
            findNavController().navigate(R.id.action_dashboardFragment_to_predictionFragment)
        }
    }

    private fun setupAiAssistantButton() {
        binding.btnAiAssistant.setOnClickListener {
            findNavController().navigate(R.id.action_dashboardFragment_to_aiAssistantFragment)
        }
    }

    private fun setupMedicationRemindersButton() {
        binding.btnMedicationReminders.setOnClickListener {
            findNavController().navigate(R.id.action_dashboardFragment_to_medicationListFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
