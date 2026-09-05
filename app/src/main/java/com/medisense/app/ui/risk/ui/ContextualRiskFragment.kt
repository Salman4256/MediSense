package com.medisense.app.ui.risk.ui

import android.content.res.ColorStateList
import android.graphics.Color
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
import com.medisense.app.databinding.FragmentContextualRiskBinding
import com.medisense.app.domain.model.ContextualRiskAssessment
import com.medisense.app.domain.model.ContextualRiskLevel
import com.medisense.app.ui.risk.adapter.ContextualRiskFactorAdapter
import com.medisense.app.ui.risk.viewmodel.ContextualRiskUiState
import com.medisense.app.ui.risk.viewmodel.ContextualRiskViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ContextualRiskFragment : Fragment() {

    private var _binding: FragmentContextualRiskBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ContextualRiskViewModel by viewModels()
    private val factorAdapter by lazy { ContextualRiskFactorAdapter() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentContextualRiskBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        observeUiState()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupRecyclerView() {
        binding.rvContributingFactors.layoutManager = LinearLayoutManager(requireContext())
        binding.rvContributingFactors.adapter = factorAdapter
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is ContextualRiskUiState.Loading -> {
                            binding.progressBar.isVisible = true
                            binding.scrollView.isVisible = false
                        }
                        is ContextualRiskUiState.Success -> {
                            binding.progressBar.isVisible = false
                            binding.scrollView.isVisible = true
                            renderAssessment(state.assessment)
                        }
                        is ContextualRiskUiState.InsufficientData -> {
                            binding.progressBar.isVisible = false
                            binding.scrollView.isVisible = true
                            renderInsufficientData(state.assessment)
                        }
                        is ContextualRiskUiState.Error -> {
                            binding.progressBar.isVisible = false
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun renderAssessment(assessment: ContextualRiskAssessment) {
        val score = assessment.overallScore ?: 0
        binding.tvScoreValue.text = score.toString()
        binding.tvScoreMax.isVisible = true
        binding.progressScoreIndicator.isVisible = true
        binding.progressScoreIndicator.progress = score

        binding.chipRiskLevel.text = assessment.riskLevel.label
        binding.tvLevelDescription.text = assessment.riskLevel.description
        binding.tvAssessmentSummary.text = assessment.generatedSummary

        when (assessment.riskLevel) {
            ContextualRiskLevel.LOW -> {
                binding.tvScoreValue.setTextColor(Color.parseColor("#2E7D32"))
                binding.chipRiskLevel.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#332E7D32"))
                binding.chipRiskLevel.setTextColor(Color.parseColor("#2E7D32"))
                binding.progressScoreIndicator.setIndicatorColor(Color.parseColor("#2E7D32"))
            }
            ContextualRiskLevel.MODERATE -> {
                binding.tvScoreValue.setTextColor(Color.parseColor("#F57C00"))
                binding.chipRiskLevel.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#33F57C00"))
                binding.chipRiskLevel.setTextColor(Color.parseColor("#F57C00"))
                binding.progressScoreIndicator.setIndicatorColor(Color.parseColor("#F57C00"))
            }
            ContextualRiskLevel.HIGH -> {
                binding.tvScoreValue.setTextColor(Color.parseColor("#C62828"))
                binding.chipRiskLevel.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#33C62828"))
                binding.chipRiskLevel.setTextColor(Color.parseColor("#C62828"))
                binding.progressScoreIndicator.setIndicatorColor(Color.parseColor("#C62828"))
            }
            ContextualRiskLevel.INSUFFICIENT_DATA -> {}
        }

        factorAdapter.submitList(assessment.contributingFactors)
        renderDataAvailability(assessment.dataAvailabilitySummary)
    }

    private fun renderInsufficientData(assessment: ContextualRiskAssessment) {
        binding.tvScoreValue.text = "--"
        binding.tvScoreMax.isVisible = false
        binding.progressScoreIndicator.isVisible = false

        binding.chipRiskLevel.text = "Insufficient Data"
        binding.chipRiskLevel.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#33757575"))
        binding.chipRiskLevel.setTextColor(Color.parseColor("#757575"))

        binding.tvLevelDescription.text = "Not enough health history is available to calculate a meaningful contextual score."
        binding.tvAssessmentSummary.text = assessment.generatedSummary

        factorAdapter.submitList(assessment.contributingFactors)
        renderDataAvailability(assessment.dataAvailabilitySummary)
    }

    private fun renderDataAvailability(availability: Map<String, Boolean>) {
        binding.chipAvailProfile.text = "Profile: ${if (availability["Health Profile"] == true) "Available" else "Missing"}"
        binding.chipAvailPredictions.text = "Predictions: ${if (availability["Prediction History"] == true) "Available" else "No Data"}"
        binding.chipAvailMedications.text = "Medications: ${if (availability["Medication Regimen"] == true) "Available" else "No Data"}"
        binding.chipAvailAppointments.text = "Appointments: ${if (availability["Appointments"] == true) "Available" else "No Data"}"
        binding.chipAvailLongitudinal.text = "Longitudinal: ${if (availability["Longitudinal Trends"] == true) "Available" else "No Data"}"
        binding.chipAvailRchr.text = "RCHR: ${if (availability["Composite Health (RCHR)"] == true) "Available" else "No Data"}"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
