package com.medbot.app.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medbot.app.core.common.AiOutputFormatter
import com.medbot.app.domain.model.ChatGenerationPhase
import com.medbot.app.domain.model.ChatMessage
import com.medbot.app.domain.model.ChatSession
import com.medbot.app.domain.model.PersonaConfig
import com.medbot.app.domain.repository.ChatRepository
import com.medbot.app.domain.repository.LocalInferenceException
import com.medbot.app.domain.repository.LocalInferenceFailure
import com.medbot.app.domain.repository.ModelRepository
import com.medbot.app.domain.repository.RagFailure
import com.medbot.app.domain.repository.RagUnavailableException
import com.medbot.app.domain.repository.UserPreferencesRepository
import com.medbot.app.domain.usecase.SendMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface ChatUiEvent {
    data class SelectSession(val sessionId: String) : ChatUiEvent
    data object PrepareNewSession : ChatUiEvent
    data class DeleteSession(val sessionId: String) : ChatUiEvent
    data object DeleteAllSessions : ChatUiEvent
    data class SendMessage(val text: String, val imageUri: String?, val imageType: String?) : ChatUiEvent
    data class SetOnlineEvidenceEnabled(val enabled: Boolean) : ChatUiEvent
    data object CancelGeneration : ChatUiEvent
    data object RetryGeneration : ChatUiEvent
}

/** One immutable render model for chat generation, persistence, and errors. */
data class ChatUiState(
    val currentSessionId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val streamingText: String = "",
    val phase: ChatGenerationPhase = ChatGenerationPhase.IDLE,
    val errorCode: String? = null,
    val lastSubmittedText: String? = null,
    val lastSubmittedImageUri: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val sendMessageUseCase: SendMessageUseCase,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val modelRepository: ModelRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    val sessions: StateFlow<List<ChatSession>> = chatRepository.getSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val personaConfig: StateFlow<PersonaConfig> = userPreferencesRepository.personaConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PersonaConfig())

    val onlineEvidenceEnabled: StateFlow<Boolean> = userPreferencesRepository.onlineEvidenceEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // Compatibility projections for screens that have not yet migrated to uiState.
    val currentSessionId: StateFlow<String?> = uiState.map { it.currentSessionId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val messages: StateFlow<List<ChatMessage>> = uiState.map { it.messages }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val isGenerating: StateFlow<Boolean> = uiState.map { it.isGenerating }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val streamingText: StateFlow<String> = uiState.map { it.streamingText }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val errorMessage: StateFlow<String?> = uiState.map { it.errorCode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val generationPhase: StateFlow<ChatGenerationPhase> = uiState.map { it.phase }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatGenerationPhase.IDLE)

    private var messagesJob: Job? = null
    private var generationJob: Job? = null
    private var generationRequestId = 0L
    private var generationSessionId: String? = null

    init {
        viewModelScope.launch { ensureModelLoaded() }
    }

    /** Reconciles verified SAF models and loads one only when its validation passes. */
    suspend fun ensureModelLoaded(): Boolean {
        if (modelRepository.isModelLoaded()) return true
        val downloadedManifest = com.medbot.app.data.ai.ModelRegistry.getAllModels().firstOrNull { manifest ->
            withContext(Dispatchers.IO) {
                modelRepository.getVerifiedModelManifest(manifest.id) != null
            }
        } ?: return false
        val result = modelRepository.loadVerifiedModelToRam(downloadedManifest.id, "AUTO")
        return result is com.medbot.app.domain.model.ModelLoadResult.Loaded
    }

    fun onEvent(event: ChatUiEvent) {
        when (event) {
            is ChatUiEvent.SelectSession -> selectSession(event.sessionId)
            ChatUiEvent.PrepareNewSession -> prepareNewSession()
            is ChatUiEvent.DeleteSession -> deleteSession(event.sessionId)
            ChatUiEvent.DeleteAllSessions -> deleteAllSessions()
            is ChatUiEvent.SendMessage -> sendMessage(event.text, event.imageUri, event.imageType)
            is ChatUiEvent.SetOnlineEvidenceEnabled -> setOnlineEvidenceEnabled(event.enabled)
            ChatUiEvent.CancelGeneration -> cancelGeneration()
            ChatUiEvent.RetryGeneration -> retryGeneration()
        }
    }

    fun selectSession(sessionId: String) {
        if (_uiState.value.currentSessionId == sessionId && messagesJob?.isActive == true) return
        cancelGeneration()
        messagesJob?.cancel()
        _uiState.update {
            it.copy(
                currentSessionId = sessionId,
                messages = emptyList(),
                streamingText = "",
                errorCode = null,
                phase = ChatGenerationPhase.IDLE,
                lastSubmittedText = null,
                lastSubmittedImageUri = null
            )
        }
        messagesJob = viewModelScope.launch {
            chatRepository.getMessages(sessionId).collect { messages ->
                _uiState.update { state ->
                    if (state.currentSessionId == sessionId) state.copy(messages = messages) else state
                }
            }
        }
    }

    fun prepareNewSession() {
        cancelGeneration()
        messagesJob?.cancel()
        messagesJob = null
        _uiState.value = ChatUiState()
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            if (_uiState.value.currentSessionId == sessionId) cancelGeneration()
            chatRepository.deleteSession(sessionId)
            if (_uiState.value.currentSessionId == sessionId) prepareNewSession()
        }
    }

    fun deleteAllSessions() {
        viewModelScope.launch {
            cancelGeneration()
            chatRepository.deleteAllSessions()
            prepareNewSession()
        }
    }

    private fun setOnlineEvidenceEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setOnlineEvidenceEnabled(enabled) }
    }

    fun sendMessage(text: String, imageUri: String? = null, imageType: String? = null) {
        val cleanText = text.trim()
        if (cleanText.isBlank() && imageUri == null) {
            _uiState.update { it.copy(errorCode = "EMPTY_INPUT", phase = ChatGenerationPhase.ERROR) }
            return
        }
        if (generationJob?.isActive == true) return

        val requestId = ++generationRequestId
        generationJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isGenerating = true,
                    streamingText = "",
                    errorCode = null,
                    phase = ChatGenerationPhase.CHECKING_MODEL,
                    lastSubmittedText = cleanText,
                    lastSubmittedImageUri = imageUri
                )
            }

            if (!modelRepository.isModelLoaded() && !ensureModelLoaded()) {
                if (generationRequestId == requestId) {
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            phase = ChatGenerationPhase.UNAVAILABLE,
                            errorCode = LocalInferenceFailure.MODEL_UNAVAILABLE.name,
                            streamingText = ""
                        )
                    }
                    generationJob = null
                }
                return@launch
            }

            var activeSessionId = _uiState.value.currentSessionId
            if (activeSessionId == null) {
                val cleanTitle = AiOutputFormatter.cleanThinkingTags(cleanText)
                    .lines()
                    .firstOrNull { it.isNotBlank() }
                    ?.take(36)
                    ?.trim()
                    ?: "Konsultasi Klinis"
                val newSession = chatRepository.createSession(
                    title = cleanTitle,
                    agentId = personaConfig.value.selectedAgentId
                )
                activeSessionId = newSession.id
                messagesJob?.cancel()
                _uiState.update { it.copy(currentSessionId = newSession.id, messages = emptyList()) }
                messagesJob = viewModelScope.launch {
                    chatRepository.getMessages(newSession.id).collect { messages ->
                        _uiState.update { state ->
                            if (state.currentSessionId == newSession.id) state.copy(messages = messages) else state
                        }
                    }
                }
            }

            val requestSessionId = activeSessionId ?: return@launch
            generationSessionId = requestSessionId
            val history = _uiState.value.messages
            try {
                sendMessageUseCase.execute(
                    sessionId = requestSessionId,
                    userQuery = cleanText,
                    history = history,
                    imageUri = imageUri,
                    imageType = imageType,
                    personaOverride = personaConfig.value,
                    allowOnlineEvidence = onlineEvidenceEnabled.value,
                    onPhase = { phase ->
                        if (isCurrentGeneration(requestId, requestSessionId)) {
                            _uiState.update { it.copy(phase = phase) }
                        }
                    }
                ).collect { delta ->
                    if (isCurrentGeneration(requestId, requestSessionId)) {
                        _uiState.update { state ->
                            state.copy(streamingText = appendDelta(state.streamingText, delta))
                        }
                    }
                }
                if (isCurrentGeneration(requestId, requestSessionId)) {
                    _uiState.update { it.copy(phase = ChatGenerationPhase.READY) }
                }
            } catch (error: CancellationException) {
                if (generationRequestId == requestId) {
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            phase = ChatGenerationPhase.CANCELLED,
                            streamingText = ""
                        )
                    }
                }
                throw error
            } catch (error: Exception) {
                if (generationRequestId == requestId) {
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            phase = if (error is LocalInferenceException &&
                                error.failure == LocalInferenceFailure.MODEL_UNAVAILABLE
                            ) ChatGenerationPhase.UNAVAILABLE else ChatGenerationPhase.ERROR,
                            errorCode = mapError(error),
                            streamingText = ""
                        )
                    }
                }
            } finally {
                if (generationRequestId == requestId) {
                    _uiState.update { it.copy(isGenerating = false, streamingText = "") }
                    generationSessionId = null
                    generationJob = null
                }
            }
        }
    }

    private fun retryGeneration() {
        val state = _uiState.value
        if (state.isGenerating) return
        val text = state.lastSubmittedText ?: return
        sendMessage(text, state.lastSubmittedImageUri, "image/*")
    }

    private fun mapError(error: Exception): String = when {
        error is LocalInferenceException -> error.failure.name
        error is RagUnavailableException && error.failure == RagFailure.EMBEDDER_UNAVAILABLE ->
            LocalInferenceFailure.RAG_UNAVAILABLE.name
        error.message?.contains("embedding", ignoreCase = true) == true ->
            LocalInferenceFailure.RAG_UNAVAILABLE.name
        else -> LocalInferenceFailure.INFERENCE_FAILED.name
    }

    private fun isCurrentGeneration(requestId: Long, sessionId: String): Boolean =
        generationRequestId == requestId &&
            generationSessionId == sessionId &&
            generationJob?.isActive == true

    private fun cancelGeneration() {
        if (generationJob?.isActive != true) return
        generationRequestId += 1
        generationSessionId = null
        generationJob?.cancel()
        generationJob = null
        _uiState.update {
            it.copy(
                isGenerating = false,
                streamingText = "",
                phase = ChatGenerationPhase.CANCELLED,
                errorCode = null
            )
        }
    }

    /** Accept both token deltas and defensive cumulative callbacks. */
    private fun appendDelta(current: String, incoming: String): String = when {
        incoming.isEmpty() -> current
        incoming == current -> current
        incoming.startsWith(current) -> incoming
        else -> current + incoming
    }
}
