package com.medbot.app.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.medbot.app.data.local.dao.*
import com.medbot.app.data.local.entities.*
import com.medbot.app.data.local.seed.ClinicalDataSeeder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        RagDocumentEntity::class,
        DocChunkEntity::class,
        SkinRecordEntity::class,
        DrugEntity::class,
        DrugInteractionEntity::class,
        LabTestEntity::class,
        SkinRemedyEntity::class,
        HealthMetricEntity::class,
        ReminderEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MedBotDatabase : RoomDatabase() {

    abstract fun chatDao(): ChatDao
    abstract fun ragDao(): RagDao
    abstract fun skinDao(): SkinDao
    abstract fun drugDao(): DrugDao
    abstract fun healthToolsDao(): HealthToolsDao

    companion object {
        @Volatile
        private var INSTANCE: MedBotDatabase? = null

        fun getDatabase(context: Context): MedBotDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MedBotDatabase::class.java,
                    "medbot_local.db"
                )
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Seed clinical drug database, lab tests, and remedies
                            CoroutineScope(Dispatchers.IO).launch {
                                val database = getDatabase(context)
                                ClinicalDataSeeder.seedInitialData(
                                    database.drugDao(),
                                    database.healthToolsDao()
                                )
                            }
                        }
                    })
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
