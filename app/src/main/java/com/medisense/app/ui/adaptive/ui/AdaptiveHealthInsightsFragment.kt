package com.medisense.app.ui.adaptive.ui

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
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.medisense.app.R
import com.medisense.app.databinding.DialogRecordInterventionObservationBinding
import com.medisense.app.databinding.FragmentAdaptiveHealthInsightsBinding
import com.medisense.app.domain.model.AdaptiveCounterfactualCandidate
import com.medisense.app.domain.model.ObservedResponse
import com.medisense.app.ui.adaptive.viewmodel.AdaptiveHealthInsightsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Fragment presenting Adaptive Health Insights and Prediction-Observation Feedback Loop (Module 25).
 */
@AndroidEntryPoint
class AdaptiveHealthInsightsFragment : Fragment() {

    private var _binding: FragmentAdaptiveHealthInsightsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdaptiveHealthInsightsViewModel by viewModels()

    private lateinit var candidateAdapter: AdaptiveCandidateAdapter
    private lateinit var historyAdapter: InterventionResponseHistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdaptiveHealthInsightsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupRecyclerViews()
        observeState()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupRecyclerViews() {
        candidateAdapter = AdaptiveCandidateAdapter { candidate ->
            showObservationDialog(candidate)
        }
        binding.rvAdaptiveCandidates.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = candidateAdapter
        }

        historyAdapter = InterventionResponseHistoryAdapter { recordId ->
            viewModel.deleteObservation(recordId)
        }
        binding.rvResponseHistory.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = historyAdapter
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // Loading indicator
                    binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE

                    // Error message
                    state.errorMessage?.let { errorMsg ->
                        Snackbar.make(binding.root, errorMsg, Snackbar.LENGTH_LONG).show()
                        viewModel.clearError()
                    }

                    // Observation saved notification
                    state.observationSavedMessage?.let { successMsg ->
                        Snackbar.make(binding.root, successMsg, Snackbar.LENGTH_SHORT).show()
                        viewModel.clearSavedMessage()
                    }

                    // Profile overview update
                    state.profile?.let { profile ->
                        binding.chipConfidenceLevel.text = profile.overallConfidenceLevel.displayName
                        binding.tvTotalObservations.text = profile.validObservationsCount.toString()

                        val aligned = profile.categorySummaries.values.sumOf { it.alignedCount }
                        binding.tvAlignedObservations.text = aligned.toString()

                        binding.tvPatternDescription.text = profile.patternDescription
                    }

                    // Candidates list
                    candidateAdapter.submitList(state.candidates)

                    // History list
                    historyAdapter.submitList(state.history)
                    if (state.history.isEmpty() && !state.isLoading) {
                        binding.layoutEmptyHistory.visibility = View.VISIBLE
                        binding.rvResponseHistory.visibility = View.GONE
                    } else {
                        binding.layoutEmptyHistory.visibility = View.GONE
                        binding.rvResponseHistory.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun showObservationDialog(candidate: AdaptiveCounterfactualCandidate) {
        val dialogBinding = DialogRecordInterventionObservationBinding.inflate(layoutInflater)
        dialogBinding.tvDialogScenarioName.text = candidate.title
        dialogBinding.tvDialogExpectedOutcome.text = candidate.expectedShiftDescription

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnCancelDialog.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSaveObservation.setOnClickListener {
            val observedResponse = when (dialogBinding.rgObservedResponse.checkedRadioButtonId) {
                R.id.rbImproved -> ObservedResponse.IMPROVED
                R.id.rbPartiallyImproved -> ObservedResponse.PARTIALLY_IMPROVED
                R.id.rbUnchanged -> ObservedResponse.UNCHANGED
                R.id.rbWorsened -> ObservedResponse.WORSENED
                R.id.rbUnclear -> ObservedResponse.UNCLEAR
                else -> ObservedResponse.IMPROVED
            }

            val notes = dialogBinding.etUserNotes.text?.toString()
            viewModel.selectCandidateForObservation(candidate)
            viewModel.saveObservation(observedResponse, notes)
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
