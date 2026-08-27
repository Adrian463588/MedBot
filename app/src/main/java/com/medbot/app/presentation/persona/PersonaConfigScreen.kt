@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.medbot.app.presentation.persona

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medbot.app.R
import com.medbot.app.core.designsystem.components.AdaptiveContent
import com.medbot.app.core.designsystem.components.MedBotSpacing
import com.medbot.app.core.designsystem.components.MedBotTopAppBar
import com.medbot.app.core.designsystem.components.springBounceClick
import com.medbot.app.domain.agents.AgentRegistry
import com.medbot.app.domain.model.AppLanguage
import com.medbot.app.domain.model.DetailDepth
import com.medbot.app.domain.model.DoctorAgent
import com.medbot.app.domain.model.PersonaTone
import com.medbot.app.presentation.common.appLanguageLabel
import com.medbot.app.presentation.common.detailDepthDescription
import com.medbot.app.presentation.common.detailDepthLabel
import com.medbot.app.presentation.common.displayName
import com.medbot.app.presentation.common.personaToneDescription
import com.medbot.app.presentation.common.personaToneLabel
import com.medbot.app.presentation.common.specialty

@Composable
fun PersonaConfigScreen(
    viewModel: PersonaViewModel,
    onNavigateBack: () -> Unit
) {
    val config by viewModel.personaConfig.collectAsStateWithLifecycle()
    var selectedAgentId by remember(config.selectedAgentId) { mutableStateOf(config.selectedAgentId) }
    var selectedTone by remember(config.tone) { mutableStateOf(config.tone) }
    var selectedDepth by remember(config.depth) { mutableStateOf(config.depth) }
    var selectedLanguage by remember(config.language) { mutableStateOf(config.language) }
    var customInstructions by remember(config.customInstructions) { mutableStateOf(config.customInstructions) }
    var patientProfile by remember(config.patientProfileSummary) { mutableStateOf(config.patientProfileSummary) }

    fun save() {
        viewModel.onEvent(
            PersonaUiEvent.SaveConfig(
                config.copy(
                    selectedAgentId = selectedAgentId,
                    tone = selectedTone,
                    depth = selectedDepth,
                    language = selectedLanguage,
                    customInstructions = customInstructions,
                    patientProfileSummary = patientProfile
                )
            )
        )
        onNavigateBack()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        MedBotTopAppBar(
            title = stringResource(R.string.persona_title),
            subtitle = stringResource(R.string.persona_subtitle),
            navigationIcon = {
                IconButton(onClick = onNavigateBack, modifier = Modifier.springBounceClick()) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            },
            actions = {
                TextButton(onClick = ::save, modifier = Modifier.springBounceClick()) {
                    Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold)
                }
            }
        )
        AdaptiveContent(modifier = Modifier.weight(1f), maxContentWidth = 840.dp) {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = MedBotSpacing.medium,
                    top = MedBotSpacing.medium,
                    end = MedBotSpacing.medium,
                    bottom = MedBotSpacing.xxLarge
                ),
                verticalArrangement = Arrangement.spacedBy(MedBotSpacing.large),
                modifier = Modifier.imePadding()
            ) {
                // Section 1: Response Style & Tone
                item {
                    PersonaSectionCard(
                        icon = Icons.Filled.Psychology,
                        title = "Gaya Komunikasi AI",
                        subtitle = "Tentukan bahasa, nada percakapan, dan tingkat kedalaman penjelasan."
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(MedBotSpacing.medium)) {
                            // 1. Language Dropdown
                            PersonaDropdown(
                                label = stringResource(R.string.persona_language),
                                selectedValue = selectedLanguage,
                                options = AppLanguage.entries,
                                itemLabel = { appLanguageLabel(it) },
                                itemDescription = null,
                                leadingIcon = Icons.Filled.Translate,
                                onSelect = { selectedLanguage = it }
                            )

                            // 2. Response Tone Dropdown
                            PersonaDropdown(
                                label = stringResource(R.string.persona_tone),
                                selectedValue = selectedTone,
                                options = PersonaTone.entries,
                                itemLabel = { personaToneLabel(it) },
                                itemDescription = { personaToneDescription(it) },
                                leadingIcon = Icons.Filled.Tune,
                                onSelect = { selectedTone = it }
                            )

                            // 3. Response Depth Dropdown
                            PersonaDropdown(
                                label = stringResource(R.string.persona_depth),
                                selectedValue = selectedDepth,
                                options = DetailDepth.entries,
                                itemLabel = { detailDepthLabel(it) },
                                itemDescription = { detailDepthDescription(it) },
                                leadingIcon = Icons.AutoMirrored.Filled.FormatListBulleted,
                                onSelect = { selectedDepth = it }
                            )
                        }
                    }
                }

                // Section 2: Clinical Specialist (46 Doctors + Auto-routing)
                item {
                    PersonaSectionCard(
                        icon = Icons.Filled.MedicalServices,
                        title = "Dokter Spesialis Medis",
                        subtitle = "Pilih dokter spesialis khusus atau gunakan Kepala Triase Medis untuk perutean otomatis."
                    ) {
                        SearchableSpecialistDropdown(
                            selectedAgentId = selectedAgentId,
                            language = selectedLanguage,
                            onSelectAgent = { selectedAgentId = it }
                        )
                    }
                }

                // Section 3: Patient Context & Custom Rules
                item {
                    PersonaSectionCard(
                        icon = Icons.Filled.AccountCircle,
                        title = stringResource(R.string.persona_context),
                        subtitle = stringResource(R.string.persona_context_support)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(MedBotSpacing.medium)) {
                            OutlinedTextField(
                                value = patientProfile,
                                onValueChange = { patientProfile = it },
                                label = { Text(stringResource(R.string.persona_patient_profile)) },
                                supportingText = { Text(stringResource(R.string.persona_patient_profile_support)) },
                                leadingIcon = {
                                    Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                maxLines = 4
                            )

                            OutlinedTextField(
                                value = customInstructions,
                                onValueChange = { customInstructions = it },
                                label = { Text(stringResource(R.string.persona_custom_instructions)) },
                                supportingText = { Text(stringResource(R.string.persona_custom_instructions_support)) },
                                leadingIcon = {
                                    Icon(Icons.Filled.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                maxLines = 4
                            )
                        }
                    }
                }

                // Bottom Save Button
                item {
                    Button(
                        onClick = ::save,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .springBounceClick(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null)
                        Spacer(Modifier.width(MedBotSpacing.small))
                        Text(
                            text = stringResource(R.string.action_save),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonaSectionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(MedBotSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(MedBotSpacing.medium)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
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
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            content()
        }
    }
}

@Composable
private fun <T> PersonaDropdown(
    label: String,
    selectedValue: T,
    options: List<T>,
    itemLabel: @Composable (T) -> String,
    itemDescription: (@Composable (T) -> String)?,
    leadingIcon: ImageVector,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = itemLabel(selectedValue),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            leadingIcon = {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                val isSelected = option == selectedValue
                val desc = itemDescription?.invoke(option)
                DropdownMenuItem(
                    text = {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(
                                text = itemLabel(option),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            if (!desc.isNullOrBlank()) {
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    trailingIcon = if (isSelected) {
                        { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    } else null,
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun SearchableSpecialistDropdown(
    selectedAgentId: String,
    language: AppLanguage,
    onSelectAgent: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val selectedAgent = remember(selectedAgentId) {
        AgentRegistry.getAgentById(selectedAgentId)
    }

    val filteredAgents = remember(searchQuery, language) {
        if (searchQuery.isBlank()) {
            AgentRegistry.ALL_AGENTS
        } else {
            AgentRegistry.ALL_AGENTS.filter { agent ->
                agent.displayNameId.contains(searchQuery, ignoreCase = true) ||
                    agent.displayNameEn.contains(searchQuery, ignoreCase = true) ||
                    agent.specialtyId.contains(searchQuery, ignoreCase = true) ||
                    agent.specialtyEn.contains(searchQuery, ignoreCase = true) ||
                    agent.id.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MedBotSpacing.medium)
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = if (expanded) searchQuery else selectedAgent.displayName(language),
                onValueChange = {
                    searchQuery = it
                    if (!expanded) expanded = true
                },
                label = { Text("Dokter Spesialis") },
                supportingText = { Text(stringResource(R.string.persona_search_agents)) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.LocalHospital,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (searchQuery.isNotBlank() && expanded) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.action_clear))
                            }
                        }
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = true)
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = {
                    expanded = false
                    searchQuery = ""
                }
            ) {
                if (filteredAgents.isEmpty()) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.tools_drug_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = {}
                    )
                } else {
                    filteredAgents.forEach { agent ->
                        val isSelected = agent.id == selectedAgentId
                        DropdownMenuItem(
                            text = {
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Text(
                                        text = agent.displayName(language),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = agent.specialty(language),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            },
                            leadingIcon = {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Filled.LocalHospital,
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            trailingIcon = if (isSelected) {
                                { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                            } else null,
                            onClick = {
                                onSelectAgent(agent.id)
                                expanded = false
                                searchQuery = ""
                            },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // Active Specialist Details Preview Card (Clean FlowRow Layout)
        ActiveSpecialistPreviewCard(agent = selectedAgent, language = language)
    }
}

@Composable
private fun ActiveSpecialistPreviewCard(
    agent: DoctorAgent,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(MedBotSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(MedBotSpacing.small)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MedBotSpacing.medium)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.LocalHospital,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = agent.displayName(language),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = agent.specialty(language),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Capability tags wrapped in FlowRow to prevent any horizontal truncation or letter-wrapping
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
            ) {
                if (agent.supportsImage) {
                    SpecialistCapabilityBadge(text = "Dukungan Foto/Citra")
                }
                if (agent.tools.isNotEmpty()) {
                    SpecialistCapabilityBadge(text = "${agent.tools.size} Perkakas Medis")
                }
                if (agent.id == "orchestrator") {
                    SpecialistCapabilityBadge(text = "Triase & Auto-Routing")
                }
            }
        }
    }
}

@Composable
private fun SpecialistCapabilityBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            maxLines = 1
        )
    }
}
