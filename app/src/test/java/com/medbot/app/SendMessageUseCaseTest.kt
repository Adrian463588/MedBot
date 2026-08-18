package com.medbot.app

import com.medbot.app.domain.agents.QueryRewriter
import com.medbot.app.domain.agents.TriageOrchestrator
import com.medbot.app.domain.model.*
import com.medbot.app.domain.repository.ChatRepository
import com.medbot.app.domain.repository.RagRepository
import com.medbot.app.domain.repository.UserPreferencesRepository
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

    @Before
    fun setUp() {
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
            override fun getMessages(sessionId: String): Flow<List<ChatMessage>> = flowOf(savedMessages.filter { it.sessionId == sessionId })
            override suspend fun insertMessage(message: ChatMessage) { savedMessages.add(message) }
            override suspend fun clearMessages(sessionId: String) { savedMessages.removeAll { it.sessionId == sessionId } }
        }

        ragRepository = object : RagRepository {
            override fun getDocuments(): Flow<List<RagDocument>> = flowOf(emptyList())
            override suspend fun ingestDocument(fileName: String, fileUri: String, mimeType: String, inputStream: InputStream): Result<RagDocument> =
                Result.success(RagDocument(id = "doc1", fileName = fileName, fileUri = fileUri, mimeType = mimeType, fileSize = 1024L, pageCount = 1, chunkCount = 1, sha256 = "", indexedAt = System.currentTimeMillis()))
            override suspend fun searchSimilarChunks(query: String, topK: Int): List<SearchResult> {
                if (query.contains("demam", ignoreCase = true)) {
                    return listOf(
                        SearchResult(
                            chunk = DocChunk(id = "c1", docId = "doc1", chunkIndex = 0, textContent = "Pedoman Kemenkes: Demam Dengue (DBD) memiliki warning signs nyeri perut dan muntah persisten.", pageNumber = 1, sectionTitle = "DBD", embedding = FloatArray(0)),
                            documentTitle = "Pedoman Praktik Klinis Kemenkes",
                            similarityScore = 0.88f
                        )
                    )
                }
                return emptyList()
            }
            override suspend fun deleteDocument(docId: String) {}
            override suspend fun getChunkCount(): Int = 1
        }

        val testPersonaFlow = MutableStateFlow(PersonaConfig())
        val testLangFlow = MutableStateFlow(AppLanguage.INDONESIAN)
        userPrefsRepository = object : UserPreferencesRepository {
            override val personaConfig: Flow<PersonaConfig> = testPersonaFlow
            override val safModelFolderUri: Flow<String?> = flowOf(null)
            override val safRagFolderUri: Flow<String?> = flowOf(null)
            override val activeLanguage: Flow<AppLanguage> = testLangFlow
            override suspend fun updatePersonaConfig(config: PersonaConfig) { testPersonaFlow.value = config }
            override suspend fun setSafModelFolderUri(uri: String?) {}
            override suspend fun setSafRagFolderUri(uri: String?) {}
            override suspend fun setLanguage(language: AppLanguage) { testLangFlow.value = language }
        }

        val dummyContext = android.content.ContextWrapper(null)
        val llmEngine = com.medbot.app.data.ai.LlmInferenceEngine(dummyContext)

        sendMessageUseCase = SendMessageUseCase(
            chatRepository = chatRepository,
            ragRepository = ragRepository,
            userPrefsRepository = userPrefsRepository,
            llmEngine = llmEngine,
            triageOrchestrator = TriageOrchestrator(),
            queryRewriter = QueryRewriter()
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

        // Verify messages persistence
        val messages = savedMessages.filter { it.sessionId == session.id }
        assertEquals(2, messages.size)
        assertTrue("First message is user", messages[0].isUser)
        assertFalse("Second message is assistant", messages[1].isUser)
        assertTrue("Assistant has RAG citations", messages[1].citations.isNotEmpty())
    }
}
