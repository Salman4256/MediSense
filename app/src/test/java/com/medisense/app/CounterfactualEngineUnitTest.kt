package com.medisense.app

import com.medisense.app.data.model.ContributionDirection
import com.medisense.app.data.model.DiseasePrediction
import com.medisense.app.data.model.PredictionExplanation
import com.medisense.app.data.model.Symptom
import com.medisense.app.data.model.XaiFeatureContribution
import com.medisense.app.data.repository.DiseasePredictionRepository
import com.medisense.app.domain.counterfactual.CounterfactualExplanationEngine
import com.medisense.app.domain.model.ConfidenceLevel
import com.medisense.app.domain.model.CounterfactualChangeType
import com.medisense.app.domain.model.ModelSensitivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CounterfactualEngineUnitTest {

    private class FakeDiseasePredictionRepository : DiseasePredictionRepository(null, null) {
        var predictHandler: ((List<Symptom>) -> List<DiseasePrediction>)? = null

        override suspend fun predict(selectedSymptoms: List<Symptom>): List<DiseasePrediction> {
            return predictHandler?.invoke(selectedSymptoms) ?: emptyList()
        }
    }

    private lateinit var fakeRepository: FakeDiseasePredictionRepository
    private lateinit var counterfactualEngine: CounterfactualExplanationEngine

    @Before
    fun setUp() {
        fakeRepository = FakeDiseasePredictionRepository()
        counterfactualEngine = CounterfactualExplanationEngine(fakeRepository)
    }

    @Test
    fun `test insufficient symptoms returns safe fallback`() = runBlocking {
        val primary = DiseasePrediction("Common Cold", 0.75f, 1)
        val symptoms = listOf(Symptom(0, "Cough", "cough"))
        val explanation = PredictionExplanation("Common Cold", 0.75f, emptyList(), "Summary")

        val result = counterfactualEngine.evaluateCounterfactuals(primary, symptoms, explanation)

        assertFalse(result.hasSufficientSymptoms)
        assertEquals(ModelSensitivity.INSUFFICIENT_DATA, result.sensitivity)
        assertTrue(result.counterfactuals.isEmpty())
        assertTrue(result.sensitivityExplanation.contains("At least 2 symptoms are needed"))
    }

    @Test
    fun `test top 3 symptom selection and prediction change detection`() = runBlocking {
        fakeRepository.predictHandler = { currentSymptoms ->
            val symptomNames = currentSymptoms.map { it.modelFeatureName }
            when {
                // If "itching" was removed (not in currentSymptoms) -> Allergy becomes top prediction (0.70)
                !symptomNames.contains("itching") -> listOf(
                    DiseasePrediction("Allergy", 0.70f, 1),
                    DiseasePrediction("Fungal Infection", 0.20f, 2),
                    DiseasePrediction("Common Cold", 0.10f, 3)
                )
                // If "skin_rash" was removed -> Fungal Infection remains top (0.60)
                !symptomNames.contains("skin_rash") -> listOf(
                    DiseasePrediction("Fungal Infection", 0.60f, 1),
                    DiseasePrediction("Allergy", 0.25f, 2),
                    DiseasePrediction("Common Cold", 0.15f, 3)
                )
                // If "nodal_skin_eruptions" was removed -> Fungal Infection remains top (0.80)
                !symptomNames.contains("nodal_skin_eruptions") -> listOf(
                    DiseasePrediction("Fungal Infection", 0.80f, 1),
                    DiseasePrediction("Allergy", 0.15f, 2),
                    DiseasePrediction("Common Cold", 0.05f, 3)
                )
                // Full set default
                else -> listOf(
                    DiseasePrediction("Fungal Infection", 0.90f, 1),
                    DiseasePrediction("Allergy", 0.05f, 2),
                    DiseasePrediction("Common Cold", 0.05f, 3)
                )
            }
        }

        val selectedSymptoms = listOf(
            Symptom(0, "Itching", "itching"),
            Symptom(1, "Skin Rash", "skin_rash"),
            Symptom(2, "Nodal Eruptions", "nodal_skin_eruptions"),
            Symptom(3, "Sneezing", "continuous_sneezing"),
            Symptom(4, "Shivering", "shivering")
        )

        val contributions = listOf(
            XaiFeatureContribution("itching", "Itching", 1.0f, ContributionDirection.SUPPORTS, 0.9f),
            XaiFeatureContribution("skin_rash", "Skin Rash", 0.8f, ContributionDirection.SUPPORTS, 0.8f),
            XaiFeatureContribution("nodal_skin_eruptions", "Nodal Eruptions", 0.6f, ContributionDirection.SUPPORTS, 0.6f),
            XaiFeatureContribution("continuous_sneezing", "Sneezing", 0.3f, ContributionDirection.SUPPORTS, 0.3f),
            XaiFeatureContribution("shivering", "Shivering", 0.1f, ContributionDirection.SUPPORTS, 0.1f)
        )

        val primary = DiseasePrediction("Fungal Infection", 0.90f, 1)
        val explanation = PredictionExplanation("Fungal Infection", 0.90f, contributions, "Summary")

        val result = counterfactualEngine.evaluateCounterfactuals(primary, selectedSymptoms, explanation)

        assertTrue(result.hasSufficientSymptoms)
        assertEquals(ConfidenceLevel.HIGH, result.confidenceSummary.confidenceLevel)
        assertEquals(3, result.counterfactuals.size) // Capped at top 3

        // Assert Model Sensitivity is SENSITIVE because itching removal changed prediction to Allergy
        assertEquals(ModelSensitivity.SENSITIVE, result.sensitivity)

        // First ranked counterfactual should be the one where prediction changed (Itching)
        val topCounterfactual = result.counterfactuals.first()
        assertEquals("itching", topCounterfactual.removedSymptom)
        assertTrue(topCounterfactual.isPredictionChanged)
        assertEquals(CounterfactualChangeType.CHANGED_PREDICTION, topCounterfactual.changeType)
        assertEquals("Allergy", topCounterfactual.resultingPrediction)
        assertTrue(topCounterfactual.explanation.contains("shifts the model's top prediction"))

        // Second counterfactual (Skin Rash) has confidence reduction with same prediction
        val secondCounterfactual = result.counterfactuals[1]
        assertEquals("skin_rash", secondCounterfactual.removedSymptom)
        assertFalse(secondCounterfactual.isPredictionChanged)
        assertEquals("Fungal Infection", secondCounterfactual.resultingPrediction)
        assertTrue(secondCounterfactual.explanation.contains("reduces model confidence"))
    }

    @Test
    fun `test stable sensitivity when all counterfactuals maintain top prediction`() = runBlocking {
        fakeRepository.predictHandler = { currentSymptoms ->
            val symptomNames = currentSymptoms.map { it.modelFeatureName }
            when {
                !symptomNames.contains("cough") -> listOf(
                    DiseasePrediction("Common Cold", 0.65f, 1),
                    DiseasePrediction("Influenza", 0.35f, 2)
                )
                !symptomNames.contains("fever") -> listOf(
                    DiseasePrediction("Common Cold", 0.70f, 1),
                    DiseasePrediction("Influenza", 0.30f, 2)
                )
                else -> listOf(
                    DiseasePrediction("Common Cold", 0.85f, 1),
                    DiseasePrediction("Influenza", 0.15f, 2)
                )
            }
        }

        val selectedSymptoms = listOf(
            Symptom(0, "Cough", "cough"),
            Symptom(1, "Fever", "fever")
        )

        val contributions = listOf(
            XaiFeatureContribution("cough", "Cough", 0.9f, ContributionDirection.SUPPORTS, 0.9f),
            XaiFeatureContribution("fever", "Fever", 0.8f, ContributionDirection.SUPPORTS, 0.8f)
        )

        val primary = DiseasePrediction("Common Cold", 0.85f, 1)
        val explanation = PredictionExplanation("Common Cold", 0.85f, contributions, "Summary")

        val result = counterfactualEngine.evaluateCounterfactuals(primary, selectedSymptoms, explanation)

        assertEquals(ModelSensitivity.STABLE, result.sensitivity)
        assertEquals(2, result.counterfactuals.size)
        assertFalse(result.counterfactuals.any { it.isPredictionChanged })
        assertTrue(result.sensitivityExplanation.contains("remained unchanged across all tested symptom removals"))
    }

    @Test
    fun `test non-diagnostic language in counterfactual explanations`() = runBlocking {
        fakeRepository.predictHandler = { currentSymptoms ->
            val symptomNames = currentSymptoms.map { it.modelFeatureName }
            when {
                !symptomNames.contains("fatigue") -> listOf(
                    DiseasePrediction("Migraine", 0.60f, 1),
                    DiseasePrediction("Tension Headache", 0.40f, 2)
                )
                !symptomNames.contains("headache") -> listOf(
                    DiseasePrediction("Migraine", 0.55f, 1),
                    DiseasePrediction("Tension Headache", 0.45f, 2)
                )
                else -> listOf(
                    DiseasePrediction("Migraine", 0.80f, 1),
                    DiseasePrediction("Tension Headache", 0.20f, 2)
                )
            }
        }

        val selectedSymptoms = listOf(
            Symptom(0, "Fatigue", "fatigue"),
            Symptom(1, "Headache", "headache")
        )

        val primary = DiseasePrediction("Migraine", 0.80f, 1)
        val explanation = PredictionExplanation("Migraine", 0.80f, emptyList(), "Summary")

        val result = counterfactualEngine.evaluateCounterfactuals(primary, selectedSymptoms, explanation)

        val combinedText = buildString {
            append(result.sensitivityExplanation).append(" ")
            result.counterfactuals.forEach {
                append(it.explanation).append(" ")
            }
        }.lowercase()

        // Assert forbidden diagnostic phrasing does not appear
        assertFalse(combinedText.contains("you definitely have"))
        assertFalse(combinedText.contains("clinical diagnosis"))
        assertFalse(combinedText.contains("prescribe"))
        assertFalse(combinedText.contains("take medicine"))
        assertFalse(combinedText.contains("medical certainty"))
    }
}
