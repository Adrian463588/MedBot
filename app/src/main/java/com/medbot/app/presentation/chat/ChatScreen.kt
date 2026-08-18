@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.medbot.app.presentation.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medbot.app.R
import com.medbot.app.core.designsystem.components.AdaptiveContent
import com.medbot.app.core.designsystem.components.AdaptiveFlowRow
import com.medbot.app.core.designsystem.components.AdaptiveListDetail
import com.medbot.app.core.designsystem.components.CitationsDialog
import com.medbot.app.core.designsystem.components.MarkdownText
import com.medbot.app.core.designsystem.components.MedBotSpacing
import com.medbot.app.core.designsystem.components.MedBotTopAppBar
import com.medbot.app.core.designsystem.components.SpecialistBadge
import com.medbot.app.core.designsystem.components.UrgencyBadge
import com.medbot.app.core.designsystem.components.springBounceClick
import com.medbot.app.domain.agents.AgentRegistry
import com.medbot.app.domain.model.ChatMessage
import com.medbot.app.domain.model.ChatSession
import com.medbot.app.presentation.common.displayName

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    initialSessionId: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToPersona: () -> Unit
) {
    val currentSessionId by viewModel.currentSessionId.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val streamingText by viewModel.streamingText.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val persona by viewModel.personaConfig.collectAsStateWithLifecycle()
    var inputText by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<String?>(null) }
    var citations by remember { mutableStateOf<List<com.medbot.app.domain.model.Citation>?>(null) }
    val newSessionTitle = stringResource(R.string.chat_new_session_title)
    val isSinglePane = LocalConfiguration.current.screenWidthDp < 840
    val listState = rememberLazyListState()
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        selectedImageUri = uri?.toString()
    }

    LaunchedEffect(initialSessionId, sessions.size) {
        when {
            initialSessionId != null -> viewModel.onEvent(ChatUiEvent.SelectSession(initialSessionId))
            currentSessionId == null && sessions.isNotEmpty() -> viewModel.onEvent(ChatUiEvent.SelectSession(sessions.first().id))
            currentSessionId == null -> viewModel.onEvent(ChatUiEvent.StartNewSession(newSessionTitle, persona.selectedAgentId))
        }
    }
    LaunchedEffect(messages.size, streamingText.length) {
        if (messages.isNotEmpty() || streamingText.isNotBlank()) {
            listState.animateScrollToItem((messages.size - 1).coerceAtLeast(0))
        }
    }

    val agent = remember(persona.selectedAgentId) { AgentRegistry.getAgentById(persona.selectedAgentId) }

    Column {
        MedBotTopAppBar(
            title = stringResource(R.string.chat_title),
             subtitle = agent.displayName(persona.language),
            navigationIcon = {
                 IconButton(onClick = { if (isSinglePane && currentSessionId != null) viewModel.clearSelection() else onNavigateBack() }, modifier = Modifier.springBounceClick()) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            },
            actions = {
                 IconButton(onClick = { viewModel.onEvent(ChatUiEvent.StartNewSession(newSessionTitle, agent.id)) }, modifier = Modifier.springBounceClick()) {
                    Icon(Icons.Filled.AddComment, contentDescription = stringResource(R.string.chat_new_session))
                }
                IconButton(onClick = onNavigateToPersona, modifier = Modifier.springBounceClick()) {
                    Icon(Icons.Filled.Tune, contentDescription = stringResource(R.string.action_persona))
                }
            }
        )
        AdaptiveContent(modifier = Modifier.weight(1f), maxContentWidth = 1_040.dp) {
            AdaptiveListDetail(
                modifier = Modifier.fillMaxSize(),
                 selectedKey = currentSessionId,
                 onBackFromDetail = { viewModel.clearSelection() },
                listPane = {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(MedBotSpacing.medium),
                        verticalArrangement = Arrangement.spacedBy(MedBotSpacing.small)
                    ) {
                        item { Text(stringResource(R.string.chat_sessions), style = MaterialTheme.typography.titleMedium) }
                        items(sessions, key = { it.id }) { session ->
                            SessionChip(
                                session = session,
                                selected = session.id == currentSessionId,
                                 onSelect = { viewModel.onEvent(ChatUiEvent.SelectSession(session.id)) },
                                 onDelete = { viewModel.onEvent(ChatUiEvent.DeleteSession(session.id)) }
                            )
                        }
                    }
                },
                detailPane = {
                    Column(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = MedBotSpacing.medium, vertical = MedBotSpacing.medium),
                    verticalArrangement = Arrangement.spacedBy(MedBotSpacing.medium)
                ) {
                    if (messages.isEmpty() && !isGenerating) {
                        item {
                             EmptyChatState(agentName = agent.displayName(persona.language))
                        }
                    }
                    items(messages, key = { it.id }) { message ->
                        ChatMessageRow(message = message, onCitationClick = { citations = message.citations })
                    }
                    if (isGenerating) {
                        item {
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(MedBotSpacing.medium)) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(MedBotSpacing.small))
                                    Text(if (streamingText.isBlank()) stringResource(R.string.chat_generating) else streamingText, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                    if (!errorMessage.isNullOrBlank()) {
                        item {
                            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(MedBotSpacing.medium)) {
                                    Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    Spacer(modifier = Modifier.width(MedBotSpacing.small))
                                    val message = when (errorMessage) {
                                        "VISION_UNAVAILABLE" -> stringResource(R.string.chat_vision_unavailable)
                                        "RAG_UNAVAILABLE" -> stringResource(R.string.chat_embedder_unavailable)
                                        else -> stringResource(R.string.chat_model_unavailable)
                                    }
                                    Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                        }
                    }
                }
                Composer(
                    inputText = inputText,
                    onInputChange = { inputText = it },
                    selectedImageUri = selectedImageUri,
                    onPickImage = { imagePicker.launch("image/*") },
                    onClearImage = { selectedImageUri = null },
                    enabled = !isGenerating && currentSessionId != null,
                    onSend = {
                        val text = inputText.trim()
                        val image = selectedImageUri
                        if (text.isNotBlank() || image != null) {
                            inputText = ""
                            selectedImageUri = null
                             viewModel.onEvent(ChatUiEvent.SendMessage(text = text, imageUri = image, imageType = "image/*"))
                        }
                    }
                )
                    }
                }
            )
        }
    }
    citations?.let { selected -> CitationsDialog(citations = selected, onDismiss = { citations = null }) }
}

@Composable
private fun SessionChip(session: ChatSession, selected: Boolean, onSelect: () -> Unit, onDelete: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onSelect,
        label = { Text(session.title, maxLines = 1) },
        trailingIcon = if (selected) {
            {
                IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = stringResource(R.string.action_delete))
                }
            }
        } else null,
        modifier = Modifier.springBounceClick()
    )
}

@Composable
private fun EmptyChatState(agentName: String) {
    Column(verticalArrangement = Arrangement.spacedBy(MedBotSpacing.small), modifier = Modifier.fillMaxWidth().padding(top = MedBotSpacing.xxLarge)) {
        SpecialistBadge(agentName = agentName, specialty = stringResource(R.string.chat_local_boundary))
        Text(stringResource(R.string.chat_empty_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.chat_empty_support), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ChatMessageRow(message: ChatMessage, onCitationClick: () -> Unit) {
    Column(horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start, modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = if (message.isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(modifier = Modifier.padding(MedBotSpacing.medium)) {
                if (message.isUser) Text(message.text, style = MaterialTheme.typography.bodyLarge)
                else MarkdownText(message.text)
                message.urgencyLevel?.let { urgency ->
                    Spacer(modifier = Modifier.padding(top = MedBotSpacing.small))
                    UrgencyBadge(urgency)
                }
                if (message.citations.isNotEmpty()) {
                    Button(onClick = onCitationClick, modifier = Modifier.padding(top = MedBotSpacing.small).springBounceClick()) {
                        Text(stringResource(R.string.chat_open_citations, message.citations.size))
                    }
                }
            }
        }
    }
}

@Composable
private fun Composer(
    inputText: String,
    onInputChange: (String) -> Unit,
    selectedImageUri: String?,
    onPickImage: () -> Unit,
    onClearImage: () -> Unit,
    enabled: Boolean,
    onSend: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = MedBotSpacing.medium, vertical = MedBotSpacing.small)) {
        selectedImageUri?.let { uri ->
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = MedBotSpacing.small, vertical = MedBotSpacing.xSmall)) {
                    Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(Uri.parse(uri).lastPathSegment ?: stringResource(R.string.chat_image_selected), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClearImage, modifier = Modifier.size(48.dp)) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close)) }
                }
            }
            Spacer(modifier = Modifier.height(MedBotSpacing.small))
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val canSend = enabled && (inputText.isNotBlank() || selectedImageUri != null)
            if (maxWidth < 420.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(MedBotSpacing.small)) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = onInputChange,
                        label = { Text(stringResource(R.string.chat_input_label)) },
                        supportingText = { Text(stringResource(R.string.chat_input_support)) },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 1,
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, keyboardType = KeyboardType.Text)
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = onPickImage, enabled = enabled, modifier = Modifier.springBounceClick()) {
                            Icon(Icons.Filled.AttachFile, contentDescription = stringResource(R.string.chat_attach_image))
                        }
                        IconButton(onClick = onSend, enabled = canSend, modifier = Modifier.springBounceClick()) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.action_send))
                        }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(MedBotSpacing.small)) {
                    IconButton(onClick = onPickImage, enabled = enabled, modifier = Modifier.springBounceClick()) {
                        Icon(Icons.Filled.AttachFile, contentDescription = stringResource(R.string.chat_attach_image))
                    }
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = onInputChange,
                        label = { Text(stringResource(R.string.chat_input_label)) },
                        supportingText = { Text(stringResource(R.string.chat_input_support)) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        minLines = 1,
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, keyboardType = KeyboardType.Text)
                    )
                    IconButton(onClick = onSend, enabled = canSend, modifier = Modifier.springBounceClick()) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.action_send))
                    }
                }
            }
        }
    }
}
