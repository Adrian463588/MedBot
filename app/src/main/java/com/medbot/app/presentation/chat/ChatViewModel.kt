package com.medbot.app.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medbot.app.data.local.entity.ChatMessage
import com.medbot.app.domain.model.AgentCatalog
import com.medbot.app.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    private val _currentStreamingText = MutableStateFlow("")
    val currentStreamingText: StateFlow<String> = _currentStreamingText.asStateFlow()

    private var currentSessionId: Long = -1

    fun loadSession(agentId: String) {
        viewModelScope.launch {
            // Find or create session
            val agent = AgentCatalog.getAgentById(agentId) ?: AgentCatalog.getAgentById("orchestrator")!!
            val title = "Konsultasi ${agent.name}"
            
            // For simplicity in this scaffold, we just create a new session
            currentSessionId = chatRepository.createSession(title, agent.id)
            
            chatRepository.getMessagesForSession(currentSessionId).collect { msgs ->
                _messages.value = msgs
            }
        }
    }

    fun sendMessage(text: String, agentId: String) {
        if (text.isBlank() || currentSessionId == -1L) return

        viewModelScope.launch {
            // Save user message
            chatRepository.saveMessage(currentSessionId, text, isUser = true, agentId = agentId)
            
            _isTyping.value = true
            _currentStreamingText.value = ""

            // Construct prompt based on agent
            val agent = AgentCatalog.getAgentById(agentId) ?: AgentCatalog.getAgentById("orchestrator")!!
            val prompt = "${agent.systemPrompt}\nUser: $text\nMedBot:"

            try {
                if (chatRepository.isAiReady()) {
                    val fullResponse = StringBuilder()
                    chatRepository.streamAiResponse(prompt)
                        .catch { e ->
                            _currentStreamingText.value = "Error: ${e.message}"
                            saveAiResponse("Error: ${e.message}", agentId)
                        }
                        .collect { token ->
                            fullResponse.append(token)
                            _currentStreamingText.value = fullResponse.toString()
                        }
                    
                    saveAiResponse(fullResponse.toString(), agentId)
                } else {
                    val errorMsg = "Model AI belum dimuat. Silakan muat di Model Manager."
                    _currentStreamingText.value = errorMsg
                    saveAiResponse(errorMsg, agentId)
                }
            } catch (e: Exception) {
                val errorMsg = "Error: ${e.message}"
                _currentStreamingText.value = errorMsg
                saveAiResponse(errorMsg, agentId)
            }
        }
    }

    private suspend fun saveAiResponse(text: String, agentId: String) {
        chatRepository.saveMessage(currentSessionId, text, isUser = false, agentId = agentId)
        _isTyping.value = false
        _currentStreamingText.value = ""
    }
}
