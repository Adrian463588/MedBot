package com.medbot.app.presentation.skin

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.medbot.app.core.designsystem.components.MarkdownText
import com.medbot.app.core.designsystem.components.MedBotTopAppBar
import com.medbot.app.core.designsystem.components.UrgencyBadge
import com.medbot.app.core.designsystem.components.springBounceClick
import com.medbot.app.core.designsystem.theme.UrgencyEmergencyRed
import com.medbot.app.core.designsystem.theme.UrgencyHighOrange
import com.medbot.app.core.designsystem.theme.UrgencyLowGreen
import com.medbot.app.core.designsystem.theme.UrgencyMediumYellow
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
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class SkinViewModel(
    private val skinRepository: SkinRepository,
    private val analyzeSkinUseCase: AnalyzeSkinUseCase
) : ViewModel() {

    val allRecords: StateFlow<List<SkinRecord>> = skinRepository.getAllRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedBodyPart = MutableStateFlow("Semua")
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
    val context = LocalContext.current
    var selectedPart by remember { mutableStateOf("Wajah") }
    var notes by remember { mutableStateOf("") }
    var capturedImagePath by remember { mutableStateOf<String?>(null) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    val currentAnalysis by viewModel.currentAnalysis.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()

    // Camera Capture Launcher
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            // Picture captured
        }
    }

    // Gallery Picker Launcher
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val lineageDir = File(context.filesDir, "skin_lineage").apply { if (!exists()) mkdirs() }
            val destFile = File(lineageDir, "skin_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            capturedImagePath = destFile.absolutePath
        }
    }

    Scaffold(
        topBar = {
            MedBotTopAppBar(
                title = "Skin Lineage & Diagnosis",
                subtitle = "Pemeriksaan Lesi Kulit Berbasis Kaidah ABCD",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.springBounceClick()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    TextButton(onClick = onNavigateToLineage, modifier = Modifier.springBounceClick()) {
                        Text("Linimasa ›", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
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
            // Photo View / Capture Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().height(240.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (capturedImagePath != null && File(capturedImagePath!!).exists()) {
                            val bitmap = remember(capturedImagePath) {
                                BitmapFactory.decodeFile(capturedImagePath)
                            }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Foto Lesi Kulit",
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp))
                                )
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = CircleShape,
                                    modifier = Modifier.size(64.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.CameraAlt,
                                            contentDescription = "Kamera",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Ambil atau Unggah Foto Lesi Kulit",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Pastikan foto memiliki pencahayaan cukup dan fokus tajam",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(14.dp))

                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = {
                                            val lineageDir = File(context.filesDir, "skin_lineage").apply { if (!exists()) mkdirs() }
                                            val photoFile = File(lineageDir, "skin_${System.currentTimeMillis()}.jpg")
                                            capturedImagePath = photoFile.absolutePath
                                            try {
                                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
                                                tempCameraUri = uri
                                                takePictureLauncher.launch(uri)
                                            } catch (e: Exception) {
                                                pickImageLauncher.launch("image/*")
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.springBounceClick()
                                    ) {
                                        Icon(Icons.Default.PhotoCamera, contentDescription = "Kamera")
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Kamera")
                                    }

                                    OutlinedButton(
                                        onClick = { pickImageLauncher.launch("image/*") },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.springBounceClick()
                                    ) {
                                        Icon(Icons.Default.PhotoLibrary, contentDescription = "Galeri")
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Pilih Galeri")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Body Part Chips
            item {
                Text("Bagian Tubuh Terkait", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(BODY_PARTS.drop(1)) { part ->
                        FilterChip(
                            selected = selectedPart == part,
                            onClick = { selectedPart = part },
                            label = { Text(part) },
                            modifier = Modifier.springBounceClick()
                        )
                    }
                }
            }

            // User Notes
            item {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Deskripsi Gejala") },
                    placeholder = { Text("Contoh: terasa gatal, kemerahan, muncul sejak 3 hari...") },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }

            // Action Button
            item {
                Button(
                    onClick = {
                        val path = capturedImagePath ?: File(context.filesDir, "skin_lineage/sample.jpg").absolutePath
                        viewModel.analyzeAndSave(path, selectedPart, notes)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .springBounceClick(),
                    shape = RoundedCornerShape(14.dp),
                    enabled = !isAnalyzing
                ) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Menganalisis Kaidah ABCD...")
                    } else {
                        Icon(Icons.Default.Analytics, contentDescription = "Diagnosa")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Diagnosa Kaidah ABCD & Simpan")
                    }
                }
            }

            // Diagnostic Result Card
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
                subtitle = "Pantau Perkembangan Lesi Kulit Anda",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.springBounceClick()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToScan, modifier = Modifier.springBounceClick()) {
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
            // Filter Bar
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(BODY_PARTS) { part ->
                    FilterChip(
                        selected = selectedPart == part,
                        onClick = { viewModel.selectBodyPartFilter(part) },
                        label = { Text(part) },
                        modifier = Modifier.springBounceClick()
                    )
                }
            }

            if (filteredRecords.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape,
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🧴", fontSize = 40.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Belum Ada Riwayat Foto Kulit",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Ambil foto lesi kulit Anda untuk mencatat linimasa perkembangan.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(
                            onClick = onNavigateToScan,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.springBounceClick()
                        ) {
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
    val animatedRisk by animateFloatAsState(
        targetValue = record.abcdEvaluation.totalRiskScore / 10f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "riskProgress"
    )

    val riskColor = when {
        record.abcdEvaluation.totalRiskScore >= 6.5f -> UrgencyEmergencyRed
        record.abcdEvaluation.totalRiskScore >= 4.0f -> UrgencyMediumYellow
        else -> UrgencyLowGreen
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
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

            Spacer(modifier = Modifier.height(14.dp))

            // Animated ABCD Risk Meter
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Skor Risiko Keganasan ABCD",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "${"%.1f".format(record.abcdEvaluation.totalRiskScore)} / 10.0 (${record.abcdEvaluation.riskClassification})",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = riskColor
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { animatedRisk },
                    modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                    color = riskColor,
                    trackColor = riskColor.copy(alpha = 0.2f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ABCD Details Breakdown
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Karakteristik Morfologi ABCD:",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AbcdRow("A - Asimetri", record.abcdEvaluation.asymmetryDescription)
                    AbcdRow("B - Batas Tepi (Border)", record.abcdEvaluation.borderDescription)
                    AbcdRow("C - Variasi Warna (Color)", record.abcdEvaluation.colorDescription)
                    AbcdRow("D - Diameter", record.abcdEvaluation.diameterDescription)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text("Diagnosis Banding Klinis:", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            record.differentialDiagnoses.forEach { dx ->
                Text("• $dx", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(record.clinicalSummary, style = MaterialTheme.typography.bodySmall)

            Spacer(modifier = Modifier.height(12.dp))
            Text("Langkah Perawatan Mandiri & Rujukan:", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
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
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().springBounceClick()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp).springBounceClick()) {
                    Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
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
                text = "Skor ABCD: ${"%.1f".format(record.abcdEvaluation.totalRiskScore)}/10.0 • ${record.abcdEvaluation.riskClassification}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (record.userNotes.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Catatan: \"${record.userNotes}\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
