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
                // Error handled gracefully
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
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
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
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                if (messages.isEmpty() && !isGenerating) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = CircleShape,
                                    modifier = Modifier.size(68.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("🩺", fontSize = 34.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Konsultasi dengan ${activeAgent.displayNameId}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Ajukan keluhan Anda atau lampirkan foto/dokumen untuk dianalisis.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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

                // Live Streaming Token Bubble
                if (isGenerating && streamingText.isNotBlank()) {
                    item {
                        StreamingBubbleItem(text = streamingText, agent = activeAgent)
                    }
                } else if (isGenerating) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sedang menganalisis secara lokal...", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Input Bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
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
                                Text("Citra terlampir: ${File(selectedImageUri!!).name}", style = MaterialTheme.typography.labelSmall)
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
                                contentDescription = "Lampirkan Citra",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Ketik pesan atau pertanyaan...") },
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.weight(1f),
                            maxLines = 4
                        )

                        Spacer(modifier = Modifier.width(6.dp))

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
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Kirim",
                                        tint = if ((inputText.isNotBlank() || selectedImageUri != null) && !isGenerating) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Citations Popup
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
    val bg = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

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
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(containerColor = bg),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (message.imageUri != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
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
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = textColor.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onCitationClick() }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = "Sitasi",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${message.citations.size} Rujukan Klinis Resmi ›",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
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
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                MarkdownText(text = text)
            }
        }
    }
}
