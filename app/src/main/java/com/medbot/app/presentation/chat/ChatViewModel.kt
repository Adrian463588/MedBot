package com.medbot.app.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.medbot.app.domain.model.ChatMessage
import com.medbot.app.domain.model.ChatSession
import com.medbot.app.domain.model.PersonaConfig
import com.medbot.app.domain.repository.ChatRepository
import com.medbot.app.domain.repository.LocalInferenceException
import com.medbot.app.domain.repository.LocalInferenceFailure
import com.medbot.app.domain.repository.UserPreferencesRepository
import com.medbot.app.domain.repository.RagFailure
import com.medbot.app.domain.repository.RagUnavailableException
import com.medbot.app.domain.usecase.SendMessageUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ChatUiEvent {
    data class SelectSession(val sessionId: String) : ChatUiEvent
    data class StartNewSession(val title: String, val agentId: String) : ChatUiEvent
    data class DeleteSession(val sessionId: String) : ChatUiEvent
    data class SendMessage(val text: String, val imageUri: String?, val imageType: String?) : ChatUiEvent
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val sendMessageUseCase: SendMessageUseCase,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {
    val sessions: StateFlow<List<ChatSession>> = chatRepository.getSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()
    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    val personaConfig: StateFlow<PersonaConfig> = userPreferencesRepository.personaConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PersonaConfig())

    private var messagesJob: Job? = null

    fun onEvent(event: ChatUiEvent) {
        when (event) {
            is ChatUiEvent.SelectSession -> selectSession(event.sessionId)
            is ChatUiEvent.StartNewSession -> startNewSession(event.title, event.agentId)
            is ChatUiEvent.DeleteSession -> deleteSession(event.sessionId)
            is ChatUiEvent.SendMessage -> sendMessage(event.text, event.imageUri, event.imageType)
        }
    }

    fun selectSession(sessionId: String) {
        if (_currentSessionId.value == sessionId && messagesJob?.isActive == true) return
        _currentSessionId.value = sessionId
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            chatRepository.getMessages(sessionId).collect { _messages.value = it }
        }
    }

    fun clearSelection() {
        messagesJob?.cancel()
        messagesJob = null
        _currentSessionId.value = null
        _messages.value = emptyList()
    }

    fun startNewSession(title: String, initialAgentId: String = "orchestrator") {
        viewModelScope.launch {
            val session = chatRepository.createSession(title = title, agentId = initialAgentId)
            selectSession(session.id)
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            chatRepository.deleteSession(sessionId)
            if (_currentSessionId.value == sessionId) {
                messagesJob?.cancel()
                _currentSessionId.value = null
                _messages.value = emptyList()
            }
        }
    }

    fun sendMessage(text: String, imageUri: String? = null, imageType: String? = null) {
        val sessionId = _currentSessionId.value ?: return
        if (text.isBlank() && imageUri == null) return
        viewModelScope.launch {
            _errorMessage.value = null
            _isGenerating.value = true
            _streamingText.value = ""
            try {
                sendMessageUseCase.execute(
                    sessionId = sessionId,
                    userQuery = text,
                    history = _messages.value,
                    imageUri = imageUri,
                    imageType = imageType,
                    personaOverride = personaConfig.value
                ).collect { token -> _streamingText.value += token }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _errorMessage.value = when {
                    error is LocalInferenceException -> error.failure.name
                    error is RagUnavailableException && error.failure == RagFailure.EMBEDDER_UNAVAILABLE -> LocalInferenceFailure.RAG_UNAVAILABLE.name
                    error.message?.contains("embedding", ignoreCase = true) == true -> LocalInferenceFailure.RAG_UNAVAILABLE.name
                    else -> LocalInferenceFailure.MODEL_UNAVAILABLE.name
                }
            } finally {
                _isGenerating.value = false
                _streamingText.value = ""
            }
        }
    }
}
