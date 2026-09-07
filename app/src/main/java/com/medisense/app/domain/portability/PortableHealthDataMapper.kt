package com.medisense.app.domain.portability

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.medisense.app.data.local.entity.AppointmentEntity
import com.medisense.app.data.local.entity.HealthProfileEntity
import com.medisense.app.data.local.entity.MedicationEntity
import com.medisense.app.data.local.entity.MedicationHistoryEntity
import com.medisense.app.data.local.entity.PredictionHistoryEntity
import com.medisense.app.domain.model.*
import com.medisense.app.domain.rchr.RchrRepresentation
import com.medisense.app.utils.AdherenceStats
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

/**
 * Deterministic, 100% offline-first mapper transforming MediSense domain data into
 * a standardized, machine-readable [PortableHealthRecordBundle].
 */
object PortableHealthDataMapper {

    private val ISO_DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val DISPLAY_DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    fun mapToBundle(
        userId: String,
        scope: PortableExportScope,
        profile: HealthProfileEntity?,
        medications: List<MedicationEntity>,
        medicationHistory: List<MedicationHistoryEntity>,
        adherenceStats: AdherenceStats?,
        appointments: List<AppointmentEntity>,
        predictions: List<PredictionHistoryEntity>,
        longitudinal: LongitudinalHealthSummary?,
        personalContext: PersonalHealthContext?,
        riskAssessment: ContextualRiskAssessment?,
        guidance: GuidanceEngineResult?,
        rchr: RchrRepresentation?,
        decisionTraces: List<HealthDecisionTrace>,
        timelineEvents: List<HealthTimelineEvent>,
        emergencyCard: EmergencyHealthCard?,
        qualitySummary: HealthDataQualitySummary?
    ): PortableHealthRecordBundle {
        val now = System.currentTimeMillis()
        val nowIso = ISO_DATE_FORMAT.format(Date(now))
        val packageId = "MS-PORTABLE-${UUID.randomUUID().toString().take(8).uppercase()}"

        val entries = mutableListOf<PortableBundleEntry>()
        val selected = scope.selectedCategories

        // 1. BASIC_PROFILE (Patient & Vitals Observations)
        if (selected.contains(PortableDataCategory.BASIC_PROFILE) && profile != null) {
            val patientResource = PortablePatientResource(
                resourceId = "pat-01",
                name = sanitizeText(profile.fullName).ifBlank { null },
                birthDate = sanitizeText(profile.dateOfBirth).ifBlank { null },
                age = calculateAge(profile.dateOfBirth),
                gender = sanitizeText(profile.gender).ifBlank { null },
                bloodGroup = sanitizeText(profile.bloodGroup).ifBlank { null },
                emergencyContact = if (!profile.emergencyContactName.isNullOrBlank() || !profile.emergencyContactNumber.isNullOrBlank()) {
                    PortableEmergencyContact(
                        name = sanitizeText(profile.emergencyContactName).ifBlank { null },
                        telecom = sanitizeText(profile.emergencyContactNumber).ifBlank { null },
                        relationship = "Emergency Contact"
                    )
                } else null
            )
            entries.add(PortableBundleEntry(fullUrl = "urn:medisense:Patient/pat-01", resource = patientResource))

            // Observations (Height, Weight, BMI)
            profile.height?.let { h ->
                if (h > 0) {
                    entries.add(
                        PortableBundleEntry(
                            fullUrl = "urn:medisense:Observation/obs-height",
                            resource = PortableObservationResource(
                                resourceId = "obs-height",
                                code = "8302-2",
                                display = "Body Height",
                                value = "%.1f".format(Locale.US, h),
                                unit = "cm",
                                effectiveDateTime = nowIso
                            )
                        )
                    )
                }
            }
            profile.weight?.let { w ->
                if (w > 0) {
                    entries.add(
                        PortableBundleEntry(
                            fullUrl = "urn:medisense:Observation/obs-weight",
                            resource = PortableObservationResource(
                                resourceId = "obs-weight",
                                code = "29463-7",
                                display = "Body Weight",
                                value = "%.1f".format(Locale.US, w),
                                unit = "kg",
                                effectiveDateTime = nowIso
                            )
                        )
                    )
                }
            }
            if (profile.height != null && profile.height > 0 && profile.weight != null && profile.weight > 0) {
                val heightM = profile.height / 100.0
                val bmi = profile.weight / (heightM * heightM)
                entries.add(
                    PortableBundleEntry(
                        fullUrl = "urn:medisense:Observation/obs-bmi",
                        resource = PortableObservationResource(
                            resourceId = "obs-bmi",
                            code = "39156-5",
                            display = "Body Mass Index",
                            value = "%.1f".format(Locale.US, bmi),
                            unit = "kg/m2",
                            effectiveDateTime = nowIso
                        )
                    )
                )
            }
        }

        // 2. EMERGENCY_INFORMATION
        if (selected.contains(PortableDataCategory.EMERGENCY_INFORMATION) && emergencyCard != null) {
            val emergencyResource = MediSenseEmergencyCardResource(
                resourceId = "emerg-01",
                patientName = sanitizeText(emergencyCard.personalInfo.fullName).ifBlank { "Not provided" },
                bloodGroup = sanitizeText(emergencyCard.bloodGroup).ifBlank { "Unknown" },
                emergencyContactName = sanitizeText(emergencyCard.emergencyContact.name).ifBlank { "Not provided" },
                emergencyContactPhone = sanitizeText(emergencyCard.emergencyContact.phone).ifBlank { "Not provided" },
                criticalAllergies = emergencyCard.allergies.allergies.map { sanitizeText(it) },
                specialInstructions = sanitizeText(emergencyCard.notes.notes).ifBlank { "No special instructions recorded." }
            )
            entries.add(PortableBundleEntry(fullUrl = "urn:medisense:MediSenseEmergencyCard/emerg-01", resource = emergencyResource))
        }

        // 3. ALLERGIES
        if (selected.contains(PortableDataCategory.ALLERGIES) && profile != null) {
            val allergiesList = splitStringList(profile.allergies)
            allergiesList.forEachIndexed { index, item ->
                val id = "alg-${index + 1}"
                entries.add(
                    PortableBundleEntry(
                        fullUrl = "urn:medisense:AllergyIntolerance/$id",
                        resource = PortableAllergyIntoleranceResource(
                            resourceId = id,
                            substance = sanitizeText(item),
                            category = "medication-or-environment",
                            clinicalStatus = "active",
                            recordedDate = nowIso
                        )
                    )
                )
            }
        }

        // 4. HEALTH_CONDITIONS
        if (selected.contains(PortableDataCategory.HEALTH_CONDITIONS) && profile != null) {
            val conditionsList = splitStringList(profile.existingDiseases)
            conditionsList.forEachIndexed { index, item ->
                val id = "cond-${index + 1}"
                entries.add(
                    PortableBundleEntry(
                        fullUrl = "urn:medisense:Condition/$id",
                        resource = PortableConditionResource(
                            resourceId = id,
                            conditionName = sanitizeText(item),
                            clinicalStatus = "active",
                            verificationStatus = "unconfirmed",
                            note = sanitizeText(profile.notes).ifBlank { null }
                        )
                    )
                )
            }
        }

        // 5. MEDICATIONS
        if (selected.contains(PortableDataCategory.MEDICATIONS)) {
            medications.forEachIndexed { index, med ->
                val id = "med-${index + 1}"
                entries.add(
                    PortableBundleEntry(
                        fullUrl = "urn:medisense:MedicationStatement/$id",
                        resource = PortableMedicationStatementResource(
                            resourceId = id,
                            medicationName = sanitizeText(med.medicineName),
                            dosageText = "${med.dosage} ${med.dosageUnit}".trim().ifBlank { null },
                            frequency = sanitizeText(med.frequency).ifBlank { null },
                            instructions = sanitizeText(med.instructions).ifBlank { null },
                            effectivePeriodStart = med.startDate?.let { DISPLAY_DATE_FORMAT.format(Date(it)) },
                            effectivePeriodEnd = med.endDate?.let { DISPLAY_DATE_FORMAT.format(Date(it)) },
                            isActive = med.active
                        )
                    )
                )
            }
        }

        // 6. MEDICATION_ADHERENCE
        if (selected.contains(PortableDataCategory.MEDICATION_ADHERENCE)) {
            val recentLogs = medicationHistory.take(20).map { hist ->
                PortableAdherenceLogItem(
                    medicineName = sanitizeText(hist.medicineName),
                    scheduledDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(hist.scheduledDate)),
                    scheduledTime = hist.scheduledTime,
                    status = hist.status,
                    actionTimeIso = hist.actionTime?.let { ISO_DATE_FORMAT.format(Date(it)) }
                )
            }
            val adherenceResource = PortableMedicationAdherenceResource(
                resourceId = "adh-01",
                totalDosesLogged = medicationHistory.size,
                takenCount = adherenceStats?.takenCount ?: 0,
                missedCount = adherenceStats?.missedCount ?: 0,
                skippedCount = adherenceStats?.skippedCount ?: 0,
                adherencePercentage = adherenceStats?.percentage?.toInt(),
                adherenceCategory = if ((adherenceStats?.percentage ?: 0f) >= 80f) "Optimal (>=80%)" else "Suboptimal (<80%)",
                recentLogEntries = recentLogs
            )
            entries.add(PortableBundleEntry(fullUrl = "urn:medisense:MediSenseAdherenceHistory/adh-01", resource = adherenceResource))
        }

        // 7. APPOINTMENTS
        if (selected.contains(PortableDataCategory.APPOINTMENTS)) {
            appointments.forEachIndexed { index, appt ->
                val id = "appt-${index + 1}"
                entries.add(
                    PortableBundleEntry(
                        fullUrl = "urn:medisense:Appointment/$id",
                        resource = PortableAppointmentResource(
                            resourceId = id,
                            practitionerName = sanitizeText(appt.doctorName).ifBlank { "Doctor" },
                            serviceProvider = sanitizeText(appt.clinicName).ifBlank { "Clinic" },
                            appointmentType = sanitizeText(appt.appointmentType),
                            startDateTime = "${appt.appointmentDate} ${appt.appointmentTime}",
                            appointmentStatus = appt.status,
                            comment = sanitizeText(appt.notes).ifBlank { null }
                        )
                    )
                )
            }
        }

        // 8. PREDICTION_HISTORY
        if (selected.contains(PortableDataCategory.PREDICTION_HISTORY)) {
            predictions.forEachIndexed { index, pred ->
                val id = "pred-${index + 1}"
                entries.add(
                    PortableBundleEntry(
                        fullUrl = "urn:medisense:MediSensePrediction/$id",
                        resource = MediSensePredictionResource(
                            resourceId = id,
                            predictedDisease = sanitizeText(pred.predictedDisease),
                            modelConfidence = pred.confidence,
                            modelVersion = pred.modelVersion,
                            assessedAtIso = ISO_DATE_FORMAT.format(Date(pred.predictionTimestamp)),
                            reportedSymptoms = pred.symptoms.map { sanitizeText(it) },
                            explanationSummary = sanitizeText(pred.explanationSummary).ifBlank { null }
                        )
                    )
                )
            }
        }

        // 9. HEALTH_TRENDS
        if (selected.contains(PortableDataCategory.HEALTH_TRENDS) && longitudinal != null) {
            val trendResource = MediSenseTrendResource(
                resourceId = "trend-01",
                analysisPeriod = longitudinal.period.displayName,
                recurringSymptoms = longitudinal.recurringSymptoms.map { it.symptomName },
                detectedPatterns = longitudinal.detectedPatterns.map { it.title + ": " + it.description },
                trendSummary = longitudinal.generatedSummary
            )
            entries.add(PortableBundleEntry(fullUrl = "urn:medisense:MediSenseLongitudinalTrend/trend-01", resource = trendResource))
        }

        // 10. PERSONAL_CONTEXT
        if (selected.contains(PortableDataCategory.PERSONAL_CONTEXT) && personalContext != null) {
            val contextResource = MediSenseContextResource(
                resourceId = "ctx-01",
                profileCompletenessScore = personalContext.profileCompleteness,
                activeMedicationsCount = personalContext.medications.activeCount,
                recentAssessmentsCount = personalContext.predictions.recentCount,
                upcomingAppointmentsCount = personalContext.appointments.upcomingCount,
                contextSummary = personalContext.generatedSummary,
                personalizationRationale = personalContext.whyPersonalized
            )
            entries.add(PortableBundleEntry(fullUrl = "urn:medisense:MediSenseContext/ctx-01", resource = contextResource))
        }

        // 11. DATA_QUALITY
        if (selected.contains(PortableDataCategory.DATA_QUALITY) && qualitySummary != null) {
            val dqResource = MediSenseDataQualityResource(
                resourceId = "dq-01",
                overallStatus = qualitySummary.status.name,
                qualityScore = qualitySummary.qualityScore,
                totalChecks = qualitySummary.totalChecks,
                passedChecks = qualitySummary.passedChecks,
                qualityNotices = qualitySummary.issues.map { "${it.title}: ${it.explanation}" }
            )
            entries.add(PortableBundleEntry(fullUrl = "urn:medisense:MediSenseDataQuality/dq-01", resource = dqResource))
        }

        // 12. PERSONALIZED_GUIDANCE
        if (selected.contains(PortableDataCategory.PERSONALIZED_GUIDANCE) && guidance != null) {
            guidance.guidanceList.forEachIndexed { index, g ->
                val id = "gd-${index + 1}"
                entries.add(
                    PortableBundleEntry(
                        fullUrl = "urn:medisense:MediSenseGuidance/$id",
                        resource = MediSenseGuidanceResource(
                            resourceId = id,
                            guidanceTitle = sanitizeText(g.title),
                            guidanceMessage = sanitizeText(g.message),
                            category = g.category.name,
                            priority = g.priority.name,
                            rationale = sanitizeText(g.explanation)
                        )
                    )
                )
            }
        }

        // 13. CONSULTATION_SUMMARY (From upcoming appointments & active care plan)
        if (selected.contains(PortableDataCategory.CONSULTATION_SUMMARY) && appointments.isNotEmpty()) {
            val appt = appointments.firstOrNull { it.status == "SCHEDULED" } ?: appointments.first()
            val summaryText = "Consultation Preparation for ${appt.doctorName} (${appt.clinicName}) on ${appt.appointmentDate}. Notes: ${appt.notes ?: "None recorded"}"
            entries.add(
                PortableBundleEntry(
                    fullUrl = "urn:medisense:MediSenseConsultationSummary/cs-01",
                    resource = MediSenseTimelineResource(
                        resourceId = "cs-01",
                        eventType = "CONSULTATION_PREPARATION",
                        title = "Doctor Consultation Summary",
                        description = summaryText,
                        timestampIso = nowIso
                    )
                )
            )
        }

        // 14. DECISION_TRACES
        if (selected.contains(PortableDataCategory.DECISION_TRACES)) {
            decisionTraces.take(10).forEachIndexed { index, trace ->
                val id = "trace-${index + 1}"
                entries.add(
                    PortableBundleEntry(
                        fullUrl = "urn:medisense:MediSenseDecisionTrace/$id",
                        resource = MediSenseDecisionTraceResource(
                            resourceId = id,
                            decisionType = trace.decisionType.displayName,
                            engineName = trace.engineName,
                            primaryOutput = trace.outputResult.primaryOutput,
                            factorAttributions = trace.inputFactors.map { f ->
                                PortableTraceFactorItem(
                                    factorName = f.name,
                                    weightDescription = "${f.weightPercentage ?: 0}% influence",
                                    interpretation = f.interpretation
                                )
                            },
                            executionPipelineSteps = trace.processingSteps.map { "${it.stepNumber}. ${it.title} (${it.engineComponent})" },
                            explanationSummary = trace.explanationText,
                            transparencyNotice = trace.limitationsText
                        )
                    )
                )
            }
        }

        // 15. HEALTH_TIMELINE
        if (selected.contains(PortableDataCategory.HEALTH_TIMELINE)) {
            timelineEvents.take(15).forEachIndexed { index, event ->
                val id = "tl-${index + 1}"
                entries.add(
                    PortableBundleEntry(
                        fullUrl = "urn:medisense:MediSenseTimelineEvent/$id",
                        resource = MediSenseTimelineResource(
                            resourceId = id,
                            eventType = event.eventType.name,
                            title = event.title,
                            description = event.shortDescription,
                            timestampIso = ISO_DATE_FORMAT.format(Date(event.timestamp))
                        )
                    )
                )
            }
        }

        // 16. RCHR_STATE
        if (selected.contains(PortableDataCategory.RCHR_STATE) && rchr != null) {
            val rchrResource = MediSenseRchrResource(
                resourceId = "rchr-01",
                rchrVersion = rchr.representationVersion,
                encodedFeaturesCount = rchr.totalEncodedFeatures,
                completenessPercentage = rchr.completenessPercentage,
                stabilityIndex = if (rchr.hasSufficientData) "STABLE" else "PRELIMINARY"
            )
            entries.add(PortableBundleEntry(fullUrl = "urn:medisense:MediSenseRchr/rchr-01", resource = rchrResource))
        }

        // Compute preliminary package to calculate fingerprint
        val tempBundle = PortableHealthRecordBundle(
            resourceType = "Bundle",
            type = "collection",
            meta = PortablePackageMetadata(
                packageId = packageId,
                appName = "MediSense",
                appVersion = "1.0",
                schemaVersion = "1.0-interop",
                exportVersion = "1.0",
                generatedAtTimestamp = now,
                generatedAtIso = nowIso,
                sha256Fingerprint = "COMPUTING",
                totalResourceCount = entries.size,
                selectedCategories = selected.map { it.displayName }
            ),
            entry = entries
        )

        val rawJson = gson.toJson(tempBundle)
        val calculatedFingerprint = computeSha256(rawJson)

        return tempBundle.copy(
            meta = tempBundle.meta.copy(sha256Fingerprint = calculatedFingerprint)
        )
    }

    fun computeCategoryCounts(
        scope: PortableExportScope,
        profile: HealthProfileEntity?,
        medications: List<MedicationEntity>,
        medicationHistory: List<MedicationHistoryEntity>,
        appointments: List<AppointmentEntity>,
        predictions: List<PredictionHistoryEntity>,
        longitudinal: LongitudinalHealthSummary?,
        personalContext: PersonalHealthContext?,
        guidance: GuidanceEngineResult?,
        rchr: RchrRepresentation?,
        decisionTraces: List<HealthDecisionTrace>,
        timelineEvents: List<HealthTimelineEvent>,
        emergencyCard: EmergencyHealthCard?,
        qualitySummary: HealthDataQualitySummary?
    ): Map<PortableDataCategory, Int> {
        val counts = mutableMapOf<PortableDataCategory, Int>()

        counts[PortableDataCategory.BASIC_PROFILE] = if (profile != null) 1 + (if (profile.height != null) 1 else 0) + (if (profile.weight != null) 1 else 0) else 0
        counts[PortableDataCategory.EMERGENCY_INFORMATION] = if (emergencyCard != null) 1 else 0
        counts[PortableDataCategory.ALLERGIES] = splitStringList(profile?.allergies).size
        counts[PortableDataCategory.HEALTH_CONDITIONS] = splitStringList(profile?.existingDiseases).size
        counts[PortableDataCategory.MEDICATIONS] = medications.size
        counts[PortableDataCategory.MEDICATION_ADHERENCE] = if (medicationHistory.isNotEmpty()) 1 else 0
        counts[PortableDataCategory.APPOINTMENTS] = appointments.size
        counts[PortableDataCategory.PREDICTION_HISTORY] = predictions.size
        counts[PortableDataCategory.HEALTH_TRENDS] = if (longitudinal != null) 1 else 0
        counts[PortableDataCategory.PERSONAL_CONTEXT] = if (personalContext != null) 1 else 0
        counts[PortableDataCategory.DATA_QUALITY] = if (qualitySummary != null) 1 else 0
        counts[PortableDataCategory.PERSONALIZED_GUIDANCE] = guidance?.guidanceList?.size ?: 0
        counts[PortableDataCategory.CONSULTATION_SUMMARY] = if (appointments.isNotEmpty()) 1 else 0
        counts[PortableDataCategory.DECISION_TRACES] = decisionTraces.size
        counts[PortableDataCategory.HEALTH_TIMELINE] = timelineEvents.size
        counts[PortableDataCategory.RCHR_STATE] = if (rchr != null) 1 else 0

        return counts
    }

    private fun splitStringList(input: String?): List<String> {
        if (input.isNullOrBlank()) return emptyList()
        return input.split(',', ';', '\n')
            .map { it.trim().trimStart('•', '-', '*').trim() }
            .filter { it.isNotBlank() }
    }

    fun sanitizeText(input: String?): String {
        if (input.isNullOrBlank()) return ""
        return input.replace(Regex("(?i)(password|bearer\\s+|secret_key=)[^\\s]*"), "[REDACTED]")
            .trim()
    }

    fun computeSha256(text: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(text.toByteArray(Charsets.UTF_8))
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "HASH-${System.currentTimeMillis()}"
        }
    }

    private fun calculateAge(dob: String?): Int? {
        if (dob.isNullOrBlank()) return null
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val birthDate = sdf.parse(dob) ?: return null
            val dobCal = Calendar.getInstance().apply { time = birthDate }
            val nowCal = Calendar.getInstance()
            var age = nowCal.get(Calendar.YEAR) - dobCal.get(Calendar.YEAR)
            if (nowCal.get(Calendar.DAY_OF_YEAR) < dobCal.get(Calendar.DAY_OF_YEAR)) {
                age--
            }
            if (age in 0..130) age else null
        } catch (e: Exception) {
            null
        }
    }
}
