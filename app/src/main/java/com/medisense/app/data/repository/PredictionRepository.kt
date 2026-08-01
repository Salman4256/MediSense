package com.medisense.app.data.repository

import com.medisense.app.data.local.dao.PredictionDao
import com.medisense.app.data.local.entity.PredictionEntity
import com.medisense.app.ml.predictor.DiseasePredictor
import com.medisense.app.ml.predictor.PredictionResult
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PredictionRepository @Inject constructor(
    private val predictionDao: PredictionDao,
    private val diseasePredictor: DiseasePredictor
) {

    // Mock method to get current user ID
    private fun getCurrentUserId(): String {
        return "mock-supabase-user-id"
    }

    suspend fun predictDisease(selectedSymptoms: List<String>): List<PredictionResult> {
        val results = diseasePredictor.predict(selectedSymptoms)
        
        if (results.isNotEmpty()) {
            val topDisease = results.first()
            val entity = PredictionEntity(
                userId = getCurrentUserId(),
                selectedSymptoms = selectedSymptoms.joinToString(","),
                topDisease = topDisease.diseaseName,
                confidence = topDisease.confidence,
                pendingSync = true
            )
            predictionDao.insertPrediction(entity)
        }
        
        return results
    }

    fun getPredictionHistory(): Flow<List<PredictionEntity>> {
        return predictionDao.getPredictions(getCurrentUserId())
    }
}
