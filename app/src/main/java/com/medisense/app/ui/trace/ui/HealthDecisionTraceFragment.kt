package com.medisense.app.ui.trace.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.medisense.app.R
import com.medisense.app.databinding.BottomSheetHealthDecisionTraceDetailBinding
import com.medisense.app.databinding.FragmentHealthDecisionTraceBinding
import com.medisense.app.databinding.ItemTraceFactorBinding
import com.medisense.app.databinding.ItemTraceStepBinding
import com.medisense.app.domain.model.HealthDecisionTrace
import com.medisense.app.domain.model.HealthDecisionTraceType
import com.medisense.app.domain.model.TraceInfluenceDirection
import com.medisense.app.ui.trace.viewmodel.HealthDecisionTraceEvent
import com.medisense.app.ui.trace.viewmodel.HealthDecisionTraceUiState
import com.medisense.app.ui.trace.viewmodel.HealthDecisionTraceViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class HealthDecisionTraceFragment : Fragment() {

    private var _binding: FragmentHealthDecisionTraceBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HealthDecisionTraceViewModel by viewModels()
    private lateinit var traceAdapter: HealthDecisionTraceAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHealthDecisionTraceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupFilterChips()
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupFilterChips() {
        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener

            val filter = when (checkedIds.first()) {
                R.id.chip_all -> null
                R.id.chip_predictions -> HealthDecisionTraceType.DISEASE_PREDICTION
                R.id.chip_risk -> HealthDecisionTraceType.CONTEXTUAL_RISK
                R.id.chip_guidance -> HealthDecisionTraceType.PERSONALIZED_GUIDANCE
                R.id.chip_rchr -> HealthDecisionTraceType.RCHR_REPRESENTATION
                R.id.chip_longitudinal -> HealthDecisionTraceType.LONGITUDINAL_PATTERN
                R.id.chip_personalization -> HealthDecisionTraceType.PERSONALIZATION
                R.id.chip_quality -> HealthDecisionTraceType.DATA_QUALITY_ASSESSMENT
                else -> null
            }
            viewModel.setFilter(filter)
        }
    }

    private fun setupRecyclerView() {
        traceAdapter = HealthDecisionTraceAdapter(
            onInspectClicked = { trace -> viewModel.onInspectTrace(trace) },
            onShareClicked = { trace -> showExportOptionsDialog(trace) }
        )

        binding.rvDecisionTraces.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = traceAdapter
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        renderUiState(state)
                    }
                }

                launch {
                    viewModel.events.collect { event ->
                        handleEvent(event)
                    }
                }
            }
        }
    }

    private fun renderUiState(state: HealthDecisionTraceUiState) {
        when (state) {
            is HealthDecisionTraceUiState.Loading -> {
                binding.progressBar.visibility = View.VISIBLE
                binding.layoutEmptyState.visibility = View.GONE
                binding.rvDecisionTraces.visibility = View.GONE
            }
            is HealthDecisionTraceUiState.Empty -> {
                binding.progressBar.visibility = View.GONE
                binding.layoutEmptyState.visibility = View.VISIBLE
                binding.rvDecisionTraces.visibility = View.GONE
            }
            is HealthDecisionTraceUiState.Success -> {
                binding.progressBar.visibility = View.GONE
                binding.layoutEmptyState.visibility = View.GONE
                binding.rvDecisionTraces.visibility = View.VISIBLE
                traceAdapter.submitList(state.traces)
            }
            is HealthDecisionTraceUiState.Error -> {
                binding.progressBar.visibility = View.GONE
                binding.layoutEmptyState.visibility = View.VISIBLE
                binding.rvDecisionTraces.visibility = View.GONE
                Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleEvent(event: HealthDecisionTraceEvent) {
        when (event) {
            is HealthDecisionTraceEvent.ShowToast -> {
                Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
            }
            is HealthDecisionTraceEvent.ShowDetailSheet -> {
                showDetailBottomSheet(event.trace)
            }
            is HealthDecisionTraceEvent.CopyToClipboard -> {
                copyToClipboard(event.text, event.label)
            }
            is HealthDecisionTraceEvent.SharePdf -> {
                launchPdfShareIntent(event.file, event.title)
            }
        }
    }

    private fun showExportOptionsDialog(trace: HealthDecisionTrace) {
        val options = arrayOf("Share PDF Audit Report", "Copy Plain Text Trace", "Share Plain Text")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Export Decision Trace")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> viewModel.onExportPdf(requireContext(), trace)
                    1 -> viewModel.onCopyPlainText(trace)
                    2 -> {
                        val plainText = viewModel.getFormattedPlainText(trace)
                        launchTextShareIntent(plainText, trace.outputResult.primaryOutput)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDetailBottomSheet(trace: HealthDecisionTrace) {
        val dialog = BottomSheetDialog(requireContext())
        val sheetBinding = BottomSheetHealthDecisionTraceDetailBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        val dateFormat = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())

        sheetBinding.tvSheetTypeBadge.text = trace.decisionType.displayName.uppercase()
        sheetBinding.tvSheetPrimaryDecision.text = trace.outputResult.primaryOutput
        sheetBinding.tvSheetEngineComponent.text = "Engine Component: ${trace.engineName} (v${trace.engineVersion})"
        sheetBinding.tvSheetTimestamp.text = "Timestamp: ${dateFormat.format(Date(trace.generatedAt))} (ID: ${trace.traceId})"

        if (!trace.outputResult.confidenceOrStatus.isNullOrBlank()) {
            sheetBinding.tvSheetConfidenceBadge.visibility = View.VISIBLE
            sheetBinding.tvSheetConfidenceBadge.text = trace.outputResult.confidenceOrStatus
        } else {
            sheetBinding.tvSheetConfidenceBadge.visibility = View.GONE
        }

        // Populate execution steps
        sheetBinding.layoutExecutionSteps.removeAllViews()
        trace.processingSteps.forEach { step ->
            val stepBinding = ItemTraceStepBinding.inflate(layoutInflater, sheetBinding.layoutExecutionSteps, false)
            stepBinding.tvStepIndex.text = step.stepNumber.toString()
            stepBinding.tvStepName.text = step.title
            stepBinding.tvStepDescription.text = step.description
            stepBinding.tvStepData.visibility = View.VISIBLE
            stepBinding.tvStepData.text = "Component: ${step.engineComponent}"
            sheetBinding.layoutExecutionSteps.addView(stepBinding.root)
        }

        // Populate factors
        sheetBinding.layoutTraceFactors.removeAllViews()
        if (trace.inputFactors.isNotEmpty()) {
            sheetBinding.cardFactors.visibility = View.VISIBLE
            trace.inputFactors.forEach { factor ->
                val factorBinding = ItemTraceFactorBinding.inflate(layoutInflater, sheetBinding.layoutTraceFactors, false)
                factorBinding.tvFactorName.text = factor.name
                val weightStr = factor.weightPercentage?.let { "$it%" } ?: factor.value
                factorBinding.tvFactorWeight.text = weightStr

                when (factor.influenceDirection) {
                    TraceInfluenceDirection.POSITIVE -> {
                        factorBinding.tvFactorDirection.text = "+ Positive"
                        factorBinding.tvFactorDirection.setBackgroundResource(R.drawable.bg_badge_supports)
                    }
                    TraceInfluenceDirection.NEGATIVE -> {
                        factorBinding.tvFactorDirection.text = "- Negative"
                        factorBinding.tvFactorDirection.setBackgroundResource(R.drawable.bg_badge_attention)
                    }
                    TraceInfluenceDirection.ALERT -> {
                        factorBinding.tvFactorDirection.text = "⚠️ Attention"
                        factorBinding.tvFactorDirection.setBackgroundResource(R.drawable.bg_badge_attention)
                    }
                    TraceInfluenceDirection.NEUTRAL -> {
                        factorBinding.tvFactorDirection.text = "• Neutral"
                        factorBinding.tvFactorDirection.setBackgroundResource(R.drawable.bg_badge_info)
                    }
                }

                val progress = factor.weightPercentage ?: 50
                factorBinding.progressFactorWeight.progress = progress
                factorBinding.tvFactorDescription.text = factor.interpretation
                sheetBinding.layoutTraceFactors.addView(factorBinding.root)
            }
        } else {
            sheetBinding.cardFactors.visibility = View.GONE
        }

        // Populate safety notice
        sheetBinding.tvSheetSafetyNotice.text = trace.limitationsText

        // Action Buttons
        sheetBinding.btnSheetCopyText.setOnClickListener {
            dialog.dismiss()
            viewModel.onCopyPlainText(trace)
        }

        sheetBinding.btnSheetSharePdf.setOnClickListener {
            dialog.dismiss()
            viewModel.onExportPdf(requireContext(), trace)
        }

        dialog.show()
    }

    private fun copyToClipboard(text: String, label: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Snackbar.make(binding.root, "$label copied to clipboard", Snackbar.LENGTH_SHORT).show()
    }

    private fun launchTextShareIntent(text: String, title: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Decision Trace - $title")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        try {
            startActivity(Intent.createChooser(shareIntent, "Share Decision Trace Text"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Unable to share text: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchPdfShareIntent(file: File, title: String) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, "Here is an explainable AI health decision trace from MediSense.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Decision Trace PDF")
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Unable to share PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
