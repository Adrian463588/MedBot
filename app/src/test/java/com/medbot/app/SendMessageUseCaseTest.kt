package com.medbot.app

import com.medbot.app.domain.agents.QueryRewriter
import com.medbot.app.domain.agents.TriageOrchestrator
import com.medbot.app.domain.model.*
import com.medbot.app.domain.repository.ChatRepository
import com.medbot.app.domain.repository.ClinicalEvidenceRepository
import com.medbot.app.domain.repository.LocalLlmGateway
import com.medbot.app.domain.repository.RagRepository
import com.medbot.app.domain.repository.OnlineEvidenceRepository
import com.medbot.app.domain.repository.UserPreferencesRepository
import com.medbot.app.data.rag.ClinicalEvidenceQueryPlanner
import com.medbot.app.domain.usecase.SendMessageUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.InputStream

class SendMessageUseCaseTest {

    private lateinit var chatRepository: ChatRepository
    private lateinit var ragRepository: RagRepository
    private lateinit var userPrefsRepository: UserPreferencesRepository
    private lateinit var sendMessageUseCase: SendMessageUseCase

    private val savedMessages = mutableListOf<ChatMessage>()
    private val sessions = mutableListOf<ChatSession>()
    private val capturedModelPrompts = mutableListOf<String>()
    private val capturedRagQueries = mutableListOf<String>()
    private val capturedOnlineQueries = mutableListOf<String>()

    @Before
    fun setUp() {
        capturedRagQueries.clear()
        capturedOnlineQueries.clear()
        chatRepository = object : ChatRepository {
            override fun getSessions(): Flow<List<ChatSession>> = flowOf(sessions)
            override suspend fun getSessionById(sessionId: String): ChatSession? = sessions.find { it.id == sessionId }
            override suspend fun createSession(title: String, agentId: String): ChatSession {
                val s = ChatSession(id = "sess_${sessions.size + 1}", title = title, activeAgentId = agentId)
                sessions.add(s)
                return s
            }
            override suspend fun updateSessionTitle(sessionId: String, title: String) {}
            override suspend fun deleteSession(sessionId: String) { sessions.removeAll { it.id == sessionId } }
            override suspend fun deleteAllSessions() { sessions.clear(); savedMessages.clear() }
            override fun getMessages(sessionId: String): Flow<List<ChatMessage>> = flowOf(savedMessages.filter { it.sessionId == sessionId })
            override suspend fun insertMessage(message: ChatMessage) { savedMessages.add(message) }
            override suspend fun clearMessages(sessionId: String) { savedMessages.removeAll { it.sessionId == sessionId } }
        }

        ragRepository = object : RagRepository {
            override fun getDocuments(): Flow<List<RagDocument>> = flowOf(emptyList())
            override suspend fun ingestDocument(fileName: String, fileUri: String, mimeType: String, inputStream: InputStream): Result<RagDocument> =
                Result.success(RagDocument(id = "doc1", fileName = fileName, fileUri = fileUri, mimeType = mimeType, fileSize = 1024L, pageCount = 1, chunkCount = 1, sha256 = "", indexedAt = System.currentTimeMillis()))
            override suspend fun searchSimilarChunks(query: String, topK: Int): List<SearchResult> {
                capturedRagQueries += query
                if (query.contains("demam", ignoreCase = true)) {
                    return listOf(
                        SearchResult(
                            chunk = DocChunk(id = "c1", docId = "doc1", chunkIndex = 0, textContent = "[PPK-FKTP] Pedoman Kemenkes: Demam Dengue (DBD) memiliki warning signs nyeri perut dan muntah persisten.", pageNumber = 1, sectionTitle = "DBD", embedding = FloatArray(0)),
                            documentTitle = "Pedoman Praktik Klinis Kemenkes",
                            similarityScore = 0.88f,
                            sourceRole = "national_clinical_guideline",
                            evidenceKind = "guideline",
                            citationId = "c1"
                        )
                    )
                }
                if (query.contains("diare", ignoreCase = true)) {
                    return listOf(
                        SearchResult(
                            chunk = DocChunk(
                                id = "c_diare",
                                docId = "doc_diare",
                                chunkIndex = 0,
                                textContent = "[WHO 2024] Diare — Anamnesis: frekuensi BAB, darah, muntah, dan hidrasi. " +
                                    "Penatalaksanaan: rehidrasi oralit dan zink; antibiotik tidak rutin. " +
                                    "Tanda Bahaya (Red Flags): darah dalam tinja atau tidak dapat minum.",
                                pageNumber = 0,
                                sectionTitle = "Diare • WHO Guideline",
                                embedding = FloatArray(0)
                            ),
                            documentTitle = "Diare • WHO Guideline",
                            similarityScore = 0.92f,
                            sourceRole = "national_clinical_guideline",
                            evidenceKind = "guideline",
                            citationId = "c_diare"
                        )
                    )
                }
                return emptyList()
            }
            override suspend fun deleteDocument(docId: String) {}
            override suspend fun getChunkCount(): Int = 1
        }

        val clinicalEvidenceRepository = object : ClinicalEvidenceRepository {
            override suspend fun retrieve(query: EvidenceQuery): EvidenceResult {
                val results = ClinicalEvidenceQueryPlanner()
                    .queries(query.text, query.medicationRequest)
                    .flatMap { expandedQuery ->
                        ragRepository.searchSimilarChunks(expandedQuery, query.topK)
                    }
                    .distinctBy { it.citationId.ifBlank { it.chunk.id } }
                    .map { result ->
                        ClinicalEvidence(
                            evidenceId = result.citationId.ifBlank { result.chunk.id },
                            text = result.chunk.textContent,
                            title = result.documentTitle,
                            sourceRole = ClinicalEvidenceSourceRole.fromWireValue(result.sourceRole),
                            evidenceKind = ClinicalEvidenceKind.fromWireValue(result.evidenceKind),
                            sourceUri = result.documentUri.takeIf(String::isNotBlank),
                            sourceUrl = result.sourceUrl.takeIf(String::isNotBlank),
                            pageNumber = result.chunk.pageNumber.takeIf { it > 0 },
                            section = result.chunk.sectionTitle,
                            similarityScore = result.similarityScore
                        )
                    }
                return if (results.isEmpty()) EvidenceResult.NoEvidence else EvidenceResult.Ready(results)
            }
        }

        val testPersonaFlow = MutableStateFlow(PersonaConfig())
        val testLangFlow = MutableStateFlow(AppLanguage.INDONESIAN)
        userPrefsRepository = object : UserPreferencesRepository {
            override val personaConfig: Flow<PersonaConfig> = testPersonaFlow
            override val safModelFolderUri: Flow<String?> = flowOf(null)
            override val safRagFolderUri: Flow<String?> = flowOf(null)
            override val activeLanguage: Flow<AppLanguage> = testLangFlow
            override val onlineEvidenceEnabled: Flow<Boolean> = flowOf(false)
            override suspend fun updatePersonaConfig(config: PersonaConfig) { testPersonaFlow.value = config }
            override suspend fun setSafModelFolderUri(uri: String?) {}
            override suspend fun setSafRagFolderUri(uri: String?) {}
            override suspend fun setLanguage(language: AppLanguage) { testLangFlow.value = language }
            override suspend fun setOnlineEvidenceEnabled(enabled: Boolean) {}
        }

        val drugRepository = object : com.medbot.app.domain.repository.DrugRepository {
            override fun searchDrugs(query: String): Flow<List<Drug>> = flowOf(emptyList())
            override fun getDrugsByCategory(category: String): Flow<List<Drug>> = flowOf(emptyList())
            override fun getAllCategories(): Flow<List<String>> = flowOf(emptyList())
            override suspend fun getDrugByName(name: String): Drug? = null
            override suspend fun findMatchingDrugs(query: String): List<Drug> = emptyList()
            override suspend fun checkInteraction(drugNames: List<String>): List<DrugInteraction> = emptyList()
            override suspend fun searchSkinRemedy(condition: String): SkinRemedy? = null
            override suspend fun getDrugCount(): Int = 0
            override suspend fun seedInitialDataIfNeeded() {}
        }

        val llmEngine = object : LocalLlmGateway {
            override fun buildFullSystemPrompt(
                agent: DoctorAgent,
                persona: PersonaConfig,
                ragContext: String?
            ): String = "test-system"

            override fun streamInference(
                prompt: String,
                systemPrompt: String,
                agent: DoctorAgent
            ): Flow<String> {
                capturedModelPrompts += prompt
                val citation = if (prompt.contains("WEB_EVIDENCE", ignoreCase = true)) "[W1]" else "[E1]"
                return flowOf(
                    if (prompt.contains("Diare", ignoreCase = true)) {
                        "**GROUNDED:**\n\nTriase awal: perlu menilai dehidrasi.\n\n" +
                            "Pertanyaan probing: usia, durasi, frekuensi BAB, darah, muntah, dan kemampuan minum.\n\n" +
                            "Diagnosis banding: penyebab infeksi, keracunan makanan, atau penyebab lain sesuai pemeriksaan.\n\n" +
                            "Penatalaksanaan: ringkas hanya rehidrasi oralit, zink, dan batas antibiotik dari sumber.\n\n" +
                            "Tanda bahaya: darah dalam tinja atau tidak dapat minum; cari pertolongan segera. $citation"
                    } else {
                        "**GROUNDED:**\n\nTriase awal: evaluasi demam dan tanda bahaya.\n\n" +
                        "Pertanyaan probing: durasi demam, suhu terukur, dan gejala penyerta.\n\n" +
                            "Diagnosis banding: infeksi virus atau bakteri sesuai pemeriksaan.\n\n" +
                            "**Evaluasi Demam**\n\n" +
                            "Penatalaksanaan: pemeriksaan klinis dan perawatan sesuai evidence.\n\n" +
                            "**Tanda bahaya perlu diperiksa oleh tenaga kesehatan.** $citation"
                    }
                )
            }
        }

        sendMessageUseCase = SendMessageUseCase(
            chatRepository = chatRepository,
            ragRepository = ragRepository,
            llmEngine = llmEngine,
            triageOrchestrator = TriageOrchestrator(),
            queryRewriter = QueryRewriter(),
            clinicalEvidenceRepository = clinicalEvidenceRepository,
            onlineEvidenceRepository = object : OnlineEvidenceRepository {
                override suspend fun search(query: String): OnlineEvidenceResult {
                    capturedOnlineQueries += query
                    return OnlineEvidenceResult.Success(
                        OnlineEvidenceBundle(
                            sanitizedQuery = query,
                            sources = listOf(
                                OnlineEvidence(
                                    sourceName = "PubMed / NCBI",
                                    title = "Cough warning signs",
                                    url = "https://pubmed.ncbi.nlm.nih.gov/12345678/",
                                    excerpt = "Tanda bahaya batuk dan diagnosis banding perlu dinilai bersama pemeriksaan klinis.",
                                    sourceRole = com.medbot.app.domain.model.OnlineEvidenceSourceRole.PRIMARY_RESEARCH,
                                    retrievedAt = System.currentTimeMillis()
                                )
                            )
                        )
                    )
                }
            }
        )
    }

    @Test
    fun `execute streams response, saves user message and assistant message with RAG citations`() = runBlocking {
        val session = chatRepository.createSession("Tes Chat", "orchestrator")
        val tokens = mutableListOf<String>()

        sendMessageUseCase.execute(
            sessionId = session.id,
            userQuery = "Saya demam sudah 2 hari dan pusing",
            history = emptyList()
        ).collect { token ->
            tokens.add(token)
        }

        assertTrue("Tokens should be streamed", tokens.isNotEmpty())
        val fullText = tokens.joinToString("")
        assertTrue(fullText.contains("Evaluasi Demam") || fullText.contains("Demam"))
        assertEquals("Repeated model paragraph should be collapsed", 1, "Tanda bahaya".toRegex().findAll(fullText).count())

        // Verify messages persistence
        val messages = savedMessages.filter { it.sessionId == session.id }
        assertEquals(2, messages.size)
        assertTrue("First message is user", messages[0].isUser)
        assertFalse("Second message is assistant", messages[1].isUser)
        assertTrue("Assistant has RAG citations", messages[1].citations.isNotEmpty())
    }

    @Test
    fun `without retrieved evidence status is limited and contradictory model status is removed`() = runBlocking {
        val session = chatRepository.createSession("Tes Chat", "orchestrator")

        val tokens = sendMessageUseCase.execute(
            sessionId = session.id,
            userQuery = "Apa yang dimaksud dengan istirahat cukup?",
            history = emptyList()
        ).toList()

        val fullText = tokens.joinToString("")
        assertTrue(fullText.startsWith("LIMITED_EVIDENCE:"))
        assertFalse(fullText.contains("GROUNDED"))
        assertFalse(
            "Contradictory model status was retained: $fullText",
            fullText.lines().any { it.trim().equals("INSUFFICIENT_DATA:", ignoreCase = true) }
        )
    }

    @Test
    fun `medication request without clinical source is explicitly blocked`() = runBlocking {
        val session = chatRepository.createSession("Tes Chat", "orchestrator")

        val fullText = sendMessageUseCase.execute(
            sessionId = session.id,
            userQuery = "Obat apa yang harus saya minum?",
            history = emptyList()
        ).toList().joinToString("")

        assertTrue(fullText.startsWith("INSUFFICIENT_DATA"))
        assertTrue(fullText.contains("monograf", ignoreCase = true))
    }

    @Test
    fun `diarrhoea medication request uses retrieved treatment evidence and clinical structure`() = runBlocking {
        val session = chatRepository.createSession("Tes Chat", "orchestrator")

        val fullText = sendMessageUseCase.execute(
            sessionId = session.id,
            userQuery = "Saya sakit diare bagaimana penanganan dan obatnya?",
            history = emptyList()
        ).toList().joinToString("")

        assertTrue(fullText.startsWith("GROUNDED:"))
        assertTrue(fullText.contains("Penatalaksanaan", ignoreCase = true))
        assertTrue(fullText.contains("Pertanyaan probing", ignoreCase = true))
        assertTrue(fullText.contains("Tanda bahaya", ignoreCase = true))
        assertTrue(capturedModelPrompts.any { it.contains("rehidrasi oralit", ignoreCase = true) })
        assertTrue(capturedRagQueries.any { it.contains("indikasi", ignoreCase = true) })
        assertTrue(capturedRagQueries.any { it.contains("monograf", ignoreCase = true) })
    }

    @Test
    fun `clinical symptom request without retrieved source is blocked before local generation`() = runBlocking {
        val session = chatRepository.createSession("Tes Chat", "orchestrator")

        val fullText = sendMessageUseCase.execute(
            sessionId = session.id,
            userQuery = "Apa tanda bahaya batuk?",
            history = emptyList()
        ).toList().joinToString("")

        assertTrue(fullText.startsWith("INSUFFICIENT_DATA"))
        assertTrue(fullText.contains("dokumen klinis", ignoreCase = true))
    }

    @Test
    fun `online evidence is used only after explicit opt in and citation is retained`() = runBlocking {
        val session = chatRepository.createSession("Tes Chat", "orchestrator")

        val fullText = sendMessageUseCase.execute(
            sessionId = session.id,
            userQuery = "Apa tanda bahaya batuk?",
            history = listOf(ChatMessage(sessionId = session.id, text = "nama pasien rahasia", isUser = true)),
            allowOnlineEvidence = true
        ).toList().joinToString("")

        assertTrue(fullText.startsWith("GROUNDED:"))
        assertEquals(listOf("Apa tanda bahaya batuk?"), capturedOnlineQueries)
        val assistant = savedMessages.last()
        assertEquals("https://pubmed.ncbi.nlm.nih.gov/12345678/", assistant.citations.single().sourceUrl)
    }

    @Test
    fun `local evidence remains first choice even when web evidence is enabled`() = runBlocking {
        val session = chatRepository.createSession("Tes Chat", "orchestrator")

        val fullText = sendMessageUseCase.execute(
            sessionId = session.id,
            userQuery = "Saya sakit diare bagaimana penanganan dan obatnya?",
            history = emptyList(),
            allowOnlineEvidence = true
        ).toList().joinToString("")

        assertTrue(fullText.startsWith("GROUNDED:"))
        assertTrue(capturedOnlineQueries.isEmpty())
        assertTrue(savedMessages.last().citations.all { it.sourceUrl.isBlank() })
    }
}
