package com.medisense.app.ui.report.ui

import android.content.Intent
import android.net.Uri
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.medisense.app.databinding.FragmentHealthReportBinding
import com.medisense.app.domain.model.HealthReport
import com.medisense.app.ui.report.viewmodel.HealthReportUiState
import com.medisense.app.ui.report.viewmodel.HealthReportViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class HealthReportFragment : Fragment() {

    private var _binding: FragmentHealthReportBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HealthReportViewModel by viewModels()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHealthReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupButtons()
        observeUiState()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupButtons() {
        binding.btnRetry.setOnClickListener {
            viewModel.loadReport()
        }

        binding.btnGeneratePdf.setOnClickListener {
            binding.btnGeneratePdf.isEnabled = false
            viewModel.generatePdf { success, errorMsg ->
                viewLifecycleOwner.lifecycleScope.launch {
                    if (_binding == null) return@launch
                    binding.btnGeneratePdf.isEnabled = true
                    if (success) {
                        Snackbar.make(binding.root, "PDF report generated successfully", Snackbar.LENGTH_LONG)
                            .setAction("Share") {
                                showPrivacyShareDialog()
                            }
                            .show()
                    } else {
                        val ctx = context ?: return@launch
                        Toast.makeText(ctx, errorMsg ?: "Failed to generate PDF", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        binding.btnShareReport.setOnClickListener {
            showPrivacyShareDialog()
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderUiState(state)
                }
            }
        }
    }

    private fun renderUiState(state: HealthReportUiState) {
        binding.loadingLayout.isVisible = state.isLoading
        binding.errorLayout.isVisible = !state.isLoading && state.errorMessage != null
        binding.reportScrollView.isVisible = !state.isLoading && state.report != null

        if (state.errorMessage != null) {
            binding.tvErrorMessage.text = state.errorMessage
        }

        state.report?.let { report ->
            bindReportData(report)
        }

        if (state.isGeneratingPdf) {
            binding.btnGeneratePdf.text = "Generating PDF..."
            binding.btnGeneratePdf.isEnabled = false
        } else {
            binding.btnGeneratePdf.text = "Generate PDF"
            binding.btnGeneratePdf.isEnabled = state.report != null
        }
    }

    private fun bindReportData(report: HealthReport) {
        // 1. Safety Notice
        binding.tvSafetyDisclaimer.text = report.safetyDisclaimer

        // 2. Header & Metadata
        binding.tvReportTitle.text = report.metadata.title
        val dateStr = dateFormat.format(Date(report.metadata.generatedTimestamp))
        binding.tvReportTimestamp.text = "Generated: $dateStr | Version: ${report.metadata.reportVersion}"

        // 3. Profile
        val p = report.profile
        binding.tvProfileNameDob.text = "Name: ${p.fullName ?: "—"}  |  DOB: ${p.dateOfBirth ?: "—"}  |  Gender: ${p.gender ?: "—"}"
        val heightStr = p.heightCm?.let { "${it} cm" } ?: "—"
        val weightStr = p.weightKg?.let { "${it} kg" } ?: "—"
        val bmiStr = p.bmi?.let { "$it kg/m²" } ?: "—"
        binding.tvProfileVitals.text = "Height: $heightStr  |  Weight: $weightStr  |  BMI: $bmiStr  |  Blood Group: ${p.bloodGroup ?: "—"}"
        binding.tvProfileClinical.text = "Allergies: ${p.allergies ?: "None reported"}  |  Existing Conditions: ${p.existingDiseases ?: "None reported"}"
        binding.tvProfileEmergency.text = "Emergency Contact: ${p.emergencyContactName ?: "—"} (${p.emergencyContactNumber ?: "—"})"
        binding.tvProfileCompleteness.text = "Profile Completeness: ${p.completenessPercentage}%"
        if (p.incompleteFields.isNotEmpty()) {
            binding.tvProfileMissingFields.isVisible = true
            binding.tvProfileMissingFields.text = "Incomplete Fields: ${p.incompleteFields.joinToString(", ")}"
        } else {
            binding.tvProfileMissingFields.isVisible = false
        }

        // 4. Data Quality
        val q = report.dataQuality
        val scoreText = if (q.qualityScore != null) "${q.qualityScore}%" else "Insufficient Data"
        binding.tvDataQualitySummary.text = "Status: ${q.status.name}  |  Quality Score: $scoreText  |  Passed Checks: ${q.passedChecks}/${q.totalChecks}"
        binding.tvDataQualityIssuesCount.text = "${q.errorCount} Errors, ${q.warningCount} Warnings, ${q.infoCount} Info notices"

        // 5. Prediction History
        val pred = report.predictions
        val avgConfText = if (pred.averageConfidencePercentage != null) "${pred.averageConfidencePercentage}%" else "—"
        binding.tvPredictionMetrics.text = "Total Predictions: ${pred.totalPredictions}  |  Average Confidence: $avgConfText"
        if (pred.recentPredictions.isEmpty()) {
            binding.tvPredictionItemsText.text = "No historical model predictions on record."
        } else {
            val text = pred.recentPredictions.joinToString("\n\n") { item ->
                "• ${item.predictedDisease} (${item.confidencePercentage}% - ${item.confidenceLevel})\n" +
                "  Date: ${item.predictionDate} | Symptoms: ${item.symptoms.joinToString(", ")}\n" +
                (item.uncertaintyInterpretation?.let { "  Uncertainty: $it" } ?: "")
            }
            binding.tvPredictionItemsText.text = text
        }

        // 6. Medications & Adherence
        val m = report.medications
        val adhText = if (m.adherencePercentage != null) "${m.adherencePercentage}% (${m.adherenceRating})" else "No Adherence Logs"
        binding.tvMedicationMetrics.text = "Active Medications: ${m.activeMedicationsCount}  |  Adherence: $adhText"
        if (m.activeMedications.isEmpty()) {
            binding.tvMedicationItemsText.text = "No active medication schedules configured."
        } else {
            val text = m.activeMedications.joinToString("\n") { med ->
                "• ${med.medicineName} (${med.dosage}) — ${med.frequency} at ${med.scheduledTimes.joinToString(", ")}"
            }
            val statsNote = if (m.totalDosesLogged > 0) {
                "\nDoses: ${m.takenDosesCount} Taken, ${m.skippedDosesCount} Skipped, ${m.missedDosesCount} Missed (Total: ${m.totalDosesLogged})"
            } else ""
            binding.tvMedicationItemsText.text = "$text$statsNote"
        }

        // 7. Appointments
        val apt = report.appointments
        binding.tvAppointmentMetrics.text = "Total Appointments: ${apt.totalAppointments}  |  Upcoming: ${apt.upcomingAppointments.size}"
        if (apt.upcomingAppointments.isEmpty()) {
            binding.tvAppointmentItemsText.text = "No upcoming doctor appointments scheduled."
        } else {
            val text = apt.upcomingAppointments.joinToString("\n") { a ->
                "• ${a.doctorName} — ${a.clinicName}\n  Date: ${a.appointmentDate} at ${a.appointmentTime} (${a.appointmentType})"
            }
            binding.tvAppointmentItemsText.text = text
        }

        // 8. Longitudinal Trends
        val t = report.trends
        val symText = if (t.topRecurringSymptoms.isNotEmpty()) "\nTop Recurring Symptoms: ${t.topRecurringSymptoms.joinToString(", ")}" else ""
        val patText = if (t.detectedPatterns.isNotEmpty()) "\n" + t.detectedPatterns.joinToString("\n") { "• $it" } else ""
        binding.tvTrendSummaryText.text = "Window: ${t.periodLabel}\nPrediction Trend: ${t.predictionFrequencyTrend}\nAdherence Trend: ${t.adherenceTrend} | Activity: ${t.appointmentActivity}$symText$patText"

        // 9. Personal Health Context
        val c = report.context
        binding.tvContextSummaryText.text = "Demographics: ${c.demographicSummary}\nConditions: ${c.chronicConditionsSummary}\nAllergies: ${c.allergySummary}\nMedications: ${c.activeMedicationSummary} (${c.adherenceContext})"

        // 10. RCHR Summary
        val r = report.rchr
        val rScore = if (r.reconstructionConsistencyScore != null) "${r.reconstructionConsistencyScore}%" else "N/A"
        binding.tvRchrScore.text = "RCHR Version: ${r.rchrVersion}  |  Reconstruction Consistency: $rScore"
        binding.tvRchrNotice.text = r.explanationNotice

        // 11. Contextual Risk Priority
        val risk = report.riskPriority
        val rScoreText = if (risk.priorityScore != null) "${risk.priorityScore}/100" else "N/A"
        val rSuffText = if (risk.hasSufficientData) "SUFFICIENT" else "LIMITED_DATA"
        binding.tvRiskPriority.text = "Priority Level: ${risk.priorityLevel.name}  |  Score: $rScoreText  |  Data: $rSuffText"
        val factorText = if (risk.contributingFactors.isNotEmpty()) "\nFactors:\n" + risk.contributingFactors.joinToString("\n") { "• $it" } else ""
        binding.tvRiskExplanation.text = "${risk.plainLanguageExplanation}$factorText\n\n${risk.notice}"

        // 12. Personalized Guidance
        val g = report.guidance
        if (g.items.isEmpty()) {
            binding.tvGuidanceItemsText.text = "No specific personalized guidance recommendations available for the current context."
        } else {
            val text = g.items.joinToString("\n\n") { item ->
                "• [${item.priority.name}] ${item.title} (${item.category.name})\n  ${item.actionableGuidance}\n  Reason: ${item.clinicalReason}"
            }
            binding.tvGuidanceItemsText.text = text
        }

        // 13. Cloud Sync
        val s = report.syncStatus
        val lastSyncStr = if (s.lastSyncTimestamp != null && s.lastSyncTimestamp > 0L) dateFormat.format(Date(s.lastSyncTimestamp)) else "Never Synced"
        val modeStr = if (s.isOfflineMode) "Offline-First Mode (local Room source of truth)" else "Cloud Backup Active"
        binding.tvSyncStatusText.text = "Sync Status: ${s.syncStatus}  |  Last Sync: $lastSyncStr  |  Pending Changes: ${s.pendingRecordsCount}\nMode: $modeStr"
    }

    private fun showPrivacyShareDialog() {
        val ctx = context ?: return
        MaterialAlertDialogBuilder(ctx)
            .setTitle("Share Personal Health Report")
            .setMessage("This report contains personal health information. Share it only with people or services you trust.")
            .setPositiveButton("Share") { _, _ ->
                viewModel.confirmShareReport(
                    onReadyToShare = { contentUri ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            launchShareIntent(contentUri)
                        }
                    },
                    onError = { errorMsg ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            if (_binding == null) return@launch
                            val safeCtx = context ?: return@launch
                            Toast.makeText(safeCtx, errorMsg, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun launchShareIntent(contentUri: Uri) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, "MediSense Personal Health Report")
            putExtra(Intent.EXTRA_TEXT, "Here is my personal health-management summary generated with MediSense.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            val chooser = Intent.createChooser(shareIntent, "Share Health Report PDF")
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(chooser)
        } catch (e: Exception) {
            val safeCtx = context ?: return
            Toast.makeText(safeCtx, "No application found to share PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
