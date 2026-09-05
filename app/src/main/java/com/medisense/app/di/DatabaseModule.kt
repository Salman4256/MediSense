package com.medisense.app.di

import android.content.Context
import androidx.room.Room
import com.medisense.app.data.local.AppDatabase
import com.medisense.app.data.local.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "medisense_db"
        )
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
    }

    @Provides
    fun provideHealthProfileDao(database: AppDatabase): HealthProfileDao {
        return database.healthProfileDao()
    }

    @Provides
    fun provideConversationDao(database: AppDatabase): ConversationDao {
        return database.conversationDao()
    }

    @Provides
    fun provideChatMessageDao(database: AppDatabase): ChatMessageDao {
        return database.chatMessageDao()
    }

    @Provides
    fun provideMedicationDao(database: AppDatabase): MedicationDao {
        return database.medicationDao()
    }

    @Provides
    fun provideMedicationHistoryDao(database: AppDatabase): MedicationHistoryDao {
        return database.medicationHistoryDao()
    }

    @Provides
    fun provideAppointmentDao(database: AppDatabase): AppointmentDao {
        return database.appointmentDao()
    }

    @Provides
    fun providePredictionHistoryDao(database: AppDatabase): PredictionHistoryDao {
        return database.predictionHistoryDao()
    }

    @Provides
    fun provideSecurityAuditEventDao(database: AppDatabase): SecurityAuditEventDao {
        return database.securityAuditEventDao()
    }
}
