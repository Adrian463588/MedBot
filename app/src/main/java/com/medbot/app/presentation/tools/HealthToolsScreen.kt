@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.medbot.app.presentation.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import com.medbot.app.R
import com.medbot.app.core.designsystem.components.AdaptiveContent
import com.medbot.app.core.designsystem.components.MedBotSpacing
import com.medbot.app.core.designsystem.components.MedBotTopAppBar
import com.medbot.app.core.designsystem.components.MedBotWindowWidth
import com.medbot.app.core.designsystem.components.springBounceClick
import com.medbot.app.domain.model.Drug
import com.medbot.app.domain.model.DrugInteraction
import com.medbot.app.domain.model.InteractionSeverity
import com.medbot.app.domain.model.LabTest
import com.medbot.app.domain.model.Reminder

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
                    text = { Text(stringResource(labelRes), maxLines = 2) }
                )
            }
        }
        AdaptiveContent(modifier = Modifier.weight(1f).fillMaxWidth()) { windowWidth ->
            when (selectedTab) {
                0 -> DrugToolsContent(viewModel)
                1 -> LabToolsContent(viewModel)
                2 -> CalculatorToolsContent(viewModel, windowWidth)
                else -> ReminderToolsContent(viewModel, windowWidth)
            }
        }
    }
}

@Composable
private fun DrugToolsContent(viewModel: ToolsViewModel) {
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
        verticalArrangement = Arrangement.spacedBy(MedBotSpacing.medium)
    ) {
        item {
            Text(stringResource(R.string.tools_drug_heading), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.tools_drug_support), style = MaterialTheme.typography.bodyMedium)
        }
        item {
            OutlinedTextField(
                value = drugA,
                onValueChange = { drugA = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.tools_drug_a)) },
                supportingText = { Text(stringResource(R.string.tools_drug_a_support)) },
                singleLine = true
            )
        }
        item {
            OutlinedTextField(
                value = drugB,
                onValueChange = { drugB = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.tools_drug_b)) },
                supportingText = { Text(stringResource(R.string.tools_drug_b_support)) },
                singleLine = true
            )
        }
        item {
            Button(
                 onClick = { viewModel.onEvent(ToolsUiEvent.CheckDrugInteraction(drugA, drugB)) },
                enabled = drugA.isNotBlank() && drugB.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.tools_check_interaction)) }
        }
        item {
            Text(stringResource(R.string.tools_drug_search_heading), style = MaterialTheme.typography.titleMedium)
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
        if (filteredDrugs.isEmpty()) {
            item { UnavailableToolState(stringResource(R.string.tools_drug_empty)) }
        } else {
            items(filteredDrugs, key = { it.name }) { drug -> DrugRow(drug) }
        }
        item {
            Text(stringResource(R.string.tools_interaction_heading), style = MaterialTheme.typography.titleMedium)
        }
        if (interactions.isEmpty()) {
            item { Text(stringResource(R.string.tools_interaction_empty), style = MaterialTheme.typography.bodyMedium) }
        } else {
            items(interactions) { interaction -> InteractionRow(interaction) }
        }
    }
}

@Composable
private fun DrugRow(drug: Drug) {
    ToolSurface {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Medication, contentDescription = null)
            Spacer(Modifier.width(MedBotSpacing.medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(drug.name, style = MaterialTheme.typography.titleMedium)
                Text(drug.genericName, style = MaterialTheme.typography.bodyMedium)
                Text(drug.indication, style = MaterialTheme.typography.bodySmall)
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
            Text("${interaction.drugA} + ${interaction.drugB}", style = MaterialTheme.typography.titleMedium)
            Text(interaction.severity.label, color = color, style = MaterialTheme.typography.labelLarge)
            Text(interaction.description, style = MaterialTheme.typography.bodyMedium)
            Text(interaction.recommendation, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LabToolsContent(viewModel: ToolsViewModel) {
    val labTests by viewModel.labTests.collectAsStateWithLifecycle()
    val evaluation by viewModel.labEvaluation.collectAsStateWithLifecycle()
    var selectedTest by rememberSaveable { mutableStateOf("") }
    var value by rememberSaveable { mutableStateOf("") }
    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = toolContentPadding(),
        verticalArrangement = Arrangement.spacedBy(MedBotSpacing.medium)
    ) {
        item {
            Text(stringResource(R.string.tools_lab_heading), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.tools_lab_support), style = MaterialTheme.typography.bodyMedium)
        }
        if (labTests.isEmpty()) {
            item { UnavailableToolState(stringResource(R.string.tools_lab_empty)) }
        } else {
            items(labTests, key = { it.testName }) { test ->
                LabTestRow(test, selected = test.testName == selectedTest) { selectedTest = test.testName }
            }
        }
        item {
            if (selectedTest.isBlank()) {
                Text(stringResource(R.string.tools_lab_no_selection), style = MaterialTheme.typography.bodyMedium)
            } else {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.tools_lab_value)) },
                    supportingText = { Text(stringResource(R.string.tools_lab_value_support)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Spacer(Modifier.height(MedBotSpacing.small))
                Button(
                     onClick = { viewModel.onEvent(ToolsUiEvent.EvaluateLab(selectedTest, value)) },
                    enabled = value.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.tools_evaluate_lab)) }
            }
        }
        evaluation?.let { result -> item { LabEvaluationSurface(result) } }
    }
}

@Composable
private fun LabTestRow(test: LabTest, selected: Boolean, onClick: () -> Unit) {
    ToolSurface(selected = selected, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Science, contentDescription = null)
            Spacer(Modifier.width(MedBotSpacing.medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(test.testName, style = MaterialTheme.typography.titleMedium)
                Text("${test.category} • ${test.normalLow}–${test.normalHigh} ${test.unit}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun CalculatorToolsContent(viewModel: ToolsViewModel, windowWidth: MedBotWindowWidth) {
    val state by viewModel.calculatorState.collectAsStateWithLifecycle()
    var bmiWeight by rememberSaveable { mutableStateOf("") }
    var bmiHeight by rememberSaveable { mutableStateOf("") }
    var day by rememberSaveable { mutableStateOf("") }
    var month by rememberSaveable { mutableStateOf("") }
    var year by rememberSaveable { mutableStateOf("") }
    var ageMonths by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableStateOf("") }
    var height by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf("") }
    var drug by rememberSaveable { mutableStateOf("") }
    var indication by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = toolContentPadding(),
        verticalArrangement = Arrangement.spacedBy(MedBotSpacing.large)
    ) {
        item {
            Text(stringResource(R.string.tools_calculator_heading), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.tools_calculator_support), style = MaterialTheme.typography.bodyMedium)
        }
        item {
            CalculatorSection(title = stringResource(R.string.tools_bmi_title)) { actionRequester ->
                ResponsiveFieldRow(windowWidth) { fieldModifier ->
                    NumberField(bmiWeight, { bmiWeight = it }, R.string.tools_weight, fieldModifier, actionRequester)
                    NumberField(bmiHeight, { bmiHeight = it }, R.string.tools_height, fieldModifier, actionRequester)
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
        item {
            CalculatorSection(title = stringResource(R.string.tools_due_date_title)) { actionRequester ->
                ResponsiveFieldRow(windowWidth) { fieldModifier ->
                    NumberField(day, { day = it }, R.string.tools_day, fieldModifier, actionRequester)
                    NumberField(month, { month = it }, R.string.tools_month, fieldModifier, actionRequester)
                    NumberField(year, { year = it }, R.string.tools_year, fieldModifier, actionRequester)
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
        item {
            CalculatorSection(title = stringResource(R.string.tools_paediatric_title)) { actionRequester ->
                ResponsiveFieldRow(windowWidth) { fieldModifier ->
                    NumberField(ageMonths, { ageMonths = it }, R.string.tools_age_months, fieldModifier, actionRequester)
                    NumberField(weight, { weight = it }, R.string.tools_weight, fieldModifier, actionRequester)
                    NumberField(height, { height = it }, R.string.tools_height, fieldModifier, actionRequester)
                }
                ResponsiveFieldRow(windowWidth) { fieldModifier ->
                    CalculatorTextField(gender, { gender = it }, R.string.tools_gender, fieldModifier, actionRequester)
                    CalculatorTextField(drug, { drug = it }, R.string.tools_drug_name, fieldModifier, actionRequester)
                    CalculatorTextField(indication, { indication = it }, R.string.tools_indication, fieldModifier, actionRequester)
                }
                Button(
                    onClick = { viewModel.onEvent(ToolsUiEvent.CalculatePaediatric(ageMonths, weight, height, gender, drug, indication)) },
                    enabled = listOf(ageMonths, weight, height, gender, drug, indication).all(String::isNotBlank),
                    modifier = Modifier
                        .bringIntoViewRequester(actionRequester)
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .springBounceClick()
                ) { Text(stringResource(R.string.tools_calculate_paediatric)) }
            }
        }
        if (state.isRunning) item { Text(stringResource(R.string.calculator_running)) }
        state.error?.let { error -> item { ToolResultSurface(calculatorErrorText(error), isError = true) } }
        state.result?.let { result -> item { CalculatorResultSurface(state.kind, result) } }
    }
}

@Composable
private fun ResponsiveFieldRow(
    windowWidth: MedBotWindowWidth,
    content: @Composable (Modifier) -> Unit
) {
    if (windowWidth == MedBotWindowWidth.COMPACT) {
        Column(verticalArrangement = Arrangement.spacedBy(MedBotSpacing.medium)) {
            content(Modifier.fillMaxWidth())
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MedBotSpacing.medium)
        ) {
            content(Modifier.weight(1f))
        }
    }
}

@Composable
private fun CalculatorSection(
    title: String,
    content: @Composable (BringIntoViewRequester) -> Unit
) {
    val actionRequester = remember { BringIntoViewRequester() }
    ToolSurface {
        Column(verticalArrangement = Arrangement.spacedBy(MedBotSpacing.medium)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Calculate, contentDescription = null)
                Spacer(Modifier.width(MedBotSpacing.medium))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content(actionRequester)
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
        verticalArrangement = Arrangement.spacedBy(MedBotSpacing.medium)
    ) {
        item {
            Text(stringResource(R.string.tools_reminder_heading), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.tools_reminder_support), style = MaterialTheme.typography.bodyMedium)
        }
        item {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.tools_reminder_title)) },
                supportingText = { Text(stringResource(R.string.tools_reminder_title_support)) },
                singleLine = true
            )
        }
        item {
            ResponsiveFieldRow(windowWidth) { fieldModifier ->
                NumberField(hour, { hour = it }, R.string.tools_reminder_hour, fieldModifier, addReminderRequester)
                NumberField(minute, { minute = it }, R.string.tools_reminder_minute, fieldModifier, addReminderRequester)
            }
        }
        item {
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
        if (reminders.isEmpty()) {
            item { UnavailableToolState(stringResource(R.string.tools_reminder_empty)) }
        } else {
            items(reminders, key = { it.id }) { reminder -> ReminderRow(reminder, viewModel) }
        }
    }
}

@Composable
private fun ReminderRow(reminder: Reminder, viewModel: ToolsViewModel) {
    ToolSurface {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.NotificationsActive, contentDescription = null)
            Spacer(Modifier.width(MedBotSpacing.medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(reminder.title, style = MaterialTheme.typography.titleMedium)
                Text("%02d:%02d".format(reminder.timeHour, reminder.timeMinute), style = MaterialTheme.typography.bodyMedium)
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
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
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
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(MedBotSpacing.medium)) { content() }
    }
}

@Composable
private fun ToolResultSurface(text: String, isError: Boolean = false) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    ) { Text(text, modifier = Modifier.padding(MedBotSpacing.medium), style = MaterialTheme.typography.bodyMedium) }
}

@Composable
private fun calculatorErrorText(error: CalculatorErrorKind): String = when (error) {
    CalculatorErrorKind.VALIDATION -> stringResource(R.string.calculator_invalid_input)
    CalculatorErrorKind.UNAVAILABLE -> stringResource(R.string.tools_calculator_unavailable)
    CalculatorErrorKind.FAILED -> stringResource(R.string.tools_calculator_failed)
}

@Composable
private fun CalculatorResultSurface(kind: CalculatorKind?, results: List<com.medbot.app.domain.agents.tools.ToolResult>) {
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
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) { Text(text, modifier = Modifier.padding(MedBotSpacing.medium), style = MaterialTheme.typography.bodyMedium) }
}

private fun toolContentPadding(): PaddingValues = PaddingValues(
    start = MedBotSpacing.medium,
    top = MedBotSpacing.large,
    end = MedBotSpacing.medium,
    bottom = MedBotSpacing.xxLarge
)
