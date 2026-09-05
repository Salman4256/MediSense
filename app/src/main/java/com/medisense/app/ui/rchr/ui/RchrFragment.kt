package com.medisense.app.ui.rchr.ui

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
import com.medisense.app.databinding.FragmentRchrBinding
import com.medisense.app.domain.rchr.RchrReconstructionResult
import com.medisense.app.domain.rchr.RchrRepresentation
import com.medisense.app.ui.rchr.adapter.ReconstructedAttributeAdapter
import com.medisense.app.ui.rchr.viewmodel.RchrUiState
import com.medisense.app.ui.rchr.viewmodel.RchrViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class RchrFragment : Fragment() {

    private var _binding: FragmentRchrBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RchrViewModel by viewModels()
    private val attributeAdapter by lazy { ReconstructedAttributeAdapter() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRchrBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        setupListeners()
        observeState()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupRecyclerView() {
        binding.rvReconstructedAttributes.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = attributeAdapter
        }
    }

    private fun setupListeners() {
        binding.btnReconstruct.setOnClickListener {
            viewModel.triggerReconstruction()
            binding.scrollContent.post {
                binding.scrollContent.smoothScrollTo(0, binding.cardReconstructionResults.top)
            }
        }

        binding.btnRetry.setOnClickListener {
            viewModel.observeRchrRepresentation()
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderUiState(state)
                }
            }
        }
    }

    private fun renderUiState(state: RchrUiState) {
        when (state) {
            is RchrUiState.Loading -> {
                binding.progressBar.visibility = View.VISIBLE
                binding.scrollContent.visibility = View.GONE
                binding.layoutEmpty.visibility = View.GONE
                binding.layoutError.visibility = View.GONE
            }
            is RchrUiState.Empty -> {
                binding.progressBar.visibility = View.GONE
                binding.scrollContent.visibility = View.GONE
                binding.layoutEmpty.visibility = View.VISIBLE
                binding.layoutError.visibility = View.GONE
                binding.tvEmptyMessage.text = state.message
            }
            is RchrUiState.Error -> {
                binding.progressBar.visibility = View.GONE
                binding.scrollContent.visibility = View.GONE
                binding.layoutEmpty.visibility = View.GONE
                binding.layoutError.visibility = View.VISIBLE
                binding.tvErrorMessage.text = state.message
            }
            is RchrUiState.Success -> {
                binding.progressBar.visibility = View.GONE
                binding.scrollContent.visibility = View.VISIBLE
                binding.layoutEmpty.visibility = View.GONE
                binding.layoutError.visibility = View.GONE
                bindRepresentation(state.representation, state.reconstructionResult)
            }
        }
    }

    private fun bindRepresentation(
        rep: RchrRepresentation,
        reconstruction: RchrReconstructionResult?
    ) {
        val dateFormat = SimpleDateFormat("MMM d, yyyy • hh:mm a", Locale.getDefault())

        // 1. Overview Header
        binding.tvVersionBadge.text = "RCHR v${rep.representationVersion}"
        binding.tvEncodedFeaturesCount.text = "${rep.totalEncodedFeatures} features encoded"
        binding.tvCompletenessBadge.text = "${rep.completenessPercentage}% complete"
        binding.tvGeneratedTimestamp.text = "Generated: ${dateFormat.format(Date(rep.generatedAt))}"

        // 2. Profile Features
        val prof = rep.profileFeatures
        val ageStr = if (prof.age != null) "Age: ${prof.age} (${prof.ageGroup ?: "N/A"})" else "Age: Unrecorded"
        val genderStr = "Gender: ${prof.gender ?: "Unspecified"}"
        val bloodStr = "Blood: ${prof.bloodGroup ?: "Unrecorded"}"
        binding.tvRchrProfileDemographics.text = "$ageStr • $genderStr • $bloodStr"

        binding.tvRchrProfileBmi.text = if (prof.bmi != null) {
            "Body Mass Index: ${prof.bmi} (${prof.bmiCategory ?: "N/A"})"
        } else {
            "Body Mass Index: Height/Weight unrecorded"
        }

        binding.tvRchrProfileAllergies.text = if (prof.allergyList.isNotEmpty()) {
            "Allergies (${prof.allergyCount}): ${prof.allergyList.joinToString(", ")}"
        } else {
            "Allergies: None documented"
        }

        binding.tvRchrProfileChronic.text = if (prof.chronicConditionList.isNotEmpty()) {
            "Chronic Conditions (${prof.chronicConditionCount}): ${prof.chronicConditionList.joinToString(", ")}"
        } else {
            "Chronic Conditions: None documented"
        }

        // 3. Symptoms Features
        val sym = rep.symptomFeatures
        binding.tvRchrSymptomsCounts.text = "Distinct Symptoms: ${sym.distinctSymptomCount} • Recent (30d): ${sym.recentSymptomCount}"
        binding.tvRchrSymptomsFrequent.text = if (sym.frequentSymptoms.isNotEmpty()) {
            "Frequent: " + sym.frequentSymptoms.take(4).joinToString(", ")
        } else {
            "Frequent: No symptom history"
        }
        binding.tvRchrSymptomsRecurring.text = if (sym.recurringSymptoms.isNotEmpty()) {
            "Recurring (≥2x): " + sym.recurringSymptoms.joinToString(", ")
        } else {
            "Recurring (≥2x): None detected"
        }

        // 4. Prediction History Features
        val pred = rep.predictionFeatures
        val domDisease = pred.dominantPredictedDisease ?: "None"
        binding.tvRchrPredSummary.text = "Total Predictions: ${pred.totalPredictionCount} • Dominant Output: $domDisease"
        binding.tvRchrPredTop.text = if (pred.topPredictedDiseases.isNotEmpty()) {
            "Top Outputs: " + pred.topPredictedDiseases.take(3).joinToString(", ")
        } else {
            "Top Outputs: No predictions recorded"
        }
        binding.tvRchrPredConfidence.text = if (pred.averageConfidence != null) {
            val avgPct = (pred.averageConfidence * 100).toInt()
            val minPct = ((pred.confidenceRangeMin ?: 0f) * 100).toInt()
            val maxPct = ((pred.confidenceRangeMax ?: 0f) * 100).toInt()
            "Model Avg Confidence: $avgPct% (Range: $minPct% – $maxPct%)"
        } else {
            "Model Avg Confidence: N/A"
        }

        // 5. Medications & Adherence Features
        val med = rep.medicationFeatures
        binding.tvRchrMedActive.text = if (med.hasActiveMedications) {
            "Active Medications (${med.activeMedicationCount}): ${med.activeMedicationNames.joinToString(", ")}"
        } else {
            "Active Medications: None tracked"
        }

        val adh = rep.adherenceFeatures
        binding.tvRchrMedAdherence.text = if (adh.adherencePercentage != null) {
            "Adherence Rate: ${adh.adherencePercentage.toInt()}% (${adh.adherenceCategory ?: "N/A"})"
        } else {
            "Adherence Rate: No dose logs"
        }
        binding.tvRchrMedDoses.text = "Recorded Doses: ${adh.takenCount} Taken • ${adh.missedCount} Missed • ${adh.skippedCount} Skipped"

        // 6. Appointments Features
        val appt = rep.appointmentFeatures
        binding.tvRchrApptUpcoming.text = if (appt.upcomingAppointmentCount > 0 && appt.nextAppointmentDoctor != null) {
            val dateStr = appt.nextAppointmentDate?.let { " on $it" } ?: ""
            "Upcoming Appointments (${appt.upcomingAppointmentCount}): Next with ${appt.nextAppointmentDoctor}$dateStr"
        } else {
            "Upcoming Appointments: None scheduled"
        }
        binding.tvRchrApptPast.text = "Completed Past Appointments: ${appt.pastAppointmentCount}"

        // 7. Temporal Dynamics Features
        val temp = rep.temporalFeatures
        binding.tvRchrTemporalTrends.text = "Prediction Trend: ${temp.predictionActivityTrend.name} • Adherence: ${temp.adherenceTrend.name}"
        binding.tvRchrTemporalPatterns.text = if (temp.detectedPatternTitles.isNotEmpty()) {
            "Detected Patterns (${temp.detectedPatternsCount}): ${temp.detectedPatternTitles.joinToString("; ")}"
        } else {
            "Detected Patterns: None in this period"
        }

        // 8. Context Features
        val ctx = rep.contextFeatures
        binding.tvRchrContextScore.text = "Context Engagement Score: ${ctx.personalizationScore.toInt()} / 100"
        binding.tvRchrContextSummary.text = ctx.contextSummary

        // 9. Reconstruction Results
        if (reconstruction != null) {
            binding.cardReconstructionResults.visibility = View.VISIBLE
            val scorePct = reconstruction.reconstructionConsistencyScore.toInt()
            binding.tvConsistencyScoreBadge.text = "Consistency: $scorePct%"
            attributeAdapter.submitList(reconstruction.reconstructedAttributes)

            if (reconstruction.unavailableAttributes.isNotEmpty()) {
                binding.layoutUnavailableAttributes.visibility = View.VISIBLE
                val formattedUnavailable = reconstruction.unavailableAttributes.joinToString("\n") { "• $it" }
                binding.tvUnavailableAttributesText.text = formattedUnavailable
            } else {
                binding.layoutUnavailableAttributes.visibility = View.GONE
            }
        } else {
            binding.cardReconstructionResults.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
