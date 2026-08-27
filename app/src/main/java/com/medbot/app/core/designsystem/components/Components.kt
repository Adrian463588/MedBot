@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.medbot.app.core.designsystem.components

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.medbot.app.R
import com.medbot.app.core.designsystem.theme.UrgencyEmergencyRed
import com.medbot.app.core.designsystem.theme.UrgencyHighOrange
import com.medbot.app.core.designsystem.theme.UrgencyLowGreen
import com.medbot.app.core.designsystem.theme.UrgencyMediumYellow
import com.medbot.app.core.designsystem.theme.UrgencyInsufficientSlate
import com.medbot.app.domain.model.Citation
import com.medbot.app.domain.model.UrgencyLevel

/**
 * Press feedback driven by the control's [MutableInteractionSource].
 *
 * Controls should pass the same source to their Material component and this
 * modifier. When a caller does not provide one, the modifier remains a safe
 * no-op for interaction state rather than competing with the control's pointer
 * input; Material components still provide their own ripple and semantics.
 */
fun Modifier.springBounceClick(
    scaleDown: Float = 0.98f,
    interactionSource: MutableInteractionSource? = null
): Modifier = composed {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pressScale"
    )

    LaunchedEffect(source) {
        source.interactions.collect { interaction ->
            if (interaction is PressInteraction.Press) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    scale(scale)
}

@Composable
fun MedBotTopAppBar(
    title: String,
    subtitle: String? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Column(modifier = Modifier.widthIn(max = 360.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        navigationIcon = { navigationIcon?.invoke() },
        actions = actions,
        // The root Scaffold owns vertical system-bar and IME insets. Keep the
        // bar aligned to that content instead of consuming the same top inset.
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

private data class NavigationDestination(
    val route: String,
    val label: String,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
private fun navigationDestinations(): List<NavigationDestination> = listOf(
    NavigationDestination("home", stringResource(R.string.nav_home), Icons.Filled.Home, Icons.Outlined.Home),
    NavigationDestination("chat", stringResource(R.string.nav_chat), Icons.AutoMirrored.Filled.Chat, Icons.AutoMirrored.Outlined.Chat),
    NavigationDestination("skin_lineage", stringResource(R.string.nav_skin), Icons.Filled.Face, Icons.Outlined.Face),
    NavigationDestination("knowledge", stringResource(R.string.nav_knowledge), Icons.AutoMirrored.Filled.MenuBook, Icons.AutoMirrored.Outlined.MenuBook),
    NavigationDestination("tools", stringResource(R.string.nav_tools), Icons.Filled.MedicalServices, Icons.Outlined.MedicalServices)
)

@Composable
fun MedBotBottomBar(currentRoute: String, onNavigate: (String) -> Unit) {
    NavigationBar {
        navigationDestinations().forEach { destination ->
            val selected = currentRoute == destination.route
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(destination.route) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = destination.label
                    )
                },
                label = { Text(destination.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}

@Composable
fun MedBotNavigationRail(currentRoute: String, onNavigate: (String) -> Unit) {
    NavigationRail {
        navigationDestinations().forEach { destination ->
            val selected = currentRoute == destination.route
            NavigationRailItem(
                selected = selected,
                onClick = { onNavigate(destination.route) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = destination.label
                    )
                },
                label = { Text(destination.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}

@Composable
fun StatusBannerCard(
    isModelLoaded: Boolean,
    modelName: String?,
    hasDownloadedModel: Boolean = false,
    downloadedModelName: String? = null,
    ragDocCount: Int,
    onLoadModel: () -> Unit = {},
    onManageModel: () -> Unit,
    onManageRag: () -> Unit
) {
    val containerColor = when {
        isModelLoaded -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        hasDownloadedModel -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }

    val borderColor = when {
        isModelLoaded -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        hasDownloadedModel -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    }

    val icon = when {
        isModelLoaded -> Icons.Filled.CheckCircle
        hasDownloadedModel -> Icons.Filled.Memory
        else -> Icons.Filled.Download
    }

    val iconTint = when {
        isModelLoaded -> MaterialTheme.colorScheme.primary
        hasDownloadedModel -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(MedBotSpacing.large)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(MedBotSpacing.small))
                Text(
                    text = when {
                        isModelLoaded -> stringResource(R.string.status_ai_ready)
                        hasDownloadedModel -> stringResource(R.string.status_model_downloaded)
                        else -> stringResource(R.string.status_model_not_downloaded)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        isModelLoaded -> MaterialTheme.colorScheme.primary
                        hasDownloadedModel -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
            Spacer(modifier = Modifier.height(MedBotSpacing.small))
            Text(
                text = when {
                    isModelLoaded && !modelName.isNullOrBlank() -> stringResource(R.string.status_active_model, modelName)
                    hasDownloadedModel && !downloadedModelName.isNullOrBlank() -> stringResource(R.string.status_model_downloaded_desc, downloadedModelName)
                    else -> stringResource(R.string.status_model_not_downloaded_desc)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(MedBotSpacing.xSmall))
            Text(
                text = stringResource(R.string.status_rag_count, ragDocCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(MedBotSpacing.medium))
            AdaptiveFlowRow {
                if (!isModelLoaded && hasDownloadedModel) {
                    Button(onClick = onLoadModel, modifier = Modifier.springBounceClick()) {
                        Icon(Icons.Filled.Memory, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(MedBotSpacing.small))
                        Text(stringResource(R.string.action_load_model), fontWeight = FontWeight.Bold)
                    }
                }
                androidx.compose.material3.OutlinedButton(onClick = onManageModel, modifier = Modifier.springBounceClick()) {
                    Text(stringResource(R.string.action_manage_model))
                }
                androidx.compose.material3.TextButton(onClick = onManageRag, modifier = Modifier.springBounceClick()) {
                    Text(stringResource(R.string.action_open_rag))
                }
            }
        }
    }
}

@Composable
fun SpecialistBadge(agentName: String, specialty: String, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = MedBotSpacing.small, vertical = MedBotSpacing.xSmall)
        ) {
            Icon(Icons.Filled.LocalHospital, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(MedBotSpacing.xSmall))
            Column {
                Text(agentName, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(specialty, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun UrgencyBadge(urgency: UrgencyLevel, modifier: Modifier = Modifier) {
    val color = when (urgency) {
        UrgencyLevel.LOW -> UrgencyLowGreen
        UrgencyLevel.MEDIUM -> UrgencyMediumYellow
        UrgencyLevel.HIGH -> UrgencyHighOrange
        UrgencyLevel.EMERGENCY -> UrgencyEmergencyRed
        UrgencyLevel.INSUFFICIENT_DATA -> UrgencyInsufficientSlate
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.small,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.45f)),
        modifier = modifier
    ) {
        Text(
            text = stringResource(R.string.urgency_format, urgency.labelId),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = MedBotSpacing.small, vertical = MedBotSpacing.xSmall)
        )
    }
}

@Composable
fun CitationsDialog(citations: List<Citation>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var openError by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(MedBotSpacing.medium),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(MedBotSpacing.large)
                    .heightIn(max = 600.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(MedBotSpacing.small))
                    Text(stringResource(R.string.citation_title), style = MaterialTheme.typography.titleLarge)
                }
                Spacer(modifier = Modifier.height(MedBotSpacing.medium))
                if (openError) {
                    Text(
                        text = stringResource(R.string.citation_open_failed),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = MedBotSpacing.small)
                    )
                }
                citations.forEachIndexed { index, citation ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = MedBotSpacing.small)) {
                        Text(
                            text = if (citation.pageNumber > 0) {
                                stringResource(R.string.citation_page, "${index + 1}. ${citation.documentTitle}", citation.pageNumber)
                            } else {
                                "${index + 1}. ${citation.documentTitle} — ${stringResource(R.string.citation_non_paginated)}"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (citation.sectionTitle.isNotBlank()) {
                            Text(stringResource(R.string.citation_section, citation.sectionTitle), style = MaterialTheme.typography.labelMedium)
                        }
                        if (citation.citationId.isNotBlank()) {
                            Text(
                                stringResource(R.string.citation_id, citation.citationId),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(citation.snippet, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = MedBotSpacing.xSmall))
                        if (citation.sourceRole.isNotBlank()) {
                            Text(
                                stringResource(R.string.citation_source_role, citation.sourceRole),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val sourceUrlIsSafe = isSafeCitationUrl(citation.sourceUrl)
                        val sourceUriIsOpenable = isOpenableCitationUri(citation.sourceUri)
                        if (sourceUrlIsSafe || sourceUriIsOpenable) {
                            TextButton(
                                onClick = {
                                    val source = if (sourceUrlIsSafe) citation.sourceUrl else citation.sourceUri
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(source)).apply {
                                        if (sourceUriIsOpenable && !sourceUrlIsSafe) {
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                    }
                                    openError = runCatching {
                                        context.startActivity(intent)
                                    }.isFailure
                                    if (!openError) {
                                        // Keep the citation sheet open so the user can inspect provenance.
                                        Unit
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                            ) {
                                Text(
                                    text = citation.sourceName.takeIf { it.isNotBlank() }
                                        ?.let {
                                            if (sourceUrlIsSafe) {
                                                stringResource(R.string.citation_open_source_named, it)
                                            } else {
                                                stringResource(R.string.citation_open_local_named, it)
                                            }
                                        }
                                        ?: if (sourceUrlIsSafe) {
                                            stringResource(R.string.citation_open_source)
                                        } else {
                                            stringResource(R.string.citation_open_local)
                                        },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (index < citations.lastIndex) HorizontalDivider(modifier = Modifier.padding(top = MedBotSpacing.small))
                    }
                }
                Spacer(modifier = Modifier.height(MedBotSpacing.medium))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().springBounceClick()) {
                    Text(stringResource(R.string.action_close_citations))
                }
            }
        }
    }
}

private fun isSafeCitationUrl(value: String): Boolean {
    val uri = Uri.parse(value)
    return uri.scheme == "https" && uri.host in setOf(
        "www.who.int",
        "eutils.ncbi.nlm.nih.gov",
        "pubmed.ncbi.nlm.nih.gov"
    )
}

private fun isOpenableCitationUri(value: String): Boolean {
    val uri = Uri.parse(value)
    return uri.scheme == "content" && !uri.authority.isNullOrBlank()
}

@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier,
    statusText: String = stringResource(R.string.chat_typing_indicator),
    agentName: String? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "typingIndicator")

    val dot1Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(0)
        ),
        label = "dot1"
    )
    val dot2Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(150)
        ),
        label = "dot2"
    )
    val dot3Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(300)
        ),
        label = "dot3"
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = MedBotSpacing.medium, vertical = MedBotSpacing.small)
        ) {
            if (!agentName.isNullOrBlank()) {
                Text(
                    text = agentName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MedBotSpacing.small)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .offset(y = dot1Offset.dp)
                            .size(6.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .offset(y = dot2Offset.dp)
                            .size(6.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .offset(y = dot3Offset.dp)
                            .size(6.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    val cleanText = remember(text) {
        com.medbot.app.core.common.AiOutputFormatter.cleanThinkingTags(text).trim()
    }
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        cleanText.lines().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("### ") -> {
                    val headingText = line.removePrefix("### ").trim()
                    Text(
                        text = parseMarkdownSpans(headingText, primaryColor, surfaceVariant),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor,
                        modifier = Modifier.padding(top = MedBotSpacing.small, bottom = 2.dp)
                    )
                }
                line.startsWith("## ") -> {
                    val headingText = line.removePrefix("## ").trim()
                    Text(
                        text = parseMarkdownSpans(headingText, primaryColor, surfaceVariant),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor,
                        modifier = Modifier.padding(top = MedBotSpacing.medium, bottom = 2.dp)
                    )
                }
                line.startsWith("# ") -> {
                    val headingText = line.removePrefix("# ").trim()
                    Text(
                        text = parseMarkdownSpans(headingText, primaryColor, surfaceVariant),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor,
                        modifier = Modifier.padding(top = MedBotSpacing.medium, bottom = 2.dp)
                    )
                }
                line.startsWith("- ") || line.startsWith("* ") || line.startsWith("• ") -> {
                    val bulletText = when {
                        line.startsWith("- ") -> line.removePrefix("- ")
                        line.startsWith("* ") -> line.removePrefix("* ")
                        else -> line.removePrefix("• ")
                    }.trim()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            color = primaryColor,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(end = MedBotSpacing.small)
                        )
                        Text(
                            text = parseMarkdownSpans(bulletText, primaryColor, surfaceVariant),
                            style = MaterialTheme.typography.bodyMedium,
                            color = color,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                line.matches(Regex("^\\d+\\.\\s+.*")) -> {
                    val dotIndex = line.indexOf(". ")
                    val number = line.substring(0, dotIndex + 1)
                    val itemText = line.substring(dotIndex + 2).trim()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = number,
                            color = primaryColor,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(end = MedBotSpacing.small)
                        )
                        Text(
                            text = parseMarkdownSpans(itemText, primaryColor, surfaceVariant),
                            style = MaterialTheme.typography.bodyMedium,
                            color = color,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                line == "---" || line == "***" -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = MedBotSpacing.small),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                }
                line.startsWith("> ") -> {
                    val quoteText = line.removePrefix("> ").trim()
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, primaryColor.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = parseMarkdownSpans(quoteText, primaryColor, surfaceVariant),
                            style = MaterialTheme.typography.bodyMedium,
                            color = color,
                            modifier = Modifier.padding(horizontal = MedBotSpacing.medium, vertical = MedBotSpacing.small)
                        )
                    }
                }
                line.isNotBlank() -> {
                    Text(
                        text = parseMarkdownSpans(line, primaryColor, surfaceVariant),
                        style = MaterialTheme.typography.bodyMedium,
                        color = color,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }
        }
    }
}

private fun parseMarkdownSpans(
    text: String,
    primaryColor: Color,
    surfaceVariant: Color
): androidx.compose.ui.text.AnnotatedString {
    return androidx.compose.ui.text.buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                // Bold: **text**
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end != -1) {
                        val content = text.substring(i + 2, end)
                        withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(content)
                        }
                        i = end + 2
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // Inline Code: `code`
                text.startsWith("`", i) -> {
                    val end = text.indexOf("`", i + 1)
                    if (end != -1) {
                        val content = text.substring(i + 1, end)
                        withStyle(
                            androidx.compose.ui.text.SpanStyle(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                background = surfaceVariant.copy(alpha = 0.6f)
                            )
                        ) {
                            append(" $content ")
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // Italic: *text*
                text.startsWith("*", i) && (i + 1 < text.length && text[i + 1] != ' ') -> {
                    val end = text.indexOf("*", i + 1)
                    if (end != -1 && (end + 1 >= text.length || text[end + 1] != '*')) {
                        val content = text.substring(i + 1, end)
                        withStyle(androidx.compose.ui.text.SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                            append(content)
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
}

@Composable
fun StreamingTypewriterText(
    text: String,
    isGenerating: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    agentName: String? = null
) {
    val parsed = remember(text, isGenerating) {
        com.medbot.app.core.common.AiOutputFormatter.parse(text, isGenerating)
    }

    Column(modifier = modifier) {
        if (parsed.displayContent.isNotBlank()) {
            MarkdownText(text = parsed.displayContent, color = color)
        }
        if (isGenerating) {
            val statusText = if (parsed.isThinking || parsed.displayContent.isBlank()) {
                stringResource(R.string.chat_thinking_indicator)
            } else {
                stringResource(R.string.chat_typing_indicator)
            }
            TypingIndicator(
                statusText = statusText,
                agentName = agentName,
                modifier = Modifier.padding(top = if (parsed.displayContent.isNotBlank()) MedBotSpacing.small else 0.dp)
            )
        }
    }
}

@Composable
fun InteractiveSkinSplitComparator(
    beforeBitmap: Bitmap?,
    afterBitmap: Bitmap?,
    beforeLabel: String,
    afterLabel: String,
    modifier: Modifier = Modifier
) {
    var splitRatio by remember { mutableFloatStateOf(0.5f) }
    val sliderLabel = stringResource(R.string.comparator_slider_description)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(MedBotSpacing.medium), modifier = Modifier.fillMaxWidth()) {
            Text(beforeLabel, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text(afterLabel, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MedBotSpacing.small), modifier = Modifier.fillMaxWidth().padding(top = MedBotSpacing.small)) {
            BitmapPane(beforeBitmap, beforeLabel, Modifier.weight(splitRatio.coerceIn(0.2f, 0.8f)))
            BitmapPane(afterBitmap, afterLabel, Modifier.weight((1f - splitRatio).coerceIn(0.2f, 0.8f)))
        }
        androidx.compose.material3.Slider(
            value = splitRatio,
            onValueChange = { splitRatio = it },
            valueRange = 0.2f..0.8f,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = sliderLabel
                    stateDescription = "${(splitRatio * 100).toInt()}%"
                }
        )
    }
}

@Composable
private fun BitmapPane(bitmap: Bitmap?, label: String, modifier: Modifier) {
    Surface(
        modifier = modifier.height(180.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap.asImageBitmap(), contentDescription = label, modifier = Modifier.fillMaxWidth())
        } else {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.skin_photo_unavailable), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
