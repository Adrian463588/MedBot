package com.medbot.app.data.repository

import android.content.Context
import android.util.Log
import com.medbot.app.data.ai.LlmInferenceEngine
import com.medbot.app.data.ai.ModelLoadResult as EngineModelLoadResult
import com.medbot.app.data.ai.ModelRegistry
import com.medbot.app.data.download.ModelDownloadManager
import com.medbot.app.data.local.dao.*
import com.medbot.app.data.local.entities.*
import com.medbot.app.data.rag.RagOrchestrator
import com.medbot.app.domain.model.*
import com.medbot.app.domain.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.InputStreamReader

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

    override suspend fun deleteAllSessions() {
        chatDao.deleteAllMessages()
        chatDao.deleteAllSessions()
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
        if (message.isUser) {
            val session = chatDao.getSessionById(message.sessionId)
            if (session != null && (session.title.isBlank() || session.title.startsWith("New Consultation", ignoreCase = true) || session.title.startsWith("Konsultasi Baru", ignoreCase = true) || session.title.startsWith("<think>"))) {
                val cleanTitle = com.medbot.app.core.common.AiOutputFormatter.cleanSessionTitle(message.text)
                chatDao.updateSessionTitle(message.sessionId, cleanTitle, System.currentTimeMillis())
            }
        }
    }

    override suspend fun clearMessages(sessionId: String) {
        chatDao.clearMessages(sessionId)
    }

    private fun serializeCitations(citations: List<Citation>): String {
        if (citations.isEmpty()) return ""
        return citations.joinToString("|||") {
            listOf(
                it.citationId,
                it.documentTitle,
                it.pageNumber.toString(),
                it.snippet,
                it.sectionTitle,
                it.sourceUrl,
                it.sourceUri,
                it.sourceName,
                it.sourceRole,
                it.sourceSha256,
                it.recordId,
                it.revision,
                it.retrievedAt?.toString().orEmpty()
            )
                .joinToString("###") { field -> field.replace("|||", " ").replace("###", " ") }
        }
    }

    private fun parseCitations(json: String): List<Citation> {
        if (json.isBlank()) return emptyList()
        return json.split("|||").mapNotNull { item ->
            val parts = item.split("###")
            if (parts.size >= 13) {
                Citation(
                    citationId = parts[0],
                    documentTitle = parts[1],
                    pageNumber = parts[2].toIntOrNull() ?: 0,
                    snippet = parts[3],
                    sectionTitle = parts[4],
                    sourceUrl = parts[5],
                    sourceUri = parts[6],
                    sourceName = parts[7],
                    sourceRole = parts[8],
                    sourceSha256 = parts[9],
                    recordId = parts[10],
                    revision = parts[11],
                    retrievedAt = parts[12].toLongOrNull()
                )
            } else if (parts.size >= 3) {
                Citation(
                    documentTitle = parts[0],
                    pageNumber = parts.getOrNull(1)?.toIntOrNull() ?: 0,
                    snippet = parts[2],
                    sectionTitle = parts.getOrNull(3).orEmpty(),
                    sourceUrl = parts.getOrNull(4).orEmpty(),
                    sourceName = parts.getOrNull(5).orEmpty()
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

    override suspend fun startDownload(modelId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            downloadManager.startDownload(modelId)
        }
    }

    override suspend fun pauseDownload(modelId: String) {
        withContext(Dispatchers.IO) {
            downloadManager.pauseDownload(modelId)
        }
    }

    override suspend fun resumeDownload(modelId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            downloadManager.startDownload(modelId)
        }
    }

    override suspend fun cancelDownload(modelId: String) {
        withContext(Dispatchers.IO) {
            downloadManager.cancelDownload(modelId)
        }
    }

    override suspend fun deleteModel(modelId: String) {
        withContext(Dispatchers.IO) {
            downloadManager.deleteModel(modelId)
        }
    }

    override fun getInstalledModelPath(modelId: String): String? =
        downloadManager.getInstalledModelPath(modelId)

    override fun getVerifiedModelManifest(modelId: String): ModelManifest? =
        downloadManager.getVerifiedModelManifest(modelId)

    override suspend fun loadModelToRam(modelPath: String, backend: String): Boolean {
        return llmEngine.loadModel(modelPath, backend)
    }

    override suspend fun loadVerifiedModelToRam(modelId: String, backend: String): ModelLoadResult {
        val manifest = ModelRegistry.getManifestById(modelId)
            ?: return ModelLoadResult.Unavailable("MODEL_UNAVAILABLE", "No verified model manifest is available")
        val effectiveManifest = withContext(Dispatchers.IO) {
            downloadManager.getVerifiedModelManifest(modelId)
        } ?: return ModelLoadResult.Unavailable(
            "MODEL_NOT_READY",
            "The verified model file is not available in SAF storage"
        )
        val modelUri = downloadManager.getInstalledModelPath(modelId)
            ?: return ModelLoadResult.Unavailable(
                "MODEL_NOT_READY",
                "The verified model file is not available in SAF storage"
            )
        return llmEngine.loadModelResult(
            path = modelUri,
            backend = backend,
            expectedSizeBytes = effectiveManifest.sizeBytes,
            expectedSha256 = effectiveManifest.sha256,
            requiresVision = effectiveManifest.requiresVision
        ).toDomainResult()
    }

    override suspend fun loadImportedModelToRam(modelUri: String, backend: String): ModelLoadResult =
        llmEngine.loadImportedModelResult(path = modelUri, backend = backend).toDomainResult()

    override fun hasHuggingFaceToken(): Boolean = downloadManager.hasHuggingFaceToken()

    override suspend fun saveHuggingFaceToken(token: String): Result<Unit> = withContext(Dispatchers.IO) {
        downloadManager.saveHuggingFaceToken(token)
    }

    override fun acceptedHuggingFaceTermsRevision(): String? =
        downloadManager.acceptedHuggingFaceTermsRevision()

    override suspend fun acceptHuggingFaceTerms(sourceRevision: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            downloadManager.acceptHuggingFaceTerms(sourceRevision)
        }

    override suspend fun restoreHuggingFaceAccessFromSaf(treeUri: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            downloadManager.restoreHuggingFaceAccessFromSaf(treeUri)
        }

    override suspend fun persistHuggingFaceAccessToSaf(treeUri: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            downloadManager.persistHuggingFaceAccessToSaf(treeUri)
        }

    override suspend fun hasHuggingFaceTokenBackup(treeUri: String): Boolean =
        withContext(Dispatchers.IO) {
            downloadManager.hasHuggingFaceTokenBackup(treeUri)
        }

    override suspend fun clearHuggingFaceToken(): Result<Unit> = withContext(Dispatchers.IO) {
        downloadManager.clearHuggingFaceToken()
    }

    override suspend fun unloadModel() {
        llmEngine.unloadModel()
    }

    override fun isModelLoaded(): Boolean = llmEngine.isModelLoaded()

    override fun getActiveModelName(): String? = llmEngine.getActiveModelPath()

    private fun EngineModelLoadResult.toDomainResult(): ModelLoadResult = when (this) {
        is EngineModelLoadResult.Loaded -> ModelLoadResult.Loaded(
            sourceUri = path,
            backend = backend,
            visionCapability = if (supportsVision) {
                VisionCapability.REQUIRED
            } else {
                VisionCapability.NOT_REQUIRED
            }
        )
        is EngineModelLoadResult.Unavailable -> ModelLoadResult.Unavailable(code.name, message)
    }
}

class RagRepositoryImpl(
    private val orchestrator: RagOrchestrator,
    private val bundledKnowledgeSeeder: com.medbot.app.data.rag.BundledKnowledgeSeeder
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

    override suspend fun awaitIndexedData(timeoutMillis: Long): Boolean {
        if (orchestrator.getChunkCount() > 0) return true
        val terminal = kotlinx.coroutines.withTimeoutOrNull(timeoutMillis) {
            bundledKnowledgeSeeder.state.first { state ->
                state is com.medbot.app.data.rag.BundledKnowledgeState.Ready ||
                    state is com.medbot.app.data.rag.BundledKnowledgeState.Failed
            }
        }
        return terminal is com.medbot.app.data.rag.BundledKnowledgeState.Ready &&
            orchestrator.getChunkCount() > 0
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
    private val context: Context,
    private val drugDao: DrugDao
) : DrugRepository {

    override fun searchDrugs(query: String): Flow<List<Drug>> {
        val trimmed = query.trim()
        return drugDao.getAllDrugs().map { list ->
            val domainList = list.map { it.toDomain() }
            if (trimmed.isBlank()) {
                domainList.take(100)
            } else {
                com.medbot.app.domain.util.DrugSimilarityMatcher.searchAndRank(trimmed, domainList)
            }
        }
    }

    override fun getDrugsByCategory(category: String): Flow<List<Drug>> {
        return drugDao.getDrugsByCategory(category).map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getAllCategories(): Flow<List<String>> {
        return drugDao.getAllCategories()
    }

    override suspend fun getDrugByName(name: String): Drug? {
        return drugDao.getDrugByName(name)?.toDomain()
    }

    override suspend fun findMatchingDrugs(query: String): List<Drug> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return emptyList()
        val all = drugDao.getAllDrugsList().map { it.toDomain() }
        return com.medbot.app.domain.util.DrugSimilarityMatcher.searchAndRank(trimmed, all, maxResults = 10)
    }

    override suspend fun getDrugCount(): Int {
        return drugDao.getDrugCount()
    }

    override suspend fun getMedicationKnowledgeStatus(): MedicationKnowledgeStatus {
        val count = drugDao.getDrugCount()
        return MedicationKnowledgeStatus(
            productCatalogAvailable = count > 0,
            productCatalogCount = count,
            monographAvailable = false,
            interactionDatasetAvailable = false,
            compoundingProtocolAvailable = false,
            provenance = "Local product catalogue only; no verified monograph, interaction dataset, or compounding protocol is bundled"
        )
    }

    override suspend fun checkInteraction(drugNames: List<String>): List<DrugInteraction> {
        if (!getMedicationKnowledgeStatus().interactionDatasetAvailable) {
            return emptyList()
        }
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
        return interactions.distinctBy {
            listOf(it.drugA.trim().lowercase(), it.drugB.trim().lowercase()).sorted().joinToString("::")
        }
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

    override suspend fun seedInitialDataIfNeeded() {
        withContext(Dispatchers.IO) {
            val count = drugDao.getDrugCount()
            val dirtyCount = drugDao.getDirtyDrugCount()
            if (count == 0 || dirtyCount > 0) {
                try {
                    if (dirtyCount > 0) {
                        drugDao.clearAllDrugs()
                    }
                    val stream = context.assets.open("cleaned_medbot_dataset.json")
                    val drugs = parseDatasetStream(stream)
                    drugs.chunked(500).forEach { chunk ->
                        drugDao.insertDrugs(chunk)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Drug catalogue seed failed", e)
                }
            }

        }
    }

    private fun sanitizeDrugText(raw: String): String {
        var text = raw.trim()
        while (text.startsWith("&amp;", ignoreCase = true) || text.startsWith("&") || text.startsWith("-") || text.startsWith("•")) {
            if (text.startsWith("&amp;", ignoreCase = true)) {
                text = text.substring(5).trim()
            } else {
                text = text.substring(1).trim()
            }
        }
        return text
    }

    private fun parseDatasetStream(stream: InputStream): List<DrugEntity> {
        val list = mutableListOf<DrugEntity>()
        val reader = android.util.JsonReader(InputStreamReader(stream, "UTF-8"))
        reader.beginArray()
        while (reader.hasNext()) {
            reader.beginObject()
            var cleanName = ""
            var genericBrand = ""
            var category = ""
            var dosageForm = ""
            var strength = ""
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "clean_name" -> cleanName = sanitizeDrugText(reader.nextString())
                    "generic_brand" -> genericBrand = sanitizeDrugText(reader.nextString())
                    "category" -> category = sanitizeDrugText(reader.nextString())
                    "dosage_form" -> dosageForm = sanitizeDrugText(reader.nextString())
                    "strength" -> strength = sanitizeDrugText(reader.nextString())
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            if (cleanName.isNotBlank()) {
                list.add(
                    DrugEntity(
                        name = cleanName,
                        genericName = if (genericBrand.isNotBlank()) genericBrand else cleanName,
                        category = if (category.isNotBlank()) category else "Umum",
                        indication = "",
                        adultDose = "",
                        childDose = "",
                        contraindications = "",
                        sideEffects = "",
                        isOtc = false,
                        alternativesJson = "",
                        dosageForm = dosageForm,
                        strength = strength
                    )
                )
            }
        }
        reader.endArray()
        reader.close()
        return list
    }

    private fun DrugEntity.toDomain(): Drug {
        val alts = if (alternativesJson.isBlank()) emptyList() else {
            alternativesJson.replace("[", "").replace("]", "").replace("\"", "").split(",").map { it.trim() }
        }
        return Drug(
            name = sanitizeDrugText(name),
            genericName = sanitizeDrugText(genericName),
            category = sanitizeDrugText(category),
            indication = indication,
            adultDose = adultDose,
            childDose = childDose,
            contraindications = contraindications,
            sideEffects = sideEffects,
            isOtc = isOtc,
            affordableAlternatives = alts,
            dosageForm = sanitizeDrugText(dosageForm),
            strength = sanitizeDrugText(strength)
        )
    }

    private companion object {
        const val TAG = "DrugRepository"
    }
}

class HealthToolsRepositoryImpl(
    private val dao: HealthToolsDao,
    private val reminderScheduler: com.medbot.app.data.reminder.ReminderScheduler? = null
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
                val days = if (it.daysOfWeekCsv.isBlank()) listOf(1, 2, 3, 4, 5, 6, 7) else {
                    it.daysOfWeekCsv.split(",").mapNotNull { d -> d.trim().toIntOrNull() }
                }
                val type = try { ReminderType.valueOf(it.type) } catch (_: Exception) { ReminderType.MEDICATION }
                val mode = try { ReminderNotificationMode.valueOf(it.notificationMode) } catch (_: Exception) { ReminderNotificationMode.SOUND_AND_VIBRATE }
                Reminder(
                    id = it.id,
                    type = type,
                    title = it.title,
                    timeHour = it.timeHour,
                    timeMinute = it.timeMinute,
                    daysOfWeek = days,
                    isEnabled = it.isEnabled,
                    notificationMode = mode,
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
                notificationMode = reminder.notificationMode.name,
                notes = reminder.notes
            )
        )
        if (reminder.isEnabled) {
            reminderScheduler?.scheduleReminder(reminder)
        } else {
            reminderScheduler?.cancelReminder(reminder.id)
        }
    }

    override suspend fun toggleReminder(id: String, enabled: Boolean) {
        dao.updateReminderStatus(id, enabled)
        if (enabled) {
            val entity = dao.getReminderById(id)
            if (entity != null) {
                val days = if (entity.daysOfWeekCsv.isBlank()) listOf(1, 2, 3, 4, 5, 6, 7) else {
                    entity.daysOfWeekCsv.split(",").mapNotNull { it.trim().toIntOrNull() }
                }
                val type = try { ReminderType.valueOf(entity.type) } catch (_: Exception) { ReminderType.MEDICATION }
                val mode = try { ReminderNotificationMode.valueOf(entity.notificationMode) } catch (_: Exception) { ReminderNotificationMode.SOUND_AND_VIBRATE }
                reminderScheduler?.scheduleReminder(
                    Reminder(
                        id = entity.id,
                        type = type,
                        title = entity.title,
                        timeHour = entity.timeHour,
                        timeMinute = entity.timeMinute,
                        daysOfWeek = days,
                        isEnabled = true,
                        notificationMode = mode,
                        notes = entity.notes
                    )
                )
            }
        } else {
            reminderScheduler?.cancelReminder(id)
        }
    }

    override suspend fun deleteReminder(id: String) {
        dao.deleteReminder(id)
        reminderScheduler?.cancelReminder(id)
    }

    override suspend fun seedInitialDataIfNeeded() {
        if (dao.getLabTestCount() == 0) {
            dao.insertLabTests(defaultLabTests)
        }
    }

    private val defaultLabTests = listOf(
        // 1. Hematologi
        LabTestEntity(
            testName = "Hemoglobin (Hb)",
            category = "Hematologi",
            unit = "g/dL",
            normalLow = 12.0,
            normalHigh = 16.0,
            interpretationLow = "Rendah: Menunjukkan anemia, perdarahan akut/kronis, defisiensi zat besi, atau gangguan sumsum tulang.",
            interpretationHigh = "Tinggi: Polisitemia, dehidrasi berat, atau hipoksia kronis (misal penyakit paru kronis/PPOK).",
            clinicalSignificance = "Protein esensial dalam eritrosit yang mengangkut oksigen ke seluruh sel dan jaringan tubuh."
        ),
        LabTestEntity(
            testName = "Leukosit (Sel Darah Putih)",
            category = "Hematologi",
            unit = "ribu/µL",
            normalLow = 4.0,
            normalHigh = 10.0,
            interpretationLow = "Rendah (Leukopenia): Infeksi virus akut (DBD, influenza, tifoid), efek samping kemoterapi, atau supresi imun.",
            interpretationHigh = "Tinggi (Leukositosis): Menandakan respon terhadap infeksi bakteri akut, peradangan sistemik, nekrosis jaringan, atau leukemia.",
            clinicalSignificance = "Sel imun garis depan pertahanan tubuh terhadap invasi patogen mikroorganisme."
        ),
        LabTestEntity(
            testName = "Trombosit (Platelet)",
            category = "Hematologi",
            unit = "ribu/µL",
            normalLow = 150.0,
            normalHigh = 450.0,
            interpretationLow = "Rendah (Trombositopenia): Waspada infeksi Dengue/DBD, ITP, sepsis, atau risiko perdarahan spontan jika < 50 ribu/µL.",
            interpretationHigh = "Tinggi (Trombositosis): Reaksi inflamasi fase akut, pemulihan perdarahan, atau kelainan mieloproliferatif.",
            clinicalSignificance = "Keping darah yang memegang peranan krusial dalam pembekuan darah dan hemostasis pembuluh darah."
        ),
        LabTestEntity(
            testName = "Hematokrit (Ht / PCV)",
            category = "Hematologi",
            unit = "%",
            normalLow = 36.0,
            normalHigh = 48.0,
            interpretationLow = "Rendah: Anemia, kehilangan darah signifikan, atau hemodilusi cairan berlebih.",
            interpretationHigh = "Tinggi: Hemokonsentrasi akibat kebocoran plasma (tanda bahaya DBD), dehidrasi berat, atau polisitemia.",
            clinicalSignificance = "Proporsi volume sel darah merah terhadap keseluruhan volume darah total."
        ),
        LabTestEntity(
            testName = "Eritrosit (Red Blood Cells)",
            category = "Hematologi",
            unit = "juta/µL",
            normalLow = 4.0,
            normalHigh = 5.5,
            interpretationLow = "Rendah: Anemia, perdarahan, atau destruksi eritrosit (hemolisis).",
            interpretationHigh = "Tinggi: Polisitemia vera atau sekunder akibat penyakit jantung bawaan sianotik.",
            clinicalSignificance = "Jumlah sel darah merah dalam sirkulasi yang mengangkut hemoglobin."
        ),
        LabTestEntity(
            testName = "Laju Endap Darah (LED / ESR)",
            category = "Hematologi",
            unit = "mm/jam",
            normalLow = 0.0,
            normalHigh = 20.0,
            interpretationLow = "Normal.",
            interpretationHigh = "Tinggi: Adanya proses inflamasi aktif, infeksi kronis (TB, osteomielitis), penyakit autoimun (Lupus, RA), atau keganasan.",
            clinicalSignificance = "Penanda non-spesifik adanya peradangan dan peningkatan protein fase akut dalam darah."
        ),

        // 2. Diabetes & Glukosa Darah
        LabTestEntity(
            testName = "Gula Darah Sewaktu (GDS)",
            category = "Diabetes & Glukosa",
            unit = "mg/dL",
            normalLow = 70.0,
            normalHigh = 140.0,
            interpretationLow = "Rendah (Hipoglikemia): Kadar < 70 mg/dL menimbulkan keringat dingin, gemetar, pusing, hingga penurunan kesadaran.",
            interpretationHigh = "Tinggi (Hiperglikemia): Waspada Diabetes Melitus jika ≥ 200 mg/dL disertai gejala klasik (banyak minum, banyak kencing, BB turun).",
            clinicalSignificance = "Kadar glukosa darah saat diperiksa sewaktu-waktu tanpa mempertimbangkan waktu makan terakhir."
        ),
        LabTestEntity(
            testName = "Gula Darah Puasa (GDP)",
            category = "Diabetes & Glukosa",
            unit = "mg/dL",
            normalLow = 70.0,
            normalHigh = 100.0,
            interpretationLow = "Rendah (Hipoglikemia): Dosis obat antidiabetes berlebih atau kelaparan berkepanjangan.",
            interpretationHigh = "Tinggi: 100-125 mg/dL menunjukkan Prediabetes; ≥ 126 mg/dL merupakan kriteria diagnosis Diabetes Melitus.",
            clinicalSignificance = "Pemeriksaan standar baku emas untuk diagnosis diabetes setelah puasa 8-10 jam."
        ),
        LabTestEntity(
            testName = "Gula Darah 2 Jam PP (Post Prandial)",
            category = "Diabetes & Glukosa",
            unit = "mg/dL",
            normalLow = 70.0,
            normalHigh = 140.0,
            interpretationLow = "Rendah: Hipoglikemia reaktif.",
            interpretationHigh = "Tinggi: 140-199 mg/dL menunjukkan Toleransi Glukosa Terganggu (TGT); ≥ 200 mg/dL menunjukkan Diabetes Melitus.",
            clinicalSignificance = "Menilai kemampuan sel beta pankreas dalam mensekresikan insulin untuk mengolah beban glukosa makanan."
        ),
        LabTestEntity(
            testName = "HbA1c (Hemoglobin A1c)",
            category = "Diabetes & Glukosa",
            unit = "%",
            normalLow = 4.0,
            normalHigh = 5.6,
            interpretationLow = "Rendah: Anemia hemolitik, kehilangan darah kronis, atau kehamilan.",
            interpretationHigh = "Tinggi: 5.7–6.4% menunjukkan Prediabetes; ≥ 6.5% menandakan Diabetes Melitus atau kontrol gula darah jangka panjang yang buruk.",
            clinicalSignificance = "Mengukur rerata glikemia selama 2-3 bulan terakhir berdasarkan persentase ikatan glukosa pada hemoglobin."
        ),

        // 3. Profil Lipid & Kolesterol
        LabTestEntity(
            testName = "Kolesterol Total",
            category = "Profil Lipid",
            unit = "mg/dL",
            normalLow = 120.0,
            normalHigh = 200.0,
            interpretationLow = "Rendah: Malnutrisi, hipertiroidisme, atau gangguan absorpsi usus.",
            interpretationHigh = "Tinggi (Hiperkolesterolemia): Meningkatkan risiko aterosklerosis pembuluh darah jantung dan otak jika > 200 mg/dL.",
            clinicalSignificance = "Jumlah keseluruhan kolesterol yang beredar dalam aliran darah."
        ),
        LabTestEntity(
            testName = "Kolesterol LDL (Kolesterol Jahat)",
            category = "Profil Lipid",
            unit = "mg/dL",
            normalLow = 0.0,
            normalHigh = 100.0,
            interpretationLow = "Optimal (< 100 mg/dL).",
            interpretationHigh = "Tinggi: Faktor risiko utama penumpukan plak aterosklerotik penyebab serangan jantung koroner dan stroke.",
            clinicalSignificance = "Lipoprotein berdensitas rendah yang mengangkut kolesterol dari hati ke dinding pembuluh darah."
        ),
        LabTestEntity(
            testName = "Kolesterol HDL (Kolesterol Baik)",
            category = "Profil Lipid",
            unit = "mg/dL",
            normalLow = 40.0,
            normalHigh = 60.0,
            interpretationLow = "Rendah (< 40 mg/dL): Peningkatan risiko penyakit kardiovaskular karena pembersihan kolesterol kurang efektif.",
            interpretationHigh = "Tinggi (≥ 60 mg/dL): Memberikan efek protektif kuat terhadap penyakit jantung koroner.",
            clinicalSignificance = "Lipoprotein berdensitas tinggi yang bertugas mengangkut kelebihan kolesterol kembali ke hati."
        ),
        LabTestEntity(
            testName = "Trigliserida",
            category = "Profil Lipid",
            unit = "mg/dL",
            normalLow = 50.0,
            normalHigh = 150.0,
            interpretationLow = "Rendah: Malnutrisi atau diet sangat rendah lemak.",
            interpretationHigh = "Tinggi: Hipertrigliseridemia terkait sindrom metabolik, konsumsi gula tinggi; waspada risiko pankreatitis akut jika > 500 mg/dL.",
            clinicalSignificance = "Bentuk simpanan lemak utama tubuh yang berasal dari asupan kalori berlebih."
        ),

        // 4. Fungsi Ginjal & Asam Urat
        LabTestEntity(
            testName = "Kreatinin Serum",
            category = "Fungsi Ginjal",
            unit = "mg/dL",
            normalLow = 0.6,
            normalHigh = 1.2,
            interpretationLow = "Rendah: Atrofi otot atau massa otot sangat rendah.",
            interpretationHigh = "Tinggi: Penurunan laju filtrasi glomerulus ginjal, gagal ginjal akut (AKI) atau kronis (CKD), atau dehidrasi berat.",
            clinicalSignificance = "Produk sisa metabolisme fosfokreatin otot yang difiltrasi murni oleh ginjal tanpa reabsorpsi signifikan."
        ),
        LabTestEntity(
            testName = "Ureum (Blood Urea Nitrogen / BUN)",
            category = "Fungsi Ginjal",
            unit = "mg/dL",
            normalLow = 10.0,
            normalHigh = 50.0,
            interpretationLow = "Rendah: Kerusakan hati parah atau diet rendah protein.",
            interpretationHigh = "Tinggi: Penurunan fungsi ekskresi ginjal, perdarahan saluran cerna atas, katabolisme tinggi, atau dehidrasi.",
            clinicalSignificance = "Produk limbah utama dari pemecahan protein tubuh yang dikeluarkan melalui urine."
        ),
        LabTestEntity(
            testName = "Asam Urat (Uric Acid)",
            category = "Fungsi Ginjal",
            unit = "mg/dL",
            normalLow = 3.0,
            normalHigh = 7.0,
            interpretationLow = "Rendah: Sangat jarang memiliki signifikansi klinis bermakna.",
            interpretationHigh = "Tinggi (Hiperurisemia): Membentuk kristal monosodium urat yang memicu radang sendi Gout (artritis pirai) dan nefrolitiasis (batu ginjal).",
            clinicalSignificance = "Produk akhir degradasi senyawa purin dalam tubuh."
        ),
        LabTestEntity(
            testName = "eGFR (Estimasi Laju Filtrasi Glomerulus)",
            category = "Fungsi Ginjal",
            unit = "mL/min/1.73m²",
            normalLow = 90.0,
            normalHigh = 120.0,
            interpretationLow = "Rendah: < 60 mL/min menunjukkan penurunan fungsi ginjal kronis (CKD Stage 3-5); < 15 mL/min gagal ginjal stadium akhir.",
            interpretationHigh = "Tinggi: Normal atau hiperfiltrasi awal pada nefropati diabetik.",
            clinicalSignificance = "Indikator terbaik untuk menentukan derajat keparahan penyakit ginjal kronis."
        ),

        // 5. Fungsi Hati & Enzim
        LabTestEntity(
            testName = "SGOT / AST",
            category = "Fungsi Hati",
            unit = "U/L",
            normalLow = 5.0,
            normalHigh = 35.0,
            interpretationLow = "Normal.",
            interpretationHigh = "Tinggi: Cedera hepatoseluler (hepatitis, fatty liver, sirosis), infark miokard jantung, atau rhabdomyolisis otot.",
            clinicalSignificance = "Enzim intraseluler yang ditemukan di jaringan hati, otot jantung, dan otot rangka."
        ),
        LabTestEntity(
            testName = "SGPT / ALT",
            category = "Fungsi Hati",
            unit = "U/L",
            normalLow = 5.0,
            normalHigh = 40.0,
            interpretationLow = "Normal.",
            interpretationHigh = "Tinggi: Sangat spesifik menandakan peradangan atau kerusakan sel hati (Hepatitis virus akut/kronis, perlemakan hati, efek hepatotoksik obat).",
            clinicalSignificance = "Enzim yang paling spesifik untuk mendeteksi integritas dan kesehatan sel hati."
        ),
        LabTestEntity(
            testName = "Bilirubin Total",
            category = "Fungsi Hati",
            unit = "mg/dL",
            normalLow = 0.2,
            normalHigh = 1.2,
            interpretationLow = "Normal.",
            interpretationHigh = "Tinggi: Memicu ikterus/kuning pada sklera mata dan kulit; disebabkan hemolisis darah, hepatitis, atau sumbatan saluran empedu.",
            clinicalSignificance = "Pigmen kuning hasil pemecahan cincin heme eritrosit yang diolah oleh hati."
        ),
        LabTestEntity(
            testName = "Albumin",
            category = "Fungsi Hati",
            unit = "g/dL",
            normalLow = 3.5,
            normalHigh = 5.0,
            interpretationLow = "Rendah (Hipoalbuminemia): Kegagalan sintesis hati (sirosis), kehilangan lewat ginjal (sindrom nefrotik), malnutrisi, menyebabkan pembengkakan/edema.",
            interpretationHigh = "Tinggi: Terjadi pada kondisi dehidrasi berat.",
            clinicalSignificance = "Protein plasma utama yang menjaga tekanan onkotik koloid pembuluh darah dan mengangkut molekul dalam darah."
        ),

        // 6. Elektrolit Serum
        LabTestEntity(
            testName = "Natrium (Na)",
            category = "Elektrolit",
            unit = "mEq/L",
            normalLow = 135.0,
            normalHigh = 145.0,
            interpretationLow = "Rendah (Hiponatremia): Lemas, mual, sakit kepala, kebingungan mental, hingga kejang dan edema serebral.",
            interpretationHigh = "Tinggi (Hipernatremia): Dehidrasi berat, rasa haus ekstrem, letargi, kejang.",
            clinicalSignificance = "Kation ekstraseluler primer pengatur volume cairan tubuh dan transmisi impuls saraf."
        ),
        LabTestEntity(
            testName = "Kalium (K)",
            category = "Elektrolit",
            unit = "mEq/L",
            normalLow = 3.5,
            normalHigh = 5.0,
            interpretationLow = "Rendah (Hipokalemia): Kelemahan otot, kram, konstipasi, dan aritmia gelombang U pada EKG.",
            interpretationHigh = "Tinggi (Hiperkalemia): Sangat berbahaya, dapat memicu aritmia fatal dan henti jantung; waspada pada pasien gagal ginjal.",
            clinicalSignificance = "Kation intraseluler utama yang menentukan potensial membran istirahat dan konduksi ritme jantung."
        ),
        LabTestEntity(
            testName = "Klorida (Cl)",
            category = "Elektrolit",
            unit = "mEq/L",
            normalLow = 96.0,
            normalHigh = 106.0,
            interpretationLow = "Rendah (Hipokloremia): Kehilangan cairan lambung (muntah berulang) atau asidosis respiratorik.",
            interpretationHigh = "Tinggi (Hiperkloremia): Dehidrasi hipertonik atau asidosis tubulus ginjal.",
            clinicalSignificance = "Anion ekstraseluler terbanyak yang menjaga netralitas listrik dan keseimbangan asam-basa tubuh."
        ),
        LabTestEntity(
            testName = "Kalsium Total (Ca)",
            category = "Elektrolit",
            unit = "mg/dL",
            normalLow = 8.5,
            normalHigh = 10.5,
            interpretationLow = "Rendah (Hipokalsium): Tetani otot, kesemutan (parestesia), kram, kejang.",
            interpretationHigh = "Tinggi (Hiperkalsium): Nyeri tulang, batu ginjal, mual, kelemahan otot, gangguan konduksi jantung.",
            clinicalSignificance = "Mineral esensial untuk kontraksi otot, integritas tulang, dan kaskade pembekuan darah."
        )
    )
}
