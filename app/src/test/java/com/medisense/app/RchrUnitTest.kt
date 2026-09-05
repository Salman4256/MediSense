package com.medisense.app

import com.medisense.app.data.local.entity.AppointmentEntity
import com.medisense.app.data.local.entity.HealthProfileEntity
import com.medisense.app.data.local.entity.MedicationEntity
import com.medisense.app.data.local.entity.MedicationHistoryEntity
import com.medisense.app.data.local.entity.PredictionHistoryEntity
import com.medisense.app.domain.model.AdherenceTrend
import com.medisense.app.domain.model.AnalysisPeriod
import com.medisense.app.domain.model.AppointmentActivityTrend
import com.medisense.app.domain.model.ConfidenceTrend
import com.medisense.app.domain.model.LongitudinalHealthSummary
import com.medisense.app.domain.model.PersonalHealthContext
import com.medisense.app.domain.model.PredictionActivityTrend
import com.medisense.app.domain.model.RecurringSymptom
import com.medisense.app.domain.model.TemporalPattern
import com.medisense.app.domain.model.TrendDirection
import com.medisense.app.domain.rchr.RchrEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RchrUnitTest {

    private val dayMillis = 24 * 60 * 60 * 1000L
    private val now = 1757000000000L // Fixed reference timestamp

    @Test
    fun testEmptyUserRepresentation() {
        val rep = RchrEngine.buildRepresentation(
            userId = "user_empty",
            profile = null,
            predictions = emptyList(),
            medications = emptyList(),
            medicationHistory = emptyList(),
            appointments = emptyList(),
            temporalSummary = null,
            personalContext = null,
            now = now
        )

        assertEquals("user_empty", rep.userId)
        assertEquals("1.0", rep.representationVersion)
        assertEquals(0, rep.totalEncodedFeatures)
        assertEquals(0, rep.completenessPercentage)
        assertFalse(rep.hasSufficientData)

        // Round-trip reconstruction on empty user
        val result = RchrEngine.reconstructHealthState(rep)
        assertNotNull(result)
        assertEquals("1.0", result.representationVersion)
        assertTrue(result.reconstructedAttributes.isEmpty())
        assertFalse(result.unavailableAttributes.isEmpty())
        assertEquals(100.0f, result.reconstructionConsistencyScore, 0.01f)
        assertTrue(result.isConsistent)
    }

    @Test
    fun testProfileEncodingAndReconstruction() {
        val profile = HealthProfileEntity(
            id = "p1",
            userId = "u1",
            fullName = "Jane Doe",
            dateOfBirth = "1998-05-20", // 28 years old
            gender = "Female",
            bloodGroup = "A+",
            height = 168.0,
            weight = 60.0, // BMI = 60 / (1.68^2) = 21.26 (Normal)
            allergies = "Penicillin, Peanuts",
            existingDiseases = "Asthma",
            currentMedications = "Inhaler",
            familyHistory = "None",
            emergencyContactName = "Bob",
            emergencyContactNumber = "1234567890",
            notes = "None"
        )

        val rep = RchrEngine.buildRepresentation(
            userId = "u1",
            profile = profile,
            predictions = emptyList(),
            medications = emptyList(),
            medicationHistory = emptyList(),
            appointments = emptyList(),
            temporalSummary = null,
            personalContext = null,
            now = now
        )

        val prof = rep.profileFeatures
        assertNotNull(prof.age)
        assertEquals("YOUNG_ADULT", prof.ageGroup)
        assertEquals("FEMALE", prof.gender)
        assertEquals("A+", prof.bloodGroup)
        assertNotNull(prof.bmi)
        assertEquals("NORMAL", prof.bmiCategory)
        assertEquals(2, prof.allergyCount)
        assertEquals(listOf("Peanuts", "Penicillin"), prof.allergyList)
        assertEquals(1, prof.chronicConditionCount)
        assertEquals(listOf("Asthma"), prof.chronicConditionList)
        assertTrue(prof.profileCompletenessPercent >= 80)

        val result = RchrEngine.reconstructHealthState(rep)
        assertTrue(result.reconstructedAttributes.size >= 4)
        val ageAttr = result.reconstructedAttributes.find { it.attributeKey == "demographics_age" }
        assertNotNull(ageAttr)
        assertTrue(ageAttr!!.humanReadableMeaning.contains("Young Adult"))

        val bmiAttr = result.reconstructedAttributes.find { it.attributeKey == "body_composition_bmi" }
        assertNotNull(bmiAttr)
        assertTrue(bmiAttr!!.humanReadableMeaning.contains("Normal range"))
    }

    @Test
    fun testSymptomAndPredictionEncoding() {
        val predictions = listOf(
            PredictionHistoryEntity(
                id = 1,
                userId = "u1",
                predictedDisease = "Common Cold",
                confidence = 0.85f,
                symptoms = listOf("cough", "fever"),
                predictionTimestamp = now - (5 * dayMillis)
            ),
            PredictionHistoryEntity(
                id = 2,
                userId = "u1",
                predictedDisease = "Common Cold",
                confidence = 0.90f,
                symptoms = listOf("cough", "headache"),
                predictionTimestamp = now - (10 * dayMillis)
            ),
            PredictionHistoryEntity(
                id = 3,
                userId = "u1",
                predictedDisease = "Allergy",
                confidence = 0.80f,
                symptoms = listOf("sneezing", "cough"),
                predictionTimestamp = now - (15 * dayMillis)
            )
        )

        val rep = RchrEngine.buildRepresentation(
            userId = "u1",
            profile = null,
            predictions = predictions,
            medications = emptyList(),
            medicationHistory = emptyList(),
            appointments = emptyList(),
            temporalSummary = null,
            personalContext = null,
            now = now
        )

        val sym = rep.symptomFeatures
        assertEquals(4, sym.distinctSymptomCount) // Cough, Fever, Headache, Sneezing
        assertEquals("Cough", sym.frequentSymptoms[0])
        assertEquals(1, sym.recurringSymptoms.size)
        assertEquals("Cough", sym.recurringSymptoms[0]) // Cough appeared 3x

        val pred = rep.predictionFeatures
        assertEquals(3, pred.totalPredictionCount)
        assertEquals("Common Cold", pred.dominantPredictedDisease)
        assertEquals(2, pred.topPredictedDiseases.size)
        assertEquals(0.85f, pred.averageConfidence ?: 0f, 0.01f)

        val result = RchrEngine.reconstructHealthState(rep)
        val recurAttr = result.reconstructedAttributes.find { it.attributeKey == "recurring_symptoms" }
        assertNotNull(recurAttr)
        assertTrue(recurAttr!!.humanReadableMeaning.contains("Cough"))

        val predAttr = result.reconstructedAttributes.find { it.attributeKey == "prediction_history" }
        assertNotNull(predAttr)
        assertTrue(predAttr!!.humanReadableMeaning.contains("Common Cold"))
    }

    @Test
    fun testMedicationAndAdherenceEncoding() {
        val meds = listOf(
            MedicationEntity(
                id = 1,
                userId = "u1",
                medicineName = "Metformin",
                dosage = "500mg",
                frequency = "TWICE_DAILY",
                scheduledTimes = listOf("08:00 AM", "08:00 PM"),
                startDate = now - (20 * dayMillis),
                active = true
            ),
            MedicationEntity(
                id = 2,
                userId = "u1",
                medicineName = "Amlodipine",
                dosage = "5mg",
                frequency = "ONCE_DAILY",
                scheduledTimes = listOf("09:00 AM"),
                startDate = now - (20 * dayMillis),
                active = true
            )
        )

        val history = (1..10).map { i ->
            MedicationHistoryEntity(
                id = i.toLong(),
                medicationId = 1,
                userId = "u1",
                medicineName = "Metformin",
                scheduledDate = now - (i * dayMillis),
                scheduledTime = "08:00 AM",
                status = if (i <= 8) "TAKEN" else "MISSED"
            )
        }

        val rep = RchrEngine.buildRepresentation(
            userId = "u1",
            profile = null,
            predictions = emptyList(),
            medications = meds,
            medicationHistory = history,
            appointments = emptyList(),
            temporalSummary = null,
            personalContext = null,
            now = now
        )

        val medFeat = rep.medicationFeatures
        assertEquals(2, medFeat.activeMedicationCount)
        assertEquals(listOf("Amlodipine", "Metformin"), medFeat.activeMedicationNames) // Sorted
        assertTrue(medFeat.hasActiveMedications)

        val adhFeat = rep.adherenceFeatures
        assertEquals(10, adhFeat.recordedDoseCount)
        assertEquals(8, adhFeat.takenCount)
        assertEquals(2, adhFeat.missedCount)
        assertEquals(80.0f, adhFeat.adherencePercentage ?: 0f, 0.1f)
        assertEquals("OPTIMAL", adhFeat.adherenceCategory)

        val result = RchrEngine.reconstructHealthState(rep)
        val medAttr = result.reconstructedAttributes.find { it.attributeKey == "active_medications" }
        assertNotNull(medAttr)
        assertTrue(medAttr!!.humanReadableMeaning.contains("Amlodipine, Metformin"))

        val adhAttr = result.reconstructedAttributes.find { it.attributeKey == "medication_adherence" }
        assertNotNull(adhAttr)
        assertTrue(adhAttr!!.humanReadableMeaning.contains("80%"))
    }

    @Test
    fun testDeterminismAndStableSorting() {
        val profile = HealthProfileEntity(
            id = "p1",
            userId = "u1",
            fullName = "John Doe",
            dateOfBirth = "1990-01-01",
            gender = "Male",
            bloodGroup = "O+",
            height = 175.0,
            weight = 70.0,
            allergies = "Dust, Penicillin, Peanuts", // mixed order
            existingDiseases = "Hypertension, Asthma", // mixed order
            currentMedications = "Amlodipine",
            familyHistory = null,
            emergencyContactName = null,
            emergencyContactNumber = null,
            notes = null
        )

        val predictions = listOf(
            PredictionHistoryEntity(
                id = 1,
                userId = "u1",
                predictedDisease = "Bronchitis",
                confidence = 0.88f,
                symptoms = listOf("fever", "cough"),
                predictionTimestamp = now - (5 * dayMillis)
            ),
            PredictionHistoryEntity(
                id = 2,
                userId = "u1",
                predictedDisease = "Allergy",
                confidence = 0.90f,
                symptoms = listOf("sneezing", "cough"),
                predictionTimestamp = now - (10 * dayMillis)
            )
        )

        // Build representation 1
        val rep1 = RchrEngine.buildRepresentation(
            userId = "u1",
            profile = profile,
            predictions = predictions,
            medications = emptyList(),
            medicationHistory = emptyList(),
            appointments = emptyList(),
            temporalSummary = null,
            personalContext = null,
            now = now
        )

        // Build representation 2
        val rep2 = RchrEngine.buildRepresentation(
            userId = "u1",
            profile = profile,
            predictions = predictions,
            medications = emptyList(),
            medicationHistory = emptyList(),
            appointments = emptyList(),
            temporalSummary = null,
            personalContext = null,
            now = now
        )

        // Must be exactly identical
        assertEquals(rep1.totalEncodedFeatures, rep2.totalEncodedFeatures)
        assertEquals(rep1.completenessPercentage, rep2.completenessPercentage)
        assertEquals(rep1.profileFeatures, rep2.profileFeatures)
        assertEquals(rep1.symptomFeatures, rep2.symptomFeatures)
        assertEquals(rep1.predictionFeatures, rep2.predictionFeatures)

        // Reconstruct both
        val res1 = RchrEngine.reconstructHealthState(rep1)
        val res2 = RchrEngine.reconstructHealthState(rep2)
        assertEquals(res1.reconstructedAttributes, res2.reconstructedAttributes)
        assertEquals(res1.reconstructionConsistencyScore, res2.reconstructionConsistencyScore, 0.01f)
    }

    @Test
    fun testRoundTripConsistencyScoreCalculation() {
        val profile = HealthProfileEntity(
            id = "p1",
            userId = "u1",
            fullName = "Alice",
            dateOfBirth = "1992-03-10",
            gender = "Female",
            bloodGroup = "B+",
            height = 165.0,
            weight = 55.0,
            allergies = "Pollen",
            existingDiseases = "None",
            currentMedications = null,
            familyHistory = null,
            emergencyContactName = null,
            emergencyContactNumber = null,
            notes = null
        )

        val rep = RchrEngine.buildRepresentation(
            userId = "u1",
            profile = profile,
            predictions = emptyList(),
            medications = emptyList(),
            medicationHistory = emptyList(),
            appointments = emptyList(),
            temporalSummary = null,
            personalContext = null,
            now = now
        )

        val result = RchrEngine.reconstructHealthState(rep)
        assertTrue(result.isConsistent)
        assertEquals(100.0f, result.reconstructionConsistencyScore, 0.01f)
        assertEquals(result.totalReconstructableCount, result.successfullyReconstructedCount)
        assertTrue(result.mismatchedAttributes.isEmpty())
    }

    @Test
    fun testNonDiagnosticLanguageIntegrity() {
        val rep = RchrEngine.buildRepresentation(
            userId = "u1",
            profile = null,
            predictions = listOf(
                PredictionHistoryEntity(
                    id = 1,
                    userId = "u1",
                    predictedDisease = "Common Cold",
                    confidence = 0.85f,
                    symptoms = listOf("cough"),
                    predictionTimestamp = now
                )
            ),
            medications = emptyList(),
            medicationHistory = emptyList(),
            appointments = emptyList(),
            temporalSummary = null,
            personalContext = null,
            now = now
        )

        val result = RchrEngine.reconstructHealthState(rep)
        for (attr in result.reconstructedAttributes) {
            val text = attr.humanReadableMeaning
            assertFalse(text.contains("You have"))
            assertFalse(text.contains("Diagnosed with"))
            assertFalse(text.contains("Prescribed treatment"))
        }
    }
}
