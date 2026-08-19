@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.medbot.app.presentation.tools

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medbot.app.R
import com.medbot.app.core.designsystem.components.AdaptiveContent
import com.medbot.app.core.designsystem.components.MedBotSpacing
import com.medbot.app.core.designsystem.components.MedBotTopAppBar
import com.medbot.app.core.designsystem.components.MedBotWindowWidth
import com.medbot.app.core.designsystem.components.springBounceClick
import com.medbot.app.domain.agents.tools.ToolResult
import com.medbot.app.domain.model.Drug
import com.medbot.app.domain.model.DrugInteraction
import com.medbot.app.domain.model.InteractionSeverity
import com.medbot.app.domain.model.LabTest
import com.medbot.app.domain.model.Reminder
import kotlinx.coroutines.delay

private val toolTabs = listOf(
    R.string.tools_tab_drugs,
    R.string.tools_tab_labs,
    R.string.tools_tab_calculators,
    R.string.tools_tab_reminders
)

@Composable
fun HealthToolsScreen(viewModel: ToolsViewModel) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        MedBotTopAppBar(
            title = stringResource(R.string.tools_title),
            subtitle = stringResource(R.string.tools_subtitle)
        )
        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                PrimaryScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.fillMaxWidth(),
                    edgePadding = MedBotSpacing.medium
                ) {
                    toolTabs.forEachIndexed { index, labelRes ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { viewModel.onEvent(ToolsUiEvent.SelectTab(index)) },
                            modifier = Modifier.heightIn(min = 48.dp),
                            text = { Text(stringResource(labelRes), maxLines = 2, fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal) }
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
        AdaptiveContent(modifier = Modifier.weight(1f).fillMaxWidth()) { windowWidth ->
            when (selectedTab) {
                0 -> DrugToolsContent(viewModel, windowWidth)
                1 -> LabToolsContent(viewModel, windowWidth)
                2 -> CalculatorToolsContent(viewModel, windowWidth)
                else -> ReminderToolsContent(viewModel, windowWidth)
            }
        }
    }
}

@Composable
private fun DrugToolsContent(viewModel: ToolsViewModel, windowWidth: MedBotWindowWidth) {
    val drugs by viewModel.searchResults.collectAsStateWithLifecycle()
    val interactions by viewModel.checkedInteractions.collectAsStateWithLifecycle()
    var drugA by rememberSaveable { mutableStateOf("") }
    var drugB by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    val filteredDrugs = remember(drugs, query) {
        drugs.filter { drug ->
            query.isBlank() || listOf(drug.name, drug.genericName, drug.category)
                .any { it.contains(query, ignoreCase = true) }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = toolContentPadding(),
        verticalArrangement = Arrangement.spacedBy(MedBotSpacing.large)
    ) {
        item {
            Text(stringResource(R.string.tools_drug_heading), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.tools_drug_support), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            ToolSurface {
                Column(verticalArrangement = Arrangement.spacedBy(MedBotSpacing.medium)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Medication, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(MedBotSpacing.small))
                        Text(stringResource(R.string.tools_check_interaction), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    if (windowWidth == MedBotWindowWidth.COMPACT) {
                        Column(verticalArrangement = Arrangement.spacedBy(MedBotSpacing.small)) {
                            OutlinedTextField(
                                value = drugA,
                                onValueChange = { drugA = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.tools_drug_a)) },
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = drugB,
                                onValueChange = { drugB = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.tools_drug_b)) },
                                singleLine = true
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(MedBotSpacing.medium)
                        ) {
                            OutlinedTextField(
                                value = drugA,
                                onValueChange = { drugA = it },
                                modifier = Modifier.weight(1f),
                                label = { Text(stringResource(R.string.tools_drug_a)) },
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = drugB,
                                onValueChange = { drugB = it },
                                modifier = Modifier.weight(1f),
                                label = { Text(stringResource(R.string.tools_drug_b)) },
                                singleLine = true
                            )
                        }
                    }
                    Button(
                        onClick = { viewModel.onEvent(ToolsUiEvent.CheckDrugInteraction(drugA, drugB)) },
                        enabled = drugA.isNotBlank() && drugB.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).springBounceClick()
                    ) {
                        Text(stringResource(R.string.tools_check_interaction))
                    }
                }
            }
        }
        if (interactions.isNotEmpty()) {
            item {
                Text(stringResource(R.string.tools_interaction_heading), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            items(interactions) { interaction -> InteractionRow(interaction) }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(MedBotSpacing.small)) {
                Text(stringResource(R.string.tools_drug_search_heading), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; viewModel.onEvent(ToolsUiEvent.SetDrugQuery(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.tools_drug_search_label)) },
                    supportingText = { Text(stringResource(R.string.tools_drug_search_support)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )
            }
        }
        if (filteredDrugs.isEmpty()) {
            item { UnavailableToolState(stringResource(R.string.tools_drug_empty)) }
        } else {
            items(filteredDrugs, key = { it.name }) { drug -> DrugRow(drug) }
        }
    }
}

@Composable
private fun DrugRow(drug: Drug) {
    ToolSurface {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Medication, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(MedBotSpacing.medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(drug.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(drug.genericName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(drug.indication, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun InteractionRow(interaction: DrugInteraction) {
    val color = when (interaction.severity) {
        InteractionSeverity.MINOR -> MaterialTheme.colorScheme.primary
        InteractionSeverity.MODERATE -> MaterialTheme.colorScheme.tertiary
        InteractionSeverity.MAJOR -> MaterialTheme.colorScheme.error
    }
    ToolSurface {
        Column(verticalArrangement = Arrangement.spacedBy(MedBotSpacing.small)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = color)
                Spacer(Modifier.width(MedBotSpacing.small))
                Text("${interaction.drugA} + ${interaction.drugB}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Surface(
                shape = MaterialTheme.shapes.small,
                color = color.copy(alpha = 0.15f)
            ) {
                Text(
                    interaction.severity.label,
                    color = color,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Text(interaction.description, style = MaterialTheme.typography.bodyMedium)
            Text(interaction.recommendation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LabToolsContent(viewModel: ToolsViewModel, windowWidth: MedBotWindowWidth) {
    val labTests by viewModel.labTests.collectAsStateWithLifecycle()
    val evaluation by viewModel.labEvaluation.collectAsStateWithLifecycle()
    var selectedTest by rememberSaveable { mutableStateOf("") }
    var value by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = toolContentPadding(),
        verticalArrangement = Arrangement.spacedBy(MedBotSpacing.large)
    ) {
        item {
            Text(stringResource(R.string.tools_lab_heading), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.tools_lab_support), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (labTests.isEmpty()) {
            item { UnavailableToolState(stringResource(R.string.tools_lab_empty)) }
        } else {
            item {
                Text("Daftar Parameter Laboratorium", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            items(labTests, key = { it.testName }) { test ->
                LabTestRow(test, selected = test.testName == selectedTest) {
                    selectedTest = test.testName
                }
            }
        }
        item {
            if (selectedTest.isNotBlank()) {
                val currentTest = labTests.find { it.testName == selectedTest }
                ToolSurface {
                    Column(verticalArrangement = Arrangement.spacedBy(MedBotSpacing.medium)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Science, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(MedBotSpacing.small))
                            Text(
                                "${stringResource(R.string.tools_evaluate_lab)}: $selectedTest",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (currentTest != null) {
                            Text(
                                "Rentang Rujukan: ${currentTest.normalLow} – ${currentTest.normalHigh} ${currentTest.unit}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedTextField(
                            value = value,
                            onValueChange = { value = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.tools_lab_value)) },
                            supportingText = { Text(stringResource(R.string.tools_lab_value_support)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            suffix = currentTest?.let { { Text(it.unit) } }
                        )
                        Button(
                            onClick = { viewModel.onEvent(ToolsUiEvent.EvaluateLab(selectedTest, value)) },
                            enabled = value.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).springBounceClick()
                        ) { Text(stringResource(R.string.tools_evaluate_lab)) }
                    }
                }
            }
        }
        evaluation?.let { result -> item { LabEvaluationSurface(result) } }
    }
}

@Composable
private fun LabTestRow(test: LabTest, selected: Boolean, onClick: () -> Unit) {
    ToolSurface(selected = selected, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Science,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(MedBotSpacing.medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(test.testName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("${test.category} • ${test.normalLow}–${test.normalHigh} ${test.unit}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun CalculatorToolsContent(viewModel: ToolsViewModel, windowWidth: MedBotWindowWidth) {
    val state by viewModel.calculatorState.collectAsStateWithLifecycle()
    var selectedCalcSubTab by rememberSaveable { mutableIntStateOf(0) }

    // BMI inputs
    var bmiWeight by rememberSaveable { mutableStateOf("") }
    var bmiHeight by rememberSaveable { mutableStateOf("") }

    // Due date inputs
    var day by rememberSaveable { mutableStateOf("") }
    var month by rememberSaveable { mutableStateOf("") }
    var year by rememberSaveable { mutableStateOf("") }

    // Paediatric inputs
    var ageMonths by rememberSaveable { mutableStateOf("") }
    var paeWeight by rememberSaveable { mutableStateOf("") }
    var paeHeight by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf("male") }
    var drug by rememberSaveable { mutableStateOf("") }
    var indication by rememberSaveable { mutableStateOf("") }

    val actionRequester = remember { BringIntoViewRequester() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = toolContentPadding(),
        verticalArrangement = Arrangement.spacedBy(MedBotSpacing.large)
    ) {
        item {
            Text(stringResource(R.string.tools_calculator_heading), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.tools_calculator_support), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Text(stringResource(R.string.tools_select_calculator), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(MedBotSpacing.small))
            Row(
                horizontalArrangement = Arrangement.spacedBy(MedBotSpacing.small),
                modifier = Modifier.fillMaxWidth()
            ) {
                ElevatedFilterChip(
                    selected = selectedCalcSubTab == 0,
                    onClick = { selectedCalcSubTab = 0 },
                    label = { Text(stringResource(R.string.tools_calc_subtab_bmi), maxLines = 1, fontWeight = if (selectedCalcSubTab == 0) FontWeight.Bold else FontWeight.Normal) },
                    leadingIcon = { Icon(Icons.Filled.Scale, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp).springBounceClick()
                )
                ElevatedFilterChip(
                    selected = selectedCalcSubTab == 1,
                    onClick = { selectedCalcSubTab = 1 },
                    label = { Text(stringResource(R.string.tools_calc_subtab_due_date), maxLines = 1, fontWeight = if (selectedCalcSubTab == 1) FontWeight.Bold else FontWeight.Normal) },
                    leadingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp).springBounceClick()
                )
                ElevatedFilterChip(
                    selected = selectedCalcSubTab == 2,
                    onClick = { selectedCalcSubTab = 2 },
                    label = { Text(stringResource(R.string.tools_calc_subtab_paediatric), maxLines = 1, fontWeight = if (selectedCalcSubTab == 2) FontWeight.Bold else FontWeight.Normal) },
                    leadingIcon = { Icon(Icons.Filled.ChildCare, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1.1f).heightIn(min = 44.dp).springBounceClick()
                )
            }
        }

        // Active calculator card
        item {
            when (selectedCalcSubTab) {
                0 -> {
                    // BMI Calculator Card
                    ToolSurface {
                        Column(verticalArrangement = Arrangement.spacedBy(MedBotSpacing.medium)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Scale, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(MedBotSpacing.small))
                                Column {
                                    Text(stringResource(R.string.tools_bmi_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("Indeks Massa Tubuh (Berat / Tinggi²)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(MedBotSpacing.medium)
                            ) {
                                NumberField(
                                    value = bmiWeight,
                                    onValueChange = { bmiWeight = it },
                                    labelRes = R.string.tools_weight,
                                    modifier = Modifier.weight(1f),
                                    actionRequester = actionRequester
                                )
                                NumberField(
                                    value = bmiHeight,
                                    onValueChange = { bmiHeight = it },
                                    labelRes = R.string.tools_height,
                                    modifier = Modifier.weight(1f),
                                    actionRequester = actionRequester
                                )
                            }
                            Button(
                                onClick = { viewModel.onEvent(ToolsUiEvent.CalculateBmi(bmiWeight, bmiHeight)) },
                                enabled = bmiWeight.isNotBlank() && bmiHeight.isNotBlank(),
                                modifier = Modifier
                                    .bringIntoViewRequester(actionRequester)
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .springBounceClick()
                            ) { Text(stringResource(R.string.tools_calculate_bmi)) }
                        }
                    }
                }
                1 -> {
                    // Estimated Due Date (HPL) Card
                    ToolSurface {
                        Column(verticalArrangement = Arrangement.spacedBy(MedBotSpacing.medium)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(MedBotSpacing.small))
                                Column {
                                    Text(stringResource(R.string.tools_due_date_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("Hari Pertama Haid Terakhir (HPHT)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(MedBotSpacing.small)
                            ) {
                                NumberField(
                                    value = day,
                                    onValueChange = { day = it },
                                    labelRes = R.string.tools_day_hint,
                                    modifier = Modifier.weight(1f),
                                    actionRequester = actionRequester
                                )
                                NumberField(
                                    value = month,
                                    onValueChange = { month = it },
                                    labelRes = R.string.tools_month_hint,
                                    modifier = Modifier.weight(1f),
                                    actionRequester = actionRequester
                                )
                                NumberField(
                                    value = year,
                                    onValueChange = { year = it },
                                    labelRes = R.string.tools_year_hint,
                                    modifier = Modifier.weight(1.2f),
                                    actionRequester = actionRequester
                                )
                            }
                            Button(
                                onClick = { viewModel.onEvent(ToolsUiEvent.CalculateDueDate(day, month, year)) },
                                enabled = day.isNotBlank() && month.isNotBlank() && year.isNotBlank(),
                                modifier = Modifier
                                    .bringIntoViewRequester(actionRequester)
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .springBounceClick()
                            ) { Text(stringResource(R.string.tools_calculate_due_date)) }
                        }
                    }
                }
                else -> {
                    // Paediatric Calculator Card
                    ToolSurface {
                        Column(verticalArrangement = Arrangement.spacedBy(MedBotSpacing.medium)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.ChildCare, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(MedBotSpacing.small))
                                Column {
                                    Text(stringResource(R.string.tools_paediatric_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("Dosis Berat Badan & Analisis Z-Score", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(MedBotSpacing.small)
                            ) {
                                NumberField(
                                    value = ageMonths,
                                    onValueChange = { ageMonths = it },
                                    labelRes = R.string.tools_age_months,
                                    modifier = Modifier.weight(1f),
                                    actionRequester = actionRequester
                                )
                                NumberField(
                                    value = paeWeight,
                                    onValueChange = { paeWeight = it },
                                    labelRes = R.string.tools_weight,
                                    modifier = Modifier.weight(1f),
                                    actionRequester = actionRequester
                                )
                                NumberField(
                                    value = paeHeight,
                                    onValueChange = { paeHeight = it },
                                    labelRes = R.string.tools_height,
                                    modifier = Modifier.weight(1f),
                                    actionRequester = actionRequester
                                )
                            }
                            // Gender choice chips
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(MedBotSpacing.small)
                            ) {
                                Text("${stringResource(R.string.tools_gender)}:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                FilterChip(
                                    selected = gender == "male",
                                    onClick = { gender = "male" },
                                    label = { Text(stringResource(R.string.gender_male)) },
                                    modifier = Modifier.heightIn(min = 40.dp).springBounceClick()
                                )
                                FilterChip(
                                    selected = gender == "female",
                                    onClick = { gender = "female" },
                                    label = { Text(stringResource(R.string.gender_female)) },
                                    modifier = Modifier.heightIn(min = 40.dp).springBounceClick()
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(MedBotSpacing.medium)
                            ) {
                                CalculatorTextField(
                                    value = drug,
                                    onValueChange = { drug = it },
                                    labelRes = R.string.tools_drug_name,
                                    modifier = Modifier.weight(1f),
                                    actionRequester = actionRequester
                                )
                                CalculatorTextField(
                                    value = indication,
                                    onValueChange = { indication = it },
                                    labelRes = R.string.tools_indication,
                                    modifier = Modifier.weight(1f),
                                    actionRequester = actionRequester
                                )
                            }
                            Button(
                                onClick = { viewModel.onEvent(ToolsUiEvent.CalculatePaediatric(ageMonths, paeWeight, paeHeight, gender, drug, indication)) },
                                enabled = listOf(ageMonths, paeWeight, paeHeight, gender, drug, indication).all(String::isNotBlank),
                                modifier = Modifier
                                    .bringIntoViewRequester(actionRequester)
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .springBounceClick()
                            ) { Text(stringResource(R.string.tools_calculate_paediatric)) }
                        }
                    }
                }
            }
        }

        if (state.isRunning) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MedBotSpacing.medium),
                    modifier = Modifier.fillMaxWidth().padding(vertical = MedBotSpacing.small)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text(stringResource(R.string.calculator_running), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        state.error?.let { error ->
            item { ToolResultSurface(calculatorErrorText(error), isError = true) }
        }

        state.result?.let { result ->
            item { CalculatorResultSurface(state.kind, result) }
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: Int,
    modifier: Modifier = Modifier.fillMaxWidth(),
    actionRequester: BringIntoViewRequester
) {
    CalculatorTextField(value, onValueChange, labelRes, modifier, actionRequester, KeyboardOptions(keyboardType = KeyboardType.Decimal))
}

@Composable
private fun CalculatorTextField(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: Int,
    modifier: Modifier,
    actionRequester: BringIntoViewRequester,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    var isFocused by remember { mutableStateOf(false) }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(isFocused, imeBottom) {
        if (isFocused && imeBottom > 0) {
            delay(150)
            actionRequester.bringIntoView()
        }
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.onFocusChanged { isFocused = it.isFocused },
        label = { Text(stringResource(labelRes)) },
        singleLine = true,
        keyboardOptions = keyboardOptions
    )
}

@Composable
private fun ReminderToolsContent(viewModel: ToolsViewModel, windowWidth: MedBotWindowWidth) {
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val addReminderRequester = remember { BringIntoViewRequester() }
    var title by rememberSaveable { mutableStateOf("") }
    var hour by rememberSaveable { mutableStateOf("") }
    var minute by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = toolContentPadding(),
        verticalArrangement = Arrangement.spacedBy(MedBotSpacing.large)
    ) {
        item {
            Text(stringResource(R.string.tools_reminder_heading), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.tools_reminder_support), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            ToolSurface {
                Column(verticalArrangement = Arrangement.spacedBy(MedBotSpacing.medium)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(MedBotSpacing.small))
                        Text(stringResource(R.string.tools_add_reminder), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.tools_reminder_title)) },
                        supportingText = { Text(stringResource(R.string.tools_reminder_title_support)) },
                        singleLine = true
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MedBotSpacing.medium)
                    ) {
                        NumberField(
                            hour,
                            { hour = it },
                            R.string.tools_reminder_hour,
                            Modifier.weight(1f),
                            addReminderRequester
                        )
                        NumberField(
                            minute,
                            { minute = it },
                            R.string.tools_reminder_minute,
                            Modifier.weight(1f),
                            addReminderRequester
                        )
                    }
                    Button(
                        onClick = { viewModel.onEvent(ToolsUiEvent.AddReminder(title, hour.toIntOrNull() ?: -1, minute.toIntOrNull() ?: -1)) },
                        enabled = title.isNotBlank() && hour.toIntOrNull() in 0..23 && minute.toIntOrNull() in 0..59,
                        modifier = Modifier
                            .bringIntoViewRequester(addReminderRequester)
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .springBounceClick()
                    ) { Text(stringResource(R.string.tools_add_reminder)) }
                }
            }
        }
        if (reminders.isNotEmpty()) {
            item {
                Text("Daftar Pengingat Aktif", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            items(reminders, key = { it.id }) { reminder -> ReminderRow(reminder, viewModel) }
        } else {
            item { UnavailableToolState(stringResource(R.string.tools_reminder_empty)) }
        }
    }
}

@Composable
private fun ReminderRow(reminder: Reminder, viewModel: ToolsViewModel) {
    ToolSurface {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(MedBotSpacing.medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(reminder.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("%02d:%02d".format(reminder.timeHour, reminder.timeMinute), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = reminder.isEnabled,
                onCheckedChange = { viewModel.onEvent(ToolsUiEvent.ToggleReminder(reminder.id, it)) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = reminder.title }
            )
            IconButton(
                onClick = { viewModel.onEvent(ToolsUiEvent.DeleteReminder(reminder.id)) },
                modifier = Modifier.heightIn(min = 48.dp).springBounceClick()
            ) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ToolSurface(
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val modifier = Modifier
        .fillMaxWidth()
        .then(
            if (onClick == null) Modifier
            else Modifier
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onClick)
        )
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        tonalElevation = if (selected) 2.dp else 1.dp,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(MedBotSpacing.medium)) { content() }
    }
}

@Composable
private fun ToolResultSurface(text: String, isError: Boolean = false) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(MedBotSpacing.medium),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(MedBotSpacing.small)
        ) {
            Icon(
                if (isError) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
            )
            Column {
                Text(
                    stringResource(R.string.tools_result_heading),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun calculatorErrorText(error: CalculatorErrorKind): String = when (error) {
    CalculatorErrorKind.VALIDATION -> stringResource(R.string.calculator_invalid_input)
    CalculatorErrorKind.UNAVAILABLE -> stringResource(R.string.tools_calculator_unavailable)
    CalculatorErrorKind.FAILED -> stringResource(R.string.tools_calculator_failed)
}

@Composable
private fun CalculatorResultSurface(kind: CalculatorKind?, results: List<ToolResult>) {
    val first = results.firstOrNull()
    val text = when (kind) {
        CalculatorKind.BMI -> {
            val bmi = (first?.data?.get("bmi") as? Number)?.toDouble()
            val category = first?.data?.get("category") as? String
            if (bmi == null || category == null) stringResource(R.string.tools_calculator_failed)
            else stringResource(R.string.tools_bmi_result, bmi, category)
        }
        CalculatorKind.DUE_DATE -> {
            val date = first?.data?.get("hplDate") as? String
            if (date.isNullOrBlank()) stringResource(R.string.tools_calculator_failed)
            else stringResource(R.string.tools_due_date_result, date)
        }
        CalculatorKind.PAEDIATRIC, null -> stringResource(R.string.tools_calculator_result)
    }
    ToolResultSurface(text)
}

@Composable
private fun LabEvaluationSurface(state: LabEvaluationState) {
    val text = when (state.kind) {
        LabEvaluationKind.INVALID_INPUT -> stringResource(R.string.tools_lab_invalid)
        LabEvaluationKind.UNAVAILABLE -> stringResource(R.string.tools_lab_unavailable)
        LabEvaluationKind.RESULT -> {
            val test = state.test
            val status = when (state.status) {
                LabComparisonStatus.BELOW_REFERENCE -> stringResource(R.string.tools_lab_below)
                LabComparisonStatus.ABOVE_REFERENCE -> stringResource(R.string.tools_lab_above)
                LabComparisonStatus.WITHIN_REFERENCE -> stringResource(R.string.tools_lab_within)
                null -> stringResource(R.string.tools_lab_unavailable)
            }
            if (test == null || state.value == null) {
                stringResource(R.string.tools_lab_unavailable)
            } else {
                stringResource(
                    R.string.tools_lab_result,
                    status,
                    state.value,
                    test.unit,
                    test.normalLow,
                    test.normalHigh
                )
            }
        }
    }
    ToolResultSurface(text, isError = state.kind != LabEvaluationKind.RESULT)
}

@Composable
private fun UnavailableToolState(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(MedBotSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MedBotSpacing.small)
        ) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun toolContentPadding(): PaddingValues = PaddingValues(
    start = MedBotSpacing.medium,
    top = MedBotSpacing.medium,
    end = MedBotSpacing.medium,
    bottom = MedBotSpacing.xxLarge
)

