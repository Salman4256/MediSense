package com.medisense.app.ui.consultation.ui

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
import com.medisense.app.R
import com.medisense.app.databinding.DialogAddCustomQuestionBinding
import com.medisense.app.databinding.FragmentConsultationPreparationBinding
import com.medisense.app.databinding.ItemConsultationQuestionBinding
import com.medisense.app.domain.model.*
import com.medisense.app.ui.consultation.viewmodel.ConsultationPreparationUiState
import com.medisense.app.ui.consultation.viewmodel.ConsultationPreparationViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Module 19: Smart Consultation Preparation & Doctor Visit Summary Fragment.
 *
 * Provides users with a structured, explainable consultation summary and
 * customizable question checklist to discuss with healthcare professionals.
 */
@AndroidEntryPoint
class ConsultationPreparationFragment : Fragment() {

    private var _binding: FragmentConsultationPreparationBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ConsultationPreparationViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConsultationPreparationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupPeriodChips()
        setupActionButtons()
        observeUiState()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupPeriodChips() {
        binding.chipGroupPeriods.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val selectedPeriod = when (checkedIds.first()) {
                R.id.chip_period_7d -> ConsultationPeriod.LAST_7_DAYS
                R.id.chip_period_30d -> ConsultationPeriod.LAST_30_DAYS
                R.id.chip_period_90d -> ConsultationPeriod.LAST_90_DAYS
                R.id.chip_period_all -> ConsultationPeriod.ALL_HISTORY
                else -> ConsultationPeriod.LAST_30_DAYS
            }
            viewModel.setPeriod(selectedPeriod)
        }
    }

    private fun setupActionButtons() {
        binding.btnRetry.setOnClickListener {
            viewModel.loadSummary()
        }

        binding.btnAddCustomQuestion.setOnClickListener {
            showAddCustomQuestionDialog()
        }

        binding.btnShareSummary.setOnClickListener {
            showPrivacyShareTextDialog()
        }

        binding.btnExportDocument.setOnClickListener {
            showPrivacyExportDocumentDialog()
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

    private fun renderUiState(state: ConsultationPreparationUiState) {
        when (state) {
            is ConsultationPreparationUiState.Loading -> {
                binding.layoutLoading.isVisible = true
                binding.layoutError.isVisible = false
                binding.scrollViewConsultation.isVisible = false
            }
            is ConsultationPreparationUiState.Error -> {
                binding.layoutLoading.isVisible = false
                binding.layoutError.isVisible = true
                binding.scrollViewConsultation.isVisible = false
                binding.tvErrorMessage.text = state.message
            }
            is ConsultationPreparationUiState.Empty -> {
                binding.layoutLoading.isVisible = false
                binding.layoutError.isVisible = false
                binding.scrollViewConsultation.isVisible = true
                bindEmptyState(state.message)
            }
            is ConsultationPreparationUiState.Content -> {
                binding.layoutLoading.isVisible = false
                binding.layoutError.isVisible = false
                binding.scrollViewConsultation.isVisible = true
                bindSummaryContent(state)
            }
        }
    }

    private fun bindEmptyState(message: String) {
        binding.tvOverviewTitle.text = "Consultation Summary"
        binding.tvCompletenessBadge.text = "Limited Data"
        binding.tvOverviewPeriod.text = "No recorded health history found for selected window."
        binding.tvOverviewMessage.text = message
        binding.tvProfileContent.text = "No profile details found."
        binding.tvSymptomsContent.text = "No recent symptoms logged."
        binding.tvPredictionsContent.text = "No disease prediction history on record."
        binding.tvMedicationsContent.text = "No active medications or doses recorded."
        binding.tvAppointmentsContent.text = "No upcoming doctor appointments scheduled."
        binding.tvTrendsContent.text = "Insufficient history to identify longitudinal health patterns."
        binding.layoutSuggestedQuestions.removeAllViews()
        binding.layoutCustomQuestions.removeAllViews()
        binding.tvQualityContent.text = "Profile and health records are incomplete."
    }

    private fun bindSummaryContent(state: ConsultationPreparationUiState.Content) {
        val summary = state.summary

        // Ensure chip reflects period without triggering infinite loops
        val chipId = when (state.selectedPeriod) {
            ConsultationPeriod.LAST_7_DAYS -> R.id.chip_period_7d
            ConsultationPeriod.LAST_30_DAYS -> R.id.chip_period_30d
            ConsultationPeriod.LAST_90_DAYS -> R.id.chip_period_90d
            ConsultationPeriod.ALL_HISTORY -> R.id.chip_period_all
        }
        if (binding.chipGroupPeriods.checkedChipId != chipId) {
            binding.chipGroupPeriods.check(chipId)
        }

        // Overview & Completeness
        binding.tvOverviewTitle.text = "Doctor Visit Preparation Summary"
        binding.tvOverviewPeriod.text = "Period: ${summary.visitOverview.dataPeriodLabel} (${summary.visitOverview.totalRecordsCount} health entries evaluated)"
        binding.tvOverviewMessage.text = summary.visitOverview.statusMessage
        binding.tvCompletenessBadge.text = summary.visitOverview.completenessStatus.label

        // Section 1: Health Profile
        binding.tvProfileContent.text = formatProfileSection(summary.profile)

        // Section 2: Symptoms
        binding.tvSymptomsContent.text = formatSymptomsSection(summary.symptoms)

        // Section 3: Model Prediction History
        binding.tvPredictionsContent.text = formatPredictionsSection(summary.predictions)

        // Section 4: Medications & Adherence
        binding.tvMedicationsContent.text = formatMedicationsSection(summary.medications)

        // Section 5: Doctor Appointments
        binding.tvAppointmentsContent.text = formatAppointmentsSection(summary.appointments)

        // Section 6: Trends & Context
        binding.tvTrendsContent.text = formatTrendsSection(summary.trends, summary.context)

        // Section 7: Suggested & Custom Questions
        bindQuestions(summary.suggestedQuestions, summary.customQuestions)

        // Section 8: Data Quality
        binding.tvQualityContent.text = formatDataQualitySection(summary.dataQuality)

        // Medical Disclaimer Footer
        binding.tvDisclaimer.text = summary.safetyDisclaimer
    }

    private fun formatProfileSection(profile: ConsultationProfileSummary): String {
        val lines = mutableListOf<String>()
        val nameStr = profile.fullName ?: "Not specified"
        val dobStr = profile.ageOrDob ?: "Not specified"
        val genderStr = profile.gender ?: "Not specified"
        val bloodStr = profile.bloodGroup ?: "Not specified"
        lines.add("• Patient: $nameStr | Age/DOB: $dobStr | Gender: $genderStr | Blood: $bloodStr")

        val clinical = mutableListOf<String>()
        clinical.add("Allergies: ${profile.allergies ?: "None recorded"}")
        clinical.add("Chronic Conditions: ${profile.existingConditions ?: "None recorded"}")
        lines.add("• Medical Baseline: ${clinical.joinToString(" | ")}")

        profile.currentMedications?.let {
            lines.add("• Profile Medication Notes: $it")
        }

        return lines.joinToString("\n")
    }

    private fun formatSymptomsSection(symptoms: ConsultationSymptomsSummary): String {
        if (symptoms.totalSymptomsCount == 0) {
            return "No symptoms recorded during this time window."
        }

        val lines = mutableListOf<String>()
        lines.add("• Total Symptom Entries: ${symptoms.totalSymptomsCount}")

        if (symptoms.frequentSymptoms.isNotEmpty()) {
            lines.add("• Frequent Symptoms: ${symptoms.frequentSymptoms.joinToString(", ")}")
        }

        if (symptoms.recentObservations.isNotEmpty()) {
            lines.add("• Recent Observations:")
            symptoms.recentObservations.take(4).forEach { (sym, date) ->
                lines.add("  - $sym ($date)")
            }
        }

        return lines.joinToString("\n")
    }

    private fun formatPredictionsSection(predictions: ConsultationPredictionsSummary): String {
        if (predictions.totalPredictionsCount == 0) {
            return "No model disease predictions recorded during this period."
        }

        val lines = mutableListOf<String>()
        lines.add("• Predictions Evaluated: ${predictions.totalPredictionsCount}")

        if (predictions.recentPredictions.isNotEmpty()) {
            lines.add("• Recent Model Screenings:")
            predictions.recentPredictions.forEach { item ->
                val symList = if (item.reportedSymptoms.isNotEmpty()) " [Symptoms: ${item.reportedSymptoms.joinToString(", ")}]" else ""
                lines.add("  - ${item.predictionDate}: ${item.predictedCondition} (${item.confidencePercentage}% model confidence)$symList")
            }
        }

        lines.add("*Note: Machine learning predictions are for educational decision-support only and do not constitute clinical diagnoses.")
        return lines.joinToString("\n")
    }

    private fun formatMedicationsSection(medications: ConsultationMedicationSummary): String {
        if (medications.activeMedications.isEmpty() && medications.adherencePercentage == null) {
            return "No active medication schedules or dose logs found for this period."
        }

        val lines = mutableListOf<String>()
        lines.add("• Active Medications: ${medications.activeMedications.size} | Overall Adherence: ${medications.adherenceSummary}")

        if (medications.activeMedications.isNotEmpty()) {
            lines.add("• Current Medication Regimens:")
            medications.activeMedications.forEach { med ->
                val inst = if (med.instructions.isNotBlank()) " [${med.instructions}]" else ""
                lines.add("  - ${med.name} (${med.dosage}) — ${med.frequency}$inst")
            }
        }

        if (medications.missedOrSkippedCount > 0) {
            lines.add("• Doses requiring attention: ${medications.missedOrSkippedCount} missed or skipped logs.")
        }

        return lines.joinToString("\n")
    }

    private fun formatAppointmentsSection(appointments: ConsultationAppointmentSummary): String {
        val lines = mutableListOf<String>()
        if (appointments.upcomingAppointmentDoctor != null) {
            lines.add("• Upcoming Appointment:")
            lines.add("  - ${appointments.upcomingAppointmentDoctor} (${appointments.upcomingAppointmentType ?: "Consultation"}) on ${appointments.upcomingAppointmentDate ?: "Scheduled"}")
            appointments.upcomingAppointmentClinic?.let {
                lines.add("    Location: $it")
            }
        } else {
            lines.add("• No upcoming doctor appointments currently scheduled.")
        }

        if (appointments.recentPastAppointmentsCount > 0) {
            lines.add("• Past Appointments in Window: ${appointments.recentPastAppointmentsCount}")
        }

        return lines.joinToString("\n")
    }

    private fun formatTrendsSection(trends: ConsultationTrendsSummary, context: ConsultationContextSummary): String {
        val lines = mutableListOf<String>()
        lines.add("• Symptom Trend: ${trends.symptomTrend}")
        lines.add("• Medication Adherence Trend: ${trends.adherenceTrend}")

        if (trends.detectedPatterns.isNotEmpty()) {
            lines.add("• Longitudinal Observations:")
            trends.detectedPatterns.forEach { p ->
                lines.add("  - $p")
            }
        }

        if (context.personalContextSummary.isNotBlank()) {
            lines.add("• Health Context: ${context.personalContextSummary}")
        }

        context.contextualPriorityNotice?.let {
            lines.add("• Context Priority: $it")
        }

        return lines.joinToString("\n")
    }

    private fun bindQuestions(
        suggested: List<ConsultationQuestion>,
        custom: List<ConsultationQuestion>
    ) {
        // Suggested Questions
        binding.layoutSuggestedQuestions.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())

        if (suggested.isEmpty()) {
            val emptyTv = android.widget.TextView(requireContext()).apply {
                text = "No automatic question suggestions generated for the current records."
                textSize = 12f
                setPadding(8, 8, 8, 8)
            }
            binding.layoutSuggestedQuestions.addView(emptyTv)
        } else {
            suggested.forEach { question ->
                val itemBinding = ItemConsultationQuestionBinding.inflate(inflater, binding.layoutSuggestedQuestions, false)
                itemBinding.tvQuestionText.text = question.questionText
                itemBinding.tvQuestionRationale.text = question.rationale
                itemBinding.tvQuestionSourceBadge.text = question.category.displayName
                itemBinding.btnDeleteQuestion.isVisible = false

                itemBinding.cbQuestionSelected.setOnCheckedChangeListener(null)
                itemBinding.cbQuestionSelected.isChecked = question.isSelected

                itemBinding.cbQuestionSelected.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked != question.isSelected) {
                        viewModel.toggleQuestionSelection(question.id)
                    }
                }

                itemBinding.cardQuestionRoot.setOnClickListener {
                    viewModel.toggleQuestionSelection(question.id)
                }

                binding.layoutSuggestedQuestions.addView(itemBinding.root)
            }
        }

        // Custom Questions
        binding.layoutCustomQuestions.removeAllViews()
        if (custom.isNotEmpty()) {
            val headerTv = android.widget.TextView(requireContext()).apply {
                text = "Your Custom Questions (${custom.size}):"
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(4, 16, 4, 8)
            }
            binding.layoutCustomQuestions.addView(headerTv)

            custom.forEach { question ->
                val itemBinding = ItemConsultationQuestionBinding.inflate(inflater, binding.layoutCustomQuestions, false)
                itemBinding.tvQuestionText.text = question.questionText
                itemBinding.tvQuestionRationale.text = question.rationale
                itemBinding.tvQuestionSourceBadge.text = question.category.displayName
                itemBinding.btnDeleteQuestion.isVisible = true

                itemBinding.cbQuestionSelected.setOnCheckedChangeListener(null)
                itemBinding.cbQuestionSelected.isChecked = question.isSelected

                itemBinding.cbQuestionSelected.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked != question.isSelected) {
                        viewModel.toggleQuestionSelection(question.id)
                    }
                }

                itemBinding.cardQuestionRoot.setOnClickListener {
                    viewModel.toggleQuestionSelection(question.id)
                }

                itemBinding.btnDeleteQuestion.setOnClickListener {
                    viewModel.removeCustomQuestion(question.id)
                }

                binding.layoutCustomQuestions.addView(itemBinding.root)
            }
        }
    }

    private fun formatDataQualitySection(dataQuality: ConsultationDataQualitySummary): String {
        val lines = mutableListOf<String>()
        lines.add("• Quality Status: ${dataQuality.dataQualityStatus.name}")

        if (dataQuality.notices.isNotEmpty()) {
            lines.add("• Quality Notices:")
            dataQuality.notices.forEach { n ->
                lines.add("  - $n")
            }
        } else {
            lines.add("• All recorded fields pass consistency checks.")
        }

        return lines.joinToString("\n")
    }

    private fun showAddCustomQuestionDialog() {
        val ctx = requireContext()
        val dialogBinding = DialogAddCustomQuestionBinding.inflate(LayoutInflater.from(ctx))

        MaterialAlertDialogBuilder(ctx)
            .setTitle("Add Custom Question")
            .setView(dialogBinding.root)
            .setPositiveButton("Add") { _, _ ->
                val text = dialogBinding.etCustomQuestion.text?.toString()?.trim().orEmpty()
                if (text.isNotBlank()) {
                    viewModel.addCustomQuestion(text)
                    Snackbar.make(binding.root, "Custom question added to visit plan", Snackbar.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(ctx, "Please enter question text", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPrivacyShareTextDialog() {
        val ctx = requireContext()
        MaterialAlertDialogBuilder(ctx)
            .setTitle("Share Consultation Summary")
            .setMessage("This summary contains your personal health observations and questions. Share it only with your doctor or trusted healthcare providers.")
            .setPositiveButton("Share") { _, _ ->
                val text = viewModel.getFormattedSummaryPlainText()
                if (text.isNotBlank()) {
                    viewModel.recordShareCompleted()
                    launchShareTextIntent(text)
                } else {
                    Toast.makeText(ctx, "No summary content to share", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun launchShareTextIntent(text: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Doctor Consultation Preparation Summary - MediSense")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        try {
            startActivity(Intent.createChooser(shareIntent, "Share Consultation Summary"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Unable to launch share app: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPrivacyExportDocumentDialog() {
        val ctx = requireContext()
        MaterialAlertDialogBuilder(ctx)
            .setTitle("Export Consultation Document")
            .setMessage("This will export your consultation preparation summary as a plain-text document file to share with your healthcare team.")
            .setPositiveButton("Export & Share") { _, _ ->
                viewModel.exportSummaryDocument(ctx) { result ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        when (result) {
                            is HealthReportExportResult.Success -> {
                                launchShareFileIntent(result.contentUri)
                            }
                            is HealthReportExportResult.Error -> {
                                Toast.makeText(ctx, result.message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun launchShareFileIntent(fileUri: Uri) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            putExtra(Intent.EXTRA_SUBJECT, "Doctor Consultation Preparation Document - MediSense")
            putExtra(Intent.EXTRA_TEXT, "Here is my doctor consultation preparation summary document generated with MediSense.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            val chooser = Intent.createChooser(shareIntent, "Export Consultation Document")
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Unable to share file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
