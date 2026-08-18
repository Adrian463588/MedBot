package com.medbot.app.presentation.tools

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.medbot.app.core.designsystem.components.MedBotTopAppBar
import com.medbot.app.core.designsystem.components.springBounceClick
import com.medbot.app.core.designsystem.theme.UrgencyEmergencyRed
import com.medbot.app.core.designsystem.theme.UrgencyLowGreen
import com.medbot.app.core.designsystem.theme.UrgencyMediumYellow
import com.medbot.app.domain.agents.tools.PaediatricDosingTool
import com.medbot.app.domain.agents.tools.ZScoreCalculatorTool
import com.medbot.app.domain.model.*
import com.medbot.app.domain.repository.DrugRepository
import com.medbot.app.domain.repository.HealthToolsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ToolsViewModel @Inject constructor(
    private val drugRepository: DrugRepository,
    private val healthToolsRepository: HealthToolsRepository
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _drugSearchQuery = MutableStateFlow("")
    val drugSearchQuery: StateFlow<String> = _drugSearchQuery.asStateFlow()

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

    fun setDrugQuery(query: String) {
        _drugSearchQuery.value = query
    }

    fun checkDrugInteraction(drugA: String, drugB: String) {
        viewModelScope.launch {
            val res = drugRepository.checkInteraction(listOf(drugA.trim(), drugB.trim()))
            _checkedInteractions.value = res
        }
    }

    fun toggleReminder(id: String, enabled: Boolean) {
        viewModelScope.launch {
            healthToolsRepository.toggleReminder(id, enabled)
        }
    }

    fun addReminder(title: String, hour: Int, minute: Int) {
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

    fun deleteReminder(id: String) {
        viewModelScope.launch {
            healthToolsRepository.deleteReminder(id)
        }
    }

}

@Composable
fun HealthToolsScreen(
    viewModel: ToolsViewModel,
    onNavigateBack: () -> Unit
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val drugs by viewModel.searchResults.collectAsStateWithLifecycle()
    val labTests by viewModel.labTests.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val interactions by viewModel.checkedInteractions.collectAsStateWithLifecycle()

    val tabs = listOf("Obat & Interaksi", "Evaluasi Lab", "Kalkulator Klinis", "Pengingat")

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            MedBotTopAppBar(
                title = "Perkakas Medis Terpadu",
                subtitle = "Formularium Obat, Rentang Lab & Kalkulator",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.springBounceClick()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
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
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { viewModel.selectTab(index) },
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                ),
                                maxLines = 1
                            )
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> DrugTabContent(drugs = drugs, interactions = interactions, onCheckInteraction = { a, b -> viewModel.checkDrugInteraction(a, b) })
                1 -> LabTabContent(labTests = labTests)
                2 -> CalculatorTabContent()
                3 -> RemindersTabContent(
                    reminders = reminders,
                    onToggle = { id, en -> viewModel.toggleReminder(id, en) },
                    onAdd = { t, h, m -> viewModel.addReminder(t, h, m) },
                    onDelete = { id -> viewModel.deleteReminder(id) }
                )
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
    var searchQuery by remember { mutableStateOf("") }

    val filteredDrugs = remember(drugs, searchQuery) {
        if (searchQuery.isBlank()) drugs else drugs.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.genericName.contains(searchQuery, ignoreCase = true) ||
            it.indication.contains(searchQuery, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Interaction Checker Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "⚡ Cek Interaksi Antar-Obat Lokal",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = drugA,
                            onValueChange = { drugA = it },
                            label = { Text("Obat 1") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = drugB,
                            onValueChange = { drugB = it },
                            label = { Text("Obat 2") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { onCheckInteraction(drugA, drugB) },
                        modifier = Modifier.fillMaxWidth().springBounceClick(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = "Cek")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Periksa Interaksi Obat")
                    }

                    if (interactions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        interactions.forEach { inter ->
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "${inter.drugA} + ${inter.drugB}",
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Surface(
                                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = inter.severity.label,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(inter.description, style = MaterialTheme.typography.bodySmall)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "💡 Saran Klinis: ${inter.recommendation}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Text("Formularium Obat & Alternatif Generik", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Cari obat (misal: paracetamol, amlodipine)...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Cari") },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        items(filteredDrugs) { drug ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
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
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (drug.isOtc) "Bebas (OTC)" else "Resep Dokter",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (drug.isOtc) UrgencyLowGreen else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "Generik: ${drug.genericName} • Kategori: ${drug.category}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Indikasi: ${drug.indication}", style = MaterialTheme.typography.bodySmall)
                    Text(text = "Dosis Dewasa: ${drug.adultDose}", style = MaterialTheme.typography.bodySmall)
                    if (drug.childDose.isNotBlank()) {
                        Text(text = "Dosis Anak: ${drug.childDose}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (drug.affordableAlternatives.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Alternatif Generik Terjangkau: ${drug.affordableAlternatives.joinToString(", ")}",
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
fun LabTabContent(labTests: List<LabTest>) {
    var selectedTest by remember { mutableStateOf<LabTest?>(null) }
    var inputValueStr by remember { mutableStateOf("") }
    var evaluationResult by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Interactive Value Checker Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "🧪 Kalkulator Interpretasi Nilai Lab",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Pilih parameter lab di bawah, lalu masukkan angka hasil tes Anda untuk evaluasi instan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (selectedTest != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Parameter: ${selectedTest!!.testName}",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Rentang Normal: ${selectedTest!!.normalLow} - ${selectedTest!!.normalHigh} ${selectedTest!!.unit}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = inputValueStr,
                                        onValueChange = { inputValueStr = it },
                                        label = { Text("Hasil Angka (${selectedTest!!.unit})") },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    Button(
                                        onClick = {
                                            val value = inputValueStr.toDoubleOrNull()
                                            if (value != null && selectedTest != null) {
                                                val t = selectedTest!!
                                                val status = when {
                                                    value < t.normalLow -> "RENDAH"
                                                    value > t.normalHigh -> "TINGGI"
                                                    else -> "NORMAL"
                                                }
                                                val explanation = when (status) {
                                                    "RENDAH" -> t.interpretationLow
                                                    "TINGGI" -> t.interpretationHigh
                                                    else -> "Nilai berada dalam batas normal sehat."
                                                }
                                                evaluationResult = "[STATUS: $status] Nilai ${value} ${t.unit} (Normal: ${t.normalLow} - ${t.normalHigh} ${t.unit}).\n\n$explanation"
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.align(Alignment.CenterVertically).springBounceClick()
                                    ) {
                                        Text("Evaluasi")
                                    }
                                }

                                if (evaluationResult != null) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = evaluationResult!!,
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
        }

        item {
            Text("Pilih Parameter Laboratorium", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }

        items(labTests) { test ->
            val isSelected = selectedTest?.testName == test.testName
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .springBounceClick()
                    .clickable {
                        selectedTest = test
                        inputValueStr = ""
                        evaluationResult = null
                    }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
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
                            text = "${test.normalLow} - ${test.normalHigh} ${test.unit}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Text(text = "Kategori: ${test.category}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Makna Klinis: ${test.clinicalSignificance}", style = MaterialTheme.typography.bodySmall)
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
    var selectedGender by remember { mutableStateOf("male") }
    var selectedDrug by remember { mutableStateOf("paracetamol") }
    var calcResult by remember { mutableStateOf<String?>(null) }

    // Adult BMI inputs
    var adultWeight by remember { mutableStateOf("65.0") }
    var adultHeight by remember { mutableStateOf("170.0") }
    var bmiResult by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // WHO Pediatric Z-Score Calculator
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "👶 Kalkulator WHO Z-Score & Dosis Anak",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedGender == "male",
                            onClick = { selectedGender = "male" },
                            label = { Text("Laki-Laki (Boy)") },
                            modifier = Modifier.springBounceClick()
                        )
                        FilterChip(
                            selected = selectedGender == "female",
                            onClick = { selectedGender = "female" },
                            label = { Text("Perempuan (Girl)") },
                            modifier = Modifier.springBounceClick()
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ageMonths,
                        onValueChange = { ageMonths = it },
                        label = { Text("Usia Anak (Bulan)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = weightKg,
                            onValueChange = { weightKg = it },
                            label = { Text("Berat (kg)") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = heightCm,
                            onValueChange = { heightCm = it },
                            label = { Text("Tinggi (cm)") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Pilihan Obat Sirup Anak:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf("paracetamol", "amoxicillin", "ibuprofen", "cetirizine")) { drug ->
                            FilterChip(
                                selected = selectedDrug == drug,
                                onClick = { selectedDrug = drug },
                                label = { Text(drug.replaceFirstChar { it.uppercase() }) },
                                modifier = Modifier.springBounceClick()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            val a = ageMonths.toIntOrNull() ?: 24
                            val w = weightKg.toDoubleOrNull() ?: 12.0
                            val h = heightCm.toDoubleOrNull() ?: 85.0

                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).run {
                                val zscoreTool = ZScoreCalculatorTool()
                                val dosingTool = PaediatricDosingTool()
                                kotlinx.coroutines.runBlocking {
                                    val r1 = zscoreTool.execute(mapOf("age_months" to a, "weight_kg" to w, "height_cm" to h, "gender" to selectedGender))
                                    val r2 = dosingTool.execute(mapOf("drug_name" to selectedDrug, "weight_kg" to w))
                                    calcResult = "📊 [Status Tumbuh Kembang WHO]\n${r1.summary}\n\n💊 [Dosis Sirup Aman]\n${r2.summary}"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().springBounceClick(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Hitung Z-Score & Dosis Sirup")
                    }

                    if (calcResult != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = calcResult!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // Adult BMI & Body Metrics Calculator
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "🏃 Indeks Massa Tubuh (BMI) & Kalori Dewasa",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = adultWeight,
                            onValueChange = { adultWeight = it },
                            label = { Text("Berat (kg)") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = adultHeight,
                            onValueChange = { adultHeight = it },
                            label = { Text("Tinggi (cm)") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val w = adultWeight.toDoubleOrNull() ?: 65.0
                            val h = (adultHeight.toDoubleOrNull() ?: 170.0) / 100.0
                            val bmi = w / (h * h)
                            val category = when {
                                bmi < 18.5 -> "Berat Badan Kurang (Underweight)"
                                bmi < 24.9 -> "Berat Badan Normal (Ideal)"
                                bmi < 29.9 -> "Kelebihan Berat Badan (Overweight)"
                                else -> "Obesitas (Obese)"
                            }
                            val bmr = 10 * w + 6.25 * (h * 100) - 5 * 30 + 5
                            bmiResult = "Skor BMI: ${"%.1f".format(bmi)} kg/m² ($category)\nEstimasi Kalori Dasar (BMR): ~${bmr.toInt()} kkal/hari."
                        },
                        modifier = Modifier.fillMaxWidth().springBounceClick(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Hitung BMI & Kebutuhan Kalori")
                    }

                    if (bmiResult != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = bmiResult!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // Pregnancy Due Date (Naegele) Calculator
        item {
            var hphtDay by remember { mutableStateOf("15") }
            var hphtMonth by remember { mutableStateOf("8") }
            var hphtYear by remember { mutableStateOf("2026") }
            var dueDateResult by remember { mutableStateOf<String?>(null) }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "🤰 Kalkulator Taksiran Persalinan (HPL / Naegele)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Masukkan Hari Pertama Haid Terakhir (HPHT)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = hphtDay,
                            onValueChange = { hphtDay = it },
                            label = { Text("Tanggal") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = hphtMonth,
                            onValueChange = { hphtMonth = it },
                            label = { Text("Bulan") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = hphtYear,
                            onValueChange = { hphtYear = it },
                            label = { Text("Tahun") },
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val d = hphtDay.toIntOrNull() ?: 1
                            val m = hphtMonth.toIntOrNull() ?: 1
                            val y = hphtYear.toIntOrNull() ?: 2026
                            val tool = com.medbot.app.domain.agents.tools.DueDateCalculatorTool()
                            kotlinx.coroutines.runBlocking {
                                val res = tool.execute(mapOf("day" to d, "month" to m, "year" to y))
                                dueDateResult = res.summary
                            }
                        },
                        modifier = Modifier.fillMaxWidth().springBounceClick(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Hitung Taksiran Persalinan (HPL)")
                    }

                    if (dueDateResult != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = dueDateResult!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(14.dp)
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
    onAdd: (String, Int, Int) -> Unit,
    onDelete: (String) -> Unit
) {
    var newTitle by remember { mutableStateOf("") }
    var hour by remember { mutableIntStateOf(8) }
    var minute by remember { mutableIntStateOf(0) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "⏰ Tambah Jadwal Pengingat",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        placeholder = { Text("Contoh: Amlodipine 5mg (Pagi hari)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Jam: %02d:%02d".format(hour, minute),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(6, 8, 12, 14, 18, 20, 22).forEach { h ->
                                FilterChip(
                                    selected = hour == h,
                                    onClick = { hour = h },
                                    label = { Text("%02d:00".format(h)) },
                                    modifier = Modifier.springBounceClick()
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val title = newTitle.ifBlank { "Minum Obat" }
                            onAdd(title, hour, minute)
                            newTitle = ""
                        },
                        modifier = Modifier.fillMaxWidth().springBounceClick(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.AlarmAdd, contentDescription = "Tambah")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Simpan Jadwal Pengingat")
                    }
                }
            }
        }

        item {
            Text("Jadwal Pengingat Aktif", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }

        if (reminders.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("Belum ada jadwal pengingat. Gunakan form di atas untuk menambahkan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(reminders) { rem ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(16.dp).fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = rem.isEnabled,
                                onCheckedChange = { onToggle(rem.id, it) }
                            )
                            IconButton(onClick = { onDelete(rem.id) }, modifier = Modifier.springBounceClick()) {
                                Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}
