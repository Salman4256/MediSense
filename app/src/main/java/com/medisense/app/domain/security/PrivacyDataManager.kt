package com.medisense.app.domain.security

import com.medisense.app.data.local.dao.*
import com.medisense.app.data.remote.supabase.AuthService
import com.medisense.app.data.repository.SecurityAuditRepository
import com.medisense.app.domain.model.PrivacyDataCategory
import com.medisense.app.domain.model.PrivacyGovernanceInformation
import com.medisense.app.domain.model.SecurityAuditEventType
import com.medisense.app.notification.AppointmentScheduler
import com.medisense.app.notification.MedicationScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service managing user-scoped local health data clearing, privacy classifications,
 * and governance transparency.
 */
@Singleton
class PrivacyDataManager @Inject constructor(
    private val healthProfileDao: HealthProfileDao,
    private val predictionHistoryDao: PredictionHistoryDao,
    private val medicationDao: MedicationDao,
    private val medicationHistoryDao: MedicationHistoryDao,
    private val appointmentDao: AppointmentDao,
    private val conversationDao: ConversationDao,
    private val chatMessageDao: ChatMessageDao,
    private val securityAuditDao: SecurityAuditEventDao,
    private val appointmentScheduler: AppointmentScheduler,
    private val medicationScheduler: MedicationScheduler,
    private val authService: AuthService,
    private val securityAuditRepository: SecurityAuditRepository
) {

    /**
     * Safely clears all local health records and cached data for the authenticated user.
     * Cancels any scheduled alarms/reminders for this user to avoid dangling notifications.
     */
    suspend fun clearLocalUserData(preserveAuditLog: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        val userId = authService.getCurrentUserId() ?: return@withContext false

        try {
            // 1. Cancel local medication alarms
            val medications = medicationDao.getActiveMedicationsForUserSync(userId)
            for (med in medications) {
                medicationScheduler.cancelReminder(med.id)
            }

            // 2. Cancel local appointment alarms
            val appointments = appointmentDao.getAllUpcomingScheduledAppointmentsSync(System.currentTimeMillis())
                .filter { it.userId == userId }
            for (appt in appointments) {
                appointmentScheduler.cancelReminder(appt)
            }

            // 3. Delete user records from local Room tables
            healthProfileDao.deleteHealthProfileByUserId(userId)
            predictionHistoryDao.deleteAllPredictionHistory(userId)
            medicationDao.deleteAllMedicationsForUser(userId)
            medicationHistoryDao.deleteAllMedicationHistoryForUser(userId)
            appointmentDao.deleteAllAppointmentsForUser(userId)
            conversationDao.deleteAllConversationsForUser(userId)
            chatMessageDao.deleteAllMessagesForUser(userId)

            // 4. Audit event handling
            if (preserveAuditLog) {
                securityAuditRepository.recordEvent(
                    eventType = SecurityAuditEventType.LOCAL_DATA_CLEARED,
                    customDescription = "All local health records and scheduled reminders cleared"
                )
            } else {
                securityAuditDao.deleteAllAuditEventsForUser(userId)
            }

            SecureLogger.i("PrivacyDataManager", "Successfully cleared local data for user: $userId")
            true
        } catch (e: Exception) {
            SecureLogger.e("PrivacyDataManager", "Error clearing local data", e)
            false
        }
    }

    /**
     * Returns the structured privacy data categories for UI transparency.
     */
    fun getPrivacyDataCategories(): List<PrivacyDataCategory> {
        return PrivacyDataCategory.entries
    }

    /**
     * Returns factual privacy governance text.
     */
    fun getGovernanceInfo(): PrivacyGovernanceInformation {
        return PrivacyGovernanceInformation
    }
}
