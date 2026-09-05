package com.medisense.app

import com.medisense.app.data.local.entity.AppointmentEntity
import com.medisense.app.data.local.entity.MedicationEntity
import com.medisense.app.data.local.entity.MedicationHistoryEntity
import com.medisense.app.data.local.entity.PredictionHistoryEntity
import com.medisense.app.domain.analytics.TemporalPatternAnalyzer
import com.medisense.app.domain.model.AnalysisPeriod
import com.medisense.app.domain.model.PatternCategory
import com.medisense.app.domain.model.PatternSeverity
import com.medisense.app.domain.model.TrendDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LongitudinalHealthUnitTest {

    private val dayMillis = 24 * 60 * 60 * 1000L
    private val now = 1757000000000L // Fixed reference timestamp

    @Test
    fun testPredictionActivityTrendCalculation() {
        val currentPeriodStart = now - (30 * dayMillis)
        val previousPeriodStart = now - (60 * dayMillis)

        // 3 predictions in current period, 1 in previous period -> Increasing (+200%)
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
                confidence = 0.88f,
                symptoms = listOf("cough", "headache"),
                predictionTimestamp = now - (10 * dayMillis)
            ),
            PredictionHistoryEntity(
                id = 3,
                userId = "u1",
                predictedDisease = "Allergy",
                confidence = 0.90f,
                symptoms = listOf("sneezing"),
                predictionTimestamp = now - (15 * dayMillis)
            ),
            PredictionHistoryEntity(
                id = 4,
                userId = "u1",
                predictedDisease = "Common Cold",
                confidence = 0.80f,
                symptoms = listOf("cough"),
                predictionTimestamp = now - (45 * dayMillis) // in previous period
            )
        )

        val summary = TemporalPatternAnalyzer.analyzeLongitudinalHealth(
            userId = "u1",
            period = AnalysisPeriod.DAYS_30,
            predictions = predictions,
            medications = emptyList(),
            medicationHistory = emptyList(),
            appointments = emptyList(),
            now = now
        )

        val trend = summary.predictionActivity
        assertEquals(3, trend.currentPeriodCount)
        assertEquals(1, trend.previousPeriodCount)
        assertEquals(TrendDirection.INCREASING, trend.direction)
        assertEquals(200.0f, trend.changePercentage ?: 0f, 0.1f)
        assertEquals(2, trend.topConditions.size)
        assertEquals("Common Cold", trend.topConditions[0])
    }

    @Test
    fun testSymptomRecurrenceDetection() {
        val predictions = listOf(
            PredictionHistoryEntity(
                id = 1,
                userId = "u1",
                predictedDisease = "Common Cold",
                confidence = 0.85f,
                symptoms = listOf("cough", "fever"),
                predictionTimestamp = now - (10 * dayMillis)
            ),
            PredictionHistoryEntity(
                id = 2,
                userId = "u1",
                predictedDisease = "Bronchitis",
                confidence = 0.90f,
                symptoms = listOf("cough", "chest_pain"),
                predictionTimestamp = now - (5 * dayMillis)
            ),
            PredictionHistoryEntity(
                id = 3,
                userId = "u1",
                predictedDisease = "Common Cold",
                confidence = 0.82f,
                symptoms = listOf("cough", "fatigue"),
                predictionTimestamp = now - (2 * dayMillis)
            )
        )

        val summary = TemporalPatternAnalyzer.analyzeLongitudinalHealth(
            userId = "u1",
            period = AnalysisPeriod.DAYS_30,
            predictions = predictions,
            medications = emptyList(),
            medicationHistory = emptyList(),
            appointments = emptyList(),
            now = now
        )

        val recurring = summary.recurringSymptoms
        assertFalse(recurring.isEmpty())

        val cough = recurring.find { it.symptomName.equals("Cough", ignoreCase = true) }
        assertNotNull(cough)
        assertEquals(3, cough!!.occurrenceCount)
        assertTrue(cough.isRecurring)
        assertEquals(now - (10 * dayMillis), cough.firstObservedDate)
        assertEquals(now - (2 * dayMillis), cough.lastObservedDate)

        val fever = recurring.find { it.symptomName.equals("Fever", ignoreCase = true) }
        assertNotNull(fever)
        assertEquals(1, fever!!.occurrenceCount)
        assertFalse(fever.isRecurring) // Only occurred once
    }

    @Test
    fun testConfidenceTrendCalculation() {
        val predictions = listOf(
            PredictionHistoryEntity(
                id = 1,
                userId = "u1",
                predictedDisease = "Common Cold",
                confidence = 0.95f,
                symptoms = listOf("cough"),
                predictionTimestamp = now - (5 * dayMillis) // current
            ),
            PredictionHistoryEntity(
                id = 2,
                userId = "u1",
                predictedDisease = "Common Cold",
                confidence = 0.70f,
                symptoms = listOf("cough"),
                predictionTimestamp = now - (40 * dayMillis) // previous
            )
        )

        val summary = TemporalPatternAnalyzer.analyzeLongitudinalHealth(
            userId = "u1",
            period = AnalysisPeriod.DAYS_30,
            predictions = predictions,
            medications = emptyList(),
            medicationHistory = emptyList(),
            appointments = emptyList(),
            now = now
        )

        val conf = summary.confidenceTrend
        assertNotNull(conf.currentAvgConfidence)
        assertEquals(0.95f, conf.currentAvgConfidence!!, 0.01f)
        assertEquals(0.70f, conf.previousAvgConfidence!!, 0.01f)
        assertEquals(TrendDirection.INCREASING, conf.direction)
    }

    @Test
    fun testMedicationAdherenceTrendCalculation() {
        // Current period: 9 taken out of 10 = 90%
        val currentHistory = (1..10).map { i ->
            MedicationHistoryEntity(
                id = i.toLong(),
                medicationId = 1,
                userId = "u1",
                medicineName = "Aspirin",
                scheduledDate = now - (i * dayMillis),
                scheduledTime = "08:00 AM",
                status = if (i == 1) "MISSED" else "TAKEN"
            )
        }

        // Previous period: 6 taken out of 10 = 60%
        val previousHistory = (11..20).map { i ->
            MedicationHistoryEntity(
                id = i.toLong(),
                medicationId = 1,
                userId = "u1",
                medicineName = "Aspirin",
                scheduledDate = now - (i * dayMillis + (30 * dayMillis)),
                scheduledTime = "08:00 AM",
                status = if (i <= 14) "MISSED" else "TAKEN"
            )
        }

        val allHistory = currentHistory + previousHistory

        val summary = TemporalPatternAnalyzer.analyzeLongitudinalHealth(
            userId = "u1",
            period = AnalysisPeriod.DAYS_30,
            predictions = emptyList(),
            medications = emptyList(),
            medicationHistory = allHistory,
            appointments = emptyList(),
            now = now
        )

        val adh = summary.adherenceTrend
        assertEquals(90.0f, adh.currentAdherencePercentage ?: 0f, 0.1f)
        assertEquals(60.0f, adh.previousAdherencePercentage ?: 0f, 0.1f)
        assertEquals(TrendDirection.IMPROVING, adh.direction)
        assertEquals(9, adh.takenCount)
        assertEquals(1, adh.missedCount)
    }

    @Test
    fun testTemporalPatternDetection() {
        val predictions = listOf(
            PredictionHistoryEntity(
                id = 1,
                userId = "u1",
                predictedDisease = "Common Cold",
                confidence = 0.85f,
                symptoms = listOf("cough", "headache"),
                predictionTimestamp = now - (10 * dayMillis)
            ),
            PredictionHistoryEntity(
                id = 2,
                userId = "u1",
                predictedDisease = "Common Cold",
                confidence = 0.90f,
                symptoms = listOf("cough", "fever"),
                predictionTimestamp = now - (5 * dayMillis)
            )
        )

        val activeMeds = listOf(
            MedicationEntity(
                id = 1,
                userId = "u1",
                medicineName = "Paracetamol",
                dosage = "500mg",
                frequency = "TWICE_DAILY",
                scheduledTimes = listOf("08:00 AM", "08:00 PM"),
                startDate = now - (10 * dayMillis),
                active = true
            )
        )

        val upcomingAppointments = listOf(
            AppointmentEntity(
                id = 1,
                userId = "u1",
                doctorName = "Dr. Adams",
                clinicName = "Central Clinic",
                appointmentType = "Followup",
                appointmentDate = "Sep 20, 2026",
                appointmentTime = "11:00 AM",
                appointmentTimestamp = now + (3 * dayMillis),
                status = "SCHEDULED"
            )
        )

        val summary = TemporalPatternAnalyzer.analyzeLongitudinalHealth(
            userId = "u1",
            period = AnalysisPeriod.DAYS_30,
            predictions = predictions,
            medications = activeMeds,
            medicationHistory = emptyList(),
            appointments = upcomingAppointments,
            now = now
        )

        val patterns = summary.detectedPatterns
        assertFalse(patterns.isEmpty())

        // Symptom recurrence pattern
        val recurringPattern = patterns.find { it.category == PatternCategory.SYMPTOMS }
        assertNotNull(recurringPattern)
        assertTrue(recurringPattern!!.title.contains("Cough"))
        assertEquals(PatternSeverity.ATTENTION, recurringPattern.severity)

        // Care routine pattern
        val routinePattern = patterns.find { it.category == PatternCategory.GENERAL }
        assertNotNull(routinePattern)
        assertEquals(PatternSeverity.POSITIVE, routinePattern!!.severity)
    }

    @Test
    fun testNonDiagnosticSummaryIntegrity() {
        val predictions = listOf(
            PredictionHistoryEntity(
                id = 1,
                userId = "u1",
                predictedDisease = "Common Cold",
                confidence = 0.85f,
                symptoms = listOf("cough"),
                predictionTimestamp = now - (2 * dayMillis)
            )
        )

        val summary = TemporalPatternAnalyzer.analyzeLongitudinalHealth(
            userId = "u1",
            period = AnalysisPeriod.DAYS_7,
            predictions = predictions,
            medications = emptyList(),
            medicationHistory = emptyList(),
            appointments = emptyList(),
            now = now
        )

        assertTrue(summary.hasSufficientData)
        assertNotNull(summary.generatedSummary)
        // Must be descriptive/factual, not clinical diagnosis
        assertFalse(summary.generatedSummary.contains("You have"))
        assertFalse(summary.generatedSummary.contains("Diagnosed with"))
        assertTrue(summary.generatedSummary.contains("1 prediction record"))
    }
}
