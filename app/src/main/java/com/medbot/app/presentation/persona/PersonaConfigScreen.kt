@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.medbot.app.presentation.persona

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medbot.app.R
import com.medbot.app.core.designsystem.components.AdaptiveContent
import com.medbot.app.core.designsystem.components.AdaptiveFlowRow
import com.medbot.app.core.designsystem.components.MedBotSpacing
import com.medbot.app.core.designsystem.components.MedBotTopAppBar
import com.medbot.app.core.designsystem.components.springBounceClick
import com.medbot.app.domain.agents.AgentRegistry
import com.medbot.app.domain.model.AppLanguage
import com.medbot.app.domain.model.DetailDepth
import com.medbot.app.domain.model.PersonaTone
import com.medbot.app.presentation.common.appLanguageLabel
import com.medbot.app.presentation.common.displayName
import com.medbot.app.presentation.common.personaToneLabel
import com.medbot.app.presentation.common.detailDepthLabel
import com.medbot.app.presentation.common.specialty

@Composable
fun PersonaConfigScreen(viewModel: PersonaViewModel, onNavigateBack: () -> Unit) {
    val config by viewModel.personaConfig.collectAsStateWithLifecycle()
    var selectedAgentId by remember(config.selectedAgentId) { mutableStateOf(config.selectedAgentId) }
    var selectedTone by remember(config.tone) { mutableStateOf(config.tone) }
    var selectedDepth by remember(config.depth) { mutableStateOf(config.depth) }
    var selectedLanguage by remember(config.language) { mutableStateOf(config.language) }
    var customInstructions by remember(config.customInstructions) { mutableStateOf(config.customInstructions) }
    var patientProfile by remember(config.patientProfileSummary) { mutableStateOf(config.patientProfileSummary) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredAgents = remember(searchQuery, selectedLanguage) {
        if (searchQuery.isBlank()) AgentRegistry.ALL_AGENTS else AgentRegistry.ALL_AGENTS.filter { agent ->
            agent.displayNameId.contains(searchQuery, ignoreCase = true) ||
                agent.displayNameEn.contains(searchQuery, ignoreCase = true) ||
                agent.specialtyId.contains(searchQuery, ignoreCase = true) ||
                agent.specialtyEn.contains(searchQuery, ignoreCase = true)
        }
    }

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

    Column {
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
                    Text(stringResource(R.string.action_save))
                }
            }
        )
        AdaptiveContent(modifier = Modifier.weight(1f), maxContentWidth = 840.dp) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = MedBotSpacing.medium, vertical = MedBotSpacing.medium),
                verticalArrangement = Arrangement.spacedBy(MedBotSpacing.large)
            ) {
                item {
                    Section(title = stringResource(R.string.persona_language)) {
                        AdaptiveFlowRow {
                            AppLanguage.values().forEach { language ->
                                FilterChip(
                                    selected = selectedLanguage == language,
                                    onClick = { selectedLanguage = language },
                                     label = { Text(appLanguageLabel(language)) },
                                    modifier = Modifier.springBounceClick()
                                )
                            }
                        }
                    }
                }
                item {
                    Section(title = stringResource(R.string.persona_tone)) {
                        AdaptiveFlowRow {
                            PersonaTone.values().forEach { tone ->
                                FilterChip(
                                    selected = selectedTone == tone,
                                    onClick = { selectedTone = tone },
                                     label = { Text(personaToneLabel(tone)) },
                                    modifier = Modifier.springBounceClick()
                                )
                            }
                        }
                    }
                }
                item {
                    Section(title = stringResource(R.string.persona_depth)) {
                        AdaptiveFlowRow {
                            DetailDepth.values().forEach { depth ->
                                FilterChip(
                                    selected = selectedDepth == depth,
                                    onClick = { selectedDepth = depth },
                                     label = { Text(detailDepthLabel(depth)) },
                                    modifier = Modifier.springBounceClick()
                                )
                            }
                        }
                    }
                }
                item {
                    Section(title = stringResource(R.string.persona_context)) {
                        Column(verticalArrangement = Arrangement.spacedBy(MedBotSpacing.medium)) {
                            OutlinedTextField(
                                value = patientProfile,
                                onValueChange = { patientProfile = it },
                                label = { Text(stringResource(R.string.persona_patient_profile)) },
                                supportingText = { Text(stringResource(R.string.persona_patient_profile_support)) },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3
                            )
                            OutlinedTextField(
                                value = customInstructions,
                                onValueChange = { customInstructions = it },
                                label = { Text(stringResource(R.string.persona_custom_instructions)) },
                                supportingText = { Text(stringResource(R.string.persona_custom_instructions_support)) },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3
                            )
                        }
                    }
                }
                item {
                    Section(title = stringResource(R.string.persona_agent_heading, filteredAgents.size)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text(stringResource(R.string.persona_search_agents)) },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
                items(filteredAgents, key = { it.id }) { agent ->
                    val selected = selectedAgentId == agent.id
                    Surface(
                         modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { selectedAgentId = agent.id }.springBounceClick(),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.medium,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(MedBotSpacing.medium),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.LocalHospital, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(MedBotSpacing.medium))
                            Column(modifier = Modifier.weight(1f)) {
                                 Text(agent.displayName(selectedLanguage), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                 Text(agent.specialty(selectedLanguage), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            if (selected) Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.persona_selected))
                        }
                    }
                }
                item {
                    Button(onClick = ::save, modifier = Modifier.fillMaxWidth().springBounceClick()) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MedBotSpacing.small)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        content()
    }
}
