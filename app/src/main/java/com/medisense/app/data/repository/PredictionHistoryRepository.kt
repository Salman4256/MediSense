package com.medisense.app.data.repository

import com.medisense.app.data.local.dao.PredictionHistoryDao
import com.medisense.app.data.local.entity.PredictionHistoryEntity
import com.medisense.app.data.remote.supabase.AuthService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PredictionHistoryRepository @Inject constructor(
    private val predictionHistoryDao: PredictionHistoryDao,
    private val authService: AuthService
) {

    fun getCurrentUserId(): String {
        return authService.getCurrentUserId() ?: "offline_user"
    }

    /**
     * Observes all prediction history records for the authenticated user (newest first).
     */
    fun observeHistory(): Flow<List<PredictionHistoryEntity>> {
        val userId = getCurrentUserId()
        return predictionHistoryDao.observePredictionHistory(userId)
    }

    /**
     * Retrieves a single historical prediction by ID ensuring user ownership.
     */
    suspend fun getHistoryById(id: Long): PredictionHistoryEntity? = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        predictionHistoryDao.getPredictionHistoryById(id, userId)
    }

    /**
     * Saves a completed disease prediction into Room database associated with the authenticated user.
     */
    suspend fun savePredictionResult(
        predictedDisease: String,
        confidence: Float,
        symptoms: List<String>,
        explanationSummary: String? = null,
        modelVersion: String = "1.0"
    ): Long = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        val record = PredictionHistoryEntity(
            userId = userId,
            predictedDisease = predictedDisease,
            confidence = confidence,
            symptoms = symptoms,
            explanationSummary = explanationSummary,
            predictionTimestamp = System.currentTimeMillis(),
            modelVersion = modelVersion,
            pendingSync = true
        )
        predictionHistoryDao.insertPredictionHistory(record)
    }

    /**
     * Deletes a single history record ensuring user ownership.
     */
    suspend fun deleteHistoryItem(id: Long) = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        predictionHistoryDao.deletePredictionHistory(id, userId)
    }

    /**
     * Deletes all prediction history records for the authenticated user.
     */
    suspend fun deleteAllHistory() = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        predictionHistoryDao.deleteAllPredictionHistory(userId)
    }

    /**
     * Retrieves all unsynced prediction history records for the current user.
     */
    suspend fun getPendingSyncRecords(): List<PredictionHistoryEntity> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        predictionHistoryDao.getPendingSyncPredictionHistory(userId)
    }
}
