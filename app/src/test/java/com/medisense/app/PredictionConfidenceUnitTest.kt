package com.medisense.app

import com.medisense.app.data.model.DiseasePrediction
import com.medisense.app.domain.counterfactual.PredictionConfidenceInterpreter
import com.medisense.app.domain.model.ConfidenceLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictionConfidenceUnitTest {

    @Test
    fun `test high confidence interpretation`() {
        val prediction = DiseasePrediction(
            diseaseName = "Fungal Infection",
            probability = 0.88f,
            rank = 1
        )

        val summary = PredictionConfidenceInterpreter.interpret(prediction, symptomCount = 3)

        assertEquals("Fungal Infection", summary.topPrediction)
        assertEquals(0.88f, summary.confidenceValue, 0.001f)
        assertEquals(88, summary.confidencePercentage)
        assertEquals(ConfidenceLevel.HIGH, summary.confidenceLevel)
        assertTrue(summary.interpretation.contains("stronger relative preference"))
    }

    @Test
    fun `test moderate confidence interpretation`() {
        val prediction = DiseasePrediction(
            diseaseName = "Common Cold",
            probability = 0.62f,
            rank = 1
        )

        val summary = PredictionConfidenceInterpreter.interpret(prediction, symptomCount = 2)

        assertEquals(ConfidenceLevel.MODERATE, summary.confidenceLevel)
        assertEquals(62, summary.confidencePercentage)
        assertTrue(summary.interpretation.contains("moderate relative preference"))
    }

    @Test
    fun `test low confidence interpretation`() {
        val prediction = DiseasePrediction(
            diseaseName = "Migraine",
            probability = 0.35f,
            rank = 1
        )

        val summary = PredictionConfidenceInterpreter.interpret(prediction, symptomCount = 2)

        assertEquals(ConfidenceLevel.LOW, summary.confidenceLevel)
        assertEquals(35, summary.confidencePercentage)
        assertTrue(summary.interpretation.contains("less decisive model output"))
    }

    @Test
    fun `test insufficient data when symptoms are too few`() {
        val prediction = DiseasePrediction(
            diseaseName = "Hypertension",
            probability = 0.90f,
            rank = 1
        )

        val summary = PredictionConfidenceInterpreter.interpret(prediction, symptomCount = 0)

        assertEquals(ConfidenceLevel.INSUFFICIENT_DATA, summary.confidenceLevel)
        assertTrue(summary.interpretation.contains("Few symptoms were reported"))
    }

    @Test
    fun `test non-diagnostic disclaimer is always present and non-diagnostic wording enforced`() {
        val prediction = DiseasePrediction(
            diseaseName = "Allergy",
            probability = 0.80f,
            rank = 1
        )

        val summary = PredictionConfidenceInterpreter.interpret(prediction, symptomCount = 3)

        assertFalse(summary.disclaimer.isBlank())
        assertTrue(summary.disclaimer.contains("Model confidence reflects how strongly"))
        assertTrue(summary.disclaimer.contains("not medical certainty"))

        // Assert forbidden diagnostic phrasing does not appear in interpretation
        val interpretation = summary.interpretation.lowercase()
        assertFalse(interpretation.contains("you have allergy"))
        assertFalse(interpretation.contains("medical certainty"))
        assertFalse(interpretation.contains("clinical diagnosis"))
        assertFalse(interpretation.contains("guaranteed risk"))
        assertFalse(interpretation.contains("prescribe"))
    }
}
