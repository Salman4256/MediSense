package com.medisense.app.ui.prediction.ui

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
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.medisense.app.R
import com.medisense.app.data.model.DiseasePrediction
import com.medisense.app.data.model.Symptom
import com.medisense.app.databinding.FragmentPredictionResultBinding
import com.medisense.app.ui.prediction.viewmodel.PredictionResultUiState
import com.medisense.app.ui.prediction.viewmodel.PredictionResultViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

import android.content.res.ColorStateList
import android.graphics.Color
import com.medisense.app.domain.model.ConfidenceLevel
import com.medisense.app.domain.model.ModelSensitivity
import com.medisense.app.ui.prediction.adapter.CounterfactualAdapter

@AndroidEntryPoint
class PredictionResultFragment : Fragment() {

    private var _binding: FragmentPredictionResultBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PredictionResultViewModel by viewModels()
    private lateinit var predictionAdapter: PredictionResultAdapter
    private lateinit var contributionAdapter: FeatureContributionAdapter
    private lateinit var counterfactualAdapter: CounterfactualAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPredictionResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerViews()
        setupListeners()
        observeViewModel()
        loadArguments()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupRecyclerViews() {
        // Predictions Adapter (Secondary conditions)
        predictionAdapter = PredictionResultAdapter()
        binding.rvPredictions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPredictions.adapter = predictionAdapter

        // Contributions Adapter (XAI feature importance bars)
        contributionAdapter = FeatureContributionAdapter()
        binding.rvContributions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvContributions.adapter = contributionAdapter

        // Counterfactual Adapter (What-If Analysis cards)
        counterfactualAdapter = CounterfactualAdapter()
        binding.rvCounterfactuals.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCounterfactuals.adapter = counterfactualAdapter
    }

    @Suppress("DEPRECATION", "UNCHECKED_CAST")
    private fun loadArguments() {
        val predictions = arguments?.getSerializable("predictions") as? ArrayList<DiseasePrediction>
        val symptoms = arguments?.getSerializable("selectedSymptoms") as? ArrayList<Symptom>

        if (!predictions.isNullOrEmpty()) {
            viewModel.loadResults(predictions, symptoms ?: emptyList())
        } else {
            Toast.makeText(requireContext(), "No prediction data found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is PredictionResultUiState.Success -> {
                            bindSuccessState(state)
                        }
                        is PredictionResultUiState.Error -> {
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                        }
                        is PredictionResultUiState.Loading -> {
                            // Loading state
                        }
                        is PredictionResultUiState.Idle -> {
                            // Idle state
                        }
                    }
                }
            }
        }
    }

    private fun bindSuccessState(state: PredictionResultUiState.Success) {
        val primary = state.primaryPrediction
        val explanation = state.explanation
        val counterfactualResult = state.counterfactualResult
        val confidenceSummary = counterfactualResult.confidenceSummary

        // 1. Primary Disease Card
        binding.tvTopDisease.text = primary.diseaseName.split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
        binding.tvTopProbability.text = String.format("%.0f%% Probability", primary.probability * 100)
        binding.tvModelVersion.text = String.format(getString(R.string.xai_model_version), explanation.modelVersion)

        // 2. Module 13: Confidence & Uncertainty Assessment
        binding.chipConfidenceLevel.text = confidenceSummary.confidenceLevel.label
        when (confidenceSummary.confidenceLevel) {
            ConfidenceLevel.HIGH -> {
                binding.chipConfidenceLevel.setTextColor(Color.parseColor("#2E7D32"))
                binding.chipConfidenceLevel.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#222E7D32"))
            }
            ConfidenceLevel.MODERATE -> {
                binding.chipConfidenceLevel.setTextColor(Color.parseColor("#1565C0"))
                binding.chipConfidenceLevel.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#221565C0"))
            }
            ConfidenceLevel.LOW -> {
                binding.chipConfidenceLevel.setTextColor(Color.parseColor("#E65100"))
                binding.chipConfidenceLevel.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#22E65100"))
            }
            ConfidenceLevel.INSUFFICIENT_DATA -> {
                binding.chipConfidenceLevel.setTextColor(Color.parseColor("#757575"))
                binding.chipConfidenceLevel.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#22757575"))
            }
        }
        binding.tvConfidenceInterpretation.text = confidenceSummary.interpretation
        binding.tvConfidenceDisclaimer.text = confidenceSummary.disclaimer

        // 3. XAI Contributing Symptoms & Visual Bars
        if (explanation.isAvailable && explanation.contributions.isNotEmpty()) {
            binding.rvContributions.visibility = View.VISIBLE
            binding.cardExplanationUnavailable.visibility = View.GONE
            contributionAdapter.submitList(explanation.contributions)
        } else {
            binding.rvContributions.visibility = View.GONE
            binding.cardExplanationUnavailable.visibility = View.VISIBLE
        }

        // 4. Explanation Summary
        binding.tvExplanationSummary.text = explanation.summary

        // 5. Module 13: What-If Counterfactual Analysis & Sensitivity
        binding.chipSensitivityLevel.text = counterfactualResult.sensitivity.label
        when (counterfactualResult.sensitivity) {
            ModelSensitivity.STABLE -> {
                binding.chipSensitivityLevel.setTextColor(Color.parseColor("#2E7D32"))
                binding.chipSensitivityLevel.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#222E7D32"))
            }
            ModelSensitivity.SENSITIVE -> {
                binding.chipSensitivityLevel.setTextColor(Color.parseColor("#E65100"))
                binding.chipSensitivityLevel.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#22E65100"))
            }
            ModelSensitivity.INSUFFICIENT_DATA -> {
                binding.chipSensitivityLevel.setTextColor(Color.parseColor("#757575"))
                binding.chipSensitivityLevel.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#22757575"))
            }
        }
        binding.tvSensitivityExplanation.text = counterfactualResult.sensitivityExplanation

        if (counterfactualResult.hasSufficientSymptoms && counterfactualResult.counterfactuals.isNotEmpty()) {
            binding.rvCounterfactuals.visibility = View.VISIBLE
            binding.cardCounterfactualUnavailable.visibility = View.GONE
            counterfactualAdapter.submitList(counterfactualResult.counterfactuals)
        } else {
            binding.rvCounterfactuals.visibility = View.GONE
            binding.cardCounterfactualUnavailable.visibility = View.VISIBLE
            binding.tvCounterfactualUnavailable.text = counterfactualResult.sensitivityExplanation
        }

        // 6. Selected Symptoms Chips
        binding.chipGroupSymptoms.removeAllViews()
        for (symptom in state.selectedSymptoms) {
            val chip = Chip(requireContext()).apply {
                text = symptom.displayName
                isCheckable = false
                isClickable = false
            }
            binding.chipGroupSymptoms.addView(chip)
        }

        // 7. Full Model Outputs (Secondary predictions)
        predictionAdapter.submitList(state.secondaryPredictions)
    }

    private fun setupListeners() {
        binding.btnViewHistory.setOnClickListener {
            findNavController().navigate(R.id.action_predictionResultFragment_to_predictionHistoryFragment)
        }

        binding.btnDone.setOnClickListener {
            // Pop back to the Dashboard
            findNavController().popBackStack(R.id.dashboardFragment, false)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
