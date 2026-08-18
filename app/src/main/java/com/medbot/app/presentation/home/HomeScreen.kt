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
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.medbot.app.core.designsystem.components.MedBotTopAppBar
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

data class HealthRecommendation(
    val title: String,
    val sub: String,
    val emoji: String,
    val color: Color,
    val description: String,
    val steps: List<String>
)

val DAILY_RECOMMENDATIONS = listOf(
    HealthRecommendation(
        title = "Pola Tidur Sehat",
        sub = "Program Pemulihan 7 Hari",
        emoji = "🌙",
        color = Color(0xFF1E293B),
        description = "Tidur berkualitas 7-8 jam sangat penting untuk perbaikan sel imun, fungsi kognitif, dan metabolisme hormon.",
        steps = listOf("Tetapkan jam tidur teratur setiap malam", "Hindari layar HP 1 jam sebelum tidur", "Pastikan kamar sejuk dan gelap", "Hindari kafein setelah jam 14:00")
    ),
    HealthRecommendation(
        title = "Nutrisi Seimbang & Energi",
        sub = "Pedoman Gizi Seimbang",
        emoji = "🥗",
        color = Color(0xFF0D7C66),
        description = "Gizi seimbang kaya serat dan antioksidan memberi energi berkelanjutan serta mencegah penyakit metabolik.",
        steps = listOf("Penuhi separuh piring dengan sayur dan buah", "Pilih karbohidrat kompleks (nasi merah/gandum)", "Minum 8 gelas air putih setiap hari", "Kurangi makanan tinggi gula dan garam")
    ),
    HealthRecommendation(
        title = "Manajemen Stres & Pikiran",
        sub = "Relaksasi Pernapasan",
        emoji = "🧘",
        color = Color(0xFF2563EB),
        description = "Stres kronis memicu peningkatan hormon kortisol yang berdampak buruk pada tekanan darah dan lambung.",
        steps = listOf("Latihan pernapasan 4-7-8 selama 5 menit", "Jalan kaki santai 15 menit setiap pagi", "Jurnal harian untuk meluapkan beban pikiran", "Batasi konsumsi berita negatif sebelum tidur")
    )
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
    var selectedRec by remember { mutableStateOf<HealthRecommendation?>(null) }

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
                subtitle = "Konsultan Medis AI & Offline RAG",
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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

            // Quick Symptom Triage Search Bar
            item {
                Text(
                    text = "Triase Gejala Cepat",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = quickQuery,
                    onValueChange = { quickQuery = it },
                    placeholder = { Text("Ketik keluhan Anda (misal: demam 3 hari, nyeri ulu hati)...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Cari",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
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
                            Surface(
                                color = if (quickQuery.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                shape = CircleShape,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Kirim",
                                        tint = if (quickQuery.isNotBlank()) Color.White else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Daily Health Recommendations Carousel
            item {
                Text(
                    text = "Rekomendasi Kesehatan Harian",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(DAILY_RECOMMENDATIONS) { rec ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = rec.color),
                            modifier = Modifier
                                .width(220.dp)
                                .springBounceClick()
                                .clickable { selectedRec = rec }
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(rec.emoji, fontSize = 24.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = rec.title,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Text(
                                    text = rec.sub,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.8f)
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
                    FeatureModuleCard(
                        title = "Knowledge RAG",
                        desc = "Dokumen PDF Medis",
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        color = Color(0xFF2563EB),
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToKnowledge
                    )
                    FeatureModuleCard(
                        title = "Alat Klinis",
                        desc = "Obat, Lab & Tensi",
                        icon = Icons.Default.MedicalInformation,
                        color = Color(0xFF059669),
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

        // Recommendation Detail Dialog
        if (selectedRec != null) {
            val rec = selectedRec!!
            Dialog(onDismissRequest = { selectedRec = null }) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(rec.emoji, fontSize = 28.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(rec.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                Text(rec.sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(rec.description, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("Langkah Praktis:", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                        Spacer(modifier = Modifier.height(6.dp))
                        rec.steps.forEach { step ->
                            Row(modifier = Modifier.padding(vertical = 3.dp)) {
                                Text("✓ ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Text(step, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(
                            onClick = { selectedRec = null },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().springBounceClick()
                        ) {
                            Text("Mengerti")
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
