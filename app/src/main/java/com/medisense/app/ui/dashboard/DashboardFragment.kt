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
import com.medisense.app.domain.model.PersonalHealthContext
import com.medisense.app.ui.auth.viewmodel.AuthState
import com.medisense.app.ui.auth.viewmodel.AuthViewModel
import com.medisense.app.ui.personalization.PersonalHealthContextUiState
import com.medisense.app.ui.personalization.PersonalHealthContextViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val authViewModel: AuthViewModel by viewModels()
    private val healthContextViewModel: PersonalHealthContextViewModel by viewModels()

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
        setupPredictionHistoryButton()
        setupHealthTrendsButton()
        setupAiAssistantButton()
        setupMedicationRemindersButton()
        setupAppointmentsButton()
        setupProfileNavigation()
        setupContextCardNavigation()
        observeAuthState()
        observeHealthContext()
        
        binding.tvWelcome.text = "Welcome to MediSense"
        authViewModel.checkSession()
        healthContextViewModel.loadPersonalHealthContext()
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

    private fun observeHealthContext() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                healthContextViewModel.uiState.collect { state ->
                    when (state) {
                        is PersonalHealthContextUiState.Loading -> {
                            binding.tvContextSummary.text = "Loading your personal health context..."
                            binding.tvProfileCompletenessBadge.text = "Profile --"
                            binding.layoutContextDetails.visibility = View.GONE
                        }
                        is PersonalHealthContextUiState.Empty -> {
                            binding.tvContextSummary.text = state.message
                            binding.tvProfileCompletenessBadge.text = "Profile 0%"
                            binding.layoutContextDetails.visibility = View.GONE
                            binding.tvWhyPersonalized.visibility = View.GONE
                        }
                        is PersonalHealthContextUiState.Success -> {
                            bindHealthContext(state.context)
                        }
                        is PersonalHealthContextUiState.Error -> {
                            binding.tvContextSummary.text = "Health context could not be loaded."
                            binding.tvProfileCompletenessBadge.text = "Profile --"
                            binding.layoutContextDetails.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    private fun bindHealthContext(context: PersonalHealthContext) {
        // 1. Profile Completeness Badge
        binding.tvProfileCompletenessBadge.text = "Profile ${context.profileCompleteness}%"

        // 2. Dynamic Factual Summary
        binding.tvContextSummary.text = context.generatedSummary
        binding.layoutContextDetails.visibility = View.VISIBLE
        binding.tvWhyPersonalized.visibility = View.VISIBLE
        binding.tvWhyPersonalized.text = "Why personalized: ${context.whyPersonalized}"

        // 3. Symptoms & Predictions Item
        if (context.predictions.recentCount > 0 && context.predictions.frequentSymptoms.isNotEmpty()) {
            binding.layoutSymptomsItem.visibility = View.VISIBLE
            val symptomsStr = context.predictions.frequentSymptoms.joinToString(" • ")
            binding.tvContextSymptoms.text = "Frequent: $symptomsStr (${context.predictions.recentCount} recent record${if (context.predictions.recentCount > 1) "s" else ""})"
        } else if (context.predictions.totalCount > 0) {
            binding.layoutSymptomsItem.visibility = View.VISIBLE
            binding.tvContextSymptoms.text = "${context.predictions.totalCount} prediction record${if (context.predictions.totalCount > 1) "s" else ""} archived"
        } else {
            binding.layoutSymptomsItem.visibility = View.GONE
        }

        // 4. Medications & Adherence Item
        if (!context.medications.adherenceSummary.isNullOrBlank()) {
            binding.layoutMedicationItem.visibility = View.VISIBLE
            binding.tvContextMedications.text = "Medications: ${context.medications.adherenceSummary}"
        } else if (context.medications.activeCount > 0) {
            binding.layoutMedicationItem.visibility = View.VISIBLE
            binding.tvContextMedications.text = "${context.medications.activeCount} active medication${if (context.medications.activeCount > 1) "s" else ""} scheduled"
        } else {
            binding.layoutMedicationItem.visibility = View.GONE
        }

        // 5. Appointments Item
        if (!context.appointments.nextAppointmentDoctor.isNullOrBlank() && !context.appointments.nextAppointmentDate.isNullOrBlank()) {
            binding.layoutAppointmentItem.visibility = View.VISIBLE
            binding.tvContextAppointments.text = "Upcoming: ${context.appointments.nextAppointmentDoctor} (${context.appointments.nextAppointmentDate})"
        } else if (context.appointments.upcomingCount > 0) {
            binding.layoutAppointmentItem.visibility = View.VISIBLE
            binding.tvContextAppointments.text = "${context.appointments.upcomingCount} upcoming doctor appointment${if (context.appointments.upcomingCount > 1) "s" else ""}"
        } else {
            binding.layoutAppointmentItem.visibility = View.GONE
        }
    }

    private fun setupContextCardNavigation() {
        binding.cardPersonalContext.setOnClickListener {
            findNavController().navigate(R.id.action_dashboardFragment_to_longitudinalHealthFragment)
        }
    }

    private fun setupHealthTrendsButton() {
        binding.btnHealthTrends.setOnClickListener {
            findNavController().navigate(R.id.action_dashboardFragment_to_longitudinalHealthFragment)
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
            findNavController().navigate(R.id.action_dashboardFragment_to_diseasePredictionFragment)
        }
    }

    private fun setupPredictionHistoryButton() {
        binding.btnPredictionHistory.setOnClickListener {
            findNavController().navigate(R.id.action_dashboardFragment_to_predictionHistoryFragment)
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

    private fun setupAppointmentsButton() {
        binding.btnAppointments.setOnClickListener {
            findNavController().navigate(R.id.action_dashboardFragment_to_appointmentFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
