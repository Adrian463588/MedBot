package com.medbot.app.presentation.tools

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.medbot.app.core.designsystem.components.MedBotTopAppBar
import com.medbot.app.core.designsystem.theme.UrgencyLowGreen
import com.medbot.app.core.designsystem.theme.UrgencyMediumYellow
import com.medbot.app.domain.agents.tools.ToolRegistry
import com.medbot.app.domain.model.*
import com.medbot.app.domain.repository.DrugRepository
import com.medbot.app.domain.repository.HealthToolsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ToolsViewModel(
    private val drugRepository: DrugRepository,
    private val healthToolsRepository: HealthToolsRepository
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    // Drug Search & Interactions
    private val _drugQuery = MutableStateFlow("")
    val drugQuery: StateFlow<String> = _drugQuery.asStateFlow()

    val searchResults: StateFlow<List<Drug>> = drugRepository.searchDrugs("")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _checkedInteractions = MutableStateFlow<List<DrugInteraction>>(emptyList())
    val checkedInteractions: StateFlow<List<DrugInteraction>> = _checkedInteractions.asStateFlow()

    val labTests: StateFlow<List<LabTest>> = healthToolsRepository.getLabTests()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reminders: StateFlow<List<Reminder>> = healthToolsRepository.getReminders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

    fun checkDrugInteraction(drugA: String, drugB: String) {
        viewModelScope.launch {
            val res = drugRepository.checkInteraction(listOf(drugA, drugB))
            _checkedInteractions.value = res
        }
    }

    fun toggleReminder(id: String, enabled: Boolean) {
        viewModelScope.launch {
            healthToolsRepository.toggleReminder(id, enabled)
        }
    }

    fun addSampleReminder(title: String, hour: Int, minute: Int) {
        viewModelScope.launch {
            healthToolsRepository.saveReminder(
                Reminder(
                    type = ReminderType.MEDICATION,
                    title = title,
                    timeHour = hour,
                    timeMinute = minute,
                    daysOfWeek = listOf(1, 2, 3, 4, 5, 6, 7)
                )
            )
        }
    }

    class Factory(
        private val drugRepository: DrugRepository,
        private val healthToolsRepository: HealthToolsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ToolsViewModel(drugRepository, healthToolsRepository) as T
        }
    }
}

@Composable
fun HealthToolsScreen(
    viewModel: ToolsViewModel,
    onNavigateBack: () -> Unit
) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val drugs by viewModel.searchResults.collectAsState()
    val labTests by viewModel.labTests.collectAsState()
    val reminders by viewModel.reminders.collectAsState()
    val interactions by viewModel.checkedInteractions.collectAsState()

    val tabs = listOf("Obat & Interaksi", "Nilai Lab", "Kalkulator Anak", "Pengingat")

    Scaffold(
        topBar = {
            MedBotTopAppBar(
                title = "Perkakas Medis Lokal",
                subtitle = "Formularium Obat, Rentang Lab & Kalkulator",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
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
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { viewModel.selectTab(index) },
                        text = { Text(title, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                    )
                }
            }

            when (selectedTab) {
                0 -> DrugTabContent(drugs = drugs, interactions = interactions, onCheckInteraction = { a, b -> viewModel.checkDrugInteraction(a, b) })
                1 -> LabTabContent(labTests = labTests)
                2 -> CalculatorTabContent()
                3 -> RemindersTabContent(reminders = reminders, onToggle = { id, en -> viewModel.toggleReminder(id, en) }, onAdd = { t, h, m -> viewModel.addSampleReminder(t, h, m) })
            }
        }
    }
}

@Composable
fun DrugTabContent(
    drugs: List<Drug>,
    interactions: List<DrugInteraction>,
    onCheckInteraction: (String, String) -> Unit
) {
    var drugA by remember { mutableStateOf("Amlodipine") }
    var drugB by remember { mutableStateOf("Simvastatin") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Interaction Checker Card
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "⚡ Cek Interaksi Obat Lokal",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = drugA,
                            onValueChange = { drugA = it },
                            label = { Text("Obat 1") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = drugB,
                            onValueChange = { drugB = it },
                            label = { Text("Obat 2") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { onCheckInteraction(drugA, drugB) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Periksa Interaksi")
                    }

                    if (interactions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        interactions.forEach { inter ->
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "[Tingkat: ${inter.severity.label}] ${inter.drugA} + ${inter.drugB}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(inter.description, style = MaterialTheme.typography.bodySmall)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Rekomendasi: ${inter.recommendation}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Text("Formularium Obat Esensial", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }

        items(drugs) { drug ->
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = drug.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Surface(
                            color = if (drug.isOtc) UrgencyLowGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (drug.isOtc) "Bebas (OTC)" else "Resep Dokter",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (drug.isOtc) UrgencyLowGreen else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(text = "Generik: ${drug.genericName} • Kategori: ${drug.category}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "Indikasi: ${drug.indication}", style = MaterialTheme.typography.bodySmall)
                    Text(text = "Dosis Dewasa: ${drug.adultDose}", style = MaterialTheme.typography.bodySmall)
                    if (drug.affordableAlternatives.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "Nama Dagang Terjangkau: ${drug.affordableAlternatives.joinToString(", ")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun LabTabContent(labTests: List<LabTest>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(labTests) { test ->
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = test.testName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Nilai Normal: ${test.normalLow} - ${test.normalHigh} ${test.unit}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Text(text = "Kategori: ${test.category}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "• Jika Rendah: ${test.interpretationLow}", style = MaterialTheme.typography.bodySmall)
                    Text(text = "• Jika Tinggi: ${test.interpretationHigh}", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Makna Klinis: ${test.clinicalSignificance}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun CalculatorTabContent() {
    var ageMonths by remember { mutableStateOf("24") }
    var weightKg by remember { mutableStateOf("12.0") }
    var heightCm by remember { mutableStateOf("85.0") }
    var calcResult by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "👶 Kalkulator WHO Z-Score & Dosis Anak",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = ageMonths,
                        onValueChange = { ageMonths = it },
                        label = { Text("Usia Anak (Bulan)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = weightKg,
                            onValueChange = { weightKg = it },
                            label = { Text("Berat Badan (kg)") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = heightCm,
                            onValueChange = { heightCm = it },
                            label = { Text("Tinggi/Panjang (cm)") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val a = ageMonths.toIntOrNull() ?: 24
                            val w = weightKg.toDoubleOrNull() ?: 12.0
                            val h = heightCm.toDoubleOrNull() ?: 85.0
                            
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).run {
                                val zscoreRes = com.medbot.app.domain.agents.tools.ZScoreCalculatorTool()
                                val dosingRes = com.medbot.app.domain.agents.tools.PaediatricDosingTool()
                                kotlinx.coroutines.runBlocking {
                                    val r1 = zscoreRes.execute(mapOf("age_months" to a, "weight_kg" to w, "height_cm" to h))
                                    val r2 = dosingRes.execute(mapOf("drug_name" to "paracetamol", "weight_kg" to w))
                                    calcResult = "${r1.summary}\n\n${r2.summary}"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Hitung Status Gizi & Dosis Sirup")
                    }

                    if (calcResult != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = calcResult!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RemindersTabContent(
    reminders: List<Reminder>,
    onToggle: (String, Boolean) -> Unit,
    onAdd: (String, Int, Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Jadwal Minum Obat & Pengingat", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Button(
                    onClick = { onAdd("Paracetamol 500mg (Sesudah Makan)", 8, 0) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Tambah", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tambah")
                }
            }
        }

        if (reminders.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("Belum ada jadwal pengingat.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(reminders) { rem ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(14.dp).fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = rem.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Waktu: %02d:%02d • Setiap Hari".format(rem.timeHour, rem.timeMinute),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Switch(
                            checked = rem.isEnabled,
                            onCheckedChange = { onToggle(rem.id, it) }
                        )
                    }
                }
            }
        }
    }
}
