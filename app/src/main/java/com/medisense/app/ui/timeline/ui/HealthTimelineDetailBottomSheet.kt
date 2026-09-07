package com.medisense.app.ui.timeline.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.medisense.app.databinding.BottomSheetHealthTimelineDetailBinding
import com.medisense.app.domain.model.HealthTimelineEvent
import com.medisense.app.domain.model.HealthTimelinePriority
import com.medisense.app.domain.model.TimelineNavigationTarget
import java.text.SimpleDateFormat
import java.util.*

class HealthTimelineDetailBottomSheet(
    private val event: HealthTimelineEvent,
    private val onNavigateTarget: (TimelineNavigationTarget, HealthTimelineEvent) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetHealthTimelineDetailBinding? = null
    private val binding get() = _binding!!

    private val dateFormatter = SimpleDateFormat("EEEE, MMMM d, yyyy • h:mm:ss a", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetHealthTimelineDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Basic Header Information
        binding.tvDetailTitle.text = event.title
        binding.tvDetailCategory.text = event.eventType.displayName
        binding.tvDetailSource.text = "Attributed Origin: ${event.source.displayName}"

        try {
            binding.tvDetailTime.text = dateFormatter.format(Date(event.timestamp))
        } catch (e: Exception) {
            binding.tvDetailTime.text = "${event.timestamp}"
        }

        // Priority Badge
        if (event.priority == HealthTimelinePriority.IMPORTANT || event.priority == HealthTimelinePriority.ATTENTION) {
            binding.tvDetailPriority.visibility = View.VISIBLE
            binding.tvDetailPriority.text = if (event.priority == HealthTimelinePriority.ATTENTION) "Attention Required" else "Important"
        } else {
            binding.tvDetailPriority.visibility = View.GONE
        }

        // 2. Detailed Description
        binding.tvDetailDescription.text = event.detailedDescription

        // 3. Explainability Capsule (WHAT, WHEN, WHY, WHERE)
        binding.tvExplainWhat.text = "• WHAT: ${event.explanation.whatHappened}"
        binding.tvExplainWhen.text = "• WHEN: ${event.explanation.whenOccurred}"
        binding.tvExplainWhy.text = "• WHY: ${event.explanation.whyShown}"
        binding.tvExplainWhere.text = "• WHERE: ${event.explanation.sourceAttribution}"

        // 4. Metadata key-values if available
        if (event.metadata.isNotEmpty()) {
            binding.layoutMetadataContainer.visibility = View.VISIBLE
            val metaStr = event.metadata.entries.joinToString("\n") { "• ${it.key}: ${it.value}" }
            binding.tvMetadataContent.text = metaStr
        } else {
            binding.layoutMetadataContainer.visibility = View.GONE
        }

        // 5. Navigation Action Button
        if (event.navigationTarget != TimelineNavigationTarget.NONE) {
            binding.btnNavigateToModule.visibility = View.VISIBLE
            val buttonLabel = when (event.navigationTarget) {
                TimelineNavigationTarget.PREDICTION_DETAIL,
                TimelineNavigationTarget.PREDICTION_HISTORY -> "Open in Prediction History"
                TimelineNavigationTarget.MEDICATION,
                TimelineNavigationTarget.MEDICATION_HISTORY -> "Open in Medications"
                TimelineNavigationTarget.APPOINTMENT -> "Open in Appointments"
                TimelineNavigationTarget.HEALTH_TRENDS -> "Open in Health Trends"
                TimelineNavigationTarget.CONTEXTUAL_RISK -> "Open in Contextual Risk"
                TimelineNavigationTarget.PERSONALIZED_GUIDANCE -> "Open in Personalized Guidance"
                TimelineNavigationTarget.HEALTH_DATA_QUALITY -> "Open in Health Data Quality"
                TimelineNavigationTarget.HEALTH_REPORT -> "Open in Health Report"
                TimelineNavigationTarget.PROFILE -> "Open in Profile"
                TimelineNavigationTarget.NONE -> ""
            }
            binding.btnNavigateToModule.text = buttonLabel
            binding.btnNavigateToModule.setOnClickListener {
                dismiss()
                onNavigateTarget(event.navigationTarget, event)
            }
        } else {
            binding.btnNavigateToModule.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "HealthTimelineDetailBottomSheet"
    }
}
