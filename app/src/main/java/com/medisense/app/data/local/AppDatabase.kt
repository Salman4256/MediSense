package com.medisense.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.medisense.app.data.local.dao.ExplanationDao
import com.medisense.app.data.local.dao.HealthProfileDao
import com.medisense.app.data.local.dao.PredictionDao
import com.medisense.app.data.local.entity.ExplanationEntity
import com.medisense.app.data.local.entity.HealthProfileEntity
import com.medisense.app.data.local.entity.PredictionEntity
import com.medisense.app.data.local.entity.ChatMessageEntity
import com.medisense.app.data.local.dao.ChatMessageDao

@Database(
    entities = [
        HealthProfileEntity::class, 
        PredictionEntity::class, 
        ExplanationEntity::class,
        ChatMessageEntity::class
    ], 
    version = 4, 
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun healthProfileDao(): HealthProfileDao
    abstract fun predictionDao(): PredictionDao
    abstract fun explanationDao(): ExplanationDao
    abstract fun chatMessageDao(): ChatMessageDao
}
