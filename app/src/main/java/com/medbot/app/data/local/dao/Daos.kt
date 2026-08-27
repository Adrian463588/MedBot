package com.medbot.app.data.local.dao

import androidx.room.*
import com.medbot.app.data.local.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    fun getSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): ChatSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSessionEntity)

    @Query("UPDATE chat_sessions SET title = :title, updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun updateSessionTitle(sessionId: String, title: String, updatedAt: Long)

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun getMessages(sessionId: String): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun clearMessages(sessionId: String)

    @Query("DELETE FROM chat_sessions")
    suspend fun deleteAllSessions()

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAllMessages()
}

@Dao
interface RagDao {
    @Query("SELECT * FROM rag_documents ORDER BY indexedAt DESC")
    fun getDocuments(): Flow<List<RagDocumentEntity>>

    @Query("SELECT * FROM rag_documents WHERE fileUri = :fileUri LIMIT 1")
    suspend fun getDocumentByFileUri(fileUri: String): RagDocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(doc: RagDocumentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<DocChunkEntity>)

    @Query("DELETE FROM doc_chunks WHERE docId = :docId")
    suspend fun deleteChunksForDocument(docId: String)

    /** Promotes a fully embedded document and its chunks as one Room transaction. */
    @Transaction
    suspend fun insertDocumentWithChunks(
        document: RagDocumentEntity,
        chunks: List<DocChunkEntity>
    ) {
        // Reindexing the same source must not leave chunks from an older
        // corpus/version behind. The transaction keeps the old index intact
        // if either delete or insert fails.
        deleteChunksForDocument(document.id)
        insertDocument(document)
        insertChunks(chunks)
    }

    /** Replaces an older source only after the new document is fully prepared. */
    @Transaction
    suspend fun replaceDocumentWithChunks(
        previousDocumentId: String?,
        document: RagDocumentEntity,
        chunks: List<DocChunkEntity>
    ) {
        if (previousDocumentId != null && previousDocumentId != document.id) {
            deleteChunksForDocument(previousDocumentId)
            deleteDocument(previousDocumentId)
        }
        deleteChunksForDocument(document.id)
        insertDocument(document)
        insertChunks(chunks)
    }

    @Query("DELETE FROM rag_documents WHERE id = :docId")
    suspend fun deleteDocument(docId: String)

    @Query("SELECT * FROM doc_chunks")
    suspend fun getAllChunks(): List<DocChunkEntity>

    @Query("SELECT COUNT(*) FROM doc_chunks")
    suspend fun getChunkCount(): Int
}

@Dao
interface SkinDao {
    @Query("SELECT * FROM skin_records ORDER BY createdAt DESC")
    fun getAllRecords(): Flow<List<SkinRecordEntity>>

    @Query("SELECT * FROM skin_records WHERE bodyPart = :bodyPart ORDER BY createdAt DESC")
    fun getRecordsByBodyPart(bodyPart: String): Flow<List<SkinRecordEntity>>

    @Query("SELECT * FROM skin_records WHERE id = :id LIMIT 1")
    suspend fun getRecordById(id: String): SkinRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: SkinRecordEntity)

    @Query("DELETE FROM skin_records WHERE id = :id")
    suspend fun deleteRecord(id: String)
}

@Dao
interface DrugDao {
    @Query("SELECT * FROM drugs_db WHERE name LIKE '%' || :query || '%' OR genericName LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%' OR dosageForm LIKE '%' || :query || '%' OR strength LIKE '%' || :query || '%' OR indication LIKE '%' || :query || '%' ORDER BY name ASC LIMIT 300")
    fun searchDrugs(query: String): Flow<List<DrugEntity>>

    @Query("SELECT * FROM drugs_db ORDER BY name ASC")
    fun getAllDrugs(): Flow<List<DrugEntity>>

    @Query("SELECT * FROM drugs_db ORDER BY name ASC")
    suspend fun getAllDrugsList(): List<DrugEntity>

    @Query("SELECT * FROM drugs_db WHERE LOWER(name) = LOWER(:name) OR LOWER(genericName) = LOWER(:name) LIMIT 1")
    suspend fun getDrugByName(name: String): DrugEntity?

    @Query("SELECT * FROM drugs_db WHERE LOWER(name) LIKE '%' || LOWER(:query) || '%' OR LOWER(genericName) LIKE '%' || LOWER(:query) || '%' LIMIT 10")
    suspend fun findMatchingDrugs(query: String): List<DrugEntity>

    @Query("SELECT * FROM drugs_db WHERE category = :category ORDER BY name ASC LIMIT 300")
    fun getDrugsByCategory(category: String): Flow<List<DrugEntity>>

    @Query("SELECT DISTINCT category FROM drugs_db WHERE category != '' ORDER BY category ASC")
    fun getAllCategories(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDrugs(drugs: List<DrugEntity>)

    @Query("""
        SELECT * FROM drug_interactions_db
        WHERE (
            (LOWER(:drugA) LIKE '%' || LOWER(drugA) || '%' OR LOWER(drugA) LIKE '%' || LOWER(:drugA) || '%')
            AND (LOWER(:drugB) LIKE '%' || LOWER(drugB) || '%' OR LOWER(drugB) LIKE '%' || LOWER(:drugB) || '%')
        ) OR (
            (LOWER(:drugA) LIKE '%' || LOWER(drugB) || '%' OR LOWER(drugB) LIKE '%' || LOWER(:drugA) || '%')
            AND (LOWER(:drugB) LIKE '%' || LOWER(drugA) || '%' OR LOWER(drugA) LIKE '%' || LOWER(:drugB) || '%')
        )
    """)
    suspend fun findInteraction(drugA: String, drugB: String): List<DrugInteractionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInteractions(interactions: List<DrugInteractionEntity>)

    @Query("SELECT * FROM skin_remedies_db WHERE LOWER(:condition) LIKE '%' || LOWER(conditionKeywords) || '%' LIMIT 1")
    suspend fun findSkinRemedy(condition: String): SkinRemedyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSkinRemedies(remedies: List<SkinRemedyEntity>)

    @Query("SELECT COUNT(*) FROM drugs_db")
    suspend fun getDrugCount(): Int

    @Query("SELECT COUNT(*) FROM drugs_db WHERE name LIKE '&%'")
    suspend fun getDirtyDrugCount(): Int

    @Query("DELETE FROM drugs_db")
    suspend fun clearAllDrugs()

    @Query("SELECT COUNT(*) FROM drug_interactions_db")
    suspend fun getInteractionCount(): Int

    @Query("DELETE FROM drug_interactions_db")
    suspend fun clearAllInteractions()
}

@Dao
interface HealthToolsDao {
    @Query("SELECT * FROM lab_tests_db ORDER BY category ASC, testName ASC")
    fun getAllLabTests(): Flow<List<LabTestEntity>>

    @Query("SELECT * FROM lab_tests_db WHERE category = :category ORDER BY testName ASC")
    fun getLabTestsByCategory(category: String): Flow<List<LabTestEntity>>

    @Query("SELECT * FROM lab_tests_db WHERE LOWER(testName) = LOWER(:name) LIMIT 1")
    suspend fun getLabTestByName(name: String): LabTestEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLabTests(tests: List<LabTestEntity>)

    @Query("SELECT * FROM health_metrics WHERE type = :type ORDER BY timestamp DESC")
    fun getMetrics(type: String): Flow<List<HealthMetricEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetric(metric: HealthMetricEntity)

    @Query("DELETE FROM health_metrics WHERE id = :id")
    suspend fun deleteMetric(id: String)

    @Query("SELECT * FROM reminders ORDER BY timeHour ASC, timeMinute ASC")
    fun getReminders(): Flow<List<ReminderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity)

    @Query("UPDATE reminders SET isEnabled = :enabled WHERE id = :id")
    suspend fun updateReminderStatus(id: String, enabled: Boolean)

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun getReminderById(id: String): ReminderEntity?

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteReminder(id: String)

    @Query("SELECT COUNT(*) FROM lab_tests_db")
    suspend fun getLabTestCount(): Int
}
