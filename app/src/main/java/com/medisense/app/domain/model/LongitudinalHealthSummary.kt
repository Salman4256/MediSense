package com.medisense.app.domain.model

data class DailyMetricPoint(
    val dateMillis: Long,
    val dateLabel: String,
    val value: Float
)

data class RecurringSymptom(
    val symptomName: String,
    val occurrenceCount: Int,
    val firstObservedDate: Long,
    val lastObservedDate: Long,
    val isRecurring: Boolean
)

data class PredictionActivityTrend(
    val currentPeriodCount: Int,
    val previousPeriodCount: Int,
    val direction: TrendDirection,
    val changePercentage: Float?,
    val topConditions: List<String>,
    val dailyPoints: List<DailyMetricPoint>
)

data class ConfidenceTrend(
    val currentAvgConfidence: Float?,
    val previousAvgConfidence: Float?,
    val direction: TrendDirection,
    val changePercentage: Float?,
    val dailyPoints: List<DailyMetricPoint>
)

data class AdherenceTrend(
    val currentAdherencePercentage: Float?,
    val previousAdherencePercentage: Float?,
    val direction: TrendDirection,
    val changePercentage: Float?,
    val totalRecordedEvents: Int,
    val takenCount: Int,
    val missedCount: Int,
    val skippedCount: Int,
    val dailyPoints: List<DailyMetricPoint>
)

data class AppointmentActivityTrend(
    val currentPeriodCount: Int,
    val previousPeriodCount: Int,
    val upcomingCount: Int,
    val direction: TrendDirection
)

/**
 * Unified longitudinal health analytics model covering predictions, symptoms, adherence, and appointments.
 */
data class LongitudinalHealthSummary(
    val userId: String,
    val period: AnalysisPeriod,
    val predictionActivity: PredictionActivityTrend,
    val recurringSymptoms: List<RecurringSymptom>,
    val confidenceTrend: ConfidenceTrend,
    val adherenceTrend: AdherenceTrend,
    val appointmentActivity: AppointmentActivityTrend,
    val detectedPatterns: List<TemporalPattern>,
    val generatedSummary: String,
    val hasSufficientData: Boolean
)
