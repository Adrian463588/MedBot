package com.medbot.app.presentation.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.medbot.app.data.ai.ModelRegistry
import com.medbot.app.domain.model.DownloadProgress
import com.medbot.app.domain.model.ModelFormat
import com.medbot.app.domain.model.ModelManifest
import com.medbot.app.domain.repository.ModelRepository
import com.medbot.app.domain.repository.ModelFileGateway
import com.medbot.app.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ModelUiEvent {
    data class SelectBackend(val backend: String) : ModelUiEvent
    data class SelectSafUri(val uri: String) : ModelUiEvent
    data object ClearSafUri : ModelUiEvent
    data class StartDownload(val modelId: String) : ModelUiEvent
    data class PauseDownload(val modelId: String) : ModelUiEvent
    data class CancelDownload(val modelId: String) : ModelUiEvent
    data class DeleteModel(val modelId: String) : ModelUiEvent
    data class LoadModel(val path: String) : ModelUiEvent
    data object UnloadModel : ModelUiEvent
    data class AddCustomModel(val name: String, val url: String, val isVision: Boolean) : ModelUiEvent
}

enum class ModelMessageKind { LOADED, UNAVAILABLE, UNLOADED, DELETED, PERMISSION_REQUIRED, CUSTOM_ADDED }

data class ModelMessage(val kind: ModelMessageKind)

@HiltViewModel
class ModelViewModel @Inject constructor(
    private val modelRepository: ModelRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val modelFileGateway: ModelFileGateway
) : ViewModel() {
    private val _modelsList = MutableStateFlow(ModelRegistry.getAllModels())
    val availableModels: StateFlow<List<ModelManifest>> = _modelsList.asStateFlow()

    val safFolderUri: StateFlow<String?> = userPreferencesRepository.safModelFolderUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val isModelLoaded: StateFlow<Boolean> = modelRepository.modelLoaded
    val activeModelName: StateFlow<String?> = modelRepository.activeModelName
    private val _modelMessage = MutableStateFlow<ModelMessage?>(null)
    val modelMessage: StateFlow<ModelMessage?> = _modelMessage.asStateFlow()
    private val _selectedBackend = MutableStateFlow("AUTO")
    val selectedBackend: StateFlow<String> = _selectedBackend.asStateFlow()
    private val _safFileName = MutableStateFlow<String?>(null)
    val safFileName: StateFlow<String?> = _safFileName.asStateFlow()

    init {
        viewModelScope.launch {
            safFolderUri.collect { uri ->
                _safFileName.value = uri?.let { modelFileGateway.displayName(it).getOrNull() }
            }
        }
    }

    fun onEvent(event: ModelUiEvent) {
        when (event) {
            is ModelUiEvent.SelectBackend -> selectBackend(event.backend)
            is ModelUiEvent.SelectSafUri -> selectModelUri(event.uri)
            ModelUiEvent.ClearSafUri -> setSafFolder(null)
            is ModelUiEvent.StartDownload -> startDownload(event.modelId)
            is ModelUiEvent.PauseDownload -> pauseDownload(event.modelId)
            is ModelUiEvent.CancelDownload -> cancelDownload(event.modelId)
            is ModelUiEvent.DeleteModel -> deleteModel(event.modelId)
            is ModelUiEvent.LoadModel -> loadModel(event.path)
            ModelUiEvent.UnloadModel -> unloadModel()
            is ModelUiEvent.AddCustomModel -> addCustomModel(event.name, event.url, event.isVision)
        }
    }

    fun selectBackend(backend: String) { _selectedBackend.value = backend }

    fun addCustomModel(name: String, url: String, isVision: Boolean) {
        val id = "custom-${System.currentTimeMillis()}"
        val manifest = ModelManifest(
            id = id,
            displayName = name.ifBlank { "Custom Model" },
            version = "1.0",
            format = ModelFormat.LITERTLM,
            downloadUrl = url.trim(),
            sizeBytes = 0L,
            sha256 = "",
            minimumRamMb = 2048,
            isMultimodal = isVision,
            recommendedBackend = if (isVision) "GPU" else "AUTO",
            description = "Custom user-provided LiteRT model URL."
        )
        ModelRegistry.registerCustomManifest(manifest)
        _modelsList.value = ModelRegistry.getAllModels()
        _modelMessage.value = ModelMessage(ModelMessageKind.CUSTOM_ADDED)
    }

    fun startDownload(modelId: String) = viewModelScope.launch { modelRepository.startDownload(modelId) }
    fun pauseDownload(modelId: String) = viewModelScope.launch { modelRepository.pauseDownload(modelId) }
    fun cancelDownload(modelId: String) = viewModelScope.launch { modelRepository.cancelDownload(modelId) }
    fun deleteModel(modelId: String) = viewModelScope.launch {
        modelRepository.deleteModel(modelId)
        _modelMessage.value = ModelMessage(ModelMessageKind.DELETED)
    }

    fun loadModel(path: String) {
        viewModelScope.launch {
            val loaded = modelRepository.loadModelToRam(path, _selectedBackend.value)
            _modelMessage.value = if (loaded) ModelMessage(ModelMessageKind.LOADED)
            else ModelMessage(ModelMessageKind.UNAVAILABLE)
        }
    }

    fun unloadModel() {
        viewModelScope.launch {
            modelRepository.unloadModel()
            _modelMessage.value = ModelMessage(ModelMessageKind.UNLOADED)
        }
    }

    fun setSafFolder(uri: String?) {
        viewModelScope.launch {
            userPreferencesRepository.setSafModelFolderUri(uri)
            if (uri != null) loadModel(uri) else unloadModel()
        }
    }

    fun selectModelUri(uri: String) {
        viewModelScope.launch {
            val permission = modelFileGateway.takePersistableReadPermission(uri)
            if (permission.isFailure) {
                _modelMessage.value = ModelMessage(ModelMessageKind.PERMISSION_REQUIRED)
                return@launch
            }
            _safFileName.value = modelFileGateway.displayName(uri).getOrNull()
            setSafFolder(uri)
        }
    }

    fun getDownloadFlow(modelId: String): StateFlow<DownloadProgress?> = modelRepository
        .getDownloadProgress(modelId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun installedModelPath(modelId: String): String? = modelRepository.getInstalledModelPath(modelId)
}

