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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.medbot.app.core.designsystem.components.MedBotTopAppBar
import com.medbot.app.core.designsystem.components.AdaptiveContent
import com.medbot.app.core.designsystem.components.MedBotWindowWidth
import com.medbot.app.core.designsystem.components.StatusBannerCard
import com.medbot.app.core.designsystem.components.springBounceClick
import com.medbot.app.core.designsystem.theme.*
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
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
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
    val isLoaded by viewModel.isModelLoaded.collectAsStateWithLifecycle()
    val modelName by viewModel.activeModelName.collectAsStateWithLifecycle()
    val ragCount by viewModel.ragDocumentCount.collectAsStateWithLifecycle()
    val persona by viewModel.personaConfig.collectAsStateWithLifecycle()

    val activeAgent = remember(persona.selectedAgentId) {
        AgentRegistry.getAgentById(persona.selectedAgentId)
    }

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
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            MedBotTopAppBar(
                title = "MedBot On-Device",
                subtitle = "Konsultan Medis AI & Offline RAG",
                navigationIcon = {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = com.medbot.app.R.drawable.medbot_logo),
                        contentDescription = "MedBot Logo",
                        modifier = Modifier
                            .padding(start = 12.dp, end = 4.dp)
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToPersona,
                        modifier = Modifier.springBounceClick()
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Atur Persona",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        AdaptiveContent(modifier = Modifier.padding(padding)) { widthClass ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
            ) {
            // Status Card with Edge AI Glow
            item {
                StatusBannerCard(
                    isModelLoaded = isLoaded,
                    modelName = modelName,
                    ragDocCount = ragCount,
                    onManageModel = onNavigateToModels,
                    onManageRag = onNavigateToKnowledge
                )
            }

            // Active Doctor Persona Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .springBounceClick()
                        .clickable { onNavigateToPersona() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape,
                                modifier = Modifier.size(46.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("👨‍⚕️", fontSize = 24.sp)
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Dokter: ${activeAgent.displayNameId}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${persona.tone.label} • ${persona.depth.label} (${persona.language.displayName})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = CircleShape,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Ubah",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2x2 Feature Modules Grid
            item {
                Text(
                    text = "Modul Layanan Klinis",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FeatureModuleCard(
                        title = "Konsultasi Chat",
                        desc = "46 Agen Dokter Spesialis",
                        icon = Icons.Default.ChatBubbleOutline,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            viewModel.createNewChat { sessionId ->
                                onNavigateToChat(sessionId)
                            }
                        }
                    )
                    FeatureModuleCard(
                        title = "Skin Lineage",
                        desc = "Foto & Riwayat ABCD",
                        icon = Icons.Default.CameraAlt,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToSkinScan
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FeatureModuleCard(
                        title = "Knowledge RAG",
                        desc = "Dokumen PDF Medis",
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToKnowledge
                    )
                    FeatureModuleCard(
                        title = "Alat Klinis",
                        desc = "Obat, Lab & Tensi",
                        icon = Icons.Default.MedicalInformation,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToTools
                    )
                }
            }

            // 46 Specialist Doctors Cluster
            item {
                Text(
                    text = "46 Dokter Spesialis Medis Terpadu",
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
                            label = { Text(cluster, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.springBounceClick()
                        )
                    }
                }
            }

            // Specialist Doctor Cards
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
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
}

@Composable
fun FeatureModuleCard(
    title: String,
    desc: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .springBounceClick()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Surface(
                color = color.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = icon, contentDescription = title, tint = color, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SpecialistCard(
    agent: DoctorAgent,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .springBounceClick()
            .clickable { onClick() }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(14.dp)
                .widthIn(min = 130.dp, max = 150.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("🩺", fontSize = 24.sp)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
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
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Konsultasi ›",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}
