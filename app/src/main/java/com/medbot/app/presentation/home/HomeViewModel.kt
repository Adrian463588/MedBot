package com.medbot.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.medbot.app.data.ai.ModelRegistry
import com.medbot.app.domain.model.PersonaConfig
import com.medbot.app.domain.repository.ChatRepository
import com.medbot.app.domain.repository.ModelRepository
import com.medbot.app.domain.repository.RagRepository
import com.medbot.app.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface HomeUiEvent {
    data class StartConsultation(val title: String) : HomeUiEvent
    data object LoadDownloadedModel : HomeUiEvent
}

sealed interface HomeUiEffect {
    data class OpenChat(val sessionId: String) : HomeUiEffect
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val modelRepository: ModelRepository,
    private val ragRepository: RagRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {
    val isModelLoaded: StateFlow<Boolean> = modelRepository.modelLoaded
    val activeModelName: StateFlow<String?> = modelRepository.activeModelName

    private val _downloadedModelId = MutableStateFlow<String?>(null)
    val downloadedModelId: StateFlow<String?> = _downloadedModelId.asStateFlow()

    private val _downloadedModelName = MutableStateFlow<String?>(null)
    val downloadedModelName: StateFlow<String?> = _downloadedModelName.asStateFlow()

    val ragDocumentCount: StateFlow<Int> = ragRepository.getDocuments()
        .map { documents -> documents.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val personaConfig: StateFlow<PersonaConfig> = userPreferencesRepository.personaConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PersonaConfig())

    private val _effects = MutableSharedFlow<HomeUiEffect>(extraBufferCapacity = 1)
    val effects = _effects.asSharedFlow()

    init {
        checkDownloadedModelsAndAutoLoad()
    }

    fun checkDownloadedModelsAndAutoLoad() {
        viewModelScope.launch {
            val downloadedManifest = ModelRegistry.getAllModels().firstOrNull { manifest ->
                modelRepository.getInstalledModelPath(manifest.id) != null
            }
            if (downloadedManifest != null) {
                _downloadedModelId.value = downloadedManifest.id
                _downloadedModelName.value = downloadedManifest.displayName

                // Auto-load if not already loaded into RAM
                if (!modelRepository.isModelLoaded()) {
                    modelRepository.loadVerifiedModelToRam(downloadedManifest.id, "AUTO")
                }
            } else {
                _downloadedModelId.value = null
                _downloadedModelName.value = null
            }
        }
    }

    fun onEvent(event: HomeUiEvent) {
        when (event) {
            is HomeUiEvent.StartConsultation -> createNewChat(event.title)
            HomeUiEvent.LoadDownloadedModel -> loadDownloadedModel()
        }
    }

    fun loadDownloadedModel() {
        val modelId = _downloadedModelId.value ?: return
        viewModelScope.launch {
            modelRepository.loadVerifiedModelToRam(modelId, "AUTO")
        }
    }

    private fun createNewChat(title: String, agentId: String? = null) {
        viewModelScope.launch {
            val session = chatRepository.createSession(
                title = title,
                agentId = agentId ?: personaConfig.value.selectedAgentId
            )
            _effects.emit(HomeUiEffect.OpenChat(session.id))
        }
    }
}
