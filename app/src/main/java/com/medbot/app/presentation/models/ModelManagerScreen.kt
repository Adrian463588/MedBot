@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.medbot.app.presentation.models

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
    val modelName by viewModel.activeModelName.collectAsStateWithLifecycle()
    val backend by viewModel.selectedBackend.collectAsStateWithLifecycle()
    val message by viewModel.modelMessage.collectAsStateWithLifecycle()
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
            }
        )
        AdaptiveContent(modifier = Modifier.weight(1f), maxContentWidth = 840.dp) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = MedBotSpacing.medium, vertical = MedBotSpacing.medium),
                verticalArrangement = Arrangement.spacedBy(MedBotSpacing.large)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(MedBotSpacing.small)) {
                        Text(stringResource(R.string.models_local_heading), style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = safName?.let { stringResource(R.string.models_selected_file, it) } ?: stringResource(R.string.models_no_file),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        AdaptiveFlowRow {
                            Button(onClick = { picker.launch(arrayOf("application/octet-stream", "application/*", "*/*")) }, modifier = Modifier.springBounceClick()) {
                                Icon(Icons.Filled.Storage, contentDescription = null)
                                Spacer(modifier = Modifier.width(MedBotSpacing.small))
                                Text(stringResource(R.string.models_choose_file))
                            }
                            if (safUri != null) {
                                 OutlinedButton(onClick = { viewModel.onEvent(ModelUiEvent.ClearSafUri) }, modifier = Modifier.springBounceClick()) {
                                    Text(stringResource(R.string.action_clear))
                                }
                            }
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(MedBotSpacing.small)) {
                        Text(stringResource(R.string.models_backend_heading), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        AdaptiveFlowRow {
                            listOf("AUTO", "CPU", "GPU").forEach { option ->
                                 FilterChip(selected = backend == option, onClick = { viewModel.onEvent(ModelUiEvent.SelectBackend(option)) }, label = { Text(option) }, modifier = Modifier.springBounceClick())
                            }
                        }
                    }
                }
                if (message != null) {
                    item {
                        Surface(color = if (isLoaded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
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
                        Text(stringResource(R.string.models_online_heading), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.models_online_support), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (viewModel.availableModels.isEmpty()) {
                    item {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.models_unavailable), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(MedBotSpacing.large))
                        }
                    }
                } else {
                    items(viewModel.availableModels, key = { it.id }) { manifest ->
                        ModelDownloadRow(
                            manifest = manifest,
                            viewModel = viewModel,
                            isLoaded = isLoaded && modelName?.contains(manifest.id) == true,
                            modelPath = viewModel.installedModelPath(manifest.id).orEmpty()
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
        ModelMessageKind.UNAVAILABLE -> stringResource(R.string.models_load_unavailable)
        ModelMessageKind.UNLOADED -> stringResource(R.string.models_unloaded)
        ModelMessageKind.DELETED -> stringResource(R.string.models_deleted)
        ModelMessageKind.PERMISSION_REQUIRED -> stringResource(R.string.models_permission_required)
        null -> ""
    }
}

@Composable
private fun ModelDownloadRow(manifest: ModelManifest, viewModel: ModelViewModel, isLoaded: Boolean, modelPath: String) {
    val progressFlow = remember(manifest.id) { viewModel.getDownloadFlow(manifest.id) }
    val progressState by progressFlow.collectAsStateWithLifecycle()
    val progress = progressState ?: DownloadProgress(manifest.id, 0, manifest.sizeBytes, 0, ModelDownloadStatus.NOT_DOWNLOADED)
    val sizeGb = manifest.sizeBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)

    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(MedBotSpacing.medium), verticalArrangement = Arrangement.spacedBy(MedBotSpacing.small)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(MedBotSpacing.medium))
                Column(modifier = Modifier.weight(1f)) {
                    Text(manifest.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.models_manifest_meta, sizeGb, manifest.minimumRamMb, manifest.format), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(manifest.description, style = MaterialTheme.typography.bodyMedium)
            if (progress.status == ModelDownloadStatus.DOWNLOADING) {
                LinearProgressIndicator(progress = { progress.progressPercent / 100f }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.models_download_progress, progress.progressPercent), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            AdaptiveFlowRow {
                when (progress.status) {
                     ModelDownloadStatus.NOT_DOWNLOADED -> Button(onClick = { viewModel.onEvent(ModelUiEvent.StartDownload(manifest.id)) }, modifier = Modifier.springBounceClick()) { Icon(Icons.Filled.Download, contentDescription = null); Spacer(modifier = Modifier.width(MedBotSpacing.small)); Text(stringResource(R.string.models_download)) }
                     ModelDownloadStatus.DOWNLOADING -> { Button(onClick = { viewModel.onEvent(ModelUiEvent.PauseDownload(manifest.id)) }, modifier = Modifier.springBounceClick()) { Icon(Icons.Filled.Pause, contentDescription = null); Spacer(modifier = Modifier.width(MedBotSpacing.small)); Text(stringResource(R.string.models_pause)) }; OutlinedButton(onClick = { viewModel.onEvent(ModelUiEvent.CancelDownload(manifest.id)) }, modifier = Modifier.springBounceClick()) { Text(stringResource(R.string.action_cancel)) } }
                     ModelDownloadStatus.PAUSED -> Button(onClick = { viewModel.onEvent(ModelUiEvent.StartDownload(manifest.id)) }, modifier = Modifier.springBounceClick()) { Icon(Icons.Filled.PlayArrow, contentDescription = null); Spacer(modifier = Modifier.width(MedBotSpacing.small)); Text(stringResource(R.string.models_resume)) }
                    ModelDownloadStatus.READY_TO_LOAD, ModelDownloadStatus.LOADED_IN_RAM -> {
                        if (isLoaded) {
                             OutlinedButton(onClick = { viewModel.onEvent(ModelUiEvent.UnloadModel) }, modifier = Modifier.springBounceClick()) { Text(stringResource(R.string.models_unload)) }
                        } else {
                             Button(onClick = { viewModel.onEvent(ModelUiEvent.LoadModel(modelPath)) }, modifier = Modifier.springBounceClick()) { Icon(Icons.Filled.Memory, contentDescription = null); Spacer(modifier = Modifier.width(MedBotSpacing.small)); Text(stringResource(R.string.models_load)) }
                             OutlinedButton(onClick = { viewModel.onEvent(ModelUiEvent.DeleteModel(manifest.id)) }, modifier = Modifier.springBounceClick()) { Text(stringResource(R.string.models_delete)) }
                        }
                    }
                     ModelDownloadStatus.ERROR -> Button(onClick = { viewModel.onEvent(ModelUiEvent.StartDownload(manifest.id)) }, modifier = Modifier.springBounceClick()) { Text(stringResource(R.string.models_retry)) }
                    else -> {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}
