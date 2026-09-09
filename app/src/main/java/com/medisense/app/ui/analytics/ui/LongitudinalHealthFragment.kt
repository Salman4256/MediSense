package com.medisense.app.ui.analytics.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.medisense.app.R
import com.medisense.app.databinding.FragmentLongitudinalHealthBinding
import com.medisense.app.domain.model.AnalysisPeriod
import com.medisense.app.domain.model.DailyMetricPoint
import com.medisense.app.domain.model.LongitudinalHealthSummary
import com.medisense.app.domain.model.TrendDirection
import com.medisense.app.ui.analytics.adapter.RecurringSymptomAdapter
import com.medisense.app.ui.analytics.adapter.TemporalPatternAdapter
import com.medisense.app.ui.analytics.viewmodel.LongitudinalHealthUiState
import com.medisense.app.ui.analytics.viewmodel.LongitudinalHealthViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LongitudinalHealthFragment : Fragment() {

    private var _binding: FragmentLongitudinalHealthBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LongitudinalHealthViewModel by viewModels()

    private val patternsAdapter by lazy { TemporalPatternAdapter() }
    private val symptomsAdapter by lazy { RecurringSymptomAdapter() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLongitudinalHealthBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupRecyclerViews()
        setupPeriodChips()
        setupRetryButton()
        observeUiState()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupRecyclerViews() {
        binding.rvPatterns.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = patternsAdapter
        }

        binding.rvRecurringSymptoms.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = symptomsAdapter
        }
    }

    private fun setupPeriodChips() {
        binding.chipGroupPeriod.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            when (checkedIds.first()) {
                R.id.chip_7_days -> viewModel.selectPeriod(AnalysisPeriod.DAYS_7)
                R.id.chip_30_days -> viewModel.selectPeriod(AnalysisPeriod.DAYS_30)
                R.id.chip_90_days -> viewModel.selectPeriod(AnalysisPeriod.DAYS_90)
            }
        }
    }

    private fun setupRetryButton() {
        binding.btnRetry.setOnClickListener {
            val checkedId = binding.chipGroupPeriod.checkedChipId
            val period = when (checkedId) {
                R.id.chip_7_days -> AnalysisPeriod.DAYS_7
                R.id.chip_90_days -> AnalysisPeriod.DAYS_90
                else -> AnalysisPeriod.DAYS_30
            }
            viewModel.selectPeriod(period)
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is LongitudinalHealthUiState.Loading -> {
                            binding.progressBar.visibility = View.VISIBLE
                            binding.scrollContent.visibility = View.GONE
                            binding.layoutInsufficientData.visibility = View.GONE
                            binding.layoutError.visibility = View.GONE
                        }
                        is LongitudinalHealthUiState.Success -> {
                            binding.progressBar.visibility = View.GONE
                            binding.scrollContent.visibility = View.VISIBLE
                            binding.layoutInsufficientData.visibility = View.GONE
                            binding.layoutError.visibility = View.GONE
                            bindSummaryData(state.summary)
                        }
                        is LongitudinalHealthUiState.InsufficientData -> {
                            binding.progressBar.visibility = View.GONE
                            binding.scrollContent.visibility = View.GONE
                            binding.layoutInsufficientData.visibility = View.VISIBLE
                            binding.layoutError.visibility = View.GONE
                            binding.tvInsufficientDataMessage.text =
                                "Not enough health records logged in the past ${state.period.displayName} to determine longitudinal trends. Log symptoms or track medications to build your analytics."
                        }
                        is LongitudinalHealthUiState.Error -> {
                            binding.progressBar.visibility = View.GONE
                            binding.scrollContent.visibility = View.GONE
                            binding.layoutInsufficientData.visibility = View.GONE
                            binding.layoutError.visibility = View.VISIBLE
                            binding.tvErrorMessage.text = state.message
                        }
                    }
                }
            }
        }
    }

    private fun bindSummaryData(summary: LongitudinalHealthSummary) {
        // 1. Executive Factual Summary
        binding.tvFactualSummary.text = summary.generatedSummary

        // 2. Detected Health Patterns
        if (summary.detectedPatterns.isEmpty()) {
            binding.rvPatterns.visibility = View.GONE
            binding.tvNoPatterns.visibility = View.VISIBLE
        } else {
            binding.rvPatterns.visibility = View.VISIBLE
            binding.tvNoPatterns.visibility = View.GONE
            patternsAdapter.submitList(summary.detectedPatterns)
        }

        // 3. Prediction Activity
        val pred = summary.predictionActivity
        binding.tvPredictionCount.text = "${pred.currentPeriodCount} records"
        val predTrendText = when (pred.direction) {
            TrendDirection.INCREASING, TrendDirection.IMPROVING -> "↑ Increasing"
            TrendDirection.DECREASING, TrendDirection.DECLINING -> "↓ Decreasing"
            TrendDirection.STABLE -> "Stable"
            TrendDirection.INSUFFICIENT_DATA -> "Baseline"
            else -> "Baseline"
        }
        binding.tvPredictionTrendBadge.text = predTrendText

        if (pred.topConditions.isNotEmpty()) {
            binding.tvTopConditions.visibility = View.VISIBLE
            binding.tvTopConditions.text = "Most frequent model outputs: ${pred.topConditions.joinToString(", ")}"
        } else {
            binding.tvTopConditions.visibility = View.GONE
        }
        setupLineChart(binding.chartPredictionActivity, pred.dailyPoints, "Prediction Logs")

        // 4. Symptom Recurrence
        if (summary.recurringSymptoms.isEmpty()) {
            binding.rvRecurringSymptoms.visibility = View.GONE
            binding.tvNoRecurringSymptoms.visibility = View.VISIBLE
        } else {
            binding.rvRecurringSymptoms.visibility = View.VISIBLE
            binding.tvNoRecurringSymptoms.visibility = View.GONE
            symptomsAdapter.submitList(summary.recurringSymptoms)
        }

        // 5. Confidence Trajectory
        val conf = summary.confidenceTrend
        if (conf.currentAvgConfidence != null) {
            binding.tvAvgConfidence.text = "${(conf.currentAvgConfidence * 100).toInt()}%"
        } else {
            binding.tvAvgConfidence.text = "--"
        }
        val confTrendText = when (conf.direction) {
            TrendDirection.INCREASING, TrendDirection.IMPROVING -> "↑ Improving"
            TrendDirection.DECREASING, TrendDirection.DECLINING -> "↓ Declining"
            TrendDirection.STABLE -> "Stable"
            TrendDirection.INSUFFICIENT_DATA -> "Baseline"
            else -> "Baseline"
        }
        binding.tvConfidenceTrendBadge.text = confTrendText
        setupLineChart(binding.chartConfidenceTrend, conf.dailyPoints, "Confidence %")

        // 6. Medication Adherence
        val adh = summary.adherenceTrend
        val adhPct = (adh.currentAdherencePercentage ?: 0f).toInt()
        binding.tvAdherencePercent.text = if (adh.currentAdherencePercentage != null) "$adhPct%" else "--"
        binding.progressAdherence.progress = adhPct

        val adhTrendText = when (adh.direction) {
            TrendDirection.INCREASING, TrendDirection.IMPROVING -> "↑ Improving"
            TrendDirection.DECREASING, TrendDirection.DECLINING -> "↓ Declining"
            TrendDirection.STABLE -> "Consistent"
            TrendDirection.INSUFFICIENT_DATA -> "Baseline"
            else -> "Baseline"
        }
        binding.tvAdherenceTrendBadge.text = adhTrendText
        binding.tvDosesTaken.text = adh.takenCount.toString()
        binding.tvDosesMissed.text = adh.missedCount.toString()
        binding.tvDosesSkipped.text = adh.skippedCount.toString()

        // 7. Doctor Appointments
        val appt = summary.appointmentActivity
        binding.tvAppointmentsInPeriod.text = "${appt.currentPeriodCount} appointments in this window"
        binding.tvUpcomingAppointments.text = "${appt.upcomingCount} upcoming scheduled"
        binding.tvAppointmentTrendBadge.text = when (appt.direction) {
            TrendDirection.INCREASING, TrendDirection.IMPROVING -> "Active"
            TrendDirection.DECREASING, TrendDirection.DECLINING -> "Low"
            TrendDirection.STABLE -> "Stable"
            TrendDirection.INSUFFICIENT_DATA -> "None"
            else -> "None"
        }
    }

    private fun setupLineChart(
        chart: LineChart,
        points: List<DailyMetricPoint>,
        label: String
    ) {
        if (points.isEmpty()) {
            chart.clear()
            chart.setNoDataText("No data available for chart")
            chart.invalidate()
            return
        }

        val entries = points.mapIndexed { index, point ->
            Entry(index.toFloat(), point.value)
        }

        val tealColor = ContextCompat.getColor(requireContext(), R.color.md_theme_light_primary)

        val dataSet = LineDataSet(entries, label).apply {
            color = tealColor
            setCircleColor(tealColor)
            lineWidth = 2f
            circleRadius = 3.5f
            setDrawCircleHole(false)
            valueTextSize = 9f
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillColor = tealColor
            fillAlpha = 40
        }

        chart.apply {
            data = LineData(dataSet)
            description.isEnabled = false
            legend.isEnabled = false
            axisRight.isEnabled = false

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                valueFormatter = IndexAxisValueFormatter(points.map { it.dateLabel })
                textSize = 9f
            }

            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = Color.parseColor("#15000000")
                textSize = 9f
            }

            setTouchEnabled(true)
            isDragEnabled = false
            setScaleEnabled(false)
            setPinchZoom(false)
            invalidate()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
