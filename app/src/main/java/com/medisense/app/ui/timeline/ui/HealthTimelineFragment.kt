package com.medisense.app.ui.timeline.ui

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
import com.medisense.app.R
import com.medisense.app.databinding.FragmentHealthTimelineBinding
import com.medisense.app.domain.model.*
import com.medisense.app.ui.timeline.viewmodel.HealthTimelineUiState
import com.medisense.app.ui.timeline.viewmodel.HealthTimelineViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class HealthTimelineFragment : Fragment() {

    private var _binding: FragmentHealthTimelineBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HealthTimelineViewModel by viewModels()
    private lateinit var timelineAdapter: HealthTimelineAdapter

    private val dateFormatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHealthTimelineBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        setupFilterChips()
        setupSortButton()
        setupRetryAndResetButtons()
        observeUiState()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupRecyclerView() {
        timelineAdapter = HealthTimelineAdapter { event ->
            showEventDetailBottomSheet(event)
        }
        binding.rvTimelineEvents.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = timelineAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun showEventDetailBottomSheet(event: HealthTimelineEvent) {
        val bottomSheet = HealthTimelineDetailBottomSheet(event) { target, _ ->
            navigateToModule(target)
        }
        bottomSheet.show(childFragmentManager, HealthTimelineDetailBottomSheet.TAG)
    }

    private fun navigateToModule(target: TimelineNavigationTarget) {
        when (target) {
            TimelineNavigationTarget.PREDICTION_DETAIL,
            TimelineNavigationTarget.PREDICTION_HISTORY -> {
                findNavController().navigate(R.id.action_healthTimelineFragment_to_predictionHistoryFragment)
            }
            TimelineNavigationTarget.MEDICATION,
            TimelineNavigationTarget.MEDICATION_HISTORY -> {
                findNavController().navigate(R.id.action_healthTimelineFragment_to_medicationListFragment)
            }
            TimelineNavigationTarget.APPOINTMENT -> {
                findNavController().navigate(R.id.action_healthTimelineFragment_to_appointmentFragment)
            }
            TimelineNavigationTarget.HEALTH_TRENDS -> {
                findNavController().navigate(R.id.action_healthTimelineFragment_to_longitudinalHealthFragment)
            }
            TimelineNavigationTarget.CONTEXTUAL_RISK -> {
                findNavController().navigate(R.id.action_healthTimelineFragment_to_contextualRiskFragment)
            }
            TimelineNavigationTarget.PERSONALIZED_GUIDANCE -> {
                findNavController().navigate(R.id.action_healthTimelineFragment_to_personalizedGuidanceFragment)
            }
            TimelineNavigationTarget.HEALTH_DATA_QUALITY -> {
                findNavController().navigate(R.id.action_healthTimelineFragment_to_healthDataQualityFragment)
            }
            TimelineNavigationTarget.HEALTH_REPORT -> {
                findNavController().navigate(R.id.action_healthTimelineFragment_to_healthReportFragment)
            }
            TimelineNavigationTarget.PROFILE -> {
                findNavController().navigate(R.id.action_healthTimelineFragment_to_profileFragment)
            }
            TimelineNavigationTarget.NONE -> {
                // No navigation required
            }
        }
    }

    private fun setupFilterChips() {
        // 1. Time Period Chips
        binding.chipGroupPeriods.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val period = when (checkedIds.first()) {
                R.id.chip_period_7d -> HealthTimelinePeriod.LAST_7_DAYS
                R.id.chip_period_30d -> HealthTimelinePeriod.LAST_30_DAYS
                R.id.chip_period_90d -> HealthTimelinePeriod.LAST_90_DAYS
                R.id.chip_period_year -> HealthTimelinePeriod.THIS_YEAR
                else -> HealthTimelinePeriod.ALL
            }
            viewModel.setPeriod(period)
        }

        // 2. Category Chips
        binding.chipGroupCategories.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val category = when (checkedIds.first()) {
                R.id.chip_cat_predictions -> HealthTimelineCategoryFilter.PREDICTIONS
                R.id.chip_cat_medications -> HealthTimelineCategoryFilter.MEDICATIONS
                R.id.chip_cat_appointments -> HealthTimelineCategoryFilter.APPOINTMENTS
                R.id.chip_cat_trends -> HealthTimelineCategoryFilter.TRENDS
                R.id.chip_cat_context_risk -> HealthTimelineCategoryFilter.CONTEXT_RISK
                R.id.chip_cat_data_quality -> HealthTimelineCategoryFilter.DATA_QUALITY
                R.id.chip_cat_reports_sync -> HealthTimelineCategoryFilter.REPORTS_SYNC
                else -> HealthTimelineCategoryFilter.ALL
            }
            viewModel.setCategory(category)
        }
    }

    private fun setupSortButton() {
        binding.btnSortOrder.setOnClickListener {
            viewModel.toggleSortOrder()
        }
    }

    private fun setupRetryAndResetButtons() {
        binding.btnRetry.setOnClickListener {
            viewModel.refresh()
        }
        binding.btnResetFilters.setOnClickListener {
            binding.chipPeriodAll.isChecked = true
            binding.chipCatAll.isChecked = true
            viewModel.setPeriod(HealthTimelinePeriod.ALL)
            viewModel.setCategory(HealthTimelineCategoryFilter.ALL)
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is HealthTimelineUiState.Loading -> {
                            binding.layoutLoading.visibility = View.VISIBLE
                            binding.layoutError.visibility = View.GONE
                            binding.layoutEmptyState.visibility = View.GONE
                            binding.rvTimelineEvents.visibility = View.GONE
                        }
                        is HealthTimelineUiState.Error -> {
                            binding.layoutLoading.visibility = View.GONE
                            binding.layoutError.visibility = View.VISIBLE
                            binding.tvErrorMessage.text = state.message
                            binding.layoutEmptyState.visibility = View.GONE
                            binding.rvTimelineEvents.visibility = View.GONE
                        }
                        is HealthTimelineUiState.Empty -> {
                            binding.layoutLoading.visibility = View.GONE
                            binding.layoutError.visibility = View.GONE
                            binding.layoutEmptyState.visibility = View.VISIBLE
                            binding.tvEmptyMessage.text = state.message
                            binding.rvTimelineEvents.visibility = View.GONE
                        }
                        is HealthTimelineUiState.Content -> {
                            binding.layoutLoading.visibility = View.GONE
                            binding.layoutError.visibility = View.GONE
                            binding.layoutEmptyState.visibility = View.GONE
                            binding.rvTimelineEvents.visibility = View.VISIBLE

                            // Bind Journey Overview Metrics
                            bindSummary(state.summary)

                            // Update Sort button label
                            binding.btnSortOrder.text = if (state.filter.sortOrder == HealthTimelineSortOrder.NEWEST_FIRST) "Newest" else "Oldest"

                            // Submit list to RecyclerView
                            timelineAdapter.submitList(state.events)
                        }
                    }
                }
            }
        }
    }

    private fun bindSummary(summary: HealthTimelineSummary) {
        binding.tvStatTotalEvents.text = summary.totalEventsCount.toString()
        binding.tvStatPredictions.text = summary.recentPredictionsCount.toString()
        binding.tvStatActiveMeds.text = summary.activeMedicationsCount.toString()
        binding.tvStatUpcomingAppts.text = summary.upcomingAppointmentsCount.toString()
        binding.tvStatPatterns.text = summary.detectedPatternsCount.toString()

        if (summary.latestEventTimestamp != null) {
            try {
                binding.tvStatLastActive.text = dateFormatter.format(Date(summary.latestEventTimestamp))
            } catch (e: Exception) {
                binding.tvStatLastActive.text = "Recent"
            }
        } else {
            binding.tvStatLastActive.text = "None"
        }

        binding.tvDataQualityBadge.text = "Data: ${summary.dataQualityStatus.name.lowercase().replaceFirstChar { it.uppercase() }}"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
