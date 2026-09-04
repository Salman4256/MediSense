package com.medisense.app.ml.xai

import com.medisense.app.data.local.xai.XaiMetadataParser
import com.medisense.app.data.model.ContributionDirection
import com.medisense.app.data.model.PredictionExplanation
import com.medisense.app.data.model.Symptom
import com.medisense.app.data.model.XaiFeatureContribution
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XaiExplanationEngine @Inject constructor(
    private val parser: XaiMetadataParser
) {

    fun generateExplanation(
        diseaseName: String,
        probability: Float,
        selectedSymptoms: List<Symptom>
    ): PredictionExplanation {
        val normalizedDisease = diseaseName.trim().lowercase()
        val featureWeights = parser.getFeatureImportances()[normalizedDisease] ?: emptyMap()
        val diseaseRules = parser.getDiseaseRules()
        val displayNames = parser.getFeatureDisplayNames()

        val contributions = selectedSymptoms.map { symptom ->
            val normFeature = symptom.modelFeatureName.trim().lowercase().replace("\\s+".toRegex(), " ")
            val weight = featureWeights[normFeature] ?: 0.1f
            val displayName = displayNames[normFeature] ?: symptom.displayName

            XaiFeatureContribution(
                featureName = symptom.modelFeatureName,
                displayName = displayName,
                contribution = weight,
                direction = if (weight > 0) ContributionDirection.SUPPORTS else ContributionDirection.NEUTRAL,
                importance = weight
            )
        }.sortedByDescending { it.contribution }

        val maxContribution = contributions.maxOfOrNull { it.contribution } ?: 1.0f
        val normalizedContributions = contributions.map {
            it.copy(contribution = if (maxContribution > 0) (it.contribution / maxContribution) else 0f)
        }

        val topSymptoms = normalizedContributions.take(3).joinToString(", ") { it.displayName }
        val ruleText = diseaseRules[normalizedDisease]
        val summary = if (!ruleText.isNullOrBlank()) {
            ruleText
        } else if (topSymptoms.isNotBlank()) {
            "Key symptoms including $topSymptoms significantly contributed to the model's confidence for this condition."
        } else {
            "The model evaluated your reported symptoms against historical disease indicators."
        }

        return PredictionExplanation(
            diseaseName = diseaseName,
            probability = probability,
            contributions = normalizedContributions,
            summary = summary,
            modelVersion = parser.getModelVersion(),
            isAvailable = true
        )
    }
}
