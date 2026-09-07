package com.medisense.app.domain.engine

import com.medisense.app.data.local.entity.*
import com.medisense.app.domain.model.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Deterministic engine for aggregating, normalizing, explaining, and filtering longitudinal health events.
 * Derives purely from persisted local Room entities and existing module domain models.
 */
object HealthTimelineEngine {

    private const val GROUPING_WINDOW_MS = 5 * 60 * 1000L // 5 minutes grouping threshold

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).apply {
        timeZone = TimeZone.getDefault()
    }
    private val dateOnlyFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
        timeZone = TimeZone.getDefault()
    }

    /**
     * Aggregates all raw health records into a normalized, chronological, explainable health journey.
     */
    fun buildTimeline(
        userId: String,
        profile: HealthProfileEntity? = null,
        predictions: List<PredictionHistoryEntity> = emptyList(),
        medications: List<MedicationEntity> = emptyList(),
        medicationHistory: List<MedicationHistoryEntity> = emptyList(),
        appointments: List<AppointmentEntity> = emptyList(),
        auditEvents: List<SecurityAuditEventEntity> = emptyList(),
        longitudinalSummary: LongitudinalHealthSummary? = null,
        personalContext: PersonalHealthContext? = null,
        riskAssessment: ContextualRiskAssessment? = null,
        guidanceResult: GuidanceEngineResult? = null,
        dataQualitySummary: HealthDataQualitySummary? = null,
        filter: HealthTimelineFilter = HealthTimelineFilter(),
        currentTime: Long = System.currentTimeMillis()
    ): Pair<List<HealthTimelineEvent>, HealthTimelineSummary> {

        if (userId.isBlank() || userId == "offline-user-empty") {
            return Pair(
                emptyList(),
                HealthTimelineSummary(
                    totalEventsCount = 0,
                    recentPredictionsCount = 0,
                    activeMedicationsCount = 0,
                    upcomingAppointmentsCount = 0,
                    detectedPatternsCount = 0,
                    dataQualityStatus = HealthDataQualityStatus.INSUFFICIENT_DATA,
                    latestEventTimestamp = null
                )
            )
        }

        val allEvents = mutableListOf<HealthTimelineEvent>()

        // 1. Health Profile Events (User-isolated)
        if (profile != null && profile.userId == userId) {
            val profileTimestamp = parseTimestamp(profile.updatedAt ?: profile.createdAt) ?: currentTime
            allEvents.add(
                HealthTimelineEvent(
                    eventId = "profile_${profile.id}",
                    userId = userId,
                    timestamp = profileTimestamp,
                    eventType = HealthTimelineEventType.PROFILE_UPDATED,
                    source = HealthTimelineSource.PROFILE,
                    title = "Health Profile Updated",
                    shortDescription = "Baseline demographic and clinical profile information recorded.",
                    detailedDescription = buildString {
                        append("Profile updated with details: ")
                        val details = mutableListOf<String>()
                        profile.gender?.let { details.add("Gender: $it") }
                        profile.bloodGroup?.let { details.add("Blood Group: $it") }
                        if (profile.height != null && profile.height > 0) details.add("Height: ${profile.height} cm")
                        if (profile.weight != null && profile.weight > 0) details.add("Weight: ${profile.weight} kg")
                        if (!profile.allergies.isNullOrBlank()) details.add("Allergies recorded")
                        if (!profile.existingDiseases.isNullOrBlank()) details.add("Existing conditions recorded")
                        if (details.isEmpty()) append("Basic demographic profile details.") else append(details.joinToString(", "))
                    },
                    priority = HealthTimelinePriority.NORMAL,
                    relatedEntityId = profile.id,
                    metadata = mapOf(
                        "Gender" to (profile.gender ?: "Not specified"),
                        "Blood Group" to (profile.bloodGroup ?: "Not specified"),
                        "Allergies" to (profile.allergies ?: "None"),
                        "Existing Conditions" to (profile.existingDiseases ?: "None")
                    ),
                    isDerived = false,
                    explanation = HealthTimelineExplanation(
                        whatHappened = "Health profile details were recorded or updated.",
                        whenOccurred = formatTimestamp(profileTimestamp),
                        whyShown = "Health profile information establishes your baseline personal health context.",
                        sourceAttribution = HealthTimelineSource.PROFILE.displayName
                    ),
                    navigationTarget = TimelineNavigationTarget.PROFILE
                )
            )
        }

        // 2. Disease Prediction Events (User-isolated, Non-Diagnostic wording)
        predictions.filter { it.userId == userId }.forEach { prediction ->
            val confidencePct = (prediction.confidence * 100).toInt()
            val symptomList = prediction.symptoms.filter { it.isNotBlank() }
            val symptomsStr = if (symptomList.isNotEmpty()) symptomList.joinToString(", ") else "None recorded"
            val explanationStr = prediction.explanationSummary ?: "Model generated probabilistic assessment based on recorded symptoms."

            allEvents.add(
                HealthTimelineEvent(
                    eventId = "prediction_${prediction.id}",
                    userId = userId,
                    timestamp = prediction.predictionTimestamp,
                    eventType = HealthTimelineEventType.PREDICTION,
                    source = HealthTimelineSource.DISEASE_PREDICTION,
                    title = "Prediction: ${prediction.predictedDisease}",
                    shortDescription = "Model output: ${prediction.predictedDisease} ($confidencePct% confidence) from ${symptomList.size} symptom(s).",
                    detailedDescription = "Selected symptoms: $symptomsStr. The AI prediction model estimated a correlation with ${prediction.predictedDisease} with $confidencePct% model confidence (Version: ${prediction.modelVersion}). This is an educational prediction, not a medical diagnosis.",
                    priority = if (confidencePct >= 80) HealthTimelinePriority.IMPORTANT else HealthTimelinePriority.NORMAL,
                    relatedEntityId = prediction.id.toString(),
                    metadata = mapOf(
                        "Predicted Condition" to prediction.predictedDisease,
                        "Model Confidence" to "$confidencePct%",
                        "Symptoms Count" to symptomList.size.toString(),
                        "Model Version" to prediction.modelVersion,
                        "Explanation" to explanationStr
                    ),
                    isDerived = false,
                    explanation = HealthTimelineExplanation(
                        whatHappened = "Disease prediction was performed using reported symptoms.",
                        whenOccurred = formatTimestamp(prediction.predictionTimestamp),
                        whyShown = "Predictions track your historical symptom patterns and AI model outputs over time.",
                        sourceAttribution = HealthTimelineSource.DISEASE_PREDICTION.displayName
                    ),
                    navigationTarget = TimelineNavigationTarget.PREDICTION_DETAIL
                )
            )
        }

        // 3. Medication Regimen Creation / Start Events (User-isolated)
        medications.filter { it.userId == userId }.forEach { med ->
            val medTimestamp = if (med.startDate > 0) med.startDate else med.createdAt
            val scheduleStr = if (med.scheduledTimes.isNotEmpty()) med.scheduledTimes.joinToString(", ") else med.frequency
            allEvents.add(
                HealthTimelineEvent(
                    eventId = "med_start_${med.id}",
                    userId = userId,
                    timestamp = medTimestamp,
                    eventType = HealthTimelineEventType.MEDICATION_STARTED,
                    source = HealthTimelineSource.MEDICATION_MANAGEMENT,
                    title = "Medication Added: ${med.medicineName}",
                    shortDescription = "Dosage: ${med.dosage} ${med.dosageUnit} • ${med.frequency} ($scheduleStr)",
                    detailedDescription = buildString {
                        append("Prescription or regimen recorded for ${med.medicineName} (${med.dosage} ${med.dosageUnit}). ")
                        append("Scheduled frequency: ${med.frequency} at $scheduleStr. ")
                        if (med.instructions.isNotBlank()) append("Instructions: ${med.instructions}. ")
                        if (med.endDate != null && med.endDate > 0) append("End date: ${formatDateOnly(med.endDate)}. ")
                    },
                    priority = HealthTimelinePriority.NORMAL,
                    relatedEntityId = med.id.toString(),
                    metadata = mapOf(
                        "Medicine Name" to med.medicineName,
                        "Dosage" to "${med.dosage} ${med.dosageUnit}",
                        "Frequency" to med.frequency,
                        "Active" to if (med.active) "Yes" else "No",
                        "Instructions" to med.instructions.ifBlank { "None" }
                    ),
                    isDerived = false,
                    explanation = HealthTimelineExplanation(
                        whatHappened = "A medication schedule was recorded in your health manager.",
                        whenOccurred = formatTimestamp(medTimestamp),
                        whyShown = "Medication records help you track active regimens and verify adherence.",
                        sourceAttribution = HealthTimelineSource.MEDICATION_MANAGEMENT.displayName
                    ),
                    navigationTarget = TimelineNavigationTarget.MEDICATION
                )
            )
        }

        // 4. Medication History / Adherence Action Events (User-isolated)
        val groupedHistory = groupMedicationHistory(medicationHistory.filter { it.userId == userId })
        groupedHistory.forEach { hist ->
            val actionTimestamp = hist.actionTime ?: hist.scheduledDate
            val (eventType, title, priority) = when (hist.status.uppercase()) {
                "TAKEN" -> Triple(HealthTimelineEventType.MEDICATION_TAKEN, "Medication Taken: ${hist.medicineName}", HealthTimelinePriority.NORMAL)
                "SKIPPED" -> Triple(HealthTimelineEventType.MEDICATION_SKIPPED, "Medication Skipped: ${hist.medicineName}", HealthTimelinePriority.IMPORTANT)
                "MISSED" -> Triple(HealthTimelineEventType.MEDICATION_MISSED, "Medication Missed: ${hist.medicineName}", HealthTimelinePriority.ATTENTION)
                "SNOOZED" -> Triple(HealthTimelineEventType.MEDICATION_SNOOZED, "Medication Snoozed: ${hist.medicineName}", HealthTimelinePriority.NORMAL)
                else -> Triple(HealthTimelineEventType.MEDICATION_TAKEN, "Medication ${hist.status}: ${hist.medicineName}", HealthTimelinePriority.NORMAL)
            }

            allEvents.add(
                HealthTimelineEvent(
                    eventId = "med_hist_${hist.id}",
                    userId = userId,
                    timestamp = actionTimestamp,
                    eventType = eventType,
                    source = HealthTimelineSource.MEDICATION_MANAGEMENT,
                    title = title,
                    shortDescription = "${hist.dosage} scheduled for ${hist.scheduledTime} was marked as ${hist.status.lowercase()}.",
                    detailedDescription = "Medication adherence entry: ${hist.medicineName} (${hist.dosage}) was recorded as ${hist.status.uppercase()} on ${formatDateOnly(hist.scheduledDate)} at ${hist.scheduledTime}.",
                    priority = priority,
                    relatedEntityId = hist.medicationId.toString(),
                    metadata = mapOf(
                        "Medicine Name" to hist.medicineName,
                        "Dosage" to hist.dosage,
                        "Status" to hist.status,
                        "Scheduled Time" to hist.scheduledTime,
                        "Scheduled Date" to formatDateOnly(hist.scheduledDate)
                    ),
                    isDerived = false,
                    explanation = HealthTimelineExplanation(
                        whatHappened = "Medication adherence action was logged for scheduled dose.",
                        whenOccurred = formatTimestamp(actionTimestamp),
                        whyShown = "Medication adherence logs document your routine health management over time.",
                        sourceAttribution = HealthTimelineSource.MEDICATION_MANAGEMENT.displayName
                    ),
                    navigationTarget = TimelineNavigationTarget.MEDICATION_HISTORY
                )
            )
        }

        // 5. Appointment Events (User-isolated)
        appointments.filter { it.userId == userId }.forEach { appt ->
            val (eventType, titlePrefix, priority) = when (appt.status.uppercase()) {
                "COMPLETED" -> Triple(HealthTimelineEventType.APPOINTMENT_COMPLETED, "Appointment Completed", HealthTimelinePriority.NORMAL)
                "CANCELLED" -> Triple(HealthTimelineEventType.APPOINTMENT_CANCELLED, "Appointment Cancelled", HealthTimelinePriority.IMPORTANT)
                else -> Triple(HealthTimelineEventType.APPOINTMENT_SCHEDULED, "Appointment Scheduled", if (appt.appointmentTimestamp > currentTime) HealthTimelinePriority.IMPORTANT else HealthTimelinePriority.NORMAL)
            }

            allEvents.add(
                HealthTimelineEvent(
                    eventId = "appt_${appt.id}",
                    userId = userId,
                    timestamp = appt.appointmentTimestamp,
                    eventType = eventType,
                    source = HealthTimelineSource.APPOINTMENTS,
                    title = "$titlePrefix: ${appt.doctorName}",
                    shortDescription = "${appt.appointmentType} at ${appt.clinicName} • ${appt.appointmentDate} ${appt.appointmentTime}",
                    detailedDescription = buildString {
                        append("Doctor appointment with ${appt.doctorName} (${appt.appointmentType}) at ${appt.clinicName}. ")
                        append("Scheduled for ${appt.appointmentDate} at ${appt.appointmentTime}. ")
                        append("Status: ${appt.status}. ")
                        if (!appt.notes.isNullOrBlank()) append("Notes: ${appt.notes}. ")
                    },
                    priority = priority,
                    relatedEntityId = appt.id.toString(),
                    metadata = mapOf(
                        "Doctor" to appt.doctorName,
                        "Clinic" to appt.clinicName,
                        "Type" to appt.appointmentType,
                        "Date" to appt.appointmentDate,
                        "Time" to appt.appointmentTime,
                        "Status" to appt.status
                    ),
                    isDerived = false,
                    explanation = HealthTimelineExplanation(
                        whatHappened = "Doctor appointment was scheduled, updated, or completed.",
                        whenOccurred = formatTimestamp(appt.appointmentTimestamp),
                        whyShown = "Clinical visits and consultations are essential milestones in your health timeline.",
                        sourceAttribution = HealthTimelineSource.APPOINTMENTS.displayName
                    ),
                    navigationTarget = TimelineNavigationTarget.APPOINTMENT
                )
            )
        }

        // 6. Longitudinal Health Trends & Temporal Patterns (Derived from Module 9B)
        if (longitudinalSummary != null && longitudinalSummary.userId == userId && longitudinalSummary.detectedPatterns.isNotEmpty()) {
            longitudinalSummary.detectedPatterns.forEachIndexed { index, pattern ->
                val patternTimestamp = currentTime - (index * 60 * 1000L) // deterministic slight offset to prevent collision
                allEvents.add(
                    HealthTimelineEvent(
                        eventId = "trend_pattern_${longitudinalSummary.period.name}_$index",
                        userId = userId,
                        timestamp = patternTimestamp,
                        eventType = HealthTimelineEventType.TEMPORAL_PATTERN,
                        source = HealthTimelineSource.HEALTH_TRENDS,
                        title = "Temporal Pattern: ${pattern.title}",
                        shortDescription = pattern.description,
                        detailedDescription = "Longitudinal analysis over the ${longitudinalSummary.period.displayName} window identified: ${pattern.description} (Category: ${pattern.category.name}, Severity: ${pattern.severity.name}, Observations: ${pattern.observationCount}).",
                        priority = if (pattern.severity == PatternSeverity.ATTENTION) HealthTimelinePriority.IMPORTANT else HealthTimelinePriority.NORMAL,
                        relatedEntityId = pattern.id,
                        metadata = mapOf(
                            "Pattern Category" to pattern.category.name,
                            "Severity" to pattern.severity.name,
                            "Observations" to pattern.observationCount.toString(),
                            "Analysis Window" to longitudinalSummary.period.displayName
                        ),
                        isDerived = true,
                        explanation = HealthTimelineExplanation(
                            whatHappened = "System detected a recurring temporal trend across your historical data.",
                            whenOccurred = formatTimestamp(patternTimestamp),
                            whyShown = "Longitudinal trends help highlight patterns across symptoms, medications, and visits.",
                            sourceAttribution = HealthTimelineSource.HEALTH_TRENDS.displayName
                        ),
                        navigationTarget = TimelineNavigationTarget.HEALTH_TRENDS
                    )
                )
            }
        }

        // 7. Contextual Risk Assessment (Derived from Module 11)
        if (riskAssessment != null && riskAssessment.userId == userId && riskAssessment.hasSufficientData) {
            allEvents.add(
                HealthTimelineEvent(
                    eventId = "risk_assessment_${riskAssessment.calculatedAt}",
                    userId = userId,
                    timestamp = riskAssessment.calculatedAt,
                    eventType = HealthTimelineEventType.RISK_ASSESSMENT,
                    source = HealthTimelineSource.CONTEXTUAL_RISK,
                    title = "Context-Aware Health Priority: ${riskAssessment.riskLevel.label}",
                    shortDescription = riskAssessment.generatedSummary,
                    detailedDescription = buildString {
                        append("Contextual health priority level evaluated as ${riskAssessment.riskLevel.label} (Score: ${riskAssessment.overallScore ?: 0}/100). ")
                        append(riskAssessment.generatedSummary)
                        if (riskAssessment.contributingFactors.isNotEmpty()) {
                            append(" Contributing factors: ")
                            append(riskAssessment.contributingFactors.joinToString("; ") { it.description })
                        }
                    },
                    priority = if (riskAssessment.riskLevel == ContextualRiskLevel.HIGH) HealthTimelinePriority.ATTENTION else HealthTimelinePriority.NORMAL,
                    relatedEntityId = null,
                    metadata = mapOf(
                        "Risk Level" to riskAssessment.riskLevel.label,
                        "Composite Score" to (riskAssessment.overallScore?.toString() ?: "N/A"),
                        "Factors Count" to riskAssessment.contributingFactors.size.toString()
                    ),
                    isDerived = true,
                    explanation = HealthTimelineExplanation(
                        whatHappened = "Contextual health risk priority was evaluated from your multi-module health profile.",
                        whenOccurred = formatTimestamp(riskAssessment.calculatedAt),
                        whyShown = "Contextual priority highlights areas needing personal health-management attention.",
                        sourceAttribution = HealthTimelineSource.CONTEXTUAL_RISK.displayName
                    ),
                    navigationTarget = TimelineNavigationTarget.CONTEXTUAL_RISK
                )
            )
        }

        // 8. Personalized Guidance Recommendations (Derived from Module 12)
        if (guidanceResult != null && guidanceResult.userId == userId && guidanceResult.guidanceList.isNotEmpty()) {
            val topGuidance = guidanceResult.guidanceList.firstOrNull()
            if (topGuidance != null) {
                allEvents.add(
                    HealthTimelineEvent(
                        eventId = "guidance_${topGuidance.id}",
                        userId = userId,
                        timestamp = guidanceResult.generatedTimestamp,
                        eventType = HealthTimelineEventType.PERSONALIZED_GUIDANCE,
                        source = HealthTimelineSource.PERSONALIZED_GUIDANCE,
                        title = "Health Guidance: ${topGuidance.title}",
                        shortDescription = topGuidance.message,
                        detailedDescription = "${topGuidance.message} Rationale: ${topGuidance.explanation} (Priority: ${topGuidance.priority.label}, Category: ${topGuidance.category.displayName}).",
                        priority = if (topGuidance.priority == GuidancePriority.HIGH) HealthTimelinePriority.IMPORTANT else HealthTimelinePriority.NORMAL,
                        relatedEntityId = topGuidance.id,
                        metadata = mapOf(
                            "Category" to topGuidance.category.displayName,
                            "Priority" to topGuidance.priority.label,
                            "Action Type" to topGuidance.actionType.name
                        ),
                        isDerived = true,
                        explanation = HealthTimelineExplanation(
                            whatHappened = "Personalized adaptive health recommendations were generated.",
                            whenOccurred = formatTimestamp(guidanceResult.generatedTimestamp),
                            whyShown = "Actionable tips help guide healthy lifestyle habits and routine health maintenance.",
                            sourceAttribution = HealthTimelineSource.PERSONALIZED_GUIDANCE.displayName
                        ),
                        navigationTarget = TimelineNavigationTarget.PERSONALIZED_GUIDANCE
                    )
                )
            }
        }

        // 9. Data Quality Issues (Derived from Module 16)
        if (dataQualitySummary != null && dataQualitySummary.issues.isNotEmpty()) {
            val highSeverityIssues = dataQualitySummary.issues.filter { it.severity == HealthDataQualitySeverity.ERROR || it.severity == HealthDataQualitySeverity.WARNING }
            val topIssue = highSeverityIssues.firstOrNull() ?: dataQualitySummary.issues.firstOrNull()
            if (topIssue != null) {
                allEvents.add(
                    HealthTimelineEvent(
                        eventId = "data_quality_${topIssue.id}",
                        userId = userId,
                        timestamp = dataQualitySummary.evaluatedTimestamp,
                        eventType = HealthTimelineEventType.DATA_QUALITY_ISSUE,
                        source = HealthTimelineSource.DATA_QUALITY,
                        title = "Data Quality Notice: ${topIssue.title}",
                        shortDescription = topIssue.explanation,
                        detailedDescription = "${topIssue.explanation} Suggested resolution: ${topIssue.suggestedCorrection}",
                        priority = HealthTimelinePriority.IMPORTANT,
                        relatedEntityId = topIssue.id,
                        metadata = mapOf(
                            "Category" to topIssue.category.name,
                            "Severity" to topIssue.severity.name,
                            "Unresolved Issues" to dataQualitySummary.issues.size.toString()
                        ),
                        isDerived = true,
                        explanation = HealthTimelineExplanation(
                            whatHappened = "Health data validation detected an incomplete or inconsistent entry.",
                            whenOccurred = formatTimestamp(dataQualitySummary.evaluatedTimestamp),
                            whyShown = "Ensuring high data quality improves the accuracy of timelines and health reports.",
                            sourceAttribution = HealthTimelineSource.DATA_QUALITY.displayName
                        ),
                        navigationTarget = TimelineNavigationTarget.HEALTH_DATA_QUALITY
                    )
                )
            }
        }

        // 10. Audit / Sync / Report Generation Events (User-isolated from Module 14 telemetry)
        auditEvents.filter { it.userId == userId }.forEach { audit ->
            when (audit.eventType) {
                SecurityAuditEventType.HEALTH_REPORT_GENERATED.name -> {
                    allEvents.add(
                        HealthTimelineEvent(
                            eventId = "audit_report_${audit.id}",
                            userId = userId,
                            timestamp = audit.timestamp,
                            eventType = HealthTimelineEventType.HEALTH_REPORT_GENERATED,
                            source = HealthTimelineSource.HEALTH_REPORT,
                            title = "Health Report Generated",
                            shortDescription = audit.description,
                            detailedDescription = "A comprehensive personal health report summary was compiled from your local health records.",
                            priority = HealthTimelinePriority.NORMAL,
                            relatedEntityId = audit.id.toString(),
                            metadata = mapOf("Event" to audit.eventType),
                            isDerived = true,
                            explanation = HealthTimelineExplanation(
                                whatHappened = "A consolidated health report was generated.",
                                whenOccurred = formatTimestamp(audit.timestamp),
                                whyShown = "Health reports provide shareable clinical summaries for your doctor visits.",
                                sourceAttribution = HealthTimelineSource.HEALTH_REPORT.displayName
                            ),
                            navigationTarget = TimelineNavigationTarget.HEALTH_REPORT
                        )
                    )
                }
                SecurityAuditEventType.CLOUD_SYNC.name -> {
                    allEvents.add(
                        HealthTimelineEvent(
                            eventId = "audit_sync_${audit.id}",
                            userId = userId,
                            timestamp = audit.timestamp,
                            eventType = HealthTimelineEventType.SYNC_EVENT,
                            source = HealthTimelineSource.SYNCHRONIZATION,
                            title = "Encrypted Cloud Sync Completed",
                            shortDescription = audit.description,
                            detailedDescription = "Your local Room health records were securely synchronized with your PostgreSQL database.",
                            priority = HealthTimelinePriority.NORMAL,
                            relatedEntityId = audit.id.toString(),
                            metadata = mapOf("Event" to audit.eventType),
                            isDerived = true,
                            explanation = HealthTimelineExplanation(
                                whatHappened = "Local health records were synced with your authenticated cloud backup.",
                                whenOccurred = formatTimestamp(audit.timestamp),
                                whyShown = "Keeps your records safely preserved across app reinstalls.",
                                sourceAttribution = HealthTimelineSource.SYNCHRONIZATION.displayName
                            ),
                            navigationTarget = TimelineNavigationTarget.NONE
                        )
                    )
                }
            }
        }

        // Deduplicate events by stable eventId
        val deduplicatedEvents = allEvents.distinctBy { it.eventId }

        // Filter events by Time Period
        val periodCutoff = if (filter.period.dayCount != null) {
            currentTime - (filter.period.dayCount.toLong() * 24 * 60 * 60 * 1000L)
        } else {
            0L
        }
        val periodFiltered = deduplicatedEvents.filter { it.timestamp >= periodCutoff }

        // Filter events by Category
        val categoryFiltered = if (filter.category == HealthTimelineCategoryFilter.ALL) {
            periodFiltered
        } else {
            periodFiltered.filter { it.eventType.category == filter.category }
        }

        // Filter events by Search Query if provided
        val searchFiltered = if (filter.searchQuery.isBlank()) {
            categoryFiltered
        } else {
            val q = filter.searchQuery.trim().lowercase()
            categoryFiltered.filter {
                it.title.lowercase().contains(q) ||
                it.shortDescription.lowercase().contains(q) ||
                it.detailedDescription.lowercase().contains(q) ||
                it.source.displayName.lowercase().contains(q)
            }
        }

        // Deterministic Chronological Sorting
        // Sort Key: timestamp (primary) -> eventType.ordinal (secondary) -> eventId (tertiary)
        val sortedEvents = if (filter.sortOrder == HealthTimelineSortOrder.NEWEST_FIRST) {
            searchFiltered.sortedWith(
                compareByDescending<HealthTimelineEvent> { it.timestamp }
                    .thenBy { it.eventType.ordinal }
                    .thenBy { it.eventId }
            )
        } else {
            searchFiltered.sortedWith(
                compareBy<HealthTimelineEvent> { it.timestamp }
                    .thenBy { it.eventType.ordinal }
                    .thenBy { it.eventId }
            )
        }

        // Build Deterministic Summary
        val recentPredictionCutoff = currentTime - (30L * 24 * 60 * 60 * 1000L)
        val recentPredCount = predictions.count { it.userId == userId && it.predictionTimestamp >= recentPredictionCutoff }
        val activeMedCount = medications.count { it.userId == userId && it.active }
        val upcomingApptCount = appointments.count { it.userId == userId && it.status.equals("SCHEDULED", ignoreCase = true) && it.appointmentTimestamp >= currentTime }
        val patternsCount = longitudinalSummary?.detectedPatterns?.size ?: 0
        val dqStatus = dataQualitySummary?.status ?: HealthDataQualityStatus.GOOD
        val latestTimestamp = deduplicatedEvents.maxOfOrNull { it.timestamp }

        val summary = HealthTimelineSummary(
            totalEventsCount = deduplicatedEvents.size,
            recentPredictionsCount = recentPredCount,
            activeMedicationsCount = activeMedCount,
            upcomingAppointmentsCount = upcomingApptCount,
            detectedPatternsCount = patternsCount,
            dataQualityStatus = dqStatus,
            latestEventTimestamp = latestTimestamp
        )

        return Pair(sortedEvents, summary)
    }

    /**
     * Groups redundant close-in-time medication actions.
     */
    private fun groupMedicationHistory(history: List<MedicationHistoryEntity>): List<MedicationHistoryEntity> {
        if (history.size <= 1) return history

        val sorted = history.sortedWith(
            compareByDescending<MedicationHistoryEntity> { it.actionTime ?: it.scheduledDate }
                .thenBy { it.medicationId }
                .thenBy { it.id }
        )

        val result = mutableListOf<MedicationHistoryEntity>()
        var lastItem: MedicationHistoryEntity? = null

        for (item in sorted) {
            if (lastItem == null) {
                result.add(item)
                lastItem = item
            } else {
                val lastTime = lastItem.actionTime ?: lastItem.scheduledDate
                val currTime = item.actionTime ?: item.scheduledDate
                val isSameMed = lastItem.medicationId == item.medicationId
                val isSameStatus = lastItem.status.equals(item.status, ignoreCase = true)
                val isWithinThreshold = kotlin.math.abs(lastTime - currTime) <= GROUPING_WINDOW_MS

                if (!(isSameMed && isSameStatus && isWithinThreshold)) {
                    result.add(item)
                    lastItem = item
                }
            }
        }
        return result
    }

    private fun formatTimestamp(timestamp: Long): String {
        return try {
            dateFormatter.format(Date(timestamp))
        } catch (e: Exception) {
            "Timestamp: $timestamp"
        }
    }

    private fun formatDateOnly(timestamp: Long): String {
        return try {
            dateOnlyFormatter.format(Date(timestamp))
        } catch (e: Exception) {
            "Date: $timestamp"
        }
    }

    private fun parseTimestamp(isoString: String?): Long? {
        if (isoString.isNullOrBlank()) return null
        return try {
            isoString.toLongOrNull() ?: run {
                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                isoFormat.parse(isoString)?.time
            }
        } catch (e: Exception) {
            null
        }
    }
}
