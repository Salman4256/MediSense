package com.medisense.app.data.repository

import com.medisense.app.data.local.dao.PredictionDao
import com.medisense.app.data.local.entity.PredictionEntity
import com.medisense.app.ml.predictor.DiseasePredictor
import com.medisense.app.ml.predictor.PredictionResult
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository that mediates between the DiseasePredictor (ML layer)
 * and the local Room database (PredictionDao).
 *
 * Supabase sync is intentionally NOT implemented — the [pendingSync]
 * flag on every [PredictionEntity] marks records that are ready for
 * future remote upload.
 */
@Singleton
class PredictionRepository @Inject constructor(
    private val predictionDao: PredictionDao,
    private val diseasePredictor: DiseasePredictor,
    private val firebaseAuthService: com.medisense.app.data.remote.firebase.FirebaseAuthService,
    private val explanationEngine: com.medisense.app.ml.xai.ExplanationEngine
) {

    // -------------------------------------------------------------------------
    // Symptom list (sourced from the predictor's known symptom set)
    // -------------------------------------------------------------------------

    /**
     * Returns the human-readable list of available symptoms for the UI.
     * These are the display names the user selects from chips.
     */
    fun getAllSymptoms(): List<String> = DISPLAY_SYMPTOMS

    // -------------------------------------------------------------------------
    // Prediction
    // -------------------------------------------------------------------------

    /**
     * Runs offline disease prediction for [selectedSymptoms], persists the
     * top result to Room, and returns the full ranked list.
     *
     * All work is done locally — no network call is made.
     */
    suspend fun predictDisease(selectedSymptoms: List<String>): Triple<Long, List<PredictionResult>, com.medisense.app.ml.xai.ExplanationResult?> {
        val results = diseasePredictor.predict(selectedSymptoms)
        var predictionId = -1L
        var explanationResult: com.medisense.app.ml.xai.ExplanationResult? = null

        if (results.isNotEmpty()) {
            val topDisease = results.first()
            explanationResult = explanationEngine.generateExplanation(
                diseaseName = topDisease.diseaseName,
                selectedSymptoms = selectedSymptoms,
                confidence = topDisease.confidence
            )

            val entity = PredictionEntity(
                userId = getCurrentUserId(),
                selectedSymptoms = selectedSymptoms.joinToString(","),
                topDisease = topDisease.diseaseName,
                confidence = topDisease.confidence,
                pendingSync = true,
                explanation = explanationResult.explanationText,
                contributingSymptoms = explanationResult.contributingSymptoms.joinToString(",")
            )
            predictionId = predictionDao.insertPrediction(entity)
        }

        return Triple(predictionId, results, explanationResult)
    }

    // -------------------------------------------------------------------------
    // History (prepared for future Supabase sync)
    // -------------------------------------------------------------------------

    /**
     * Returns a Flow of all locally stored predictions for the current user.
     * This is ready to be merged with remote data when Supabase sync is added.
     */
    fun getPredictionHistory(): Flow<List<PredictionEntity>> {
        return predictionDao.getPredictions(getCurrentUserId())
    }

    /**
     * Returns prediction records that haven't been synced to Supabase yet.
     * Intended for use by a future background sync worker.
     */
    fun getPendingSyncPredictions(): Flow<List<PredictionEntity>> {
        return predictionDao.getPendingSyncPredictions(getCurrentUserId())
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the actual user ID from the Firebase auth session.
     */
    private fun getCurrentUserId(): String = firebaseAuthService.getCurrentUser()?.uid ?: "local-user"


    companion object {
        /**
         * Curated list of common symptoms shown to the user.
         * Maps 1-to-1 to the internal keys used in symptoms.json.
         */
        val DISPLAY_SYMPTOMS = listOf(
            "Fever",
            "High Fever",
            "Mild Fever",
            "Cough",
            "Headache",
            "Fatigue",
            "Vomiting",
            "Nausea",
            "Diarrhoea",
            "Chest Pain",
            "Sore Throat",
            "Body Pain",
            "Dizziness",
            "Runny Nose",
            "Chills",
            "Sneezing",
            "Loss of Appetite",
            "Breathlessness",
            "Joint Pain",
            "Muscle Pain",
            "Stomach Pain",
            "Abdominal Pain",
            "Back Pain",
            "Neck Pain",
            "Skin Rash",
            "Itching",
            "Sweating",
            "Dehydration",
            "Constipation",
            "Indigestion",
            "Swollen Legs",
            "Swelling Joints",
            "Loss of Smell",
            "Blurred Vision",
            "Palpitations",
            "Anxiety",
            "Depression",
            "Weight Loss",
            "Excessive Hunger",
            "Increased Appetite",
            "Polyuria",
            "Burning Urination",
            "Fast Heart Rate",
            "Weakness in Limbs",
            "Stiff Neck"
        )
    }
}
