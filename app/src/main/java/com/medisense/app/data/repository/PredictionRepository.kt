package com.medisense.app.data.repository

import com.medisense.app.data.local.dao.PredictionDao
import com.medisense.app.data.remote.supabase.AuthService
import com.medisense.app.ml.predictor.DiseasePredictor
import com.medisense.app.ml.xai.ExplanationEngine
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@Singleton
class PredictionRepository @Inject constructor(
    private val predictionDao: PredictionDao,
    private val diseasePredictor: DiseasePredictor,
    private val firebaseAuthService: AuthService,
    private val explanationEngine: ExplanationEngine
) {
    suspend fun predictDisease(symptoms: List<String>): Result<Any> {
        return Result.failure(Exception("Not implemented"))
    }

    fun getPredictions(): Flow<List<Any>> {
        return emptyFlow()
    }

    suspend fun syncPendingPredictions() {}
}
