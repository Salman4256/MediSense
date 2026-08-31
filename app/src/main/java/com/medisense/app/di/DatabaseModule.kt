package com.medisense.app.di

import android.content.Context
import androidx.room.Room
import com.medisense.app.data.local.AppDatabase
import com.medisense.app.data.local.dao.HealthProfileDao
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
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideHealthProfileDao(database: AppDatabase): HealthProfileDao {
        return database.healthProfileDao()
    }

    @Provides
    fun provideMedicationDao(database: AppDatabase): com.medisense.app.data.local.dao.MedicationDao {
        return database.medicationDao()
    }

    @Provides
    fun provideMedicationHistoryDao(database: AppDatabase): com.medisense.app.data.local.dao.MedicationHistoryDao {
        return database.medicationHistoryDao()
    }
}
