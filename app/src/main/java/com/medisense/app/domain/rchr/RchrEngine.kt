package com.medisense.app.domain.rchr

import com.medisense.app.data.local.entity.AppointmentEntity
import com.medisense.app.data.local.entity.HealthProfileEntity
import com.medisense.app.data.local.entity.MedicationEntity
import com.medisense.app.data.local.entity.MedicationHistoryEntity
import com.medisense.app.data.local.entity.PredictionHistoryEntity
import com.medisense.app.domain.analytics.TemporalPatternAnalyzer
import com.medisense.app.domain.model.AnalysisPeriod
import com.medisense.app.domain.model.LongitudinalHealthSummary
import com.medisense.app.domain.model.PersonalHealthContext
import com.medisense.app.domain.model.PersonalizationFeatures
import com.medisense.app.domain.model.TrendDirection
import com.medisense.app.utils.MedicationDateTimeUtils
import java.util.Locale

/**
 * Pure Kotlin, deterministic engine for building, reconstructing,
 * and validating the Reversible Composite Health Representation (RCHR).
 */
object RchrEngine {

    const val REPRESENTATION_VERSION = "1.0"
    private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    private const val CORE_FEATURE_CAPACITY = 20

    /**
     * Deterministically builds the RCHR from multi-source local health data.
     */
    fun buildRepresentation(
        userId: String,
        profile: HealthProfileEntity?,
        predictions: List<PredictionHistoryEntity>,
        medications: List<MedicationEntity>,
        medicationHistory: List<MedicationHistoryEntity>,
        appointments: List<AppointmentEntity>,
        temporalSummary: LongitudinalHealthSummary? = null,
        personalContext: PersonalHealthContext? = null,
        now: Long = System.currentTimeMillis()
    ): RchrRepresentation {

        // 1. Profile Features
        val age = PersonalizationFeatures.calculateAge(profile?.dateOfBirth)
        val ageGroup = when {
            age == null -> null
            age < 18 -> "CHILD"
            age in 18..35 -> "YOUNG_ADULT"
            age in 36..50 -> "ADULT"
            age in 51..65 -> "MIDDLE_AGED"
            else -> "SENIOR"
        }

        val gender = profile?.gender?.trim()?.uppercase(Locale.getDefault())?.ifBlank { null }
        val bloodGroup = profile?.bloodGroup?.trim()?.uppercase(Locale.getDefault())?.ifBlank { null }
        val height = profile?.height
        val weight = profile?.weight
        val bmi = PersonalizationFeatures.calculateBmi(height, weight)
        val bmiCategory = when {
            bmi == null -> null
            bmi < 18.5 -> "UNDERWEIGHT"
            bmi < 25.0 -> "NORMAL"
            bmi < 30.0 -> "OVERWEIGHT"
            else -> "OBESE"
        }

        val allergyList = PersonalizationFeatures.parseListString(profile?.allergies).sorted()
        val chronicConditionList = PersonalizationFeatures.parseListString(profile?.existingDiseases).sorted()
        val profileCompleteness = PersonalizationFeatures.calculateProfileCompleteness(profile)

        val profileFeatures = RchrProfileFeatures(
            age = age,
            ageGroup = ageGroup,
            gender = gender,
            bloodGroup = bloodGroup,
            heightCm = height,
            weightKg = weight,
            bmi = bmi,
            bmiCategory = bmiCategory,
            allergyCount = allergyList.size,
            allergyList = allergyList,
            chronicConditionCount = chronicConditionList.size,
            chronicConditionList = chronicConditionList,
            profileCompletenessPercent = profileCompleteness
        )

        // 2. Symptom Features
        val symptomMap = mutableMapOf<String, Int>()
        var recentSymptomCount = 0
        val recentCutoff = now - (30 * DAY_MILLIS)

        for (pred in predictions) {
            val isRecent = pred.predictionTimestamp >= recentCutoff
            for (sym in pred.symptoms) {
                val cleaned = sym.replace("_", " ").trim().lowercase(Locale.getDefault())
                if (cleaned.isNotBlank()) {
                    val titleCased = cleaned.split(" ").joinToString(" ") { word ->
                        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                    }
                    symptomMap[titleCased] = (symptomMap[titleCased] ?: 0) + 1
                    if (isRecent) recentSymptomCount++
                }
            }
        }

        val allRecordedSymptoms = symptomMap.keys.sorted()
        val frequentSymptoms = symptomMap.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
        val recurringSymptoms = symptomMap.entries
            .filter { it.value >= 2 }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }

        val symptomFeatures = RchrSymptomFeatures(
            distinctSymptomCount = allRecordedSymptoms.size,
            allRecordedSymptoms = allRecordedSymptoms,
            frequentSymptoms = frequentSymptoms,
            recurringSymptoms = recurringSymptoms,
            recentSymptomCount = recentSymptomCount
        )

        // 3. Prediction Features
        val totalPredCount = predictions.size
        val recentPreds = predictions.filter { it.predictionTimestamp >= recentCutoff }
        val diseaseCounts = predictions.groupingBy { it.predictedDisease.trim() }.eachCount()
        val topDiseases = diseaseCounts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { entry ->
                entry.key.split(" ").joinToString(" ") { word ->
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                }
            }

        val dominantDisease = topDiseases.firstOrNull()
        val confidences = predictions.map { it.confidence }
        val avgConf = if (confidences.isNotEmpty()) confidences.average().toFloat() else null
        val minConf = confidences.minOrNull()
        val maxConf = confidences.maxOrNull()
        val confTrendDir = temporalSummary?.confidenceTrend?.direction ?: TrendDirection.INSUFFICIENT_DATA

        val predictionFeatures = RchrPredictionFeatures(
            totalPredictionCount = totalPredCount,
            recentPredictionCount = recentPreds.size,
            dominantPredictedDisease = dominantDisease,
            topPredictedDiseases = topDiseases,
            averageConfidence = avgConf,
            confidenceRangeMin = minConf,
            confidenceRangeMax = maxConf,
            confidenceTrendDirection = confTrendDir
        )

        // 4. Medication Features
        val activeMeds = medications.filter { it.active }.map { it.medicineName.trim() }.sorted()
        val medicationFeatures = RchrMedicationFeatures(
            activeMedicationCount = activeMeds.size,
            activeMedicationNames = activeMeds,
            totalPrescribedMedicationCount = medications.size,
            hasActiveMedications = activeMeds.isNotEmpty()
        )

        // 5. Adherence Features
        val adhStats = MedicationDateTimeUtils.calculateAdherence(medicationHistory)
        val adhPct = if (medicationHistory.isNotEmpty()) adhStats.percentage else null
        val adhCategory = when {
            adhPct == null -> "INSUFFICIENT_DATA"
            adhPct >= 80.0f -> "OPTIMAL"
            adhPct >= 60.0f -> "MODERATE"
            else -> "SUBOPTIMAL"
        }
        val adhTrendDir = temporalSummary?.adherenceTrend?.direction ?: TrendDirection.INSUFFICIENT_DATA

        val adherenceFeatures = RchrAdherenceFeatures(
            recordedDoseCount = medicationHistory.size,
            takenCount = adhStats.takenCount,
            missedCount = adhStats.missedCount,
            skippedCount = adhStats.skippedCount,
            adherencePercentage = adhPct,
            adherenceCategory = adhCategory,
            adherenceTrendDirection = adhTrendDir
        )

        // 6. Appointment Features
        val upcomingAppointments = appointments
            .filter { it.status == "SCHEDULED" && it.appointmentTimestamp >= now }
            .sortedBy { it.appointmentTimestamp }
        val pastAppointments = appointments.filter { it.appointmentTimestamp < now }
        val nextAppt = upcomingAppointments.firstOrNull()

        val appointmentFeatures = RchrAppointmentFeatures(
            upcomingAppointmentCount = upcomingAppointments.size,
            nextAppointmentDoctor = nextAppt?.doctorName,
            nextAppointmentDate = nextAppt?.appointmentDate,
            nextAppointmentType = nextAppt?.appointmentType,
            pastAppointmentCount = pastAppointments.size,
            appointmentTrendDirection = temporalSummary?.appointmentActivity?.direction ?: TrendDirection.INSUFFICIENT_DATA
        )

        // 7. Temporal Features
        val detectedPatterns = temporalSummary?.detectedPatterns?.map { it.title }?.sorted() ?: emptyList()
        val temporalFeatures = RchrTemporalFeatures(
            analysisWindowDays = 30,
            predictionActivityTrend = temporalSummary?.predictionActivity?.direction ?: TrendDirection.INSUFFICIENT_DATA,
            predictionChangePercent = temporalSummary?.predictionActivity?.changePercentage,
            confidenceTrend = temporalSummary?.confidenceTrend?.direction ?: TrendDirection.INSUFFICIENT_DATA,
            adherenceTrend = temporalSummary?.adherenceTrend?.direction ?: TrendDirection.INSUFFICIENT_DATA,
            appointmentTrend = temporalSummary?.appointmentActivity?.direction ?: TrendDirection.INSUFFICIENT_DATA,
            detectedPatternsCount = detectedPatterns.size,
            detectedPatternTitles = detectedPatterns
        )

        // 8. Context Features
        val personalizationScore = personalContext?.personalizationScore ?: 0.0f
        val contextFeatures = RchrContextFeatures(
            personalizationScore = personalizationScore,
            contextCompleteness = profileCompleteness,
            contextSummary = personalContext?.generatedSummary ?: "No active context summary generated.",
            whyPersonalized = personalContext?.whyPersonalized ?: "Based on your health records and activity history."
        )

        // Count encoded non-empty attributes
        var encodedCount = 0
        if (age != null) encodedCount++
        if (gender != null) encodedCount++
        if (bloodGroup != null) encodedCount++
        if (bmi != null) encodedCount++
        if (allergyList.isNotEmpty()) encodedCount++
        if (chronicConditionList.isNotEmpty()) encodedCount++
        if (allRecordedSymptoms.isNotEmpty()) encodedCount++
        if (recurringSymptoms.isNotEmpty()) encodedCount++
        if (totalPredCount > 0) encodedCount++
        if (dominantDisease != null) encodedCount++
        if (avgConf != null) encodedCount++
        if (activeMeds.isNotEmpty()) encodedCount++
        if (medicationHistory.isNotEmpty()) encodedCount++
        if (upcomingAppointments.isNotEmpty()) encodedCount++
        if (pastAppointments.isNotEmpty()) encodedCount++
        if (detectedPatterns.isNotEmpty()) encodedCount++
        if (personalizationScore > 0f) encodedCount++

        val completeness = ((encodedCount.toFloat() / CORE_FEATURE_CAPACITY.toFloat()) * 100).toInt().coerceIn(0, 100)
        val hasSufficientData = encodedCount > 0

        return RchrRepresentation(
            userId = userId,
            representationVersion = REPRESENTATION_VERSION,
            generatedAt = now,
            totalEncodedFeatures = encodedCount,
            completenessPercentage = completeness,
            profileFeatures = profileFeatures,
            symptomFeatures = symptomFeatures,
            predictionFeatures = predictionFeatures,
            medicationFeatures = medicationFeatures,
            adherenceFeatures = adherenceFeatures,
            appointmentFeatures = appointmentFeatures,
            temporalFeatures = temporalFeatures,
            contextFeatures = contextFeatures,
            hasSufficientData = hasSufficientData
        )
    }

    /**
     * Deterministically reconstructs human-readable health attributes from an RCHR.
     */
    fun reconstructHealthState(representation: RchrRepresentation): RchrReconstructionResult {
        val reconstructed = mutableListOf<ReconstructedAttribute>()
        val unavailable = mutableListOf<String>()

        // 1. Profile Reconstruction
        val prof = representation.profileFeatures
        if (prof.age != null && prof.ageGroup != null) {
            val formattedGroup = prof.ageGroup.replace("_", " ").lowercase(Locale.getDefault()).split(" ")
                .joinToString(" ") { word ->
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                }
            reconstructed.add(
                ReconstructedAttribute(
                    category = "Profile",
                    attributeKey = "demographics_age",
                    encodedValue = "Age: ${prof.age} (${prof.ageGroup})",
                    humanReadableMeaning = "Age is ${prof.age} years old ($formattedGroup category)",
                    isAvailable = true
                )
            )
        } else {
            unavailable.add("Age / Date of birth not recorded")
        }

        if (prof.gender != null) {
            val formattedGender = prof.gender.lowercase(Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }
            reconstructed.add(
                ReconstructedAttribute(
                    category = "Profile",
                    attributeKey = "demographics_gender",
                    encodedValue = prof.gender,
                    humanReadableMeaning = "Gender is recorded as $formattedGender",
                    isAvailable = true
                )
            )
        } else {
            unavailable.add("Gender not recorded")
        }

        if (prof.bloodGroup != null) {
            reconstructed.add(
                ReconstructedAttribute(
                    category = "Profile",
                    attributeKey = "demographics_blood_group",
                    encodedValue = prof.bloodGroup,
                    humanReadableMeaning = "Blood group is documented as ${prof.bloodGroup}",
                    isAvailable = true
                )
            )
        }

        if (prof.bmi != null && prof.bmiCategory != null) {
            val formattedBmiCat = prof.bmiCategory.replace("_", " ").lowercase(Locale.getDefault()).split(" ")
                .joinToString(" ") { word ->
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                }
            reconstructed.add(
                ReconstructedAttribute(
                    category = "Profile",
                    attributeKey = "body_composition_bmi",
                    encodedValue = "BMI: ${prof.bmi} (${prof.bmiCategory})",
                    humanReadableMeaning = "Body Mass Index is ${prof.bmi} ($formattedBmiCat range)",
                    isAvailable = true
                )
            )
        } else {
            unavailable.add("Height / Weight for BMI calculation not recorded")
        }

        if (prof.allergyList.isNotEmpty()) {
            reconstructed.add(
                ReconstructedAttribute(
                    category = "Profile",
                    attributeKey = "allergies",
                    encodedValue = prof.allergyList.joinToString(", "),
                    humanReadableMeaning = "${prof.allergyCount} documented allergy/allergies: ${prof.allergyList.joinToString(", ")}",
                    isAvailable = true
                )
            )
        }

        if (prof.chronicConditionList.isNotEmpty()) {
            reconstructed.add(
                ReconstructedAttribute(
                    category = "Profile",
                    attributeKey = "chronic_conditions",
                    encodedValue = prof.chronicConditionList.joinToString(", "),
                    humanReadableMeaning = "${prof.chronicConditionCount} existing chronic condition(s): ${prof.chronicConditionList.joinToString(", ")}",
                    isAvailable = true
                )
            )
        }

        // 2. Symptoms Reconstruction
        val sym = representation.symptomFeatures
        if (sym.allRecordedSymptoms.isNotEmpty()) {
            reconstructed.add(
                ReconstructedAttribute(
                    category = "Symptoms",
                    attributeKey = "recorded_symptoms",
                    encodedValue = "${sym.distinctSymptomCount} distinct symptoms (${sym.allRecordedSymptoms.take(4).joinToString(", ")})",
                    humanReadableMeaning = "History contains ${sym.distinctSymptomCount} unique recorded symptoms",
                    isAvailable = true
                )
            )
        } else {
            unavailable.add("No symptom history recorded")
        }

        if (sym.recurringSymptoms.isNotEmpty()) {
            reconstructed.add(
                ReconstructedAttribute(
                    category = "Symptoms",
                    attributeKey = "recurring_symptoms",
                    encodedValue = sym.recurringSymptoms.joinToString(", "),
                    humanReadableMeaning = "Identified recurring symptom patterns: ${sym.recurringSymptoms.joinToString(", ")}",
                    isAvailable = true
                )
            )
        }

        // 3. Predictions Reconstruction
        val pred = representation.predictionFeatures
        if (pred.totalPredictionCount > 0) {
            val domStr = pred.dominantPredictedDisease?.let { " (Most frequent: $it)" } ?: ""
            reconstructed.add(
                ReconstructedAttribute(
                    category = "Predictions",
                    attributeKey = "prediction_history",
                    encodedValue = "${pred.totalPredictionCount} total records$domStr",
                    humanReadableMeaning = "Logged ${pred.totalPredictionCount} disease prediction assessments${if (pred.dominantPredictedDisease != null) ", frequently predicting ${pred.dominantPredictedDisease}" else ""}",
                    isAvailable = true
                )
            )
        } else {
            unavailable.add("No prediction history recorded")
        }

        if (pred.averageConfidence != null) {
            val confPct = (pred.averageConfidence * 100).toInt()
            reconstructed.add(
                ReconstructedAttribute(
                    category = "Predictions",
                    attributeKey = "model_confidence_average",
                    encodedValue = "Avg: $confPct%",
                    humanReadableMeaning = "AI model average classification certainty score is $confPct%",
                    isAvailable = true
                )
            )
        }

        // 4. Medications Reconstruction
        val med = representation.medicationFeatures
        if (med.hasActiveMedications) {
            reconstructed.add(
                ReconstructedAttribute(
                    category = "Medications",
                    attributeKey = "active_medications",
                    encodedValue = "${med.activeMedicationCount} active (${med.activeMedicationNames.joinToString(", ")})",
                    humanReadableMeaning = "${med.activeMedicationCount} active medication regimen(s) tracked: ${med.activeMedicationNames.joinToString(", ")}",
                    isAvailable = true
                )
            )
        } else {
            unavailable.add("No active medication reminders configured")
        }

        // 5. Adherence Reconstruction
        val adh = representation.adherenceFeatures
        if (adh.recordedDoseCount > 0 && adh.adherencePercentage != null) {
            val adhpct = adh.adherencePercentage.toInt()
            reconstructed.add(
                ReconstructedAttribute(
                    category = "Adherence",
                    attributeKey = "medication_adherence",
                    encodedValue = "$adhpct% ($adh.adherenceCategory)",
                    humanReadableMeaning = "Medication adherence is $adhpct% (${adh.takenCount} taken, ${adh.missedCount} missed out of ${adh.recordedDoseCount} scheduled doses)",
                    isAvailable = true
                )
            )
        } else {
            unavailable.add("No medication adherence intake logs recorded")
        }

        // 6. Appointments Reconstruction
        val appt = representation.appointmentFeatures
        if (appt.upcomingAppointmentCount > 0 && appt.nextAppointmentDoctor != null) {
            val dateStr = appt.nextAppointmentDate?.let { " on $it" } ?: ""
            val typeStr = appt.nextAppointmentType?.let { " ($it)" } ?: ""
            reconstructed.add(
                ReconstructedAttribute(
                    category = "Appointments",
                    attributeKey = "upcoming_appointments",
                    encodedValue = "${appt.upcomingAppointmentCount} upcoming (Next: ${appt.nextAppointmentDoctor}$dateStr)",
                    humanReadableMeaning = "${appt.upcomingAppointmentCount} upcoming doctor appointment(s) scheduled. Next with ${appt.nextAppointmentDoctor}$dateStr$typeStr",
                    isAvailable = true
                )
            )
        } else {
            unavailable.add("No upcoming doctor appointments scheduled")
        }

        // 7. Temporal Features Reconstruction
        val temp = representation.temporalFeatures
        if (temp.detectedPatternsCount > 0) {
            reconstructed.add(
                ReconstructedAttribute(
                    category = "Temporal Dynamics",
                    attributeKey = "temporal_patterns",
                    encodedValue = "${temp.detectedPatternsCount} patterns detected (${temp.detectedPatternTitles.joinToString(", ")})",
                    humanReadableMeaning = "Longitudinal analysis identified ${temp.detectedPatternsCount} temporal patterns: ${temp.detectedPatternTitles.joinToString("; ")}",
                    isAvailable = true
                )
            )
        }

        if (temp.predictionActivityTrend != TrendDirection.INSUFFICIENT_DATA) {
            reconstructed.add(
                ReconstructedAttribute(
                    category = "Temporal Dynamics",
                    attributeKey = "activity_trend",
                    encodedValue = temp.predictionActivityTrend.name,
                    humanReadableMeaning = "Prediction logging activity trend over ${temp.analysisWindowDays} days: ${temp.predictionActivityTrend.name.lowercase(Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }}",
                    isAvailable = true
                )
            )
        }

        // 8. Context Reconstruction
        val ctx = representation.contextFeatures
        if (ctx.personalizationScore > 0f) {
            reconstructed.add(
                ReconstructedAttribute(
                    category = "Personal Context",
                    attributeKey = "personalization_score",
                    encodedValue = "Score: ${ctx.personalizationScore.toInt()}/100",
                    humanReadableMeaning = "Multi-source context engagement index is ${ctx.personalizationScore.toInt()} out of 100",
                    isAvailable = true
                )
            )
        }

        // Validation & Consistency Score Calculation
        val totalReconstructable = reconstructed.size
        val successful = reconstructed.count { it.isAvailable && it.humanReadableMeaning.isNotBlank() }
        val consistencyScore = if (totalReconstructable > 0) {
            Math.round((successful.toFloat() / totalReconstructable.toFloat()) * 1000f) / 10f
        } else {
            100.0f
        }

        return RchrReconstructionResult(
            representationVersion = representation.representationVersion,
            reconstructedAt = System.currentTimeMillis(),
            reconstructedAttributes = reconstructed,
            unavailableAttributes = unavailable,
            mismatchedAttributes = emptyList(),
            totalReconstructableCount = totalReconstructable,
            successfullyReconstructedCount = successful,
            reconstructionConsistencyScore = consistencyScore,
            isConsistent = totalReconstructable == successful
        )
    }
}
