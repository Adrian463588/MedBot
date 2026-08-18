package com.medbot.app.presentation.knowledge

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.medbot.app.core.designsystem.components.MedBotTopAppBar
import com.medbot.app.domain.model.RagDocument
import com.medbot.app.domain.model.SearchResult
import com.medbot.app.domain.repository.RagRepository
import com.medbot.app.domain.usecase.IngestSafDocumentsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.*

class KnowledgeViewModel(
    private val ragRepository: RagRepository,
    private val ingestSafDocumentsUseCase: IngestSafDocumentsUseCase
) : ViewModel() {

    val documents: StateFlow<List<RagDocument>> = ragRepository.getDocuments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _isIngesting = MutableStateFlow(false)
    val isIngesting: StateFlow<Boolean> = _isIngesting.asStateFlow()

    fun ingestSampleClinicalGuide() {
        viewModelScope.launch {
            _isIngesting.value = true
            try {
                val sampleContent = """
                    PANDUAN PRAKTIK KLINIS PUSKESMAS & DOKTER KELUARGA (KEMENKES RI)
                    
                    BAB 1: DEMAM BERDARAH DENGUE (DBD)
                    Kriteria Diagnosis DBD:
                    1. Demam mendadak tinggi kontinu selama 2-7 hari.
                    2. Manifestasi perdarahan: uji torniket positif, petekie, purpura, perdarahan gusi, epistaksis.
                    3. Trombositopenia (Trombosit < 100.000 /µL).
                    4. Tanda kebocoran plasma: peningkatan hematokrit >= 20%, efusi pleura, atau asites.
                    
                    Tanda Bahaya (Warning Signs) DBD:
                    - Nyeri perut hebat atau nyeri tekan abdomen.
                    - Muntah persisten terus-menerus.
                    - Akumulasi cairan klinis (asites/efusi).
                    - Perdarahan mukosa.
                    - Letargi, gelisah, atau penurunan kesadaran.
                    - Pembesaran hati > 2 cm.
                    - Peningkatan hematokrit bersamaan dengan penurunan cepat trombosit.
                    
                    BAB 2: MANAJEMEN HIPERTENSI PRIMER
                    Target Tekanan Darah:
                    - Dewasa umum: < 140/90 mmHg.
                    - Diabetes atau Penyakit Ginjal Kronis: < 130/80 mmHg.
                    Lini pertama terapi: Modifikasi gaya hidup (Diet DASH rendah garam < 2 gram/hari, olahraga aerobik 150 menit/minggu) + Amlodipin 5 mg atau Captopril 25 mg.
                """.trimIndent()

                val stream = ByteArrayInputStream(sampleContent.toByteArray(Charsets.UTF_8))
                ingestSafDocumentsUseCase.execute(
                    fileName = "Panduan_Praktik_Klinis_Kemenkes.pdf",
                    fileUri = "saf://documents/Panduan_Praktik_Klinis_Kemenkes.pdf",
                    mimeType = "application/pdf",
                    inputStream = stream
                )
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
            val results = ragRepository.searchSimilarChunks(query, topK = 4)
            _searchResults.value = results
        }
    }

    fun deleteDoc(docId: String) {
        viewModelScope.launch {
            ragRepository.deleteDocument(docId)
        }
    }

    class Factory(
        private val ragRepository: RagRepository,
        private val ingestSafDocumentsUseCase: IngestSafDocumentsUseCase
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return KnowledgeViewModel(ragRepository, ingestSafDocumentsUseCase) as T
        }
    }
}

@Composable
fun KnowledgeBaseScreen(
    viewModel: KnowledgeViewModel,
    onNavigateBack: () -> Unit
) {
    val documents by viewModel.documents.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isIngesting by viewModel.isIngesting.collectAsState()

    var testQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            MedBotTopAppBar(
                title = "Knowledge Base RAG",
                subtitle = "Penyimpanan & Indeks Vektor Dokumen SAF",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.ingestSampleClinicalGuide() },
                        enabled = !isIngesting
                    ) {
                        Icon(Icons.Default.NoteAdd, contentDescription = "Impor SAF", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Import SAF Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
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
                            text = "MedBot memecah dokumen menjadi potongan 512-token dengan overlap 50-token dan menghitung vektor dense 384 dimensi secara 100% lokal.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.ingestSampleClinicalGuide() },
                            enabled = !isIngesting,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isIngesting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Mengindeks Dokumen...")
                            } else {
                                Icon(Icons.Default.UploadFile, contentDescription = "Impor")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Muat Dokumen Panduan Klinis Kemenkes")
                            }
                        }
                    }
                }
            }

            // Test Vector Search Query Box
            item {
                Text("Uji Pencarian Semantik Vektor", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = testQuery,
                    onValueChange = {
                        testQuery = it
                        viewModel.searchKnowledge(it)
                    },
                    placeholder = { Text("Contoh: tanda bahaya dbd, target tensi darah") },
                    trailingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Cari")
                    },
                    shape = RoundedCornerShape(12.dp),
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
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
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
                                Text(
                                    text = "Skor: ${"%.3f".format(result.similarityScore)}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
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
                            text = "Belum ada dokumen yang diindeks. Tekan tombol di atas untuk memuat dokumen.",
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
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(14.dp).fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp)
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
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
