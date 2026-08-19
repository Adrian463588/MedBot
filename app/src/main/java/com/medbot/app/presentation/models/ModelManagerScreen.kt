@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.medbot.app.presentation.models

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    var showCustomDialog by rememberSaveable { mutableStateOf(false) }

    val filteredModels = remember(allModels, selectedTab) {
        when (selectedTab) {
            1 -> allModels.filter { !it.isMultimodal }
            2 -> allModels.filter { it.isMultimodal }
            else -> allModels
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.onEvent(ModelUiEvent.SelectSafUri(uri.toString()))
    }

    Column {
        MedBotTopAppBar(
            title = stringResource(R.string.models_title),
            subtitle = stringResource(R.string.models_subtitle),
            navigationIcon = {
                IconButton(onClick = onNavigateBack, modifier = Modifier.springBounceClick()) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            },
            actions = {
                IconButton(onClick = { showCustomDialog = !showCustomDialog }, modifier = Modifier.springBounceClick()) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.models_custom_heading))
                }
            }
        )

        AdaptiveContent(modifier = Modifier.weight(1f), maxContentWidth = 840.dp) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = MedBotSpacing.medium, vertical = MedBotSpacing.medium),
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
                                text = safName?.let { stringResource(R.string.models_selected_file, it) } ?: stringResource(R.string.models_no_file),
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
                                    onClick = { picker.launch(arrayOf("application/octet-stream", "application/*", "*/*")) },
                                    modifier = Modifier.springBounceClick()
                                ) {
                                    Icon(Icons.Filled.FolderOpen, contentDescription = null)
                                    Spacer(modifier = Modifier.width(MedBotSpacing.small))
                                    Text(stringResource(R.string.models_choose_file))
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

                if (showCustomDialog) {
                    item {
                        CustomModelInputCard(
                            onAdd = { name, url, isVision ->
                                viewModel.onEvent(ModelUiEvent.AddCustomModel(name, url, isVision))
                                showCustomDialog = false
                            },
                            onCancel = { showCustomDialog = false }
                        )
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
                        PrimaryTabRow(selectedTabIndex = selectedTab) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text(stringResource(R.string.models_tab_all)) }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text(stringResource(R.string.models_tab_llm)) }
                            )
                            Tab(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                text = { Text(stringResource(R.string.models_tab_vision)) }
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
                            modelPath = viewModel.installedModelPath(manifest.id).orEmpty()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomModelInputCard(
    onAdd: (String, String, Boolean) -> Unit,
    onCancel: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var isVision by rememberSaveable { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(MedBotSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(MedBotSpacing.small)
        ) {
            Text(
                text = stringResource(R.string.models_custom_heading),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.models_custom_name_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(R.string.models_custom_url_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isVision, onCheckedChange = { isVision = it })
                Spacer(modifier = Modifier.width(MedBotSpacing.xSmall))
                Text(
                    text = stringResource(R.string.models_custom_vision_checkbox),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            AdaptiveFlowRow {
                Button(
                    onClick = { onAdd(name, url, isVision) },
                    enabled = url.startsWith("https://", ignoreCase = true) && url.contains(".litertlm", ignoreCase = true),
                    modifier = Modifier.springBounceClick()
                ) {
                    Text(stringResource(R.string.models_custom_add_button))
                }
                TextButton(onClick = onCancel, modifier = Modifier.springBounceClick()) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    }
}

@Composable
private fun modelMessageText(message: ModelMessage?): String {
    return when (message?.kind) {
        ModelMessageKind.LOADED -> stringResource(R.string.models_loaded)
        ModelMessageKind.UNAVAILABLE -> stringResource(R.string.models_load_unavailable)
        ModelMessageKind.UNLOADED -> stringResource(R.string.models_unloaded)
        ModelMessageKind.DELETED -> stringResource(R.string.models_deleted)
        ModelMessageKind.PERMISSION_REQUIRED -> stringResource(R.string.models_permission_required)
        ModelMessageKind.CUSTOM_ADDED -> stringResource(R.string.models_custom_added)
        null -> ""
    }
}

@Composable
private fun ModelDownloadRow(
    manifest: ModelManifest,
    viewModel: ModelViewModel,
    isLoaded: Boolean,
    modelPath: String
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

            AdaptiveFlowRow {
                when (progress.status) {
                    ModelDownloadStatus.NOT_DOWNLOADED -> {
                        Button(
                            onClick = { viewModel.onEvent(ModelUiEvent.StartDownload(manifest.id)) },
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
                                onClick = { viewModel.onEvent(ModelUiEvent.LoadModel(modelPath)) },
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
                        Button(
                            onClick = { viewModel.onEvent(ModelUiEvent.StartDownload(manifest.id)) },
                            modifier = Modifier.springBounceClick()
                        ) {
                            Text(stringResource(R.string.models_retry))
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

