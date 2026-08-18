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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Width policy used by MedBot screens. Breakpoints match compact phones, tablets, and expanded layouts.
 */
enum class MedBotWindowWidth {
    COMPACT,
    MEDIUM,
    EXPANDED
}

/**
 * Centers screen content on wide windows while preserving the actual window constraints on phones.
 */
@Composable
fun AdaptiveContent(
    modifier: Modifier = Modifier,
    maxContentWidth: Dp = 720.dp,
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
                .widthIn(max = maxContentWidth)
                .fillMaxWidth()
        ) {
            content(widthClass)
        }
    }
}

/**
 * Wraps chips and short actions instead of forcing a horizontal row at large font scales.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdaptiveFlowRow(
    modifier: Modifier = Modifier,
    content: @Composable FlowRowScope.() -> Unit
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}
