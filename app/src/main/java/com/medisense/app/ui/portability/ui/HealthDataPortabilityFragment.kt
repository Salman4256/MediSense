package com.medisense.app.ui.portability.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.medisense.app.databinding.DialogPortabilityPreviewBinding
import com.medisense.app.databinding.FragmentHealthDataPortabilityBinding
import com.medisense.app.domain.model.PortableExportPreview
import com.medisense.app.domain.model.PortableExportResult
import com.medisense.app.domain.security.SecureLogger
import com.medisense.app.ui.portability.viewmodel.ExportPreset
import com.medisense.app.ui.portability.viewmodel.HealthDataPortabilityUiState
import com.medisense.app.ui.portability.viewmodel.HealthDataPortabilityViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HealthDataPortabilityFragment : Fragment() {

    private val TAG = "HealthDataPortabilityFrag"
    private var _binding: FragmentHealthDataPortabilityBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HealthDataPortabilityViewModel by viewModels()
    private lateinit var categoryAdapter: PortableCategoryAdapter
    private var previewDialog: AlertDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHealthDataPortabilityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupCategoriesRecyclerView()
        setupPresetChips()
        setupSwitches()
        setupButtons()
        observeUiState()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupCategoriesRecyclerView() {
        categoryAdapter = PortableCategoryAdapter { category ->
            viewModel.toggleCategory(category)
        }
        binding.rvCategories.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = categoryAdapter
        }
    }

    private fun setupPresetChips() {
        binding.chipPresetFull.setOnClickListener {
            viewModel.applyPreset(ExportPreset.FULL)
        }
        binding.chipPresetClinical.setOnClickListener {
            viewModel.applyPreset(ExportPreset.CLINICAL)
        }
        binding.chipPresetEmergency.setOnClickListener {
            viewModel.applyPreset(ExportPreset.EMERGENCY)
        }
        binding.chipPresetAiInsights.setOnClickListener {
            viewModel.applyPreset(ExportPreset.AI_INSIGHTS)
        }
    }

    private fun setupSwitches() {
        binding.switchPrettyPrint.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setPrettyPrint(isChecked)
        }
        binding.switchIncludeDisclaimers.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setIncludeDisclaimers(isChecked)
        }
        binding.switchIncludeFingerprint.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setIncludeFingerprint(isChecked)
        }
    }

    private fun setupButtons() {
        binding.btnSelectAll.setOnClickListener {
            viewModel.selectAllCategories()
        }
        binding.btnClearAll.setOnClickListener {
            viewModel.clearAllCategories()
        }

        binding.btnPreviewExport.setOnClickListener {
            viewModel.generatePreview()
        }

        binding.btnGenerateExport.setOnClickListener {
            viewModel.generateExport(requireContext())
        }

        binding.btnDismissResult.setOnClickListener {
            viewModel.dismissExportResult()
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    renderUi(state)
                }
            }
        }
    }

    private fun renderUi(state: HealthDataPortabilityUiState) {
        binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        binding.btnPreviewExport.isEnabled = !state.isLoading
        binding.btnGenerateExport.isEnabled = !state.isLoading

        if (state.userId.isNotBlank()) {
            val maskedId = if (state.userId.length > 8) "${state.userId.take(8)}..." else state.userId
            binding.tvUserUuid.text = "User: $maskedId (Local Isolated Storage)"
        }

        categoryAdapter.submitList(state.categories)

        binding.switchPrettyPrint.isChecked = state.prettyPrint
        binding.switchIncludeDisclaimers.isChecked = state.includeDisclaimers
        binding.switchIncludeFingerprint.isChecked = state.includeFingerprint

        // Show/Hide Preview Dialog
        if (state.preview != null) {
            showPreviewDialog(state.preview)
        } else {
            previewDialog?.dismiss()
            previewDialog = null
        }

        // Show/Hide Export Result Card
        if (state.exportResult != null) {
            if (state.exportResult is PortableExportResult.Success) {
                renderExportResult(state.exportResult)
            }
        } else {
            binding.cardExportResult.visibility = View.GONE
        }

        // Handle error message
        state.errorMessage?.let { error ->
            Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show()
            viewModel.dismissError()
        }

        // Handle info message
        state.infoMessage?.let { info ->
            Snackbar.make(binding.root, info, Snackbar.LENGTH_SHORT).show()
            viewModel.dismissInfo()
        }
    }

    private fun showPreviewDialog(preview: PortableExportPreview) {
        if (previewDialog?.isShowing == true) return

        val dialogBinding = DialogPortabilityPreviewBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()

        dialogBinding.tvPreviewPackageId.text = "Bundle ID: ${preview.packageId}"
        dialogBinding.tvPreviewFormatMeta.text = "Format: FHIR-Inspired JSON (v1.0-interop) • ${preview.estimatedResourceCount} Resources"

        val categoriesSummary = StringBuilder("Selected Categories (${preview.totalCategoriesSelected}):\n")
        preview.categoryBreakdown.forEach { (cat, count) ->
            categoriesSummary.appendLine("• ${cat.displayName} ($count resources)")
        }
        dialogBinding.tvPreviewCategoriesSummary.text = categoriesSummary.toString().trim()

        preview.qualitySummary?.let { quality ->
            if (quality.status == com.medisense.app.domain.model.HealthDataQualityStatus.NEEDS_ATTENTION ||
                quality.status == com.medisense.app.domain.model.HealthDataQualityStatus.INSUFFICIENT_DATA
            ) {
                dialogBinding.cardQualityAlert.visibility = View.VISIBLE
                val firstIssueMsg = quality.issues.firstOrNull()?.explanation ?: "Some health records are incomplete."
                dialogBinding.tvQualityAlertText.text = "Data Quality Notice: $firstIssueMsg"
            } else {
                dialogBinding.cardQualityAlert.visibility = View.GONE
            }
        } ?: run {
            dialogBinding.cardQualityAlert.visibility = View.GONE
        }

        dialogBinding.tvJsonPreviewSample.text = preview.previewJsonSample

        dialogBinding.btnConfirmExport.isEnabled = false
        dialogBinding.cbPrivacyConfirmation.setOnCheckedChangeListener { _, isChecked ->
            dialogBinding.btnConfirmExport.isEnabled = isChecked
        }

        dialogBinding.btnCancelPreview.setOnClickListener {
            viewModel.dismissPreview()
            dialog.dismiss()
        }

        dialogBinding.btnConfirmExport.setOnClickListener {
            dialog.dismiss()
            viewModel.dismissPreview()
            viewModel.generateExport(requireContext())
        }

        dialog.setOnDismissListener {
            viewModel.dismissPreview()
        }

        previewDialog = dialog
        dialog.show()
    }

    private fun renderExportResult(result: PortableExportResult.Success) {
        binding.cardExportResult.visibility = View.VISIBLE
        binding.tvExportFileName.text = result.file.name
        val fileSizeKb = (result.file.length() / 1024.0).let { "%.1f KB".format(it) }
        binding.tvExportDetails.text = "Size: $fileSizeKb • ${result.resourceCount} Resources • SHA-256: ${result.sha256Fingerprint.take(12)}..."

        binding.btnShareExport.setOnClickListener {
            shareExportFile(result)
        }
    }

    private fun shareExportFile(result: PortableExportResult.Success) {
        try {
            val contentUri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                result.file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "MediSense Portable Health Record Export")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Attached is my portable health record export generated by MediSense (${result.packageId})."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            viewModel.recordShareAuditEvent(result.packageId)
            startActivity(Intent.createChooser(shareIntent, "Share Health Data Package"))
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to share portable export file", e)
            Snackbar.make(binding.root, "Failed to launch share sheet: ${e.message}", Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        previewDialog?.dismiss()
        previewDialog = null
        _binding = null
    }
}
