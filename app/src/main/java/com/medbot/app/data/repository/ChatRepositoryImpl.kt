package com.medbot.app.data.repository

import com.medbot.app.data.ai.LlmEngine
import com.medbot.app.data.local.dao.ChatDao
import com.medbot.app.data.local.entity.ChatMessage
import com.medbot.app.data.local.entity.ChatSession
import com.medbot.app.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ChatRepositoryImpl @Inject constructor(
    private val chatDao: ChatDao,
    private val llmEngine: LlmEngine
) : ChatRepository {

    override fun getSessions(): Flow<List<ChatSession>> {
        return chatDao.getAllSessions()
    }

    override fun getMessagesForSession(sessionId: Long): Flow<List<ChatMessage>> {
        return chatDao.getMessagesForSession(sessionId)
    }

    override suspend fun createSession(title: String, agentId: String): Long {
        val session = ChatSession(
            title = title,
            agentId = agentId,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        return chatDao.insertSession(session)
    }

    override suspend fun saveMessage(
        sessionId: Long,
        text: String,
        isUser: Boolean,
        agentId: String,
        citationsJson: String?
    ) {
        val message = ChatMessage(
            sessionId = sessionId,
            text = text,
            isUser = isUser,
            agentId = agentId,
            citationsJson = citationsJson,
            createdAt = System.currentTimeMillis()
        )
        chatDao.insertMessage(message)
    }

    override fun streamAiResponse(prompt: String): Flow<String> {
        return llmEngine.generateResponseAsync(prompt)
    }

    override suspend fun isAiReady(): Boolean {
        return llmEngine.isLoaded()
    }
}
