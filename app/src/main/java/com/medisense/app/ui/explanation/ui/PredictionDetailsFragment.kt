package com.medisense.app.ui.explanation.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.medisense.app.R
import com.medisense.app.databinding.FragmentPredictionDetailsBinding
import com.medisense.app.ui.explanation.viewmodel.PredictionDetailsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.roundToInt

@AndroidEntryPoint
class PredictionDetailsFragment : Fragment() {

    private var _binding: FragmentPredictionDetailsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PredictionDetailsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPredictionDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        readArguments()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun readArguments() {
        val args = arguments ?: return
        val diseaseName = args.getString("diseaseName") ?: ""
        val confidence = args.getFloat("confidence", 0f)
        val predictionId = args.getInt("predictionId", -1)
        val selectedSymptoms = args.getStringArray("selectedSymptoms") ?: emptyArray()

        // Card 1 bindings
        val percentage = (confidence * 100).roundToInt()
        binding.tvPredictedDisease.text = diseaseName
        binding.tvConfidence.text = "$percentage%"
        binding.progressConfidence.progress = percentage

        // Card 3 bindings (Chips)
        binding.chipGroupSymptoms.removeAllViews()
        selectedSymptoms.forEach { symptom ->
            val chip = Chip(requireContext()).apply {
                text = symptom.replace("_", " ").replaceFirstChar { it.uppercaseChar() }
                isClickable = false
                isCheckable = false
            }
            binding.chipGroupSymptoms.addView(chip)
        }

        // Trigger ViewModel to load Explanation, Recommendations, and Risk Level
        viewModel.initialize(predictionId, diseaseName, confidence, selectedSymptoms)
    }

    private fun observeViewModel() {
        // Loading state
        viewModel.loading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        // Error state
        viewModel.error.observe(viewLifecycleOwner) { errorMessage ->
            errorMessage?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
            }
        }

        // Risk Level (Card 4)
        viewModel.riskLevel.observe(viewLifecycleOwner) { riskLevel ->
            when (riskLevel) {
                PredictionDetailsViewModel.RiskLevel.LOW -> {
                    binding.tvRiskLevel.text = "Low Risk"
                    binding.tvRiskLevel.setTextColor(Color.parseColor("#4CAF50"))
                    binding.ivRiskIcon.imageTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
                }
                PredictionDetailsViewModel.RiskLevel.MEDIUM -> {
                    binding.tvRiskLevel.text = "Medium Risk"
                    binding.tvRiskLevel.setTextColor(Color.parseColor("#FF9800"))
                    binding.ivRiskIcon.imageTintList = ColorStateList.valueOf(Color.parseColor("#FF9800"))
                }
                PredictionDetailsViewModel.RiskLevel.HIGH -> {
                    binding.tvRiskLevel.text = "High Risk"
                    binding.tvRiskLevel.setTextColor(Color.parseColor("#F44336"))
                    binding.ivRiskIcon.imageTintList = ColorStateList.valueOf(Color.parseColor("#F44336"))
                }
            }
        }

        // Explanations (Card 2)
        viewModel.explanationResult.observe(viewLifecycleOwner) { result ->
            val explanationText = result.contributingSymptoms.joinToString(separator = "\n\n") { "✔ ${it.explanation}" }
            binding.tvExplanationText.text = explanationText.ifBlank { "No detailed XAI explanation available." }
        }

        // Recommendations (Card 5)
        viewModel.recommendedCare.observe(viewLifecycleOwner) { recommendations ->
            binding.tvRecommendations.text = recommendations.joinToString(separator = "\n\n") { "• $it" }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
