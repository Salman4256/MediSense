package com.medisense.app.ui.sharing.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.content.FileProvider
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.medisense.app.R
import com.medisense.app.databinding.DialogSharingPreviewConsentBinding
import com.medisense.app.databinding.FragmentHealthSharingBinding
import com.medisense.app.domain.model.*
import com.medisense.app.domain.security.SecureLogger
import com.medisense.app.ui.sharing.viewmodel.HealthSharingViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.util.ArrayList

@AndroidEntryPoint
class HealthSharingFragment : Fragment() {

    private val TAG = "HealthSharingFragment"
    private var _binding: FragmentHealthSharingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HealthSharingViewModel by viewModels()

    private lateinit var categoryAdapter: SharingCategoryAdapter
    private lateinit var historyAdapter: SharingHistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHealthSharingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupTabs()
        setupPurposeDropdown()
        setupRecipientInput()
        setupCategoriesList()
        setupFormatSelector()
        setupHistoryList()
        setupActionButtons()
        observeUiState()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        binding.scrollPrepareShare.visibility = View.VISIBLE
                        binding.cardBottomActions.visibility = View.VISIBLE
                        binding.layoutConsentHistory.visibility = View.GONE
                    }
                    1 -> {
                        binding.scrollPrepareShare.visibility = View.GONE
                        binding.cardBottomActions.visibility = View.GONE
                        binding.layoutConsentHistory.visibility = View.VISIBLE
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupPurposeDropdown() {
        val purposes = SharingPurpose.entries.map { it.displayName }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, purposes)
        binding.actPurpose.setAdapter(adapter)

        binding.actPurpose.setOnItemClickListener { _, _, position, _ ->
            val selectedPurpose = SharingPurpose.entries[position]
            viewModel.updatePurpose(selectedPurpose)
        }
    }

    private fun setupRecipientInput() {
        binding.etRecipient.doAfterTextChanged { text ->
            viewModel.updateRecipientLabel(text?.toString().orEmpty())
        }
    }

    private fun setupCategoriesList() {
        categoryAdapter = SharingCategoryAdapter { category, isSelected ->
            viewModel.toggleCategory(category, isSelected)
        }

        binding.rvSharingCategories.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = categoryAdapter
        }

        binding.btnSelectAllCategories.setOnClickListener {
            val currentSelected = viewModel.uiState.value.scope.selectedCategories
            val allSelected = currentSelected.size == SharingDataCategory.entries.size
            viewModel.selectAllCategories(!allSelected)
        }
    }

    private fun setupFormatSelector() {
        binding.rgFormat.setOnCheckedChangeListener { _, checkedId ->
            val format = when (checkedId) {
                R.id.rb_format_pdf -> SharingFormat.PDF_DOCUMENT
                R.id.rb_format_json -> SharingFormat.JSON_PACKAGE
                R.id.rb_format_both -> SharingFormat.BOTH
                else -> SharingFormat.PDF_DOCUMENT
            }
            viewModel.updateFormat(format)
        }
    }

    private fun setupHistoryList() {
        historyAdapter = SharingHistoryAdapter(
            onRevokeClicked = { consent ->
                showRevokeConfirmationDialog(consent)
            },
            onDeleteClicked = { consent ->
                showDeleteConfirmationDialog(consent)
            }
        )

        binding.rvConsentHistory.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = historyAdapter
        }
    }

    private fun setupActionButtons() {
        binding.btnPreviewConsent.setOnClickListener {
            viewModel.preparePackageForPreview { previewPkg ->
                showPreviewConsentDialog(previewPkg)
            }
        }
    }

    private fun showPreviewConsentDialog(pkg: HealthSharingPackage) {
        val dialogBinding = DialogSharingPreviewConsentBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()

        dialogBinding.tvPreviewPackageId.text = "Package: ${pkg.packageMetadata.packageId}"
        dialogBinding.tvPreviewPurpose.text = "Purpose: ${pkg.consentDeclaration.purpose}"
        dialogBinding.tvPreviewRecipient.text = "Recipient: ${pkg.consentDeclaration.recipientLabel}"
        dialogBinding.tvPreviewFingerprint.text = "SHA-256: ${pkg.packageMetadata.sha256Fingerprint.take(16)}... (Local Checksum)"

        val sb = StringBuilder("Included Categories (${pkg.packageMetadata.totalCategoriesIncluded}):\n")
        pkg.consentDeclaration.grantedCategories.forEach { cat ->
            sb.appendLine("• $cat")
        }
        dialogBinding.tvPreviewSections.text = sb.toString().trim()

        // Data Quality Notice
        val qualitySummary = viewModel.uiState.value.qualitySummary
        if (qualitySummary != null && qualitySummary.issues.isNotEmpty()) {
            dialogBinding.cardQualityNotice.visibility = View.VISIBLE
            dialogBinding.tvQualityNoticeText.text = "Data Quality Notice: ${qualitySummary.issues.size} non-blocking notices on record (${qualitySummary.status.name}). Sharing will proceed with available local records."
        } else {
            dialogBinding.cardQualityNotice.visibility = View.GONE
        }

        dialogBinding.cbConsentDisclosure.setOnCheckedChangeListener { _, isChecked ->
            dialogBinding.btnConfirmExport.isEnabled = isChecked
        }

        dialogBinding.btnCancelConsent.setOnClickListener {
            viewModel.cancelSharingFlow("User dismissed preview consent dialog")
            dialog.dismiss()
        }

        dialogBinding.btnConfirmExport.setOnClickListener {
            dialog.dismiss()
            viewModel.grantConsentAndExport(requireContext()) { exportResult ->
                if (exportResult != null) {
                    dispatchShareIntent(exportResult)
                }
            }
        }

        dialog.show()
    }

    private fun dispatchShareIntent(result: HealthSharingExportResult.Success) {
        try {
            val context = requireContext()
            val fileUris = ArrayList<Uri>()

            result.exportedFiles.forEach { file ->
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                fileUris.add(uri)
            }

            val shareIntent = if (fileUris.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = when (result.consent.format) {
                        SharingFormat.JSON_PACKAGE -> "application/json"
                        SharingFormat.PDF_DOCUMENT -> "application/pdf"
                        SharingFormat.BOTH -> "*/*"
                    }
                    putExtra(Intent.EXTRA_STREAM, fileUris.first())
                    putExtra(Intent.EXTRA_SUBJECT, "MediSense Health Record (${result.consent.recipientLabel})")
                    putExtra(Intent.EXTRA_TEXT, "Attached is a secure, user-consented health data summary generated locally by MediSense.")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, fileUris)
                    putExtra(Intent.EXTRA_SUBJECT, "MediSense Health Data Package (${result.consent.recipientLabel})")
                    putExtra(Intent.EXTRA_TEXT, "Attached are user-consented health data export files generated locally by MediSense.")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }

            startActivity(Intent.createChooser(shareIntent, "Share Health Data Package"))
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to launch share intent", e)
            Snackbar.make(binding.root, "Could not open share chooser: ${e.localizedMessage}", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun showRevokeConfirmationDialog(consent: HealthDataSharingConsent) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Revoke Sharing Consent?")
            .setMessage("Revoking this consent marks the record as revoked in MediSense and stops further exports. Please note that files already exported or shared externally cannot be remotely deleted.")
            .setPositiveButton("Revoke Consent") { _, _ ->
                viewModel.revokeConsent(consent.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirmationDialog(consent: HealthDataSharingConsent) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Consent Record?")
            .setMessage("Permanently remove this consent record from your local history?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteConsent(consent.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    // Loading State
                    binding.loadingLayout.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                    binding.tvLoadingText.text = state.loadingMessage

                    // Category Adapter Sync
                    categoryAdapter.updateSelected(state.scope.selectedCategories)

                    val allSelected = state.scope.selectedCategories.size == SharingDataCategory.entries.size
                    binding.btnSelectAllCategories.text = if (allSelected) "Deselect All" else "Select All"

                    // History Adapter Sync
                    historyAdapter.submitList(state.consentHistory)
                    binding.emptyHistoryLayout.visibility = if (state.consentHistory.isEmpty()) View.VISIBLE else View.GONE
                    binding.rvConsentHistory.visibility = if (state.consentHistory.isEmpty()) View.GONE else View.VISIBLE

                    // Error Feedback
                    state.errorMessage?.let { error ->
                        Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show()
                        viewModel.clearErrorMessage()
                    }

                    // User Message Feedback
                    state.userMessage?.let { msg ->
                        Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
                        viewModel.clearUserMessage()
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
