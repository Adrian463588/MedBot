package com.medbot.app.presentation.skin

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.medbot.app.core.designsystem.components.MarkdownText
import com.medbot.app.core.designsystem.components.MedBotTopAppBar
import com.medbot.app.core.designsystem.components.UrgencyBadge
import com.medbot.app.domain.model.BodyPartCategory
import com.medbot.app.domain.model.SkinRecord
import com.medbot.app.domain.model.UrgencyLevel
import com.medbot.app.domain.repository.SkinRepository
import com.medbot.app.domain.usecase.AnalyzeSkinUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class SkinViewModel(
    private val skinRepository: SkinRepository,
    private val analyzeSkinUseCase: AnalyzeSkinUseCase
) : ViewModel() {

    val allRecords: StateFlow<List<SkinRecord>> = skinRepository.getAllRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedBodyPart = MutableStateFlow<String>("Semua")
    val selectedBodyPart: StateFlow<String> = _selectedBodyPart.asStateFlow()

    private val _currentAnalysis = MutableStateFlow<SkinRecord?>(null)
    val currentAnalysis: StateFlow<SkinRecord?> = _currentAnalysis.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    fun selectBodyPartFilter(bodyPart: String) {
        _selectedBodyPart.value = bodyPart
    }

    fun analyzeAndSave(imagePath: String, bodyPart: String, userNotes: String) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            try {
                val record = analyzeSkinUseCase.execute(imagePath, bodyPart, userNotes)
                _currentAnalysis.value = record
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun deleteRecord(id: String) {
        viewModelScope.launch {
            skinRepository.deleteRecord(id)
        }
    }

    fun clearCurrentAnalysis() {
        _currentAnalysis.value = null
    }

    class Factory(
        private val skinRepository: SkinRepository,
        private val analyzeSkinUseCase: AnalyzeSkinUseCase
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SkinViewModel(skinRepository, analyzeSkinUseCase) as T
        }
    }
}

val BODY_PARTS = listOf(
    "Semua", "Wajah", "Leher", "Dada", "Punggung", "Lengan Kiri", "Lengan Kanan", "Tangan", "Tungkai", "Kaki"
)

@Composable
fun SkinScanScreen(
    viewModel: SkinViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToLineage: () -> Unit
) {
    var selectedPart by remember { mutableStateOf("Wajah") }
    var notes by remember { mutableStateOf("") }
    var samplePhotoPath by remember { mutableStateOf("skin_scan_active.jpg") }

    val currentAnalysis by viewModel.currentAnalysis.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()

    Scaffold(
        topBar = {
            MedBotTopAppBar(
                title = "Skin Lineage & Diagnosis",
                subtitle = "Pemeriksaan Lesi Kulit Berbasis ABCD",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    TextButton(onClick = onNavigateToLineage) {
                        Text("Linimasa ›", style = MaterialTheme.typography.labelMedium)
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
            // Camera / Image View Area
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth().height(220.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "Kamera",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Posisikan lesi kulit di tengah kamera",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Pastikan pencahayaan cukup dan fokus tajam",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Body Part Selector
            item {
                Text("Bagian Tubuh", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(BODY_PARTS.drop(1)) { part ->
                        FilterChip(
                            selected = selectedPart == part,
                            onClick = { selectedPart = part },
                            label = { Text(part) }
                        )
                    }
                }
            }

            // User Notes / Symptoms
            item {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Deskripsi Gejala (Contoh: gatal, bersisik, merah sejak 3 hari)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }

            // Scan Action Button
            item {
                Button(
                    onClick = {
                        viewModel.analyzeAndSave(samplePhotoPath, selectedPart, notes)
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isAnalyzing
                ) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Menganalisis Kaidah ABCD...")
                    } else {
                        Icon(Icons.Default.Analytics, contentDescription = "Diagnosa")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Diagnosa Lesi & Simpan ke Linimasa")
                    }
                }
            }

            // Analysis Result Card
            if (currentAnalysis != null) {
                val record = currentAnalysis!!
                item {
                    SkinResultCard(record = record)
                }
            }
        }
    }
}

@Composable
fun SkinLineageScreen(
    viewModel: SkinViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToScan: () -> Unit
) {
    val records by viewModel.allRecords.collectAsState()
    val selectedPart by viewModel.selectedBodyPart.collectAsState()

    val filteredRecords = remember(records, selectedPart) {
        if (selectedPart == "Semua") records else records.filter { it.bodyPart == selectedPart }
    }

    Scaffold(
        topBar = {
            MedBotTopAppBar(
                title = "Linimasa Kulit (Skin Lineage)",
                subtitle = "Pantau Perubahan Lesi Kulit Secara Berkala",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToScan) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = "Foto Baru", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Filter Row
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(BODY_PARTS) { part ->
                    FilterChip(
                        selected = selectedPart == part,
                        onClick = { viewModel.selectBodyPartFilter(part) },
                        label = { Text(part) }
                    )
                }
            }

            if (filteredRecords.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🧴", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Belum Ada Riwayat Foto Kulit",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Ambil foto lesi kulit Anda untuk memantau evolusi ABCD.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onNavigateToScan) {
                            Text("Ambil Foto Sekarang")
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredRecords) { record ->
                        SkinLineageTimelineItem(record = record, onDelete = { viewModel.deleteRecord(record.id) })
                    }
                }
            }
        }
    }
}

@Composable
fun SkinResultCard(record: SkinRecord) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Hasil Evaluasi: ${record.bodyPart}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                UrgencyBadge(urgency = record.urgencyLevel)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ABCD Metrics Grid
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Kaidah Dermatologi ABCD (Skor: ${"%.1f".format(record.abcdEvaluation.totalRiskScore)}/10.0)",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AbcdRow("A - Asimetri", record.abcdEvaluation.asymmetryDescription)
                    AbcdRow("B - Batas Tepi (Border)", record.abcdEvaluation.borderDescription)
                    AbcdRow("C - Warna (Color)", record.abcdEvaluation.colorDescription)
                    AbcdRow("D - Diameter", record.abcdEvaluation.diameterDescription)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text("Diagnosis Banding Klinis:", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            record.differentialDiagnoses.forEach { dx ->
                Text("• $dx", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(record.clinicalSummary, style = MaterialTheme.typography.bodySmall)

            Spacer(modifier = Modifier.height(10.dp))
            Text("Langkah Perawatan Mandiri:", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            record.homeCareAdvice.forEach { adv ->
                Text("✓ $adv", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun AbcdRow(label: String, desc: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(text = "$label: ", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
        Text(text = desc, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SkinLineageTimelineItem(record: SkinRecord, onDelete: () -> Unit) {
    val dateStr = remember(record.createdAt) {
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
        sdf.format(Date(record.createdAt))
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🗓️", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = dateStr, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Lokasi: ${record.bodyPart}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                UrgencyBadge(urgency = record.urgencyLevel)
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Skor Risiko ABCD: ${"%.1f".format(record.abcdEvaluation.totalRiskScore)}/10.0 • ${record.abcdEvaluation.riskClassification}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (record.userNotes.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Catatan: \"${record.userNotes}\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
