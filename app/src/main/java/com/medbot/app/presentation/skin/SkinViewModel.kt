package com.medbot.app.presentation.skin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.medbot.app.domain.model.SkinRecord
import com.medbot.app.domain.repository.SkinAnalysisException
import com.medbot.app.domain.repository.SkinAnalysisStatus
import com.medbot.app.domain.repository.SkinRepository
import com.medbot.app.domain.repository.SkinCaptureTarget
import com.medbot.app.domain.repository.SkinMediaGateway
import com.medbot.app.domain.usecase.AnalyzeSkinUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SkinUiEvent {
    data class SelectBodyPartFilter(val bodyPart: String) : SkinUiEvent
    data object PrepareCameraCapture : SkinUiEvent
    data class CameraResult(val success: Boolean) : SkinUiEvent
    data class ImportImage(val uri: String) : SkinUiEvent
    data class Analyze(val bodyPart: String, val notes: String) : SkinUiEvent
    data class DeleteRecord(val id: String) : SkinUiEvent
    data object ClearCurrentAnalysis : SkinUiEvent
}

enum class SkinAnalysisUiStatus { IDLE, UNAVAILABLE, INSUFFICIENT_DATA, FAILED }
enum class SkinAnalysisReason { MODEL_UNAVAILABLE, INPUT_REQUIRED, FAILED }

data class SkinAnalysisUiState(
    val status: SkinAnalysisUiStatus = SkinAnalysisUiStatus.IDLE,
    val reason: SkinAnalysisReason? = null
)

@HiltViewModel
class SkinViewModel @Inject constructor(
    private val skinRepository: SkinRepository,
    private val analyzeSkinUseCase: AnalyzeSkinUseCase,
    private val skinMediaGateway: SkinMediaGateway
) : ViewModel() {
    val allRecords: StateFlow<List<SkinRecord>> = skinRepository.getAllRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _selectedBodyPart = MutableStateFlow("all")
    val selectedBodyPart: StateFlow<String> = _selectedBodyPart.asStateFlow()
    private val _currentAnalysis = MutableStateFlow<SkinRecord?>(null)
    val currentAnalysis: StateFlow<SkinRecord?> = _currentAnalysis.asStateFlow()
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()
    private val _analysisState = MutableStateFlow(SkinAnalysisUiState())
    val analysisState: StateFlow<SkinAnalysisUiState> = _analysisState.asStateFlow()
    private val _selectedImagePath = MutableStateFlow<String?>(null)
    val selectedImagePath: StateFlow<String?> = _selectedImagePath.asStateFlow()
    private val _captureTarget = MutableStateFlow<SkinCaptureTarget?>(null)
    val captureTarget: StateFlow<SkinCaptureTarget?> = _captureTarget.asStateFlow()
    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    fun onEvent(event: SkinUiEvent) {
        when (event) {
            is SkinUiEvent.SelectBodyPartFilter -> selectBodyPartFilter(event.bodyPart)
            SkinUiEvent.PrepareCameraCapture -> prepareCameraCapture()
            is SkinUiEvent.CameraResult -> onCameraResult(event.success)
            is SkinUiEvent.ImportImage -> importImage(event.uri)
            is SkinUiEvent.Analyze -> analyzeSelected(event.bodyPart, event.notes)
            is SkinUiEvent.DeleteRecord -> deleteRecord(event.id)
            SkinUiEvent.ClearCurrentAnalysis -> clearCurrentAnalysis()
        }
    }

    fun selectBodyPartFilter(bodyPart: String) { _selectedBodyPart.value = bodyPart }

    fun prepareCameraCapture() {
        viewModelScope.launch {
            _captureTarget.value = runCatching { skinMediaGateway.createCaptureTarget() }.getOrNull()
            if (_captureTarget.value == null) {
                _analysisState.value = SkinAnalysisUiState(SkinAnalysisUiStatus.FAILED, SkinAnalysisReason.FAILED)
            }
        }
    }

    fun onCameraResult(success: Boolean) {
        val target = _captureTarget.value ?: return
        _captureTarget.value = null
        if (success) {
            _selectedImagePath.value = target.path
            clearAnalysisState()
        } else {
            viewModelScope.launch { skinMediaGateway.discard(target.path) }
            _analysisState.value = SkinAnalysisUiState(SkinAnalysisUiStatus.INSUFFICIENT_DATA, SkinAnalysisReason.INPUT_REQUIRED)
        }
    }

    fun importImage(uri: String) {
        viewModelScope.launch {
            _isImporting.value = true
            val result = skinMediaGateway.importImage(uri)
            result.onSuccess {
                _selectedImagePath.value = it
                clearAnalysisState()
            }.onFailure {
                _analysisState.value = SkinAnalysisUiState(SkinAnalysisUiStatus.INSUFFICIENT_DATA, SkinAnalysisReason.INPUT_REQUIRED)
            }
            _isImporting.value = false
        }
    }

    fun analyzeSelected(bodyPart: String, userNotes: String) {
        val imagePath = _selectedImagePath.value
        if (imagePath.isNullOrBlank() || bodyPart.isBlank()) {
            _analysisState.value = SkinAnalysisUiState(SkinAnalysisUiStatus.INSUFFICIENT_DATA, SkinAnalysisReason.INPUT_REQUIRED)
            return
        }
        viewModelScope.launch {
            _isAnalyzing.value = true
            _analysisState.value = SkinAnalysisUiState()
            try {
                _currentAnalysis.value = analyzeSkinUseCase.execute(imagePath, bodyPart, userNotes)
            } catch (error: CancellationException) {
                throw error
            } catch (error: SkinAnalysisException) {
                _analysisState.value = SkinAnalysisUiState(
                    status = when (error.status) {
                        SkinAnalysisStatus.UNAVAILABLE -> SkinAnalysisUiStatus.UNAVAILABLE
                        SkinAnalysisStatus.INSUFFICIENT_DATA -> SkinAnalysisUiStatus.INSUFFICIENT_DATA
                    },
                    reason = when (error.status) {
                        SkinAnalysisStatus.UNAVAILABLE -> SkinAnalysisReason.MODEL_UNAVAILABLE
                        SkinAnalysisStatus.INSUFFICIENT_DATA -> SkinAnalysisReason.INPUT_REQUIRED
                    }
                )
            } catch (error: Exception) {
                _analysisState.value = SkinAnalysisUiState(SkinAnalysisUiStatus.FAILED, SkinAnalysisReason.FAILED)
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun deleteRecord(id: String) { viewModelScope.launch { skinRepository.deleteRecord(id) } }
    fun clearCurrentAnalysis() { _currentAnalysis.value = null }
    fun clearAnalysisState() { _analysisState.value = SkinAnalysisUiState() }
}
