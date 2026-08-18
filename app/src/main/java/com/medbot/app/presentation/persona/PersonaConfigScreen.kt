package com.medbot.app.presentation.persona

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.medbot.app.core.designsystem.components.MedBotTopAppBar
import com.medbot.app.domain.agents.AgentRegistry
import com.medbot.app.domain.model.*
import com.medbot.app.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PersonaViewModel(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val personaConfig: StateFlow<PersonaConfig> = userPreferencesRepository.personaConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PersonaConfig())

    fun updateConfig(config: PersonaConfig) {
        viewModelScope.launch {
            userPreferencesRepository.updatePersonaConfig(config)
        }
    }

    fun selectAgent(agentId: String) {
        val current = personaConfig.value
        updateConfig(current.copy(selectedAgentId = agentId))
    }

    fun selectTone(tone: PersonaTone) {
        val current = personaConfig.value
        updateConfig(current.copy(tone = tone))
    }

    fun selectDepth(depth: DetailDepth) {
        val current = personaConfig.value
        updateConfig(current.copy(depth = depth))
    }

    fun selectLanguage(lang: AppLanguage) {
        val current = personaConfig.value
        updateConfig(current.copy(language = lang))
        viewModelScope.launch {
            userPreferencesRepository.setLanguage(lang)
        }
    }

    class Factory(
        private val userPreferencesRepository: UserPreferencesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PersonaViewModel(userPreferencesRepository) as T
        }
    }
}

@Composable
fun PersonaConfigScreen(
    viewModel: PersonaViewModel,
    onNavigateBack: () -> Unit
) {
    val config by viewModel.personaConfig.collectAsState()

    var customInstructions by remember(config.customInstructions) { mutableStateOf(config.customInstructions) }
    var patientProfile by remember(config.patientProfileSummary) { mutableStateOf(config.patientProfileSummary) }
    var agentSearchQuery by remember { mutableStateOf("") }

    val filteredAgents = remember(agentSearchQuery) {
        if (agentSearchQuery.isBlank()) AgentRegistry.ALL_AGENTS else {
            AgentRegistry.ALL_AGENTS.filter {
                it.displayNameId.contains(agentSearchQuery, ignoreCase = true) ||
                it.specialtyId.contains(agentSearchQuery, ignoreCase = true)
            }
        }
    }

    Scaffold(
        topBar = {
            MedBotTopAppBar(
                title = "Personalisasi Persona AI",
                subtitle = "Pilih Dokter Spesialis & Gaya Komunikasi",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.updateConfig(
                                config.copy(
                                    customInstructions = customInstructions,
                                    patientProfileSummary = patientProfile
                                )
                            )
                            onNavigateBack()
                        }
                    ) {
                        Text("Simpan", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Language Switcher (Indonesian & English only)
            item {
                Text("Bahasa Komunikasi", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppLanguage.values().forEach { lang ->
                        FilterChip(
                            selected = config.language == lang,
                            onClick = { viewModel.selectLanguage(lang) },
                            label = { Text("${lang.flag} ${lang.displayName}") }
                        )
                    }
                }
            }

            // 2. Communication Tone
            item {
                Text("Gaya Nada Bicara (Tone)", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PersonaTone.values().forEach { tone ->
                        ToneSelectionCard(
                            tone = tone,
                            isSelected = config.tone == tone,
                            onClick = { viewModel.selectTone(tone) }
                        )
                    }
                }
            }

            // 3. Detail Depth
            item {
                Text("Tingkat Kedalaman Penjelasan", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailDepth.values().forEach { depth ->
                        FilterChip(
                            selected = config.depth == depth,
                            onClick = { viewModel.selectDepth(depth) },
                            label = { Text(depth.label) }
                        )
                    }
                }
            }

            // 4. Patient Profile Summary (Contextual Prompt Injection)
            item {
                Text("Ringkasan Profil Pasien", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(
                    text = "Disuntikkan ke prompt model lokal untuk personalisasi riwayat penyakit/alergi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = patientProfile,
                    onValueChange = { patientProfile = it },
                    placeholder = { Text("Contoh: Pasien Wanita, 32 th, riwayat alergi amoksisilin & asma.") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 5. Custom Persona Directives
            item {
                Text("Instruksi Tambahan (Custom Directive)", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = customInstructions,
                    onValueChange = { customInstructions = it },
                    placeholder = { Text("Contoh: Selalu ingatkan jadwal minum obat dan gunakan analogi mudah.") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }

            // 6. Specialist Doctor Selection
            item {
                Text("Pilih Dokter Spesialis (46 Pilihan)", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = agentSearchQuery,
                    onValueChange = { agentSearchQuery = it },
                    placeholder = { Text("Cari spesialis (misal: Kulit, Anak, Jantung, Gigi)...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Cari") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            items(filteredAgents) { agent ->
                val isSelected = config.selectedAgentId == agent.id
                Surface(
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.selectAgent(agent.id) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Surface(
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🩺", fontSize = 18.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = agent.displayNameId,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = agent.specialtyId,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isSelected) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Terpilih", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ToneSelectionCard(
    tone: PersonaTone,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(10.dp),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = tone.label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = tone.promptModifier,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
