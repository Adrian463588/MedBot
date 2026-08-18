package com.medbot.app.domain.repository

import com.medbot.app.domain.model.*
import kotlinx.coroutines.flow.Flow
import java.io.InputStream

interface ChatRepository {
    fun getSessions(): Flow<List<ChatSession>>
    suspend fun getSessionById(sessionId: String): ChatSession?
    suspend fun createSession(title: String, agentId: String): ChatSession
    suspend fun deleteSession(sessionId: String)
    suspend fun updateSessionTitle(sessionId: String, title: String)
    
    fun getMessages(sessionId: String): Flow<List<ChatMessage>>
    suspend fun insertMessage(message: ChatMessage)
    suspend fun clearMessages(sessionId: String)
}

interface ModelRepository {
    fun getInstalledModels(): Flow<List<ModelManifest>>
    fun getAvailableOnlineModels(): List<ModelManifest>
    fun getDownloadProgress(modelId: String): Flow<DownloadProgress?>
    suspend fun startDownload(modelId: String)
    suspend fun pauseDownload(modelId: String)
    suspend fun resumeDownload(modelId: String)
    suspend fun cancelDownload(modelId: String)
    suspend fun deleteModel(modelId: String)
    suspend fun loadModelToRam(modelPath: String, backend: String): Boolean
    suspend fun unloadModel()
    fun isModelLoaded(): Boolean
    fun getActiveModelName(): String?
}

interface RagRepository {
    fun getDocuments(): Flow<List<RagDocument>>
    suspend fun ingestDocument(
        fileName: String,
        fileUri: String,
        mimeType: String,
        inputStream: InputStream
    ): Result<RagDocument>
    suspend fun deleteDocument(docId: String)
    suspend fun searchSimilarChunks(query: String, topK: Int = 4): List<SearchResult>
    suspend fun getChunkCount(): Int
}

interface SkinRepository {
    fun getAllRecords(): Flow<List<SkinRecord>>
    fun getRecordsByBodyPart(bodyPart: String): Flow<List<SkinRecord>>
    suspend fun getRecordById(id: String): SkinRecord?
    suspend fun saveRecord(record: SkinRecord)
    suspend fun deleteRecord(id: String)
}

interface DrugRepository {
    fun searchDrugs(query: String): Flow<List<Drug>>
    suspend fun getDrugByName(name: String): Drug?
    suspend fun checkInteraction(drugNames: List<String>): List<DrugInteraction>
    suspend fun searchSkinRemedy(condition: String): SkinRemedy?
}

interface HealthToolsRepository {
    fun getLabTests(category: String? = null): Flow<List<LabTest>>
    suspend fun getLabTestByName(name: String): LabTest?
    fun getMetrics(type: MetricType): Flow<List<HealthMetric>>
    suspend fun saveMetric(metric: HealthMetric)
    suspend fun deleteMetric(id: String)
    fun getReminders(): Flow<List<Reminder>>
    suspend fun saveReminder(reminder: Reminder)
    suspend fun toggleReminder(id: String, enabled: Boolean)
    suspend fun deleteReminder(id: String)
}

interface UserPreferencesRepository {
    val personaConfig: Flow<PersonaConfig>
    suspend fun updatePersonaConfig(config: PersonaConfig)
    val safModelFolderUri: Flow<String?>
    suspend fun setSafModelFolderUri(uri: String?)
    val safRagFolderUri: Flow<String?>
    suspend fun setSafRagFolderUri(uri: String?)
    val activeLanguage: Flow<AppLanguage>
    suspend fun setLanguage(language: AppLanguage)
}
