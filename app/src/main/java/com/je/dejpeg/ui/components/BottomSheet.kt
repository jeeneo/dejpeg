/*
 * SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

package com.je.dejpeg.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheet(
    modifier: Modifier = Modifier,
    background: Color,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    expandedHeight: Dp,
    backProgress: Float = 0f,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val expandedHeightPx = with(density) { expandedHeight.toPx() }
    val progress = backProgress.coerceIn(0f, 1f)
    var heightPx by remember { mutableFloatStateOf(0f) }
    var animating by remember { mutableStateOf(false) }
    LaunchedEffect(expanded, expandedHeightPx, progress) {
        val target = if (expanded) {
            val lowerBy = expandedHeightPx * progress * 0.2f
            (expandedHeightPx - lowerBy).coerceAtLeast(0f)
        } else {
            0f
        }
        if (target != heightPx) {
            if (progress > 0f) {
                animating = false
                heightPx = target
            } else {
                animating = true
                animate(
                    initialValue = heightPx, targetValue = target, animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) { value, _ -> heightPx = value }
                animating = false
            }
        }
    }

    Column(
        modifier = modifier
            .height(with(density) { heightPx.toDp() })
            .clipToBounds()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 1.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = expanded && progress == 0f
                ) {
                    onExpandedChange(false)
                }
                .pointerInput(expanded, animating, progress) {
                    if (!expanded || animating || progress > 0f) {
                        return@pointerInput
                    }
                    detectVerticalDragGestures(onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        heightPx = (heightPx - dragAmount).coerceIn(0f, expandedHeightPx)
                    }, onDragEnd = {
                        val settled = heightPx >= expandedHeightPx * 0.9f
                        scope.launch {
                            animate(
                                initialValue = heightPx,
                                targetValue = if (settled) expandedHeightPx else 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            ) { value, _ -> heightPx = value }
                            onExpandedChange(settled)
                        }
                    }, onDragCancel = {
                        scope.launch {
                            animate(
                                initialValue = heightPx,
                                targetValue = if (expanded) expandedHeightPx else 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            ) { value, _ -> heightPx = value }
                        }
                    })
                }) {
            Spacer(modifier = Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .size(width = 48.dp, height = 4.dp)
                    .background(Color.Gray, RoundedCornerShape(50))
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
        Surface(
            color = background, shape = RoundedCornerShape(
                topStart = ScreenHorizontalPadding, topEnd = ScreenHorizontalPadding
            ), shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}
