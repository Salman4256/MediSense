package com.medisense.app.data.repository

import com.medisense.app.data.local.dao.ExplanationDao
import com.medisense.app.data.local.entity.ExplanationEntity
import com.medisense.app.ml.xai.ExplanationEngine
import com.medisense.app.ml.xai.ExplanationRepository
import com.medisense.app.ml.xai.ExplanationResult
import com.medisense.app.ml.xai.SymptomContribution
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExplanationDataRepository @Inject constructor(
    private val explanationDao: ExplanationDao,
    private val explanationEngine: ExplanationEngine
) : ExplanationRepository {

    override suspend fun generateAndSaveExplanation(
        predictionId: Int,
        selectedSymptoms: List<String>,
        predictedDisease: String
    ): ExplanationResult {
        // Generate explanation
        val result = explanationEngine.explainPrediction(selectedSymptoms, predictedDisease)

        // Convert to entity
        val explanationText = result.contributingSymptoms.joinToString(separator = "\n\n") { "• ${it.explanation}" }
        val contributingSymptomsCsv = result.contributingSymptoms.joinToString(",") { it.symptomName }
        val nonContributingSymptomsCsv = result.nonContributingSymptoms.joinToString(",")

        val entity = ExplanationEntity(
            predictionId = predictionId,
            explanationText = explanationText,
            contributingSymptoms = contributingSymptomsCsv,
            nonContributingSymptoms = nonContributingSymptomsCsv
        )

        // Save to DB
        explanationDao.insertExplanation(entity)

        return result
    }

    override suspend fun getExplanationForPrediction(predictionId: Int): ExplanationResult? {
        val entity = explanationDao.getExplanationForPrediction(predictionId).firstOrNull() ?: return null
        
        // This is a simplified reconstruction. In a real app we might store JSON.
        // But for this requirement, if we just need to display it, we reconstruct it:
        val contributingList = if (entity.contributingSymptoms.isNotBlank()) {
            val symptomNames = entity.contributingSymptoms.split(",")
            val explanations = entity.explanationText.split("\n\n").map { it.removePrefix("• ") }
            symptomNames.mapIndexed { index, name ->
                SymptomContribution(
                    symptomName = name,
                    explanation = explanations.getOrElse(index) { "Contributed to prediction." },
                    weight = 0f // Weight is lost in simple string storage, but UI mainly needs text
                )
            }
        } else {
            emptyList()
        }

        val nonContributingList = if (entity.nonContributingSymptoms.isNotBlank()) {
            entity.nonContributingSymptoms.split(",")
        } else {
            emptyList()
        }

        return ExplanationResult(
            predictedDisease = "", // Not strictly needed here, typically fetched from PredictionEntity
            contributingSymptoms = contributingList,
            nonContributingSymptoms = nonContributingList
        )
    }

    override suspend fun getRecommendedCare(diseaseName: String): List<String> {
        return when (diseaseName.lowercase()) {
            "influenza", "common cold" -> listOf(
                "Drink plenty of water.",
                "Take proper rest.",
                "Monitor your temperature.",
                "Visit a doctor if symptoms worsen."
            )
            "dengue", "malaria" -> listOf(
                "Stay hydrated with fluids like ORS.",
                "Monitor your body temperature and platelet count.",
                "Rest thoroughly in a mosquito-free environment.",
                "Seek immediate medical attention."
            )
            "typhoid" -> listOf(
                "Consume safe, boiled drinking water.",
                "Eat easily digestible, hygienic food.",
                "Complete the prescribed antibiotic course.",
                "Monitor for abdominal pain."
            )
            "migraine" -> listOf(
                "Rest in a quiet, dark room.",
                "Stay hydrated and avoid known triggers.",
                "Apply a cold or warm compress to your head or neck.",
                "Take pain relievers early if prescribed."
            )
            "diabetes" -> listOf(
                "Monitor your blood sugar levels regularly.",
                "Maintain a balanced, low-sugar diet.",
                "Engage in regular physical activity.",
                "Consult an endocrinologist."
            )
            else -> listOf(
                "Monitor your symptoms closely.",
                "Ensure adequate rest and hydration.",
                "Maintain a healthy and balanced diet.",
                "Consult a healthcare provider for proper diagnosis."
            )
        }
    }
}
