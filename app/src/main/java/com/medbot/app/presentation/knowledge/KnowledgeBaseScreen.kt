package com.medbot.app.presentation.knowledge

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.medbot.app.core.designsystem.components.MedBotTopAppBar
import com.medbot.app.core.designsystem.components.springBounceClick
import com.medbot.app.domain.model.RagDocument
import com.medbot.app.domain.model.SearchResult
import com.medbot.app.domain.repository.RagRepository
import com.medbot.app.domain.usecase.IngestSafDocumentsUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class KnowledgeViewModel @Inject constructor(
    private val ragRepository: RagRepository,
    private val ingestSafDocumentsUseCase: IngestSafDocumentsUseCase
) : ViewModel() {

    val documents: StateFlow<List<RagDocument>> = ragRepository.getDocuments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _isIngesting = MutableStateFlow(false)
    val isIngesting: StateFlow<Boolean> = _isIngesting.asStateFlow()

    private val _ingestMessage = MutableStateFlow<String?>(null)
    val ingestMessage: StateFlow<String?> = _ingestMessage.asStateFlow()

    fun ingestDocumentFromUri(
        fileName: String,
        fileUri: String,
        mimeType: String,
        inputStream: InputStream
    ) {
        viewModelScope.launch {
            _isIngesting.value = true
            _ingestMessage.value = "Sedang memproses $fileName..."
            try {
                val res = ingestSafDocumentsUseCase.execute(fileName, fileUri, mimeType, inputStream)
                if (res.isSuccess) {
                    _ingestMessage.value = "Sukses mengindeks $fileName (${res.getOrNull()?.chunkCount} Chunks Vektor)"
                } else {
                    _ingestMessage.value = "${res.exceptionOrNull()?.message ?: "RAG tidak tersedia"}"
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _ingestMessage.value = "RAG tidak tersedia: ${error.message ?: "proses gagal"}"
            } finally {
                _isIngesting.value = false
            }
        }
    }

    fun searchKnowledge(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            try {
                _searchResults.value = ragRepository.searchSimilarChunks(query, topK = 4)
                _ingestMessage.value = null
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _searchResults.value = emptyList()
                _ingestMessage.value = "RAG tidak tersedia: ${error.message ?: "embedding lokal belum tersedia"}"
            }
        }
    }

    fun deleteDoc(docId: String) {
        viewModelScope.launch {
            ragRepository.deleteDocument(docId)
        }
    }

    fun reportUnavailable(message: String) {
        _ingestMessage.value = message
    }

}

@Composable
fun KnowledgeBaseScreen(
    viewModel: KnowledgeViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isIngesting by viewModel.isIngesting.collectAsStateWithLifecycle()
    val ingestMsg by viewModel.ingestMessage.collectAsStateWithLifecycle()
    val ingestMessage = ingestMsg

    var testQuery by remember { mutableStateOf("") }

    // Native SAF Document Picker Activity Result Launcher
    val safDocPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri)
            val fileName = contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }?.takeIf { it.isNotBlank() }
            val mimeType = contentResolver.getType(uri).orEmpty()

            if (inputStream != null && fileName != null) {
                viewModel.ingestDocumentFromUri(
                    fileName = fileName,
                    fileUri = uri.toString(),
                    mimeType = mimeType,
                    inputStream = inputStream
                )
            } else {
                inputStream?.close()
                viewModel.reportUnavailable("RAG tidak tersedia: nama atau isi dokumen sumber tidak dapat dibaca.")
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            MedBotTopAppBar(
                title = "Knowledge Base RAG",
                subtitle = "Penyimpanan & Indeks Vektor Dokumen SAF",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.springBounceClick()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            safDocPickerLauncher.launch(arrayOf("application/pdf", "text/plain", "text/markdown", "*/*"))
                        },
                        enabled = !isIngesting,
                        modifier = Modifier.springBounceClick()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = "Pilih Dokumen SAF", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
        ) {
            // Import SAF Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Folder SAF",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Pemuatan Dokumen Medis (SAF)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Pilih file PDF atau TXT panduan medis dari penyimpanan perangkat Anda via Android Storage Access Framework (SAF).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                safDocPickerLauncher.launch(arrayOf("application/pdf", "text/plain", "text/markdown", "*/*"))
                            },
                            enabled = !isIngesting,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().springBounceClick()
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = "Pilih File")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pilih File SAF")
                        }

            if (isIngesting) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = ingestMsg ?: "Sedang memproses dokumen...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    if (!isIngesting && !ingestMessage.isNullOrBlank()) {
                        val message = ingestMessage.orEmpty()
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            color = if (message.startsWith("Sukses")) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }

            // Test Vector Search Query Box
            item {
                Text("Uji Pencarian Semantik Vektor (Cosine Similarity)", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = testQuery,
                    onValueChange = {
                        testQuery = it
                        viewModel.searchKnowledge(it)
                    },
                    placeholder = { Text("Contoh: tanda bahaya dbd, target tensi darah diabetes") },
                    trailingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Cari")
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Live Vector Search Results
            if (searchResults.isNotEmpty()) {
                item {
                    Text(
                        text = "Hasil Cosine Similarity Top-${searchResults.size}:",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                items(searchResults) { result ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = result.documentTitle,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "Skor: ${"%.3f".format(result.similarityScore)}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = result.chunk.textContent,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // Indexed Documents List
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Dokumen Terindeks (${documents.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            if (documents.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Belum ada dokumen yang diindeks. Tekan tombol di atas untuk memilih file dari SAF.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(documents) { doc ->
                    DocumentItemCard(doc = doc, onDelete = { viewModel.deleteDoc(doc.id) })
                }
            }
        }
    }
}

@Composable
fun DocumentItemCard(doc: RagDocument, onDelete: () -> Unit) {
    val dateStr = remember(doc.indexedAt) {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))
        sdf.format(Date(doc.indexedAt))
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(16.dp).fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = CircleShape,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = doc.fileName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1
                    )
                    Text(
                        text = "${doc.chunkCount} Chunks Vektor • ${doc.pageCount} Halaman • $dateStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.springBounceClick()) {
                Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
