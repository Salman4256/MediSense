package com.medisense.app.ui.predictionhistory.ui

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
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.medisense.app.data.local.entity.PredictionHistoryEntity
import com.medisense.app.databinding.FragmentPredictionHistoryDetailBinding
import com.medisense.app.ui.predictionhistory.viewmodel.PredictionDetailUiState
import com.medisense.app.ui.predictionhistory.viewmodel.PredictionHistoryViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class PredictionHistoryDetailFragment : Fragment() {

    private var _binding: FragmentPredictionHistoryDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PredictionHistoryViewModel by viewModels()
    private var currentRecordId: Long = 0L

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPredictionHistoryDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentRecordId = arguments?.getLong("recordId") ?: 0L
        setupToolbar()
        setupListeners()
        observeViewModel()

        if (currentRecordId > 0) {
            viewModel.loadDetail(currentRecordId)
        } else {
            Toast.makeText(requireContext(), "Invalid record ID", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupListeners() {
        binding.btnDeleteRecord.setOnClickListener {
            showDeleteConfirmationDialog()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.detailState.collect { state ->
                    when (state) {
                        is PredictionDetailUiState.Loading -> {
                            // Loading state
                        }
                        is PredictionDetailUiState.Success -> {
                            bindRecordDetails(state.record)
                        }
                        is PredictionDetailUiState.Error -> {
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                            findNavController().navigateUp()
                        }
                    }
                }
            }
        }
    }

    private fun bindRecordDetails(record: PredictionHistoryEntity) {
        val dateFormat = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())

        // 1. Title & Disease Name
        binding.tvDiseaseName.text = record.predictedDisease.split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }

        // 2. Neutral Confidence Display
        val confidencePercent = (record.confidence * 100).toInt().coerceIn(1, 100)
        binding.tvConfidence.text = "Model confidence: $confidencePercent%"

        // 3. Timestamp & Model Version
        binding.tvTimestamp.text = "Prediction Date: " + dateFormat.format(Date(record.predictionTimestamp))
        binding.tvModelVersion.text = "Model Version: ${record.modelVersion} (Local Inference)"

        // 4. Symptoms Chips
        binding.chipGroupSymptoms.removeAllViews()
        for (symptomName in record.symptoms) {
            val chip = Chip(requireContext()).apply {
                text = symptomName
                isCheckable = false
                isClickable = false
            }
            binding.chipGroupSymptoms.addView(chip)
        }

        // 5. Explanation Summary
        val summaryText = record.explanationSummary
        if (!summaryText.isNullOrBlank()) {
            binding.cardExplanation.visibility = View.VISIBLE
            binding.tvExplanationSummary.text = summaryText
        } else {
            binding.tvExplanationSummary.text = "Historical explanation summary was not generated for this prediction."
        }
    }

    private fun showDeleteConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Record")
            .setMessage("Are you sure you want to delete this prediction record?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteHistoryItem(currentRecordId)
                Toast.makeText(requireContext(), "Record deleted", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
