package com.medbot.app.presentation.knowledge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.medbot.app.domain.model.RagDocument
import com.medbot.app.domain.model.SearchResult
import com.medbot.app.data.rag.BundledKnowledgeSeeder
import com.medbot.app.data.rag.BundledKnowledgeState
import com.medbot.app.domain.repository.RagRepository
import com.medbot.app.domain.repository.RagFailure
import com.medbot.app.domain.repository.RagUnavailableException
import com.medbot.app.domain.repository.SafDocumentGateway
import com.medbot.app.domain.usecase.IngestSafDocumentsUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import javax.inject.Inject

sealed interface KnowledgeUiEvent {
    data class IngestDocument(val uri: String) : KnowledgeUiEvent
    data class Search(val query: String) : KnowledgeUiEvent
    data class DeleteDocument(val documentId: String) : KnowledgeUiEvent
    data class ReportUnavailable(val kind: KnowledgeMessageKind) : KnowledgeUiEvent
}

enum class KnowledgeMessageKind {
    PROCESSING,
    INDEXED,
    EMBEDDER_UNAVAILABLE,
    DOCUMENT_UNAVAILABLE,
    FAILED
}

data class KnowledgeMessage(
    val kind: KnowledgeMessageKind,
    val fileName: String? = null,
    val chunkCount: Int? = null
)

@HiltViewModel
class KnowledgeViewModel @Inject constructor(
    private val ragRepository: RagRepository,
    private val ingestSafDocumentsUseCase: IngestSafDocumentsUseCase,
    private val safDocumentGateway: SafDocumentGateway,
    bundledKnowledgeSeeder: BundledKnowledgeSeeder
) : ViewModel() {
    val documents: StateFlow<List<RagDocument>> = ragRepository.getDocuments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _isIngesting = MutableStateFlow(false)
    val isIngesting: StateFlow<Boolean> = _isIngesting.asStateFlow()

    private val _ingestMessage = MutableStateFlow<KnowledgeMessage?>(null)
    val ingestMessage: StateFlow<KnowledgeMessage?> = _ingestMessage.asStateFlow()
    val bundledKnowledgeState: StateFlow<BundledKnowledgeState> = bundledKnowledgeSeeder.state
    private var searchJob: Job? = null

    fun onEvent(event: KnowledgeUiEvent) {
        when (event) {
            is KnowledgeUiEvent.IngestDocument -> ingestDocumentFromUri(event.uri)
            is KnowledgeUiEvent.Search -> searchKnowledge(event.query)
            is KnowledgeUiEvent.DeleteDocument -> deleteDoc(event.documentId)
            is KnowledgeUiEvent.ReportUnavailable -> reportUnavailable(event.kind)
        }
    }

    fun ingestDocumentFromUri(uri: String) {
        viewModelScope.launch {
            _isIngesting.value = true
            try {
                val source = safDocumentGateway.materialize(uri).getOrElse {
                    _ingestMessage.value = KnowledgeMessage(KnowledgeMessageKind.DOCUMENT_UNAVAILABLE)
                    return@launch
                }
                _ingestMessage.value = KnowledgeMessage(KnowledgeMessageKind.PROCESSING, fileName = source.fileName)
                try {
                    FileInputStream(source.localPath).use { stream ->
                        val result = withContext(Dispatchers.IO) {
                            ingestSafDocumentsUseCase.execute(source.fileName, source.fileUri, source.mimeType, stream)
                        }
                        _ingestMessage.value = if (result.isSuccess) {
                            KnowledgeMessage(
                                kind = KnowledgeMessageKind.INDEXED,
                                fileName = source.fileName,
                                chunkCount = result.getOrNull()?.chunkCount ?: 0
                            )
                        } else {
                            result.exceptionOrNull().toKnowledgeMessage()
                        }
                    }
                } finally {
                    safDocumentGateway.deleteStagedFile(source.localPath)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _ingestMessage.value = error.toKnowledgeMessage()
            } finally {
                _isIngesting.value = false
            }
        }
    }

    fun searchKnowledge(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            try {
                delay(250)
                _searchResults.value = ragRepository.searchSimilarChunks(query, topK = 4)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _searchResults.value = emptyList()
                _ingestMessage.value = error.toKnowledgeMessage()
            }
        }
    }

    fun deleteDoc(docId: String) {
        viewModelScope.launch { ragRepository.deleteDocument(docId) }
    }

    fun reportUnavailable(kind: KnowledgeMessageKind = KnowledgeMessageKind.DOCUMENT_UNAVAILABLE) {
        _ingestMessage.value = KnowledgeMessage(kind)
    }

    private fun Throwable?.toKnowledgeMessage(): KnowledgeMessage {
        return when (this) {
            is RagUnavailableException -> when (failure) {
                RagFailure.EMBEDDER_UNAVAILABLE -> KnowledgeMessage(KnowledgeMessageKind.EMBEDDER_UNAVAILABLE)
                else -> KnowledgeMessage(KnowledgeMessageKind.FAILED)
            }
            else -> KnowledgeMessage(KnowledgeMessageKind.FAILED)
        }
    }
}
