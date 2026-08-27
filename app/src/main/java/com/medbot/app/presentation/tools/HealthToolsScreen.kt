@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.medbot.app.presentation.tools

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAlarm
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Boy
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Girl
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

private data class ToolTabItem(
    val titleRes: Int,
    val icon: ImageVector
)

private val toolTabs = listOf(
    ToolTabItem(R.string.tools_tab_drugs, Icons.Filled.Medication),
    ToolTabItem(R.string.tools_tab_labs, Icons.Filled.Science),
    ToolTabItem(R.string.tools_tab_calculators, Icons.Filled.Calculate),
    ToolTabItem(R.string.tools_tab_reminders, Icons.Filled.NotificationsActive)
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
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                PrimaryScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.fillMaxWidth(),
                    edgePadding = MedBotSpacing.medium
                ) {
                    toolTabs.forEachIndexed { index, tabItem ->
                        val isSelected = selectedTab == index
                        Tab(
                            selected = isSelected,
                            onClick = { viewModel.onEvent(ToolsUiEvent.SelectTab(index)) },
                            modifier = Modifier.heightIn(min = 48.dp),
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(MedBotSpacing.small)
                                ) {
                                    Icon(
                                        imageVector = tabItem.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = stringResource(tabItem.titleRes),
                                        maxLines = 1,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            }
        }
        AdaptiveContent(modifier = Modifier.weight(1f).fillMaxWidth(), maxContentWidth = 900.dp) { windowWidth ->
            when (selectedTab) {
                0 -> DrugToolsContent(viewModel, windowWidth)
                1 -> LabToolsContent(viewModel, windowWidth)
                2 -> CalculatorToolsContent(viewModel, windowWidth)
                else -> ReminderToolsContent(viewModel, windowWidth)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 1. DRUGS TAB
// ---------------------------------------------------------------------------

@Composable
private fun DrugToolsContent(viewModel: ToolsViewModel, windowWidth: MedBotWindowWidth) {
    val drugs by viewModel.searchResults.collectAsStateWithLifecycle()
    val interactionState by viewModel.interactionCheckState.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedDrugCategory.collectAsStateWithLifecycle()
    var drugA by rememberSaveable { mutableStateOf("") }
    var drugB by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }

    if (windowWidth == MedBotWindowWidth.EXPANDED) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(toolContentPadding())
                .imePadding(),
            horizontalArrangement = Arrangement.spacedBy(MedBotSpacing.large)
        ) {
            Column(
                modifier = Modifier.weight(1.1f),
                verticalArrangement = Arrangement.spacedBy(MedBotSpacing.large)
            ) {
                DrugInteractionSection(
                    drugA = drugA,
                    drugB = drugB,
                    onDrugAChange = { drugA = it },
                    onDrugBChange = { drugB = it },
                    onSwap = {
                        val temp = drugA
                        drugA = drugB
                        drugB = temp
                    },
                    onCheck = { viewModel.onEvent(ToolsUiEvent.CheckDrugInteraction(drugA, drugB)) },
                    interactionState = interactionState,
                    onGetSuggestions = { viewModel.getDrugSuggestions(it) }
                )
            }
            Column(
                modifier = Modifier.weight(0.9f),
                verticalArrangement = Arrangement.spacedBy(MedBotSpacing.medium)
            ) {
                DrugSearchHeader(
                    query = query,
                    onQueryChange = {
                        query = it
                        viewModel.onEvent(ToolsUiEvent.SetDrugQuery(it))
                    },
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onSelectCategory = { viewModel.onEvent(ToolsUiEvent.SelectDrugCategory(it)) },
                    totalCount = drugs.size,
                    categoryCount = categories.size
                )
                if (drugs.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Filled.Medication,
                        title = if (query.isNotBlank()) "Obat tidak ditemukan" else stringResource(R.string.tools_drug_empty),
                        description = if (query.isNotBlank()) "Tidak ada hasil untuk \"$query\". Coba periksa ejaan atau ubah filter kategori." else stringResource(R.string.tools_drug_search_support)
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(MedBotSpacing.small),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(drugs.take(100), key = { it.name + it.genericName }) { drug ->
                            DrugRow(
                                drug = drug,
                                onSelect = {
                                    if (drugA.isBlank()) {
                                        drugA = drug.name
                                    } else {
                                        drugB = drug.name
                                        viewModel.onEvent(ToolsUiEvent.CheckDrugInteraction(drugA, drug.name))
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = toolContentPadding(),
            verticalArrangement = Arrangement.spacedBy(MedBotSpacing.medium)
        ) {
            item {
                DrugInteractionSection(
                    drugA = drugA,
                    drugB = drugB,
                    onDrugAChange = { drugA = it },
                    onDrugBChange = { drugB = it },
                    onSwap = {
                        val temp = drugA
                        drugA = drugB
                        drugB = temp
                    },
                    onCheck = { viewModel.onEvent(ToolsUiEvent.CheckDrugInteraction(drugA, drugB)) },
                    interactionState = interactionState,
                    onGetSuggestions = { viewModel.getDrugSuggestions(it) }
                )
            }
            item {
                Spacer(Modifier.height(MedBotSpacing.small))
                DrugSearchHeader(
                    query = query,
                    onQueryChange = {
                        query = it
                        viewModel.onEvent(ToolsUiEvent.SetDrugQuery(it))
                    },
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onSelectCategory = { viewModel.onEvent(ToolsUiEvent.SelectDrugCategory(it)) },
                    totalCount = drugs.size,
                    categoryCount = categories.size
                )
            }
            if (drugs.isEmpty()) {
                item {
                    EmptyStateView(
                        icon = Icons.Filled.Medication,
                        title = if (query.isNotBlank()) "Obat tidak ditemukan" else stringResource(R.string.tools_drug_empty),
                        description = if (query.isNotBlank()) "Tidak ada hasil untuk \"$query\". Coba periksa ejaan atau ubah filter kategori." else stringResource(R.string.tools_drug_search_support)
                    )
                }
            } else {
                items(drugs.take(100), key = { it.name + it.genericName }) { drug ->
                    DrugRow(
                        drug = drug,
                        onSelect = {
                            if (drugA.isBlank()) {
                                drugA = drug.name
                            } else {
                                drugB = drug.name
                                viewModel.onEvent(ToolsUiEvent.CheckDrugInteraction(drugA, drug.name))
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DrugAutoCompleteTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supportingText: String,
    onGetSuggestions: suspend (String) -> List<Drug>,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<Drug>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(value, isFocused) {
        if (isFocused && value.trim().length >= 2) {
            isSearching = true
            val results = onGetSuggestions(value)
            suggestions = results
            expanded = results.isNotEmpty()
            isSearching = false
        } else {
            expanded = false
            suggestions = emptyList()
        }
    }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { isFocused = it.isFocused },
            label = { Text(label) },
            supportingText = { Text(supportingText) },
            leadingIcon = {
                Icon(Icons.Filled.Medication, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            trailingIcon = {
                if (isSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else if (value.isNotBlank()) {
                    IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )

        AnimatedVisibility(
            visible = expanded && suggestions.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    Text(
                        text = "Saran Obat dari Database:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    suggestions.forEach { drug ->
                        Surface(
                            onClick = {
                                onValueChange(drug.name)
                                expanded = false
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Medication,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = sanitizeText(drug.name),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (drug.genericName.isNotBlank() && !drug.genericName.equals(drug.name, ignoreCase = true)) {
                                        Text(
                                            text = sanitizeText(drug.genericName),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (drug.dosageForm.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = sanitizeText(drug.dosageForm),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrugInteractionSection(
    drugA: String,
    drugB: String,
    onDrugAChange: (String) -> Unit,
    onDrugBChange: (String) -> Unit,
    onSwap: () -> Unit,
    onCheck: () -> Unit,
    interactionState: InteractionCheckState,
    onGetSuggestions: suspend (String) -> List<Drug>
) {
    Column(verticalArrangement = Arrangement.spacedBy(MedBotSpacing.medium)) {
        ToolCard {
            ToolCardHeader(
                icon = Icons.AutoMirrored.Filled.CompareArrows,
                title = stringResource(R.string.tools_drug_heading),
                subtitle = stringResource(R.string.tools_drug_support)
            )

            // Sleek, Connected Input Group
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(MedBotSpacing.medium),
                    verticalArrangement = Arrangement.spacedBy(MedBotSpacing.small)
                ) {
                    DrugAutoCompleteTextField(
                        value = drugA,
                        onValueChange = onDrugAChange,
                        label = stringResource(R.string.tools_drug_a),
                        supportingText = stringResource(R.string.tools_drug_a_support),
                        onGetSuggestions = onGetSuggestions
                    )

                    // Centered Swap Button with subtle divider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        FilledTonalIconButton(
                            onClick = onSwap,
                            modifier = Modifier
                                .padding(horizontal = MedBotSpacing.small)
                                .size(36.dp)
                                .springBounceClick()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SwapVert,
                                contentDescription = "Tukar Posisi Obat",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }

                    DrugAutoCompleteTextField(
                        value = drugB,
                        onValueChange = onDrugBChange,
                        label = stringResource(R.string.tools_drug_b),
                        supportingText = stringResource(R.string.tools_drug_b_support),
                        onGetSuggestions = onGetSuggestions
                    )
                }
            }

            Button(
                onClick = onCheck,
                enabled = drugA.isNotBlank() && drugB.isNotBlank() && !interactionState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .springBounceClick(),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (interactionState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(MedBotSpacing.small))
                    Text("Memeriksa Interaksi...")
                } else {
                    Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null)
                    Spacer(Modifier.width(MedBotSpacing.small))
                    Text(stringResource(R.string.tools_check_interaction), fontWeight = FontWeight.Bold)
                }
            }
        }

        // Interaction Results Card
        AnimatedVisibility(
            visible = interactionState.isChecked,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            val interactions = remember(interactionState.interactions) {
                interactionState.interactions.distinctBy {
                    listOf(it.drugA.trim().lowercase(), it.drugB.trim().lowercase()).sorted().joinToString("::")
                }
            }
            val hasInteractions = interactions.isNotEmpty()

            ToolCard {
                if (interactionState.status == InteractionCheckStatus.UNAVAILABLE ||
                    interactionState.status == InteractionCheckStatus.INSUFFICIENT_DATA
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = interactionState.message.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    return@ToolCard
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (hasInteractions) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = if (hasInteractions) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = if (hasInteractions) "Terdeteksi ${interactions.size} Potensi Interaksi" else "Tidak ada catatan interaksi pada data yang tersedia",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (hasInteractions) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }

                if (hasInteractions) {
                    interactions.forEach { interaction ->
                        InteractionRow(interaction)
                    }
                } else {
                    Text(
                        text = "Tidak ada hasil interaksi yang dapat diverifikasi untuk kombinasi ${sanitizeText(drugA)} dan ${sanitizeText(drugB)}. Hasil kosong tidak membuktikan kombinasi aman.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DrugSearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    categories: List<String>,
    selectedCategory: String?,
    onSelectCategory: (String?) -> Unit,
    totalCount: Int,
    categoryCount: Int = categories.size
) {
    var isDropdownOpen by remember { mutableStateOf(false) }
    var categoryFilterQuery by remember { mutableStateOf("") }

    val cleanCategories = remember(categories) {
        categories.map { it to sanitizeText(it) }
    }
    val filteredCategories = remember(cleanCategories, categoryFilterQuery) {
        if (categoryFilterQuery.isBlank()) cleanCategories
        else cleanCategories.filter { it.second.contains(categoryFilterQuery, ignoreCase = true) }
    }

    ToolCard {
        ToolCardHeader(
            icon = Icons.Filled.Search,
            title = stringResource(R.string.tools_drug_search_heading),
            subtitle = stringResource(R.string.tools_drug_catalog_notice)
        )
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.tools_drug_search_label)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
        )

        if (query.isNotBlank() && totalCount > 0) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Ditemukan $totalCount produk mirip untuk \"$query\"",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        if (categories.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Filter Kategori Obat",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )

                Surface(
                    onClick = {
                        categoryFilterQuery = ""
                        isDropdownOpen = !isDropdownOpen
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (selectedCategory != null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(
                        1.dp,
                        if (selectedCategory != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MedBotSpacing.medium, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (selectedCategory != null) Icons.Filled.FilterList else Icons.Filled.Category,
                                contentDescription = null,
                                tint = if (selectedCategory != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = if (selectedCategory != null) sanitizeText(selectedCategory) else "Semua Kategori ($categoryCount Kategori)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selectedCategory != null) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedCategory != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (selectedCategory != null) {
                                IconButton(
                                    onClick = { onSelectCategory(null) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Reset Kategori",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (isDropdownOpen) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = isDropdownOpen,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = categoryFilterQuery,
                                onValueChange = { categoryFilterQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.tools_drug_category_search_label)) },
                                leadingIcon = {
                                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                trailingIcon = {
                                    if (categoryFilterQuery.isNotEmpty()) {
                                        IconButton(onClick = { categoryFilterQuery = "" }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Filled.Clear, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    focusedContainerColor = MaterialTheme.colorScheme.surface
                                )
                            )

                            Surface(
                                onClick = {
                                    onSelectCategory(null)
                                    isDropdownOpen = false
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (selectedCategory == null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Semua Kategori ($totalCount Produk)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (selectedCategory == null) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selectedCategory == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (selectedCategory == null) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                if (filteredCategories.isEmpty()) {
                                    Text(
                                        text = "Kategori tidak ditemukan",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                } else {
                                    filteredCategories.forEach { (rawCat, cleanCat) ->
                                        val isSelected = selectedCategory == rawCat
                                        Surface(
                                            onClick = {
                                                onSelectCategory(if (isSelected) null else rawCat)
                                                isDropdownOpen = false
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = cleanCat,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrugRow(
    drug: Drug,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cleanName = remember(drug.name) { sanitizeText(drug.name) }
    val cleanBrand = remember(drug.genericName) { sanitizeText(drug.genericName) }
    val cleanCategory = remember(drug.category) { sanitizeText(drug.category) }
    val cleanDosage = remember(drug.dosageForm) { sanitizeText(drug.dosageForm) }
    val cleanStrength = remember(drug.strength) { sanitizeText(drug.strength) }

    ToolCard(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        onClick = onSelect
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Medication,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = cleanName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (cleanBrand.isNotBlank() && !cleanBrand.equals(cleanName, ignoreCase = true)) {
                    Text(
                        text = "Merek: $cleanBrand",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(2.dp))
                if (cleanCategory.isNotBlank()) {
                    DrugTagBadge(
                        text = cleanCategory,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (cleanDosage.isNotBlank() || cleanStrength.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (cleanDosage.isNotBlank()) {
                            DrugTagBadge(
                                text = cleanDosage,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        if (cleanStrength.isNotBlank()) {
                            DrugTagBadge(
                                text = cleanStrength,
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                        contentDescription = "Bandingkan interaksi",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

private fun sanitizeText(raw: String): String {
    var text = raw.trim()
    while (text.startsWith("&amp;", ignoreCase = true) || text.startsWith("&") || text.startsWith("-") || text.startsWith("•")) {
        if (text.startsWith("&amp;", ignoreCase = true)) {
            text = text.substring(5).trim()
        } else {
            text = text.substring(1).trim()
        }
    }
    return text
}

@Composable
private fun DrugTagBadge(
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
        )
    }
}

@Composable
private fun InteractionRow(interaction: DrugInteraction) {
    val (color, containerColor) = when (interaction.severity) {
        InteractionSeverity.MINOR -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primaryContainer
        InteractionSeverity.MODERATE -> MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.tertiaryContainer
        InteractionSeverity.MAJOR -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = containerColor.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(MedBotSpacing.large),
            verticalArrangement = Arrangement.spacedBy(MedBotSpacing.small)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = color)
                    Spacer(Modifier.width(MedBotSpacing.small))
                    Text(
                        "${interaction.drugA} + ${interaction.drugB}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(MedBotSpacing.small))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = color
                ) {
                    Text(
                        text = interaction.severity.label,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Text(
                text = interaction.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Rekomendasi: ${interaction.recommendation}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(MedBotSpacing.medium)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 2. LABORATORY TAB
// ---------------------------------------------------------------------------

@Composable
private fun LabToolsContent(viewModel: ToolsViewModel, windowWidth: MedBotWindowWidth) {
    val labTests by viewModel.labTests.collectAsStateWithLifecycle()
    val evaluation by viewModel.labEvaluation.collectAsStateWithLifecycle()
    var selectedTest by rememberSaveable { mutableStateOf("") }
    var value by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf("Semua") }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val categories = remember(labTests) {
        listOf("Semua") + labTests.map { it.category }.distinct()
    }
    val filteredTests = remember(labTests, selectedCategory, searchQuery) {
        labTests.filter { test ->
            val matchesCategory = (selectedCategory == "Semua" || test.category == selectedCategory)
            val matchesQuery = (searchQuery.isBlank() || test.testName.contains(searchQuery, ignoreCase = true) || test.clinicalSignificance.contains(searchQuery, ignoreCase = true) || test.category.contains(searchQuery, ignoreCase = true))
            matchesCategory && matchesQuery
        }
    }

    val currentTest = remember(selectedTest, labTests) {
        labTests.find { it.testName == selectedTest }
    }

    if (windowWidth == MedBotWindowWidth.EXPANDED) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(toolContentPadding())
                .imePadding(),
            horizontalArrangement = Arrangement.spacedBy(MedBotSpacing.large)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MedBotSpacing.medium)
            ) {
                LabCatalogSection(
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onSelectCategory = { selectedCategory = it },
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    filteredTests = filteredTests,
                    selectedTest = selectedTest,
                    onSelectTest = {
                        selectedTest = it
                        value = ""
                        viewModel.onEvent(ToolsUiEvent.ResetLabEvaluation)
                    }
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MedBotSpacing.medium)
            ) {
                LabEvaluationSection(
                    selectedTest = selectedTest,
                    currentTest = currentTest,
                    value = value,
                    onValueChange = { value = it },
                    onEvaluate = { viewModel.onEvent(ToolsUiEvent.EvaluateLab(selectedTest, value)) },
                    evaluation = evaluation
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = toolContentPadding(),
            verticalArrangement = Arrangement.spacedBy(MedBotSpacing.large)
        ) {
            item {
                ToolCard {
                    ToolCardHeader(
                        icon = Icons.Filled.Science,
                        title = stringResource(R.string.tools_lab_heading),
                        subtitle = stringResource(R.string.tools_lab_support)
                    )
                }
            }

            if (selectedTest.isNotBlank()) {
                item {
                    LabEvaluationSection(
                        selectedTest = selectedTest,
                        currentTest = currentTest,
                        value = value,
                        onValueChange = { value = it },
                        onEvaluate = { viewModel.onEvent(ToolsUiEvent.EvaluateLab(selectedTest, value)) },
                        evaluation = evaluation
                    )
                }
            }

            item {
                LabCatalogSection(
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onSelectCategory = { selectedCategory = it },
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    filteredTests = filteredTests,
                    selectedTest = selectedTest,
                    onSelectTest = {
                        selectedTest = it
                        value = ""
                        viewModel.onEvent(ToolsUiEvent.ResetLabEvaluation)
                    }
                )
            }
        }
    }
}

@Composable
private fun LabCatalogSection(
    categories: List<String>,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    filteredTests: List<LabTest>,
    selectedTest: String,
    onSelectTest: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(MedBotSpacing.medium)) {
        Text(
            text = "Katalog Parameter Laboratorium",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        // Search Input Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            label = { Text(stringResource(R.string.tools_lab_search_label)) },
            leadingIcon = {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true
        )

        // Category Filter Row
        LazyRow(horizontalArrangement = Arrangement.spacedBy(MedBotSpacing.small)) {
            items(categories) { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { onSelectCategory(cat) },
                    shape = RoundedCornerShape(8.dp),
                    label = { Text(cat) },
                    modifier = Modifier.springBounceClick()
                )
            }
        }

        if (filteredTests.isEmpty()) {
            EmptyStateView(
                icon = Icons.Filled.Science,
                title = if (searchQuery.isNotBlank()) "Parameter Tidak Ditemukan" else stringResource(R.string.tools_lab_empty),
                description = if (searchQuery.isNotBlank()) "Tidak ada hasil parameter lab yang cocok dengan \"$searchQuery\"." else stringResource(R.string.tools_lab_support)
            )
        } else {
            filteredTests.forEach { test ->
                LabTestRow(
                    test = test,
                    selected = test.testName == selectedTest,
                    onClick = { onSelectTest(test.testName) }
                )
            }
        }
    }
}

@Composable
private fun LabEvaluationSection(
    selectedTest: String,
    currentTest: LabTest?,
    value: String,
    onValueChange: (String) -> Unit,
    onEvaluate: () -> Unit,
    evaluation: LabEvaluationState?
) {
    Column(verticalArrangement = Arrangement.spacedBy(MedBotSpacing.medium)) {
        if (selectedTest.isNotBlank()) {
            ToolCard(selected = true) {
                ToolCardHeader(
                    icon = Icons.Filled.Science,
                    title = "${stringResource(R.string.tools_lab_selected_title)}: $selectedTest",
                    subtitle = currentTest?.category
                )

                if (currentTest != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(MedBotSpacing.medium),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(MedBotSpacing.small))
                            Text(
                                text = "${stringResource(R.string.tools_lab_reference_label)}: ${currentTest.normalLow} – ${currentTest.normalHigh} ${currentTest.unit}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.tools_lab_value)) },
                    supportingText = { Text(stringResource(R.string.tools_lab_value_support)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    suffix = currentTest?.let { { Text(it.unit, fontWeight = FontWeight.Bold) } }
                )

                Button(
                    onClick = onEvaluate,
                    enabled = value.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .springBounceClick(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Science, contentDescription = null)
                    Spacer(Modifier.width(MedBotSpacing.small))
                    Text(stringResource(R.string.tools_evaluate_lab), fontWeight = FontWeight.Bold)
                }
            }
        } else {
            EmptyStateView(
                icon = Icons.Filled.Science,
                title = stringResource(R.string.tools_lab_no_selection),
                description = stringResource(R.string.tools_lab_support)
            )
        }

        evaluation?.let { result ->
            LabEvaluationSurface(result)
        }
    }
}

@Composable
private fun LabTestRow(test: LabTest, selected: Boolean, onClick: () -> Unit) {
    ToolCard(selected = selected, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Science,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.width(MedBotSpacing.medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    test.testName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${test.category} • Rentang: ${test.normalLow}–${test.normalHigh} ${test.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 3. CALCULATORS TAB (Completely Unified, Beautiful, High-Contrast UI)
// ---------------------------------------------------------------------------

@Composable
private fun CalculatorToolsContent(viewModel: ToolsViewModel, windowWidth: MedBotWindowWidth) {
    val state by viewModel.calculatorState.collectAsStateWithLifecycle()
    var selectedCalcSubTab by rememberSaveable { mutableIntStateOf(0) }

    var bmiWeight by rememberSaveable { mutableStateOf("") }
    var bmiHeight by rememberSaveable { mutableStateOf("") }

    var day by rememberSaveable { mutableStateOf("") }
    var month by rememberSaveable { mutableStateOf("") }
    var year by rememberSaveable { mutableStateOf("") }

    var ageMonths by rememberSaveable { mutableStateOf("") }
    var paeWeight by rememberSaveable { mutableStateOf("") }
    var paeHeight by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf("male") }
    var drug by rememberSaveable { mutableStateOf("") }
    var indication by rememberSaveable { mutableStateOf("") }

    val actionRequester = remember { BringIntoViewRequester() }

    val content = @Composable {
        Column(verticalArrangement = Arrangement.spacedBy(MedBotSpacing.large)) {
            // SINGLE UNIFIED HERO CARD
            ToolCard {
                ToolCardHeader(
                    icon = when (selectedCalcSubTab) {
                        0 -> Icons.Filled.Scale
                        1 -> Icons.Filled.CalendarMonth
                        else -> Icons.Filled.ChildCare
                    },
                    title = when (selectedCalcSubTab) {
                        0 -> stringResource(R.string.tools_bmi_title)
                        1 -> stringResource(R.string.tools_due_date_title)
                        else -> stringResource(R.string.tools_paediatric_title)
                    },
                    subtitle = when (selectedCalcSubTab) {
                        0 -> "Indeks Massa Tubuh (Berat / Tinggi²)"
                        1 -> "Rumus Naegele Hari Pertama Haid Terakhir (HPHT)"
                        else -> "Dosis Berat Badan & Analisis Z-Score"
                    }
                )

                // Sleek Full-Width Segmented Sub-tab Selector
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        CalculatorSubTabPill(
                            selected = selectedCalcSubTab == 0,
                            icon = Icons.Filled.Scale,
                            label = "BMI",
                            onClick = {
                                if (selectedCalcSubTab != 0) {
                                    selectedCalcSubTab = 0
                                    viewModel.onEvent(ToolsUiEvent.ResetCalculator)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        CalculatorSubTabPill(
                            selected = selectedCalcSubTab == 1,
                            icon = Icons.Filled.CalendarMonth,
                            label = "HPL",
                            onClick = {
                                if (selectedCalcSubTab != 1) {
                                    selectedCalcSubTab = 1
                                    viewModel.onEvent(ToolsUiEvent.ResetCalculator)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        CalculatorSubTabPill(
                            selected = selectedCalcSubTab == 2,
                            icon = Icons.Filled.ChildCare,
                            label = "Pediatrik",
                            onClick = {
                                if (selectedCalcSubTab != 2) {
                                    selectedCalcSubTab = 2
                                    viewModel.onEvent(ToolsUiEvent.ResetCalculator)
                                }
                            },
                            modifier = Modifier.weight(1.1f)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                // CALCULATOR FORM BODY
                when (selectedCalcSubTab) {
                    0 -> {
                        // BMI Form
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(MedBotSpacing.medium)
                        ) {
                            NumberField(
                                value = bmiWeight,
                                onValueChange = { bmiWeight = it },
                                label = "Berat Badan",
                                exampleText = "Contoh: 65",
                                suffix = "kg",
                                modifier = Modifier.weight(1f),
                                actionRequester = actionRequester
                            )
                            NumberField(
                                value = bmiHeight,
                                onValueChange = { bmiHeight = it },
                                label = "Tinggi Badan",
                                exampleText = "Contoh: 170",
                                suffix = "cm",
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
                                .springBounceClick(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Scale, contentDescription = null)
                            Spacer(Modifier.width(MedBotSpacing.small))
                            Text(stringResource(R.string.tools_calculate_bmi), fontWeight = FontWeight.Bold)
                        }
                    }
                    1 -> {
                        // Due Date (HPL) Form
                        Text(
                            text = "Hari Pertama Haid Terakhir (HPHT):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(MedBotSpacing.small)
                        ) {
                            NumberField(
                                value = day,
                                onValueChange = { day = it },
                                label = "Hari",
                                exampleText = "1–31",
                                modifier = Modifier.weight(1f),
                                actionRequester = actionRequester
                            )
                            NumberField(
                                value = month,
                                onValueChange = { month = it },
                                label = "Bulan",
                                exampleText = "1–12",
                                modifier = Modifier.weight(1f),
                                actionRequester = actionRequester
                            )
                            NumberField(
                                value = year,
                                onValueChange = { year = it },
                                label = "Tahun",
                                exampleText = "YYYY",
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
                                .springBounceClick(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.CalendarMonth, contentDescription = null)
                            Spacer(Modifier.width(MedBotSpacing.small))
                            Text(stringResource(R.string.tools_calculate_due_date), fontWeight = FontWeight.Bold)
                        }
                    }
                    else -> {
                        // Paediatric Calculator Form
                        Text(
                            text = "1. Data Pasien Anak",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Row 1: Usia & Berat Badan
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(MedBotSpacing.medium)
                        ) {
                            NumberField(
                                value = ageMonths,
                                onValueChange = { ageMonths = it },
                                label = "Usia Anak",
                                exampleText = "Contoh: 24",
                                suffix = "bln",
                                modifier = Modifier.weight(1f),
                                actionRequester = actionRequester
                            )
                            NumberField(
                                value = paeWeight,
                                onValueChange = { paeWeight = it },
                                label = "Berat Badan",
                                exampleText = "Contoh: 12",
                                suffix = "kg",
                                modifier = Modifier.weight(1f),
                                actionRequester = actionRequester
                            )
                        }

                        // Row 2: Tinggi Badan
                        NumberField(
                            value = paeHeight,
                            onValueChange = { paeHeight = it },
                            label = "Tinggi Badan",
                            exampleText = "Contoh: 85",
                            suffix = "cm",
                            modifier = Modifier.fillMaxWidth(),
                            actionRequester = actionRequester
                        )

                        // Row 3: Jenis Kelamin dengan Radio Button
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Jenis Kelamin Anak",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(MedBotSpacing.medium)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (gender == "male") MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (gender == "male") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { gender = "male" }
                                        .springBounceClick()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = gender == "male",
                                            onClick = { gender = "male" }
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.gender_male),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (gender == "male") FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (gender == "female") MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (gender == "female") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { gender = "female" }
                                        .springBounceClick()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = gender == "female",
                                            onClick = { gender = "female" }
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.gender_female),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (gender == "female") FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = "2. Informasi Terapi Obat",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Row 4: Nama Obat & Indikasi
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(MedBotSpacing.medium)
                        ) {
                            CalculatorTextField(
                                value = drug,
                                onValueChange = { drug = it },
                                label = "Nama Obat",
                                exampleText = "Contoh: Paracetamol",
                                modifier = Modifier.weight(1f),
                                actionRequester = actionRequester
                            )
                            CalculatorTextField(
                                value = indication,
                                onValueChange = { indication = it },
                                label = "Indikasi",
                                exampleText = "Contoh: Demam",
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
                                .springBounceClick(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.ChildCare, contentDescription = null)
                            Spacer(Modifier.width(MedBotSpacing.small))
                            Text(stringResource(R.string.tools_calculate_paediatric), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            val currentSubTabKind = when (selectedCalcSubTab) {
                0 -> CalculatorKind.BMI
                1 -> CalculatorKind.DUE_DATE
                else -> CalculatorKind.PAEDIATRIC
            }

            if (state.kind == currentSubTabKind) {
                if (state.isRunning) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(MedBotSpacing.large)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                            Spacer(Modifier.width(MedBotSpacing.medium))
                            Text(stringResource(R.string.calculator_running), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                state.error?.let { error ->
                    ToolResultSurface(calculatorErrorText(error), isError = true)
                }

                state.result?.let { result ->
                    CalculatorResultSurface(state.kind, result)
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = toolContentPadding(),
        verticalArrangement = Arrangement.spacedBy(MedBotSpacing.large)
    ) {
        item {
            content()
        }
    }
}

@Composable
private fun CalculatorSubTabPill(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(40.dp)
            .clickable(role = Role.Tab, onClick = onClick)
            .springBounceClick(),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun GenderToggleOption(
    selected: Boolean,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .clickable(role = Role.Button, onClick = onClick)
            .springBounceClick(),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 4. REMINDERS TAB
// ---------------------------------------------------------------------------

@Composable
private fun ReminderToolsContent(viewModel: ToolsViewModel, windowWidth: MedBotWindowWidth) {
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val addReminderRequester = remember { BringIntoViewRequester() }

    var title by rememberSaveable { mutableStateOf("") }
    var selectedType by rememberSaveable { mutableStateOf(com.medbot.app.domain.model.ReminderType.MEDICATION) }
    var hour by rememberSaveable { mutableStateOf("08") }
    var minute by rememberSaveable { mutableStateOf("00") }
    var notificationMode by rememberSaveable { mutableStateOf(com.medbot.app.domain.model.ReminderNotificationMode.SOUND_AND_VIBRATE) }
    var showTimePickerDialog by rememberSaveable { mutableStateOf(false) }

    val quickTitles = listOf(
        Pair("💊 Minum Obat", com.medbot.app.domain.model.ReminderType.MEDICATION),
        Pair("🩸 Cek Gula Darah", com.medbot.app.domain.model.ReminderType.VITALS_CHECK),
        Pair("🩺 Cek Tekanan Darah", com.medbot.app.domain.model.ReminderType.VITALS_CHECK),
        Pair("💧 Minum Air", com.medbot.app.domain.model.ReminderType.WATER),
        Pair("🥗 Vitamin & Suplemen", com.medbot.app.domain.model.ReminderType.MEDICATION)
    )

    val quickTimes = listOf(
        Pair("07:00 Pagi", Pair("07", "00")),
        Pair("12:00 Siang", Pair("12", "00")),
        Pair("18:00 Sore", Pair("18", "00")),
        Pair("21:00 Malam", Pair("21", "00"))
    )

    if (showTimePickerDialog) {
        val initialH = hour.toIntOrNull()?.coerceIn(0, 23) ?: 8
        val initialM = minute.toIntOrNull()?.coerceIn(0, 59) ?: 0
        val timePickerState = rememberTimePickerState(
            initialHour = initialH,
            initialMinute = initialM,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePickerDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        hour = "%02d".format(timePickerState.hour)
                        minute = "%02d".format(timePickerState.minute)
                        showTimePickerDialog = false
                    },
                    modifier = Modifier.springBounceClick(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Pilih Waktu")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showTimePickerDialog = false },
                    modifier = Modifier.springBounceClick()
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TimePicker(state = timePickerState)
                }
            }
        )
    }

    if (windowWidth == MedBotWindowWidth.EXPANDED) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(toolContentPadding())
                .imePadding(),
            horizontalArrangement = Arrangement.spacedBy(MedBotSpacing.large)
        ) {
            Column(
                modifier = Modifier.weight(1.1f),
                verticalArrangement = Arrangement.spacedBy(MedBotSpacing.large)
            ) {
                AddReminderCard(
                    title = title,
                    onTitleChange = { title = it },
                    selectedType = selectedType,
                    onTypeChange = { selectedType = it },
                    hour = hour,
                    minute = minute,
                    notificationMode = notificationMode,
                    onNotificationModeChange = { notificationMode = it },
                    onPickTimeClick = { showTimePickerDialog = true },
                    quickTitles = quickTitles,
                    quickTimes = quickTimes,
                    onSelectPresetTime = { h, m ->
                        hour = h
                        minute = m
                    },
                    onSubmit = {
                        viewModel.onEvent(
                            ToolsUiEvent.AddReminder(
                                title = title,
                                hour = hour.toIntOrNull() ?: -1,
                                minute = minute.toIntOrNull() ?: -1,
                                type = selectedType,
                                notificationMode = notificationMode
                            )
                        )
                        title = ""
                    },
                    actionRequester = addReminderRequester
                )
            }

            Column(
                modifier = Modifier.weight(0.9f),
                verticalArrangement = Arrangement.spacedBy(MedBotSpacing.medium)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.tools_reminder_active_heading),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${reminders.size} Pengingat",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (reminders.isNotEmpty()) {
                    reminders.forEach { reminder ->
                        ReminderRow(reminder, viewModel)
                    }
                } else {
                    EmptyStateView(
                        icon = Icons.Filled.NotificationsNone,
                        title = stringResource(R.string.tools_reminder_empty),
                        description = stringResource(R.string.tools_reminder_empty_desc)
                    )
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = toolContentPadding(),
            verticalArrangement = Arrangement.spacedBy(MedBotSpacing.large)
        ) {
            item {
                AddReminderCard(
                    title = title,
                    onTitleChange = { title = it },
                    selectedType = selectedType,
                    onTypeChange = { selectedType = it },
                    hour = hour,
                    minute = minute,
                    notificationMode = notificationMode,
                    onNotificationModeChange = { notificationMode = it },
                    onPickTimeClick = { showTimePickerDialog = true },
                    quickTitles = quickTitles,
                    quickTimes = quickTimes,
                    onSelectPresetTime = { h, m ->
                        hour = h
                        minute = m
                    },
                    onSubmit = {
                        viewModel.onEvent(
                            ToolsUiEvent.AddReminder(
                                title = title,
                                hour = hour.toIntOrNull() ?: -1,
                                minute = minute.toIntOrNull() ?: -1,
                                type = selectedType,
                                notificationMode = notificationMode
                            )
                        )
                        title = ""
                    },
                    actionRequester = addReminderRequester
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.tools_reminder_active_heading),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${reminders.size} Pengingat",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (reminders.isNotEmpty()) {
                items(reminders, key = { it.id }) { reminder ->
                    ReminderRow(reminder, viewModel)
                }
            } else {
                item {
                    EmptyStateView(
                        icon = Icons.Filled.NotificationsNone,
                        title = stringResource(R.string.tools_reminder_empty),
                        description = stringResource(R.string.tools_reminder_empty_desc)
                    )
                }
            }
        }
    }
}

@Composable
private fun AddReminderCard(
    title: String,
    onTitleChange: (String) -> Unit,
    selectedType: com.medbot.app.domain.model.ReminderType,
    onTypeChange: (com.medbot.app.domain.model.ReminderType) -> Unit,
    hour: String,
    minute: String,
    notificationMode: com.medbot.app.domain.model.ReminderNotificationMode,
    onNotificationModeChange: (com.medbot.app.domain.model.ReminderNotificationMode) -> Unit,
    onPickTimeClick: () -> Unit,
    quickTitles: List<Pair<String, com.medbot.app.domain.model.ReminderType>>,
    quickTimes: List<Pair<String, Pair<String, String>>>,
    onSelectPresetTime: (String, String) -> Unit,
    onSubmit: () -> Unit,
    actionRequester: BringIntoViewRequester
) {
    val hVal = hour.toIntOrNull()
    val mVal = minute.toIntOrNull()
    val isValid = title.isNotBlank() && hVal != null && hVal in 0..23 && mVal != null && mVal in 0..59

    ToolCard {
        ToolCardHeader(
            icon = Icons.Filled.NotificationsActive,
            title = stringResource(R.string.tools_reminder_heading),
            subtitle = "Jadwalkan alarm & notifikasi pengingat kesehatan offline"
        )

        // 1. Input Judul / Nama Pengingat
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Nama Pengingat / Obat *") },
            supportingText = { Text(stringResource(R.string.tools_reminder_title_support)) },
            leadingIcon = { Icon(Icons.Filled.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            trailingIcon = {
                if (title.isNotBlank()) {
                    IconButton(onClick = { onTitleChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        // Quick Suggestions (2 rows responsive wrap)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Templat Cepat:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quickTitles.take(3).forEach { (suggestion, type) ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                onTitleChange(suggestion.substringAfter(" "))
                                onTypeChange(type)
                            }
                            .springBounceClick()
                    ) {
                        Text(
                            text = suggestion,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quickTitles.drop(3).forEach { (suggestion, type) ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                onTitleChange(suggestion.substringAfter(" "))
                                onTypeChange(type)
                            }
                            .springBounceClick()
                    ) {
                        Text(
                            text = suggestion,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

        // 2. Kategori / Jenis Pengingat
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Jenis Pengingat",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                com.medbot.app.domain.model.ReminderType.values().forEach { type ->
                    val isSelected = selectedType == type
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onTypeChange(type) }
                            .springBounceClick()
                    ) {
                        Text(
                            text = type.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

        // 3. Waktu & Jam Pengingat
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.tools_reminder_time_label),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onPickTimeClick)
                    .springBounceClick(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MedBotSpacing.large, vertical = MedBotSpacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.AccessTime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(MedBotSpacing.medium))
                        Text(
                            text = "${hour.padStart(2, '0')} : ${minute.padStart(2, '0')} WIB",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    OutlinedButton(
                        onClick = onPickTimeClick,
                        modifier = Modifier.springBounceClick(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Pilih Jam")
                    }
                }
            }

            // 2x2 Grid of Preset Times (Responsive, never cut-off)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quickTimes.take(2).forEach { (label, timePair) ->
                    val isSelected = hour == timePair.first && minute == timePair.second
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectPresetTime(timePair.first, timePair.second) },
                        shape = RoundedCornerShape(8.dp),
                        label = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                        modifier = Modifier.weight(1f).springBounceClick()
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quickTimes.drop(2).forEach { (label, timePair) ->
                    val isSelected = hour == timePair.first && minute == timePair.second
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectPresetTime(timePair.first, timePair.second) },
                        shape = RoundedCornerShape(8.dp),
                        label = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                        modifier = Modifier.weight(1f).springBounceClick()
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

        // 4. Mode Notifikasi (Suara & Getar / Hanya Getar / Tanpa Suara)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Mode Notifikasi Alarm",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                com.medbot.app.domain.model.ReminderNotificationMode.values().forEach { mode ->
                    val isSelected = notificationMode == mode
                    val icon = when (mode) {
                        com.medbot.app.domain.model.ReminderNotificationMode.SOUND_AND_VIBRATE -> Icons.Filled.NotificationsActive
                        com.medbot.app.domain.model.ReminderNotificationMode.VIBRATE_ONLY -> Icons.Filled.NotificationsActive
                        com.medbot.app.domain.model.ReminderNotificationMode.SILENT -> Icons.Filled.NotificationsNone
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNotificationModeChange(mode) }
                            .springBounceClick()
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(MedBotSpacing.xSmall))

        Button(
            onClick = onSubmit,
            enabled = isValid,
            modifier = Modifier
                .bringIntoViewRequester(actionRequester)
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .springBounceClick(),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Filled.AddAlarm, contentDescription = null)
            Spacer(Modifier.width(MedBotSpacing.small))
            Text(
                text = if (title.isBlank()) "Ketik nama pengingat untuk menyimpan" else stringResource(R.string.tools_add_reminder),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ReminderRow(reminder: Reminder, viewModel: ToolsViewModel) {
    ToolCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (reminder.isEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(54.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "%02d:%02d".format(reminder.timeHour, reminder.timeMinute),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (reminder.isEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(MedBotSpacing.medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reminder.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (reminder.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = reminder.type.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = when (reminder.notificationMode) {
                            com.medbot.app.domain.model.ReminderNotificationMode.VIBRATE_ONLY -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                            com.medbot.app.domain.model.ReminderNotificationMode.SILENT -> MaterialTheme.colorScheme.surfaceVariant
                            com.medbot.app.domain.model.ReminderNotificationMode.SOUND_AND_VIBRATE -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        }
                    ) {
                        Text(
                            text = when (reminder.notificationMode) {
                                com.medbot.app.domain.model.ReminderNotificationMode.VIBRATE_ONLY -> "📳 Getar"
                                com.medbot.app.domain.model.ReminderNotificationMode.SILENT -> "🔕 Hening"
                                com.medbot.app.domain.model.ReminderNotificationMode.SOUND_AND_VIBRATE -> "🔊 Suara"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Switch(
                checked = reminder.isEnabled,
                onCheckedChange = { viewModel.onEvent(ToolsUiEvent.ToggleReminder(reminder.id, it)) },
                modifier = Modifier
                    .semantics { contentDescription = reminder.title }
                    .springBounceClick()
            )
            IconButton(
                onClick = { viewModel.onEvent(ToolsUiEvent.DeleteReminder(reminder.id)) },
                modifier = Modifier.springBounceClick()
            ) {
                Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 5. COMMON UI & HELPER COMPONENTS
// ---------------------------------------------------------------------------

@Composable
private fun ToolCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(MedBotSpacing.medium),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val clickableModifier = if (onClick != null) {
        Modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .springBounceClick()
    } else {
        Modifier
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(clickableModifier),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface,
        tonalElevation = if (selected) 2.dp else 1.dp,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(MedBotSpacing.medium)
        ) {
            content()
        }
    }
}

@Composable
private fun ToolCardHeader(
    icon: ImageVector,
    title: String,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(42.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(Modifier.width(MedBotSpacing.medium))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyStateView(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = MedBotSpacing.xLarge, horizontal = MedBotSpacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MedBotSpacing.small)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(Modifier.height(MedBotSpacing.xSmall))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    exampleText: String? = null,
    suffix: String? = null,
    modifier: Modifier = Modifier.fillMaxWidth(),
    actionRequester: BringIntoViewRequester
) {
    CalculatorTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        exampleText = exampleText,
        suffix = suffix,
        modifier = modifier,
        actionRequester = actionRequester,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

@Composable
private fun CalculatorTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    exampleText: String? = null,
    suffix: String? = null,
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
        label = { Text(label) },
        supportingText = exampleText?.let { { Text(it) } },
        suffix = suffix?.let { { Text(it, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary) } },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = keyboardOptions
    )
}

@Composable
private fun ToolResultSurface(text: String, isError: Boolean = false) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(MedBotSpacing.large),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(MedBotSpacing.medium)
        ) {
            Icon(
                if (isError) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    stringResource(R.string.tools_result_heading),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(4.dp))
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
    when (state.kind) {
        LabEvaluationKind.INVALID_INPUT -> {
            ToolResultSurface(stringResource(R.string.tools_lab_invalid), isError = true)
        }
        LabEvaluationKind.UNAVAILABLE -> {
            ToolResultSurface(stringResource(R.string.tools_lab_unavailable), isError = true)
        }
        LabEvaluationKind.RESULT -> {
            val test = state.test
            val value = state.value
            if (test == null || value == null) {
                ToolResultSurface(stringResource(R.string.tools_lab_unavailable), isError = true)
                return
            }

            val statusText = when (state.status) {
                LabComparisonStatus.WITHIN_REFERENCE -> "NORMAL (Dalam Rentang Rujukan)"
                LabComparisonStatus.ABOVE_REFERENCE -> "DI ATAS NORMAL (Tinggi)"
                LabComparisonStatus.BELOW_REFERENCE -> "DI BAWAH NORMAL (Rendah)"
                null -> "EVALUASI"
            }
            val statusBg = when (state.status) {
                LabComparisonStatus.WITHIN_REFERENCE -> Color(0xFF27AE60).copy(alpha = 0.15f)
                LabComparisonStatus.ABOVE_REFERENCE -> Color(0xFFE74C3C).copy(alpha = 0.15f)
                LabComparisonStatus.BELOW_REFERENCE -> Color(0xFFF39C12).copy(alpha = 0.15f)
                null -> MaterialTheme.colorScheme.surfaceVariant
            }
            val statusFg = when (state.status) {
                LabComparisonStatus.WITHIN_REFERENCE -> Color(0xFF27AE60)
                LabComparisonStatus.ABOVE_REFERENCE -> Color(0xFFE74C3C)
                LabComparisonStatus.BELOW_REFERENCE -> Color(0xFFF39C12)
                null -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            val statusIcon = when (state.status) {
                LabComparisonStatus.WITHIN_REFERENCE -> Icons.Filled.CheckCircle
                LabComparisonStatus.ABOVE_REFERENCE -> Icons.Filled.Warning
                LabComparisonStatus.BELOW_REFERENCE -> Icons.Filled.Info
                null -> Icons.Filled.Info
            }

            val interpretationText = when (state.status) {
                LabComparisonStatus.WITHIN_REFERENCE -> "Hasil laboratorium Anda berada dalam batas rentang rujukan normal (${test.normalLow} – ${test.normalHigh} ${test.unit}). Pertahankan pola hidup sehat dan kontrol berkala."
                LabComparisonStatus.ABOVE_REFERENCE -> test.interpretationHigh
                LabComparisonStatus.BELOW_REFERENCE -> test.interpretationLow
                null -> ""
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.5.dp, statusFg.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(MedBotSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(MedBotSpacing.medium)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = test.testName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Kategori: ${test.category}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = statusBg
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = statusIcon,
                                    contentDescription = null,
                                    tint = statusFg,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = statusFg
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = MedBotSpacing.medium, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Nilai Terukur",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "$value ${test.unit}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = statusFg
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Nilai Rujukan Normal",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${test.normalLow} – ${test.normalHigh} ${test.unit}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Interpretasi Klinis:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = interpretationText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (test.clinicalSignificance.isNotBlank()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Signifikansi Medis:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = test.clinicalSignificance,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun toolContentPadding(): PaddingValues = PaddingValues(
    start = MedBotSpacing.medium,
    top = MedBotSpacing.medium,
    end = MedBotSpacing.medium,
    bottom = MedBotSpacing.xxLarge
)
