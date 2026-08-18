@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.medbot.app.core.designsystem.components

import android.graphics.Bitmap
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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

/** Press feedback observes real pointer input and leaves click handling to the control. */
fun Modifier.springBounceClick(scaleDown: Float = 0.98f): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pressScale"
    )

    LaunchedEffect(pressed) {
        if (pressed) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            pressed = true
            waitForUpOrCancellation(pass = PointerEventPass.Initial)
            pressed = false
        }
    }.scale(scale)
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
            Column(modifier = Modifier.widthIn(max = 280.dp)) {
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
                label = { Text(destination.label, maxLines = 1) }
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
                label = { Text(destination.label, maxLines = 1) }
            )
        }
    }
}

@Composable
fun StatusBannerCard(
    isModelLoaded: Boolean,
    modelName: String?,
    ragDocCount: Int,
    onManageModel: () -> Unit,
    onManageRag: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (isModelLoaded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(MedBotSpacing.large)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isModelLoaded) Icons.Filled.Memory else Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint = if (isModelLoaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(MedBotSpacing.small))
                Text(
                    text = if (isModelLoaded) stringResource(R.string.status_ai_ready) else stringResource(R.string.status_model_not_loaded),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(MedBotSpacing.small))
            Text(
                text = if (isModelLoaded && !modelName.isNullOrBlank()) {
                    stringResource(R.string.status_active_model, modelName)
                } else {
                    stringResource(R.string.status_model_source)
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
                Button(onClick = onManageModel, modifier = Modifier.springBounceClick()) {
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
                        Text(citation.snippet, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = MedBotSpacing.xSmall))
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

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier = modifier) {
        text.lines().forEach { line ->
            when {
                line.startsWith("### ") -> Text(line.removePrefix("### ").trim(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = MedBotSpacing.small))
                line.startsWith("## ") -> Text(line.removePrefix("## ").trim(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = MedBotSpacing.medium))
                line.startsWith("- ") || line.startsWith("* ") -> Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text("•", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = MedBotSpacing.small))
                    Text(line.substring(2).trim(), style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.weight(1f))
                }
                line == "---" -> HorizontalDivider(modifier = Modifier.padding(vertical = MedBotSpacing.small))
                line.isNotBlank() -> Text(line.trim(), style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

@Composable
fun StreamingTypewriterText(
    text: String,
    isGenerating: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(modifier = modifier) {
        MarkdownText(text = text, color = color)
        if (isGenerating) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = MedBotSpacing.small)) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(MedBotSpacing.small))
                Text(stringResource(R.string.streaming_typing), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
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
