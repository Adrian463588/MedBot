@file:OptIn(androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi::class)

package com.medbot.app.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldDefaults
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldRole
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import kotlinx.coroutines.launch

enum class MedBotWindowWidth {
    COMPACT,
    MEDIUM,
    EXPANDED
}

object MedBotSpacing {
    val xSmall = 4.dp
    val small = 8.dp
    val medium = 16.dp
    val large = 24.dp
    val xLarge = 32.dp
    val xxLarge = 48.dp
}

/** Reflows content instead of stretching phone layouts across large windows. */
@Composable
fun AdaptiveContent(
    modifier: Modifier = Modifier,
    maxContentWidth: Dp = 800.dp,
    content: @Composable (MedBotWindowWidth) -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthClass = when {
            maxWidth < 600.dp -> MedBotWindowWidth.COMPACT
            maxWidth < 840.dp -> MedBotWindowWidth.MEDIUM
            else -> MedBotWindowWidth.EXPANDED
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .widthIn(max = maxContentWidth)
        ) {
            content(widthClass)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdaptiveFlowRow(
    modifier: Modifier = Modifier,
    content: @Composable FlowRowScope.() -> Unit
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(MedBotSpacing.small),
        verticalArrangement = Arrangement.spacedBy(MedBotSpacing.small),
        content = content
    )
}

/**
 * Uses the Material Adaptive list-detail scaffold. The library decides whether
 * the list and detail are side by side, reflowed, or single-pane for the
 * current window and posture; callers only provide content and events.
 */
@Composable
fun AdaptiveListDetail(
    modifier: Modifier = Modifier,
    selectedKey: Any? = null,
    listPane: @Composable () -> Unit,
    detailPane: @Composable () -> Unit,
    onBackFromDetail: () -> Unit = {}
) {
    BoxWithConstraints(modifier = modifier) {
        // Compact and medium windows use an explicit single-pane reflow. This
        // keeps the selected detail reachable on phones even when the adaptive
        // directive chooses a list-first scaffold value.
        if (maxWidth < 840.dp) {
            BackHandler(enabled = selectedKey != null) { onBackFromDetail() }
            if (selectedKey == null) listPane() else detailPane()
        } else {
            ExpandedListDetail(
                modifier = Modifier.fillMaxSize(),
                selectedKey = selectedKey,
                listPane = listPane,
                detailPane = detailPane,
                onBackFromDetail = onBackFromDetail
            )
        }
    }
}

@Composable
private fun ExpandedListDetail(
    modifier: Modifier,
    selectedKey: Any?,
    listPane: @Composable () -> Unit,
    detailPane: @Composable () -> Unit,
    onBackFromDetail: () -> Unit
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<Any>(
        scaffoldDirective = calculatePaneScaffoldDirective(currentWindowAdaptiveInfo()),
        adaptStrategies = ListDetailPaneScaffoldDefaults.adaptStrategies()
    )
    val scope = rememberCoroutineScope()
    BackHandler(
        enabled = navigator.canNavigateBack(BackNavigationBehavior.PopUntilContentChange)
    ) {
        scope.launch {
            if (navigator.canNavigateBack(BackNavigationBehavior.PopUntilContentChange)) {
                navigator.navigateBack(BackNavigationBehavior.PopUntilContentChange)
            } else {
                onBackFromDetail()
            }
        }
    }
    LaunchedEffect(selectedKey) {
        if (selectedKey != null) {
            navigator.navigateTo(ThreePaneScaffoldRole.Secondary, selectedKey)
        }
    }
    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        modifier = modifier,
        listPane = { listPane() },
        detailPane = { detailPane() }
    )
}
