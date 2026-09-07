package com.medisense.app.domain.quality

import com.medisense.app.R
import com.medisense.app.data.local.entity.PredictionHistoryEntity
import com.medisense.app.domain.model.HealthDataQualityCategory
import com.medisense.app.domain.model.HealthDataQualityIssue
import com.medisense.app.domain.model.HealthDataQualitySeverity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Validates saved disease prediction history records for metadata completeness and numerical validity.
 */
@Singleton
class PredictionDataValidator @Inject constructor() {

    fun validate(predictions: List<PredictionHistoryEntity>): ValidationResult {
        val issues = mutableListOf<HealthDataQualityIssue>()
        var totalChecks = 0
        var passedChecks = 0

        for (pred in predictions) {
            // 1. Disease Label
            totalChecks++
            if (pred.predictedDisease.isBlank()) {
                issues.add(
                    HealthDataQualityIssue(
                        id = "pred_missing_label_${pred.id}",
                        category = HealthDataQualityCategory.PREDICTION_HISTORY,
                        severity = HealthDataQualitySeverity.ERROR,
                        title = "Missing Prediction Outcome",
                        explanation = "Prediction history entry (ID: ${pred.id}) is missing the outcome label.",
                        affectedRecordType = "PredictionHistory",
                        recordId = pred.id.toString(),
                        suggestedCorrection = "Entry has incomplete classification metadata.",
                        navigationDestinationId = R.id.predictionHistoryFragment
                    )
                )
            } else {
                passedChecks++
            }

            // 2. Numerical Confidence Range (0.0 .. 1.0)
            totalChecks++
            if (pred.confidence < 0.0f || pred.confidence > 1.0f || pred.confidence.isNaN()) {
                issues.add(
                    HealthDataQualityIssue(
                        id = "pred_invalid_conf_${pred.id}",
                        category = HealthDataQualityCategory.PREDICTION_HISTORY,
                        severity = HealthDataQualitySeverity.ERROR,
                        title = "Invalid Confidence Value",
                        explanation = "Prediction '${pred.predictedDisease}' has confidence ${pred.confidence}, outside valid 0.0–1.0 range.",
                        affectedRecordType = "PredictionHistory",
                        recordId = pred.id.toString(),
                        suggestedCorrection = "Entry contains an out-of-bounds probability metric.",
                        navigationDestinationId = R.id.predictionHistoryFragment
                    )
                )
            } else {
                passedChecks++
            }

            // 3. Symptoms List Non-Empty
            totalChecks++
            if (pred.symptoms.isEmpty()) {
                issues.add(
                    HealthDataQualityIssue(
                        id = "pred_empty_symptoms_${pred.id}",
                        category = HealthDataQualityCategory.PREDICTION_HISTORY,
                        severity = HealthDataQualitySeverity.ERROR,
                        title = "Missing Input Symptoms",
                        explanation = "Prediction '${pred.predictedDisease}' contains an empty symptoms list.",
                        affectedRecordType = "PredictionHistory",
                        recordId = pred.id.toString(),
                        suggestedCorrection = "Clinical input symptoms were not captured for this analysis entry.",
                        navigationDestinationId = R.id.predictionHistoryFragment
                    )
                )
            } else {
                passedChecks++
            }

            // 4. Prediction Timestamp Sanity
            totalChecks++
            if (pred.predictionTimestamp <= 0L) {
                issues.add(
                    HealthDataQualityIssue(
                        id = "pred_invalid_time_${pred.id}",
                        category = HealthDataQualityCategory.PREDICTION_HISTORY,
                        severity = HealthDataQualitySeverity.ERROR,
                        title = "Invalid Prediction Timestamp",
                        explanation = "Prediction '${pred.predictedDisease}' has an invalid timestamp value.",
                        affectedRecordType = "PredictionHistory",
                        recordId = pred.id.toString(),
                        suggestedCorrection = "Timestamp is required for longitudinal trend mapping.",
                        navigationDestinationId = R.id.predictionHistoryFragment
                    )
                )
            } else {
                passedChecks++
            }

            // 5. Model Version Identifier
            totalChecks++
            if (pred.modelVersion.isBlank()) {
                issues.add(
                    HealthDataQualityIssue(
                        id = "pred_missing_version_${pred.id}",
                        category = HealthDataQualityCategory.PREDICTION_HISTORY,
                        severity = HealthDataQualitySeverity.INFO,
                        title = "Missing Model Version",
                        explanation = "Prediction '${pred.predictedDisease}' does not record the model engine version.",
                        affectedRecordType = "PredictionHistory",
                        recordId = pred.id.toString(),
                        suggestedCorrection = "Model version metadata is recommended for historical reproducibility.",
                        navigationDestinationId = R.id.predictionHistoryFragment
                    )
                )
            } else {
                passedChecks++
            }
        }

        return ValidationResult(totalChecks = totalChecks, passedChecks = passedChecks, issues = issues)
    }
}
