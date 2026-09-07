package com.medisense.app.domain.sharing

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.medisense.app.data.local.entity.AppointmentEntity
import com.medisense.app.data.local.entity.HealthProfileEntity
import com.medisense.app.data.local.entity.MedicationEntity
import com.medisense.app.data.local.entity.MedicationHistoryEntity
import com.medisense.app.data.local.entity.PredictionHistoryEntity
import com.medisense.app.domain.model.*
import com.medisense.app.domain.security.SecureLogger
import com.medisense.app.utils.AdherenceStats
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

/**
 * Builds deterministic, sanitized, and offline-first [HealthSharingPackage] instances.
 * Implements strict data minimization, safety filters, and SHA-256 fingerprinting.
 */
object HealthSharingPackageBuilder {

    private const val TAG = "HealthSharingPkgBuilder"
    private val ISO_DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val DISPLAY_DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()

    /**
     * Builds the complete [HealthSharingPackage] object based on the user's selected [HealthSharingScope].
     */
    fun buildPackage(
        userId: String,
        scope: HealthSharingScope,
        profile: HealthProfileEntity?,
        medications: List<MedicationEntity>,
        medicationHistory: List<MedicationHistoryEntity>,
        adherenceStats: AdherenceStats?,
        predictions: List<PredictionHistoryEntity>,
        appointments: List<AppointmentEntity>,
        decisionTraces: List<HealthDecisionTrace>,
        longitudinal: LongitudinalHealthSummary?,
        emergencyCard: EmergencyHealthCard?,
        qualitySummary: HealthDataQualitySummary?
    ): HealthSharingPackage {
        val now = System.currentTimeMillis()
        val nowIso = ISO_DATE_FORMAT.format(Date(now))
        val packageId = "MS-SHARE-${UUID.randomUUID().toString().take(8).uppercase()}"

        val userHash = hashUserId(userId)

        val categories = scope.selectedCategories
        val grantedCategoryNames = categories.map { it.displayName }

        // 1. Profile Section
        val profileSection = if (categories.contains(SharingDataCategory.PROFILE) && profile != null) {
            SharedProfileSection(
                fullName = sanitizeText(profile.fullName),
                age = calculateAge(profile.dateOfBirth),
                dateOfBirth = sanitizeText(profile.dateOfBirth).ifBlank { null },
                gender = sanitizeText(profile.gender).ifBlank { null },
                bloodGroup = sanitizeText(profile.bloodGroup).ifBlank { null },
                emergencyContactName = sanitizeText(profile.emergencyContactName).ifBlank { null },
                emergencyContactPhone = sanitizeText(profile.emergencyContactNumber).ifBlank { null }
            )
        } else null

        // 2. Conditions & Allergies Section
        val conditionsSection = if (categories.contains(SharingDataCategory.CONDITIONS_ALLERGIES) && profile != null) {
            val conditionsList = splitStringList(profile.existingDiseases).map { sanitizeText(it) }
            val allergiesList = splitStringList(profile.allergies).map { sanitizeText(it) }
            SharedConditionsAllergiesSection(
                conditions = conditionsList,
                allergies = allergiesList,
                notes = sanitizeText(profile.notes).ifBlank { null }
            )
        } else null

        // 3. Medications & Adherence Section
        val medicationsSection = if (categories.contains(SharingDataCategory.MEDICATIONS)) {
            val activeItems = medications.filter { it.active }.map { med ->
                SharedMedicationItem(
                    medicineName = sanitizeText(med.medicineName),
                    dosage = "${med.dosage} ${med.dosageUnit}".trim(),
                    frequency = sanitizeText(med.frequency),
                    instructions = sanitizeText(med.instructions),
                    startDate = med.startDate?.let { DISPLAY_DATE_FORMAT.format(Date(it)) },
                    endDate = med.endDate?.let { DISPLAY_DATE_FORMAT.format(Date(it)) }
                )
            }
            val recentDoses = medicationHistory.take(15).map { hist ->
                SharedAdherenceItem(
                    medicineName = sanitizeText(hist.medicineName),
                    scheduledDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(hist.scheduledDate)),
                    scheduledTime = hist.scheduledTime,
                    status = hist.status,
                    actionTime = hist.actionTime?.let { DISPLAY_DATE_FORMAT.format(Date(it)) }
                )
            }
            SharedMedicationsSection(
                totalActiveMedications = activeItems.size,
                overallAdherencePercentage = adherenceStats?.percentage?.toInt(),
                activeMedications = activeItems,
                recentDoseHistory = recentDoses
            )
        } else null

        // 4. Prediction / Assessment History Section
        val predictionsSection = if (categories.contains(SharingDataCategory.PREDICTIONS)) {
            val items = predictions.take(10).map { pred ->
                val dateStr = DISPLAY_DATE_FORMAT.format(Date(pred.predictionTimestamp))
                SharedPredictionItem(
                    dateIso = dateStr,
                    reportedSymptoms = pred.symptoms.map { sanitizeText(it) },
                    primaryAssessment = sanitizeText(pred.predictedDisease),
                    confidencePercentage = (pred.confidence * 100).toInt().coerceIn(0, 100),
                    riskLevel = if (pred.confidence >= 0.85f) "High Association" else "Moderate Association",
                    recommendedPrecautions = emptyList()
                )
            }
            SharedPredictionsSection(
                totalAssessments = items.size,
                assessments = items
            )
        } else null

        // 5. Appointments Section
        val appointmentsSection = if (categories.contains(SharingDataCategory.APPOINTMENTS)) {
            val items = appointments.map { appt ->
                SharedAppointmentItem(
                    doctorName = sanitizeText(appt.doctorName),
                    clinicName = sanitizeText(appt.clinicName),
                    appointmentType = sanitizeText(appt.appointmentType),
                    appointmentDate = appt.appointmentDate,
                    appointmentTime = appt.appointmentTime,
                    status = appt.status,
                    notes = sanitizeText(appt.notes).ifBlank { null }
                )
            }
            SharedAppointmentsSection(
                totalAppointments = items.size,
                appointments = items
            )
        } else null

        // 6. Decision Traces Section
        val decisionTracesSection = if (categories.contains(SharingDataCategory.DECISION_TRACES)) {
            val items = decisionTraces.take(8).map { trace ->
                SharedDecisionTraceItem(
                    traceId = trace.traceId,
                    decisionType = trace.decisionType.displayName,
                    title = trace.outputResult.primaryOutput,
                    summary = trace.explanationText,
                    generatedAtIso = ISO_DATE_FORMAT.format(Date(trace.generatedAt)),
                    confidenceScore = trace.outputResult.confidenceOrStatus?.filter { it.isDigit() }?.toFloatOrNull()?.let { it / 100f },
                    contributingFactors = trace.inputFactors.map { f ->
                        SharedTraceFactor(
                            factorName = f.name,
                            weightDescription = "${f.weightPercentage ?: 0}% influence",
                            observation = f.interpretation
                        )
                    },
                    transparencyNotice = trace.limitationsText
                )
            }
            SharedDecisionTracesSection(
                totalTraces = items.size,
                traces = items
            )
        } else null

        // 7. Longitudinal Summary Section
        val longitudinalSection = if (categories.contains(SharingDataCategory.LONGITUDINAL_SUMMARY) && longitudinal != null) {
            SharedLongitudinalSection(
                analysisPeriod = longitudinal.period.displayName,
                recurringSymptoms = longitudinal.recurringSymptoms.map { it.symptomName },
                detectedPatterns = longitudinal.detectedPatterns.map { it.description },
                stabilitySummary = longitudinal.generatedSummary
            )
        } else null

        // 8. Emergency Section
        val emergencySection = if (categories.contains(SharingDataCategory.EMERGENCY_SUMMARY) && emergencyCard != null) {
            SharedEmergencySection(
                emergencyContactName = emergencyCard.emergencyContact.name.ifBlank { "Not specified" },
                emergencyContactPhone = emergencyCard.emergencyContact.phone.ifBlank { "Not specified" },
                bloodGroup = emergencyCard.bloodGroup.ifBlank { "Unknown" },
                criticalAllergies = emergencyCard.allergies.allergies,
                emergencyInstructions = emergencyCard.notes.notes.ifBlank { "No special emergency instructions recorded." }
            )
        } else null

        // 9. Data Quality Notice Section
        val qualitySection = if (categories.contains(SharingDataCategory.DATA_QUALITY_NOTE) && qualitySummary != null) {
            SharedDataQualitySection(
                overallQualityStatus = qualitySummary.status.name,
                completenessScore = qualitySummary.qualityScore,
                checksPassed = qualitySummary.passedChecks,
                totalChecks = qualitySummary.totalChecks,
                qualityIssues = qualitySummary.issues.map { "${it.title}: ${it.explanation}" },
                noticeText = if (qualitySummary.issues.isEmpty()) {
                    "Health records passed all local completeness and consistency validations."
                } else {
                    "Note: ${qualitySummary.issues.size} non-blocking data quality notices were recorded during package compilation."
                }
            )
        } else null

        // Calculate package content fingerprint
        val tempPackageWithoutFingerprint = HealthSharingPackage(
            packageMetadata = PackageMetadata(
                packageId = packageId,
                generatedAtTimestamp = now,
                generatedAtIso = nowIso,
                sha256Fingerprint = "COMPUTING",
                totalCategoriesIncluded = categories.size
            ),
            consentDeclaration = ConsentDeclaration(
                consentedByUserIdHash = userHash,
                purpose = scope.purpose.displayName,
                recipientLabel = scope.recipientLabel.ifBlank { "Designated Recipient" },
                consentedAtIso = nowIso,
                grantedCategories = grantedCategoryNames,
                exportFormat = scope.format.displayName
            ),
            personalProfile = profileSection,
            conditionsAndAllergies = conditionsSection,
            medicationsAndAdherence = medicationsSection,
            predictionHistory = predictionsSection,
            doctorAppointments = appointmentsSection,
            decisionTraces = decisionTracesSection,
            longitudinalSummary = longitudinalSection,
            emergencyAccessCard = emergencySection,
            dataQualityNotice = qualitySection
        )

        val rawJson = gson.toJson(tempPackageWithoutFingerprint)
        val calculatedFingerprint = computeSha256(rawJson)

        return tempPackageWithoutFingerprint.copy(
            packageMetadata = tempPackageWithoutFingerprint.packageMetadata.copy(
                sha256Fingerprint = calculatedFingerprint
            )
        )
    }

    /**
     * Serializes the [HealthSharingPackage] to formatted JSON and writes it to cacheDir/shares/.
     */
    fun writeJsonPackageToFile(context: Context, pkg: HealthSharingPackage): File {
        val sharesDir = File(context.cacheDir, "shares").apply {
            if (!exists()) mkdirs()
        }
        val timestamp = System.currentTimeMillis()
        val file = File(sharesDir, "medisense_health_package_${pkg.packageMetadata.packageId}_$timestamp.json")
        val jsonString = gson.toJson(pkg)
        FileOutputStream(file).use { out ->
            out.write(jsonString.toByteArray(Charsets.UTF_8))
        }
        SecureLogger.d(TAG, "Saved health sharing JSON package: ${file.name} (${file.length()} bytes)")
        return file
    }

    /**
     * Generates a human-readable summary of package contents for preview and consent screens.
     */
    fun generatePreviewSummary(pkg: HealthSharingPackage): String {
        val sb = StringBuilder()
        sb.appendLine("📦 Package ID: ${pkg.packageMetadata.packageId}")
        sb.appendLine("🎯 Purpose: ${pkg.consentDeclaration.purpose}")
        sb.appendLine("👤 Recipient: ${pkg.consentDeclaration.recipientLabel}")
        sb.appendLine("🔒 SHA-256: ${pkg.packageMetadata.sha256Fingerprint.take(16)}...")
        sb.appendLine("📅 Exported: ${pkg.packageMetadata.generatedAtIso}")
        sb.appendLine()
        sb.appendLine("Included Data Categories (${pkg.packageMetadata.totalCategoriesIncluded}):")
        pkg.consentDeclaration.grantedCategories.forEach { cat ->
            sb.appendLine(" • $cat")
        }
        return sb.toString().trim()
    }

    private fun splitStringList(input: String?): List<String> {
        if (input.isNullOrBlank()) return emptyList()
        return input.split(',', ';', '\n')
            .map { it.trim().trimStart('•', '-', '*').trim() }
            .filter { it.isNotBlank() }
    }

    private fun sanitizeText(input: String?): String {
        if (input.isNullOrBlank()) return ""
        // Strip sensitive internal keywords, credentials, or tokens if present
        return input.replace(Regex("(?i)(password|bearer\\s+|secret_key=)[^\\s]*"), "[REDACTED]")
            .trim()
    }

    private fun hashUserId(userId: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(userId.toByteArray(Charsets.UTF_8))
            hashBytes.joinToString("") { "%02x".format(it) }.take(16)
        } catch (e: Exception) {
            "ANON-${userId.takeLast(6)}"
        }
    }

    private fun computeSha256(text: String): String {
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
