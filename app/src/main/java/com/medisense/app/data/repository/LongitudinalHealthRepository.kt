package com.medisense.app.data.repository

import com.medisense.app.data.local.dao.AppointmentDao
import com.medisense.app.data.local.dao.MedicationDao
import com.medisense.app.data.local.dao.MedicationHistoryDao
import com.medisense.app.data.local.dao.PredictionHistoryDao
import com.medisense.app.data.remote.supabase.AuthService
import com.medisense.app.domain.analytics.TemporalPatternAnalyzer
import com.medisense.app.domain.model.AnalysisPeriod
import com.medisense.app.domain.model.LongitudinalHealthSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LongitudinalHealthRepository @Inject constructor(
    private val predictionHistoryDao: PredictionHistoryDao,
    private val medicationDao: MedicationDao,
    private val medicationHistoryDao: MedicationHistoryDao,
    private val appointmentDao: AppointmentDao,
    private val authService: AuthService
) {

    fun getCurrentUserId(): String {
        return authService.getCurrentUserId() ?: "offline-user"
    }

    /**
     * Reactively observes longitudinal health trends and patterns for a chosen time window.
     * Guaranteed 100% offline-first from local Room database tables.
     */
    fun observeLongitudinalSummary(period: AnalysisPeriod): Flow<LongitudinalHealthSummary> {
        val userId = getCurrentUserId()
        return combine(
            predictionHistoryDao.observePredictionHistory(userId),
            medicationDao.getMedicationsForUser(userId),
            medicationHistoryDao.getHistoryForUser(userId),
            appointmentDao.observeAppointments(userId)
        ) { predictions, medications, medHistory, appointments ->
            TemporalPatternAnalyzer.analyzeLongitudinalHealth(
                userId = userId,
                period = period,
                predictions = predictions,
                medications = medications,
                medicationHistory = medHistory,
                appointments = appointments
            )
        }.flowOn(Dispatchers.IO)
    }
}
