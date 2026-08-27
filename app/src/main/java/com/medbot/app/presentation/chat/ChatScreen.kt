@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.medbot.app.presentation.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medbot.app.R
import com.medbot.app.core.designsystem.components.AdaptiveContent
import com.medbot.app.core.designsystem.components.CitationsDialog
import com.medbot.app.core.designsystem.components.MarkdownText
import com.medbot.app.core.designsystem.components.MedBotSpacing
import com.medbot.app.core.designsystem.components.MedBotTopAppBar
import com.medbot.app.core.designsystem.components.SpecialistBadge
import com.medbot.app.core.designsystem.components.UrgencyBadge
import com.medbot.app.core.designsystem.components.springBounceClick
import com.medbot.app.domain.agents.AgentRegistry
import com.medbot.app.domain.model.AppLanguage
import com.medbot.app.domain.model.ChatMessage
import com.medbot.app.domain.model.ChatGenerationPhase
import com.medbot.app.domain.model.ChatSession
import com.medbot.app.domain.model.PersonaConfig
import com.medbot.app.presentation.common.displayName
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
    val generationPhase by viewModel.generationPhase.collectAsStateWithLifecycle()
    val streamingText by viewModel.streamingText.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val persona by viewModel.personaConfig.collectAsStateWithLifecycle()
    val onlineEvidenceEnabled by viewModel.onlineEvidenceEnabled.collectAsStateWithLifecycle()
    var inputText by rememberSaveable { mutableStateOf("") }
    var selectedImageUri by rememberSaveable { mutableStateOf<String?>(null) }
    var citations by remember { mutableStateOf<List<com.medbot.app.domain.model.Citation>?>(null) }
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        selectedImageUri = uri?.toString()
    }

    // Load initial session ONLY if explicit ID provided. No empty sessions created on launch!
    LaunchedEffect(initialSessionId) {
        if (initialSessionId != null) {
            viewModel.onEvent(ChatUiEvent.SelectSession(initialSessionId))
        }
    }

    LaunchedEffect(messages.size, streamingText.length, isGenerating) {
        if (messages.isNotEmpty() || streamingText.isNotBlank()) {
            // The streaming row is appended after persisted messages. Keep
            // the active generation and its cancel action visible while the
            // local model emits deltas.
            val lastVisibleItem = if (isGenerating) messages.size else messages.lastIndex
            listState.animateScrollToItem(lastVisibleItem.coerceAtLeast(0))
        }
    }

    val agent = remember(persona.selectedAgentId) { AgentRegistry.getAgentById(persona.selectedAgentId) }
    val currentSession = remember(currentSessionId, sessions) {
        sessions.find { it.id == currentSessionId }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ChatSessionsDrawerSheet(
                sessions = sessions,
                currentSessionId = currentSessionId,
                personaConfig = persona,
                language = persona.language,
                onSelectSession = { sessionId ->
                    viewModel.onEvent(ChatUiEvent.SelectSession(sessionId))
                },
                onNewSession = {
                    viewModel.onEvent(ChatUiEvent.PrepareNewSession)
                },
                onDeleteSession = { sessionId ->
                    viewModel.onEvent(ChatUiEvent.DeleteSession(sessionId))
                },
                onDeleteAllSessions = {
                    viewModel.onEvent(ChatUiEvent.DeleteAllSessions)
                },
                onNavigateToPersona = onNavigateToPersona,
                onCloseDrawer = {
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            MedBotTopAppBar(
                title = currentSession?.title?.let { com.medbot.app.core.common.AiOutputFormatter.cleanSessionTitle(it) } ?: stringResource(R.string.chat_title),
                subtitle = "${agent.displayName(persona.language)} • On-Device",
                navigationIcon = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                if (drawerState.isClosed) drawerState.open() else drawerState.close()
                            }
                        },
                        modifier = Modifier.springBounceClick()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Menu,
                            contentDescription = stringResource(R.string.chat_sessions)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.onEvent(ChatUiEvent.PrepareNewSession)
                        },
                        modifier = Modifier.springBounceClick()
                    ) {
                        Icon(Icons.Filled.AddComment, contentDescription = stringResource(R.string.chat_new_session))
                    }
                    IconButton(
                        onClick = onNavigateToPersona,
                        modifier = Modifier.springBounceClick()
                    ) {
                        Icon(Icons.Filled.Tune, contentDescription = stringResource(R.string.action_persona))
                    }
                }
            )

            AdaptiveContent(modifier = Modifier.weight(1f), maxContentWidth = 860.dp) {
                Column(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = MedBotSpacing.medium, vertical = MedBotSpacing.medium),
                        verticalArrangement = Arrangement.spacedBy(MedBotSpacing.large)
                    ) {
                        if (messages.isEmpty() && !isGenerating) {
                            item {
                                GeminiEmptyChatState(
                                    agentName = agent.displayName(persona.language),
                                    onNavigateToPersona = onNavigateToPersona
                                )
                            }
                        }
                        items(messages, key = { it.id }) { message ->
                            ChatMessageRow(
                                message = message,
                                agent = agent,
                                language = persona.language,
                                onCitationClick = { citations = message.citations }
                            )
                        }
                        if (isGenerating) {
                            item {
                                StreamingMessageRow(
                                    streamingText = streamingText,
                                    agent = agent,
                                    language = persona.language,
                                    generationPhase = generationPhase,
                                    onCancel = { viewModel.onEvent(ChatUiEvent.CancelGeneration) }
                                )
                            }
                        }
                        if (!errorMessage.isNullOrBlank()) {
                            item {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(MedBotSpacing.medium)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                            Spacer(modifier = Modifier.width(MedBotSpacing.small))
                                            val message = when (errorMessage) {
                                                "EMPTY_INPUT" -> stringResource(R.string.chat_empty_input)
                                                "VISION_UNAVAILABLE" -> stringResource(R.string.chat_vision_unavailable)
                                                "RAG_UNAVAILABLE" -> stringResource(R.string.chat_embedder_unavailable)
                                                "INSUFFICIENT_EVIDENCE" -> stringResource(R.string.chat_insufficient_evidence)
                                                "UNSAFE_OUTPUT" -> stringResource(R.string.chat_unsafe_output)
                                                "INFERENCE_FAILED" -> stringResource(R.string.chat_inference_failed)
                                                else -> stringResource(R.string.chat_model_unavailable)
                                            }
                                            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                                        }
                                        if (errorMessage != "EMPTY_INPUT") {
                                            TextButton(
                                                onClick = { viewModel.onEvent(ChatUiEvent.RetryGeneration) },
                                                modifier = Modifier.heightIn(min = 48.dp)
                                            ) {
                                                Text(stringResource(R.string.chat_retry_generation))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Modern Gemini/ChatGPT Style Floating Input Bar
                    ChatComposer(
                        inputText = inputText,
                        onInputChange = { inputText = it },
                        selectedImageUri = selectedImageUri,
                        onPickImage = { imagePicker.launch("image/*") },
                        onClearImage = { selectedImageUri = null },
                        enabled = !isGenerating,
                        onlineEvidenceEnabled = onlineEvidenceEnabled,
                        onOnlineEvidenceChange = { enabled ->
                            viewModel.onEvent(ChatUiEvent.SetOnlineEvidenceEnabled(enabled))
                        },
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
        }
    }

    citations?.let { selected ->
        CitationsDialog(citations = selected, onDismiss = { citations = null })
    }
}

// ---------------------------------------------------------------------------
// 1. ChatGPT / Gemini Navigation Drawer with Delete All Sessions
// ---------------------------------------------------------------------------

@Composable
private fun ChatSessionsDrawerSheet(
    sessions: List<ChatSession>,
    currentSessionId: String?,
    personaConfig: PersonaConfig,
    language: AppLanguage,
    onSelectSession: (String) -> Unit,
    onNewSession: () -> Unit,
    onDeleteSession: (String) -> Unit,
    onDeleteAllSessions: () -> Unit,
    onNavigateToPersona: () -> Unit,
    onCloseDrawer: () -> Unit
) {
    var sessionToDelete by remember { mutableStateOf<ChatSession?>(null) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    // Single Session Delete Confirmation Dialog
    if (sessionToDelete != null) {
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = { Text(stringResource(R.string.chat_confirm_delete_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.chat_confirm_delete_desc)) },
            confirmButton = {
                Button(
                    onClick = {
                        sessionToDelete?.let { onDeleteSession(it.id) }
                        sessionToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.springBounceClick()
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { sessionToDelete = null },
                    modifier = Modifier.springBounceClick()
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Delete ALL Sessions Confirmation Dialog
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text(stringResource(R.string.chat_confirm_delete_all_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.chat_confirm_delete_all_desc)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteAllSessions()
                        showDeleteAllDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.springBounceClick()
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteAllDialog = false },
                    modifier = Modifier.springBounceClick()
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    ModalDrawerSheet(
        modifier = Modifier
            .widthIn(max = 320.dp)
            .fillMaxHeight(),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerShape = RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp),
        drawerTonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = MedBotSpacing.medium, vertical = MedBotSpacing.large)
        ) {
            // Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = MedBotSpacing.medium)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(MedBotSpacing.small))
                    Text(
                        text = stringResource(R.string.chat_sidebar_header),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onCloseDrawer, modifier = Modifier.springBounceClick()) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                }
            }

            // Sleek "+ Sesi Baru" Button
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) {
                        onNewSession()
                        onCloseDrawer()
                    }
                    .springBounceClick()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MedBotSpacing.medium, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.AddComment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(MedBotSpacing.small))
                    Text(
                        text = stringResource(R.string.chat_new_session),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(MedBotSpacing.medium))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            Spacer(Modifier.height(MedBotSpacing.small))

            // Grouped Session List
            if (sessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.chat_empty_title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val groupedSessions = remember(sessions) {
                    groupSessionsByTime(sessions)
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    groupedSessions.forEach { (timeGroup, sessionList) ->
                        item {
                            val groupLabel = when (timeGroup) {
                                TimeGroup.TODAY -> stringResource(R.string.chat_today)
                                TimeGroup.YESTERDAY -> stringResource(R.string.chat_yesterday)
                                TimeGroup.PREVIOUS -> stringResource(R.string.chat_previous)
                            }
                            Text(
                                text = groupLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp, start = 4.dp)
                            )
                        }

                        items(sessionList, key = { it.id }) { session ->
                            val isSelected = session.id == currentSessionId
                            val sessionAgent = remember(session.activeAgentId) {
                                AgentRegistry.getAgentById(session.activeAgentId)
                            }
                            val formattedTime = remember(session.updatedAt) {
                                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(session.updatedAt))
                            }

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(role = Role.Button) {
                                        onSelectSession(session.id)
                                        onCloseDrawer()
                                    }
                                    .springBounceClick(),
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                } else {
                                    Color.Transparent
                                },
                                border = if (isSelected) {
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                                } else null
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) Icons.AutoMirrored.Filled.Chat else Icons.AutoMirrored.Outlined.Chat,
                                        contentDescription = null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = com.medbot.app.core.common.AiOutputFormatter.cleanSessionTitle(session.title),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${sessionAgent.displayName(language)} • $formattedTime",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = { sessionToDelete = session },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.DeleteOutline,
                                            contentDescription = stringResource(R.string.action_delete),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Delete All Sessions Button in Drawer
                    item {
                        Spacer(Modifier.height(MedBotSpacing.small))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button) { showDeleteAllDialog = true }
                                .springBounceClick()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = MedBotSpacing.medium, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Filled.DeleteSweep,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(MedBotSpacing.small))
                                Text(
                                    text = stringResource(R.string.chat_delete_all_sessions),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(MedBotSpacing.small))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            Spacer(Modifier.height(MedBotSpacing.small))

            // Modern User / Persona Profile Footer Card
            val activeAgent = remember(personaConfig.selectedAgentId) {
                AgentRegistry.getAgentById(personaConfig.selectedAgentId)
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) {
                        onNavigateToPersona()
                        onCloseDrawer()
                    }
                    .springBounceClick()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MedBotSpacing.medium, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.MedicalServices,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(MedBotSpacing.small))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (personaConfig.patientProfileSummary.isNotBlank()) personaConfig.patientProfileSummary else "Profil Pasien",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${activeAgent.displayName(language)} • ${personaConfig.tone.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        Icons.Filled.Tune,
                        contentDescription = stringResource(R.string.action_persona),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 2. Gemini / ChatGPT Style Welcome Empty State with Suggestion Chips
// ---------------------------------------------------------------------------

@Composable
private fun GeminiEmptyChatState(
    agentName: String,
    onNavigateToPersona: () -> Unit = {}
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(MedBotSpacing.medium),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = MedBotSpacing.xLarge, start = MedBotSpacing.small, end = MedBotSpacing.small),
        horizontalAlignment = Alignment.Start
    ) {
        // Active Specialist Pill (Clickable to change specialist)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            modifier = Modifier
                .clickable(role = Role.Button, onClick = onNavigateToPersona)
                .springBounceClick()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = MedBotSpacing.medium, vertical = MedBotSpacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(MedBotSpacing.small))
                Text(
                    agentName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Filled.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        Text(
            text = stringResource(R.string.chat_greeting_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.chat_empty_support),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

    }
}

// ---------------------------------------------------------------------------
// 3. Message Bubble & Rendering
// ---------------------------------------------------------------------------

@Composable
private fun ChatMessageRow(
    message: ChatMessage,
    agent: com.medbot.app.domain.model.DoctorAgent,
    language: AppLanguage,
    onCitationClick: () -> Unit
) {
    val displayAgent = remember(message.agentId, agent) {
        message.agentId?.let { com.medbot.app.domain.agents.AgentRegistry.getAgentById(it) } ?: agent
    }

    Column(
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (message.isUser) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp),
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        } else {
            val parsed = remember(message.text) {
                com.medbot.app.core.common.AiOutputFormatter.parse(message.text)
            }
            var showThinking by remember { mutableStateOf(false) }

            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth(0.94f)
            ) {
                Column(
                    modifier = Modifier.padding(MedBotSpacing.medium),
                    verticalArrangement = Arrangement.spacedBy(MedBotSpacing.small)
                ) {
                    // Header Specialist Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Filled.MedicalServices,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(MedBotSpacing.small))
                            Text(
                                text = displayAgent.displayName(language),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (parsed.thinkingContent != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .clickable(role = Role.Button) { showThinking = !showThinking }
                                    .springBounceClick()
                            ) {
                                Text(
                                    text = if (showThinking) stringResource(R.string.chat_hide_thinking) else stringResource(R.string.chat_view_thinking),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    if (showThinking && parsed.thinkingContent != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = parsed.thinkingContent,
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(MedBotSpacing.small)
                            )
                        }
                    }

                    // Clean Structured Markdown
                    MarkdownText(parsed.displayContent)

                    message.urgencyLevel?.let { urgency ->
                        Spacer(modifier = Modifier.height(2.dp))
                        UrgencyBadge(urgency)
                    }

                    if (message.citations.isNotEmpty()) {
                        FilledTonalButton(
                            onClick = onCitationClick,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .springBounceClick()
                        ) {
                            Text(stringResource(R.string.chat_open_citations, message.citations.size))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamingMessageRow(
    streamingText: String,
    agent: com.medbot.app.domain.model.DoctorAgent,
    language: AppLanguage,
    generationPhase: ChatGenerationPhase,
    onCancel: () -> Unit
) {
    val parsed = remember(streamingText) {
        com.medbot.app.core.common.AiOutputFormatter.parse(streamingText, isGenerating = true)
    }
    var showThinkingDetails by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.Start,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth(0.94f)
        ) {
            Column(
                modifier = Modifier.padding(MedBotSpacing.medium),
                verticalArrangement = Arrangement.spacedBy(MedBotSpacing.small)
            ) {
                // Header with Specialist Name
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.MedicalServices,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(MedBotSpacing.small))
                    Text(
                        text = agent.displayName(language),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (parsed.thinkingContent != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .clickable(role = Role.Button) { showThinkingDetails = !showThinkingDetails }
                            .springBounceClick()
                    ) {
                        Text(
                            text = if (showThinkingDetails) stringResource(R.string.chat_hide_thinking) else stringResource(R.string.chat_view_thinking),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    if (showThinkingDetails) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = parsed.thinkingContent,
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(MedBotSpacing.small)
                            )
                        }
                    }
                }

                if (parsed.displayContent.isNotBlank()) {
                    MarkdownText(parsed.displayContent)
                }

                val typingText = if (parsed.isThinking || parsed.displayContent.isBlank()) {
                    stringResource(R.string.chat_thinking_indicator)
                } else {
                    stringResource(R.string.chat_typing_indicator)
                }
                val phaseText = when (generationPhase) {
                    ChatGenerationPhase.CHECKING_MODEL -> stringResource(R.string.chat_checking_model)
                    ChatGenerationPhase.INDEXING_KNOWLEDGE -> stringResource(R.string.chat_indexing_knowledge)
                    ChatGenerationPhase.RETRIEVING_LOCAL_EVIDENCE -> stringResource(R.string.chat_retrieving_evidence)
                    ChatGenerationPhase.SEARCHING_WEB -> stringResource(R.string.chat_searching_web)
                    ChatGenerationPhase.SEARCHING_LOCAL -> stringResource(R.string.chat_searching_local)
                    ChatGenerationPhase.PREPARING -> stringResource(R.string.chat_preparing)
                    ChatGenerationPhase.GENERATING -> stringResource(R.string.chat_generating_answer)
                    else -> typingText
                }
                com.medbot.app.core.designsystem.components.TypingIndicator(statusText = phaseText)
                TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.chat_cancel_generation))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 4. Gemini / ChatGPT Style Floating Input Composer
// ---------------------------------------------------------------------------

@Composable
private fun ChatComposer(
    inputText: String,
    onInputChange: (String) -> Unit,
    selectedImageUri: String?,
    onPickImage: () -> Unit,
    onClearImage: () -> Unit,
    enabled: Boolean,
    onlineEvidenceEnabled: Boolean,
    onOnlineEvidenceChange: (Boolean) -> Unit,
    onSend: () -> Unit
) {
    val canSend = enabled && (inputText.isNotBlank() || selectedImageUri != null)
    val onlineEvidenceLabel = stringResource(R.string.chat_online_evidence_title)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MedBotSpacing.medium, vertical = MedBotSpacing.small)
            .imePadding()
    ) {
        AnimatedVisibility(
            visible = selectedImageUri != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            selectedImageUri?.let { uri ->
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = MedBotSpacing.small)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = MedBotSpacing.small, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(MedBotSpacing.small))
                        Text(
                            text = Uri.parse(uri).lastPathSegment ?: stringResource(R.string.chat_image_selected),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(onClick = onClearImage, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = MedBotSpacing.small)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MedBotSpacing.medium, vertical = MedBotSpacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.chat_online_evidence_title),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.chat_online_evidence_support),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = onlineEvidenceEnabled,
                    onCheckedChange = onOnlineEvidenceChange,
                    enabled = enabled,
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics {
                            contentDescription = onlineEvidenceLabel
                        },
                    colors = SwitchDefaults.colors()
                )
            }
        }

        // Modern Capsule Input Container
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                // Attach Image Button
                IconButton(
                    onClick = onPickImage,
                    enabled = enabled,
                    modifier = Modifier
                        .size(48.dp)
                        .springBounceClick()
                ) {
                    Icon(
                        imageVector = Icons.Filled.AddPhotoAlternate,
                        contentDescription = stringResource(R.string.chat_attach_image),
                        tint = if (selectedImageUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Seamless Multiline Input Field
                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    label = { Text(stringResource(R.string.chat_input_label)) },
                    supportingText = { Text(stringResource(R.string.chat_input_support)) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    minLines = 1,
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        keyboardType = KeyboardType.Text
                    )
                )

                // High-Contrast Send Button
                FilledIconButton(
                    onClick = onSend,
                    enabled = canSend,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .springBounceClick()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.action_send),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 5. Time Group Helper
// ---------------------------------------------------------------------------

private enum class TimeGroup {
    TODAY, YESTERDAY, PREVIOUS
}

private fun groupSessionsByTime(sessions: List<ChatSession>): Map<TimeGroup, List<ChatSession>> {
    val now = Calendar.getInstance()
    val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val yesterdayStart = todayStart - (24 * 60 * 60 * 1000)

    val map = mutableMapOf<TimeGroup, MutableList<ChatSession>>()

    sessions.forEach { session ->
        val group = when {
            session.updatedAt >= todayStart -> TimeGroup.TODAY
            session.updatedAt >= yesterdayStart -> TimeGroup.YESTERDAY
            else -> TimeGroup.PREVIOUS
        }
        map.getOrPut(group) { mutableListOf() }.add(session)
    }

    return map
}
