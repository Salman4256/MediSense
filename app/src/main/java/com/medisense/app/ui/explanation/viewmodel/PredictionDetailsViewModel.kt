package com.medisense.app.ui.explanation.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medisense.app.ml.xai.ExplanationRepository
import com.medisense.app.ml.xai.ExplanationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class PredictionDetailsViewModel @Inject constructor(
    private val explanationRepository: ExplanationRepository
) : ViewModel() {

    enum class RiskLevel { LOW, MEDIUM, HIGH }

    // Navigation Arguments provided by Fragment
    private var predictionId: Int = -1
    private var diseaseName: String = ""
    private var confidence: Float = 0f
    private var selectedSymptoms: List<String> = emptyList()

    // Screen State
    private val _explanationResult = MutableLiveData<ExplanationResult>()
    val explanationResult: LiveData<ExplanationResult> = _explanationResult

    private val _recommendedCare = MutableLiveData<List<String>>()
    val recommendedCare: LiveData<List<String>> = _recommendedCare

    private val _riskLevel = MutableLiveData<RiskLevel>()
    val riskLevel: LiveData<RiskLevel> = _riskLevel

    private val _loading = MutableLiveData<Boolean>(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    fun initialize(id: Int, name: String, conf: Float, symptoms: Array<String>) {
        // Prevent re-initialization on config change if already loaded
        if (predictionId == id) return

        predictionId = id
        diseaseName = name
        confidence = conf
        selectedSymptoms = symptoms.toList()

        calculateRiskLevel()
        loadExplanationData()
    }

    private fun calculateRiskLevel() {
        val percentage = (confidence * 100).roundToInt()
        _riskLevel.value = when {
            percentage <= 40 -> RiskLevel.LOW
            percentage <= 75 -> RiskLevel.MEDIUM
            else -> RiskLevel.HIGH
        }
    }

    private fun loadExplanationData() {
        _loading.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                // 1. Check if explanation is already saved in DB
                var explanation = explanationRepository.getExplanationForPrediction(predictionId)
                
                // 2. If not saved (e.g. fresh prediction), generate and save it now
                if (explanation == null || explanation.contributingSymptoms.isEmpty()) {
                    explanation = explanationRepository.generateAndSaveExplanation(
                        predictionId = predictionId,
                        selectedSymptoms = selectedSymptoms,
                        predictedDisease = diseaseName
                    )
                }

                _explanationResult.value = explanation!!

                // 3. Load Recommendations
                val recommendations = explanationRepository.getRecommendedCare(diseaseName)
                _recommendedCare.value = recommendations

            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load prediction details."
            } finally {
                _loading.value = false
            }
        }
    }
}
