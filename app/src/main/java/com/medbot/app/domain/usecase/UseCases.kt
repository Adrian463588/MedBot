package com.medbot.app.domain.usecase

import com.medbot.app.data.ai.LlmInferenceEngine
import com.medbot.app.domain.agents.AgentRegistry
import com.medbot.app.domain.agents.QueryRewriter
import com.medbot.app.domain.agents.TriageOrchestrator
import com.medbot.app.domain.agents.tools.ToolRegistry
import com.medbot.app.domain.model.*
import com.medbot.app.domain.repository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.InputStream
import java.util.UUID

class SendMessageUseCase(
    private val chatRepository: ChatRepository,
    private val ragRepository: RagRepository,
    private val userPrefsRepository: UserPreferencesRepository,
    private val llmEngine: LlmInferenceEngine,
    private val triageOrchestrator: TriageOrchestrator = TriageOrchestrator(),
    private val queryRewriter: QueryRewriter = QueryRewriter()
) {

    suspend fun execute(
        sessionId: String,
        userQuery: String,
        history: List<ChatMessage>,
        imageUri: String? = null,
        imageType: String? = null,
        personaOverride: PersonaConfig? = null
    ): Flow<String> = flow {
        // 1. Save User Message
        val userMsg = ChatMessage(
            sessionId = sessionId,
            text = userQuery,
            isUser = true,
            imageUri = imageUri
        )
        chatRepository.insertMessage(userMsg)

        // 2. Query Rewriting with conversational context
        val contextualQuery = queryRewriter.rewriteQueryWithHistory(userQuery, history)

        // 3. Triage & Specialist Selection
        val triageResult = triageOrchestrator.triage(
            query = contextualQuery,
            hasImage = imageUri != null,
            imageType = imageType
        )

        val targetAgentId = personaOverride?.selectedAgentId?.takeIf { it != "orchestrator" }
            ?: triageResult.primarySpecialist
        val agent = AgentRegistry.getAgentById(targetAgentId)

        // 4. Retrieve RAG Document Chunks
        val searchResults = ragRepository.searchSimilarChunks(contextualQuery, topK = 4)
        val citations = searchResults.map {
            Citation(
                documentTitle = it.documentTitle,
                pageNumber = it.chunk.pageNumber,
                snippet = it.chunk.textContent.take(160),
                sectionTitle = it.chunk.sectionTitle
            )
        }
        val ragContextText = searchResults.joinToString("\n\n") {
            "[Dokumen: ${it.documentTitle} Hal ${it.chunk.pageNumber}]:\n${it.chunk.textContent}"
        }

        // 5. Execute relevant tools if applicable
        val toolContext = StringBuilder()
        for (toolName in agent.tools) {
            val res = ToolRegistry.executeTool(toolName, mapOf("query" to contextualQuery))
            if (res.isSuccess) {
                toolContext.append("\n[Hasil Perhitungan ${res.toolName}]: ${res.summary}\n")
            }
        }

        // 6. Build Final System Prompt
        val persona = personaOverride ?: PersonaConfig(selectedAgentId = agent.id)
        val fullSystemPrompt = llmEngine.buildFullSystemPrompt(
            agent = agent,
            persona = persona,
            ragContext = (ragContextText + "\n" + toolContext.toString()).trim()
        )

        // 7. Stream LLM Response Tokens
        val responseAccumulator = StringBuilder()
        llmEngine.streamInference(contextualQuery, fullSystemPrompt, agent).collect { token ->
            responseAccumulator.append(token)
            emit(token)
        }

        // 8. Save Assistant Response
        val assistantMsg = ChatMessage(
            sessionId = sessionId,
            text = responseAccumulator.toString().trim(),
            isUser = false,
            agentId = agent.id,
            citations = citations,
            urgencyLevel = triageResult.urgency
        )
        chatRepository.insertMessage(assistantMsg)
    }
}

class IngestSafDocumentsUseCase(
    private val ragRepository: RagRepository
) {
    suspend fun execute(
        fileName: String,
        fileUri: String,
        mimeType: String,
        inputStream: InputStream
    ): Result<RagDocument> {
        return ragRepository.ingestDocument(fileName, fileUri, mimeType, inputStream)
    }
}

class AnalyzeSkinUseCase(
    private val skinRepository: SkinRepository,
    private val skinDiagnosisEngine: com.medbot.app.data.vision.SkinDiagnosisEngine
) {
    suspend fun execute(
        imagePath: String,
        bodyPart: String,
        userNotes: String = ""
    ): SkinRecord {
        val record = skinDiagnosisEngine.analyzeSkinImage(imagePath, bodyPart, userNotes)
        skinRepository.saveRecord(record)
        return record
    }
}

class CheckDrugInteractionsUseCase(
    private val drugRepository: DrugRepository
) {
    suspend fun execute(drugNames: List<String>): List<DrugInteraction> {
        return drugRepository.checkInteraction(drugNames)
    }
}
