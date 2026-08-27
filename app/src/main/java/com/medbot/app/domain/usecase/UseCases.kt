package com.medbot.app.domain.usecase

import com.medbot.app.domain.agents.AgentRegistry
import com.medbot.app.domain.agents.QueryRewriter
import com.medbot.app.domain.agents.TriageOrchestrator
import com.medbot.app.domain.ai.MedicalAnswerGuardrail
import com.medbot.app.domain.clinical.ClinicalEvidenceSelector
import com.medbot.app.domain.clinical.ClinicalRetrievalPlanner
import com.medbot.app.domain.clinical.ClinicalResponsePlanner
import com.medbot.app.domain.model.*
import com.medbot.app.domain.repository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withTimeoutOrNull
import java.io.InputStream

class SendMessageUseCase(
    private val chatRepository: ChatRepository,
    private val ragRepository: RagRepository,
    private val llmEngine: LocalLlmGateway,
    private val triageOrchestrator: TriageOrchestrator = TriageOrchestrator(),
    private val queryRewriter: QueryRewriter = QueryRewriter(),
    private val answerGuardrail: MedicalAnswerGuardrail = MedicalAnswerGuardrail(),
    private val clinicalResponsePlanner: ClinicalResponsePlanner = ClinicalResponsePlanner(),
    private val clinicalRetrievalPlanner: ClinicalRetrievalPlanner = ClinicalRetrievalPlanner(),
    private val onlineEvidenceRepository: OnlineEvidenceRepository? = null,
    /** Non-null in the application graph; nullable only for legacy test construction. */
    private val clinicalEvidenceRepository: ClinicalEvidenceRepository? = null
) {

    suspend fun execute(
        sessionId: String,
        userQuery: String,
        history: List<ChatMessage>,
        imageUri: String? = null,
        @Suppress("UNUSED_PARAMETER") imageType: String? = null,
        personaOverride: PersonaConfig? = null,
        allowOnlineEvidence: Boolean = false,
        onPhase: (ChatGenerationPhase) -> Unit = {}
    ): Flow<String> = flow {
        val trimmedUserQuery = userQuery.trim()
        require(trimmedUserQuery.isNotBlank() || imageUri != null) {
            "A clinical question or image is required"
        }
        // An image-only turn still needs a bounded text instruction for
        // routing and the local multimodal runtime. This is task context, not
        // a patient fact and never becomes a persisted user claim.
        val pipelineQuery = trimmedUserQuery.ifBlank {
            "Analisis citra klinis yang dipilih dan jelaskan data klinis yang masih diperlukan."
        }

        onPhase(ChatGenerationPhase.PREPARING)
        val userMsg = ChatMessage(
            sessionId = sessionId,
            text = trimmedUserQuery,
            isUser = true,
            imageUri = imageUri
        )
        chatRepository.insertMessage(userMsg)

        onPhase(ChatGenerationPhase.CHECKING_MODEL)
        if (!llmEngine.isReady()) {
            onPhase(ChatGenerationPhase.UNAVAILABLE)
            throw LocalInferenceException(
                LocalInferenceFailure.MODEL_UNAVAILABLE,
                "No initialized local language model is available"
            )
        }
        if (imageUri != null && !llmEngine.supportsVision()) {
            onPhase(ChatGenerationPhase.UNAVAILABLE)
            throw LocalInferenceException(
                LocalInferenceFailure.VISION_UNAVAILABLE,
                "The initialized local model does not support image input"
            )
        }

        val contextualQuery = queryRewriter.rewriteQueryWithHistory(
            pipelineQuery.take(MAX_QUERY_CHARS),
            history
        )

        val triageResult = triageOrchestrator.triage(
            query = contextualQuery,
            hasImage = imageUri != null,
            imageType = imageType
        )
        val medicationRequest = answerGuardrail.isMedicationRequest(contextualQuery)

        val targetAgentId = personaOverride?.selectedAgentId?.takeIf { it != "orchestrator" }
            ?: triageResult.primarySpecialist
        val agent = AgentRegistry.getAgentById(targetAgentId)

        // The release-owned BankBook is indexed asynchronously at application
        // startup. A short, visible wait is allowed; an opaque five-minute
        // wait is not. The UI can retry while indexing continues.
        onPhase(ChatGenerationPhase.INDEXING_KNOWLEDGE)
        var indexedChunkCount = ragRepository.getChunkCount()
        if (indexedChunkCount == 0) {
            ragRepository.awaitIndexedData(RAG_INDEX_WAIT_TIMEOUT_MS)
            indexedChunkCount = ragRepository.getChunkCount()
        }
        onPhase(ChatGenerationPhase.RETRIEVING_LOCAL_EVIDENCE)
        val localEvidenceResult = if (indexedChunkCount == 0) {
            EvidenceResult.NoEvidence
        } else {
            withTimeoutOrNull(RAG_RETRIEVAL_TIMEOUT_MS) {
                clinicalEvidenceRepository?.retrieve(
                    EvidenceQuery(
                        text = contextualQuery,
                        medicationRequest = medicationRequest,
                        prescriptionOrCompoundingRequest = answerGuardrail.isPrescriptionOrCompoundingRequest(contextualQuery),
                        topK = MAX_GROUNDING_CHUNKS
                    )
                ) ?: retrieveLegacyEvidence(contextualQuery, medicationRequest)
            } ?: EvidenceResult.InvalidSource("Local evidence retrieval exceeded its time budget")
        }
        val localEvidence = (localEvidenceResult as? EvidenceResult.Ready)?.evidence.orEmpty()
        // E1..En are per-answer labels. Rebind the domain evidence to the
        // exact labels rendered in the citation UI before guardrail review.
        val boundLocalEvidence = localEvidence.mapIndexed { index, evidence ->
            evidence.copy(evidenceId = "E${index + 1}")
        }
        val ragUnavailable = localEvidenceResult is EvidenceResult.EmbedderUnavailable
        val indexedLocalEvidence = boundLocalEvidence.map { evidence -> evidence.evidenceId to evidence }
        val localCitations = indexedLocalEvidence.map { (citationId, evidence) ->
            toCitation(evidence, citationId)
        }
        val localRagContextText = indexedLocalEvidence.joinToString("\n\n") { (citationId, evidence) ->
            val location = evidence.pageNumber?.let { "Hal $it" } ?: "Bagian tanpa nomor halaman"
            val title = sanitizeReferenceText(evidence.title)
            val section = sanitizeReferenceText(evidence.section.orEmpty())
            val body = sanitizeReferenceText(evidence.text).take(MAX_CHUNK_CHARS)
            val evidenceId = sanitizeReferenceText(citationId).take(120)
            "[EVIDENCE_ID=$evidenceId source_role=${evidence.sourceRole.wireValue} " +
                "evidence_kind=${evidence.evidenceKind.wireValue} source=$title $location " +
                "${section.takeIf { it.isNotBlank() } ?: ""}]:\n$body"
        }

        var onlineEvidenceSources: List<OnlineEvidence> = emptyList()
        var onlineFailure: OnlineEvidenceFailure? = null
        val localHasManagementEvidence = localEvidence.any { it.medicationEligible }
        val localHasGroundingEvidence = localEvidence.any { it.generalClinicalEligible }
        val needsOnlineEvidence = localEvidence.isEmpty() ||
            (answerGuardrail.requiresClinicalEvidence(contextualQuery) &&
                if (medicationRequest) !localHasManagementEvidence else !localHasGroundingEvidence)

        // Optional network retrieval is an evidence fallback only. Send the
        // current question after sanitization in the gateway; never send
        // history, images, or the patient profile. LiteRT-LM remains the only
        // component that generates the displayed answer.
        if (allowOnlineEvidence && needsOnlineEvidence && imageUri == null) {
            onPhase(ChatGenerationPhase.SEARCHING_WEB)
            when (val remote = onlineEvidenceRepository?.search(userQuery)
                ?: OnlineEvidenceResult.Unavailable(
                    OnlineEvidenceFailure.SOURCE_UNAVAILABLE,
                    "Online evidence gateway is not configured"
                )) {
                is OnlineEvidenceResult.Success -> onlineEvidenceSources = remote.bundle.sources
                is OnlineEvidenceResult.Unavailable -> onlineFailure = remote.failure
            }
        }

        val onlineRagContextText = onlineEvidenceSources.joinToString("\n\n") { source ->
            val sourceName = sanitizeReferenceText(source.sourceName).take(120)
            val title = sanitizeReferenceText(source.title).take(180)
            val role = source.sourceRole.name
            val url = source.url.take(300)
            val excerpt = sanitizeReferenceText(source.excerpt).take(MAX_CHUNK_CHARS)
            "[WEB_EVIDENCE source_name=$sourceName source_role=$role title=$title url=$url]:\n$excerpt"
        }
        val ragContextText = listOf(localRagContextText, onlineRagContextText)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
        val onlineCitations = onlineEvidenceSources.mapIndexed { index, source ->
            val citationId = "W${index + 1}"
            Citation(
                citationId = citationId,
                documentTitle = source.title,
                pageNumber = 0,
                snippet = source.excerpt.take(220),
                sectionTitle = source.sourceRole.name,
                sourceUrl = source.url,
                sourceName = source.sourceName,
                sourceRole = source.sourceRole.name,
                sourceSha256 = source.responseSha256.orEmpty(),
                retrievedAt = source.retrievedAt
            )
        }
        val citations = localCitations + onlineCitations

        val persona = personaOverride ?: PersonaConfig(selectedAgentId = agent.id)
        val clinicalPlan = clinicalResponsePlanner.plan(
            query = contextualQuery,
            triage = triageResult,
            evidenceText = ragContextText,
            language = persona.language,
            evidence = boundLocalEvidence
        )
        val hasClinicalManagementEvidence = localHasManagementEvidence || onlineEvidenceSources.any {
            it.sourceRole == OnlineEvidenceSourceRole.AUTHORITATIVE_GUIDANCE
        }
        val hasClinicalGroundingEvidence = localHasGroundingEvidence || onlineEvidenceSources.any {
            it.sourceRole != OnlineEvidenceSourceRole.SECONDARY_EDUCATION
        }
        val hasAnyEvidence = localEvidence.isNotEmpty() || onlineEvidenceSources.isNotEmpty()
        if (answerGuardrail.requiresClinicalEvidence(contextualQuery) &&
            (!hasAnyEvidence ||
                if (medicationRequest) {
                    !hasClinicalManagementEvidence
                } else {
                    !hasClinicalGroundingEvidence
                })
        ) {
            val blockedResponse = clinicalPlan.appendProbingContext(
                if (ragUnavailable) {
                    answerGuardrail.ragUnavailableMessage(persona.language)
                } else if (onlineFailure != null) {
                    answerGuardrail.onlineEvidenceUnavailableMessage(persona.language)
                } else if (medicationRequest) {
                    answerGuardrail.blockedMessage(persona.language)
                } else {
                    answerGuardrail.clinicalEvidenceUnavailableMessage(persona.language)
                })
            chatRepository.insertMessage(
                ChatMessage(
                    sessionId = sessionId,
                    text = blockedResponse,
                    isUser = false,
                    agentId = agent.id,
                    citations = emptyList(),
                    urgencyLevel = triageResult.urgency
                )
            )
            emit(blockedResponse)
            return@flow
        }

        val triageContext = "[HASIL TRIASE DETERMINISTIK — BUKAN DIAGNOSIS]\n" +
            "urgency=${triageResult.urgency.name}\n" +
            "reasoning=${sanitizeReferenceText(triageResult.reasoning)}"
        // Put source evidence first because the LiteRT-LM gateway applies a
        // bounded per-turn character budget. The system prompt already carries
        // the stable safety/format contract; the actual clinical source must
        // never be truncated behind routing prose.
        val combinedGrounding = listOf(
            ragContextText.takeIf { it.isNotBlank() },
            clinicalPlan.toPromptBlock(),
            triageContext
        ).filterNotNull().joinToString("\n\n")

        val fullSystemPrompt = llmEngine.buildFullSystemPrompt(
            agent = agent,
            persona = persona,
            // Keep the system instruction stable so LiteRT-LM can retain its
            // prefetched KV state across turns. Per-query evidence belongs to
            // the user message and is still wrapped as passive reference data.
            ragContext = null
        )
        val modelPrompt = buildModelPrompt(pipelineQuery, combinedGrounding)

        onPhase(ChatGenerationPhase.GENERATING)
        val responseAccumulator = StringBuilder()
        val inferenceFlow = if (imageUri != null) {
            llmEngine.streamVisionInferenceInConversation(
                conversationId = sessionId,
                imageUri = imageUri,
                prompt = modelPrompt,
                systemPrompt = fullSystemPrompt,
                agent = agent,
                history = history
            )
        } else {
            llmEngine.streamInferenceInConversation(
                conversationId = sessionId,
                prompt = modelPrompt,
                systemPrompt = fullSystemPrompt,
                agent = agent,
                history = history
            )
        }
        val completed = withTimeoutOrNull(INFERENCE_TIMEOUT_MS) {
            inferenceFlow.collect { token ->
                responseAccumulator.append(token)
            }
            true
        } ?: false
        if (!completed) {
            throw LocalInferenceException(
                LocalInferenceFailure.INFERENCE_FAILED,
                "Local inference exceeded the bounded response time"
            )
        }

        val generatedResponse = com.medbot.app.core.common.AiOutputFormatter.cleanThinkingTags(
            com.medbot.app.core.common.AiOutputFormatter.sanitizeMedicalText(responseAccumulator.toString())
        ).trim()
        if (generatedResponse.isBlank()) {
            throw LocalInferenceException(
                LocalInferenceFailure.UNSAFE_OUTPUT,
                "The local model returned no displayable answer"
            )
        }
        val statusResponse = normalizeEvidenceStatus(
            response = generatedResponse,
            hasRetrievedEvidence = ragContextText.isNotBlank()
        )
        // Citation labels must come from the model's grounded answer. The
        // application never decorates an otherwise uncited response with a
        // source label, because that would falsely imply claim grounding.
        val finalResponse = clinicalPlan.appendProbingContext(statusResponse)

        val review = answerGuardrail.reviewWithEvidence(
            query = contextualQuery,
            response = finalResponse,
            evidence = boundLocalEvidence + onlineEvidenceSources.mapIndexed { index, source ->
                ClinicalEvidence(
                    evidenceId = "W${index + 1}",
                    text = source.excerpt,
                    title = source.title,
                    sourceRole = when (source.sourceRole) {
                        OnlineEvidenceSourceRole.AUTHORITATIVE_GUIDANCE ->
                            ClinicalEvidenceSourceRole.NATIONAL_CLINICAL_GUIDELINE
                        OnlineEvidenceSourceRole.PRIMARY_RESEARCH ->
                            ClinicalEvidenceSourceRole.PRIMARY_RESEARCH
                        OnlineEvidenceSourceRole.SECONDARY_EDUCATION ->
                            ClinicalEvidenceSourceRole.SECONDARY_EDUCATION
                    },
                    evidenceKind = when (source.sourceRole) {
                        OnlineEvidenceSourceRole.AUTHORITATIVE_GUIDANCE -> ClinicalEvidenceKind.GUIDELINE
                        OnlineEvidenceSourceRole.PRIMARY_RESEARCH -> ClinicalEvidenceKind.DIAGNOSTIC_GUIDANCE
                        OnlineEvidenceSourceRole.SECONDARY_EDUCATION -> ClinicalEvidenceKind.EDUCATION
                    },
                    sourceUrl = source.url,
                    sourceSha256 = source.responseSha256,
                    retrievedAt = source.retrievedAt
                )
            },
            citations = citations,
            language = persona.language
        )
        if (review.decision != com.medbot.app.domain.ai.MedicalAnswerDecision.ALLOW) {
            if (medicationRequest && review.decision == com.medbot.app.domain.ai.MedicalAnswerDecision.INSUFFICIENT_EVIDENCE) {
                val blockedResponse = clinicalPlan.appendProbingContext(
                    answerGuardrail.blockedMessage(persona.language)
                )
                chatRepository.insertMessage(
                    ChatMessage(
                        sessionId = sessionId,
                        text = blockedResponse,
                        isUser = false,
                        agentId = agent.id,
                        citations = emptyList(),
                        urgencyLevel = triageResult.urgency
                    )
                )
                emit(blockedResponse)
                return@flow
            }
            throw LocalInferenceException(
                if (review.decision == com.medbot.app.domain.ai.MedicalAnswerDecision.INSUFFICIENT_EVIDENCE) {
                    LocalInferenceFailure.INSUFFICIENT_EVIDENCE
                } else {
                    LocalInferenceFailure.UNSAFE_OUTPUT
                },
                review.reason
            )
        }

        if (answerGuardrail.requiresClinicalEvidence(contextualQuery) &&
            !answerGuardrail.hasRequiredClinicalStructure(finalResponse)
        ) {
            val blockedResponse = clinicalPlan.appendProbingContext(
                answerGuardrail.structuredAnswerUnavailableMessage(persona.language)
            )
            chatRepository.insertMessage(
                ChatMessage(
                    sessionId = sessionId,
                    text = blockedResponse,
                    isUser = false,
                    agentId = agent.id,
                    citations = emptyList(),
                    urgencyLevel = triageResult.urgency
                )
            )
            emit(blockedResponse)
            return@flow
        }

        onPhase(ChatGenerationPhase.READY)
        val assistantMsg = ChatMessage(
            sessionId = sessionId,
            text = finalResponse,
            isUser = false,
            agentId = agent.id,
            citations = citations,
            urgencyLevel = triageResult.urgency
        )
        chatRepository.insertMessage(assistantMsg)
        emit(finalResponse)
    }

    private fun sanitizeReferenceText(raw: String): String {
        return raw
            .replace("<REFERENCE_DATA>", "", ignoreCase = true)
            .replace("</REFERENCE_DATA>", "", ignoreCase = true)
            .replace("<|im_start|>", "", ignoreCase = true)
            .replace("<|im_end|>", "", ignoreCase = true)
            .trim()
            .take(8_000)
    }

    /**
     * Compatibility bridge for older unit-test constructors. The application
     * graph always supplies [ClinicalEvidenceRepository]; this bridge still
     * returns typed evidence and never invents a source or a clinical fact.
     */
    private suspend fun retrieveLegacyEvidence(
        query: String,
        medicationRequest: Boolean
    ): EvidenceResult {
        return try {
            val results = clinicalRetrievalPlanner.queries(query, medicationRequest)
                .flatMap { ragRepository.searchSimilarChunks(it, topK = 12) }
                .groupBy { it.chunk.id }
                .mapNotNull { (_, candidates) -> candidates.maxByOrNull { it.similarityScore } }
            val selected = ClinicalEvidenceSelector.select(query, results, MAX_GROUNDING_CHUNKS)
            val evidence = selected.map { result ->
                val sourceRole = ClinicalEvidenceSourceRole.fromWireValue(
                    result.sourceRole.ifBlank { result.chunk.sourceRole }
                )
                val evidenceKind = ClinicalEvidenceKind.fromWireValue(
                    result.evidenceKind.ifBlank { result.chunk.evidenceKind }
                )
                ClinicalEvidence(
                    evidenceId = result.citationId.ifBlank {
                        result.chunk.citationId.ifBlank { "legacy:${result.chunk.id}" }
                    },
                    text = result.chunk.textContent,
                    title = result.documentTitle,
                    sourceRole = sourceRole,
                    evidenceKind = evidenceKind,
                    sourceUri = result.documentUri.takeIf(String::isNotBlank),
                    sourceUrl = result.sourceUrl.ifBlank { result.chunk.sourceUrl }
                        .takeIf(String::isNotBlank),
                    pageNumber = result.chunk.pageNumber.takeIf { it > 0 },
                    section = result.chunk.sectionTitle,
                    recordId = result.recordId.ifBlank { result.chunk.recordId }
                        .takeIf(String::isNotBlank),
                    sourceSha256 = result.sourceSha256.ifBlank { result.chunk.sourceSha256 }
                        .takeIf(String::isNotBlank),
                    revision = result.revision.ifBlank { result.chunk.revision }
                        .takeIf(String::isNotBlank),
                    similarityScore = result.similarityScore
                )
            }
            if (evidence.isEmpty()) EvidenceResult.NoEvidence else EvidenceResult.Ready(evidence)
        } catch (error: RagUnavailableException) {
            if (error.failure == RagFailure.EMBEDDER_UNAVAILABLE) EvidenceResult.EmbedderUnavailable
            else EvidenceResult.InvalidSource(error.message ?: "Local evidence is unavailable")
        }
    }

    private fun toCitation(evidence: ClinicalEvidence, citationId: String): Citation = Citation(
        citationId = citationId,
        documentTitle = evidence.title,
        pageNumber = evidence.pageNumber ?: 0,
        snippet = evidence.text.take(220),
        sectionTitle = evidence.section.orEmpty(),
        sourceUrl = evidence.sourceUrl.orEmpty(),
        sourceUri = evidence.sourceUri.orEmpty(),
        sourceName = evidence.title,
        sourceRole = evidence.sourceRole.wireValue,
        sourceSha256 = evidence.sourceSha256.orEmpty(),
        recordId = evidence.recordId.orEmpty(),
        revision = evidence.revision.orEmpty(),
        retrievedAt = evidence.retrievedAt
    )

    /**
     * Keeps changing RAG evidence out of the system prompt. This allows the
     * LiteRT-LM conversation to reuse its prefetched system KV cache while
     * still grounding every turn with the evidence retrieved for that turn.
     */
    private fun buildModelPrompt(userQuery: String, grounding: String): String {
        val query = userQuery.trim().take(MAX_MODEL_QUERY_CHARS)
        val reference = grounding.trim().take(MAX_MODEL_REFERENCE_CHARS)
        return buildString {
            append("USER_QUERY:\n<USER_QUERY>\n")
            append(query.ifBlank { "No additional text was provided." })
            append("\n</USER_QUERY>")
            if (reference.isNotBlank()) {
                append("\n\nREFERENCE_DATA (PASSIVE EVIDENCE ONLY):\n<REFERENCE_DATA>\n")
                append(reference)
                append("\n</REFERENCE_DATA>")
            }
        }
    }

    private fun normalizeEvidenceStatus(
        response: String,
        hasRetrievedEvidence: Boolean
    ): String {
        val expected = if (hasRetrievedEvidence) "GROUNDED" else "LIMITED_EVIDENCE"
        var firstStatus: String? = null
        val bodyLines = response.lines().mapNotNull { line ->
            val status = statusFromHeading(line)
            if (status != null) {
                if (firstStatus == null) firstStatus = status
                null
            } else if (MARKDOWN_ONLY_LINE.matches(line.trim())) {
                null
            } else {
                line
            }
        }

        // Small local models sometimes emit several contradictory headings or
        // repeat a partially completed paragraph. Remove model-owned headings,
        // then add one authoritative status owned by the application. The
        // prefix check only collapses repeated display text; it never invents
        // or rewrites a clinical claim.
        val authoritativeStatus = when (firstStatus) {
            "INSUFFICIENT_DATA", "MODEL_UNAVAILABLE" -> firstStatus
            else -> expected
        }
        val body = collapseRepeatedParagraphs(bodyLines.joinToString("\n").trim())
        return "$authoritativeStatus:\n\n$body"
    }

    private fun statusFromHeading(line: String): String? {
        val normalized = line.trim()
            .trimStart(' ', '\t', '-', '_', '*', '•', '#', '>')
            .trimEnd(' ', '\t', '-', '_', '*', '•', '#', '>', ':')
            .uppercase()
        return EVIDENCE_STATUSES.firstOrNull { normalized == it }
    }

    private fun collapseRepeatedParagraphs(body: String): String {
        val paragraphs = body
            .split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val kept = mutableListOf<String>()
        paragraphs.forEach { paragraph ->
            val key = paragraph
                .lowercase()
                .replace(Regex("[*_`~]"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
            val duplicateIndex = kept.indexOfFirst { existing ->
                val existingKey = existing
                    .lowercase()
                    .replace(Regex("[*_`~]"), "")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                existingKey == key || existingKey.startsWith(key) || key.startsWith(existingKey)
            }
            if (duplicateIndex == -1) {
                kept += paragraph
            } else {
                val currentKey = kept[duplicateIndex]
                    .lowercase()
                    .replace(Regex("[*_`~]"), "")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                if (key.length > currentKey.length) kept[duplicateIndex] = paragraph
            }
        }
        return kept.joinToString("\n\n")
    }

    private companion object {
        const val MAX_QUERY_CHARS = 4_000
        const val MAX_GROUNDING_CHUNKS = 4
        const val MAX_CHUNK_CHARS = 1_300
        const val MAX_MODEL_QUERY_CHARS = 500
        const val MAX_MODEL_REFERENCE_CHARS = 3_000
        // Never leave a chat request waiting for the entire corpus build. The
        // UI exposes indexing and the user can retry once the seeder is ready.
        const val RAG_INDEX_WAIT_TIMEOUT_MS = 8_000L
        const val RAG_RETRIEVAL_TIMEOUT_MS = 20_000L
        const val INFERENCE_TIMEOUT_MS = 120_000L
        val EVIDENCE_STATUSES = setOf(
            "GROUNDED",
            "LIMITED_EVIDENCE",
            "INSUFFICIENT_DATA",
            "MODEL_UNAVAILABLE"
        )
        val MARKDOWN_ONLY_LINE = Regex("^[\\s*_`~:]+$")
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
    private val skinDiagnosisEngine: SkinAnalysisGateway
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
