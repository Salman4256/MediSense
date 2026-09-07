package com.medisense.app.domain.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Domain models for Module 20: Emergency & Critical Health Access Card.
 * Offline-first, derived representation of health profile and medication data.
 */

data class EmergencyPersonalInfo(
    val fullName: String,
    val dateOfBirth: String,
    val age: Int?,
    val gender: String
)

data class EmergencyAllergyInfo(
    val allergies: List<String>,
    val rawText: String
)

data class EmergencyConditionInfo(
    val conditions: List<String>,
    val rawText: String
)

data class EmergencyMedicationItem(
    val name: String,
    val dosage: String,
    val frequency: String,
    val instructions: String
)

data class EmergencyMedicationInfo(
    val medications: List<EmergencyMedicationItem>,
    val rawFallback: String
)

data class EmergencyContactInfo(
    val name: String,
    val phone: String,
    val relationship: String = ""
)

data class EmergencyHealthNote(
    val notes: String
)

data class EmergencyHealthCardCompleteness(
    val scorePercentage: Int,
    val completedFields: List<String>,
    val missingFields: List<String>
)

data class EmergencyHealthCard(
    val userId: String,
    val personalInfo: EmergencyPersonalInfo,
    val allergies: EmergencyAllergyInfo,
    val bloodGroup: String,
    val conditions: EmergencyConditionInfo,
    val medications: EmergencyMedicationInfo,
    val emergencyContact: EmergencyContactInfo,
    val notes: EmergencyHealthNote,
    val completeness: EmergencyHealthCardCompleteness,
    val lastUpdatedTimestamp: Long
) {
    companion object {
        fun calculateAge(dobIsoString: String?): Int? {
            if (dobIsoString.isNullOrBlank()) return null
            val formats = listOf("yyyy-MM-dd", "yyyy/MM/dd", "dd-MM-yyyy", "dd/MM/yyyy")
            for (format in formats) {
                try {
                    val sdf = SimpleDateFormat(format, Locale.US)
                    sdf.isLenient = false
                    val dobDate = sdf.parse(dobIsoString.trim())
                    if (dobDate != null) {
                        val dobCal = Calendar.getInstance().apply { time = dobDate }
                        val todayCal = Calendar.getInstance()
                        var age = todayCal.get(Calendar.YEAR) - dobCal.get(Calendar.YEAR)
                        if (todayCal.get(Calendar.DAY_OF_YEAR) < dobCal.get(Calendar.DAY_OF_YEAR)) {
                            age--
                        }
                        if (age in 0..130) return age
                    }
                } catch (_: Exception) { }
            }
            return null
        }

        fun calculateCompleteness(
            fullName: String?,
            bloodGroup: String?,
            contactName: String?,
            contactPhone: String?,
            allergiesText: String?,
            conditionsText: String?,
            hasMedications: Boolean
        ): EmergencyHealthCardCompleteness {
            val completed = mutableListOf<String>()
            val missing = mutableListOf<String>()

            // 1. Personal Name
            if (!fullName.isNullOrBlank()) completed.add("Full Name") else missing.add("Full Name")

            // 2. Emergency Contact
            if (!contactName.isNullOrBlank() && !contactPhone.isNullOrBlank()) {
                completed.add("Emergency Contact")
            } else {
                missing.add("Emergency Contact")
            }

            // 3. Blood Group
            if (!bloodGroup.isNullOrBlank() && !bloodGroup.equals("Unknown", ignoreCase = true) && !bloodGroup.equals("Not recorded", ignoreCase = true)) {
                completed.add("Blood Group")
            } else {
                missing.add("Blood Group")
            }

            // 4. Allergies
            if (!allergiesText.isNullOrBlank()) {
                completed.add("Known Allergies")
            } else {
                missing.add("Known Allergies")
            }

            // 5. Medical Conditions
            if (!conditionsText.isNullOrBlank()) {
                completed.add("Medical Conditions")
            } else {
                missing.add("Medical Conditions")
            }

            // 6. Medications
            if (hasMedications) {
                completed.add("Active Medications")
            } else {
                missing.add("Active Medications")
            }

            val total = completed.size + missing.size
            val percentage = if (total > 0) ((completed.size.toDouble() / total) * 100).toInt() else 0

            return EmergencyHealthCardCompleteness(
                scorePercentage = percentage,
                completedFields = completed,
                missingFields = missing
            )
        }

        fun formatAsPlainText(card: EmergencyHealthCard): String {
            val sb = StringBuilder()
            val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            val dateStr = dateFormat.format(Date(card.lastUpdatedTimestamp))

            sb.appendLine("========================================")
            sb.appendLine("       MEDISENSE EMERGENCY HEALTH CARD  ")
            sb.appendLine("========================================")
            sb.appendLine("Last Updated: $dateStr")
            sb.appendLine()

            // 1. Emergency Contact
            sb.appendLine("--- EMERGENCY CONTACT ---")
            if (card.emergencyContact.name.isNotBlank() || card.emergencyContact.phone.isNotBlank()) {
                if (card.emergencyContact.name.isNotBlank()) sb.appendLine("Name: ${card.emergencyContact.name}")
                if (card.emergencyContact.phone.isNotBlank()) sb.appendLine("Phone: ${card.emergencyContact.phone}")
            } else {
                sb.appendLine("No emergency contact recorded.")
            }
            sb.appendLine()

            // 2. Known Allergies
            sb.appendLine("--- KNOWN ALLERGIES ---")
            if (card.allergies.allergies.isNotEmpty()) {
                card.allergies.allergies.forEach { sb.appendLine("• $it") }
            } else if (card.allergies.rawText.isNotBlank()) {
                sb.appendLine(card.allergies.rawText)
            } else {
                sb.appendLine("No known allergies recorded.")
            }
            sb.appendLine()

            // 3. Blood Group
            sb.appendLine("--- BLOOD GROUP ---")
            sb.appendLine(card.bloodGroup.ifBlank { "Not recorded" })
            sb.appendLine()

            // 4. Active Medications
            sb.appendLine("--- ACTIVE MEDICATIONS ---")
            if (card.medications.medications.isNotEmpty()) {
                card.medications.medications.forEach { med ->
                    val dosagePart = if (med.dosage.isNotBlank()) " - ${med.dosage}" else ""
                    val freqPart = if (med.frequency.isNotBlank()) " (${med.frequency})" else ""
                    sb.appendLine("• ${med.name}$dosagePart$freqPart")
                    if (med.instructions.isNotBlank()) {
                        sb.appendLine("  Instructions: ${med.instructions}")
                    }
                }
            } else if (card.medications.rawFallback.isNotBlank()) {
                sb.appendLine(card.medications.rawFallback)
            } else {
                sb.appendLine("No active medications recorded.")
            }
            sb.appendLine()

            // 5. Medical Conditions
            sb.appendLine("--- MEDICAL CONDITIONS ---")
            if (card.conditions.conditions.isNotEmpty()) {
                card.conditions.conditions.forEach { sb.appendLine("• $it") }
            } else if (card.conditions.rawText.isNotBlank()) {
                sb.appendLine(card.conditions.rawText)
            } else {
                sb.appendLine("No medical conditions recorded.")
            }
            sb.appendLine()

            // 6. Personal Information
            sb.appendLine("--- PERSONAL INFORMATION ---")
            sb.appendLine("Name: ${card.personalInfo.fullName.ifBlank { "Not recorded" }}")
            if (card.personalInfo.dateOfBirth.isNotBlank()) {
                val agePart = card.personalInfo.age?.let { " (Age $it)" } ?: ""
                sb.appendLine("DOB: ${card.personalInfo.dateOfBirth}$agePart")
            }
            if (card.personalInfo.gender.isNotBlank()) {
                sb.appendLine("Gender: ${card.personalInfo.gender}")
            }
            sb.appendLine()

            // 7. Important Notes
            if (card.notes.notes.isNotBlank()) {
                sb.appendLine("--- IMPORTANT HEALTH NOTES ---")
                sb.appendLine(card.notes.notes)
                sb.appendLine()
            }

            // 8. Disclaimer
            sb.appendLine("----------------------------------------")
            sb.appendLine("CRITICAL NOTICE & MEDICAL DISCLAIMER:")
            sb.appendLine("This emergency health card is an assistive summary of health records stored locally in MediSense. It does not constitute medical advice or emergency dispatch services. In a life-threatening medical emergency, immediately contact emergency response personnel (e.g. 911 / 112 / local emergency services).")
            sb.appendLine("----------------------------------------")

            return sb.toString()
        }
    }
}
