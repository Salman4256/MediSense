package com.medisense.app

import com.medisense.app.domain.guidance.GuidanceConfiguration
import com.medisense.app.domain.guidance.GuidanceSafetyFilter
import com.medisense.app.domain.guidance.PersonalizedGuidanceEngine
import com.medisense.app.domain.model.AdherenceTrend
import com.medisense.app.domain.model.AllergyContext
import com.medisense.app.domain.model.AnalysisPeriod
import com.medisense.app.domain.model.AppointmentActivityTrend
import com.medisense.app.domain.model.AppointmentContext
import com.medisense.app.domain.model.ChronicConditionContext
import com.medisense.app.domain.model.ConfidenceTrend
import com.medisense.app.domain.model.ContextualRiskAssessment
import com.medisense.app.domain.model.ContextualRiskCategory
import com.medisense.app.domain.model.ContextualRiskFactor
import com.medisense.app.domain.model.ContextualRiskLevel
import com.medisense.app.domain.model.DemographicContext
import com.medisense.app.domain.model.FactorEffectDirection
import com.medisense.app.domain.model.GuidanceActionType
import com.medisense.app.domain.model.GuidanceCategory
import com.medisense.app.domain.model.GuidancePriority
import com.medisense.app.domain.model.LongitudinalHealthSummary
import com.medisense.app.domain.model.MedicationContext
import com.medisense.app.domain.model.PersonalHealthContext
import com.medisense.app.domain.model.PersonalizedGuidance
import com.medisense.app.domain.model.PredictionActivityTrend
import com.medisense.app.domain.model.PredictionContext
import com.medisense.app.domain.model.RecurringSymptom
import com.medisense.app.domain.model.TrendDirection
import com.medisense.app.domain.rchr.RchrAdherenceFeatures
import com.medisense.app.domain.rchr.RchrAppointmentFeatures
import com.medisense.app.domain.rchr.RchrContextFeatures
import com.medisense.app.domain.rchr.RchrMedicationFeatures
import com.medisense.app.domain.rchr.RchrPredictionFeatures
import com.medisense.app.domain.rchr.RchrProfileFeatures
import com.medisense.app.domain.rchr.RchrRepresentation
import com.medisense.app.domain.rchr.RchrSymptomFeatures
import com.medisense.app.domain.rchr.RchrTemporalFeatures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PersonalizedGuidanceEngineUnitTest {

    private lateinit var engine: PersonalizedGuidanceEngine

    @Before
    fun setUp() {
        engine = PersonalizedGuidanceEngine()
    }

    @Test
    fun `test fresh user with no data produces baseline setup guidance`() {
        val result = engine.evaluate(
            userId = "user_fresh",
            personalContext = null,
            longitudinalSummary = null,
            rchr = null,
            riskAssessment = null
        )

        assertEquals("user_fresh", result.userId)
        assertFalse(result.guidanceList.isEmpty())
        val baseline = result.guidanceList.find { it.id == GuidanceConfiguration.RULE_BASELINE_SETUP }
        assertNotNull(baseline)
        assertEquals(GuidanceCategory.RECORD_MAINTENANCE, baseline?.category)
        assertEquals(GuidanceActionType.NAVIGATE_PROFILE, baseline?.actionType)
        assertNotNull(result.dataLimitationsNotice)
    }

    @Test
    fun `test incomplete profile produces profile completeness guidance`() {
        val context = PersonalHealthContext(
            userId = "user_incomplete_prof",
            demographics = DemographicContext(age = 22, gender = "Female"),
            chronicConditions = ChronicConditionContext(hasChronicConditions = false, conditions = emptyList()),
            allergies = AllergyContext(hasAllergies = false, allergiesList = emptyList()),
            medications = MedicationContext(activeCount = 0),
            predictions = PredictionContext(totalCount = 0),
            appointments = AppointmentContext(upcomingCount = 0),
            profileCompleteness = 40,
            personalizationScore = 30f,
            generatedSummary = "Profile 40%",
            whyPersonalized = "Test",
            hasSufficientData = false
        )

        val result = engine.evaluate(
            userId = "user_incomplete_prof",
            personalContext = context,
            longitudinalSummary = null,
            rchr = null,
            riskAssessment = null
        )

        val profileGuidance = result.guidanceList.find { it.id == GuidanceConfiguration.RULE_PROFILE_COMPLETENESS }
        assertNotNull(profileGuidance)
        assertEquals(GuidanceCategory.PROFILE_COMPLETENESS, profileGuidance?.category)
        assertEquals(GuidanceActionType.NAVIGATE_PROFILE, profileGuidance?.actionType)
        assertEquals("Update Profile", profileGuidance?.actionLabel)
        assertTrue(profileGuidance!!.explanation.contains("40%"))
    }

    @Test
    fun `test recurring symptoms produces symptom monitoring guidance`() {
        val longitudinal = LongitudinalHealthSummary(
            userId = "user_symptoms",
            period = AnalysisPeriod.DAYS_30,
            predictionActivity = PredictionActivityTrend(1, 2, TrendDirection.INCREASING, 100f, emptyList(), emptyList()),
            recurringSymptoms = listOf(
                RecurringSymptom("cough", 4, 1000L, 5000L, true),
                RecurringSymptom("headache", 3, 2000L, 6000L, true)
            ),
            confidenceTrend = ConfidenceTrend(0.80f, 0.70f, TrendDirection.STABLE, 0f, emptyList()),
            adherenceTrend = AdherenceTrend(90f, 90f, TrendDirection.STABLE, 0f, 10, 9, 1, 0, emptyList()),
            appointmentActivity = AppointmentActivityTrend(0, 0, 0, TrendDirection.STABLE),
            detectedPatterns = emptyList(),
            generatedSummary = "Recurring symptoms detected",
            hasSufficientData = true
        )

        val result = engine.evaluate(
            userId = "user_symptoms",
            personalContext = null,
            longitudinalSummary = longitudinal,
            rchr = null,
            riskAssessment = null
        )

        val symptomGuidance = result.guidanceList.find { it.id == GuidanceConfiguration.RULE_SYMPTOM_MONITORING }
        assertNotNull(symptomGuidance)
        assertEquals(GuidanceCategory.SYMPTOM_MONITORING, symptomGuidance?.category)
        assertEquals(GuidancePriority.HIGH, symptomGuidance?.priority)
        assertEquals(GuidanceActionType.NAVIGATE_TRENDS, symptomGuidance?.actionType)
        assertTrue(symptomGuidance!!.message.contains("cough"))
        assertTrue(symptomGuidance.message.contains("headache"))
    }

    @Test
    fun `test medication adherence guidance focuses on routine adherence without dosage modification`() {
        val personalContext = PersonalHealthContext(
            userId = "user_meds",
            demographics = DemographicContext(age = 50, gender = "Male", bmi = 27.0),
            chronicConditions = ChronicConditionContext(hasChronicConditions = true, conditions = listOf("Hypertension")),
            allergies = AllergyContext(hasAllergies = false, allergiesList = emptyList()),
            medications = MedicationContext(
                activeCount = 2,
                activeNames = listOf("Amlodipine", "Lisinopril"),
                adherencePercentage = 55.0f,
                adherenceSummary = "55% adherence"
            ),
            predictions = PredictionContext(totalCount = 2, recentCount = 1),
            appointments = AppointmentContext(upcomingCount = 0),
            profileCompleteness = 95,
            personalizationScore = 70f,
            generatedSummary = "Medication test",
            whyPersonalized = "Test",
            hasSufficientData = true
        )

        val result = engine.evaluate(
            userId = "user_meds",
            personalContext = personalContext,
            longitudinalSummary = null,
            rchr = null,
            riskAssessment = null
        )

        val medGuidance = result.guidanceList.find { it.id == GuidanceConfiguration.RULE_MEDICATION_ADHERENCE }
        assertNotNull(medGuidance)
        assertEquals(GuidanceCategory.MEDICATION_ADHERENCE, medGuidance?.category)
        assertEquals(GuidancePriority.HIGH, medGuidance?.priority)
        assertEquals(GuidanceActionType.NAVIGATE_MEDICATIONS, medGuidance?.actionType)
        assertEquals("Review Medications", medGuidance?.actionLabel)

        // Safety check: ensure dosage manipulation words do NOT appear
        val medText = "${medGuidance!!.title} ${medGuidance.message} ${medGuidance.explanation}".lowercase()
        assertFalse(medText.contains("take 500mg"))
        assertFalse(medText.contains("increase dosage"))
        assertFalse(medText.contains("change dosage"))
        assertFalse(medText.contains("stop taking"))
    }

    @Test
    fun `test upcoming appointment produces appointment follow-up guidance`() {
        val personalContext = PersonalHealthContext(
            userId = "user_appt",
            demographics = DemographicContext(age = 35, gender = "Female"),
            chronicConditions = ChronicConditionContext(hasChronicConditions = false, conditions = emptyList()),
            allergies = AllergyContext(hasAllergies = false, allergiesList = emptyList()),
            medications = MedicationContext(activeCount = 0),
            predictions = PredictionContext(totalCount = 1, recentCount = 0),
            appointments = AppointmentContext(
                upcomingCount = 1,
                nextAppointmentDoctor = "Dr. Strange",
                nextAppointmentDate = "2026-09-18"
            ),
            profileCompleteness = 90,
            personalizationScore = 65f,
            generatedSummary = "Appointment test",
            whyPersonalized = "Test",
            hasSufficientData = true
        )

        val result = engine.evaluate(
            userId = "user_appt",
            personalContext = personalContext,
            longitudinalSummary = null,
            rchr = null,
            riskAssessment = null
        )

        val apptGuidance = result.guidanceList.find { it.id == GuidanceConfiguration.RULE_APPOINTMENT_FOLLOW_UP }
        assertNotNull(apptGuidance)
        assertEquals(GuidanceCategory.APPOINTMENT_FOLLOW_UP, apptGuidance?.category)
        assertEquals(GuidanceActionType.NAVIGATE_APPOINTMENTS, apptGuidance?.actionType)
        assertTrue(apptGuidance!!.message.contains("Dr. Strange"))
        assertTrue(apptGuidance.message.contains("2026-09-18"))
    }

    @Test
    fun `test high contextual risk from Module 11 triggers professional review suggestion`() {
        val factor1 = ContextualRiskFactor(
            factorId = "f1",
            category = ContextualRiskCategory.SYMPTOM_RECURRENCE,
            title = "Recurring symptoms",
            description = "Desc",
            rawContributionScore = 75f,
            weightedContribution = 16.5f,
            weight = 0.22f,
            source = "Module 9B",
            isAvailable = true,
            effectDirection = FactorEffectDirection.INCREASES_SCORE,
            explanation = "Frequent cough"
        )
        val factor2 = ContextualRiskFactor(
            factorId = "f2",
            category = ContextualRiskCategory.RECENT_HEALTH_ACTIVITY,
            title = "Frequent checkup activity",
            description = "Desc",
            rawContributionScore = 70f,
            weightedContribution = 12.6f,
            weight = 0.18f,
            source = "Module 8",
            isAvailable = true,
            effectDirection = FactorEffectDirection.INCREASES_SCORE,
            explanation = "Multiple checkups"
        )

        val highRiskAssessment = ContextualRiskAssessment(
            userId = "user_high_risk",
            overallScore = 68,
            riskLevel = ContextualRiskLevel.HIGH,
            contributingFactors = listOf(factor1, factor2),
            positiveContributors = listOf(factor1, factor2),
            neutralOrMitigatingFactors = emptyList(),
            unavailableFactors = emptyList(),
            generatedSummary = "High contextual priority",
            dataAvailabilitySummary = emptyMap(),
            hasSufficientData = true
        )

        val result = engine.evaluate(
            userId = "user_high_risk",
            personalContext = null,
            longitudinalSummary = null,
            rchr = null,
            riskAssessment = highRiskAssessment
        )

        val reviewGuidance = result.guidanceList.find { it.id == GuidanceConfiguration.RULE_PROFESSIONAL_REVIEW }
        assertNotNull(reviewGuidance)
        assertEquals(GuidanceCategory.PROFESSIONAL_REVIEW, reviewGuidance?.category)
        assertEquals(GuidancePriority.HIGH, reviewGuidance?.priority)
        assertEquals(GuidanceActionType.NAVIGATE_RISK, reviewGuidance?.actionType)
        assertTrue(reviewGuidance!!.message.contains("discussing persistent symptoms with a qualified healthcare provider", ignoreCase = true))
    }

    @Test
    fun `test deduplication merges sources across Module 9B and RCHR`() {
        val longitudinal = LongitudinalHealthSummary(
            userId = "user_dedup",
            period = AnalysisPeriod.DAYS_30,
            predictionActivity = PredictionActivityTrend(1, 2, TrendDirection.INCREASING, 50f, emptyList(), emptyList()),
            recurringSymptoms = listOf(RecurringSymptom("headache", 3, 1000L, 4000L, true)),
            confidenceTrend = ConfidenceTrend(0.80f, 0.80f, TrendDirection.STABLE, 0f, emptyList()),
            adherenceTrend = AdherenceTrend(90f, 90f, TrendDirection.STABLE, 0f, 5, 5, 0, 0, emptyList()),
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
            profileFeatures = RchrProfileFeatures(30, "ADULT", "Male", "O+", 175.0, 70.0, 22.0, "NORMAL", 0, emptyList(), 0, emptyList(), 100),
            symptomFeatures = RchrSymptomFeatures(1, listOf("headache"), listOf("headache"), listOf("headache"), 1),
            predictionFeatures = RchrPredictionFeatures(2, 1, "Cold", listOf("Cold"), 0.80f, 0.75f, 0.85f, TrendDirection.STABLE),
            medicationFeatures = RchrMedicationFeatures(0, emptyList(), 0, false),
            adherenceFeatures = RchrAdherenceFeatures(0, 0, 0, 0, null, "INSUFFICIENT_DATA", TrendDirection.STABLE),
            appointmentFeatures = RchrAppointmentFeatures(0, null, null, null, 0, TrendDirection.STABLE),
            temporalFeatures = RchrTemporalFeatures(30, TrendDirection.INCREASING, 50f, TrendDirection.STABLE, TrendDirection.STABLE, TrendDirection.STABLE, 0, emptyList()),
            contextFeatures = RchrContextFeatures(75f, 80, "Summary", "Why"),
            hasSufficientData = true
        )

        val result = engine.evaluate(
            userId = "user_dedup",
            personalContext = null,
            longitudinalSummary = longitudinal,
            rchr = rchr,
            riskAssessment = null
        )

        val symptomItems = result.guidanceList.filter { it.category == GuidanceCategory.SYMPTOM_MONITORING }
        assertEquals(1, symptomItems.size)
        assertTrue(symptomItems.first().sources.contains(GuidanceConfiguration.SOURCE_MODULE_9B))
        assertTrue(symptomItems.first().sources.contains(GuidanceConfiguration.SOURCE_MODULE_10))
    }

    @Test
    fun `test deterministic priority ranking - high priority items appear first`() {
        val context = PersonalHealthContext(
            userId = "user_ranking",
            demographics = DemographicContext(age = 45, gender = "Male"),
            chronicConditions = ChronicConditionContext(hasChronicConditions = false, conditions = emptyList()),
            allergies = AllergyContext(hasAllergies = false, allergiesList = emptyList()),
            medications = MedicationContext(activeCount = 2, activeNames = listOf("M1", "M2"), adherencePercentage = 40f, adherenceSummary = "40%"),
            predictions = PredictionContext(totalCount = 3, recentCount = 2),
            appointments = AppointmentContext(upcomingCount = 1, nextAppointmentDoctor = "Dr. House", nextAppointmentDate = "2026-09-22"),
            profileCompleteness = 50,
            personalizationScore = 60f,
            generatedSummary = "Ranking test",
            whyPersonalized = "Why",
            hasSufficientData = true
        )

        val result = engine.evaluate(
            userId = "user_ranking",
            personalContext = context,
            longitudinalSummary = null,
            rchr = null,
            riskAssessment = null
        )

        assertTrue(result.guidanceList.size <= GuidanceConfiguration.MAX_DISPLAYED_RECOMMENDATIONS)
        for (i in 0 until result.guidanceList.size - 1) {
            assertTrue(result.guidanceList[i].priority.level >= result.guidanceList[i + 1].priority.level)
        }
    }

    @Test
    fun `test safety filter blocks prohibited diagnosis and prescription statements`() {
        val unsafeDiagnosis = PersonalizedGuidance(
            id = "unsafe_1",
            category = GuidanceCategory.HEALTH_TRACKING,
            title = "Confirmed Flu Diagnosis",
            message = "You have flu and should stay in bed.",
            explanation = "Diagnosed with severe symptoms.",
            priority = GuidancePriority.HIGH,
            sources = listOf("Test")
        )

        val unsafePrescription = PersonalizedGuidance(
            id = "unsafe_2",
            category = GuidanceCategory.MEDICATION_ADHERENCE,
            title = "Change your medicine",
            message = "Take 500mg of aspirin and stop taking your current pills.",
            explanation = "Double your dose for better effect.",
            priority = GuidancePriority.HIGH,
            sources = listOf("Test")
        )

        val safeGuidance = PersonalizedGuidance(
            id = "safe_1",
            category = GuidanceCategory.HEALTH_TRACKING,
            title = "Review your health trends",
            message = "Your MediSense history contains recorded checkups.",
            explanation = "Longitudinal health analysis identified trends.",
            priority = GuidancePriority.LOW,
            sources = listOf("Test")
        )

        assertFalse(GuidanceSafetyFilter.isSafe(unsafeDiagnosis))
        assertFalse(GuidanceSafetyFilter.isSafe(unsafePrescription))
        assertTrue(GuidanceSafetyFilter.isSafe(safeGuidance))

        val filtered = GuidanceSafetyFilter.filterSafeGuidance(listOf(unsafeDiagnosis, unsafePrescription, safeGuidance))
        assertEquals(1, filtered.size)
        assertEquals("safe_1", filtered.first().id)
    }

    @Test
    fun `test strict user isolation between user A and user B`() {
        val contextA = PersonalHealthContext(
            userId = "user_A",
            demographics = DemographicContext(age = 20, gender = "Female"),
            chronicConditions = ChronicConditionContext(hasChronicConditions = false, conditions = emptyList()),
            allergies = AllergyContext(hasAllergies = false, allergiesList = emptyList()),
            medications = MedicationContext(activeCount = 0),
            predictions = PredictionContext(totalCount = 0),
            appointments = AppointmentContext(upcomingCount = 0),
            profileCompleteness = 30,
            personalizationScore = 20f,
            generatedSummary = "User A",
            whyPersonalized = "A",
            hasSufficientData = false
        )

        val contextB = PersonalHealthContext(
            userId = "user_B",
            demographics = DemographicContext(age = 60, gender = "Male"),
            chronicConditions = ChronicConditionContext(hasChronicConditions = true, conditions = listOf("Asthma")),
            allergies = AllergyContext(hasAllergies = true, allergiesList = listOf("Penicillin")),
            medications = MedicationContext(activeCount = 1, activeNames = listOf("Inhaler"), adherencePercentage = 50f, adherenceSummary = "50%"),
            predictions = PredictionContext(totalCount = 5, recentCount = 3),
            appointments = AppointmentContext(upcomingCount = 1, nextAppointmentDoctor = "Dr. Grey", nextAppointmentDate = "2026-09-30"),
            profileCompleteness = 100,
            personalizationScore = 85f,
            generatedSummary = "User B",
            whyPersonalized = "B",
            hasSufficientData = true
        )

        val resultA = engine.evaluate("user_A", contextA, null, null, null)
        val resultB = engine.evaluate("user_B", contextB, null, null, null)

        assertEquals("user_A", resultA.userId)
        assertEquals("user_B", resultB.userId)
        assertFalse(resultA.guidanceList.map { it.id } == resultB.guidanceList.map { it.id })
    }
}
