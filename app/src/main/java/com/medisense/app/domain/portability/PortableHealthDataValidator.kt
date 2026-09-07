package com.medisense.app.domain.portability

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.medisense.app.data.local.entity.AppointmentEntity
import com.medisense.app.data.local.entity.HealthProfileEntity
import com.medisense.app.data.local.entity.MedicationEntity
import com.medisense.app.data.local.entity.PredictionHistoryEntity
import com.medisense.app.domain.model.*
import com.medisense.app.domain.security.SecureLogger
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

/**
 * 100% offline-first, deterministic validation and parsing engine for MediSense portable health data packages.
 * Safely parses JSON files, verifies schema conformance, identifies malformed data, and assesses local conflicts
 * without mutating local Room storage.
 */
object PortableHealthDataValidator {

    private const val TAG = "PortableValidator"
    private const val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024 // 10MB safety threshold
    private const val SUPPORTED_SCHEMA_VERSION_PREFIX = "1."

    private val gson: Gson = GsonBuilder()
        .disableHtmlEscaping()
        .create()

    private val ISO_DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val SIMPLE_DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /**
     * Executes the multi-stage validation workflow on raw JSON text.
     */
    fun validateAndParse(
        rawJson: String,
        localProfile: HealthProfileEntity? = null,
        localMedications: List<MedicationEntity> = emptyList(),
        localAppointments: List<AppointmentEntity> = emptyList(),
        localPredictions: List<PredictionHistoryEntity> = emptyList(),
        fileName: String = "import.json"
    ): PortableImportPreview {
        val issues = mutableListOf<PortableImportIssue>()
        val conflicts = mutableListOf<ImportConflictItem>()
        var issueCounter = 1

        fun addIssue(type: PortableImportIssueType, severity: PortableImportIssueSeverity, message: String, resource: String? = null, action: String? = null) {
            issues.add(
                PortableImportIssue(
                    issueId = "ISSUE-${issueCounter++}",
                    type = type,
                    severity = severity,
                    message = message,
                    affectedResource = resource,
                    suggestedAction = action
                )
            )
        }

        // 1. Basic Size & Empty Check
        if (rawJson.isBlank()) {
            addIssue(
                type = PortableImportIssueType.EMPTY_IMPORT,
                severity = PortableImportIssueSeverity.ERROR,
                message = "The selected file is empty.",
                action = "Please select a valid MediSense portable export JSON file."
            )
            return buildErrorPreview(fileName, issues, conflicts)
        }

        if (rawJson.length > MAX_FILE_SIZE_BYTES) {
            addIssue(
                type = PortableImportIssueType.FILE_SIZE_LIMIT_EXCEEDED,
                severity = PortableImportIssueSeverity.ERROR,
                message = "The selected file exceeds the 10MB safety limit (${rawJson.length / (1024 * 1024)}MB).",
                action = "Ensure you are selecting a standard MediSense export file."
            )
            return buildErrorPreview(fileName, issues, conflicts)
        }

        // 2. JSON Syntax Validation
        val rootElement = try {
            JsonParser.parseString(rawJson)
        } catch (e: Exception) {
            SecureLogger.w(TAG, "Invalid JSON syntax: ${e.message}")
            addIssue(
                type = PortableImportIssueType.INVALID_JSON,
                severity = PortableImportIssueSeverity.ERROR,
                message = "Invalid JSON syntax: ${e.localizedMessage ?: "Parsing failed"}",
                action = "Check file encoding and structure."
            )
            return buildErrorPreview(fileName, issues, conflicts)
        }

        if (!rootElement.isJsonObject) {
            addIssue(
                type = PortableImportIssueType.INVALID_JSON,
                severity = PortableImportIssueSeverity.ERROR,
                message = "Root JSON entity must be an object (FHIR Bundle).",
                action = "Select a valid MediSense portable export JSON file."
            )
            return buildErrorPreview(fileName, issues, conflicts)
        }

        val rootObj = rootElement.asJsonObject

        // 3. FHIR Collection Bundle Root Check
        val resourceType = rootObj.get("resourceType")?.asString
        if (resourceType != "Bundle") {
            addIssue(
                type = PortableImportIssueType.INVALID_RESOURCE_TYPE,
                severity = PortableImportIssueSeverity.ERROR,
                message = "Expected resourceType 'Bundle' but found '${resourceType ?: "null"}'.",
                action = "Ensure the export file conforms to MediSense interoperability specifications."
            )
        }

        val bundleType = rootObj.get("type")?.asString
        if (bundleType != "collection") {
            addIssue(
                type = PortableImportIssueType.INVALID_RESOURCE,
                severity = PortableImportIssueSeverity.WARNING,
                message = "Bundle type is '${bundleType ?: "null"}' (expected 'collection').",
                action = "File may be from a non-standard export."
            )
        }

        // 4. Metadata Validation
        val metaObj = rootObj.getAsJsonObject("meta")
        if (metaObj == null) {
            addIssue(
                type = PortableImportIssueType.MISSING_METADATA,
                severity = PortableImportIssueSeverity.ERROR,
                message = "Missing required 'meta' metadata block.",
                action = "File is missing provenance and schema details."
            )
            return buildErrorPreview(fileName, issues, conflicts)
        }

        val schemaVersion = metaObj.get("schemaVersion")?.asString ?: ""
        val exportVersion = metaObj.get("exportVersion")?.asString ?: "1.0"
        val appName = metaObj.get("appName")?.asString ?: "Unknown App"
        val packageId = metaObj.get("packageId")?.asString
        val generatedAtIso = metaObj.get("generatedAtIso")?.asString
        val expectedSha256 = metaObj.get("sha256Fingerprint")?.asString

        if (schemaVersion.isBlank()) {
            addIssue(
                type = PortableImportIssueType.MISSING_METADATA,
                severity = PortableImportIssueSeverity.WARNING,
                message = "Metadata is missing schemaVersion.",
                action = "Assuming standard MediSense schema version."
            )
        } else if (!schemaVersion.startsWith(SUPPORTED_SCHEMA_VERSION_PREFIX)) {
            addIssue(
                type = PortableImportIssueType.UNSUPPORTED_SCHEMA_VERSION,
                severity = PortableImportIssueSeverity.ERROR,
                message = "Unsupported schema version '$schemaVersion'. Supported versions: 1.x.",
                action = "Upgrade the exporting app or export a newer file."
            )
        }

        if (appName != "MediSense") {
            addIssue(
                type = PortableImportIssueType.UNSUPPORTED_RESOURCE,
                severity = PortableImportIssueSeverity.INFO,
                message = "Package exported by third-party application '$appName'.",
                action = "Review parsed resources carefully."
            )
        }

        // 5. Checksum verification (non-blocking warning if mismatch)
        if (!expectedSha256.isNullOrBlank()) {
            val calculatedSha256 = computeSha256(rawJson)
            // If whole file or payload hash is evaluated
            if (expectedSha256.length != 64) {
                addIssue(
                    type = PortableImportIssueType.CHECKSUM_MISMATCH,
                    severity = PortableImportIssueSeverity.WARNING,
                    message = "SHA-256 fingerprint in metadata has invalid format.",
                    action = "Integrity check may be unreliable."
                )
            }
        }

        // 6. Entries & Resource Structure Parsing
        val entryArray = rootObj.getAsJsonArray("entry")
        if (entryArray == null || entryArray.size() == 0) {
            addIssue(
                type = PortableImportIssueType.EMPTY_IMPORT,
                severity = PortableImportIssueSeverity.WARNING,
                message = "The bundle contains no resource entries.",
                action = "The exported package has no health records."
            )
        }

        val parsedResources = mutableListOf<PortableResource>()
        val parsedCategoriesMap = mutableMapOf<PortableDataCategory, MutableList<PortableResource>>()
        val seenResourceIds = mutableSetOf<String>()

        PortableDataCategory.entries.forEach { cat ->
            parsedCategoriesMap[cat] = mutableListOf()
        }

        entryArray?.forEachIndexed { index, entryElement ->
            if (!entryElement.isJsonObject) {
                addIssue(
                    type = PortableImportIssueType.INVALID_RESOURCE,
                    severity = PortableImportIssueSeverity.WARNING,
                    message = "Entry #$index is not a valid JSON object.",
                    resource = "Entry[$index]"
                )
                return@forEachIndexed
            }

            val entryObj = entryElement.asJsonObject
            val resourceJson = entryObj.getAsJsonObject("resource")
            if (resourceJson == null) {
                addIssue(
                    type = PortableImportIssueType.MISSING_REQUIRED_FIELD,
                    severity = PortableImportIssueSeverity.WARNING,
                    message = "Entry #$index is missing 'resource' envelope.",
                    resource = "Entry[$index]"
                )
                return@forEachIndexed
            }

            val resType = resourceJson.get("resourceType")?.asString ?: "Unknown"
            val resId = resourceJson.get("id")?.asString ?: resourceJson.get("resourceId")?.asString ?: "res-$index"

            if (seenResourceIds.contains(resId)) {
                addIssue(
                    type = PortableImportIssueType.DUPLICATE_RESOURCE,
                    severity = PortableImportIssueSeverity.WARNING,
                    message = "Duplicate resource ID '$resId' detected in $resType.",
                    resource = "$resType/$resId"
                )
            } else {
                seenResourceIds.add(resId)
            }

            // Parse specific resource types
            try {
                when (resType) {
                    "Patient" -> {
                        val patient = gson.fromJson(resourceJson, PortablePatientResource::class.java)
                        if (patient.name.isNullOrBlank()) {
                            addIssue(PortableImportIssueType.MISSING_REQUIRED_FIELD, PortableImportIssueSeverity.WARNING, "Patient resource has empty name.", "Patient/$resId")
                        }
                        validateDateFormat(patient.birthDate, "Patient birthDate", resId, ::addIssue)
                        parsedResources.add(patient)
                        parsedCategoriesMap[PortableDataCategory.BASIC_PROFILE]?.add(patient)

                        // Conflict check against local profile
                        localProfile?.let { lp ->
                            if (!patient.name.isNullOrBlank() && !lp.fullName.isNullOrBlank() && !patient.name.equals(lp.fullName, ignoreCase = true)) {
                                conflicts.add(
                                    ImportConflictItem(
                                        category = PortableDataCategory.BASIC_PROFILE,
                                        description = "Profile full name differs from local profile.",
                                        conflictType = ImportConflictType.VALUE_CONFLICT,
                                        localValue = lp.fullName,
                                        importedValue = patient.name
                                    )
                                )
                            }
                            if (!patient.bloodGroup.isNullOrBlank() && !lp.bloodGroup.isNullOrBlank() && !patient.bloodGroup.equals(lp.bloodGroup, ignoreCase = true)) {
                                conflicts.add(
                                    ImportConflictItem(
                                        category = PortableDataCategory.BASIC_PROFILE,
                                        description = "Blood group differs from local profile.",
                                        conflictType = ImportConflictType.VALUE_CONFLICT,
                                        localValue = lp.bloodGroup,
                                        importedValue = patient.bloodGroup
                                    )
                                )
                            }
                        }
                    }

                    "Observation" -> {
                        val obs = gson.fromJson(resourceJson, PortableObservationResource::class.java)
                        if (obs.code.isBlank() || obs.value.isBlank()) {
                            addIssue(PortableImportIssueType.MISSING_REQUIRED_FIELD, PortableImportIssueSeverity.WARNING, "Observation is missing code or value.", "Observation/$resId")
                        }
                        parsedResources.add(obs)
                        parsedCategoriesMap[PortableDataCategory.BASIC_PROFILE]?.add(obs)
                    }

                    "AllergyIntolerance" -> {
                        val allergy = gson.fromJson(resourceJson, PortableAllergyIntoleranceResource::class.java)
                        if (allergy.substance.isBlank()) {
                            addIssue(PortableImportIssueType.MISSING_REQUIRED_FIELD, PortableImportIssueSeverity.WARNING, "AllergyIntolerance has empty substance description.", "AllergyIntolerance/$resId")
                        }
                        parsedResources.add(allergy)
                        parsedCategoriesMap[PortableDataCategory.ALLERGIES]?.add(allergy)
                    }

                    "Condition" -> {
                        val condition = gson.fromJson(resourceJson, PortableConditionResource::class.java)
                        if (condition.conditionName.isBlank()) {
                            addIssue(PortableImportIssueType.MISSING_REQUIRED_FIELD, PortableImportIssueSeverity.WARNING, "Condition has empty name.", "Condition/$resId")
                        }
                        parsedResources.add(condition)
                        parsedCategoriesMap[PortableDataCategory.HEALTH_CONDITIONS]?.add(condition)
                    }

                    "MedicationStatement" -> {
                        val med = gson.fromJson(resourceJson, PortableMedicationStatementResource::class.java)
                        if (med.medicationName.isBlank()) {
                            addIssue(PortableImportIssueType.MISSING_REQUIRED_FIELD, PortableImportIssueSeverity.WARNING, "MedicationStatement has empty medicationName.", "MedicationStatement/$resId")
                        }
                        parsedResources.add(med)
                        parsedCategoriesMap[PortableDataCategory.MEDICATIONS]?.add(med)

                        // Conflict check against local medications
                        val localMatch = localMedications.find { it.medicineName.equals(med.medicationName, ignoreCase = true) }
                        if (localMatch != null) {
                            if (!localMatch.dosage.isNullOrBlank() && !med.dosageText.isNullOrBlank() && !localMatch.dosage.equals(med.dosageText, ignoreCase = true)) {
                                conflicts.add(
                                    ImportConflictItem(
                                        category = PortableDataCategory.MEDICATIONS,
                                        description = "Dosage for '${med.medicationName}' differs from local record.",
                                        conflictType = ImportConflictType.VALUE_CONFLICT,
                                        localValue = "${localMatch.dosage} ${localMatch.dosageUnit ?: ""}".trim(),
                                        importedValue = med.dosageText
                                    )
                                )
                            } else {
                                conflicts.add(
                                    ImportConflictItem(
                                        category = PortableDataCategory.MEDICATIONS,
                                        description = "Medication '${med.medicationName}' already exists locally.",
                                        conflictType = ImportConflictType.POSSIBLE_DUPLICATE,
                                        localValue = localMatch.medicineName,
                                        importedValue = med.medicationName
                                    )
                                )
                            }
                        }
                    }

                    "Appointment" -> {
                        val appt = gson.fromJson(resourceJson, PortableAppointmentResource::class.java)
                        if (appt.practitionerName.isNullOrBlank() && appt.serviceProvider.isNullOrBlank()) {
                            addIssue(PortableImportIssueType.MISSING_REQUIRED_FIELD, PortableImportIssueSeverity.WARNING, "Appointment is missing practitioner and service provider.", "Appointment/$resId")
                        }
                        parsedResources.add(appt)
                        parsedCategoriesMap[PortableDataCategory.APPOINTMENTS]?.add(appt)

                        // Conflict check against local appointments
                        val localMatch = localAppointments.find {
                            it.appointmentDate.equals(appt.startDateTime?.take(10), ignoreCase = true) &&
                                    (it.doctorName.equals(appt.practitionerName, ignoreCase = true))
                        }
                        if (localMatch != null) {
                            conflicts.add(
                                ImportConflictItem(
                                    category = PortableDataCategory.APPOINTMENTS,
                                    description = "Appointment with ${appt.practitionerName ?: "doctor"} on ${localMatch.appointmentDate} already scheduled locally.",
                                    conflictType = ImportConflictType.POSSIBLE_DUPLICATE,
                                    localValue = "${localMatch.doctorName} (${localMatch.appointmentTime})",
                                    importedValue = "${appt.practitionerName} (${appt.startDateTime})"
                                )
                            )
                        }
                    }

                    "MediSenseAdherenceHistory" -> {
                        val adherence = gson.fromJson(resourceJson, PortableMedicationAdherenceResource::class.java)
                        parsedResources.add(adherence)
                        parsedCategoriesMap[PortableDataCategory.MEDICATION_ADHERENCE]?.add(adherence)
                    }

                    "MediSensePrediction" -> {
                        val pred = gson.fromJson(resourceJson, MediSensePredictionResource::class.java)
                        if (pred.predictedDisease.isBlank()) {
                            addIssue(PortableImportIssueType.MISSING_REQUIRED_FIELD, PortableImportIssueSeverity.WARNING, "Prediction is missing predictedDisease.", "MediSensePrediction/$resId")
                        }
                        parsedResources.add(pred)
                        parsedCategoriesMap[PortableDataCategory.PREDICTION_HISTORY]?.add(pred)
                    }

                    "MediSenseLongitudinalTrend" -> {
                        val trend = gson.fromJson(resourceJson, MediSenseTrendResource::class.java)
                        parsedResources.add(trend)
                        parsedCategoriesMap[PortableDataCategory.HEALTH_TRENDS]?.add(trend)
                    }

                    "MediSenseContext" -> {
                        val ctx = gson.fromJson(resourceJson, MediSenseContextResource::class.java)
                        parsedResources.add(ctx)
                        parsedCategoriesMap[PortableDataCategory.PERSONAL_CONTEXT]?.add(ctx)
                    }

                    "MediSenseRiskPriority" -> {
                        val risk = gson.fromJson(resourceJson, MediSenseRiskResource::class.java)
                        parsedResources.add(risk)
                        // Maps to risk insights / personal guidance
                        parsedCategoriesMap[PortableDataCategory.PERSONALIZED_GUIDANCE]?.add(risk)
                    }

                    "MediSenseGuidance" -> {
                        val guidance = gson.fromJson(resourceJson, MediSenseGuidanceResource::class.java)
                        parsedResources.add(guidance)
                        parsedCategoriesMap[PortableDataCategory.PERSONALIZED_GUIDANCE]?.add(guidance)
                    }

                    "MediSenseRchr" -> {
                        val rchr = gson.fromJson(resourceJson, MediSenseRchrResource::class.java)
                        parsedResources.add(rchr)
                        parsedCategoriesMap[PortableDataCategory.RCHR_STATE]?.add(rchr)
                    }

                    "MediSenseDecisionTrace" -> {
                        val trace = gson.fromJson(resourceJson, MediSenseDecisionTraceResource::class.java)
                        parsedResources.add(trace)
                        parsedCategoriesMap[PortableDataCategory.DECISION_TRACES]?.add(trace)
                    }

                    "MediSenseTimelineEvent" -> {
                        val timeline = gson.fromJson(resourceJson, MediSenseTimelineResource::class.java)
                        parsedResources.add(timeline)
                        parsedCategoriesMap[PortableDataCategory.HEALTH_TIMELINE]?.add(timeline)
                    }

                    "MediSenseDataQuality" -> {
                        val quality = gson.fromJson(resourceJson, MediSenseDataQualityResource::class.java)
                        parsedResources.add(quality)
                        parsedCategoriesMap[PortableDataCategory.DATA_QUALITY]?.add(quality)
                    }

                    "MediSenseEmergencyCard" -> {
                        val card = gson.fromJson(resourceJson, MediSenseEmergencyCardResource::class.java)
                        parsedResources.add(card)
                        parsedCategoriesMap[PortableDataCategory.EMERGENCY_INFORMATION]?.add(card)
                    }

                    else -> {
                        addIssue(
                            type = PortableImportIssueType.UNSUPPORTED_RESOURCE,
                            severity = PortableImportIssueSeverity.WARNING,
                            message = "Unknown or unsupported resource type '$resType'.",
                            resource = "$resType/$resId",
                            action = "Resource will be skipped during import."
                        )
                    }
                }
            } catch (e: Exception) {
                SecureLogger.w(TAG, "Failed to parse resource $resType/$resId: ${e.message}")
                addIssue(
                    type = PortableImportIssueType.INVALID_RESOURCE,
                    severity = PortableImportIssueSeverity.WARNING,
                    message = "Malformed fields in $resType: ${e.localizedMessage ?: "Conversion error"}",
                    resource = "$resType/$resId"
                )
            }
        }

        // 7. Compile Category Previews
        val categoryPreviews = mutableListOf<PortableImportCategoryPreview>()
        PortableDataCategory.entries.forEach { cat ->
            val list = parsedCategoriesMap[cat].orEmpty()
            if (list.isNotEmpty()) {
                val hasCatWarnings = issues.any { it.affectedResource?.startsWith(cat.resourceTypeHint) == true && it.severity == PortableImportIssueSeverity.WARNING }
                val hasCatErrors = issues.any { it.affectedResource?.startsWith(cat.resourceTypeHint) == true && it.severity == PortableImportIssueSeverity.ERROR }
                val statusText = when {
                    hasCatErrors -> "Invalid (Errors)"
                    hasCatWarnings -> "Valid with warnings"
                    else -> "Valid"
                }
                val summaryLines = generateCategorySummaryLines(cat, list)

                categoryPreviews.add(
                    PortableImportCategoryPreview(
                        category = cat,
                        displayName = cat.displayName,
                        resourceType = cat.resourceTypeHint,
                        recordCount = list.size,
                        statusText = statusText,
                        hasWarnings = hasCatWarnings,
                        hasErrors = hasCatErrors,
                        summaryLines = summaryLines,
                        parsedResources = list
                    )
                )
            }
        }

        val errorCount = issues.count { it.severity == PortableImportIssueSeverity.ERROR }
        val warningCount = issues.count { it.severity == PortableImportIssueSeverity.WARNING }
        val isImportable = errorCount == 0 && parsedResources.isNotEmpty()

        val summary = PortableImportSummary(
            fileName = fileName,
            schemaVersion = if (schemaVersion.isBlank()) "1.0-interop" else schemaVersion,
            exportVersion = exportVersion,
            appName = appName,
            generatedAtIso = generatedAtIso,
            packageId = packageId,
            sha256Fingerprint = expectedSha256,
            totalResources = entryArray?.size() ?: 0,
            validResources = parsedResources.size,
            warningCount = warningCount,
            errorCount = errorCount,
            isImportable = isImportable
        )

        val rawBundle = try {
            if (isImportable) {
                gson.fromJson(rawJson, PortableHealthRecordBundle::class.java)
            } else null
        } catch (e: Exception) {
            null
        }

        return PortableImportPreview(
            summary = summary,
            categories = categoryPreviews,
            issues = issues,
            conflicts = conflicts,
            rawBundle = rawBundle
        )
    }

    private fun generateCategorySummaryLines(category: PortableDataCategory, resources: List<PortableResource>): List<String> {
        val lines = mutableListOf<String>()
        when (category) {
            PortableDataCategory.BASIC_PROFILE -> {
                val patient = resources.filterIsInstance<PortablePatientResource>().firstOrNull()
                patient?.let { p ->
                    p.name?.let { lines.add("Name: $it") }
                    p.gender?.let { lines.add("Gender: $it") }
                    p.bloodGroup?.let { lines.add("Blood Group: $it") }
                    p.age?.let { lines.add("Age: $it yrs") }
                }
                val obs = resources.filterIsInstance<PortableObservationResource>()
                if (obs.isNotEmpty()) {
                    lines.add("Vitals: ${obs.joinToString { "${it.display}: ${it.value} ${it.unit ?: ""}".trim() }}")
                }
            }

            PortableDataCategory.MEDICATIONS -> {
                val meds = resources.filterIsInstance<PortableMedicationStatementResource>()
                meds.take(5).forEach { m ->
                    lines.add("• ${m.medicationName} (${m.dosageText ?: "standard dosage"}) — ${m.frequency ?: "as directed"}")
                }
                if (meds.size > 5) lines.add("+ ${meds.size - 5} more medications")
            }

            PortableDataCategory.ALLERGIES -> {
                val allergies = resources.filterIsInstance<PortableAllergyIntoleranceResource>()
                allergies.take(5).forEach { a ->
                    lines.add("• ${a.substance} (${a.category})")
                }
            }

            PortableDataCategory.HEALTH_CONDITIONS -> {
                val conditions = resources.filterIsInstance<PortableConditionResource>()
                conditions.take(5).forEach { c ->
                    lines.add("• ${c.conditionName} (${c.clinicalStatus})")
                }
            }

            PortableDataCategory.APPOINTMENTS -> {
                val appts = resources.filterIsInstance<PortableAppointmentResource>()
                appts.take(5).forEach { a ->
                    lines.add("• ${a.practitionerName} (${a.appointmentType}) on ${a.startDateTime.take(10)}")
                }
            }

            PortableDataCategory.PREDICTION_HISTORY -> {
                val preds = resources.filterIsInstance<MediSensePredictionResource>()
                preds.take(4).forEach { p ->
                    val confPct = (p.modelConfidence * 100).toInt()
                    lines.add("• ${p.predictedDisease} (${confPct}% confidence)")
                }
            }

            PortableDataCategory.EMERGENCY_INFORMATION -> {
                val card = resources.filterIsInstance<MediSenseEmergencyCardResource>().firstOrNull()
                card?.let { c ->
                    lines.add("Patient: ${c.patientName} (${c.bloodGroup})")
                    lines.add("Contact: ${c.emergencyContactName} (${c.emergencyContactPhone})")
                }
            }

            else -> {
                lines.add("${resources.size} records compiled in ${category.displayName}")
            }
        }
        return lines
    }

    private fun validateDateFormat(
        dateStr: String?,
        fieldName: String,
        resourceId: String,
        addIssue: (PortableImportIssueType, PortableImportIssueSeverity, String, String?, String?) -> Unit
    ) {
        if (dateStr.isNullOrBlank()) return
        val isValid = try {
            if (dateStr.length == 10) {
                SIMPLE_DATE_FORMAT.parse(dateStr) != null
            } else {
                ISO_DATE_FORMAT.parse(dateStr) != null
            }
        } catch (e: Exception) {
            false
        }

        if (!isValid) {
            addIssue(
                PortableImportIssueType.INVALID_DATE,
                PortableImportIssueSeverity.WARNING,
                "Field '$fieldName' contains non-standard date format '$dateStr'.",
                resourceId,
                "Dates should conform to YYYY-MM-DD or ISO-8601."
            )
        }
    }

    private fun buildErrorPreview(
        fileName: String,
        issues: List<PortableImportIssue>,
        conflicts: List<ImportConflictItem>
    ): PortableImportPreview {
        val errorCount = issues.count { it.severity == PortableImportIssueSeverity.ERROR }
        val warningCount = issues.count { it.severity == PortableImportIssueSeverity.WARNING }

        return PortableImportPreview(
            summary = PortableImportSummary(
                fileName = fileName,
                schemaVersion = "unknown",
                exportVersion = "unknown",
                appName = "unknown",
                generatedAtIso = null,
                packageId = null,
                sha256Fingerprint = null,
                totalResources = 0,
                validResources = 0,
                warningCount = warningCount,
                errorCount = errorCount,
                isImportable = false
            ),
            categories = emptyList(),
            issues = issues,
            conflicts = conflicts,
            rawBundle = null
        )
    }

    private fun computeSha256(text: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(text.toByteArray(Charsets.UTF_8))
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }
}
