package com.medisense.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.medisense.app.data.local.dao.*
import com.medisense.app.data.local.entity.*

@Database(
    entities = [
        HealthProfileEntity::class,
        PredictionEntity::class,
        ExplanationEntity::class,
        ConversationEntity::class,
        ChatMessageEntity::class,
        MedicationEntity::class,
        MedicationHistoryEntity::class,
        AppointmentEntity::class,
        PredictionHistoryEntity::class,
        SecurityAuditEventEntity::class
    ],
    version = 6,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun healthProfileDao(): HealthProfileDao
    abstract fun predictionDao(): PredictionDao
    abstract fun explanationDao(): ExplanationDao
    abstract fun conversationDao(): ConversationDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun medicationDao(): MedicationDao
    abstract fun medicationHistoryDao(): MedicationHistoryDao
    abstract fun appointmentDao(): AppointmentDao
    abstract fun predictionHistoryDao(): PredictionHistoryDao
    abstract fun securityAuditEventDao(): SecurityAuditEventDao
}
