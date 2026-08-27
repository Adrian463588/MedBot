@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.medbot.app.presentation.models

import android.Manifest
import android.net.Uri
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
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
import com.medbot.app.domain.model.ModelAccessRequirement

private fun ModelManifest.isDisplayableVerifiedManifest(): Boolean =
    ModelManifestValidator.isVerified(this)

@Composable
private fun downloadErrorText(errorCode: String?): String? {
    return when (errorCode) {
        null, "" -> null
        "STORAGE_PERMISSION_REQUIRED" -> stringResource(R.string.models_download_error_permission)
        "STORAGE_UNAVAILABLE", "MODEL_STORAGE_DESTINATION_REQUIRED" ->
            stringResource(R.string.models_download_error_storage)
        "SHA256_MISMATCH", "TARGET_EXISTS_INVALID", "INTEGRITY_MISMATCH" ->
            stringResource(R.string.models_download_error_checksum)
        "PARTIAL_ARTIFACT_OVERSIZED", "PARTIAL_ARTIFACT_MISSING", "RESPONSE_OVERSIZED",
        "RESUME_VALIDATOR_MISSING" ->
            stringResource(R.string.models_download_error_partial)
        "DOWNLOAD_IO_FAILED", "HTTP_ERROR", "HTTP_RANGE_REQUIRED", "HTTP_RANGE_MISMATCH",
        "HTTP_SOURCE_CHANGED", "RESPONSE_BODY_EMPTY", "DOWNLOAD_FAILED" ->
            stringResource(R.string.models_download_error_network)
        "MODEL_ACCESS_REQUIRED", "MODEL_ACCESS_TOKEN_REQUIRED", "MODEL_ACCESS_UNAUTHORIZED",
        "MODEL_TERMS_NOT_ACCEPTED" ->
            stringResource(R.string.models_access_token_required)
        else -> stringResource(R.string.models_download_error_generic, errorCode)
    }
}

private data class PendingModelDownload(
    val modelId: String,
    val accessToken: String?
)

@Composable
fun ModelManagerScreen(viewModel: ModelViewModel, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val safUri by viewModel.safFolderUri.collectAsStateWithLifecycle()
    val safName by viewModel.safFileName.collectAsStateWithLifecycle()
    val isLoaded by viewModel.isModelLoaded.collectAsStateWithLifecycle()
    val activeModelName by viewModel.activeModelName.collectAsStateWithLifecycle()
    val loadingModelId by viewModel.loadingModelId.collectAsStateWithLifecycle()
    val backend by viewModel.selectedBackend.collectAsStateWithLifecycle()
    val message by viewModel.modelMessage.collectAsStateWithLifecycle()
    val allModels by viewModel.availableModels.collectAsStateWithLifecycle()
    val hasHuggingFaceToken by viewModel.hasHuggingFaceToken.collectAsStateWithLifecycle()
    val acceptedHuggingFaceTermsRevision by viewModel.acceptedHuggingFaceTermsRevision.collectAsStateWithLifecycle()
    val hasHuggingFaceSafBackup by viewModel.hasHuggingFaceSafBackup.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    var pendingDownload by remember { mutableStateOf<PendingModelDownload?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        val request = pendingDownload
        pendingDownload = null
        request?.let {
            viewModel.onEvent(ModelUiEvent.StartDownload(it.modelId, it.accessToken))
        }
    }

    val requestDownload: (String, String?) -> Unit = { modelId, accessToken ->
        val needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        if (needsNotificationPermission) {
            pendingDownload = PendingModelDownload(modelId, accessToken)
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.onEvent(ModelUiEvent.StartDownload(modelId, accessToken))
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.reconcileSafFolder()
        viewModel.effects.collect { effect ->
            when (effect) {
                is ModelUiEffect.OpenOfficialSource -> uriHandler.openUri(effect.url)
            }
        }
    }

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
                // -----------------------------------------------------------
                // 1. SAF FOLDER & STORAGE STATUS CARD
                // -----------------------------------------------------------
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
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
                            if (loadingModelId != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(MedBotSpacing.small))
                                    Text(
                                        text = stringResource(R.string.models_loading),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            AdaptiveFlowRow {
                                Button(
                                     onClick = {
                                         folderPicker.launch(
                                             safUri.takeIf { safName != null }?.let(Uri::parse)
                                         )
                                     },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.springBounceClick()
                                ) {
                                    Icon(Icons.Filled.FolderOpen, contentDescription = null)
                                    Spacer(modifier = Modifier.width(MedBotSpacing.small))
                                    Text(stringResource(R.string.models_choose_folder))
                                }
                                OutlinedButton(
                                    onClick = { picker.launch(arrayOf("application/octet-stream", "application/*", "*/*")) },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.springBounceClick()
                                ) {
                                    Text(stringResource(R.string.models_import_file))
                                }
                                if (isLoaded) {
                                    OutlinedButton(
                                        onClick = { viewModel.onEvent(ModelUiEvent.UnloadModel) },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.springBounceClick()
                                    ) {
                                        Text(stringResource(R.string.models_unload))
                                    }
                                }
                                if (safUri != null) {
                                    OutlinedButton(
                                        onClick = { viewModel.onEvent(ModelUiEvent.ClearSafUri) },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.springBounceClick()
                                    ) {
                                        Text(stringResource(R.string.action_clear))
                                    }
                                }
                            }
                        }
                    }
                }

                // -----------------------------------------------------------
                // 3. BACKEND SELECTOR (AUTO / GPU / CPU)
                // -----------------------------------------------------------
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
                                    shape = RoundedCornerShape(8.dp),
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
                            shape = RoundedCornerShape(8.dp),
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

                // -----------------------------------------------------------
                // 4. ONLINE CATALOGUE & DOWNLOAD LIST
                // -----------------------------------------------------------
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
                        val isManifestActive = isLoaded &&
                            activeModelName?.equals(manifest.fileName, ignoreCase = true) == true
                        ModelDownloadRow(
                            manifest = manifest,
                            viewModel = viewModel,
                            isLoaded = isManifestActive,
                             canStartDownload = safName != null,
                             loadingModelId = loadingModelId,
                              hasHuggingFaceToken = hasHuggingFaceToken,
                             acceptedHuggingFaceTermsRevision = acceptedHuggingFaceTermsRevision,
                              hasHuggingFaceSafBackup = hasHuggingFaceSafBackup,
                             onStartDownload = requestDownload
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
        ModelMessageKind.DOWNLOAD_FAILED -> downloadErrorText(message.detail)
            ?: stringResource(R.string.models_download_error_generic, message.detail.orEmpty())
        null -> ""
    }
}

@Composable
private fun ModelDownloadRow(
    manifest: ModelManifest,
    viewModel: ModelViewModel,
    isLoaded: Boolean,
    canStartDownload: Boolean,
    loadingModelId: String?,
    hasHuggingFaceToken: Boolean,
    acceptedHuggingFaceTermsRevision: String?,
    hasHuggingFaceSafBackup: Boolean,
    onStartDownload: (String, String?) -> Unit
) {
    val progressFlow = remember(manifest.id) { viewModel.getDownloadFlow(manifest.id) }
    val progressState by progressFlow.collectAsStateWithLifecycle()
    val progress = progressState ?: DownloadProgress(manifest.id, 0, manifest.sizeBytes, 0, ModelDownloadStatus.NOT_DOWNLOADED)
    val sizeGb = if (manifest.sizeBytes > 0) manifest.sizeBytes.toDouble() / (1024.0 * 1024.0 * 1024.0) else 0.0
    val requiresOfficialAccess = manifest.accessRequirement != ModelAccessRequirement.NONE
    val isLoading = loadingModelId == manifest.id
    val anyModelLoading = loadingModelId != null
    var accessToken by rememberSaveable(manifest.id) { mutableStateOf("") }

    val isDownloaded = progress.status == ModelDownloadStatus.READY_TO_LOAD || progress.status == ModelDownloadStatus.LOADED_IN_RAM
    val accessRejected = progress.errorMessage == "MODEL_ACCESS_UNAUTHORIZED"
    val tokenAvailableForUi = hasHuggingFaceToken && !accessRejected
    val termsAccepted = acceptedHuggingFaceTermsRevision == manifest.sourceRevision
    val accessSetupComplete = !requiresOfficialAccess || (tokenAvailableForUi && termsAccepted)
    val accessReadyForAction = !requiresOfficialAccess ||
        (termsAccepted && (tokenAvailableForUi || accessToken.isNotBlank()))
    val downloadEnabled = canStartDownload && accessReadyForAction

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            if (isLoaded) 1.5.dp else 1.dp,
            if (isLoaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = manifest.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        if (isLoaded) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.models_status_active),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else if (isDownloaded) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.padding(start = 4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.models_status_ready),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Capability & Specs tags
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (manifest.isMultimodal) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = if (manifest.isMultimodal) stringResource(R.string.models_for_vision) else stringResource(R.string.models_for_chat),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (manifest.isMultimodal) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Text(
                            text = stringResource(R.string.models_manifest_meta, sizeGb, manifest.minimumRamMb, manifest.format),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            Text(
                text = manifest.description,
                style = MaterialTheme.typography.bodyMedium
            )

            if (requiresOfficialAccess) {
                if (accessSetupComplete) {
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.padding(start = MedBotSpacing.small)) {
                            Text(
                                text = stringResource(R.string.models_access_ready),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.models_access_ready_support),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (canStartDownload && hasHuggingFaceSafBackup) {
                                Text(
                                    text = stringResource(R.string.models_access_saf_backup_ready),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.models_access_required),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(MedBotSpacing.medium)
                        )
                    }
                    if (!tokenAvailableForUi) {
                        androidx.compose.material3.OutlinedTextField(
                            value = accessToken,
                            onValueChange = { accessToken = it },
                            label = { Text(stringResource(R.string.models_access_token_label)) },
                            supportingText = {
                                Text(stringResource(R.string.models_access_token_support))
                            },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (hasHuggingFaceSafBackup) {
                            Text(
                                text = stringResource(R.string.models_access_saf_backup_reentry),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.models_access_token_saved),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!termsAccepted) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable(role = Role.Checkbox) {
                                    viewModel.onEvent(
                                        ModelUiEvent.AcceptHuggingFaceTerms(manifest.sourceRevision)
                                    )
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = false,
                                onCheckedChange = null
                            )
                            Text(
                                text = stringResource(R.string.models_access_terms_confirmation),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = MedBotSpacing.small)
                            )
                        }
                    }
                }
            }

            if (progress.status == ModelDownloadStatus.DOWNLOADING) {
                LinearProgressIndicator(
                    progress = { progress.progressPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                )
                val speedKb = progress.speedBytesPerSec / 1024
                val speedText = if (speedKb > 1024) "${speedKb / 1024} MB/s" else "$speedKb KB/s"
                Text(
                    text = "${stringResource(R.string.models_download_progress, progress.progressPercent)} • $speedText",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (progress.status == ModelDownloadStatus.PREPARING) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(MedBotSpacing.small))
                    Text(
                        text = stringResource(R.string.models_download_preparing),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (progress.status == ModelDownloadStatus.VERIFYING) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(6.dp)
                )
                Text(
                    text = stringResource(R.string.models_download_verifying),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (!requiresOfficialAccess && !canStartDownload &&
                progress.status != ModelDownloadStatus.DOWNLOADING &&
                progress.status != ModelDownloadStatus.PREPARING &&
                progress.status != ModelDownloadStatus.VERIFYING &&
                !isDownloaded
            ) {
                Text(
                    text = stringResource(R.string.models_folder_required),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!isDownloaded) {
                downloadErrorText(progress.errorMessage)?.let { errorText ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
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
            }

            AdaptiveFlowRow {
                if (isDownloaded) {
                    if (isLoaded) {
                        OutlinedButton(
                            onClick = { viewModel.onEvent(ModelUiEvent.UnloadModel) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.springBounceClick()
                        ) {
                            Text(stringResource(R.string.models_unload))
                        }
                    } else {
                        Button(
                            onClick = { viewModel.onEvent(ModelUiEvent.LoadModel(manifest.id)) },
                            enabled = !anyModelLoading,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.springBounceClick()
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null)
                            }
                            Spacer(modifier = Modifier.width(MedBotSpacing.small))
                            Text(
                                stringResource(
                                    if (isLoading) R.string.models_loading
                                    else R.string.models_switch_model
                                ),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        OutlinedButton(
                            onClick = { viewModel.onEvent(ModelUiEvent.DeleteModel(manifest.id)) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.springBounceClick()
                        ) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.models_delete))
                        }
                    }
                } else {
                    when (progress.status) {
                        ModelDownloadStatus.NOT_DOWNLOADED -> {
                            if (requiresOfficialAccess) {
                                OutlinedButton(
                                    onClick = { viewModel.onEvent(ModelUiEvent.OpenOfficialSource(manifest.id)) },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.springBounceClick()
                                ) {
                                    Text(stringResource(R.string.models_open_official_source))
                                }
                            }
                            Button(
                                onClick = {
                                    onStartDownload(manifest.id, accessToken.takeIf { it.isNotBlank() })
                                },
                                enabled = downloadEnabled,
                                shape = RoundedCornerShape(8.dp),
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
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.springBounceClick()
                            ) {
                                Icon(Icons.Filled.Pause, contentDescription = null)
                                Spacer(modifier = Modifier.width(MedBotSpacing.small))
                                Text(stringResource(R.string.models_pause))
                            }
                            OutlinedButton(
                                onClick = { viewModel.onEvent(ModelUiEvent.CancelDownload(manifest.id)) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.springBounceClick()
                            ) {
                                Text(stringResource(R.string.action_cancel))
                            }
                        }
                        ModelDownloadStatus.PAUSED -> {
                            if (requiresOfficialAccess) {
                                OutlinedButton(
                                    onClick = { viewModel.onEvent(ModelUiEvent.OpenOfficialSource(manifest.id)) },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.springBounceClick()
                                ) {
                                    Text(stringResource(R.string.models_open_official_source))
                                }
                            }
                            Button(
                                onClick = {
                                    onStartDownload(manifest.id, accessToken.takeIf { it.isNotBlank() })
                                },
                                enabled = downloadEnabled,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.springBounceClick()
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(MedBotSpacing.small))
                                Text(stringResource(R.string.models_resume))
                            }
                            OutlinedButton(
                                onClick = { viewModel.onEvent(ModelUiEvent.CancelDownload(manifest.id)) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.springBounceClick()
                            ) {
                                Text(stringResource(R.string.action_cancel))
                            }
                        }
                        ModelDownloadStatus.ERROR -> {
                            if (requiresOfficialAccess) {
                                OutlinedButton(
                                    onClick = { viewModel.onEvent(ModelUiEvent.OpenOfficialSource(manifest.id)) },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.springBounceClick()
                                ) {
                                    Text(stringResource(R.string.models_open_official_source))
                                }
                            }
                            if (downloadEnabled && progress.errorMessage != "MODEL_UNAVAILABLE") {
                                Button(
                                    onClick = {
                                        onStartDownload(manifest.id, accessToken.takeIf { it.isNotBlank() })
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.springBounceClick()
                                ) {
                                    Text(stringResource(R.string.models_retry))
                                }
                            }
                        }
                        ModelDownloadStatus.PREPARING,
                        ModelDownloadStatus.VERIFYING -> {
                            OutlinedButton(
                                onClick = { viewModel.onEvent(ModelUiEvent.CancelDownload(manifest.id)) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.springBounceClick()
                            ) {
                                Text(stringResource(R.string.action_cancel))
                            }
                        }
                        else -> {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                    if (requiresOfficialAccess) {
                        if (hasHuggingFaceToken) {
                            OutlinedButton(
                                onClick = { viewModel.onEvent(ModelUiEvent.ClearHuggingFaceToken) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.springBounceClick()
                            ) {
                                Text(stringResource(R.string.models_clear_access_token))
                            }
                        }
                        Text(
                            text = stringResource(R.string.models_gated_import_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
