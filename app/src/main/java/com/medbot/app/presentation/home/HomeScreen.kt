package com.medbot.app.presentation.home

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collect
import com.medbot.app.R
import com.medbot.app.core.designsystem.components.AdaptiveContent
import com.medbot.app.core.designsystem.components.MedBotSpacing
import com.medbot.app.core.designsystem.components.MedBotTopAppBar
import com.medbot.app.core.designsystem.components.MedBotWindowWidth
import com.medbot.app.core.designsystem.components.StatusBannerCard
import com.medbot.app.core.designsystem.components.springBounceClick
import com.medbot.app.domain.agents.AgentRegistry
import com.medbot.app.presentation.common.appLanguageLabel
import com.medbot.app.presentation.common.displayName
import com.medbot.app.presentation.common.specialty

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
    val isModelLoaded by viewModel.isModelLoaded.collectAsStateWithLifecycle()
    val modelName by viewModel.activeModelName.collectAsStateWithLifecycle()
    val ragCount by viewModel.ragDocumentCount.collectAsStateWithLifecycle()
    val persona by viewModel.personaConfig.collectAsStateWithLifecycle()
    val newSessionTitle = stringResource(R.string.chat_new_session_title)
    val activeAgent = remember(persona.selectedAgentId) { AgentRegistry.getAgentById(persona.selectedAgentId) }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is HomeUiEffect.OpenChat -> onNavigateToChat(effect.sessionId)
            }
        }
    }

    Column {
        MedBotTopAppBar(
            title = stringResource(R.string.home_title),
            subtitle = stringResource(R.string.home_subtitle),
            navigationIcon = {
                Image(
                    painter = painterResource(R.drawable.medbot_logo),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.padding(start = MedBotSpacing.medium, end = MedBotSpacing.small).size(32.dp)
                )
            },
            actions = {
                IconButton(onClick = onNavigateToPersona, modifier = Modifier.springBounceClick()) {
                    Icon(Icons.Filled.Tune, contentDescription = stringResource(R.string.action_persona))
                }
            }
        )
        AdaptiveContent(modifier = Modifier.weight(1f), maxContentWidth = 840.dp) { widthClass ->
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = MedBotSpacing.medium, vertical = MedBotSpacing.medium),
                verticalArrangement = Arrangement.spacedBy(MedBotSpacing.large)
            ) {
                item {
                    Column {
                        Text(stringResource(R.string.home_greeting), style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.padding(top = MedBotSpacing.xSmall))
                        Text(
                            text = stringResource(R.string.home_intro),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                item {
                    StatusBannerCard(
                        isModelLoaded = isModelLoaded,
                        modelName = modelName,
                        ragDocCount = ragCount,
                        onManageModel = onNavigateToModels,
                        onManageRag = onNavigateToKnowledge
                    )
                }
                item {
                    Button(
                         onClick = { viewModel.onEvent(HomeUiEvent.StartConsultation(newSessionTitle)) },
                        modifier = Modifier.fillMaxWidth().springBounceClick(),
                        contentPadding = PaddingValues(vertical = MedBotSpacing.medium)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                        Spacer(modifier = Modifier.width(MedBotSpacing.small))
                        Text(stringResource(R.string.home_start_consultation))
                    }
                }
                item {
                    Surface(
                         modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onNavigateToPersona).springBounceClick(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(MedBotSpacing.medium),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.MedicalServices, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(MedBotSpacing.medium))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.home_persona_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                 Text(activeAgent.displayName(persona.language), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                 Text(
                                     text = "${activeAgent.specialty(persona.language)} • ${appLanguageLabel(persona.language)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.action_persona))
                        }
                    }
                }
                item {
                    Text(stringResource(R.string.home_quick_access), style = MaterialTheme.typography.titleLarge)
                }
                item {
                    val actions = listOf(
                        HomeAction(stringResource(R.string.home_action_skin), stringResource(R.string.home_action_skin_support), Icons.Filled.CameraAlt, onNavigateToSkinScan),
                        HomeAction(stringResource(R.string.home_action_knowledge), stringResource(R.string.home_action_knowledge_support), Icons.AutoMirrored.Filled.MenuBook, onNavigateToKnowledge),
                        HomeAction(stringResource(R.string.home_action_tools), stringResource(R.string.home_action_tools_support), Icons.Filled.MedicalServices, onNavigateToTools),
                        HomeAction(stringResource(R.string.home_action_model), stringResource(R.string.home_action_model_support), Icons.Filled.Memory, onNavigateToModels)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(MedBotSpacing.small)) {
                        actions.forEach { action ->
                            HomeActionRow(action = action, widthClass = widthClass)
                        }
                    }
                }
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            text = stringResource(R.string.home_agents_summary, AgentRegistry.ALL_AGENTS.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(MedBotSpacing.medium)
                        )
                    }
                }
            }
        }
    }
}

private data class HomeAction(
    val title: String,
    val support: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val onClick: () -> Unit
)

@Composable
private fun HomeActionRow(action: HomeAction, widthClass: MedBotWindowWidth) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = action.onClick).springBounceClick(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = MedBotSpacing.medium, vertical = if (widthClass == MedBotWindowWidth.COMPACT) MedBotSpacing.medium else MedBotSpacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(action.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(MedBotSpacing.medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(action.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(action.support, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = action.title)
        }
    }
}
