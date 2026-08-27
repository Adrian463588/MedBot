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
    version = 7,
    exportSchema = true
)
abstract class MedBotDatabase : RoomDatabase() {

    abstract fun chatDao(): ChatDao
    abstract fun ragDao(): RagDao
    abstract fun skinDao(): SkinDao
    abstract fun drugDao(): DrugDao
    abstract fun healthToolsDao(): HealthToolsDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No schema change in version 2; intentionally preserve all tables and rows.
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE drugs_db ADD COLUMN dosageForm TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE drugs_db ADD COLUMN strength TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN notificationMode TEXT NOT NULL DEFAULT 'SOUND_AND_VIBRATE'")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rag_documents ADD COLUMN embeddingModel TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE rag_documents ADD COLUMN embeddingModelSha256 TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rag_documents ADD COLUMN sourceRole TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE rag_documents ADD COLUMN sourceUrl TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE rag_documents ADD COLUMN revision TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE rag_documents ADD COLUMN recordId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE doc_chunks ADD COLUMN recordId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE doc_chunks ADD COLUMN sourceRole TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE doc_chunks ADD COLUMN sourceUrl TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE doc_chunks ADD COLUMN sourceSha256 TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE doc_chunks ADD COLUMN revision TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE doc_chunks ADD COLUMN evidenceKind TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE doc_chunks ADD COLUMN citationId TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rag_documents ADD COLUMN embeddingVersion TEXT NOT NULL DEFAULT ''")
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
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
