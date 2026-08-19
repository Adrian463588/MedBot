package com.medbot.app.domain.repository

import com.medbot.app.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
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
    val modelLoaded: StateFlow<Boolean>
    val activeModelName: StateFlow<String?>
    fun getInstalledModels(): Flow<List<ModelManifest>>
    fun getAvailableOnlineModels(): List<ModelManifest>
    fun getDownloadProgress(modelId: String): Flow<DownloadProgress?>
    suspend fun startDownload(modelId: String)
    suspend fun pauseDownload(modelId: String)
    suspend fun resumeDownload(modelId: String)
    suspend fun cancelDownload(modelId: String)
    suspend fun deleteModel(modelId: String)
    fun getInstalledModelPath(modelId: String): String?
    suspend fun loadModelToRam(modelPath: String, backend: String): Boolean
    suspend fun loadVerifiedModelToRam(modelId: String, backend: String): ModelLoadResult
    suspend fun loadImportedModelToRam(modelUri: String, backend: String): ModelLoadResult
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

/** A SAF document copied to an app-private staging file before parsing. */
data class SafDocumentSource(
    val fileName: String,
    val fileUri: String,
    val mimeType: String,
    val localPath: String
)

/** Owns ContentResolver access and bounded SAF materialization for RAG ingestion. */
interface SafDocumentGateway {
    suspend fun materialize(uri: String): Result<SafDocumentSource>
    suspend fun deleteStagedFile(path: String)
}

/** Owns model SAF metadata and persistable read permission handling. */
interface ModelFileGateway {
    suspend fun displayName(uri: String): Result<String>
    suspend fun takePersistableReadPermission(uri: String): Result<Unit>
}

enum class RagFailure {
    EMBEDDER_UNAVAILABLE,
    PARSER_UNAVAILABLE,
    INVALID_DOCUMENT
}

/** Domain-level boundary for a RAG capability that cannot produce evidence. */
class RagUnavailableException(
    val failure: RagFailure,
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)

interface SkinRepository {
    fun getAllRecords(): Flow<List<SkinRecord>>
    fun getRecordsByBodyPart(bodyPart: String): Flow<List<SkinRecord>>
    suspend fun getRecordById(id: String): SkinRecord?
    suspend fun saveRecord(record: SkinRecord)
    suspend fun deleteRecord(id: String)
}

data class SkinCaptureTarget(val uri: String, val path: String)

/** Owns photo file I/O so Compose only emits user intent and renders state. */
interface SkinMediaGateway {
    suspend fun createCaptureTarget(): SkinCaptureTarget
    suspend fun importImage(uri: String): Result<String>
    suspend fun discard(path: String)
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
