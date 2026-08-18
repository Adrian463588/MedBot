package com.medbot.app.presentation.home

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.medbot.app.core.designsystem.components.MedBotTopAppBar
import com.medbot.app.core.designsystem.components.StatusBannerCard
import com.medbot.app.core.designsystem.components.springBounceClick
import com.medbot.app.domain.agents.AgentRegistry
import com.medbot.app.domain.model.DoctorAgent
import com.medbot.app.domain.model.PersonaConfig
import com.medbot.app.domain.repository.ChatRepository
import com.medbot.app.domain.repository.ModelRepository
import com.medbot.app.domain.repository.RagRepository
import com.medbot.app.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val modelRepository: ModelRepository,
    private val ragRepository: RagRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {

    val isModelLoaded: StateFlow<Boolean> = kotlinx.coroutines.flow.flow {
        emit(modelRepository.isModelLoaded())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val activeModelName: StateFlow<String?> = kotlinx.coroutines.flow.flow {
        emit(modelRepository.getActiveModelName())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val ragDocumentCount: StateFlow<Int> = ragRepository.getDocuments()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val personaConfig: StateFlow<PersonaConfig> = userPreferencesRepository.personaConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PersonaConfig())

    fun createNewChat(agentId: String? = null, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val targetAgent = agentId ?: personaConfig.value.selectedAgentId
            val session = chatRepository.createSession(
                title = "Konsultasi Medis Baru",
                agentId = targetAgent
            )
            onCreated(session.id)
        }
    }

    class Factory(
        private val modelRepository: ModelRepository,
        private val ragRepository: RagRepository,
        private val userPreferencesRepository: UserPreferencesRepository,
        private val chatRepository: ChatRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(modelRepository, ragRepository, userPreferencesRepository, chatRepository) as T
        }
    }
}

val CLINICAL_CLUSTERS = listOf(
    "Semua (46)",
    "Pelayanan Primer & IGD",
    "Penyakit Dalam & Organ",
    "Anak & Tahapan Usia",
    "Kulit, Bedah & Indera",
    "Kandungan & Reproduksi",
    "Diagnostik & Farmasi",
    "Saraf & Rehabilitasi"
)

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToChat: (String?) -> Unit,
    onNavigateToSkinScan: () -> Unit,
    onNavigateToKnowledge: () -> Unit,
    onNavigateToModels: () -> Unit,
    onNavigateToPersona: () -> Unit,
    onNavigateToTools: () -> Unit
) {
    val isLoaded by viewModel.isModelLoaded.collectAsState()
    val modelName by viewModel.activeModelName.collectAsState()
    val ragCount by viewModel.ragDocumentCount.collectAsState()
    val persona by viewModel.personaConfig.collectAsState()

    val activeAgent = remember(persona.selectedAgentId) {
        AgentRegistry.getAgentById(persona.selectedAgentId)
    }

    var quickQuery by remember { mutableStateOf("") }
    var selectedCluster by remember { mutableStateOf("Semua (46)") }

    val filteredSpecialists = remember(selectedCluster) {
        when (selectedCluster) {
            "Pelayanan Primer & IGD" -> AgentRegistry.ALL_AGENTS.filter { it.id in listOf("orchestrator", "general_practice", "emergency_medicine", "preventive_medicine", "lifestyle_medicine") }
            "Penyakit Dalam & Organ" -> AgentRegistry.ALL_AGENTS.filter { it.id in listOf("internal_medicine", "cardiology", "pulmonology", "gastroenterology", "nephrology", "endocrinology", "infectious_disease", "haematology", "rheumatology", "allergy_immunology") }
            "Anak & Tahapan Usia" -> AgentRegistry.ALL_AGENTS.filter { it.id in listOf("paediatrics", "neonatology", "adolescent_medicine", "geriatrics") }
            "Kulit, Bedah & Indera" -> AgentRegistry.ALL_AGENTS.filter { it.id in listOf("dermatology", "orthopaedics", "ophthalmology", "otorhinolaryngology", "dentistry", "urology") }
            "Kandungan & Reproduksi" -> AgentRegistry.ALL_AGENTS.filter { it.id in listOf("obstetrics_gynecology", "fertility") }
            "Diagnostik & Farmasi" -> AgentRegistry.ALL_AGENTS.filter { it.id in listOf("pharmacy", "radiology", "clinical_pathology", "toxicology") }
            "Saraf & Rehabilitasi" -> AgentRegistry.ALL_AGENTS.filter { it.id in listOf("neurology", "psychiatry", "sleep_medicine", "pain_management", "sports_medicine", "rehabilitation", "palliative_care", "genetics", "travel_medicine", "vascular_medicine", "transplant_medicine", "integrative_medicine", "addiction_medicine", "occupational_medicine", "nutrition_dietetics") }
            else -> AgentRegistry.ALL_AGENTS
        }
    }

    Scaffold(
        topBar = {
            MedBotTopAppBar(
                title = "MedBot On-Device",
                subtitle = "Asisten Medis Lokal & Offline RAG",
                actions = {
                    IconButton(onClick = onNavigateToPersona, modifier = Modifier.springBounceClick()) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Atur Persona",
                            tint = MaterialTheme.colorScheme.primary
                        )
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                StatusBannerCard(
                    isModelLoaded = isLoaded,
                    modelName = modelName,
                    ragDocCount = ragCount,
                    onManageModel = onNavigateToModels,
                    onManageRag = onNavigateToKnowledge
                )
            }

            // Persona Indicator Card
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .springBounceClick()
                        .clickable { onNavigateToPersona() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("🩺", fontSize = 20.sp)
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Dokter Aktif: ${activeAgent.displayNameId}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "${persona.tone.label} • ${persona.depth.label} (${persona.language.displayName})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Ubah",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Quick Symptom Triage Input with microinteractions
            item {
                Text(
                    text = "Triase Gejala Cepat",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = quickQuery,
                    onValueChange = { quickQuery = it },
                    placeholder = { Text("Ketik keluhan atau gejala sakit Anda...") },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (quickQuery.isNotBlank()) {
                                    viewModel.createNewChat { sessionId ->
                                        onNavigateToChat(sessionId)
                                    }
                                }
                            },
                            enabled = quickQuery.isNotBlank(),
                            modifier = Modifier.springBounceClick()
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Kirim",
                                tint = if (quickQuery.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Feature Quick Action Grid
            item {
                Text(
                    text = "Modul Layanan Medis",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FeatureCard(
                        title = "Konsultasi Chat",
                        desc = "Chatbot 46 Spesialis",
                        icon = Icons.Default.ChatBubbleOutline,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            viewModel.createNewChat { sessionId ->
                                onNavigateToChat(sessionId)
                            }
                        }
                    )
                    FeatureCard(
                        title = "Skin Lineage",
                        desc = "Foto & Riwayat Kulit",
                        icon = Icons.Default.CameraAlt,
                        color = Color(0xFFE67E22),
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToSkinScan
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FeatureCard(
                        title = "Knowledge RAG",
                        desc = "Dokumen PDF Medis",
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        color = Color(0xFF2980B9),
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToKnowledge
                    )
                    FeatureCard(
                        title = "Alat Klinis",
                        desc = "Obat, Lab & Tensi",
                        icon = Icons.Default.MedicalInformation,
                        color = Color(0xFF27AE60),
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToTools
                    )
                }
            }

            // 46 Specialist Medical Doctors Cluster Selection
            item {
                Text(
                    text = "46 Agen Spesialis Medis Terpadu",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(CLINICAL_CLUSTERS) { cluster ->
                        FilterChip(
                            selected = selectedCluster == cluster,
                            onClick = { selectedCluster = cluster },
                            label = { Text(cluster, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(filteredSpecialists) { agent ->
                        SpecialistCard(
                            agent = agent,
                            onClick = {
                                viewModel.createNewChat(agent.id) { sessionId ->
                                    onNavigateToChat(sessionId)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FeatureCard(
    title: String,
    desc: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .springBounceClick()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Surface(
                color = color.copy(alpha = 0.12f),
                shape = CircleShape,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = icon, contentDescription = title, tint = color, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Text(text = desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SpecialistCard(
    agent: DoctorAgent,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp,
        modifier = Modifier
            .springBounceClick()
            .clickable { onClick() }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .widthIn(min = 120.dp, max = 140.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("🩺", fontSize = 22.sp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = agent.displayNameId,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = agent.specialtyId,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = "Konsultasi ›",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}
