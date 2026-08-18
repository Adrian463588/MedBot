package com.medbot.app.domain.repository

import com.medbot.app.data.local.entity.ChatMessage
import com.medbot.app.data.local.entity.ChatSession
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getSessions(): Flow<List<ChatSession>>
    fun getMessagesForSession(sessionId: Long): Flow<List<ChatMessage>>
    suspend fun createSession(title: String, agentId: String): Long
    suspend fun saveMessage(sessionId: Long, text: String, isUser: Boolean, agentId: String, citationsJson: String? = null)
    fun streamAiResponse(prompt: String): Flow<String>
    suspend fun isAiReady(): Boolean
}
