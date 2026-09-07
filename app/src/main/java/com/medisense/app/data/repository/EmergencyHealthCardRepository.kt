package com.medisense.app.data.repository

import com.medisense.app.data.local.dao.HealthProfileDao
import com.medisense.app.data.local.dao.MedicationDao
import com.medisense.app.data.local.entity.HealthProfileEntity
import com.medisense.app.data.local.entity.MedicationEntity
import com.medisense.app.data.remote.supabase.AuthService
import com.medisense.app.domain.model.EmergencyAllergyInfo
import com.medisense.app.domain.model.EmergencyConditionInfo
import com.medisense.app.domain.model.EmergencyContactInfo
import com.medisense.app.domain.model.EmergencyHealthCard
import com.medisense.app.domain.model.EmergencyHealthNote
import com.medisense.app.domain.model.EmergencyMedicationInfo
import com.medisense.app.domain.model.EmergencyMedicationItem
import com.medisense.app.domain.model.EmergencyPersonalInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-first repository for aggregating critical health info into an EmergencyHealthCard.
 * Combines HealthProfileEntity and active MedicationEntity records without creating duplicate storage.
 */
@Singleton
class EmergencyHealthCardRepository @Inject constructor(
    private val healthProfileDao: HealthProfileDao,
    private val medicationDao: MedicationDao,
    private val authService: AuthService
) {

    fun getCurrentUserId(): String? {
        return authService.getCurrentUserId()
    }

    /**
     * Observes real-time reactive updates to the Emergency Health Card for a specific user.
     */
    fun observeEmergencyHealthCard(userId: String): Flow<EmergencyHealthCard> {
        return combine(
            healthProfileDao.observeHealthProfile(userId),
            medicationDao.getActiveMedicationsForUser(userId)
        ) { profileEntity, activeMedications ->
            buildEmergencyHealthCard(userId, profileEntity, activeMedications)
        }.flowOn(Dispatchers.IO)
    }

    /**
     * One-shot snapshot retrieval of the user's Emergency Health Card.
     */
    suspend fun getEmergencyHealthCard(userId: String): EmergencyHealthCard = withContext(Dispatchers.IO) {
        val profile = healthProfileDao.getHealthProfile(userId)
        val activeMedications = medicationDao.getActiveMedicationsForUserSync(userId)
        buildEmergencyHealthCard(userId, profile, activeMedications)
    }

    private fun buildEmergencyHealthCard(
        userId: String,
        profile: HealthProfileEntity?,
        activeMeds: List<MedicationEntity>
    ): EmergencyHealthCard {
        val fullName = profile?.fullName?.trim().orEmpty()
        val dob = profile?.dateOfBirth?.trim().orEmpty()
        val gender = profile?.gender?.trim().orEmpty()
        val age = EmergencyHealthCard.calculateAge(dob)

        val personalInfo = EmergencyPersonalInfo(
            fullName = fullName,
            dateOfBirth = dob,
            age = age,
            gender = gender
        )

        // Allergies
        val rawAllergies = profile?.allergies?.trim().orEmpty()
        val parsedAllergies = splitStringList(rawAllergies)
        val allergyInfo = EmergencyAllergyInfo(
            allergies = parsedAllergies,
            rawText = rawAllergies
        )

        // Blood Group
        val bloodGroup = profile?.bloodGroup?.trim().orEmpty()

        // Existing Conditions
        val rawConditions = profile?.existingDiseases?.trim().orEmpty()
        val parsedConditions = splitStringList(rawConditions)
        val conditionInfo = EmergencyConditionInfo(
            conditions = parsedConditions,
            rawText = rawConditions
        )

        // Active Medications
        val rawProfileMeds = profile?.currentMedications?.trim().orEmpty()
        val medicationItems = if (activeMeds.isNotEmpty()) {
            activeMeds.map { med ->
                val dosageDisplay = if (med.dosage.isNotBlank()) "${med.dosage} ${med.dosageUnit}".trim() else ""
                EmergencyMedicationItem(
                    name = med.medicineName,
                    dosage = dosageDisplay,
                    frequency = med.frequency,
                    instructions = med.instructions
                )
            }
        } else if (rawProfileMeds.isNotBlank()) {
            splitStringList(rawProfileMeds).map { medName ->
                EmergencyMedicationItem(
                    name = medName,
                    dosage = "",
                    frequency = "",
                    instructions = ""
                )
            }
        } else {
            emptyList()
        }

        val medicationInfo = EmergencyMedicationInfo(
            medications = medicationItems,
            rawFallback = rawProfileMeds
        )

        // Emergency Contact
        val contactName = profile?.emergencyContactName?.trim().orEmpty()
        val contactPhone = profile?.emergencyContactNumber?.trim().orEmpty()
        val contactInfo = EmergencyContactInfo(
            name = contactName,
            phone = contactPhone,
            relationship = ""
        )

        // Notes
        val notesText = profile?.notes?.trim().orEmpty()
        val notesInfo = EmergencyHealthNote(notes = notesText)

        // Completeness calculation
        val hasMedications = medicationItems.isNotEmpty() || rawProfileMeds.isNotBlank()
        val completeness = EmergencyHealthCard.calculateCompleteness(
            fullName = fullName,
            bloodGroup = bloodGroup,
            contactName = contactName,
            contactPhone = contactPhone,
            allergiesText = rawAllergies,
            conditionsText = rawConditions,
            hasMedications = hasMedications
        )

        val lastUpdated = System.currentTimeMillis()

        return EmergencyHealthCard(
            userId = userId,
            personalInfo = personalInfo,
            allergies = allergyInfo,
            bloodGroup = bloodGroup,
            conditions = conditionInfo,
            medications = medicationInfo,
            emergencyContact = contactInfo,
            notes = notesInfo,
            completeness = completeness,
            lastUpdatedTimestamp = lastUpdated
        )
    }

    private fun splitStringList(input: String): List<String> {
        if (input.isBlank()) return emptyList()
        return input.split(',', ';', '\n')
            .map { it.trim().trimStart('•', '-', '*').trim() }
            .filter { it.isNotBlank() }
    }
}
