package com.medisense.app.data.sync.remote

import com.medisense.app.data.sync.dto.*
import com.medisense.app.domain.security.SecureLogger
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

interface ISupabaseSyncDataSource {
    suspend fun upsertHealthProfile(profile: HealthProfileDto): Boolean
    suspend fun fetchHealthProfile(userId: String): HealthProfileDto?

    suspend fun upsertMedications(medications: List<MedicationDto>): Boolean
    suspend fun fetchMedications(userId: String, sinceTimestamp: Long): List<MedicationDto>

    suspend fun upsertMedicationHistory(history: List<MedicationHistoryDto>): Boolean
    suspend fun fetchMedicationHistory(userId: String, sinceTimestamp: Long): List<MedicationHistoryDto>

    suspend fun upsertAppointments(appointments: List<AppointmentDto>): Boolean
    suspend fun fetchAppointments(userId: String, sinceTimestamp: Long): List<AppointmentDto>

    suspend fun upsertPredictionHistory(predictions: List<PredictionHistoryDto>): Boolean
    suspend fun fetchPredictionHistory(userId: String, sinceTimestamp: Long): List<PredictionHistoryDto>
}

@Singleton
open class SupabaseSyncDataSource @Inject constructor(
    private val supabaseClient: SupabaseClient
) : ISupabaseSyncDataSource {

    override suspend fun upsertHealthProfile(profile: HealthProfileDto): Boolean = withContext(Dispatchers.IO) {
        try {
            supabaseClient.postgrest["health_profiles"].upsert(profile)
            true
        } catch (e: Exception) {
            SecureLogger.e("SupabaseSync", "Error upserting health profile to cloud", e)
            false
        }
    }

    override suspend fun fetchHealthProfile(userId: String): HealthProfileDto? = withContext(Dispatchers.IO) {
        try {
            supabaseClient.postgrest["health_profiles"]
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeSingleOrNull<HealthProfileDto>()
        } catch (e: Exception) {
            SecureLogger.e("SupabaseSync", "Error fetching health profile from cloud", e)
            null
        }
    }

    override suspend fun upsertMedications(medications: List<MedicationDto>): Boolean = withContext(Dispatchers.IO) {
        if (medications.isEmpty()) return@withContext true
        try {
            supabaseClient.postgrest["medications"].upsert(medications)
            true
        } catch (e: Exception) {
            SecureLogger.e("SupabaseSync", "Error upserting medications to cloud", e)
            false
        }
    }

    override suspend fun fetchMedications(userId: String, sinceTimestamp: Long): List<MedicationDto> = withContext(Dispatchers.IO) {
        try {
            supabaseClient.postgrest["medications"]
                .select {
                    filter {
                        eq("user_id", userId)
                        if (sinceTimestamp > 0) {
                            gt("updated_at", sinceTimestamp)
                        }
                    }
                }
                .decodeList<MedicationDto>()
        } catch (e: Exception) {
            SecureLogger.e("SupabaseSync", "Error fetching medications from cloud", e)
            emptyList()
        }
    }

    override suspend fun upsertMedicationHistory(history: List<MedicationHistoryDto>): Boolean = withContext(Dispatchers.IO) {
        if (history.isEmpty()) return@withContext true
        try {
            supabaseClient.postgrest["medication_history"].upsert(history)
            true
        } catch (e: Exception) {
            SecureLogger.e("SupabaseSync", "Error upserting medication history to cloud", e)
            false
        }
    }

    override suspend fun fetchMedicationHistory(userId: String, sinceTimestamp: Long): List<MedicationHistoryDto> = withContext(Dispatchers.IO) {
        try {
            supabaseClient.postgrest["medication_history"]
                .select {
                    filter {
                        eq("user_id", userId)
                        if (sinceTimestamp > 0) {
                            gt("updated_at", sinceTimestamp)
                        }
                    }
                }
                .decodeList<MedicationHistoryDto>()
        } catch (e: Exception) {
            SecureLogger.e("SupabaseSync", "Error fetching medication history from cloud", e)
            emptyList()
        }
    }

    override suspend fun upsertAppointments(appointments: List<AppointmentDto>): Boolean = withContext(Dispatchers.IO) {
        if (appointments.isEmpty()) return@withContext true
        try {
            supabaseClient.postgrest["appointments"].upsert(appointments)
            true
        } catch (e: Exception) {
            SecureLogger.e("SupabaseSync", "Error upserting appointments to cloud", e)
            false
        }
    }

    override suspend fun fetchAppointments(userId: String, sinceTimestamp: Long): List<AppointmentDto> = withContext(Dispatchers.IO) {
        try {
            supabaseClient.postgrest["appointments"]
                .select {
                    filter {
                        eq("user_id", userId)
                        if (sinceTimestamp > 0) {
                            gt("updated_at", sinceTimestamp)
                        }
                    }
                }
                .decodeList<AppointmentDto>()
        } catch (e: Exception) {
            SecureLogger.e("SupabaseSync", "Error fetching appointments from cloud", e)
            emptyList()
        }
    }

    override suspend fun upsertPredictionHistory(predictions: List<PredictionHistoryDto>): Boolean = withContext(Dispatchers.IO) {
        if (predictions.isEmpty()) return@withContext true
        try {
            supabaseClient.postgrest["prediction_history"].upsert(predictions)
            true
        } catch (e: Exception) {
            SecureLogger.e("SupabaseSync", "Error upserting prediction history to cloud", e)
            false
        }
    }

    override suspend fun fetchPredictionHistory(userId: String, sinceTimestamp: Long): List<PredictionHistoryDto> = withContext(Dispatchers.IO) {
        try {
            supabaseClient.postgrest["prediction_history"]
                .select {
                    filter {
                        eq("user_id", userId)
                        if (sinceTimestamp > 0) {
                            gt("updated_at", sinceTimestamp)
                        }
                    }
                }
                .decodeList<PredictionHistoryDto>()
        } catch (e: Exception) {
            SecureLogger.e("SupabaseSync", "Error fetching prediction history from cloud", e)
            emptyList()
        }
    }
}
