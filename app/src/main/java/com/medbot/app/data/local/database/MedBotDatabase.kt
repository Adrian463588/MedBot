package com.medbot.app.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.medbot.app.data.local.dao.*
import com.medbot.app.data.local.entities.*

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
    version = 2,
    exportSchema = false
)
abstract class MedBotDatabase : RoomDatabase() {

    abstract fun chatDao(): ChatDao
    abstract fun ragDao(): RagDao
    abstract fun skinDao(): SkinDao
    abstract fun drugDao(): DrugDao
    abstract fun healthToolsDao(): HealthToolsDao

    companion object {
        /**
         * Keeps the version-1 schema and every existing row intact while making
         * the migration path explicit for the version-2 database contract.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No schema change in version 2; intentionally preserve all tables and rows.
            }
        }

        @Volatile
        private var INSTANCE: MedBotDatabase? = null

        fun getDatabase(context: Context): MedBotDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MedBotDatabase::class.java,
                    "medbot_local.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
