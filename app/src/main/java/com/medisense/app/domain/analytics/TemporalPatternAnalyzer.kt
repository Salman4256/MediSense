package com.medisense.app.domain.analytics

import com.medisense.app.data.local.entity.AppointmentEntity
import com.medisense.app.data.local.entity.MedicationEntity
import com.medisense.app.data.local.entity.MedicationHistoryEntity
import com.medisense.app.data.local.entity.PredictionHistoryEntity
import com.medisense.app.domain.model.AdherenceTrend
import com.medisense.app.domain.model.AnalysisPeriod
import com.medisense.app.domain.model.AppointmentActivityTrend
import com.medisense.app.domain.model.ConfidenceTrend
import com.medisense.app.domain.model.DailyMetricPoint
import com.medisense.app.domain.model.LongitudinalHealthSummary
import com.medisense.app.domain.model.PatternCategory
import com.medisense.app.domain.model.PatternSeverity
import com.medisense.app.domain.model.PredictionActivityTrend
import com.medisense.app.domain.model.RecurringSymptom
import com.medisense.app.domain.model.TemporalPattern
import com.medisense.app.domain.model.TrendDirection
import com.medisense.app.utils.MedicationDateTimeUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Pure Kotlin, deterministic temporal analysis engine.
 * Computes longitudinal trends and detects explainable health patterns across configurable time windows.
 */
object TemporalPatternAnalyzer {

    // Configurable thresholds
    const val MIN_OBSERVATIONS_FOR_RECURRENCE = 2
    const val ACTIVITY_CHANGE_THRESHOLD_PERCENT = 25.0f
    const val CONFIDENCE_CHANGE_THRESHOLD_PERCENT = 5.0f
    const val ADHERENCE_CHANGE_THRESHOLD_PERCENT = 8.0f
    const val HIGH_ADHERENCE_THRESHOLD_PERCENT = 80.0f

    private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

    fun analyzeLongitudinalHealth(
        userId: String,
        period: AnalysisPeriod,
        predictions: List<PredictionHistoryEntity>,
        medications: List<MedicationEntity>,
        medicationHistory: List<MedicationHistoryEntity>,
        appointments: List<AppointmentEntity>,
        now: Long = System.currentTimeMillis()
    ): LongitudinalHealthSummary {
        val currentPeriodStart = now - (period.days * DAY_MILLIS)
        val previousPeriodStart = now - (2 * period.days * DAY_MILLIS)
        val previousPeriodEnd = currentPeriodStart

        // 1. Prediction Activity & Trends
        val currentPredictions = predictions.filter { it.predictionTimestamp in currentPeriodStart..now }
        val previousPredictions = predictions.filter { it.predictionTimestamp in previousPeriodStart until previousPeriodEnd }

        val predictionActivityTrend = calculatePredictionActivityTrend(
            currentPredictions = currentPredictions,
            previousPredictions = previousPredictions,
            period = period,
            currentPeriodStart = currentPeriodStart,
            now = now
        )

        // 2. Symptom Recurrence Analysis
        val recurringSymptoms = analyzeSymptomRecurrence(currentPredictions)

        // 3. Prediction Confidence Trend
        val confidenceTrend = calculateConfidenceTrend(
            currentPredictions = currentPredictions,
            previousPredictions = previousPredictions,
            period = period,
            currentPeriodStart = currentPeriodStart,
            now = now
        )

        // 4. Medication Adherence Trend
        val currentMedHistory = medicationHistory.filter { it.scheduledDate in currentPeriodStart..now }
        val previousMedHistory = medicationHistory.filter { it.scheduledDate in previousPeriodStart until previousPeriodEnd }

        val adherenceTrend = calculateAdherenceTrend(
            currentHistory = currentMedHistory,
            previousHistory = previousMedHistory,
            period = period,
            currentPeriodStart = currentPeriodStart,
            now = now
        )

        // 5. Appointment Activity Trend
        val currentAppointments = appointments.filter { it.appointmentTimestamp in currentPeriodStart..now }
        val previousAppointments = appointments.filter { it.appointmentTimestamp in previousPeriodStart until previousPeriodEnd }
        val upcomingAppointments = appointments.filter { it.status == "SCHEDULED" && it.appointmentTimestamp >= now }

        val appointmentTrend = calculateAppointmentTrend(
            currentAppointments = currentAppointments,
            previousAppointments = previousAppointments,
            upcomingAppointments = upcomingAppointments
        )

        // 6. Pattern Detection
        val detectedPatterns = detectTemporalPatterns(
            period = period,
            predictionTrend = predictionActivityTrend,
            recurringSymptoms = recurringSymptoms,
            confidenceTrend = confidenceTrend,
            adherenceTrend = adherenceTrend,
            upcomingAppointments = upcomingAppointments,
            activeMeds = medications.filter { it.active }
        )

        // 7. Factual Summary Generation
        val hasSufficientData = currentPredictions.isNotEmpty() || currentMedHistory.isNotEmpty() || currentAppointments.isNotEmpty() || upcomingAppointments.isNotEmpty()
        val generatedSummary = generateFactualLongitudinalSummary(
            period = period,
            predictionTrend = predictionActivityTrend,
            recurringSymptoms = recurringSymptoms,
            adherenceTrend = adherenceTrend,
            appointmentTrend = appointmentTrend,
            hasSufficientData = hasSufficientData
        )

        return LongitudinalHealthSummary(
            userId = userId,
            period = period,
            predictionActivity = predictionActivityTrend,
            recurringSymptoms = recurringSymptoms,
            confidenceTrend = confidenceTrend,
            adherenceTrend = adherenceTrend,
            appointmentActivity = appointmentTrend,
            detectedPatterns = detectedPatterns,
            generatedSummary = generatedSummary,
            hasSufficientData = hasSufficientData
        )
    }

    private fun calculatePredictionActivityTrend(
        currentPredictions: List<PredictionHistoryEntity>,
        previousPredictions: List<PredictionHistoryEntity>,
        period: AnalysisPeriod,
        currentPeriodStart: Long,
        now: Long
    ): PredictionActivityTrend {
        val currentCount = currentPredictions.size
        val prevCount = previousPredictions.size

        val (direction, changePercent) = when {
            currentCount == 0 && prevCount == 0 -> Pair(TrendDirection.INSUFFICIENT_DATA, null)
            prevCount == 0 -> Pair(TrendDirection.INCREASING, 100f)
            else -> {
                val change = ((currentCount - prevCount).toFloat() / prevCount) * 100f
                val dir = when {
                    change >= ACTIVITY_CHANGE_THRESHOLD_PERCENT -> TrendDirection.INCREASING
                    change <= -ACTIVITY_CHANGE_THRESHOLD_PERCENT -> TrendDirection.DECREASING
                    else -> TrendDirection.STABLE
                }
                Pair(dir, Math.round(change * 10f) / 10f)
            }
        }

        // Top conditions in current period
        val topConditions = currentPredictions
            .groupingBy { it.predictedDisease.trim() }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(3)
            .map { entry ->
                entry.key.split(" ").joinToString(" ") { word ->
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                }
            }

        // Daily chart data points
        val dailyPoints = generateDailyPredictionPoints(currentPredictions, currentPeriodStart, now, period)

        return PredictionActivityTrend(
            currentPeriodCount = currentCount,
            previousPeriodCount = prevCount,
            direction = direction,
            changePercentage = changePercent,
            topConditions = topConditions,
            dailyPoints = dailyPoints
        )
    }

    private fun analyzeSymptomRecurrence(predictions: List<PredictionHistoryEntity>): List<RecurringSymptom> {
        if (predictions.isEmpty()) return emptyList()

        data class SymptomRecord(var count: Int, var firstSeen: Long, var lastSeen: Long)
        val symptomMap = mutableMapOf<String, SymptomRecord>()

        for (pred in predictions) {
            for (symptom in pred.symptoms) {
                val cleaned = symptom.replace("_", " ").trim()
                if (cleaned.isNotBlank()) {
                    val record = symptomMap.getOrPut(cleaned) {
                        SymptomRecord(0, pred.predictionTimestamp, pred.predictionTimestamp)
                    }
                    record.count++
                    if (pred.predictionTimestamp < record.firstSeen) record.firstSeen = pred.predictionTimestamp
                    if (pred.predictionTimestamp > record.lastSeen) record.lastSeen = pred.predictionTimestamp
                }
            }
        }

        return symptomMap.entries
            .sortedByDescending { it.value.count }
            .map { (name, record) ->
                val titleCased = name.split(" ").joinToString(" ") { word ->
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                }
                RecurringSymptom(
                    symptomName = titleCased,
                    occurrenceCount = record.count,
                    firstObservedDate = record.firstSeen,
                    lastObservedDate = record.lastSeen,
                    isRecurring = record.count >= MIN_OBSERVATIONS_FOR_RECURRENCE
                )
            }
    }

    private fun calculateConfidenceTrend(
        currentPredictions: List<PredictionHistoryEntity>,
        previousPredictions: List<PredictionHistoryEntity>,
        period: AnalysisPeriod,
        currentPeriodStart: Long,
        now: Long
    ): ConfidenceTrend {
        val currentAvg = if (currentPredictions.isNotEmpty()) {
            currentPredictions.map { it.confidence }.average().toFloat()
        } else null

        val prevAvg = if (previousPredictions.isNotEmpty()) {
            previousPredictions.map { it.confidence }.average().toFloat()
        } else null

        val (direction, changePercent) = when {
            currentAvg == null || prevAvg == null -> Pair(TrendDirection.INSUFFICIENT_DATA, null)
            else -> {
                val diff = (currentAvg - prevAvg) * 100f
                val dir = when {
                    diff >= CONFIDENCE_CHANGE_THRESHOLD_PERCENT -> TrendDirection.INCREASING
                    diff <= -CONFIDENCE_CHANGE_THRESHOLD_PERCENT -> TrendDirection.DECREASING
                    else -> TrendDirection.STABLE
                }
                Pair(dir, Math.round(diff * 10f) / 10f)
            }
        }

        val dailyPoints = generateDailyConfidencePoints(currentPredictions, currentPeriodStart, now)

        return ConfidenceTrend(
            currentAvgConfidence = currentAvg,
            previousAvgConfidence = prevAvg,
            direction = direction,
            changePercentage = changePercent,
            dailyPoints = dailyPoints
        )
    }

    private fun calculateAdherenceTrend(
        currentHistory: List<MedicationHistoryEntity>,
        previousHistory: List<MedicationHistoryEntity>,
        period: AnalysisPeriod,
        currentPeriodStart: Long,
        now: Long
    ): AdherenceTrend {
        val currentStats = MedicationDateTimeUtils.calculateAdherence(currentHistory)
        val prevStats = MedicationDateTimeUtils.calculateAdherence(previousHistory)

        val currentRate = if (currentHistory.isNotEmpty()) currentStats.percentage else null
        val prevRate = if (previousHistory.isNotEmpty()) prevStats.percentage else null

        val (direction, changePercent) = when {
            currentRate == null || prevRate == null -> Pair(TrendDirection.INSUFFICIENT_DATA, null)
            else -> {
                val diff = currentRate - prevRate
                val dir = when {
                    diff >= ADHERENCE_CHANGE_THRESHOLD_PERCENT -> TrendDirection.IMPROVING
                    diff <= -ADHERENCE_CHANGE_THRESHOLD_PERCENT -> TrendDirection.DECLINING
                    else -> TrendDirection.STABLE
                }
                Pair(dir, Math.round(diff * 10f) / 10f)
            }
        }

        val dailyPoints = generateDailyAdherencePoints(currentHistory, currentPeriodStart, now)

        return AdherenceTrend(
            currentAdherencePercentage = currentRate,
            previousAdherencePercentage = prevRate,
            direction = direction,
            changePercentage = changePercent,
            totalRecordedEvents = currentHistory.size,
            takenCount = currentStats.takenCount,
            missedCount = currentStats.missedCount,
            skippedCount = currentStats.skippedCount,
            dailyPoints = dailyPoints
        )
    }

    private fun calculateAppointmentTrend(
        currentAppointments: List<AppointmentEntity>,
        previousAppointments: List<AppointmentEntity>,
        upcomingAppointments: List<AppointmentEntity>
    ): AppointmentActivityTrend {
        val currentCount = currentAppointments.size
        val prevCount = previousAppointments.size

        val direction = when {
            currentCount == 0 && prevCount == 0 -> TrendDirection.INSUFFICIENT_DATA
            currentCount > prevCount -> TrendDirection.INCREASING
            currentCount < prevCount -> TrendDirection.DECREASING
            else -> TrendDirection.STABLE
        }

        return AppointmentActivityTrend(
            currentPeriodCount = currentCount,
            previousPeriodCount = prevCount,
            upcomingCount = upcomingAppointments.size,
            direction = direction
        )
    }

    private fun detectTemporalPatterns(
        period: AnalysisPeriod,
        predictionTrend: PredictionActivityTrend,
        recurringSymptoms: List<RecurringSymptom>,
        confidenceTrend: ConfidenceTrend,
        adherenceTrend: AdherenceTrend,
        upcomingAppointments: List<AppointmentEntity>,
        activeMeds: List<MedicationEntity>
    ): List<TemporalPattern> {
        val patterns = mutableListOf<TemporalPattern>()

        // 1. Symptom Recurrence Patterns
        val topRecurring = recurringSymptoms.filter { it.isRecurring }
        for (symptom in topRecurring.take(2)) {
            patterns.add(
                TemporalPattern(
                    id = "recurring_symptom_${symptom.symptomName.lowercase().replace(" ", "_")}",
                    title = "Recurring Symptom: ${symptom.symptomName}",
                    description = "${symptom.symptomName} was recorded ${symptom.occurrenceCount} times in your prediction history over the past ${period.displayName}.",
                    category = PatternCategory.SYMPTOMS,
                    severity = PatternSeverity.ATTENTION,
                    firstObservedDate = symptom.firstObservedDate,
                    lastObservedDate = symptom.lastObservedDate,
                    observationCount = symptom.occurrenceCount
                )
            )
        }

        // 2. Prediction Activity Change Patterns
        if (predictionTrend.direction == TrendDirection.INCREASING && predictionTrend.currentPeriodCount >= 2) {
            patterns.add(
                TemporalPattern(
                    id = "prediction_activity_increased",
                    title = "Increased Prediction Logging",
                    description = "You recorded ${predictionTrend.currentPeriodCount} predictions in this period compared to ${predictionTrend.previousPeriodCount} in the previous period.",
                    category = PatternCategory.PREDICTIONS,
                    severity = PatternSeverity.INFO,
                    observationCount = predictionTrend.currentPeriodCount
                )
            )
        } else if (predictionTrend.direction == TrendDirection.DECREASING && predictionTrend.previousPeriodCount >= 2) {
            patterns.add(
                TemporalPattern(
                    id = "prediction_activity_decreased",
                    title = "Decreased Prediction Logging",
                    description = "Fewer prediction records logged this period (${predictionTrend.currentPeriodCount}) compared to the previous period (${predictionTrend.previousPeriodCount}).",
                    category = PatternCategory.PREDICTIONS,
                    severity = PatternSeverity.INFO,
                    observationCount = predictionTrend.currentPeriodCount
                )
            )
        }

        // 3. Prediction Confidence Model Output Pattern
        if (confidenceTrend.direction == TrendDirection.INCREASING && confidenceTrend.currentAvgConfidence != null) {
            val currPct = (confidenceTrend.currentAvgConfidence * 100).toInt()
            val prevPct = (confidenceTrend.previousAvgConfidence?.times(100))?.toInt() ?: 0
            patterns.add(
                TemporalPattern(
                    id = "confidence_trend_higher",
                    title = "Higher Model Confidence Outputs",
                    description = "The AI model average confidence was higher this period ($currPct%) compared to previous records ($prevPct%).",
                    category = PatternCategory.PREDICTIONS,
                    severity = PatternSeverity.INFO
                )
            )
        }

        // 4. Medication Adherence Patterns
        if (adherenceTrend.direction == TrendDirection.IMPROVING && adherenceTrend.currentAdherencePercentage != null) {
            val currPct = adherenceTrend.currentAdherencePercentage.toInt()
            patterns.add(
                TemporalPattern(
                    id = "adherence_improving",
                    title = "Improving Medication Adherence",
                    description = "Medication adherence reached $currPct% this period, improving from previous period records.",
                    category = PatternCategory.MEDICATION,
                    severity = PatternSeverity.POSITIVE,
                    observationCount = adherenceTrend.totalRecordedEvents
                )
            )
        } else if (adherenceTrend.direction == TrendDirection.DECLINING && adherenceTrend.currentAdherencePercentage != null) {
            val currPct = adherenceTrend.currentAdherencePercentage.toInt()
            patterns.add(
                TemporalPattern(
                    id = "adherence_declining",
                    title = "Declining Medication Adherence",
                    description = "Medication adherence decreased to $currPct% this period (${adherenceTrend.missedCount} missed doses recorded).",
                    category = PatternCategory.MEDICATION,
                    severity = PatternSeverity.ATTENTION,
                    observationCount = adherenceTrend.missedCount
                )
            )
        } else if (adherenceTrend.currentAdherencePercentage != null && adherenceTrend.currentAdherencePercentage >= HIGH_ADHERENCE_THRESHOLD_PERCENT) {
            val currPct = adherenceTrend.currentAdherencePercentage.toInt()
            patterns.add(
                TemporalPattern(
                    id = "adherence_high_consistency",
                    title = "Consistent Medication Adherence",
                    description = "Maintained a strong $currPct% medication adherence rate over the past ${period.displayName}.",
                    category = PatternCategory.MEDICATION,
                    severity = PatternSeverity.POSITIVE,
                    observationCount = adherenceTrend.takenCount
                )
            )
        }

        // 5. Active Care Routine Pattern
        if (activeMeds.isNotEmpty() && upcomingAppointments.isNotEmpty()) {
            patterns.add(
                TemporalPattern(
                    id = "active_care_routine",
                    title = "Active Healthcare Routine",
                    description = "You have ${activeMeds.size} active medication${if (activeMeds.size > 1) "s" else ""} tracked and ${upcomingAppointments.size} scheduled doctor appointment${if (upcomingAppointments.size > 1) "s" else ""}.",
                    category = PatternCategory.GENERAL,
                    severity = PatternSeverity.POSITIVE
                )
            )
        }

        return patterns
    }

    private fun generateDailyPredictionPoints(
        predictions: List<PredictionHistoryEntity>,
        periodStart: Long,
        now: Long,
        period: AnalysisPeriod
    ): List<DailyMetricPoint> {
        val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
        val points = mutableListOf<DailyMetricPoint>()
        val stepDays = when (period) {
            AnalysisPeriod.DAYS_7 -> 1
            AnalysisPeriod.DAYS_30 -> 3
            AnalysisPeriod.DAYS_90 -> 7
        }

        val stepMillis = stepDays * DAY_MILLIS
        var cursor = periodStart

        while (cursor <= now) {
            val nextCursor = cursor + stepMillis
            val count = predictions.count { it.predictionTimestamp in cursor until nextCursor }
            points.add(
                DailyMetricPoint(
                    dateMillis = cursor,
                    dateLabel = dateFormat.format(Date(cursor)),
                    value = count.toFloat()
                )
            )
            cursor = nextCursor
        }

        return points
    }

    private fun generateDailyConfidencePoints(
        predictions: List<PredictionHistoryEntity>,
        periodStart: Long,
        now: Long
    ): List<DailyMetricPoint> {
        val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
        return predictions.sortedBy { it.predictionTimestamp }.map {
            DailyMetricPoint(
                dateMillis = it.predictionTimestamp,
                dateLabel = dateFormat.format(Date(it.predictionTimestamp)),
                value = it.confidence * 100f
            )
        }
    }

    private fun generateDailyAdherencePoints(
        history: List<MedicationHistoryEntity>,
        periodStart: Long,
        now: Long
    ): List<DailyMetricPoint> {
        val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
        val grouped = history.groupBy { it.scheduledDate }

        return grouped.entries.sortedBy { it.key }.map { (dateMillis, list) ->
            val stats = MedicationDateTimeUtils.calculateAdherence(list)
            DailyMetricPoint(
                dateMillis = dateMillis,
                dateLabel = dateFormat.format(Date(dateMillis)),
                value = stats.percentage
            )
        }
    }

    private fun generateFactualLongitudinalSummary(
        period: AnalysisPeriod,
        predictionTrend: PredictionActivityTrend,
        recurringSymptoms: List<RecurringSymptom>,
        adherenceTrend: AdherenceTrend,
        appointmentTrend: AppointmentActivityTrend,
        hasSufficientData: Boolean
    ): String {
        if (!hasSufficientData) {
            return "Not enough historical data recorded in the last ${period.displayName} to determine longitudinal trends."
        }

        val parts = mutableListOf<String>()

        // 1. Prediction summary
        if (predictionTrend.currentPeriodCount > 0) {
            val trendText = when (predictionTrend.direction) {
                TrendDirection.INCREASING -> "increased from previous period"
                TrendDirection.DECREASING -> "decreased from previous period"
                else -> "remained steady"
            }
            parts.add("${predictionTrend.currentPeriodCount} prediction record${if (predictionTrend.currentPeriodCount > 1) "s" else ""} ($trendText)")
        }

        // 2. Symptom recurrence summary
        val recurring = recurringSymptoms.filter { it.isRecurring }
        if (recurring.isNotEmpty()) {
            val names = recurring.take(2).joinToString(" & ") { "${it.symptomName} (${it.occurrenceCount}x)" }
            parts.add("recurring symptoms: $names")
        }

        // 3. Adherence summary
        if (adherenceTrend.currentAdherencePercentage != null) {
            val adherencePct = adherenceTrend.currentAdherencePercentage.toInt()
            val adherenceText = when (adherenceTrend.direction) {
                TrendDirection.IMPROVING -> "improving"
                TrendDirection.DECLINING -> "declining"
                else -> "consistent"
            }
            parts.add("$adherencePct% medication adherence ($adherenceText)")
        }

        // 4. Appointment summary
        if (appointmentTrend.upcomingCount > 0) {
            parts.add("${appointmentTrend.upcomingCount} upcoming appointment scheduled")
        }

        return if (parts.isNotEmpty()) {
            "In the past ${period.displayName}, you had " + parts.joinToString(", ") + "."
        } else {
            "Historical activity in the past ${period.displayName} is steady."
        }
    }
}
