@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.medbot.app.presentation.models

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medbot.app.R
import com.medbot.app.core.designsystem.components.AdaptiveContent
import com.medbot.app.core.designsystem.components.AdaptiveFlowRow
import com.medbot.app.core.designsystem.components.MedBotSpacing
import com.medbot.app.core.designsystem.components.MedBotTopAppBar
import com.medbot.app.core.designsystem.components.springBounceClick
import com.medbot.app.domain.model.DownloadProgress
import com.medbot.app.domain.model.ModelDownloadStatus
import com.medbot.app.domain.model.ModelManifest
import com.medbot.app.domain.model.ModelManifestValidator

private fun ModelManifest.isDisplayableVerifiedManifest(): Boolean =
    ModelManifestValidator.isVerified(this)

@Composable
private fun downloadErrorText(errorCode: String?): String? {
    return when (errorCode) {
        null, "" -> null
        "STORAGE_PERMISSION_REQUIRED" -> stringResource(R.string.models_download_error_permission)
        "STORAGE_UNAVAILABLE", "MODEL_STORAGE_DESTINATION_REQUIRED" ->
            stringResource(R.string.models_download_error_storage)
        "SHA256_MISMATCH", "TARGET_EXISTS_INVALID" ->
            stringResource(R.string.models_download_error_checksum)
        "PARTIAL_ARTIFACT_OVERSIZED", "PARTIAL_ARTIFACT_MISSING", "RESPONSE_OVERSIZED",
        "RESUME_VALIDATOR_MISSING" ->
            stringResource(R.string.models_download_error_partial)
        "DOWNLOAD_IO_FAILED", "HTTP_ERROR", "HTTP_RANGE_REQUIRED", "HTTP_RANGE_MISMATCH",
        "HTTP_SOURCE_CHANGED", "RESPONSE_BODY_EMPTY", "DOWNLOAD_FAILED" ->
            stringResource(R.string.models_download_error_network)
        else -> stringResource(R.string.models_download_error_generic, errorCode)
    }
}

@Composable
fun ModelManagerScreen(viewModel: ModelViewModel, onNavigateBack: () -> Unit) {
    val safUri by viewModel.safFolderUri.collectAsStateWithLifecycle()
    val safName by viewModel.safFileName.collectAsStateWithLifecycle()
    val isLoaded by viewModel.isModelLoaded.collectAsStateWithLifecycle()
    val activeModelName by viewModel.activeModelName.collectAsStateWithLifecycle()
    val backend by viewModel.selectedBackend.collectAsStateWithLifecycle()
    val message by viewModel.modelMessage.collectAsStateWithLifecycle()
    val allModels by viewModel.availableModels.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val filteredModels = remember(allModels, selectedTab) {
        val verifiedModels = allModels.filter { it.isDisplayableVerifiedManifest() }
        when (selectedTab) {
            1 -> verifiedModels.filter { !it.isMultimodal }
            2 -> verifiedModels.filter { it.isMultimodal }
            else -> verifiedModels
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.onEvent(ModelUiEvent.SelectSafUri(uri.toString()))
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.onEvent(ModelUiEvent.SelectSafFolder(uri.toString()))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        MedBotTopAppBar(
            title = stringResource(R.string.models_title),
            subtitle = stringResource(R.string.models_subtitle),
            navigationIcon = {
                IconButton(onClick = onNavigateBack, modifier = Modifier.springBounceClick()) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            },
            actions = {}
        )

        AdaptiveContent(modifier = Modifier.weight(1f).fillMaxWidth(), maxContentWidth = 840.dp) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().imePadding(),
                contentPadding = PaddingValues(
                    start = MedBotSpacing.medium,
                    top = MedBotSpacing.medium,
                    end = MedBotSpacing.medium,
                    bottom = MedBotSpacing.xxLarge
                ),
                verticalArrangement = Arrangement.spacedBy(MedBotSpacing.large)
            ) {
                item {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(
                            modifier = Modifier.padding(MedBotSpacing.large),
                            verticalArrangement = Arrangement.spacedBy(MedBotSpacing.small)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isLoaded) Icons.Filled.CheckCircle else Icons.Filled.Memory,
                                    contentDescription = null,
                                    tint = if (isLoaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(MedBotSpacing.small))
                                Text(
                                    text = stringResource(R.string.models_local_heading),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = safName?.let { stringResource(R.string.models_selected_folder, it) } ?: stringResource(R.string.models_no_folder),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (activeModelName != null && isLoaded) {
                                Text(
                                    text = stringResource(R.string.status_active_model, activeModelName.orEmpty()),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            AdaptiveFlowRow {
                                Button(
                                    onClick = { folderPicker.launch(safUri?.let(Uri::parse)) },
                                    modifier = Modifier.springBounceClick()
                                ) {
                                    Icon(Icons.Filled.FolderOpen, contentDescription = null)
                                    Spacer(modifier = Modifier.width(MedBotSpacing.small))
                                    Text(stringResource(R.string.models_choose_folder))
                                }
                                OutlinedButton(
                                    onClick = { picker.launch(arrayOf("application/octet-stream", "application/*", "*/*")) },
                                    modifier = Modifier.springBounceClick()
                                ) {
                                    Text(stringResource(R.string.models_import_file))
                                }
                                if (safUri != null) {
                                    OutlinedButton(
                                        onClick = { viewModel.onEvent(ModelUiEvent.ClearSafUri) },
                                        modifier = Modifier.springBounceClick()
                                    ) {
                                        Text(stringResource(R.string.action_clear))
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(MedBotSpacing.small)) {
                        Text(
                            text = stringResource(R.string.models_backend_heading),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        AdaptiveFlowRow {
                            listOf("AUTO", "GPU", "CPU").forEach { option ->
                                FilterChip(
                                    selected = backend == option,
                                    onClick = { viewModel.onEvent(ModelUiEvent.SelectBackend(option)) },
                                    label = { Text(option) },
                                    modifier = Modifier.springBounceClick()
                                )
                            }
                        }
                    }
                }

                if (message != null) {
                    item {
                        Surface(
                            color = if (isLoaded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = modelMessageText(message),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(MedBotSpacing.medium)
                            )
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(MedBotSpacing.small)) {
                        Text(
                            text = stringResource(R.string.models_online_heading),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.models_online_support),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        PrimaryScrollableTabRow(
                            selectedTabIndex = selectedTab,
                            modifier = Modifier.fillMaxWidth(),
                            edgePadding = MedBotSpacing.small
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                modifier = Modifier.heightIn(min = 48.dp),
                                text = { Text(stringResource(R.string.models_tab_all), maxLines = 2) }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                modifier = Modifier.heightIn(min = 48.dp),
                                text = { Text(stringResource(R.string.models_tab_llm), maxLines = 2) }
                            )
                            Tab(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                modifier = Modifier.heightIn(min = 48.dp),
                                text = { Text(stringResource(R.string.models_tab_vision), maxLines = 2) }
                            )
                        }
                    }
                }

                if (filteredModels.isEmpty()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.models_unavailable),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(MedBotSpacing.large)
                            )
                        }
                    }
                } else {
                    items(filteredModels, key = { it.id }) { manifest ->
                        ModelDownloadRow(
                            manifest = manifest,
                            viewModel = viewModel,
                            isLoaded = isLoaded && activeModelName?.contains(manifest.id) == true,
                            canStartDownload = safUri != null
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun modelMessageText(message: ModelMessage?): String {
    return when (message?.kind) {
        ModelMessageKind.LOADED -> stringResource(R.string.models_loaded)
        ModelMessageKind.UNAVAILABLE -> message.detail?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.models_load_unavailable)
        ModelMessageKind.UNLOADED -> stringResource(R.string.models_unloaded)
        ModelMessageKind.DELETED -> stringResource(R.string.models_deleted)
        ModelMessageKind.PERMISSION_REQUIRED -> stringResource(R.string.models_permission_required)
        null -> ""
    }
}

@Composable
private fun ModelDownloadRow(
    manifest: ModelManifest,
    viewModel: ModelViewModel,
    isLoaded: Boolean,
    canStartDownload: Boolean
) {
    val progressFlow = remember(manifest.id) { viewModel.getDownloadFlow(manifest.id) }
    val progressState by progressFlow.collectAsStateWithLifecycle()
    val progress = progressState ?: DownloadProgress(manifest.id, 0, manifest.sizeBytes, 0, ModelDownloadStatus.NOT_DOWNLOADED)
    val sizeGb = if (manifest.sizeBytes > 0) manifest.sizeBytes.toDouble() / (1024.0 * 1024.0 * 1024.0) else 0.0

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, if (isLoaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(MedBotSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(MedBotSpacing.small)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (manifest.isMultimodal) Icons.Filled.Visibility else Icons.Filled.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(MedBotSpacing.medium))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = manifest.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.models_manifest_meta, sizeGb, manifest.minimumRamMb, manifest.format),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = manifest.description,
                style = MaterialTheme.typography.bodyMedium
            )

            if (progress.status == ModelDownloadStatus.DOWNLOADING) {
                LinearProgressIndicator(
                    progress = { progress.progressPercent / 100f },
                    modifier = Modifier.fillMaxWidth().height(8.dp)
                )
                val speedKb = progress.speedBytesPerSec / 1024
                val speedText = if (speedKb > 1024) "${speedKb / 1024} MB/s" else "$speedKb KB/s"
                Text(
                    text = "${stringResource(R.string.models_download_progress, progress.progressPercent)} • $speedText",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (!canStartDownload && progress.status != ModelDownloadStatus.DOWNLOADING) {
                Text(
                    text = stringResource(R.string.models_folder_required),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            downloadErrorText(progress.errorMessage)?.let { errorText ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(MedBotSpacing.medium)
                    )
                }
            }

            AdaptiveFlowRow {
                when (progress.status) {
                    ModelDownloadStatus.NOT_DOWNLOADED -> {
                        Button(
                            onClick = { viewModel.onEvent(ModelUiEvent.StartDownload(manifest.id)) },
                            enabled = canStartDownload,
                            modifier = Modifier.springBounceClick()
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(MedBotSpacing.small))
                            Text(stringResource(R.string.models_download))
                        }
                    }
                    ModelDownloadStatus.DOWNLOADING -> {
                        Button(
                            onClick = { viewModel.onEvent(ModelUiEvent.PauseDownload(manifest.id)) },
                            modifier = Modifier.springBounceClick()
                        ) {
                            Icon(Icons.Filled.Pause, contentDescription = null)
                            Spacer(modifier = Modifier.width(MedBotSpacing.small))
                            Text(stringResource(R.string.models_pause))
                        }
                        OutlinedButton(
                            onClick = { viewModel.onEvent(ModelUiEvent.CancelDownload(manifest.id)) },
                            modifier = Modifier.springBounceClick()
                        ) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                    ModelDownloadStatus.PAUSED -> {
                        Button(
                            onClick = { viewModel.onEvent(ModelUiEvent.StartDownload(manifest.id)) },
                            enabled = canStartDownload,
                            modifier = Modifier.springBounceClick()
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(MedBotSpacing.small))
                            Text(stringResource(R.string.models_resume))
                        }
                        OutlinedButton(
                            onClick = { viewModel.onEvent(ModelUiEvent.CancelDownload(manifest.id)) },
                            modifier = Modifier.springBounceClick()
                        ) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                    ModelDownloadStatus.READY_TO_LOAD, ModelDownloadStatus.LOADED_IN_RAM -> {
                        if (isLoaded) {
                            OutlinedButton(
                                onClick = { viewModel.onEvent(ModelUiEvent.UnloadModel) },
                                modifier = Modifier.springBounceClick()
                            ) {
                                Text(stringResource(R.string.models_unload))
                            }
                        } else {
                            Button(
                                onClick = { viewModel.onEvent(ModelUiEvent.LoadModel(manifest.id)) },
                                modifier = Modifier.springBounceClick()
                            ) {
                                Icon(Icons.Filled.Memory, contentDescription = null)
                                Spacer(modifier = Modifier.width(MedBotSpacing.small))
                                Text(stringResource(R.string.models_load))
                            }
                            OutlinedButton(
                                onClick = { viewModel.onEvent(ModelUiEvent.DeleteModel(manifest.id)) },
                                modifier = Modifier.springBounceClick()
                            ) {
                                Text(stringResource(R.string.models_delete))
                            }
                        }
                    }
                    ModelDownloadStatus.ERROR -> {
                        if (canStartDownload && progress.errorMessage != "MODEL_UNAVAILABLE") {
                            Button(
                                onClick = { viewModel.onEvent(ModelUiEvent.StartDownload(manifest.id)) },
                                modifier = Modifier.springBounceClick()
                            ) {
                                Text(stringResource(R.string.models_retry))
                            }
                        }
                    }
                    else -> {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}
