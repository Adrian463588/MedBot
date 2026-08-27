package com.medbot.app.presentation.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.medbot.app.data.ai.ModelRegistry
import com.medbot.app.data.download.ModelDownloadStartException
import com.medbot.app.domain.model.DownloadProgress
import com.medbot.app.domain.model.ModelManifest
import com.medbot.app.domain.repository.ModelRepository
import com.medbot.app.domain.repository.ModelFileGateway
import com.medbot.app.domain.repository.ModelStorageArtifactNames
import com.medbot.app.domain.repository.ModelStorageGateway
import com.medbot.app.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface ModelUiEvent {
    data class SelectBackend(val backend: String) : ModelUiEvent
    data class SelectSafFolder(val uri: String) : ModelUiEvent
    data class SelectSafUri(val uri: String) : ModelUiEvent
    data object ClearSafUri : ModelUiEvent
    data class StartDownload(val modelId: String, val accessToken: String? = null) : ModelUiEvent
    data class PauseDownload(val modelId: String) : ModelUiEvent
    data class CancelDownload(val modelId: String) : ModelUiEvent
    data class DeleteModel(val modelId: String) : ModelUiEvent
    data class LoadModel(val modelId: String) : ModelUiEvent
    data object UnloadModel : ModelUiEvent
    data class OpenOfficialSource(val modelId: String) : ModelUiEvent
    data class AcceptHuggingFaceTerms(val sourceRevision: String) : ModelUiEvent
    data object ClearHuggingFaceToken : ModelUiEvent
}

sealed interface ModelUiEffect {
    data class OpenOfficialSource(val url: String) : ModelUiEffect
}

enum class ModelMessageKind { LOADED, UNAVAILABLE, UNLOADED, DELETED, PERMISSION_REQUIRED, DOWNLOAD_FAILED }

data class ModelMessage(val kind: ModelMessageKind, val detail: String? = null)

@HiltViewModel
class ModelViewModel @Inject constructor(
    private val modelRepository: ModelRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val modelFileGateway: ModelFileGateway,
    private val modelStorageGateway: ModelStorageGateway
) : ViewModel() {
    private val _modelsList = MutableStateFlow(ModelRegistry.getAllModels())
    val availableModels: StateFlow<List<ModelManifest>> = _modelsList.asStateFlow()

    val safFolderUri: StateFlow<String?> = userPreferencesRepository.safModelFolderUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val isModelLoaded: StateFlow<Boolean> = modelRepository.modelLoaded
    val activeModelName: StateFlow<String?> = modelRepository.activeModelName
    private val _modelMessage = MutableStateFlow<ModelMessage?>(null)
    val modelMessage: StateFlow<ModelMessage?> = _modelMessage.asStateFlow()
    private val _loadingModelId = MutableStateFlow<String?>(null)
    val loadingModelId: StateFlow<String?> = _loadingModelId.asStateFlow()
    private val _selectedBackend = MutableStateFlow("AUTO")
    val selectedBackend: StateFlow<String> = _selectedBackend.asStateFlow()
    private val _safFileName = MutableStateFlow<String?>(null)
    val safFileName: StateFlow<String?> = _safFileName.asStateFlow()
    private val _effects = MutableSharedFlow<ModelUiEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<ModelUiEffect> = _effects.asSharedFlow()
    private val _hasHuggingFaceToken = MutableStateFlow(modelRepository.hasHuggingFaceToken())
    val hasHuggingFaceToken: StateFlow<Boolean> = _hasHuggingFaceToken.asStateFlow()
    private val _acceptedHuggingFaceTermsRevision =
        MutableStateFlow(modelRepository.acceptedHuggingFaceTermsRevision())
    val acceptedHuggingFaceTermsRevision: StateFlow<String?> =
        _acceptedHuggingFaceTermsRevision.asStateFlow()
    private val _hasHuggingFaceSafBackup = MutableStateFlow(false)
    val hasHuggingFaceSafBackup: StateFlow<Boolean> = _hasHuggingFaceSafBackup.asStateFlow()

    init {
        viewModelScope.launch {
            safFolderUri.collect { uri ->
                _safFileName.value = uri?.let { folderUri ->
                    withContext(Dispatchers.IO) {
                        modelStorageGateway.validateDestination(folderUri).getOrNull()?.displayName
                    }
                }
            }
        }
        reconcileSafFolder()
    }

    fun onEvent(event: ModelUiEvent) {
        when (event) {
            is ModelUiEvent.SelectBackend -> selectBackend(event.backend)
            is ModelUiEvent.SelectSafFolder -> selectSafFolder(event.uri)
            is ModelUiEvent.SelectSafUri -> selectModelUri(event.uri)
            ModelUiEvent.ClearSafUri -> setSafFolder(null)
            is ModelUiEvent.StartDownload -> startDownload(event.modelId, event.accessToken)
            is ModelUiEvent.PauseDownload -> pauseDownload(event.modelId)
            is ModelUiEvent.CancelDownload -> cancelDownload(event.modelId)
            is ModelUiEvent.DeleteModel -> deleteModel(event.modelId)
            is ModelUiEvent.LoadModel -> loadModel(event.modelId)
            ModelUiEvent.UnloadModel -> unloadModel()
            is ModelUiEvent.OpenOfficialSource -> openOfficialSource(event.modelId)
            is ModelUiEvent.AcceptHuggingFaceTerms -> acceptHuggingFaceTerms(event.sourceRevision)
            ModelUiEvent.ClearHuggingFaceToken -> clearHuggingFaceToken()
        }
    }

    fun selectBackend(backend: String) { _selectedBackend.value = backend }

    fun startDownload(modelId: String, accessToken: String? = null) = viewModelScope.launch {
        if (!accessToken.isNullOrBlank()) {
            val saved = modelRepository.saveHuggingFaceToken(accessToken)
            if (saved.isFailure) {
                _modelMessage.value = ModelMessage(
                    ModelMessageKind.UNAVAILABLE,
                    saved.exceptionOrNull()?.message
                )
                return@launch
            }
            _hasHuggingFaceToken.value = true
            refreshHuggingFaceState()
        }
        val result = modelRepository.startDownload(modelId)
        if (result.isFailure) {
            val code = (result.exceptionOrNull() as? ModelDownloadStartException)?.code
                ?: "DOWNLOAD_ENQUEUE_FAILED"
            _modelMessage.value = ModelMessage(ModelMessageKind.DOWNLOAD_FAILED, code)
        }
    }
    fun pauseDownload(modelId: String) = viewModelScope.launch { modelRepository.pauseDownload(modelId) }
    fun cancelDownload(modelId: String) = viewModelScope.launch { modelRepository.cancelDownload(modelId) }
    fun deleteModel(modelId: String) = viewModelScope.launch {
        modelRepository.deleteModel(modelId)
        _modelMessage.value = ModelMessage(ModelMessageKind.DELETED)
    }

    fun loadModel(modelId: String) {
        if (_loadingModelId.value != null) return
        _loadingModelId.value = modelId
        _modelMessage.value = null
        viewModelScope.launch {
            try {
                when (val result = modelRepository.loadVerifiedModelToRam(modelId, _selectedBackend.value)) {
                    is com.medbot.app.domain.model.ModelLoadResult.Loaded -> {
                        _modelMessage.value = ModelMessage(ModelMessageKind.LOADED)
                    }
                    is com.medbot.app.domain.model.ModelLoadResult.Unavailable -> {
                        _modelMessage.value = ModelMessage(ModelMessageKind.UNAVAILABLE, result.message)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _modelMessage.value = ModelMessage(
                    ModelMessageKind.UNAVAILABLE,
                    error.message?.takeIf { it.isNotBlank() } ?: "MODEL_LOAD_FAILED"
                )
            } finally {
                _loadingModelId.value = null
            }
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
            if (uri == null) {
                _safFileName.value = null
                _hasHuggingFaceSafBackup.value = false
            }
        }
    }

    /** Revalidates the stored grant and recovers a persisted SAF tree after recreation. */
    fun reconcileSafFolder() {
        viewModelScope.launch {
            val configuredUri = userPreferencesRepository.safModelFolderUri.first()
            val configuredDestination = configuredUri?.let { storedUri ->
                withContext(Dispatchers.IO) {
                    val permission = modelStorageGateway.takePersistableTreePermission(storedUri)
                    if (permission.isSuccess) {
                        modelStorageGateway.validateDestination(storedUri).getOrNull()
                    } else {
                        null
                    }
                }
            }
            if (configuredDestination != null) {
                _safFileName.value = configuredDestination.displayName
                reconcileHuggingFaceAccess(configuredDestination.treeUri)
                return@launch
            }

            val candidateNames = ModelRegistry.getAllModels().flatMap { manifest ->
                listOf(
                    manifest.fileName,
                    manifest.partialFileName,
                    ModelStorageArtifactNames.verificationMetadata(manifest.id)
                )
            }.toMutableSet().apply {
                add(ModelStorageArtifactNames.HUGGING_FACE_ACCESS)
                add(ModelStorageArtifactNames.HUGGING_FACE_ACCESS_TEMP)
            }
            val discovered = withContext(Dispatchers.IO) {
                modelStorageGateway.discoverPersistedDestination(candidateNames).getOrNull()
            }
            if (discovered != null) {
                userPreferencesRepository.setSafModelFolderUri(discovered.treeUri)
                _safFileName.value = discovered.displayName
                reconcileHuggingFaceAccess(discovered.treeUri)
                return@launch
            }

            if (!configuredUri.isNullOrBlank()) {
                userPreferencesRepository.setSafModelFolderUri(null)
            }
            _safFileName.value = null
            _hasHuggingFaceSafBackup.value = false
        }
    }

    private fun selectSafFolder(uri: String) {
        viewModelScope.launch {
            val destination = withContext(Dispatchers.IO) {
                val permission = modelStorageGateway.takePersistableTreePermission(uri)
                permission.fold(
                    onSuccess = { modelStorageGateway.validateDestination(uri) },
                    onFailure = { Result.failure(it) }
                )
            }
            if (destination.isFailure) {
                _modelMessage.value = ModelMessage(ModelMessageKind.PERMISSION_REQUIRED)
                return@launch
            }
            userPreferencesRepository.setSafModelFolderUri(uri)
            _safFileName.value = destination.getOrNull()?.displayName
            reconcileHuggingFaceAccess(uri)
            _modelMessage.value = null
        }
    }

    fun selectModelUri(uri: String) {
        viewModelScope.launch {
            val permission = withContext(Dispatchers.IO) {
                modelFileGateway.takePersistableReadPermission(uri)
            }
            if (permission.isFailure) {
                _modelMessage.value = ModelMessage(ModelMessageKind.PERMISSION_REQUIRED)
                return@launch
            }
            _loadingModelId.value = IMPORTED_MODEL_LOADING_ID
            _modelMessage.value = null
            try {
                when (val result = modelRepository.loadImportedModelToRam(uri, _selectedBackend.value)) {
                    is com.medbot.app.domain.model.ModelLoadResult.Loaded -> {
                        _modelMessage.value = ModelMessage(ModelMessageKind.LOADED)
                    }
                    is com.medbot.app.domain.model.ModelLoadResult.Unavailable -> {
                        _modelMessage.value = ModelMessage(ModelMessageKind.UNAVAILABLE, result.message)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _modelMessage.value = ModelMessage(
                    ModelMessageKind.UNAVAILABLE,
                    error.message?.takeIf { it.isNotBlank() } ?: "MODEL_LOAD_FAILED"
                )
            } finally {
                _loadingModelId.value = null
            }
        }
    }

    fun getDownloadFlow(modelId: String): StateFlow<DownloadProgress?> = modelRepository
        .getDownloadProgress(modelId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun installedModelPath(modelId: String): String? = modelRepository.getInstalledModelPath(modelId)

    private fun openOfficialSource(modelId: String) {
        val manifest = ModelRegistry.getManifestById(modelId) ?: return
        _effects.tryEmit(ModelUiEffect.OpenOfficialSource(manifest.sourcePageUrl))
    }

    private fun acceptHuggingFaceTerms(sourceRevision: String) {
        val normalized = sourceRevision.trim()
        if (ModelRegistry.getAllModels().none { manifest ->
                manifest.accessRequirement != com.medbot.app.domain.model.ModelAccessRequirement.NONE &&
                    manifest.sourceRevision == normalized
            }) {
            _modelMessage.value = ModelMessage(
                ModelMessageKind.UNAVAILABLE,
                "Unknown gated model source revision"
            )
            return
        }
        viewModelScope.launch {
            val saved = modelRepository.acceptHuggingFaceTerms(normalized)
            if (saved.isSuccess) {
                _acceptedHuggingFaceTermsRevision.value = normalized
                refreshHuggingFaceState()
            } else {
                _modelMessage.value = ModelMessage(
                    ModelMessageKind.UNAVAILABLE,
                    saved.exceptionOrNull()?.message
                )
            }
        }
    }

    private fun clearHuggingFaceToken() {
        viewModelScope.launch {
            val cleared = modelRepository.clearHuggingFaceToken()
            if (cleared.isSuccess || !modelRepository.hasHuggingFaceToken()) {
                _hasHuggingFaceToken.value = false
                _acceptedHuggingFaceTermsRevision.value = null
                _hasHuggingFaceSafBackup.value = false
            }
            if (cleared.isFailure) {
                _modelMessage.value = ModelMessage(
                    ModelMessageKind.UNAVAILABLE,
                    cleared.exceptionOrNull()?.message
                )
            }
        }
    }

    private suspend fun refreshHuggingFaceState() {
        val treeUri = userPreferencesRepository.safModelFolderUri.first()
        if (treeUri.isNullOrBlank()) {
            _hasHuggingFaceToken.value = modelRepository.hasHuggingFaceToken()
            _acceptedHuggingFaceTermsRevision.value = modelRepository.acceptedHuggingFaceTermsRevision()
            _hasHuggingFaceSafBackup.value = false
        } else {
            reconcileHuggingFaceAccess(treeUri)
        }
    }

    private suspend fun reconcileHuggingFaceAccess(treeUri: String) {
        modelRepository.restoreHuggingFaceAccessFromSaf(treeUri)
        if (modelRepository.hasHuggingFaceToken()) {
            modelRepository.persistHuggingFaceAccessToSaf(treeUri)
        }
        _hasHuggingFaceToken.value = modelRepository.hasHuggingFaceToken()
        _acceptedHuggingFaceTermsRevision.value = modelRepository.acceptedHuggingFaceTermsRevision()
        _hasHuggingFaceSafBackup.value = modelRepository.hasHuggingFaceTokenBackup(treeUri)
    }

    private companion object {
        const val IMPORTED_MODEL_LOADING_ID = "__imported_model__"
    }
}
