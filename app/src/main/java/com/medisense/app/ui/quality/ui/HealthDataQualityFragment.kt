package com.medisense.app.ui.quality.ui

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
import com.medisense.app.R
import com.medisense.app.databinding.FragmentHealthDataQualityBinding
import com.medisense.app.domain.model.HealthDataQualityCategory
import com.medisense.app.domain.model.HealthDataQualityStatus
import com.medisense.app.ui.quality.adapter.HealthDataQualityAdapter
import com.medisense.app.ui.quality.viewmodel.HealthDataQualityViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HealthDataQualityFragment : Fragment() {

    private var _binding: FragmentHealthDataQualityBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HealthDataQualityViewModel by viewModels()
    private lateinit var qualityAdapter: HealthDataQualityAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHealthDataQualityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        setupFilterChips()
        setupActionListeners()
        observeUiState()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupRecyclerView() {
        qualityAdapter = HealthDataQualityAdapter { issue ->
            issue.navigationDestinationId?.let { destId ->
                try {
                    findNavController().navigate(destId)
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Opening ${issue.affectedRecordType} settings...", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.rvQualityIssues.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = qualityAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupFilterChips() {
        binding.chipGroupCategories.setOnCheckedStateChangeListener { _, checkedIds ->
            val selectedCategory = when (checkedIds.firstOrNull()) {
                R.id.chip_profile -> HealthDataQualityCategory.PROFILE_COMPLETENESS
                R.id.chip_medications -> HealthDataQualityCategory.MEDICATION_DATA
                R.id.chip_appointments -> HealthDataQualityCategory.APPOINTMENT_DATA
                R.id.chip_predictions -> HealthDataQualityCategory.PREDICTION_HISTORY
                R.id.chip_temporal -> HealthDataQualityCategory.TEMPORAL_CONSISTENCY
                R.id.chip_sync -> HealthDataQualityCategory.SYNC_READINESS
                else -> null // All
            }
            viewModel.filterByCategory(selectedCategory)
        }
    }

    private fun setupActionListeners() {
        binding.btnRecheckQuality.setOnClickListener {
            viewModel.loadDataQuality()
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.btnRecheckQuality.isEnabled = !state.isLoading

                    state.summary?.let { summary ->
                        // 1. Status badge
                        binding.tvQualityStatusBadge.text = summary.status.name.replace("_", " ")
                        when (summary.status) {
                            HealthDataQualityStatus.GOOD -> {
                                binding.tvQualityStatusBadge.setBackgroundResource(R.drawable.bg_badge_positive)
                                binding.tvQualityHeadline.text = "Your health data is complete and structurally consistent."
                            }
                            HealthDataQualityStatus.NEEDS_ATTENTION -> {
                                binding.tvQualityStatusBadge.setBackgroundResource(R.drawable.bg_badge_attention)
                                binding.tvQualityHeadline.text = "Some records have missing fields or formatting issues."
                            }
                            HealthDataQualityStatus.INSUFFICIENT_DATA -> {
                                binding.tvQualityStatusBadge.setBackgroundResource(R.drawable.bg_badge_info)
                                binding.tvQualityHeadline.text = "Add profile, medication, or appointment records to assess data quality."
                            }
                        }

                        // 2. Quality score
                        if (summary.qualityScore != null) {
                            binding.tvQualityScore.text = "${summary.qualityScore}%"
                            binding.progressQualityScore.progress = summary.qualityScore
                            binding.progressQualityScore.isVisible = true
                        } else {
                            binding.tvQualityScore.text = "--"
                            binding.progressQualityScore.isVisible = false
                        }

                        // 3. Metric breakdown
                        binding.tvPassedChecks.text = "${summary.passedChecks}/${summary.totalChecks}"
                        binding.tvErrorCount.text = summary.errorCount.toString()
                        binding.tvWarningCount.text = summary.warningCount.toString()

                        // 4. Issues list
                        qualityAdapter.submitList(state.filteredIssues)
                        binding.tvEmptyIssues.isVisible = state.filteredIssues.isEmpty()
                        binding.rvQualityIssues.isVisible = state.filteredIssues.isNotEmpty()
                    }

                    state.errorMessage?.let { msg ->
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
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
