package com.medbot.app.presentation.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.medbot.app.core.designsystem.components.*
import com.medbot.app.core.designsystem.theme.*
import com.medbot.app.domain.agents.AgentRegistry
import com.medbot.app.domain.model.*
import com.medbot.app.domain.repository.ChatRepository
import com.medbot.app.domain.repository.UserPreferencesRepository
import com.medbot.app.domain.usecase.SendMessageUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val sendMessageUseCase: SendMessageUseCase,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val sessions: StateFlow<List<ChatSession>> = chatRepository.getSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    val personaConfig: StateFlow<PersonaConfig> = userPreferencesRepository.personaConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PersonaConfig())

    fun selectSession(sessionId: String) {
        _currentSessionId.value = sessionId
        viewModelScope.launch {
            chatRepository.getMessages(sessionId).collect { list ->
                _messages.value = list
            }
        }
    }

    fun startNewSession(initialAgentId: String = "orchestrator") {
        viewModelScope.launch {
            val session = chatRepository.createSession(
                title = "Konsultasi Medis Baru",
                agentId = initialAgentId
            )
            selectSession(session.id)
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            chatRepository.deleteSession(sessionId)
            if (_currentSessionId.value == sessionId) {
                _currentSessionId.value = null
                _messages.value = emptyList()
            }
        }
    }

    fun sendMessage(text: String, imageUri: String? = null, imageType: String? = null) {
        val sId = _currentSessionId.value ?: return
        if (text.isBlank() && imageUri == null) return

        viewModelScope.launch {
            _isGenerating.value = true
            _streamingText.value = ""

            try {
                sendMessageUseCase.execute(
                    sessionId = sId,
                    userQuery = text,
                    history = _messages.value,
                    imageUri = imageUri,
                    imageType = imageType,
                    personaOverride = personaConfig.value
                ).collect { token ->
                    _streamingText.value += token
                }
            } catch (e: Exception) {
                // Handled gracefully
            } finally {
                _isGenerating.value = false
                _streamingText.value = ""
            }
        }
    }

    class Factory(
        private val chatRepository: ChatRepository,
        private val sendMessageUseCase: SendMessageUseCase,
        private val userPreferencesRepository: UserPreferencesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(chatRepository, sendMessageUseCase, userPreferencesRepository) as T
        }
    }
}

val QUICK_SYMPTOMS = listOf(
    "Demam dan pusing 2 hari",
    "Perut mual dan nyeri ulu hati",
    "Ruam merah terasa gatal",
    "Batuk berdahak dan sesak",
    "Konsultasi aturan minum obat"
)

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    initialSessionId: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToPersona: () -> Unit
) {
    val context = LocalContext.current
    val currentSessionId by viewModel.currentSessionId.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val streamingText by viewModel.streamingText.collectAsState()
    val persona by viewModel.personaConfig.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<String?>(null) }
    var selectedCitationList by remember { mutableStateOf<List<Citation>?>(null) }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val lineageDir = File(context.filesDir, "chat_attachments").apply { if (!exists()) mkdirs() }
            val destFile = File(lineageDir, "attach_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            selectedImageUri = destFile.absolutePath
        }
    }

    LaunchedEffect(initialSessionId) {
        if (initialSessionId != null) {
            viewModel.selectSession(initialSessionId)
        } else if (currentSessionId == null && sessions.isNotEmpty()) {
            viewModel.selectSession(sessions.first().id)
        } else if (currentSessionId == null) {
            viewModel.startNewSession()
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, streamingText.length) {
        if (messages.isNotEmpty() || streamingText.isNotEmpty()) {
            listState.animateScrollToItem((messages.size + if (isGenerating) 1 else 0).coerceAtLeast(0))
        }
    }

    val activeAgent = remember(persona.selectedAgentId) {
        AgentRegistry.getAgentById(persona.selectedAgentId)
    }

    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars.union(WindowInsets.ime),
        topBar = {
            MedBotTopAppBar(
                title = activeAgent.displayNameId,
                subtitle = "${activeAgent.specialtyId} • ${persona.tone.label}",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.springBounceClick()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.startNewSession() }, modifier = Modifier.springBounceClick()) {
                        Icon(Icons.Default.AddComment, contentDescription = "Sesi Baru", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onNavigateToPersona, modifier = Modifier.springBounceClick()) {
                        Icon(Icons.Default.Tune, contentDescription = "Persona", tint = MaterialTheme.colorScheme.primary)
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
            // Sessions Bar
            if (sessions.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    items(sessions) { s ->
                        val isSelected = s.id == currentSessionId
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectSession(s.id) },
                            label = { Text(s.title.take(20), style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.springBounceClick(),
                            trailingIcon = {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Hapus",
                                        modifier = Modifier.size(14.dp).clickable { viewModel.deleteSession(s.id) }
                                    )
                                }
                            }
                        )
                    }
                }
            }

            // Message List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                if (messages.isEmpty() && !isGenerating) {
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("🩺", fontSize = 28.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Konsultasi ${activeAgent.displayNameId}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Ajukan keluhan kesehatan Anda secara aman & privat.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Pertanyaan Medis Populer:",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(QUICK_SYMPTOMS) { prompt ->
                                    SuggestionChip(
                                        onClick = { viewModel.sendMessage(prompt) },
                                        label = { Text(prompt, style = MaterialTheme.typography.bodySmall) },
                                        icon = { Text("💬", fontSize = 12.sp) },
                                        modifier = Modifier.springBounceClick()
                                    )
                                }
                            }
                        }
                    }
                }

                items(messages) { msg ->
                    ChatBubbleItem(
                        message = msg,
                        onCitationClick = { selectedCitationList = msg.citations }
                    )
                }

                // Streaming token bubble
                if (isGenerating && streamingText.isNotBlank()) {
                    item {
                        StreamingBubbleItem(text = streamingText, agent = activeAgent)
                    }
                } else if (isGenerating) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.padding(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Menganalisis data medis secara lokal...", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // Input Bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (selectedImageUri != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Image, contentDescription = "Foto", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Foto terlampir: ${File(selectedImageUri!!).name}", style = MaterialTheme.typography.labelSmall)
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Hapus",
                                    modifier = Modifier.size(14.dp).clickable { selectedImageUri = null }
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = { pickImageLauncher.launch("image/*") },
                            modifier = Modifier.springBounceClick()
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = "Lampirkan Foto",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Tanyakan keluhan kesehatan...") },
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier.weight(1f),
                            maxLines = 4
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = {
                                if (inputText.isNotBlank() || selectedImageUri != null) {
                                    val textToSend = inputText
                                    val imgToSend = selectedImageUri
                                    inputText = ""
                                    selectedImageUri = null
                                    viewModel.sendMessage(
                                        text = textToSend,
                                        imageUri = imgToSend,
                                        imageType = if (imgToSend != null) "skin_lesion" else null
                                    )
                                }
                            },
                            enabled = (inputText.isNotBlank() || selectedImageUri != null) && !isGenerating,
                            modifier = Modifier.springBounceClick()
                        ) {
                            Surface(
                                color = if ((inputText.isNotBlank() || selectedImageUri != null) && !isGenerating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                shape = CircleShape,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Kirim",
                                        tint = if ((inputText.isNotBlank() || selectedImageUri != null) && !isGenerating) Color.White else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (selectedCitationList != null && selectedCitationList!!.isNotEmpty()) {
            CitationsDialog(
                citations = selectedCitationList!!,
                onDismiss = { selectedCitationList = null }
            )
        }
    }
}

@Composable
fun ChatBubbleItem(
    message: ChatMessage,
    onCitationClick: () -> Unit
) {
    val isUser = message.isUser
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bg = if (isUser) MedicalEmerald else MaterialTheme.colorScheme.surface
    val textColor = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        if (!isUser && message.agentId != null) {
            val agent = AgentRegistry.getAgentById(message.agentId)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
            ) {
                Text("🩺", fontSize = 12.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${agent.displayNameId} (${agent.specialtyId})",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                if (message.urgencyLevel != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    UrgencyBadge(urgency = message.urgencyLevel)
                }
            }
        }

        Card(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            colors = CardDefaults.cardColors(containerColor = bg),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isUser) 1.dp else 2.dp),
            border = if (!isUser) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) else null,
            modifier = Modifier.widthIn(max = 330.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (message.imageUri != null) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(140.dp).padding(bottom = 8.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = "Foto", tint = MaterialTheme.colorScheme.primary)
                                Text("Foto Terlampir", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                MarkdownText(text = message.text, color = textColor)

                if (message.citations.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = textColor.copy(alpha = 0.15f))
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onCitationClick() }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = "Sitasi",
                            modifier = Modifier.size(14.dp),
                            tint = if (isUser) Color.White else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${message.citations.size} Rujukan Klinis Resmi ›",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isUser) Color.White else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StreamingBubbleItem(text: String, agent: DoctorAgent) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
        ) {
            Text("🩺", fontSize = 12.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${agent.displayNameId} (Mengetik...)",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }

        Card(
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.widthIn(max = 330.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                StreamingTypewriterText(text = text, isGenerating = true)
            }
        }
    }
}
