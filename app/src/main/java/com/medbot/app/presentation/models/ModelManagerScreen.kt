package com.medbot.app.presentation.models

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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.medbot.app.core.designsystem.theme.UrgencyLowGreen
import com.medbot.app.data.ai.ModelRegistry
import com.medbot.app.domain.model.DownloadProgress
import com.medbot.app.domain.model.ModelDownloadStatus
import com.medbot.app.domain.model.ModelManifest
import com.medbot.app.domain.repository.ModelRepository
import com.medbot.app.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModelViewModel @Inject constructor(
    private val modelRepository: ModelRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val availableModels: List<ModelManifest> = modelRepository.getAvailableOnlineModels()

    val safFolderUri: StateFlow<String?> = userPreferencesRepository.safModelFolderUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isModelLoaded = MutableStateFlow(modelRepository.isModelLoaded())
    val isModelLoaded: StateFlow<Boolean> = _isModelLoaded.asStateFlow()

    private val _activeModelName = MutableStateFlow(modelRepository.getActiveModelName())
    val activeModelName: StateFlow<String?> = _activeModelName.asStateFlow()

    private val _modelMessage = MutableStateFlow<String?>(null)
    val modelMessage: StateFlow<String?> = _modelMessage.asStateFlow()

    private val _selectedBackend = MutableStateFlow("AUTO")
    val selectedBackend: StateFlow<String> = _selectedBackend.asStateFlow()

    fun selectBackend(backend: String) {
        _selectedBackend.value = backend
    }

    fun startDownload(modelId: String) {
        viewModelScope.launch {
            modelRepository.startDownload(modelId)
        }
    }

    fun pauseDownload(modelId: String) {
        viewModelScope.launch {
            modelRepository.pauseDownload(modelId)
        }
    }

    fun cancelDownload(modelId: String) {
        viewModelScope.launch {
            modelRepository.cancelDownload(modelId)
        }
    }

    fun loadModel(path: String) {
        viewModelScope.launch {
            val loaded = modelRepository.loadModelToRam(path, _selectedBackend.value)
            _isModelLoaded.value = loaded
            _activeModelName.value = modelRepository.getActiveModelName()
            _modelMessage.value = if (loaded) {
                "Model lokal berhasil diinisialisasi."
            } else {
                "MODEL_UNAVAILABLE: berkas tidak lolos validasi atau engine LiteRT-LM gagal diinisialisasi."
            }
        }
    }

    fun unloadModel() {
        viewModelScope.launch {
            modelRepository.unloadModel()
            _isModelLoaded.value = false
            _activeModelName.value = null
            _modelMessage.value = "Model lokal dilepas dari memori."
        }
    }

    fun setSafFolder(uri: String?) {
        viewModelScope.launch {
            userPreferencesRepository.setSafModelFolderUri(uri)
            if (uri != null) {
                loadModel(uri)
            } else {
                modelRepository.unloadModel()
                _isModelLoaded.value = false
                _activeModelName.value = null
                _modelMessage.value = null
            }
        }
    }

    fun getDownloadFlow(modelId: String): StateFlow<DownloadProgress?> {
        return modelRepository.getDownloadProgress(modelId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    }

}

@Composable
fun ModelManagerScreen(
    viewModel: ModelViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val safUri by viewModel.safFolderUri.collectAsStateWithLifecycle()
    val isLoaded by viewModel.isModelLoaded.collectAsStateWithLifecycle()
    val modelName by viewModel.activeModelName.collectAsStateWithLifecycle()
    val selectedBackend by viewModel.selectedBackend.collectAsStateWithLifecycle()
    val modelMessage by viewModel.modelMessage.collectAsStateWithLifecycle()

    val safModelName = remember(safUri) {
        safUri?.let { uriString ->
            context.contentResolver.query(
                Uri.parse(uriString),
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }
    }

    // Native SAF model-file launcher. The engine needs a real file, not a tree URI.
    val safModelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.setSafFolder(uri.toString())
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            MedBotTopAppBar(
                title = "Model Manager & Edge AI",
                subtitle = "Pemuatan Dual-Mode: SAF & In-App Downloader",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.springBounceClick()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
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
            // Mode 1: Storage Access Framework (SAF) Folder Loader
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SdStorage, contentDescription = "SAF", tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Mode 1: Folder SAF Lokal (Offline)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (safUri != null) {
                                "Berkas model terpilih: ${safModelName ?: "URI SAF tersimpan"}"
                            } else {
                                "Pilih berkas model .litertlm yang benar-benar tersedia di perangkat melalui Storage Access Framework."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    safModelPickerLauncher.launch(arrayOf("application/octet-stream", "application/*", "*/*"))
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.springBounceClick()
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = "Pilih Berkas Model")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pilih Berkas Model")
                            }
                            if (safUri != null) {
                                OutlinedButton(
                                    onClick = { viewModel.setSafFolder(null) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.springBounceClick()
                                ) {
                                    Text("Reset")
                                }
                            }
                        }
                    }
                }
            }

            if (!modelMessage.isNullOrBlank()) {
                item {
                    Surface(
                        color = if (isLoaded) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = modelMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            // Backend Hardware Accelerator Selector
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Akselerator Inferensi Perangkat", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("AUTO", "GPU", "CPU").forEach { backend ->
                                FilterChip(
                                    selected = selectedBackend == backend,
                                    onClick = { viewModel.selectBackend(backend) },
                                    label = { Text(backend) },
                                    modifier = Modifier.springBounceClick()
                                )
                            }
                        }
                    }
                }
            }

            // Mode 2: Official Models In-App Downloader
            item {
                Text(
                    text = "Mode 2: Bundel Model Resmi (In-App Downloader)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Downloader hanya aktif jika manifest resmi berisi URL, ukuran, dan SHA-256 terverifikasi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (viewModel.availableModels.isEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "MODEL_UNAVAILABLE: belum ada manifest model resmi yang dapat diverifikasi pada build ini.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            items(viewModel.availableModels) { manifest ->
                ModelDownloadItemCard(
                    manifest = manifest,
                    viewModel = viewModel,
                    isLoaded = isLoaded && modelName?.contains(manifest.id) == true
                )
            }
        }
    }
}

@Composable
fun ModelDownloadItemCard(
    manifest: ModelManifest,
    viewModel: ModelViewModel,
    isLoaded: Boolean
) {
    val progressFlow = remember(manifest.id) { viewModel.getDownloadFlow(manifest.id) }
    val progressState by progressFlow.collectAsStateWithLifecycle()
    val progress = progressState ?: DownloadProgress(manifest.id, 0, manifest.sizeBytes, 0, ModelDownloadStatus.NOT_DOWNLOADED)

    val sizeMb = remember(manifest.sizeBytes) {
        "%.1f GB".format(manifest.sizeBytes.toDouble() / (1024.0 * 1024.0 * 1024.0))
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = manifest.displayName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Ukuran: $sizeMb • Min RAM: ${manifest.minimumRamMb} MB • Format: ${manifest.format}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isLoaded) {
                    Surface(
                        color = UrgencyLowGreen.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Aktif di RAM",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = UrgencyLowGreen,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(text = manifest.description, style = MaterialTheme.typography.bodySmall)

            // Progress bar during downloading
            if (progress.status == ModelDownloadStatus.DOWNLOADING) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress.progressPercent / 100f },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Mengunduh: ${"%.1f".format(progress.progressPercent)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${"%.1f".format(progress.bytesDownloaded.toDouble() / (1024 * 1024))} MB / ${"%.1f".format(progress.totalBytes.toDouble() / (1024 * 1024))} MB",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons with microinteraction bounce
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (progress.status) {
                    ModelDownloadStatus.NOT_DOWNLOADED -> {
                        Button(
                            onClick = { viewModel.startDownload(manifest.id) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.springBounceClick()
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Unduh")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Unduh Model ($sizeMb)")
                        }
                    }
                    ModelDownloadStatus.DOWNLOADING -> {
                        Button(
                            onClick = { viewModel.pauseDownload(manifest.id) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.springBounceClick()
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = "Jeda")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Jeda")
                        }
                        OutlinedButton(
                            onClick = { viewModel.cancelDownload(manifest.id) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.springBounceClick()
                        ) {
                            Text("Batal")
                        }
                    }
                    ModelDownloadStatus.PAUSED -> {
                        Button(
                            onClick = { viewModel.startDownload(manifest.id) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.springBounceClick()
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Lanjutkan")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Lanjutkan Unduh")
                        }
                    }
                    ModelDownloadStatus.READY_TO_LOAD, ModelDownloadStatus.LOADED_IN_RAM -> {
                        if (!isLoaded) {
                            Button(
                                onClick = { viewModel.loadModel("/data/data/com.medbot.app/files/models/${manifest.id}.litertlm") },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.springBounceClick()
                            ) {
                                Icon(Icons.Default.Memory, contentDescription = "Muat ke RAM")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Muat ke RAM Perangkat")
                            }
                        } else {
                            OutlinedButton(
                                onClick = { viewModel.unloadModel() },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.springBounceClick()
                            ) {
                                Text("Lepas dari RAM (Unload)")
                            }
                        }
                    }
                    ModelDownloadStatus.ERROR -> {
                        Button(
                            onClick = { viewModel.startDownload(manifest.id) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.springBounceClick()
                        ) {
                            Text("Coba Unduh Ulang")
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}
