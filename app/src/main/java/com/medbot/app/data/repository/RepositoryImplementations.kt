package com.medbot.app.data.repository

import com.medbot.app.data.ai.LlmInferenceEngine
import com.medbot.app.data.ai.ModelRegistry
import com.medbot.app.data.download.ModelDownloadManager
import com.medbot.app.data.local.dao.*
import com.medbot.app.data.local.entities.*
import com.medbot.app.data.rag.RagOrchestrator
import com.medbot.app.domain.model.*
import com.medbot.app.domain.repository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.InputStream

class ChatRepositoryImpl(
    private val chatDao: ChatDao
) : ChatRepository {

    override fun getSessions(): Flow<List<ChatSession>> {
        return chatDao.getSessions().map { list ->
            list.map { entity ->
                ChatSession(
                    id = entity.id,
                    title = entity.title,
                    activeAgentId = entity.activeAgentId,
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt
                )
            }
        }
    }

    override suspend fun getSessionById(sessionId: String): ChatSession? {
        val entity = chatDao.getSessionById(sessionId) ?: return null
        return ChatSession(
            id = entity.id,
            title = entity.title,
            activeAgentId = entity.activeAgentId,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    override suspend fun createSession(title: String, agentId: String): ChatSession {
        val now = System.currentTimeMillis()
        val session = ChatSession(title = title, activeAgentId = agentId, createdAt = now, updatedAt = now)
        chatDao.insertSession(
            ChatSessionEntity(
                id = session.id,
                title = session.title,
                activeAgentId = session.activeAgentId,
                createdAt = session.createdAt,
                updatedAt = session.updatedAt
            )
        )
        return session
    }

    override suspend fun deleteSession(sessionId: String) {
        chatDao.deleteSession(sessionId)
    }

    override suspend fun updateSessionTitle(sessionId: String, title: String) {
        chatDao.updateSessionTitle(sessionId, title, System.currentTimeMillis())
    }

    override fun getMessages(sessionId: String): Flow<List<ChatMessage>> {
        return chatDao.getMessages(sessionId).map { list ->
            list.map { entity ->
                val urgency = entity.urgencyLevel?.let {
                    try { UrgencyLevel.valueOf(it) } catch (e: Exception) { null }
                }
                ChatMessage(
                    id = entity.id,
                    sessionId = entity.sessionId,
                    text = entity.text,
                    isUser = entity.isUser,
                    agentId = entity.agentId,
                    imageUri = entity.imageUri,
                    citations = parseCitations(entity.citationsJson),
                    urgencyLevel = urgency,
                    createdAt = entity.createdAt
                )
            }
        }
    }

    override suspend fun insertMessage(message: ChatMessage) {
        val citationsJson = serializeCitations(message.citations)
        chatDao.insertMessage(
            ChatMessageEntity(
                id = message.id,
                sessionId = message.sessionId,
                text = message.text,
                isUser = message.isUser,
                agentId = message.agentId,
                imageUri = message.imageUri,
                citationsJson = citationsJson,
                urgencyLevel = message.urgencyLevel?.name,
                createdAt = message.createdAt
            )
        )
        chatDao.updateSessionTitle(message.sessionId, message.text.take(30), System.currentTimeMillis())
    }

    override suspend fun clearMessages(sessionId: String) {
        chatDao.clearMessages(sessionId)
    }

    private fun serializeCitations(citations: List<Citation>): String {
        if (citations.isEmpty()) return ""
        return citations.joinToString("|||") { "${it.documentTitle}###${it.pageNumber}###${it.snippet}###${it.sectionTitle}" }
    }

    private fun parseCitations(json: String): List<Citation> {
        if (json.isBlank()) return emptyList()
        return json.split("|||").mapNotNull { item ->
            val parts = item.split("###")
            if (parts.size >= 3) {
                Citation(
                    documentTitle = parts[0],
                    pageNumber = parts[1].toIntOrNull() ?: 1,
                    snippet = parts[2],
                    sectionTitle = if (parts.size > 3) parts[3] else ""
                )
            } else null
        }
    }
}

class ModelRepositoryImpl(
    private val downloadManager: ModelDownloadManager,
    private val llmEngine: LlmInferenceEngine
) : ModelRepository {

    override val modelLoaded = llmEngine.modelLoaded
    override val activeModelName = llmEngine.activeModelName

    override fun getInstalledModels(): Flow<List<ModelManifest>> {
        return kotlinx.coroutines.flow.flowOf(ModelRegistry.getAllModels())
    }

    override fun getAvailableOnlineModels(): List<ModelManifest> {
        return ModelRegistry.getAllModels()
    }

    override fun getDownloadProgress(modelId: String): Flow<DownloadProgress?> {
        return downloadManager.getDownloadProgressFlow(modelId)
    }

    override suspend fun startDownload(modelId: String) {
        downloadManager.startDownload(modelId)
    }

    override suspend fun pauseDownload(modelId: String) {
        downloadManager.pauseDownload(modelId)
    }

    override suspend fun resumeDownload(modelId: String) {
        downloadManager.startDownload(modelId)
    }

    override suspend fun cancelDownload(modelId: String) {
        downloadManager.cancelDownload(modelId)
    }

    override suspend fun deleteModel(modelId: String) {
        downloadManager.deleteModel(modelId)
    }

    override fun getInstalledModelPath(modelId: String): String? =
        downloadManager.getInstalledModelPath(modelId)

    override suspend fun loadModelToRam(modelPath: String, backend: String): Boolean {
        return llmEngine.loadModel(modelPath, backend)
    }

    override suspend fun unloadModel() {
        llmEngine.unloadModel()
    }

    override fun isModelLoaded(): Boolean = llmEngine.isModelLoaded()

    override fun getActiveModelName(): String? = llmEngine.getActiveModelPath()
}

class RagRepositoryImpl(
    private val orchestrator: RagOrchestrator
) : RagRepository {

    override fun getDocuments(): Flow<List<RagDocument>> = orchestrator.getDocumentsFlow()

    override suspend fun ingestDocument(
        fileName: String,
        fileUri: String,
        mimeType: String,
        inputStream: InputStream
    ): Result<RagDocument> {
        val result = orchestrator.ingestDocument(fileName, fileUri, mimeType, inputStream)
        return if (result.isSuccess) {
            result
        } else {
            Result.failure(result.exceptionOrNull().toDomainRagFailure())
        }
    }

    override suspend fun deleteDocument(docId: String) {
        orchestrator.deleteDocument(docId)
    }

    override suspend fun searchSimilarChunks(query: String, topK: Int): List<SearchResult> {
        return try {
            orchestrator.searchSimilar(query, topK)
        } catch (error: com.medbot.app.data.rag.RagProcessingException) {
            throw com.medbot.app.domain.repository.RagUnavailableException(
                failure = when (error.code) {
                    com.medbot.app.data.rag.RagFailureCode.EMBEDDER_UNAVAILABLE -> com.medbot.app.domain.repository.RagFailure.EMBEDDER_UNAVAILABLE
                    com.medbot.app.data.rag.RagFailureCode.PARSER_UNAVAILABLE -> com.medbot.app.domain.repository.RagFailure.PARSER_UNAVAILABLE
                    com.medbot.app.data.rag.RagFailureCode.INVALID_DOCUMENT -> com.medbot.app.domain.repository.RagFailure.INVALID_DOCUMENT
                },
                message = error.message ?: "RAG capability is unavailable",
                cause = error
            )
        }
    }

    override suspend fun getChunkCount(): Int {
        return orchestrator.getChunkCount()
    }

    private fun Throwable?.toDomainRagFailure(): Throwable {
        val error = this as? com.medbot.app.data.rag.RagProcessingException
        return if (error == null) {
            com.medbot.app.domain.repository.RagUnavailableException(
                com.medbot.app.domain.repository.RagFailure.INVALID_DOCUMENT,
                "RAG document processing failed",
                this
            )
        } else {
            com.medbot.app.domain.repository.RagUnavailableException(
                failure = when (error.code) {
                    com.medbot.app.data.rag.RagFailureCode.EMBEDDER_UNAVAILABLE -> com.medbot.app.domain.repository.RagFailure.EMBEDDER_UNAVAILABLE
                    com.medbot.app.data.rag.RagFailureCode.PARSER_UNAVAILABLE -> com.medbot.app.domain.repository.RagFailure.PARSER_UNAVAILABLE
                    com.medbot.app.data.rag.RagFailureCode.INVALID_DOCUMENT -> com.medbot.app.domain.repository.RagFailure.INVALID_DOCUMENT
                },
                message = error.message ?: "RAG document processing failed",
                cause = error
            )
        }
    }
}

class SkinRepositoryImpl(
    private val skinDao: SkinDao
) : SkinRepository {

    override fun getAllRecords(): Flow<List<SkinRecord>> {
        return skinDao.getAllRecords().map { list -> list.map { mapEntityToDomain(it) } }
    }

    override fun getRecordsByBodyPart(bodyPart: String): Flow<List<SkinRecord>> {
        return skinDao.getRecordsByBodyPart(bodyPart).map { list -> list.map { mapEntityToDomain(it) } }
    }

    override suspend fun getRecordById(id: String): SkinRecord? {
        val entity = skinDao.getRecordById(id) ?: return null
        return mapEntityToDomain(entity)
    }

    override suspend fun saveRecord(record: SkinRecord) {
        val entity = SkinRecordEntity(
            id = record.id,
            bodyPart = record.bodyPart,
            imagePath = record.imagePath,
            asymmetryScore = record.abcdEvaluation.asymmetryScore,
            borderScore = record.abcdEvaluation.borderScore,
            colorScore = record.abcdEvaluation.colorScore,
            diameterMm = record.abcdEvaluation.diameterMm,
            totalRiskScore = record.abcdEvaluation.totalRiskScore,
            riskClassification = record.abcdEvaluation.riskClassification,
            asymmetryDesc = record.abcdEvaluation.asymmetryDescription,
            borderDesc = record.abcdEvaluation.borderDescription,
            colorDesc = record.abcdEvaluation.colorDescription,
            diameterDesc = record.abcdEvaluation.diameterDescription,
            differentialDxJson = record.differentialDiagnoses.joinToString("|||"),
            urgencyLevel = record.urgencyLevel.name,
            clinicalSummary = record.clinicalSummary,
            homeCareAdviceJson = record.homeCareAdvice.joinToString("|||"),
            userNotes = record.userNotes,
            createdAt = record.createdAt
        )
        skinDao.insertRecord(entity)
    }

    override suspend fun deleteRecord(id: String) {
        skinDao.deleteRecord(id)
    }

    private fun mapEntityToDomain(entity: SkinRecordEntity): SkinRecord {
        val abcd = AbcdEvaluation(
            asymmetryScore = entity.asymmetryScore,
            borderScore = entity.borderScore,
            colorScore = entity.colorScore,
            diameterMm = entity.diameterMm,
            totalRiskScore = entity.totalRiskScore,
            riskClassification = entity.riskClassification,
            asymmetryDescription = entity.asymmetryDesc,
            borderDescription = entity.borderDesc,
            colorDescription = entity.colorDesc,
            diameterDescription = entity.diameterDesc
        )
        val urgency = try { UrgencyLevel.valueOf(entity.urgencyLevel) } catch (e: Exception) { UrgencyLevel.INSUFFICIENT_DATA }
        val diffs = if (entity.differentialDxJson.isBlank()) emptyList() else entity.differentialDxJson.split("|||")
        val advice = if (entity.homeCareAdviceJson.isBlank()) emptyList() else entity.homeCareAdviceJson.split("|||")

        return SkinRecord(
            id = entity.id,
            bodyPart = entity.bodyPart,
            imagePath = entity.imagePath,
            abcdEvaluation = abcd,
            differentialDiagnoses = diffs,
            urgencyLevel = urgency,
            clinicalSummary = entity.clinicalSummary,
            homeCareAdvice = advice,
            userNotes = entity.userNotes,
            createdAt = entity.createdAt
        )
    }
}

class DrugRepositoryImpl(
    private val drugDao: DrugDao
) : DrugRepository {

    override fun searchDrugs(query: String): Flow<List<Drug>> {
        return drugDao.searchDrugs(query).map { list ->
            list.map { entity ->
                val alts = if (entity.alternativesJson.isBlank()) emptyList() else {
                    entity.alternativesJson.replace("[", "").replace("]", "").replace("\"", "").split(",").map { it.trim() }
                }
                Drug(
                    name = entity.name,
                    genericName = entity.genericName,
                    category = entity.category,
                    indication = entity.indication,
                    adultDose = entity.adultDose,
                    childDose = entity.childDose,
                    contraindications = entity.contraindications,
                    sideEffects = entity.sideEffects,
                    isOtc = entity.isOtc,
                    affordableAlternatives = alts
                )
            }
        }
    }

    override suspend fun getDrugByName(name: String): Drug? {
        val entity = drugDao.getDrugByName(name) ?: return null
        val alts = if (entity.alternativesJson.isBlank()) emptyList() else {
            entity.alternativesJson.replace("[", "").replace("]", "").replace("\"", "").split(",").map { it.trim() }
        }
        return Drug(
            name = entity.name,
            genericName = entity.genericName,
            category = entity.category,
            indication = entity.indication,
            adultDose = entity.adultDose,
            childDose = entity.childDose,
            contraindications = entity.contraindications,
            sideEffects = entity.sideEffects,
            isOtc = entity.isOtc,
            affordableAlternatives = alts
        )
    }

    override suspend fun checkInteraction(drugNames: List<String>): List<DrugInteraction> {
        val interactions = mutableListOf<DrugInteraction>()
        for (i in drugNames.indices) {
            for (j in (i + 1) until drugNames.size) {
                val found = drugDao.findInteraction(drugNames[i], drugNames[j])
                for (item in found) {
                    val severity = try { InteractionSeverity.valueOf(item.severity) } catch (e: Exception) { InteractionSeverity.MODERATE }
                    interactions.add(
                        DrugInteraction(
                            drugA = item.drugA,
                            drugB = item.drugB,
                            severity = severity,
                            description = item.description,
                            recommendation = item.recommendation
                        )
                    )
                }
            }
        }
        return interactions
    }

    override suspend fun searchSkinRemedy(condition: String): SkinRemedy? {
        val entity = drugDao.findSkinRemedy(condition) ?: return null
        return SkinRemedy(
            conditionKeywords = entity.conditionKeywords,
            naturalRemedy = entity.naturalRemedy,
            otcCream = entity.otcCream,
            referralFlag = entity.referralFlag
        )
    }
}

class HealthToolsRepositoryImpl(
    private val dao: HealthToolsDao
) : HealthToolsRepository {

    override fun getLabTests(category: String?): Flow<List<LabTest>> {
        val flow = if (category.isNullOrBlank()) dao.getAllLabTests() else dao.getLabTestsByCategory(category)
        return flow.map { list ->
            list.map {
                LabTest(
                    testName = it.testName,
                    category = it.category,
                    unit = it.unit,
                    normalLow = it.normalLow,
                    normalHigh = it.normalHigh,
                    interpretationLow = it.interpretationLow,
                    interpretationHigh = it.interpretationHigh,
                    clinicalSignificance = it.clinicalSignificance
                )
            }
        }
    }

    override suspend fun getLabTestByName(name: String): LabTest? {
        val it = dao.getLabTestByName(name) ?: return null
        return LabTest(
            testName = it.testName,
            category = it.category,
            unit = it.unit,
            normalLow = it.normalLow,
            normalHigh = it.normalHigh,
            interpretationLow = it.interpretationLow,
            interpretationHigh = it.interpretationHigh,
            clinicalSignificance = it.clinicalSignificance
        )
    }

    override fun getMetrics(type: MetricType): Flow<List<HealthMetric>> {
        return dao.getMetrics(type.name).map { list ->
            list.map {
                HealthMetric(
                    id = it.id,
                    type = type,
                    valuePrimary = it.valuePrimary,
                    valueSecondary = it.valueSecondary,
                    unit = it.unit,
                    notes = it.notes,
                    timestamp = it.timestamp
                )
            }
        }
    }

    override suspend fun saveMetric(metric: HealthMetric) {
        dao.insertMetric(
            HealthMetricEntity(
                id = metric.id,
                type = metric.type.name,
                valuePrimary = metric.valuePrimary,
                valueSecondary = metric.valueSecondary,
                unit = metric.unit,
                notes = metric.notes,
                timestamp = metric.timestamp
            )
        )
    }

    override suspend fun deleteMetric(id: String) {
        dao.deleteMetric(id)
    }

    override fun getReminders(): Flow<List<Reminder>> {
        return dao.getReminders().map { list ->
            list.map {
                val days = if (it.daysOfWeekCsv.isBlank()) listOf(1,2,3,4,5,6,7) else {
                    it.daysOfWeekCsv.split(",").mapNotNull { d -> d.trim().toIntOrNull() }
                }
                val type = try { ReminderType.valueOf(it.type) } catch (e: Exception) { ReminderType.MEDICATION }
                Reminder(
                    id = it.id,
                    type = type,
                    title = it.title,
                    timeHour = it.timeHour,
                    timeMinute = it.timeMinute,
                    daysOfWeek = days,
                    isEnabled = it.isEnabled,
                    notes = it.notes
                )
            }
        }
    }

    override suspend fun saveReminder(reminder: Reminder) {
        dao.insertReminder(
            ReminderEntity(
                id = reminder.id,
                type = reminder.type.name,
                title = reminder.title,
                timeHour = reminder.timeHour,
                timeMinute = reminder.timeMinute,
                daysOfWeekCsv = reminder.daysOfWeek.joinToString(","),
                isEnabled = reminder.isEnabled,
                notes = reminder.notes
            )
        )
    }

    override suspend fun toggleReminder(id: String, enabled: Boolean) {
        dao.updateReminderStatus(id, enabled)
    }

    override suspend fun deleteReminder(id: String) {
        dao.deleteReminder(id)
    }
}
