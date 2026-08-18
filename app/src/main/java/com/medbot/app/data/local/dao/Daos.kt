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
}

@Dao
interface RagDao {
    @Query("SELECT * FROM rag_documents ORDER BY indexedAt DESC")
    fun getDocuments(): Flow<List<RagDocumentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(doc: RagDocumentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<DocChunkEntity>)

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
    @Query("SELECT * FROM drugs_db WHERE name LIKE '%' || :query || '%' OR genericName LIKE '%' || :query || '%' OR indication LIKE '%' || :query || '%'")
    fun searchDrugs(query: String): Flow<List<DrugEntity>>

    @Query("SELECT * FROM drugs_db WHERE LOWER(name) = LOWER(:name) OR LOWER(genericName) = LOWER(:name) LIMIT 1")
    suspend fun getDrugByName(name: String): DrugEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDrugs(drugs: List<DrugEntity>)

    @Query("SELECT * FROM drug_interactions_db WHERE (LOWER(drugA) = LOWER(:drugA) AND LOWER(drugB) = LOWER(:drugB)) OR (LOWER(drugA) = LOWER(:drugB) AND LOWER(drugB) = LOWER(:drugA))")
    suspend fun findInteraction(drugA: String, drugB: String): List<DrugInteractionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInteractions(interactions: List<DrugInteractionEntity>)

    @Query("SELECT * FROM skin_remedies_db WHERE LOWER(:condition) LIKE '%' || LOWER(conditionKeywords) || '%' LIMIT 1")
    suspend fun findSkinRemedy(condition: String): SkinRemedyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSkinRemedies(remedies: List<SkinRemedyEntity>)

    @Query("SELECT COUNT(*) FROM drugs_db")
    suspend fun getDrugCount(): Int
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

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteReminder(id: String)

    @Query("SELECT COUNT(*) FROM lab_tests_db")
    suspend fun getLabTestCount(): Int
}
