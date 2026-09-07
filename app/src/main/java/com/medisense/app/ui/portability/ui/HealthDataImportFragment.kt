package com.medisense.app.ui.portability.ui

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.medisense.app.databinding.DialogImportResourceDetailBinding
import com.medisense.app.databinding.FragmentHealthDataImportBinding
import com.medisense.app.domain.model.PortableImportCategoryPreview
import com.medisense.app.ui.portability.viewmodel.HealthDataImportUiState
import com.medisense.app.ui.portability.viewmodel.HealthDataImportViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HealthDataImportFragment : Fragment() {

    private var _binding: FragmentHealthDataImportBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HealthDataImportViewModel by viewModels()
    private lateinit var importAdapter: PortableImportAdapter
    private var detailDialog: AlertDialog? = null

    private val documentPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.processFileUri(requireContext(), it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHealthDataImportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        setupButtons()
        observeUiState()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupRecyclerView() {
        importAdapter = PortableImportAdapter { categoryPreview ->
            viewModel.inspectCategory(categoryPreview)
        }
        binding.rvImportCategories.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = importAdapter
        }
    }

    private fun setupButtons() {
        binding.btnSelectFile.setOnClickListener {
            documentPickerLauncher.launch(arrayOf("application/json", "*/*"))
        }

        binding.btnClearSelection.setOnClickListener {
            viewModel.clearFile()
        }

        binding.btnPrepareImport.setOnClickListener {
            showPrepareConfirmationDialog()
        }

        binding.btnDismissPrepared.setOnClickListener {
            viewModel.dismissValidatedPackage()
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

    private fun renderUi(state: HealthDataImportUiState) {
        binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        binding.btnSelectFile.isEnabled = !state.isLoading

        if (state.activeUserId.isNotBlank()) {
            val maskedId = if (state.activeUserId.length > 8) "${state.activeUserId.take(8)}..." else state.activeUserId
            binding.tvActiveUserScope.text = "Target Account: $maskedId (Local Isolated Storage)"
        }

        // Render preview if loaded
        if (state.hasFileLoaded && state.preview != null) {
            val preview = state.preview
            binding.cardFileInfo.visibility = View.VISIBLE
            binding.tvFileName.text = preview.summary.fileName
            binding.tvFormatVersion.text = "Format: ${preview.summary.appName} • Schema: ${preview.summary.schemaVersion} (v${preview.summary.exportVersion})"
            binding.tvPackageId.text = "Package ID: ${preview.summary.packageId ?: "Unspecified"} • Fingerprint: ${preview.summary.sha256Fingerprint?.take(12) ?: "None"}..."

            binding.layoutMetrics.visibility = View.VISIBLE
            binding.chipTotalResources.text = "${preview.summary.totalResources} Total"
            binding.chipValidResources.text = "${preview.summary.validResources} Valid"
            binding.chipWarningCount.text = "${preview.summary.warningCount} Warnings"
            binding.chipErrorCount.text = "${preview.summary.errorCount} Errors"

            // Conflicts card
            if (preview.conflicts.isNotEmpty()) {
                binding.cardConflictAlert.visibility = View.VISIBLE
                val conflictDesc = preview.conflicts.firstOrNull()?.description ?: "${preview.conflicts.size} differences with local records detected."
                binding.tvConflictAlertText.text = "Local Discrepancy: $conflictDesc (Existing data was not changed)."
            } else {
                binding.cardConflictAlert.visibility = View.GONE
            }

            // Categories list
            binding.tvCategoriesHeader.visibility = View.VISIBLE
            binding.rvImportCategories.visibility = View.VISIBLE
            importAdapter.submitList(preview.categories)

            // Bottom actions
            binding.layoutBottomActions.visibility = View.VISIBLE
            binding.btnPrepareImport.isEnabled = state.isImportable && !state.isLoading
            if (!state.isImportable) {
                binding.btnPrepareImport.text = "Blocked by Errors"
            } else {
                binding.btnPrepareImport.text = "Prepare Import Package"
            }
        } else {
            binding.cardFileInfo.visibility = View.GONE
            binding.layoutMetrics.visibility = View.GONE
            binding.cardConflictAlert.visibility = View.GONE
            binding.tvCategoriesHeader.visibility = View.GONE
            binding.rvImportCategories.visibility = View.GONE
            binding.layoutBottomActions.visibility = View.GONE
            importAdapter.submitList(emptyList())
        }

        // Show/Hide category detail dialog
        if (state.selectedCategoryDetail != null) {
            showCategoryDetailDialog(state.selectedCategoryDetail)
        } else {
            detailDialog?.dismiss()
            detailDialog = null
        }

        // Show/Hide Prepared Result Card
        if (state.validatedPackage != null) {
            binding.cardPreparedResult.visibility = View.VISIBLE
            binding.tvPreparedDetails.text = "${state.validatedPackage.totalValidRecords} valid records from ${state.validatedPackage.readyCategories.size} categories validated and staged in memory. Local database was not changed."
        } else {
            binding.cardPreparedResult.visibility = View.GONE
        }

        // Error message
        state.errorMessage?.let { error ->
            Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show()
            viewModel.dismissError()
        }

        // Info message
        state.infoMessage?.let { info ->
            Snackbar.make(binding.root, info, Snackbar.LENGTH_SHORT).show()
            viewModel.dismissInfo()
        }
    }

    private fun showCategoryDetailDialog(categoryDetail: PortableImportCategoryPreview) {
        if (detailDialog?.isShowing == true) return

        val dialogBinding = DialogImportResourceDetailBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()

        dialogBinding.tvDetailCategoryTitle.text = categoryDetail.displayName
        dialogBinding.tvDetailResourceType.text = "Resource Schema: ${categoryDetail.resourceType} • ${categoryDetail.recordCount} records"

        val recordsContent = if (categoryDetail.summaryLines.isNotEmpty()) {
            categoryDetail.summaryLines.joinToString("\n\n")
        } else {
            "No record summaries available."
        }
        dialogBinding.tvDetailRecordsContent.text = recordsContent

        if (categoryDetail.hasWarnings || categoryDetail.hasErrors) {
            dialogBinding.layoutDetailIssues.visibility = View.VISIBLE
            dialogBinding.tvDetailIssuesText.text = if (categoryDetail.hasErrors) {
                "• Category contains fields violating required schema constraints."
            } else {
                "• Category contains minor format differences or conflicts with current records."
            }
        } else {
            dialogBinding.layoutDetailIssues.visibility = View.GONE
        }

        dialogBinding.btnCloseDetail.setOnClickListener {
            dialog.dismiss()
            viewModel.dismissCategoryDetail()
        }

        dialog.setOnDismissListener {
            viewModel.dismissCategoryDetail()
        }

        detailDialog = dialog
        dialog.show()
    }

    private fun showPrepareConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Prepare Import Package")
            .setMessage("Preparing this package validates and stages health records in memory for safe future integration. Your local records will NOT be overwritten during this process.\n\nDo you wish to proceed?")
            .setPositiveButton("Confirm") { _, _ ->
                viewModel.prepareValidatedImportPackage(userConfirmed = true)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        detailDialog?.dismiss()
        detailDialog = null
        _binding = null
    }
}
