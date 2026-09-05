package com.medisense.app

import com.medisense.app.domain.model.AdherenceTrend
import com.medisense.app.domain.model.AllergyContext
import com.medisense.app.domain.model.AnalysisPeriod
import com.medisense.app.domain.model.AppointmentActivityTrend
import com.medisense.app.domain.model.AppointmentContext
import com.medisense.app.domain.model.ChronicConditionContext
import com.medisense.app.domain.model.ConfidenceTrend
import com.medisense.app.domain.model.ContextualRiskLevel
import com.medisense.app.domain.model.DemographicContext
import com.medisense.app.domain.model.FactorEffectDirection
import com.medisense.app.domain.model.LongitudinalHealthSummary
import com.medisense.app.domain.model.MedicationContext
import com.medisense.app.domain.model.PatternCategory
import com.medisense.app.domain.model.PatternSeverity
import com.medisense.app.domain.model.PersonalHealthContext
import com.medisense.app.domain.model.PredictionActivityTrend
import com.medisense.app.domain.model.PredictionContext
import com.medisense.app.domain.model.RecurringSymptom
import com.medisense.app.domain.model.TemporalPattern
import com.medisense.app.domain.model.TrendDirection
import com.medisense.app.domain.rchr.RchrAdherenceFeatures
import com.medisense.app.domain.rchr.RchrAppointmentFeatures
import com.medisense.app.domain.rchr.RchrMedicationFeatures
import com.medisense.app.domain.rchr.RchrPredictionFeatures
import com.medisense.app.domain.rchr.RchrProfileFeatures
import com.medisense.app.domain.rchr.RchrRepresentation
import com.medisense.app.domain.rchr.RchrSymptomFeatures
import com.medisense.app.domain.rchr.RchrTemporalFeatures
import com.medisense.app.domain.risk.ContextAwareRiskEngine
import com.medisense.app.domain.risk.ContextualRiskConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ContextAwareRiskEngineUnitTest {

    private lateinit var riskEngine: ContextAwareRiskEngine

    @Before
    fun setUp() {
        riskEngine = ContextAwareRiskEngine()
    }

    @Test
    fun `test fresh user with no health data returns INSUFFICIENT_DATA`() {
        val assessment = riskEngine.evaluate(
            userId = "user_fresh_1",
            personalContext = null,
            longitudinalSummary = null,
            rchr = null
        )

        assertEquals("user_fresh_1", assessment.userId)
        assertNull(assessment.overallScore)
        assertEquals(ContextualRiskLevel.INSUFFICIENT_DATA, assessment.riskLevel)
        assertFalse(assessment.hasSufficientData)
        assertTrue(assessment.positiveContributors.isEmpty())
        assertFalse(assessment.unavailableFactors.isEmpty())
        assertTrue(assessment.generatedSummary.contains("Not enough health history"))
    }

    @Test
    fun `test user with profile only`() {
        val profileContext = PersonalHealthContext(
            userId = "user_prof_only",
            demographics = DemographicContext(age = 25, gender = "Female", bmi = 22.0, bloodGroup = "B+"),
            chronicConditions = ChronicConditionContext(hasChronicConditions = false, conditions = emptyList()),
            allergies = AllergyContext(hasAllergies = false, allergiesList = emptyList()),
            medications = MedicationContext(activeCount = 0, activeNames = emptyList(), adherencePercentage = null, adherenceSummary = null),
            predictions = PredictionContext(totalCount = 0, recentCount = 0, frequentSymptoms = emptyList(), avgConfidence = null),
            appointments = AppointmentContext(upcomingCount = 0, nextAppointmentDoctor = null, nextAppointmentDate = null),
            profileCompleteness = 70,
            personalizationScore = 30.0f,
            generatedSummary = "Profile available",
            whyPersonalized = "Baseline test",
            hasSufficientData = false
        )

        val assessment = riskEngine.evaluate(
            userId = "user_prof_only",
            personalContext = profileContext,
            longitudinalSummary = null,
            rchr = null
        )

        // Without predictions or other activity, profile alone is insufficient for risk scoring
        assertEquals(ContextualRiskLevel.INSUFFICIENT_DATA, assessment.riskLevel)
        assertNull(assessment.overallScore)
        assertTrue(assessment.dataAvailabilitySummary["Health Profile"] == true)
        assertTrue(assessment.dataAvailabilitySummary["Prediction History"] == false)
    }

    @Test
    fun `test user with profile and prediction history`() {
        val personalContext = PersonalHealthContext(
            userId = "user_pred_active",
            demographics = DemographicContext(age = 30, gender = "Male", bmi = 23.5, bloodGroup = "O+"),
            chronicConditions = ChronicConditionContext(hasChronicConditions = true, conditions = listOf("Asthma")),
            allergies = AllergyContext(hasAllergies = true, allergiesList = listOf("Pollen")),
            medications = MedicationContext(activeCount = 1, activeNames = listOf("Inhaler"), adherencePercentage = 90f, adherenceSummary = "90% adherence"),
            predictions = PredictionContext(
                totalCount = 4,
                recentCount = 2,
                frequentSymptoms = listOf("cough", "fatigue"),
                avgConfidence = 0.85f
            ),
            appointments = AppointmentContext(upcomingCount = 1, nextAppointmentDoctor = "Dr. Adams", nextAppointmentDate = "2026-09-15"),
            profileCompleteness = 100,
            personalizationScore = 80.0f,
            generatedSummary = "Active profile with recent checks",
            whyPersonalized = "Context personalization",
            hasSufficientData = true
        )

        val assessment = riskEngine.evaluate(
            userId = "user_pred_active",
            personalContext = personalContext,
            longitudinalSummary = null,
            rchr = null
        )

        assertTrue(assessment.hasSufficientData)
        assertNotNull(assessment.overallScore)
        assertTrue(assessment.overallScore!! in 0..100)
        assertTrue(assessment.riskLevel != ContextualRiskLevel.INSUFFICIENT_DATA)
        assertFalse(assessment.contributingFactors.isEmpty())

        // Check Chronic and Allergy factor is present
        val chronicFactor = assessment.contributingFactors.find { it.factorId == ContextualRiskConfiguration.ID_CHRONIC_ALLERGY }
        assertNotNull(chronicFactor)
        assertEquals(FactorEffectDirection.INCREASES_SCORE, chronicFactor?.effectDirection)
    }

    @Test
    fun `test user with recurring symptoms increases risk priority`() {
        val longitudinal = LongitudinalHealthSummary(
            userId = "user_recurring",
            period = AnalysisPeriod.DAYS_30,
            predictionActivity = PredictionActivityTrend(1, 4, TrendDirection.INCREASING, 300.0f, emptyList(), emptyList()),
            recurringSymptoms = listOf(
                RecurringSymptom("headache", occurrenceCount = 4, firstObservedDate = 1000L, lastObservedDate = 5000L, isRecurring = true),
                RecurringSymptom("fever", occurrenceCount = 3, firstObservedDate = 2000L, lastObservedDate = 6000L, isRecurring = true)
            ),
            confidenceTrend = ConfidenceTrend(0.82f, 0.70f, TrendDirection.INCREASING, 17.0f, emptyList()),
            adherenceTrend = AdherenceTrend(80.0f, 80.0f, TrendDirection.STABLE, 0.0f, 10, 8, 2, 0, emptyList()),
            appointmentActivity = AppointmentActivityTrend(0, 1, 1, TrendDirection.STABLE),
            detectedPatterns = emptyList(),
            generatedSummary = "Recurring symptom patterns detected",
            hasSufficientData = true
        )

        val assessment = riskEngine.evaluate(
            userId = "user_recurring",
            personalContext = null,
            longitudinalSummary = longitudinal,
            rchr = null
        )

        assertTrue(assessment.hasSufficientData)
        val symptomFactor = assessment.contributingFactors.find { it.factorId == ContextualRiskConfiguration.ID_SYMPTOM_RECURRENCE }
        assertNotNull(symptomFactor)
        assertEquals(FactorEffectDirection.INCREASES_SCORE, symptomFactor?.effectDirection)
        assertTrue(symptomFactor!!.rawContributionScore > 0f)
        assertTrue(symptomFactor.explanation.contains("Repeated symptom activity"))
    }

    @Test
    fun `test medication adherence effect - high adherence vs low adherence`() {
        // High Adherence (95%)
        val highAdherenceLongitudinal = LongitudinalHealthSummary(
            userId = "user_high_adh",
            period = AnalysisPeriod.DAYS_30,
            predictionActivity = PredictionActivityTrend(2, 2, TrendDirection.STABLE, 0.0f, emptyList(), emptyList()),
            recurringSymptoms = emptyList(),
            confidenceTrend = ConfidenceTrend(0.80f, 0.80f, TrendDirection.STABLE, 0.0f, emptyList()),
            adherenceTrend = AdherenceTrend(95.0f, 95.0f, TrendDirection.STABLE, 0.0f, 20, 19, 1, 0, emptyList()),
            appointmentActivity = AppointmentActivityTrend(1, 1, 1, TrendDirection.STABLE),
            detectedPatterns = emptyList(),
            generatedSummary = "High adherence summary",
            hasSufficientData = true
        )

        val highAssessment = riskEngine.evaluate(
            userId = "user_high_adh",
            personalContext = null,
            longitudinalSummary = highAdherenceLongitudinal,
            rchr = null
        )

        val highAdhFactor = highAssessment.contributingFactors.find { it.factorId == ContextualRiskConfiguration.ID_MEDICATION_ADHERENCE }
        assertNotNull(highAdhFactor)
        assertEquals(FactorEffectDirection.DECREASES_SCORE, highAdhFactor?.effectDirection)
        assertEquals(10f, highAdhFactor!!.rawContributionScore, 0.01f)

        // Low Adherence (30%)
        val lowAdherenceLongitudinal = LongitudinalHealthSummary(
            userId = "user_low_adh",
            period = AnalysisPeriod.DAYS_30,
            predictionActivity = PredictionActivityTrend(2, 2, TrendDirection.STABLE, 0.0f, emptyList(), emptyList()),
            recurringSymptoms = emptyList(),
            confidenceTrend = ConfidenceTrend(0.80f, 0.80f, TrendDirection.STABLE, 0.0f, emptyList()),
            adherenceTrend = AdherenceTrend(30.0f, 30.0f, TrendDirection.DECREASING, -40.0f, 10, 3, 7, 0, emptyList()),
            appointmentActivity = AppointmentActivityTrend(1, 1, 1, TrendDirection.STABLE),
            detectedPatterns = emptyList(),
            generatedSummary = "Low adherence summary",
            hasSufficientData = true
        )

        val lowAssessment = riskEngine.evaluate(
            userId = "user_low_adh",
            personalContext = null,
            longitudinalSummary = lowAdherenceLongitudinal,
            rchr = null
        )

        val lowAdhFactor = lowAssessment.contributingFactors.find { it.factorId == ContextualRiskConfiguration.ID_MEDICATION_ADHERENCE }
        assertNotNull(lowAdhFactor)
        assertEquals(FactorEffectDirection.INCREASES_SCORE, lowAdhFactor?.effectDirection)
        assertEquals(80f, lowAdhFactor!!.rawContributionScore, 0.01f)
        assertTrue(lowAssessment.overallScore!! > highAssessment.overallScore!!)
    }

    @Test
    fun `test upcoming appointment context is mitigating`() {
        val personalContext = PersonalHealthContext(
            userId = "user_appt",
            demographics = DemographicContext(age = 45, gender = "Male", bmi = 26.0, bloodGroup = "A-"),
            chronicConditions = ChronicConditionContext(hasChronicConditions = false, conditions = emptyList()),
            allergies = AllergyContext(hasAllergies = false, allergiesList = emptyList()),
            medications = MedicationContext(activeCount = 0, activeNames = emptyList(), adherencePercentage = null, adherenceSummary = null),
            predictions = PredictionContext(totalCount = 2, recentCount = 1, frequentSymptoms = listOf("joint pain"), avgConfidence = 0.75f),
            appointments = AppointmentContext(upcomingCount = 1, nextAppointmentDoctor = "Dr. Watson", nextAppointmentDate = "2026-09-20"),
            profileCompleteness = 90,
            personalizationScore = 70.0f,
            generatedSummary = "Appointment scheduled",
            whyPersonalized = "Appointment test",
            hasSufficientData = true
        )

        val assessment = riskEngine.evaluate(
            userId = "user_appt",
            personalContext = personalContext,
            longitudinalSummary = null,
            rchr = null
        )

        val apptFactor = assessment.contributingFactors.find { it.factorId == ContextualRiskConfiguration.ID_APPOINTMENT_CONTEXT }
        assertNotNull(apptFactor)
        assertEquals(FactorEffectDirection.DECREASES_SCORE, apptFactor?.effectDirection)
        assertEquals(15f, apptFactor!!.rawContributionScore, 0.01f)
        assertTrue(apptFactor.explanation.contains("Dr. Watson"))
    }

    @Test
    fun `test temporal patterns and longitudinal trends`() {
        val longitudinal = LongitudinalHealthSummary(
            userId = "user_temporal",
            period = AnalysisPeriod.DAYS_30,
            predictionActivity = PredictionActivityTrend(1, 3, TrendDirection.INCREASING, 100.0f, emptyList(), emptyList()),
            recurringSymptoms = emptyList(),
            confidenceTrend = ConfidenceTrend(0.90f, 0.70f, TrendDirection.INCREASING, 28.0f, emptyList()),
            adherenceTrend = AdherenceTrend(100.0f, 100.0f, TrendDirection.STABLE, 0.0f, 10, 10, 0, 0, emptyList()),
            appointmentActivity = AppointmentActivityTrend(0, 0, 0, TrendDirection.STABLE),
            detectedPatterns = listOf(
                TemporalPattern(
                    id = "p1",
                    title = "Cyclic Headaches",
                    description = "Headaches occurring periodically",
                    category = PatternCategory.SYMPTOMS,
                    severity = PatternSeverity.ATTENTION,
                    firstObservedDate = 1000L,
                    lastObservedDate = 5000L,
                    observationCount = 4
                )
            ),
            generatedSummary = "Cyclic pattern summary",
            hasSufficientData = true
        )

        val assessment = riskEngine.evaluate(
            userId = "user_temporal",
            personalContext = null,
            longitudinalSummary = longitudinal,
            rchr = null
        )

        val patternFactor = assessment.contributingFactors.find { it.factorId == ContextualRiskConfiguration.ID_TEMPORAL_PATTERNS }
        assertNotNull(patternFactor)
        assertEquals(FactorEffectDirection.INCREASES_SCORE, patternFactor?.effectDirection)
        assertEquals(40f, patternFactor!!.rawContributionScore, 0.01f)
    }

    @Test
    fun `test double counting prevention across Module 9B and RCHR`() {
        val longitudinal = LongitudinalHealthSummary(
            userId = "user_dedup",
            period = AnalysisPeriod.DAYS_30,
            predictionActivity = PredictionActivityTrend(1, 3, TrendDirection.INCREASING, 100.0f, emptyList(), emptyList()),
            recurringSymptoms = listOf(
                RecurringSymptom("cough", occurrenceCount = 3, firstObservedDate = 1000L, lastObservedDate = 5000L, isRecurring = true),
                RecurringSymptom("fever", occurrenceCount = 2, firstObservedDate = 2000L, lastObservedDate = 6000L, isRecurring = true)
            ),
            confidenceTrend = ConfidenceTrend(0.80f, 0.80f, TrendDirection.STABLE, 0.0f, emptyList()),
            adherenceTrend = AdherenceTrend(100.0f, 100.0f, TrendDirection.STABLE, 0.0f, 5, 5, 0, 0, emptyList()),
            appointmentActivity = AppointmentActivityTrend(0, 0, 0, TrendDirection.STABLE),
            detectedPatterns = emptyList(),
            generatedSummary = "Summary",
            hasSufficientData = true
        )

        val rchr = RchrRepresentation(
            userId = "user_dedup",
            representationVersion = "1.0",
            generatedAt = 1757000000000L,
            totalEncodedFeatures = 15,
            completenessPercentage = 80,
            profileFeatures = RchrProfileFeatures(
                age = 30,
                ageGroup = "ADULT",
                gender = "Male",
                bloodGroup = "O+",
                heightCm = 175.0,
                weightKg = 70.0,
                bmi = 22.0,
                bmiCategory = "NORMAL",
                allergyCount = 0,
                allergyList = emptyList(),
                chronicConditionCount = 0,
                chronicConditionList = emptyList(),
                profileCompletenessPercent = 100
            ),
            symptomFeatures = RchrSymptomFeatures(
                distinctSymptomCount = 2,
                allRecordedSymptoms = listOf("cough", "fever"),
                frequentSymptoms = listOf("cough", "fever"),
                recurringSymptoms = listOf("cough", "fever"),
                recentSymptomCount = 2
            ),
            predictionFeatures = RchrPredictionFeatures(
                totalPredictionCount = 3,
                recentPredictionCount = 1,
                dominantPredictedDisease = "Common Cold",
                topPredictedDiseases = listOf("Common Cold"),
                averageConfidence = 0.80f,
                confidenceRangeMin = 0.75f,
                confidenceRangeMax = 0.85f,
                confidenceTrendDirection = TrendDirection.STABLE
            ),
            medicationFeatures = RchrMedicationFeatures(
                activeMedicationCount = 1,
                activeMedicationNames = listOf("Vitamin C"),
                totalPrescribedMedicationCount = 1,
                hasActiveMedications = true
            ),
            adherenceFeatures = RchrAdherenceFeatures(
                recordedDoseCount = 5,
                takenCount = 5,
                missedCount = 0,
                skippedCount = 0,
                adherencePercentage = 100.0f,
                adherenceCategory = "OPTIMAL",
                adherenceTrendDirection = TrendDirection.STABLE
            ),
            appointmentFeatures = RchrAppointmentFeatures(
                upcomingAppointmentCount = 0,
                nextAppointmentDoctor = null,
                nextAppointmentDate = null,
                nextAppointmentType = null,
                pastAppointmentCount = 0,
                appointmentTrendDirection = TrendDirection.STABLE
            ),
            temporalFeatures = RchrTemporalFeatures(
                analysisWindowDays = 30,
                predictionActivityTrend = TrendDirection.INCREASING,
                predictionChangePercent = 100.0f,
                confidenceTrend = TrendDirection.STABLE,
                adherenceTrend = TrendDirection.STABLE,
                appointmentTrend = TrendDirection.STABLE,
                detectedPatternsCount = 0,
                detectedPatternTitles = emptyList()
            ),
            contextFeatures = com.medisense.app.domain.rchr.RchrContextFeatures(
                personalizationScore = 80.0f,
                contextCompleteness = 80,
                contextSummary = "Context summary",
                whyPersonalized = "Why"
            ),
            hasSufficientData = true
        )

        val assessment = riskEngine.evaluate(
            userId = "user_dedup",
            personalContext = null,
            longitudinalSummary = longitudinal,
            rchr = rchr
        )

        // Cough and Fever appear in both 9B and RCHR. The engine should deduplicate to exactly 2 symptoms (rawScore = 45f), NOT 4 symptoms (rawScore = 75f).
        val symptomFactor = assessment.contributingFactors.find { it.factorId == ContextualRiskConfiguration.ID_SYMPTOM_RECURRENCE }
        assertNotNull(symptomFactor)
        assertEquals(45f, symptomFactor!!.rawContributionScore, 0.01f)
    }

    @Test
    fun `test deterministic scoring - identical inputs yield identical outputs`() {
        val personalContext = PersonalHealthContext(
            userId = "user_determ",
            demographics = DemographicContext(age = 40, gender = "Female", bmi = 24.0, bloodGroup = "AB+"),
            chronicConditions = ChronicConditionContext(hasChronicConditions = true, conditions = listOf("Hypertension")),
            allergies = AllergyContext(hasAllergies = false, allergiesList = emptyList()),
            medications = MedicationContext(activeCount = 1, activeNames = listOf("Lisinopril"), adherencePercentage = 85f, adherenceSummary = "85% adherence"),
            predictions = PredictionContext(totalCount = 3, recentCount = 2, frequentSymptoms = listOf("dizziness"), avgConfidence = 0.80f),
            appointments = AppointmentContext(upcomingCount = 0, nextAppointmentDoctor = null, nextAppointmentDate = null),
            profileCompleteness = 100,
            personalizationScore = 75.0f,
            generatedSummary = "Determ summary",
            whyPersonalized = "Determ",
            hasSufficientData = true
        )

        val run1 = riskEngine.evaluate(
            userId = "user_determ",
            personalContext = personalContext,
            longitudinalSummary = null,
            rchr = null
        )

        val run2 = riskEngine.evaluate(
            userId = "user_determ",
            personalContext = personalContext,
            longitudinalSummary = null,
            rchr = null
        )

        assertEquals(run1.overallScore, run2.overallScore)
        assertEquals(run1.riskLevel, run2.riskLevel)
        assertEquals(run1.generatedSummary, run2.generatedSummary)
        assertEquals(run1.contributingFactors.size, run2.contributingFactors.size)
        for (i in run1.contributingFactors.indices) {
            assertEquals(run1.contributingFactors[i].factorId, run2.contributingFactors[i].factorId)
            assertEquals(run1.contributingFactors[i].rawContributionScore, run2.contributingFactors[i].rawContributionScore, 0.001f)
            assertEquals(run1.contributingFactors[i].weightedContribution, run2.contributingFactors[i].weightedContribution, 0.001f)
            assertEquals(run1.contributingFactors[i].explanation, run2.contributingFactors[i].explanation)
        }
    }

    @Test
    fun `test factor ordering is deterministic`() {
        val longitudinal = LongitudinalHealthSummary(
            userId = "user_order",
            period = AnalysisPeriod.DAYS_30,
            predictionActivity = PredictionActivityTrend(5, 5, TrendDirection.INCREASING, 200.0f, emptyList(), emptyList()),
            recurringSymptoms = listOf(
                RecurringSymptom("cough", 5, 1000L, 5000L, true),
                RecurringSymptom("fever", 4, 2000L, 6000L, true),
                RecurringSymptom("chills", 3, 3000L, 7000L, true)
            ),
            confidenceTrend = ConfidenceTrend(0.88f, 0.70f, TrendDirection.INCREASING, 25.0f, emptyList()),
            adherenceTrend = AdherenceTrend(20.0f, 20.0f, TrendDirection.DECREASING, -50.0f, 10, 2, 8, 0, emptyList()),
            appointmentActivity = AppointmentActivityTrend(0, 0, 0, TrendDirection.STABLE),
            detectedPatterns = listOf(
                TemporalPattern("p1", "Pattern 1", "Desc", PatternCategory.SYMPTOMS, PatternSeverity.ATTENTION, 100L, 200L, 2),
                TemporalPattern("p2", "Pattern 2", "Desc", PatternCategory.SYMPTOMS, PatternSeverity.ATTENTION, 100L, 200L, 2)
            ),
            generatedSummary = "High activity",
            hasSufficientData = true
        )

        val assessment = riskEngine.evaluate(
            userId = "user_order",
            personalContext = null,
            longitudinalSummary = longitudinal,
            rchr = null
        )

        val positive = assessment.positiveContributors
        for (i in 0 until positive.size - 1) {
            assertTrue(positive[i].weightedContribution >= positive[i + 1].weightedContribution)
        }
    }

    @Test
    fun `test no diagnostic wording appears in generated explanations or summaries`() {
        val longitudinal = LongitudinalHealthSummary(
            userId = "user_safety",
            period = AnalysisPeriod.DAYS_30,
            predictionActivity = PredictionActivityTrend(3, 3, TrendDirection.INCREASING, 100.0f, emptyList(), emptyList()),
            recurringSymptoms = listOf(RecurringSymptom("cough", 3, 1000L, 5000L, true)),
            confidenceTrend = ConfidenceTrend(0.85f, 0.80f, TrendDirection.INCREASING, 6.0f, emptyList()),
            adherenceTrend = AdherenceTrend(100.0f, 100.0f, TrendDirection.STABLE, 0.0f, 10, 10, 0, 0, emptyList()),
            appointmentActivity = AppointmentActivityTrend(1, 1, 1, TrendDirection.STABLE),
            detectedPatterns = emptyList(),
            generatedSummary = "Safety check",
            hasSufficientData = true
        )

        val assessment = riskEngine.evaluate(
            userId = "user_safety",
            personalContext = null,
            longitudinalSummary = longitudinal,
            rchr = null
        )

        val allText = buildString {
            append(assessment.generatedSummary)
            append(" ")
            assessment.contributingFactors.forEach {
                append(it.title).append(" ")
                append(it.explanation).append(" ")
            }
        }.lowercase()

        // Verify forbidden diagnostic/prescriptive terms do NOT appear
        assertFalse(allText.contains("you have flu"))
        assertFalse(allText.contains("diagnosed with"))
        assertFalse(allText.contains("clinical certainty"))
        assertFalse(allText.contains("take 500mg"))
        assertFalse(allText.contains("prescribe"))
        assertFalse(allText.contains("guaranteed risk"))
    }

    @Test
    fun `test user isolation between user A and user B`() {
        val contextUserA = PersonalHealthContext(
            userId = "user_A",
            demographics = DemographicContext(age = 22, gender = "Female", bmi = 20.0, bloodGroup = "O+"),
            chronicConditions = ChronicConditionContext(hasChronicConditions = true, conditions = listOf("Migraine")),
            allergies = AllergyContext(hasAllergies = false, allergiesList = emptyList()),
            medications = MedicationContext(activeCount = 0, activeNames = emptyList(), adherencePercentage = null, adherenceSummary = null),
            predictions = PredictionContext(totalCount = 1, recentCount = 1, frequentSymptoms = listOf("headache"), avgConfidence = 0.60f),
            appointments = AppointmentContext(upcomingCount = 0, nextAppointmentDoctor = null, nextAppointmentDate = null),
            profileCompleteness = 80,
            personalizationScore = 40.0f,
            generatedSummary = "User A Summary",
            whyPersonalized = "A",
            hasSufficientData = true
        )

        val contextUserB = PersonalHealthContext(
            userId = "user_B",
            demographics = DemographicContext(age = 65, gender = "Male", bmi = 31.0, bloodGroup = "A+"),
            chronicConditions = ChronicConditionContext(hasChronicConditions = true, conditions = listOf("Diabetes", "Hypertension")),
            allergies = AllergyContext(hasAllergies = true, allergiesList = listOf("Penicillin", "Sulfa")),
            medications = MedicationContext(activeCount = 3, activeNames = listOf("Metformin", "Amlodipine", "Atorvastatin"), adherencePercentage = 40f, adherenceSummary = "40% adherence"),
            predictions = PredictionContext(totalCount = 10, recentCount = 5, frequentSymptoms = listOf("fatigue", "frequent urination"), avgConfidence = 0.90f),
            appointments = AppointmentContext(upcomingCount = 2, nextAppointmentDoctor = "Dr. House", nextAppointmentDate = "2026-09-12"),
            profileCompleteness = 100,
            personalizationScore = 90.0f,
            generatedSummary = "User B Summary",
            whyPersonalized = "B",
            hasSufficientData = true
        )

        val assessmentA = riskEngine.evaluate("user_A", contextUserA, null, null)
        val assessmentB = riskEngine.evaluate("user_B", contextUserB, null, null)

        assertEquals("user_A", assessmentA.userId)
        assertEquals("user_B", assessmentB.userId)
        assertTrue(assessmentB.overallScore!! > assessmentA.overallScore!!)
        assertFalse(assessmentA.generatedSummary == assessmentB.generatedSummary)
    }
}
