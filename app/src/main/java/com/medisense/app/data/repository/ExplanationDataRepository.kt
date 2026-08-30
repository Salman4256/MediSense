package com.medisense.app.data.repository

import com.medisense.app.data.local.dao.ExplanationDao
import com.medisense.app.data.local.entity.ExplanationEntity
import com.medisense.app.ml.xai.ExplanationEngine
import com.medisense.app.ml.xai.ExplanationRepository
import com.medisense.app.ml.xai.ExplanationResult
import com.medisense.app.ml.xai.SymptomContribution
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
        val result = explanationEngine.explainPrediction(selectedSymptoms, predictedDisease)

        val entity = ExplanationEntity(
            predictionId = predictionId,
            explanationText = result.predictedDisease,
            contributingSymptoms = result.contributingSymptoms.joinToString(",") { it.symptomName },
            nonContributingSymptoms = result.nonContributingSymptoms.joinToString(","),
            createdAt = System.currentTimeMillis()
        )
        explanationDao.insertExplanation(entity)

        return result
    }

    override suspend fun getExplanationForPrediction(predictionId: Int): ExplanationResult? {
        val entity = explanationDao.getExplanationSnapshotForPrediction(predictionId) ?: return null
        return ExplanationResult(
            predictedDisease = entity.explanationText,
            contributingSymptoms = entity.contributingSymptoms.split(",")
                .filter { it.isNotBlank() }
                .map { SymptomContribution(it, "", 1.0f) },
            nonContributingSymptoms = entity.nonContributingSymptoms.split(",").filter { it.isNotBlank() }
        )
    }

    override suspend fun getRecommendedCare(diseaseName: String): List<String> {
        return listOf(
            "Consult a general physician or specialist for proper clinical diagnosis.",
            "Stay hydrated and ensure adequate rest.",
            "Monitor symptoms and seek immediate emergency care if conditions worsen."
        )
    }
}
