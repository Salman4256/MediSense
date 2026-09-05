package com.medisense.app

import com.medisense.app.data.local.entity.AppointmentEntity
import com.medisense.app.data.local.entity.HealthProfileEntity
import com.medisense.app.data.local.entity.MedicationEntity
import com.medisense.app.data.local.entity.PredictionHistoryEntity
import com.medisense.app.domain.model.PersonalizationFeatures
import com.medisense.app.utils.AdherenceStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalHealthContextUnitTest {

    @Test
    fun testCalculateProfileCompleteness() {
        // Null profile -> 0%
        assertEquals(0, PersonalizationFeatures.calculateProfileCompleteness(null))

        // Empty profile -> 0%
        val emptyProfile = HealthProfileEntity(
            id = "p1",
            userId = "u1",
            fullName = null,
            dateOfBirth = null,
            gender = null,
            bloodGroup = null,
            height = null,
            weight = null,
            allergies = null,
            existingDiseases = null,
            currentMedications = null,
            familyHistory = null,
            emergencyContactName = null,
            emergencyContactNumber = null,
            notes = null
        )
        assertEquals(0, PersonalizationFeatures.calculateProfileCompleteness(emptyProfile))

        // Partial profile (5 out of 10 fields) -> 50%
        val partialProfile = emptyProfile.copy(
            fullName = "John Doe",
            dateOfBirth = "1995-06-15",
            gender = "Male",
            bloodGroup = "O+",
            height = 175.0
        )
        assertEquals(50, PersonalizationFeatures.calculateProfileCompleteness(partialProfile))

        // Complete profile (10 out of 10 fields) -> 100%
        val fullProfile = partialProfile.copy(
            weight = 70.0,
            allergies = "Peanuts",
            existingDiseases = "Hypertension",
            currentMedications = "Amlodipine",
            familyHistory = "Diabetes"
        )
        assertEquals(100, PersonalizationFeatures.calculateProfileCompleteness(fullProfile))
    }

    @Test
    fun testCalculateAgeAndBmi() {
        val age = PersonalizationFeatures.calculateAge("1990-01-01")
        assertNotNull(age)
        assertTrue(age!! >= 30)

        assertNull(PersonalizationFeatures.calculateAge(null))
        assertNull(PersonalizationFeatures.calculateAge("invalid-date"))

        // BMI: 70kg, 175cm -> 70 / (1.75^2) = 22.86 -> 22.9
        val bmi = PersonalizationFeatures.calculateBmi(175.0, 70.0)
        assertEquals(22.9, bmi!!, 0.1)

        assertNull(PersonalizationFeatures.calculateBmi(null, 70.0))
        assertNull(PersonalizationFeatures.calculateBmi(0.0, 70.0))
    }

    @Test
    fun testParseListString() {
        val items = PersonalizationFeatures.parseListString("Penicillin, Peanuts; Dust\nNone")
        assertEquals(3, items.size)
        assertEquals("Penicillin", items[0])
        assertEquals("Peanuts", items[1])
        assertEquals("Dust", items[2])

        assertTrue(PersonalizationFeatures.parseListString(null).isEmpty())
        assertTrue(PersonalizationFeatures.parseListString("None").isEmpty())
    }

    @Test
    fun testExtractFrequentSymptomsAndDiseases() {
        val history = listOf(
            PredictionHistoryEntity(
                id = 1,
                userId = "u1",
                predictedDisease = "Common Cold",
                confidence = 0.85f,
                symptoms = listOf("cough", "fever", "fatigue"),
                predictionTimestamp = System.currentTimeMillis()
            ),
            PredictionHistoryEntity(
                id = 2,
                userId = "u1",
                predictedDisease = "Common Cold",
                confidence = 0.90f,
                symptoms = listOf("cough", "headache", "fever"),
                predictionTimestamp = System.currentTimeMillis()
            ),
            PredictionHistoryEntity(
                id = 3,
                userId = "u1",
                predictedDisease = "Influenza",
                confidence = 0.92f,
                symptoms = listOf("fever", "body_pain"),
                predictionTimestamp = System.currentTimeMillis()
            )
        )

        val topSymptoms = PersonalizationFeatures.extractFrequentSymptoms(history, topN = 3)
        assertEquals(3, topSymptoms.size)
        assertEquals("Fever", topSymptoms[0]) // fever appeared 3 times
        assertEquals("Cough", topSymptoms[1]) // cough appeared 2 times

        val topDiseases = PersonalizationFeatures.extractFrequentDiseases(history, topN = 2)
        assertEquals(2, topDiseases.size)
        assertEquals("Common Cold", topDiseases[0]) // appeared 2 times
        assertEquals("Influenza", topDiseases[1]) // appeared 1 time

        val avgConf = PersonalizationFeatures.calculateAverageConfidence(history)
        assertNotNull(avgConf)
        assertEquals(0.89f, avgConf!!, 0.01f)
    }

    @Test
    fun testCalculatePersonalizationScoreBounds() {
        // Zero state
        val zeroScore = PersonalizationFeatures.calculatePersonalizationScore(
            completeness = 0,
            activeMedCount = 0,
            adherenceStats = null,
            recentPredictionCount = 0,
            hasRecurringSymptoms = false,
            upcomingAppointmentCount = 0
        )
        assertEquals(0.0f, zeroScore, 0.01f)

        // Fully engaged state
        val fullScore = PersonalizationFeatures.calculatePersonalizationScore(
            completeness = 100, // 20 pts
            activeMedCount = 2,
            adherenceStats = AdherenceStats(takenCount = 10, totalScheduled = 10, percentage = 100f), // 25 pts
            recentPredictionCount = 3, // 25 pts
            hasRecurringSymptoms = true, // 10 pts (total pred = 35 pts)
            upcomingAppointmentCount = 2 // 20 pts
        )
        assertEquals(100.0f, fullScore, 0.01f)
    }

    @Test
    fun testGenerateFactualSummary_nonDiagnostic() {
        val summary = PersonalizationFeatures.generateFactualSummary(
            profileCompleteness = 80,
            recentPredictions = listOf(
                PredictionHistoryEntity(
                    id = 1,
                    userId = "u1",
                    predictedDisease = "Allergy",
                    confidence = 0.88f,
                    symptoms = listOf("sneezing", "runny_nose"),
                    predictionTimestamp = System.currentTimeMillis()
                )
            ),
            frequentSymptoms = listOf("Sneezing", "Runny Nose"),
            activeMeds = listOf(
                MedicationEntity(
                    id = 1,
                    userId = "u1",
                    medicineName = "Cetirizine",
                    dosage = "10mg",
                    frequency = "ONCE_DAILY",
                    scheduledTimes = listOf("08:00 AM"),
                    startDate = System.currentTimeMillis()
                )
            ),
            adherenceStats = AdherenceStats(takenCount = 7, totalScheduled = 7, percentage = 100f),
            upcomingAppointments = listOf(
                AppointmentEntity(
                    id = 1,
                    userId = "u1",
                    doctorName = "Dr. Miller",
                    clinicName = "City Clinic",
                    appointmentType = "Checkup",
                    appointmentDate = "Sep 15, 2026",
                    appointmentTime = "10:00 AM",
                    appointmentTimestamp = System.currentTimeMillis() + 86400000L
                )
            )
        )

        assertNotNull(summary)
        assertTrue(summary.contains("Sneezing, Runny Nose"))
        assertTrue(summary.contains("Cetirizine") || summary.contains("1 active medication"))
        assertTrue(summary.contains("Dr. Miller"))
        assertFalse(summary.contains("You have"))
    }
}
